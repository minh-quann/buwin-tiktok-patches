/*
 * Copyright 2026 Buwin contributors
 * https://github.com/minh-quann/buwin-tiktok-patches
 */
package app.morphe.extension.tiktok.feed;

import android.view.View;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import app.morphe.extension.tiktok.blockauthor.Reflect;

/** Keeps native edge-speedup's temporary chrome hiding out of the video crop calculation. */
public final class HoldVideoViewport {
    private static final Map<View, int[]> LAST = new WeakHashMap<>();
    private static final Map<View, Hold> HOLDS = new WeakHashMap<>();
    private HoldVideoViewport() { }

    private static final class Hold {
        final WeakReference<Object> owner;
        final WeakReference<View> cell;
        final int width, height, windowWidth, windowHeight;
        boolean released;
        Hold(Object owner, View container, int[] size) {
            this.owner = new WeakReference<>(owner);
            cell = new WeakReference<>(SeeThroughStatusBar.findCellRoot(container));
            width = size[0]; height = size[1];
            windowWidth = container.getRootView().getWidth();
            windowHeight = container.getRootView().getHeight();
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

    static int[] size(View view, int width, int height) {
        View root = view.getRootView();
        Hold hold = HOLDS.get(root);
        if (hold != null) {
            View cell = hold.cell.get();
            boolean valid = hold.owner.get() != null && cell != null && cell.isAttachedToWindow()
                    // Preloaded cells also ask for a crop; they share this window's viewport
                    // and must not cancel the visible cell's hold while binding off screen.
                    && root.getWidth() == hold.windowWidth && root.getHeight() == hold.windowHeight;
            if (!valid || (hold.released && width == hold.width && height == hold.height)) {
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
