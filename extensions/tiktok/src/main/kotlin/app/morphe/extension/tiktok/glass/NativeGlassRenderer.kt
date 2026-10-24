/* Copyright 2026 Buwin contributors. Original Kyant Compose component bridge, API 33+. */
package app.morphe.extension.tiktok.glass

import android.graphics.Canvas
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

object NativeGlassRenderer {
    @JvmStatic fun beginScene(mask: ViewGroup, canvas: Canvas): Canvas =
        if (Build.VERSION.SDK_INT >= 33) NativeGlassScene.begin(mask, canvas) else canvas
    @JvmStatic fun skipSceneChild(mask: ViewGroup, child: View): Boolean =
        Build.VERSION.SDK_INT >= 33 && NativeGlassScene.skip(mask, child)
    @JvmStatic fun endScene(mask: ViewGroup, canvas: Canvas): View? =
        if (Build.VERSION.SDK_INT >= 33) NativeGlassScene.end(mask, canvas) else null
    @JvmStatic fun nativeChildCount(bar: ViewGroup, count: Int): Int =
        if (Build.VERSION.SDK_INT >= 33) NativeLiquidTabs.nativeChildCount(bar, count) else count
    @JvmStatic fun draw(bar: View, canvas: Canvas, chin: Int, captureVideo: Boolean) {
        if (Build.VERSION.SDK_INT >= 33) NativeLiquidTabs.prepare(bar, chin, captureVideo)
    }
    @JvmStatic fun attach(bar: View) { }
    @JvmStatic fun fade(bar: View, percent: Int): Boolean =
        Build.VERSION.SDK_INT >= 33 && NativeLiquidTabs.fade(bar, percent)
    @JvmStatic fun layout(bar: View) { if (Build.VERSION.SDK_INT >= 33) NativeLiquidTabs.layout(bar) }
    @JvmStatic fun consumeTouch(bar: View, event: MotionEvent): Boolean =
        Build.VERSION.SDK_INT >= 33 && NativeLiquidTabs.consumeTouch(bar, event)
    @JvmStatic fun dispatchFromMask(mask: View, event: MotionEvent): Boolean =
        Build.VERSION.SDK_INT >= 33 && NativeLiquidTabs.dispatchFromMask(mask, event)
    @JvmStatic fun touch(bar: View, event: MotionEvent) { if (Build.VERSION.SDK_INT >= 33) NativeLiquidTabs.touch(bar, event) }
    @JvmStatic fun release(bar: View) { if (Build.VERSION.SDK_INT >= 33) NativeLiquidTabs.release(bar) }
}
