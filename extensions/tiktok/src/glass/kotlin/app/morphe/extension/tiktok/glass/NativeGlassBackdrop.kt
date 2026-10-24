/* Native adaptation of MEBIECO's region-culled RenderNode backdrop recorder. */
package app.morphe.extension.tiktok.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup

/** Records only content behind the bar. Never draw the bar or take a window screenshot. Texture layers stay on the GPU. */
object NativeGlassBackdrop {
    @JvmStatic @JvmOverloads fun draw(root: View, excluded: View, canvas: Canvas, region: Rect,
        video: View?, frame: Bitmap?, fallback: Int, frameBounds: Rect? = null, sampleOnly: Boolean = false): Int {
        val painter = Painter(excluded, canvas, region, video, frame, fallback, frameBounds, sampleOnly)
        if (!sampleOnly) canvas.drawColor(fallback)
        val location = IntArray(2)
        root.getLocationInWindow(location)
        val saved = canvas.save()
        try {
            canvas.translate((location[0]-region.left).toFloat(), (location[1]-region.top).toFloat())
            painter.visit(root, location[0].toFloat(), location[1].toFloat())
        } finally { canvas.restoreToCount(saved) }
        return painter.background
    }

    private class Painter(val excluded: View, val canvas: Canvas, val region: Rect,
        val video: View?, val frame: Bitmap?, var background: Int, val frameBounds: Rect?, val sampleOnly: Boolean) {
        private var visited = 0
        fun visit(view: View, x: Float, y: Float) {
            if (view === excluded || view.visibility != View.VISIBLE || view.alpha <= 0.01f
                || view.width <= 0 || view.height <= 0 || visited++ >= 1600) return
            if (x >= region.right || y >= region.bottom || x+view.width <= region.left || y+view.height <= region.top) return
            val saved = canvas.save()
            try {
                if (view.alpha < 1f) canvas.saveLayerAlpha(0f, 0f, view.width.toFloat(), view.height.toFloat(), (view.alpha*255).toInt())
                val drawable = view.background
                if (drawable is ColorDrawable && region.exactCenterX() >= x && region.exactCenterX() < x+view.width
                    && region.exactCenterY() >= y && region.exactCenterY() < y+view.height) {
                    val color = drawable.color
                    val alpha = Color.alpha(color)*view.alpha
                    fun channel(front: Int, back: Int) = ((front*alpha+back*(255-alpha))/255).toInt()
                    background = Color.rgb(channel(Color.red(color), Color.red(background)),
                        channel(Color.green(color), Color.green(background)), channel(Color.blue(color), Color.blue(background)))
                }
                if (view is ViewGroup) {
                    drawBackground(view)
                    if (view.clipChildren) canvas.clipRect(0, 0, view.width, view.height)
                    for (i in 0 until view.childCount) {
                        val child = view.getChildAt(i) ?: continue
                        if (child === excluded || child.visibility != View.VISIBLE) continue
                        val dx = child.left-view.scrollX
                        val dy = child.top-view.scrollY
                        val childSave = canvas.save()
                        try {
                            canvas.translate(dx.toFloat(), dy.toFloat())
                            canvas.concat(child.matrix)
                            visit(child, x+dx+child.translationX, y+dy+child.translationY)
                        } finally { canvas.restoreToCount(childSave) }
                    }
                } else if (view is TextureView) {
                    // Android's TextureView.draw(RecordingCanvas) records the existing
                    // TextureLayer directly. No bitmap, readback or replacement listener.
                    if (!sampleOnly && view === video && canvas.isHardwareAccelerated) view.draw(canvas)
                } else if (view is SurfaceView) {
                    drawBackground(view)
                    if (!sampleOnly && view === video && frame != null && !frame.isRecycled)
                        canvas.drawBitmap(frame, null, frameBounds ?: Rect(0, 0, view.width, view.height), null)
                } else if (!sampleOnly) view.draw(canvas)
            } catch (_: RuntimeException) {
                // A decorative capture must not break playback when a native custom view
                // cannot be recorded offscreen. Continue with its siblings, as in MEBIECO.
            } finally { canvas.restoreToCount(saved) }
        }
        private fun drawBackground(view: View) {
            if (sampleOnly) return
            val drawable = view.background ?: return
            val bounds = drawable.copyBounds()
            try { drawable.setBounds(0, 0, view.width, view.height); drawable.draw(canvas) }
            finally { drawable.bounds = bounds }
        }
    }
}
