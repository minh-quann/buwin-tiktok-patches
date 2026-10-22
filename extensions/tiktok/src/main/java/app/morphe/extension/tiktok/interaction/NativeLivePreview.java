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
    private static final java.util.Map<View, Layout> ORIGINAL = new java.util.WeakHashMap<>();
    private static final java.util.Map<View, Boolean> NATIVE_LAYOUT = new java.util.WeakHashMap<>();

    private static final class Layout {
        final FrameLayout.LayoutParams original;
        FrameLayout.LayoutParams applied;
        Layout(FrameLayout.LayoutParams params) { original = new FrameLayout.LayoutParams(params); }
        boolean owns(FrameLayout.LayoutParams params) {
            return applied != null && params.width == applied.width && params.height == applied.height
                    && params.leftMargin == applied.leftMargin && params.topMargin == applied.topMargin
                    && params.rightMargin == applied.rightMargin && params.bottomMargin == applied.bottomMargin
                    && params.gravity == applied.gravity;
        }
    }

    /** Prevent the legacy pre-draw fill from enlarging a native guest tile after onMeasure. */
    public static boolean keepsNativeLayout(View surface) {
        return NATIVE_LAYOUT.containsKey(surface);
    }
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
        if (!feedCell) {
            NATIVE_LAYOUT.remove(surface);
            return restore(surface);
        }
        NATIVE_LAYOUT.put(surface, Boolean.TRUE);
        NativeBottomBar.measureLiveCell(surface);
        if (sourceWidth <= 0 || sourceHeight <= 0 || sourceWidth > sourceHeight
                || !(surface.getLayoutParams() instanceof FrameLayout.LayoutParams)) return restore(surface);
        float[] viewport = SeeThroughStatusBar.nativeFillViewport(surface);
        if (viewport == null) return restore(surface);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) surface.getLayoutParams();
        Layout saved = ORIGINAL.get(surface);
        // TikTok can rebind a single-host surface as a guest tile without replacing the View.
        // A new native layout supersedes our saved full-screen layout.
        if (saved != null && !saved.owns(params)) {
            ORIGINAL.remove(surface);
            saved = null;
        }
        FrameLayout.LayoutParams nativeParams = saved == null ? params : saved.original;
        if (!fullScreenSlot(surface, cell, nativeParams, viewport)) return restore(surface);
        NATIVE_LAYOUT.remove(surface);
        if (saved == null) {
            saved = new Layout(params);
            ORIGINAL.put(surface, saved);
        }
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
        saved.applied = new FrameLayout.LayoutParams(params);
        SeeThroughStatusBar.releaseFillTransforms(surface);
        return ((long) fill.width << 32) | (fill.height & 0xffffffffL);
    }

    /**
     * A guest grid may contain portrait streams too. Its native slots, including a single
     * composite surface in a short container, must never inherit the whole feed viewport.
     * Inspect layout constraints before our crop, and every enclosing slot up to the cell.
     * Leave room for the normal status-bar inset and the native LIVE footer.
     */
    private static boolean fullScreenSlot(View surface, View cell,
            FrameLayout.LayoutParams params, float[] viewport) {
        if (smallSlot(params.width, params.height, viewport)) return false;
        for (View parent = surface.getParent() instanceof View ? (View) surface.getParent() : null;
                parent != null && parent != cell;
                parent = parent.getParent() instanceof View ? (View) parent.getParent() : null) {
            android.view.ViewGroup.LayoutParams slot = parent.getLayoutParams();
            if (slot != null && smallSlot(slot.width, slot.height, viewport)) return false;
            int width = parent.getMeasuredWidth(), height = parent.getMeasuredHeight();
            if (smallSlot(width, height, viewport)) return false;
        }
        return true;
    }

    private static boolean smallSlot(int width, int height, float[] viewport) {
        return (width > 0 && width < viewport[0] * 0.9f)
                || (height > 0 && height < viewport[1] * 0.75f);
    }

    private static long restore(View surface) {
        Layout saved = ORIGINAL.remove(surface);
        if (saved != null && surface.getLayoutParams() instanceof FrameLayout.LayoutParams
                && saved.owns((FrameLayout.LayoutParams) surface.getLayoutParams())) {
            surface.setLayoutParams(new FrameLayout.LayoutParams(saved.original));
        }
        return 0;
    }
}
