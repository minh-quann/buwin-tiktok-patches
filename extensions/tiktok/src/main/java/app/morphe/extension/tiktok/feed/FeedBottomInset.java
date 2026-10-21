/*
 * Copyright 2026 Buwin contributors
 * https://github.com/minh-quann/buwin-tiktok-patches
 */
package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.tiktok.blockauthor.FeedVisibility;

/**
 * Owns the feed's bottom safe space at the Activity content boundary. Padding participates in
 * Android's measure/layout, so tabs, pager and controls share the same reduced viewport. The
 * root paints the uncovered space; no foreground view or per-child translation is needed.
 */
final class FeedBottomInset {
    private static final Map<ViewGroup, State> STATES = new WeakHashMap<>();

    private static final class State {
        final int paddingBottom;
        final Drawable background;
        final boolean clipChildren, clipToPadding;
        final Drawable black = new ColorDrawable(Color.BLACK);

        State(ViewGroup root) {
            paddingBottom = root.getPaddingBottom();
            background = root.getBackground();
            clipChildren = root.getClipChildren();
            clipToPadding = root.getClipToPadding();
        }
    }

    private FeedBottomInset() {}

    /** Returns true when children need a fresh measure before player geometry can be used. */
    static boolean apply(Activity activity, boolean enabled) {
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return false;
        ViewGroup root = (ViewGroup) content;
        boolean feed = enabled && FeedVisibility.isOnFeed(activity);
        int reserve = feed && !keyboardVisible(root)
                && !SeeThroughBottomBar.isSystemIndicatorActive(root)
                ? SeeThroughBottomBar.getChinHeight(activity) : 0;
        return apply(root, reserve);
    }

    static boolean apply(ViewGroup root, int reserve) {
        State state = STATES.get(root);
        if (reserve <= 0) {
            if (state == null) return false;
            boolean changed = root.getPaddingBottom() != state.paddingBottom;
            root.setPadding(root.getPaddingLeft(), root.getPaddingTop(),
                    root.getPaddingRight(), state.paddingBottom);
            if (root.getBackground() == state.black) root.setBackground(state.background);
            root.setClipChildren(state.clipChildren);
            root.setClipToPadding(state.clipToPadding);
            STATES.remove(root);
            return changed;
        }
        if (state == null) {
            state = new State(root);
            STATES.put(root, state);
        }
        int bottom = state.paddingBottom + reserve;
        boolean changed = root.getPaddingBottom() != bottom;
        if (changed) {
            root.setPadding(root.getPaddingLeft(), root.getPaddingTop(), root.getPaddingRight(), bottom);
        }
        // A scaled player may unclip its cell, but must never escape the content viewport.
        root.setClipChildren(true);
        root.setClipToPadding(true);
        if (root.getBackground() != state.black) root.setBackground(state.black);
        return changed;
    }

    private static boolean keyboardVisible(View root) {
        WindowInsets insets = root.getRootWindowInsets();
        if (Build.VERSION.SDK_INT >= 30 && insets != null) {
            return insets.isVisible(WindowInsets.Type.ime());
        }
        Rect visible = new Rect();
        root.getWindowVisibleDisplayFrame(visible);
        int height = root.getRootView().getHeight();
        // Legacy resize windows expose the keyboard as a large reduction of the visible frame.
        return height > 0 && visible.height() > 0
                && height - visible.height() > Math.max(160 * root.getResources()
                .getDisplayMetrics().density, height / 4f);
    }
}
