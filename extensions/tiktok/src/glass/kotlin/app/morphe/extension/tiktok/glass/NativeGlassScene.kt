/* Copyright 2026 Buwin contributors. Native single-pass display-list bridge. */
package app.morphe.extension.tiktok.glass

import android.graphics.Canvas
import android.graphics.RenderNode
import android.view.View
import android.view.ViewGroup
import app.morphe.extension.tiktok.feed.NativeBottomBar
import app.morphe.extension.tiktok.feed.NativeGlassBar
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Shares the original content pass with Backdrop, like Compose's layerBackdrop.
 * drawChild skips only the native tabs while recording. Native content is traversed
 * once, and the tabs are drawn afterwards by the original parent's drawChild.
 */
internal object NativeGlassScene {
    private class Scene(mask: ViewGroup, bar: View) {
        val mask = WeakReference(mask)
        val bar = WeakReference(bar)
        val node = RenderNode("MorpheNativeContent")
        var recording = false
        var enabled = true
        var changed = true
        var width = 0
        var height = 0
        val at = IntArray(2)
        val barAt = IntArray(2)
    }
    private val scenes = WeakHashMap<View, Scene>()
    private val byBar = WeakHashMap<View, Scene>()

    fun begin(mask: ViewGroup, canvas: Canvas): Canvas {
        if (!NativeGlassBar.isGlass() || !canvas.isHardwareAccelerated || mask.width <= 0 || mask.height <= 0) return canvas
        var scene = scenes[mask]
        val bar = scene?.bar?.get()?.takeIf { it.parent === mask && it.isAttachedToWindow }
            ?: NativeBottomBar.tabs(mask) ?: return canvas
        if (bar.parent !== mask || !bar.isShown) return canvas
        if (!NativeLiquidTabs.canShareScene(bar)) { scene?.enabled = false; return canvas }
        if (scene == null || scene.bar.get() !== bar) {
            scene?.node?.discardDisplayList()
            scene = Scene(mask, bar); scenes[mask] = scene; byBar[bar] = scene
        }
        scene.enabled = true
        scene.changed = scene.width != mask.width || scene.height != mask.height
        scene.width = mask.width; scene.height = mask.height
        // Only native content dirtiness should wake Backdrop. A redraw of the glass
        // itself must not create a self-sustaining invalidate/record loop.
        for (i in 0 until mask.childCount) {
            val child = mask.getChildAt(i)
            if (child !== bar && child.visibility == View.VISIBLE && child.isDirty) scene.changed = true
        }
        scene.node.setPosition(0, 0, mask.width, mask.height)
        val recording = scene.node.beginRecording(mask.width, mask.height)
        scene.recording = true
        mask.background?.draw(recording)
        return recording
    }
    fun skip(mask: ViewGroup, child: View): Boolean {
        val scene = scenes[mask] ?: return false
        return scene.recording && scene.bar.get() === child
    }
    fun end(mask: ViewGroup, canvas: Canvas): View? {
        val scene = scenes[mask] ?: return null
        if (!scene.recording) return null
        scene.recording = false; scene.node.endRecording()
        canvas.drawRenderNode(scene.node)
        val bar = scene.bar.get() ?: return null
        if (scene.changed) NativeLiquidTabs.sceneChanged(bar)
        return bar
    }
    fun available(bar: View): Boolean = byBar[bar]?.let { it.enabled && it.mask.get()?.isAttachedToWindow == true && it.node.hasDisplayList() } == true
    fun draw(bar: View, canvas: Canvas): Boolean {
        val scene = byBar[bar] ?: return false
        val mask = scene.mask.get() ?: return false
        if (!scene.enabled || !canvas.isHardwareAccelerated || !scene.node.hasDisplayList() || !mask.isAttachedToWindow) return false
        mask.getLocationInWindow(scene.at); bar.getLocationInWindow(scene.barAt)
        val save = canvas.save()
        canvas.translate((scene.at[0]-scene.barAt[0]).toFloat(), (scene.at[1]-scene.barAt[1]).toFloat())
        canvas.drawRenderNode(scene.node)
        canvas.restoreToCount(save)
        return true
    }
    fun release(bar: View) {
        val scene = byBar.remove(bar) ?: return
        scene.mask.get()?.let { scenes.remove(it) }
        scene.node.discardDisplayList()
    }
}
