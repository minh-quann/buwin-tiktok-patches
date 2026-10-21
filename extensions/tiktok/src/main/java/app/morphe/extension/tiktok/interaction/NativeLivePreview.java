package app.morphe.extension.tiktok.interaction;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import app.morphe.extension.tiktok.feed.NativeBottomBar;
import app.morphe.extension.tiktok.feed.SeeThroughStatusBar;
import app.morphe.extension.shared.ResourceIdCache;

/** Invoked inside the LIVE SurfaceView's own onMeasure, never in an entered LIVE room. */
public final class NativeLivePreview {
    private static final ResourceIdCache IDS = new ResourceIdCache();
    private static final java.util.Map<View, FrameLayout.LayoutParams> ORIGINAL = new java.util.WeakHashMap<>();
    private NativeLivePreview() { }

    /** High word is measured width, low word measured height; zero leaves native room layout alone. */
    public static long measure(View surface, int sourceWidth, int sourceHeight) {
        // Feed cell detection also distinguishes this same SurfaceView class in a real LIVE room.
        View cell = surface;
        boolean feedCell = false;
        int cellId = IDS.resolve(surface.getResources(), surface.getContext().getPackageName(), "view_rootview", false);
        while (cell != null) {
            if (cellId != 0 && cell.getId() == cellId) { feedCell = true; break; }
            cell = cell.getParent() instanceof View ? (View) cell.getParent() : null;
        }
        if (!feedCell) return restore(surface);
        NativeBottomBar.measureLiveCell(surface);
        if (sourceWidth <= 0 || sourceHeight <= 0 || sourceWidth > sourceHeight
                || !(surface.getLayoutParams() instanceof FrameLayout.LayoutParams)) return restore(surface);
        float[] viewport = SeeThroughStatusBar.nativeFillViewport(surface);
        if (viewport == null) return restore(surface);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) surface.getLayoutParams();
        if (!ORIGINAL.containsKey(surface)) ORIGINAL.put(surface, new FrameLayout.LayoutParams(params));
        // Margins are the native layout position here, so calculate without its previous crop.
        FrameLayout.LayoutParams origin = new FrameLayout.LayoutParams(params.width, params.height);
        StatusBarVideoFill.Geometry fill = StatusBarVideoFill.geometry(sourceWidth, sourceHeight, viewport, origin);
        int left = (int) fill.x, top = (int) fill.y;
        if (params.width != fill.width || params.height != fill.height || params.leftMargin != left
                || params.topMargin != top || params.gravity != (Gravity.TOP | Gravity.LEFT)) {
            params.width = fill.width;
            params.height = fill.height;
            params.leftMargin = left;
            params.topMargin = top;
            params.gravity = Gravity.TOP | Gravity.LEFT;
            surface.setLayoutParams(params);
        }
        if (surface.getScaleX() != 1) surface.setScaleX(1);
        if (surface.getScaleY() != 1) surface.setScaleY(1);
        if (surface.getTranslationX() != 0) surface.setTranslationX(0);
        if (surface.getTranslationY() != 0) surface.setTranslationY(0);
        SeeThroughStatusBar.releaseFillTransforms(surface);
        return ((long) fill.width << 32) | (fill.height & 0xffffffffL);
    }

    private static long restore(View surface) {
        FrameLayout.LayoutParams original = ORIGINAL.remove(surface);
        if (original != null) surface.setLayoutParams(original);
        return 0;
    }
}
