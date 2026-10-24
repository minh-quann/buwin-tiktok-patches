/* Native component bridge. Kyant's tabs/effects/gestures are vendored unchanged except package. */
package app.morphe.extension.tiktok.glass

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.graphics.RenderNode
import android.view.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.*
import androidx.savedstate.*
import app.morphe.extension.tiktok.glass.resources.GlassResourceTable
import app.morphe.extension.tiktok.glass.mebieco.components.LiquidBottomTab
import app.morphe.extension.tiktok.glass.mebieco.components.LiquidBottomTabs
import app.morphe.extension.tiktok.interaction.NativeVideoAmbient
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal object NativeLiquidTabs {
    fun nativeChildCount(bar: ViewGroup, count: Int): Int =
        if (count > 0 && bar.getChildAt(count - 1) is MirrorView) count - 1 else count
    private val hosts = WeakHashMap<View, Host>()
    private val queued = WeakHashMap<View, Any>()
    private val fadeLevels = WeakHashMap<View, Float>()
    fun prepare(bar: View, chin: Int, capture: Boolean) {
        if (bar !is ViewGroup) return
        hosts[bar]?.let { it.update(capture, chin); it.sync(); return }
        if (queued.containsKey(bar)) return
        val token = Any(); queued[bar] = token
        bar.post {
            if (queued[bar] !== token) return@post
            queued.remove(bar)
            if (bar.isAttachedToWindow && hosts[bar] == null) {
                try { hosts[bar] = Host(bar, chin, capture) }
                catch (e: RuntimeException) { android.util.Log.e("morphe: NativeGlass", "Original Kyant tabs could not attach", e) }
            }
        }
    }
    fun canShareScene(bar: View): Boolean = hosts[bar]?.canShareScene() == true
    fun sceneChanged(bar: View) { hosts[bar]?.sceneChanged() }
    fun layout(bar: View) { hosts[bar]?.layout() }
    fun consumeTouch(bar: View, event: MotionEvent): Boolean = hosts[bar]?.ownedTouch(event, null) == true
    fun dispatchFromMask(mask: View, event: MotionEvent): Boolean {
        val bar = app.morphe.extension.tiktok.feed.NativeBottomBar.tabs(mask) ?: return false
        if (bar.parent !== mask) return false
        return hosts[bar]?.ownedTouch(event, mask) == true
    }
    fun touch(bar: View, event: MotionEvent) { hosts[bar]?.touch(event) }
    fun fade(bar: View, percent: Int): Boolean {
        val alpha = percent.coerceIn(0, 100) / 100f
        fadeLevels[bar] = alpha
        val host = hosts[bar] ?: return false
        host.foregroundAlpha = alpha
        host.sync()
        return true
    }
    fun release(bar: View) { queued.remove(bar); fadeLevels.remove(bar); hosts.remove(bar)?.close(); NativeVideoAmbient.release(bar); NativeGlassScene.release(bar) }

    private class ScopedResources(private val original: Resources) : Resources(original.assets, original.displayMetrics, original.configuration) {
        override fun getText(id: Int): CharSequence = GlassResourceTable.text(id) ?: original.getText(id)
        override fun getText(id: Int, def: CharSequence?): CharSequence = GlassResourceTable.text(id) ?: original.getText(id, def)
    }
    private class GlassContext(base: Context) : ContextWrapper(base) {
        private val scoped = ScopedResources(base.resources)
        override fun getResources(): Resources = scoped
    }
    private class Owner : SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
        init { controller.performAttach(); controller.performRestore(null); registry.currentState = Lifecycle.State.RESUMED }
        fun close() { registry.currentState = Lifecycle.State.DESTROYED }
    }
    /** Native tab actions/accessibility stay intact; Compose owns gestures in its capsule. */
    private class MirrorView(context: Context) : AbstractComposeView(context) {
        private var contentBlock by mutableStateOf<(@Composable () -> Unit)?>(null)
        @Composable override fun Content() { contentBlock?.invoke() }
        fun setContent(block: @Composable () -> Unit) { contentBlock = block }
        var mirror = false
        override fun dispatchTouchEvent(event: MotionEvent): Boolean = if (mirror) super.dispatchTouchEvent(event) else false
    }
    private class Host(bar: ViewGroup, var chin: Int, private var capture: Boolean) : ViewTreeObserver.OnPreDrawListener {
        private val barRef = WeakReference(bar)
        private val tabs = (0 until bar.childCount).map { bar.getChildAt(it) }
        private val original = tabs.map { it.alpha to it.importantForAccessibility }
        private val originalClipChildren = bar.clipChildren
        private val originalClipPadding = bar.clipToPadding
        private val owner = Owner()
        private val compose = MirrorView(GlassContext(bar.context))
        private val scope = CoroutineScope(AndroidUiDispatcher.Main + SupervisorJob())
        private val recomposer = Recomposer(scope.coroutineContext)
        private var selected by mutableIntStateOf(0)
        var foregroundAlpha by mutableFloatStateOf(fadeLevels[bar] ?: 1f)
        private var shifts by mutableStateOf<List<Offset>>(emptyList())
        private var icons by mutableStateOf<List<Bitmap>>(emptyList())
        private var frame: Bitmap? = null
        private val backdropNode = RenderNode("MorpheLiquidTabsBackdrop")
        private var backdropVersion by mutableIntStateOf(0)
        private var darkPage by mutableStateOf(capture)
        private var backdropDirty = true
        private var themeDirty = true
        private var sceneProbeDirty = true
        private var sceneProbeTab = -1
        private var surfaceInScene = WeakReference<View>(null)
        private val themeCanvas = Canvas()
        private var frameScheduled = false
        private var scheduled = false
        private var lastWakeup = SystemClock.uptimeMillis()
        private var lastTextureTimestamp = Long.MIN_VALUE
        private var lastSurfaceCopy = Long.MIN_VALUE
        private var liveTexture = false
        private val sourceBounds = Rect()
        private val surfaceBounds = Rect()
        private var textureRef = WeakReference<android.graphics.SurfaceTexture>(null)
        private val viewport = Rect()
        private val at = IntArray(2)
        private val barAt = IntArray(2)
        private var video: WeakReference<View> = WeakReference(null)
        private var pending = false
        private var closed = false
        private var mirroredEvent = false
        private var ownsTouch = false
        private val inverseTouch = android.graphics.Matrix()
        private var dragGesture = false
        private var downX = 0f
        private var downY = 0f
        private val handler = Handler(Looper.getMainLooper())
        private val scrollListener = ViewTreeObserver.OnScrollChangedListener {
            // Keep a valid source during ordinary scrolls; snapshot validates its visible
            // bounds. Do not rescan the entire Activity on every scroll notification.
            wake()
        }
        private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { themeDirty = true; sceneProbeDirty = true; wake() }
        private val captureFrame = object : Runnable {
            override fun run() {
                scheduled = false; frameScheduled = false
                val root = barRef.get() ?: return
                if (closed || !root.isAttachedToWindow || !root.isShown || root.windowVisibility != View.VISIBLE) return
                if (capture && !pending) snapshot(root)
                if (backdropDirty) recordBackdrop(root)
                if (capture || (!NativeGlassScene.available(root) && SystemClock.uptimeMillis() - lastWakeup < 350L)) schedule()
            }
        }
        private fun schedule() {
            if (scheduled || closed) return
            scheduled = true
            handler.postDelayed(captureFrame, if (liveTexture) 32L else 100L)
        }
        fun canShareScene(): Boolean {
            val bar = barRef.get() ?: return false
            val index = tabs.indexOfFirst { it.isSelected }
            val old = surfaceInScene.get()
            if (old != null && (!old.isAttachedToWindow || !old.isShown || !old.getGlobalVisibleRect(viewport))) sceneProbeDirty = true
            if (sceneProbeDirty || sceneProbeTab != index) {
                sceneProbeDirty = false; sceneProbeTab = index; surfaceInScene.clear()
                val queue = java.util.ArrayDeque<View>()
                queue.add((bar.parent as? View) ?: bar.rootView)
                var visited = 0
                while (queue.isNotEmpty() && visited++ < 1600) {
                    val child = queue.removeFirst()
                    if (child === bar || !child.isShown) continue
                    if (child is SurfaceView && child.getGlobalVisibleRect(viewport)) {
                        surfaceInScene = WeakReference(child); break
                    }
                    if (child is ViewGroup) for (i in 0 until child.childCount) queue.add(child.getChildAt(i))
                }
            }
            // A SurfaceView RenderNode also owns a compositor-position listener. Never
            // replay that node through a refracting/blurred layer: preserve native drawing
            // and use only the bounded asynchronous pixel strip for this renderer.
            return surfaceInScene.get() == null
        }
        fun sceneChanged() { wake() }
        private fun wake() {
            if (closed) return
            backdropDirty = true
            lastWakeup = SystemClock.uptimeMillis()
            val bar = barRef.get() ?: return
            if (NativeGlassScene.available(bar)) {
                // Content already has a GPU display list. Follow native vsync instead
                // of sampling scrolling lists at 10fps or traversing them a second time.
                if (!frameScheduled) {
                    handler.removeCallbacks(captureFrame)
                    scheduled = true; frameScheduled = true
                    compose.postOnAnimation(captureFrame)
                }
            } else if (!scheduled) {
                scheduled = true
                handler.post(captureFrame)
            }
        }
        fun update(captureVideo: Boolean, bottomChin: Int) {
            chin = bottomChin
            if (capture == captureVideo) return
            capture = captureVideo
            themeDirty = true
            // A page switch must never keep refracting the previous feed video.
            video = WeakReference(null); frame = null
            barRef.get()?.let { NativeVideoAmbient.release(it) }
            lastSurfaceCopy = Long.MIN_VALUE; liveTexture = false; sourceBounds.setEmpty(); textureRef.clear()
            lastTextureTimestamp = Long.MIN_VALUE
            wake()
        }
        init {
            require(tabs.isNotEmpty()) { "Native bottom tabs are empty" }
            compose.id = View.generateViewId()
            bar.clipChildren = false; bar.clipToPadding = false
            compose.clipChildren = false; compose.clipToPadding = false
            // Keep TikTok's native accessibility actions; the copied component is visual.
            compose.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            compose.setViewTreeLifecycleOwner(owner)
            compose.setViewTreeSavedStateRegistryOwner(owner)
            compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            compose.setParentCompositionContext(recomposer)
            scope.launch { recomposer.runRecomposeAndApplyChanges() }
            compose.setContent {
                // Match MEBIECO: record the GPU backdrop into a layer without drawing a
                // source image on screen. Read the version only in draw, never composition.
                val backdrop = rememberLayerBackdrop(onDraw = {
                    @Suppress("UNUSED_VARIABLE") val version = backdropVersion
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        val bar = barRef.get()
                        if (bar != null && NativeGlassScene.draw(bar, native)) {
                            if (capture) {
                                val bitmap = frame
                                if (bitmap != null && !bitmap.isRecycled) {
                                    val save = native.save()
                                    native.translate((sourceBounds.left-barAt[0]).toFloat(), (sourceBounds.top-barAt[1]).toFloat())
                                    native.drawBitmap(bitmap, null, surfaceBounds, null)
                                    native.restoreToCount(save)
                                }
                                NativeVideoAmbient.draw(bar, native)
                            }
                        } else if (backdropNode.hasDisplayList() && native.isHardwareAccelerated) native.drawRenderNode(backdropNode)
                    }
                })
                val ink = if (darkPage) Color.White else Color.Black
                Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop))
                LiquidBottomTabs(
                    selectedTabIndex = { selected },
                    onTabSelected = { index ->
                        // Compose owns pointer gestures; invoke the original native action once.
                        // Keep compatibility with the legacy native-first mirror path.
                        val nativeIndex = tabs.indexOfFirst { it.isSelected }
                        if ((!mirroredEvent || dragGesture) && index in tabs.indices
                            && nativeIndex != index && selected != index) tabs[index].performClick()
                        selected = index
                    },
                    backdrop = backdrop,
                    tabsCount = tabs.size,
                    blurRadiusDp = 2f,
                    // Icons/text are colored individually below. A whole-row tint erases
                    // the black plus and cyan/red edges from TikTok's multicolor bitmap.
                    tintActiveContent = false,
                    isDark = darkPage,
                    activeTabColor = if (darkPage) "#FFFFFF" else "#000000",
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 8.dp)
                ) {
                    tabs.forEachIndexed { index, tab ->
                        LiquidBottomTab(
                            onClick = { if (!mirroredEvent && !tab.isSelected) tab.performClick(); selected = index },
                            modifier = Modifier.graphicsLayer {
                                alpha = foregroundAlpha
                                val shift = shifts.getOrNull(index) ?: Offset.Zero
                                translationX = shift.x; translationY = shift.y
                            }
                        ) {
                            icons.getOrNull(index)?.let { bitmap ->
                                Image(bitmap.asImageBitmap(), contentDescription = null,
                                    modifier = if (index == tabs.size / 2) Modifier.size(width = 48.dp, height = 34.dp) else Modifier.size(20.dp),
                                    contentScale = ContentScale.Fit,
                                    colorFilter = if (index == tabs.size / 2) null else ColorFilter.tint(ink))
                            }
                            val text = label(tab)
                            if (text.isNotBlank()) BasicText(text, style = TextStyle(ink, 12.sp))
                        }
                    }
                }
                }
            }
            bar.addView(compose, ViewGroup.LayoutParams(0, 0))
            tabs.forEach { it.alpha = 0f }
            bar.viewTreeObserver.addOnPreDrawListener(this)
            layout(); sync()
            bar.viewTreeObserver.addOnScrollChangedListener(scrollListener)
            bar.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
            wake()
        }
        fun layout() {
            val bar = barRef.get() ?: return
            val width = bar.width.coerceAtLeast(1)
            val height = bar.height.coerceAtLeast(1)
            // The original pill can grow beyond 56dp. Its Compose host owns the full 72dp
            // frame, including the source's shadow/press space, not just the capsule bounds.
            if (compose.measuredWidth != width || compose.measuredHeight != height) {
                compose.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                backdropDirty = true
            }
            if (compose.left != 0 || compose.top != 0 || compose.right != width || compose.bottom != height) {
                compose.layout(0, 0, width, height)
                wake()
            }
        }
        fun sync() {
            // Both TikTok's own tab animations and our foreground-fade traversal can write
            // alpha back to one after mounting. Suppress the native copy again immediately
            // before dispatchDraw; keep its bounds, visibility and click listeners intact.
            tabs.forEach { if (it.alpha != 0f) it.alpha = 0f }
            if (tabs.indices.any { shifts.getOrNull(it) != Offset(tabs[it].translationX, tabs[it].translationY) }) {
                shifts = tabs.map { Offset(it.translationX, it.translationY) }
            }
            val index = tabs.indexOfFirst { it.isSelected }
            if (index >= 0 && selected != index) { selected = index; themeDirty = true; wake() }
            if (icons.isEmpty() && tabs.all { it.width > 0 && it.height > 0 }) {
                icons = tabs.mapIndexed { index, tab ->
                    val icon = icon(tab)
                    if (icon == null) Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    else {
                        val bitmap = Bitmap.createBitmap(icon.width, icon.height, Bitmap.Config.ARGB_8888)
                        icon.draw(Canvas(bitmap))
                        if (index == tabs.size / 2) trimIcon(bitmap) else bitmap
                    }
                }
            }
        }
        override fun onPreDraw(): Boolean {
            layout(); sync()
            // Restart only when visible again after backgrounding; hidden windows stop
            // their sampler instead of keeping a handler/repaint loop alive.
            val bar = barRef.get()
            if (capture && !scheduled && bar?.windowVisibility == View.VISIBLE && bar.isShown) wake()
            return true
        }
        fun ownedTouch(event: MotionEvent, mask: View?): Boolean {
            if (!ownsTouch && event.actionMasked != MotionEvent.ACTION_DOWN) return false
            val bar = barRef.get() ?: return false
            if (closed || !bar.isShown || !compose.isAttachedToWindow) {
                if (ownsTouch) {
                    val cancel = MotionEvent.obtain(event); cancel.action = MotionEvent.ACTION_CANCEL
                    compose.mirror = true
                    try { compose.dispatchTouchEvent(cancel) }
                    finally { compose.mirror = false; cancel.recycle(); ownsTouch = false }
                    bar.parent?.requestDisallowInterceptTouchEvent(false)
                }
                return false
            }
            val copied = MotionEvent.obtain(event)
            try {
                if (mask != null) {
                    copied.offsetLocation((mask.scrollX-bar.left).toFloat(), (mask.scrollY-bar.top).toFloat())
                    if (!bar.matrix.isIdentity) {
                        if (!bar.matrix.invert(inverseTouch)) return false
                        copied.transform(inverseTouch)
                    }
                }
                copied.offsetLocation(-compose.left.toFloat(), -compose.top.toFloat())
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    val d = bar.resources.displayMetrics.density
                    ownsTouch = copied.x >= 16*d && copied.x < compose.width-16*d
                        && copied.y >= 8*d && copied.y < 64*d
                    if (ownsTouch) bar.parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (!ownsTouch) return false
                wake()
                // The original Compose detector receives DOWN first and owns the entire
                // stream, including moves outside the pill. The seek bar never sees it.
                compose.mirror = true
                mirroredEvent = false
                try { compose.dispatchTouchEvent(copied) }
                finally { compose.mirror = false }
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    ownsTouch = false
                    bar.parent?.requestDisallowInterceptTouchEvent(false)
                } else bar.parent?.requestDisallowInterceptTouchEvent(true)
                return true
            } finally { copied.recycle() }
        }
        fun touch(event: MotionEvent) {
            wake()
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                downX = event.x; downY = event.y; dragGesture = false
            } else {
                val slop = ViewConfiguration.get(compose.context).scaledTouchSlop
                if (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop) dragGesture = true
            }
            val copied = MotionEvent.obtain(event)
            copied.offsetLocation(-compose.left.toFloat(), -compose.top.toFloat())
            compose.mirror = true
            mirroredEvent = true
            try { compose.dispatchTouchEvent(copied) } finally { mirroredEvent = false; compose.mirror = false; copied.recycle() }
        }
        private fun findVideo(root: View): View? {
            val queue = java.util.ArrayDeque<View>(); queue.add(root)
            var best: View? = null; var area = 0L; var visited = 0
            val visible = Rect()
            while (queue.isNotEmpty() && visited++ < 1600) {
                val v = queue.removeFirst()
                if (v === compose || !v.isShown) continue
                if (v is TextureView || v is SurfaceView) {
                    if (v.getGlobalVisibleRect(visible) && visible.width().toLong()*visible.height() > area) {
                        best = v; area = visible.width().toLong()*visible.height()
                    }
                } else if (v is ViewGroup) for (i in 0 until v.childCount) queue.add(v.getChildAt(i))
            }
            return best
        }
        private fun snapshot(bar: ViewGroup) {
            var source = video.get()
            if (source == null || !source.isAttachedToWindow || !source.isShown || !source.getGlobalVisibleRect(viewport)
                || viewport.height() < source.height / 2) {
                source = findVideo((bar.parent as? View) ?: bar.rootView); video = WeakReference(source)
                lastTextureTimestamp = Long.MIN_VALUE; lastSurfaceCopy = Long.MIN_VALUE
                frame = null; backdropDirty = true; sourceBounds.setEmpty(); textureRef.clear()
                NativeVideoAmbient.release(bar)
            }
            liveTexture = false
            source ?: return
            NativeVideoAmbient.select(bar, source)
            source.getLocationInWindow(at); compose.getLocationInWindow(barAt)
            val picture = Rect(at[0], at[1], at[0]+source.width, at[1]+source.height)
            if (sourceBounds != picture) { sourceBounds.set(picture); backdropDirty = true }
            val d = bar.resources.displayMetrics.density
            val capsule = Rect(barAt[0]+(16*d).toInt(), barAt[1]+(8*d).toInt(),
                barAt[0]+compose.width-(16*d).toInt(), barAt[1]+(64*d).toInt())
            if (!Rect.intersects(picture, capsule)) {
                if (frame != null) { frame = null; backdropDirty = true }
                // Ambient uses the existing native cover palette, and draw checks edge
                // proximity. A distant letterboxed video causes no pixel copies at all.
                return
            }
            if (source is TextureView) {
                val texture = source.surfaceTexture ?: return
                if (!source.isAvailable) return
                liveTexture = true
                if (textureRef.get() !== texture) {
                    textureRef = WeakReference(texture); backdropDirty = true
                }
                val timestamp = texture.timestamp
                if (timestamp != lastTextureTimestamp) {
                    lastTextureTimestamp = timestamp
                    // The recorded display list references the player's existing GPU
                    // layer. Redraw the Compose backdrop without traversing native views
                    // or recomposing the tabs on every video frame.
                    backdropVersion++
                }
            } else if (source is SurfaceView && source.holder.surface.isValid) {
                val now = SystemClock.uptimeMillis()
                if (lastSurfaceCopy != Long.MIN_VALUE && now-lastSurfaceCopy < 200L) return
                val strip = Rect(barAt[0], barAt[1], barAt[0]+compose.width, barAt[1]+compose.height)
                if (!strip.intersect(picture)) return
                strip.offset(-picture.left, -picture.top)
                val width = minOf(192, strip.width())
                if (width <= 0 || strip.height() <= 0) return
                val height = (width.toLong()*strip.height()/strip.width()).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                lastSurfaceCopy = now; pending = true
                try {
                    PixelCopy.request(source, strip, bitmap, { result ->
                        pending = false
                        if (!closed && capture && video.get() === source && result == PixelCopy.SUCCESS) {
                            surfaceBounds.set(strip); publish(bitmap)
                        } else bitmap.recycle()
                    }, handler)
                } catch (_: IllegalArgumentException) { pending = false; bitmap.recycle() }
            }
        }
        private fun recordBackdrop(bar: ViewGroup) {
            if (compose.width <= 0 || compose.height <= 0) return
            compose.getLocationInWindow(barAt)
            viewport.set(barAt[0], barAt[1], barAt[0]+compose.width, barAt[1]+compose.height)
            if (NativeGlassScene.available(bar)) {
                if (themeDirty) {
                    val color = NativeGlassBackdrop.draw(bar.rootView, bar, themeCanvas, viewport, null, null,
                        android.graphics.Color.WHITE, sampleOnly = true)
                    darkPage = capture || (android.graphics.Color.red(color)*299 + android.graphics.Color.green(color)*587
                        + android.graphics.Color.blue(color)*114) < 128000
                    themeDirty = false
                }
                backdropDirty = false; backdropVersion++
                return
            }
            backdropNode.setPosition(0, 0, compose.width, compose.height)
            val canvas = backdropNode.beginRecording(compose.width, compose.height)
            var background = if (capture) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            try {
                background = NativeGlassBackdrop.draw(bar.rootView, bar, canvas, viewport,
                    if (capture) video.get() else null, if (capture) frame else null,
                    if (capture) android.graphics.Color.BLACK else android.graphics.Color.WHITE, surfaceBounds)
                if (capture) NativeVideoAmbient.draw(bar, canvas)
            } catch (error: RuntimeException) {
                android.util.Log.w("morphe: NativeGlass", "Could not record native backdrop", error)
            } finally { backdropNode.endRecording() }
            darkPage = capture || (android.graphics.Color.red(background)*299 + android.graphics.Color.green(background)*587
                + android.graphics.Color.blue(background)*114) < 128000
            backdropDirty = false
            backdropVersion++
        }
        private fun publish(bitmap: Bitmap) {
            if (closed) { bitmap.recycle(); return }
            // RenderNode owns references to recorded bitmap pixels. Let Android reclaim old
            // frames safely instead of recycling a bitmap still used by RenderThread.
            frame = bitmap
            backdropDirty = true
        }
        fun close() {
            closed = true; handler.removeCallbacks(captureFrame); compose.removeCallbacks(captureFrame)
            if (ownsTouch) barRef.get()?.parent?.requestDisallowInterceptTouchEvent(false)
            ownsTouch = false
            val bar = barRef.get()
            if (bar != null) {
                if (bar.viewTreeObserver.isAlive) {
                    bar.viewTreeObserver.removeOnPreDrawListener(this)
                    bar.viewTreeObserver.removeOnScrollChangedListener(scrollListener)
                    bar.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
                }
                bar.clipChildren = originalClipChildren; bar.clipToPadding = originalClipPadding
                tabs.forEachIndexed { i, tab -> tab.alpha = original[i].first; tab.importantForAccessibility = original[i].second }
                compose.disposeComposition()
                bar.post { bar.removeView(compose) }
            }
            owner.close()
            recomposer.cancel(); scope.cancel()
            backdropNode.discardDisplayList()
            frame = null; icons = emptyList()
        }
        private fun label(view: View): String {
            if (view.visibility != View.VISIBLE) return ""
            if (view is android.widget.TextView) return view.text.toString()
            if (view is ViewGroup) return (0 until view.childCount).map { label(view.getChildAt(it)) }.filter { it.isNotBlank() }.joinToString(" ")
            return ""
        }
        // Native ImageView padding can make the middle glyph tiny even in a larger
        // Compose box. Trim its transparent margins once, when copying tab artwork.
        private fun trimIcon(bitmap: Bitmap): Bitmap {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            var left = bitmap.width; var top = bitmap.height; var right = -1; var bottom = -1
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if ((pixels[y * bitmap.width + x] ushr 24) > 16) {
                    left = minOf(left, x); top = minOf(top, y)
                    right = maxOf(right, x); bottom = maxOf(bottom, y)
                }
            }
            return if (right >= left && bottom >= top) Bitmap.createBitmap(bitmap, left, top, right-left+1, bottom-top+1) else bitmap
        }
        private fun icon(view: View): android.widget.ImageView? {
            if (view.visibility != View.VISIBLE) return null
            if (view is android.widget.ImageView && view.width > 0 && view.height > 0 && view.drawable != null) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) icon(view.getChildAt(i))?.let { return it }
            return null
        }
    }
}
