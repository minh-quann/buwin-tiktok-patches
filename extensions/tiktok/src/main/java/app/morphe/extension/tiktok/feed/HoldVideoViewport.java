/*
 * Copyright 2026 Buwin contributors
 * https://github.com/minh-quann/buwin-tiktok-patches
 */
package app.morphe.extension.tiktok.feed;

import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import app.morphe.extension.tiktok.blockauthor.Reflect;

/** Keeps gesture-driven chrome hiding out of the video's base crop calculation. */
public final class HoldVideoViewport {
    private static final Map<View, int[]> LAST = new WeakHashMap<>();
    private static final Map<View, int[]> GUIDE_MARGINS = new WeakHashMap<>();
    private static final Map<View, Hold> HOLDS = new WeakHashMap<>();
    private HoldVideoViewport() { }

    private static final class Hold {
        final WeakReference<Object> owner;
        final WeakReference<View> cell;
        final int width, height, windowWidth, windowHeight;
        boolean released;
        boolean pinch;
        Hold(Object owner, View container, int[] size) {
            this.owner = new WeakReference<>(owner);
            cell = new WeakReference<>(SeeThroughStatusBar.findCellRoot(container));
            width = size[0]; height = size[1];
            windowWidth = container.getRootView().getWidth();
            windowHeight = container.getRootView().getHeight();
        }
    }

    /** A few physical pixels below the video edge; preserve native size and animations. */
    public static void positionGuide(View guide) {
        if (!(guide.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) guide.getLayoutParams();
        int[] held = GUIDE_MARGINS.get(guide);
        int top = held != null && params.topMargin == held[0] + held[2] ? held[0] : params.topMargin;
        int bottom = held != null && params.bottomMargin == held[1] - held[2] ? held[1] : params.bottomMargin;
        int shift = SeeThroughStatusBar.wantsNativeFill(guide) ? 12 : 0;
        GUIDE_MARGINS.put(guide, new int[]{top, bottom, shift});
        if (params.topMargin != top + shift || params.bottomMargin != bottom - shift) {
            params.topMargin = top + shift;
            params.bottomMargin = bottom - shift;
            guide.setLayoutParams(params);
        }
    }

    /** Hooked immediately after the native gesture enters speed-up, before it hides controls. */
    public static void begin(Object owner) {
        Object value = Reflect.invoke(owner, "getContainerView");
        if (!(value instanceof View)) return;
        View container = (View) value;
        if (!SeeThroughStatusBar.wantsNativeFill(container)) return;
        View root = container.getRootView();
        // Read now, while the original bottom tabs/clear-display state are still in place.
        float[] viewport = SeeThroughStatusBar.nativeFillViewport(container);
        int[] size = viewport == null ? LAST.get(root) : new int[]{(int) viewport[0], (int) viewport[1]};
        if (size != null) HOLDS.put(root, new Hold(owner, container, size));
    }

    /** Release does not unfreeze until the native chrome has actually returned to its layout. */
    public static void end(Object owner) {
        for (Hold hold : HOLDS.values()) {
            if (hold.owner.get() == owner) hold.released = true;
        }
    }

    /** PinchComponent calls this before its clean-UI event resizes the native pager. */
    public static void pinch(Object owner, boolean clearing) {
        if (!clearing) { end(owner); return; }
        begin(owner);
        for (Hold hold : HOLDS.values()) {
            if (hold.owner.get() == owner) hold.pinch = true;
        }
    }

    /** Clip the unscaled native cell, not the zoomed texture: the bottom row stays outside zoom. */
    public static void clipCell(View cell, Canvas canvas) {
        Hold hold = HOLDS.get(cell.getRootView());
        if (hold != null && hold.pinch && hold.cell.get() == cell && isHolding(cell))
            canvas.clipRect(0, 0, hold.width, hold.height);
    }

    /** Scene identity survives the native hold temporarily hiding its DM composer. */
    static boolean hasPendingHold(View view) {
        if (view == null) return false;
        View root = view.getRootView();
        Hold hold = HOLDS.get(root);
        View cell = hold == null ? null : hold.cell.get();
        return hold != null && hold.owner.get() != null && cell != null && cell.isAttachedToWindow()
                && root.getWidth() == hold.windowWidth && root.getHeight() == hold.windowHeight;
    }

    /** Also covers the release frame while native chrome/layout is still being restored. */
    static boolean isHolding(View view) {
        if (view == null) return false;
        View root = view.getRootView();
        Hold hold = HOLDS.get(root);
        if (hold == null) return false;
        View cell = hold.cell.get();
        if (hold.owner.get() == null || cell == null || !cell.isAttachedToWindow()
                || !SeeThroughStatusBar.wantsNativeFill(cell)
                || root.getWidth() != hold.windowWidth || root.getHeight() != hold.windowHeight) {
            HOLDS.remove(root);
            return false;
        }
        return true;
    }

    static int[] size(View view, int width, int height) {
        View root = view.getRootView();
        if (isHolding(view)) {
            Hold hold = HOLDS.get(root);
            if (hold.released && width == hold.width && height == hold.height
                    && NativeBottomBar.holdLayoutRestored(hold.cell.get())) {
                HOLDS.remove(root);
            } else {
                return new int[]{hold.width, hold.height};
            }
        }
        int[] size = new int[]{width, height};
        LAST.put(root, size);
        return size;
    }
}
