/*
 * Copyright 2026 Buwin contributors
 * https://github.com/minh-quann/buwin-tiktok-patches
 */
package app.morphe.extension.tiktok.feed;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.shared.ResourceIdCache;
import app.morphe.extension.shared.settings.BuwinPause;
import app.morphe.extension.tiktok.settings.SettingsStatus;

/** Called only from the original bottom-tabs component's onMeasure/dispatchDraw overrides. */
public final class NativeBottomBar {
    private static final Map<View, Integer> BASE_PADDING = new WeakHashMap<>();
    private static final ResourceIdCache IDS = new ResourceIdCache();
    private static final Map<View, WeakReference<View>> DIVIDERS = new WeakHashMap<>();
    private NativeBottomBar() {}

    private static boolean enabled() {
        return SettingsStatus.videoOverlaysEnabled && !BuwinPause.isPaused();
    }

    /** The incoming spec is TikTok's native row height, never our previous measured height. */
    public static int heightSpec(View bar, int spec) {
        Integer padding = BASE_PADDING.get(bar);
        if (!enabled()) {
            if (padding != null) {
                if (bar.getPaddingBottom() != padding) bar.setPadding(bar.getPaddingLeft(),
                        bar.getPaddingTop(), bar.getPaddingRight(), padding);
                BASE_PADDING.remove(bar);
            }
            return spec;
        }
        if (padding == null) {
            padding = bar.getPaddingBottom();
            BASE_PADDING.put(bar, padding);
        }
        int chin = SeeThroughBottomBar.isSystemIndicatorActive(bar)
                ? 0 : SeeThroughBottomBar.getChinHeight(bar.getContext());
        int bottom = padding + chin;
        if (bar.getPaddingBottom() != bottom) bar.setPadding(bar.getPaddingLeft(),
                bar.getPaddingTop(), bar.getPaddingRight(), bottom);
        blackBackground(bar);
        removeDivider(bar);
        SeeThroughBottomBar.adjustBottomBarChin(bar);
        int mode = View.MeasureSpec.getMode(spec);
        // UNSPECIFIED already measures padding in LinearLayout; EXACTLY/AT_MOST need extra room.
        return mode == View.MeasureSpec.UNSPECIFIED ? spec
                : View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(spec) + chin, mode);
    }

    /** Native component background and reserved space are painted as one continuous rectangle. */
    public static void drawBackground(View bar, Canvas canvas) {
        if (!enabled()) return;
        blackBackground(bar);
        int save = canvas.save();
        canvas.clipRect(0, 0, bar.getWidth(), bar.getHeight());
        canvas.drawColor(Color.BLACK);
        canvas.restoreToCount(save);
    }

    private static void blackBackground(View bar) {
        Drawable background = bar.getBackground();
        if (!(background instanceof ColorDrawable)
                || ((ColorDrawable) background).getColor() != Color.BLACK) bar.setBackgroundColor(Color.BLACK);
    }

    /** Registered by the native component itself, so host rebinds cannot flash the separator. */
    public static boolean prepareFrame(View bar) {
        if (!enabled() || !bar.isShown()) return true;
        blackBackground(bar);
        return !removeDivider(bar);
    }

    private static boolean removeDivider(View bar) {
        int id = IDS.resolve(bar.getResources(), bar.getContext().getPackageName(), "47.4.3:cnj", false);
        if (id == 0) return false;
        View root = bar.getRootView();
        WeakReference<View> held = DIVIDERS.get(bar);
        View divider = held == null ? null : held.get();
        if (divider == null || divider.getRootView() != root) {
            divider = root.findViewById(id);
            if (divider == null) return false;
            DIVIDERS.put(bar, new WeakReference<>(divider));
        }
        boolean changed = divider.getVisibility() != View.GONE;
        if (changed) divider.setVisibility(View.GONE);
        ViewGroup.LayoutParams params = divider.getLayoutParams();
        if (params != null && params.height != 0) {
            params.height = 0;
            divider.setLayoutParams(params);
            changed = true;
        }
        return changed;
    }
}
