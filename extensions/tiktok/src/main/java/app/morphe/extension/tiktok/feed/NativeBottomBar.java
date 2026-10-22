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
    private static final Map<View, int[]> MARGINS = new WeakHashMap<>();
    private static final Map<View, int[]> SEARCH_MARGINS = new WeakHashMap<>();
    private static final Map<View, int[]> SEEK_MARGINS = new WeakHashMap<>();
    private static final Map<View, Drawable> CLEAR_BACKGROUNDS = new WeakHashMap<>();
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
        boolean active = enabled() && (bar.isShown() || HoldVideoViewport.isHolding(bar));
        boolean changed = false;
        // Sibling cells can reuse their measure cache when only the tab bar changes. Restore
        // or reapply their owned margins before this frame, including a navigation-mode change.
        for (View widgets : new java.util.ArrayList<>(MARGINS.keySet())) {
            if (widgets.getRootView() == bar.getRootView()) changed |= moveMargins(widgets, active ? reserve(bar) : 0);
        }
        if (!active) {
            if (bar.getParent() instanceof View) changed |= measureFeed((View) bar.getParent());
            return !changed;
        }
        blackBackground(bar);
        changed |= removeDivider(bar);
        if (bar.getParent() instanceof View) changed |= measureFeed((View) bar.getParent());
        return !changed;
    }

    private static View tabs(View view) {
        int id = IDS.resolve(view.getResources(), view.getContext().getPackageName(), "47.4.3:onp", false);
        return id == 0 ? null : view.getRootView().findViewById(id);
    }

    private static int reserve(View bar) {
        return enabled() && bar != null && (bar.getVisibility() == View.VISIBLE || HoldVideoViewport.isHolding(bar))
                && !SeeThroughBottomBar.isSystemIndicatorActive(bar)
                ? SeeThroughBottomBar.getChinHeight(bar.getContext()) : 0;
    }

    /** Runs in the original feed cell's onMeasure, including cells preloaded off screen. */
    public static void measureCell(View cell) {
        int id = IDS.resolve(cell.getResources(), cell.getContext().getPackageName(), "widget_container", false);
        if (id == 0) return;
        View widgets = cell.findViewById(id);
        if (widgets != null) {
            moveMargins(widgets, reserve(tabs(cell)));
            keepDetailSearchAboveFooter(cell, widgets);
        }
    }

    /**
     * The native widget root extends below the pager by a negative bottom margin. Its search
     * row normally cancels that offset with its own margin, but rebinds can reset the row to
     * zero. Restore that constraint before measuring: native siblings anchored above the row
     * (caption, subtitles and action rail) then move with it, including preloaded search cells.
     */
    private static void keepDetailSearchAboveFooter(View cell, View widgets) {
        int id = IDS.resolve(cell.getResources(), cell.getContext().getPackageName(), "47.4.3:ljb", false);
        View search = id == 0 ? null : widgets.findViewById(id);
        if (search == null || search.getParent() != widgets
                || !(search.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)
                || !(widgets.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) search.getLayoutParams();
        int[] held = SEARCH_MARGINS.get(search);
        int base = held != null && params.bottomMargin == held[1] ? held[0] : params.bottomMargin;
        boolean active = enabled() && app.morphe.extension.tiktok.blockauthor.FeedVisibility
                .isDetailPager(SeeThroughStatusBar.activityOf(cell)) && search.getVisibility() == View.VISIBLE;
        int overflow = Math.max(0, -((ViewGroup.MarginLayoutParams) widgets.getLayoutParams()).bottomMargin);
        int bottom = active ? Math.max(base, overflow + 1) : base;
        if (active) SEARCH_MARGINS.put(search, new int[]{base, bottom});
        else SEARCH_MARGINS.remove(search);
        if (params.bottomMargin != bottom) {
            params.bottomMargin = bottom;
            search.setLayoutParams(params);
        }
    }

    /** LIVE uses an ordinary FrameLayout cell and its own commerce/tips constraint group. */
    public static void measureLiveCell(View surface) {
        View cell = SeeThroughStatusBar.findCellRoot(surface);
        if (cell == null) return;
        int lift = reserve(tabs(cell));
        // The product card is a sibling of the tips/user group, not a child of it.
        for (String name : new String[]{"47.4.3:o3v", "47.4.3:stb"}) {
            int id = IDS.resolve(cell.getResources(), cell.getContext().getPackageName(), name, false);
            View controls = id == 0 ? null : cell.findViewById(id);
            if (controls != null) moveMargins(controls, lift);
        }
    }

    /** Move the native widget layout without changing its measured size or adding an overlay. */
    private static boolean moveMargins(View view, int lift) {
        if (!(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return false;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        int[] held = MARGINS.get(view);
        int top, bottom;
        if (held != null && HoldVideoViewport.isHolding(view)) {
            // Keep the pre-gesture native margins. A taller parent must not push a bottom-
            // anchored caption down when the host temporarily removes its bottom strip.
            top = held[0]; bottom = held[1];
            lift = held[4] + parentHeight(view) - held[3];
            held[2] = lift;
        } else {
            // A normal host rebind may replace either base margin; never add our lift twice.
            top = held != null && params.topMargin == held[0] - held[2] ? held[0] : params.topMargin;
            bottom = held != null && params.bottomMargin == held[1] + held[2] ? held[1] : params.bottomMargin;
            MARGINS.put(view, new int[]{top, bottom, lift, parentHeight(view), lift});
        }
        if (params.topMargin != top - lift || params.bottomMargin != bottom + lift) {
            params.topMargin = top - lift;
            params.bottomMargin = bottom + lift;
            view.setLayoutParams(params);
            return true;
        }
        return false;
    }

    /** Runs before the native seek mask measures its children, not after a visible frame. */
    public static boolean measureFeed(View mask) {
        View clearBar = prepareClearBar(mask);
        NativeSafeArea.measureDetailPage(mask);
        if (!(mask instanceof ViewGroup)) return false;
        View bar = tabs(mask);
        if ((bar == null || bar.getParent() != mask) && clearBar == null) return false;
        int id = IDS.resolve(mask.getResources(), mask.getContext().getPackageName(), "video_seek_bar", false);
        View seek = id == 0 ? null : mask.findViewById(id);
        if (seek == null || !(seek.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return false;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) seek.getLayoutParams();
        int[] held = SEEK_MARGINS.get(seek);
        int base = held != null && params.bottomMargin == held[1] ? held[0] : params.bottomMargin;
        int seekHeight = params.height > 0 ? params.height : seek.getMeasuredHeight();
        int margin;
        if (clearBar != null && clearBar.getVisibility() == View.VISIBLE && seek.getParent() == mask) {
            // Clear-mode's original row is the painted boundary while tabs are GONE. It
            // must also work on a fresh bind with no cached seek margins or gesture owner.
            ViewGroup.LayoutParams rowParams = clearBar.getLayoutParams();
            int rowHeight = rowParams != null && rowParams.height > 0
                    ? rowParams.height : clearBar.getMeasuredHeight();
            if (rowHeight <= 0) return false;
            int rowBottom = rowParams instanceof ViewGroup.MarginLayoutParams
                    ? ((ViewGroup.MarginLayoutParams) rowParams).bottomMargin : 0;
            // Both bottom-anchored siblings already inherit the mask's bottom padding.
            margin = rowHeight + rowBottom
                    - Math.round(clearBar.getTranslationY()) - seekHeight / 2 + 1;
            SEEK_MARGINS.put(seek, new int[]{base, margin, margin + seekHeight / 2, parentHeight(seek)});
        } else if (enabled() && held != null && HoldVideoViewport.isHolding(mask)) {
            // Preserve the TRACK centre even if the native drag touch target changes height.
            margin = held[2] - seekHeight / 2 + parentHeight(seek) - held[3];
            held[1] = margin;
        } else {
            if (!enabled() || bar == null || bar.getVisibility() != View.VISIBLE) {
                SEEK_MARGINS.remove(seek);
                if (held == null || params.bottomMargin != held[1] || params.bottomMargin == base) return false;
                params.bottomMargin = base;
                seek.setLayoutParams(params);
                return true;
            }
            int row = bar.getLayoutParams().height;
            if (row <= 0) return false;
            // The track stays one pixel above the native bottom-tabs boundary.
            margin = row + reserve(bar) - seekHeight / 2 + 1;
            SEEK_MARGINS.put(seek, new int[]{base, margin, margin + seekHeight / 2, parentHeight(seek)});
        }
        if (params.bottomMargin == margin) return false;
        params.bottomMargin = margin;
        seek.setLayoutParams(params);
        return true;
    }

    /** TikTok replaces its tabs with this original clear-mode row during pinch zoom. */
    private static View prepareClearBar(View mask) {
        int id = IDS.resolve(mask.getResources(), mask.getContext().getPackageName(), "47.4.3:l6f", false);
        View row = id == 0 ? null : mask.findViewById(id);
        if (row == null || row.getParent() != mask) return null;
        if (enabled() && SeeThroughStatusBar.wantsNativeFill(mask)) {
            if (!CLEAR_BACKGROUNDS.containsKey(row)) CLEAR_BACKGROUNDS.put(row, row.getBackground());
            blackBackground(row);
            return row;
        } else if (CLEAR_BACKGROUNDS.containsKey(row)) {
            Drawable before = CLEAR_BACKGROUNDS.remove(row);
            Drawable now = row.getBackground();
            if (now instanceof ColorDrawable && ((ColorDrawable) now).getColor() == Color.BLACK)
                row.setBackground(before);
        }
        return null;
    }

    private static int parentHeight(View view) {
        if (!(view.getParent() instanceof View)) return 0;
        View parent = (View) view.getParent();
        return parent.getMeasuredHeight() > 0 ? parent.getMeasuredHeight() : parent.getHeight();
    }

    /** Do not unlock on the frame tabs return before the widget/seek parents finish layout. */
    static boolean holdLayoutRestored(View cell) {
        if (cell == null) return true;
        for (Map.Entry<View, int[]> entry : MARGINS.entrySet()) {
            View view = entry.getKey();
            View at = view;
            while (at != null && at != cell) at = at.getParent() instanceof View ? (View) at.getParent() : null;
            if (at == cell && view.isAttachedToWindow() && parentHeight(view) != entry.getValue()[3]) return false;
        }
        for (Map.Entry<View, int[]> entry : SEEK_MARGINS.entrySet()) {
            View view = entry.getKey();
            if (view.getRootView() == cell.getRootView() && view.isAttachedToWindow()
                    && parentHeight(view) != entry.getValue()[3]) return false;
        }
        return true;
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
