/* Copyright 2026 Buwin contributors. */
package app.morphe.extension.tiktok.feed;

import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import app.morphe.extension.shared.ResourceIdCache;
import java.util.Map;
import java.util.WeakHashMap;
import app.morphe.extension.shared.settings.BuwinPause;
import app.morphe.extension.tiktok.glass.NativeGlassRenderer;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;

/** Process-latched native bottom-bar style: changing the preference requires a restart. */
public final class NativeGlassBar {
    private static String launchStyle;
    private static final Map<View, int[]> PADDING = new WeakHashMap<>();
    private static final ResourceIdCache FOOTER_IDS = new ResourceIdCache();
    private static final Map<View, Footer> FOOTERS = new WeakHashMap<>();
    private static final Map<View, Integer> FOOTER_PASSES = new WeakHashMap<>();
    private static final Map<View, Boolean> OTHER_TABS = new WeakHashMap<>();
    private static final Map<View, WeakReference<View>> FRIEND_LISTS = new WeakHashMap<>();
    private static final Map<View, Integer> FRIEND_GAPS = new WeakHashMap<>();
    private static final class Footer {
        int height;
        final float alpha;
        Footer(View view) { height = view.getLayoutParams().height; alpha = view.getAlpha(); }
    }
    private NativeGlassBar() { }

    public static String style() {
        if (launchStyle == null) {
            String selected = Settings.BOTTOM_BAR_STYLE.get();
            launchStyle = "original".equals(selected) || "glass".equals(selected) ? selected : "black";
        }
        return launchStyle;
    }

    static boolean original() { return "original".equals(style()); }

    public static boolean isGlass() {
        return Build.VERSION.SDK_INT >= 33 && "glass".equals(style())
                && SettingsStatus.videoOverlaysEnabled && !BuwinPause.isPaused();
    }

    static int heightSpec(View bar, int spec, int chin) {
        // MEBIECO: 8dp top + 56dp capsule + 24dp bottom. Its bottom gap already
        // contains the gesture chin; adding the system reserve again raises the bar.
        int height = Math.round(88 * bar.getResources().getDisplayMetrics().density);
        return View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.getMode(spec));
    }

    public static void touch(View bar, android.view.MotionEvent event) {
        if (isGlass()) NativeGlassRenderer.touch(bar, event);
    }
    public static boolean consumeTouch(View bar, android.view.MotionEvent event) {
        return isGlass() && NativeGlassRenderer.consumeTouch(bar, event);
    }
    public static boolean dispatchFromMask(View mask, android.view.MotionEvent event) {
        return isGlass() && NativeGlassRenderer.dispatchFromMask(mask, event);
    }
    static boolean fade(View bar, int percent) {
        return isGlass() && NativeGlassRenderer.fade(bar, percent);
    }
    public static int nativeChildCount(android.view.ViewGroup bar, int count) {
        return isGlass() ? NativeGlassRenderer.nativeChildCount(bar, count) : count;
    }

    static int bottomGap(View bar) {
        return isGlass() ? Math.round(24 * bar.getResources().getDisplayMetrics().density) : 0;
    }

    static int reserve(View bar) {
        return Math.round(88 * bar.getResources().getDisplayMetrics().density);
    }

    static void prepare(View bar) {
        if (bar == null) return;
        int[] own = PADDING.get(bar);
        if (isGlass()) {
            if (own == null) {
                own = new int[]{bar.getPaddingLeft(), bar.getPaddingTop(), bar.getPaddingRight()};
                PADDING.put(bar, own);
            }
            float density = bar.getResources().getDisplayMetrics().density;
            int side = Math.round(16 * density), top = Math.round(8 * density);
            if (bar.getPaddingLeft() != own[0] + side || bar.getPaddingTop() != own[1] + top
                    || bar.getPaddingRight() != own[2] + side) {
                bar.setPadding(own[0] + side, own[1] + top, own[2] + side, bar.getPaddingBottom());
            }
            if (!(bar.getBackground() instanceof android.graphics.drawable.ColorDrawable)
                    || ((android.graphics.drawable.ColorDrawable) bar.getBackground()).getColor() != Color.TRANSPARENT)
                bar.setBackgroundColor(Color.TRANSPARENT);
            NativeGlassRenderer.attach(bar);
        } else {
            if (own != null) {
                bar.setPadding(own[0], own[1], own[2], bar.getPaddingBottom());
                PADDING.remove(bar);
            }
            NativeGlassRenderer.release(bar);
            if (!original()) bar.setBackgroundColor(Color.BLACK);
        }
    }

    static void draw(View bar, Canvas canvas, int chin) {
        boolean feed = isHome(bar);
        if (feed) app.morphe.extension.tiktok.interaction.NativeVideoAmbient.draw(bar, canvas);
        NativeGlassRenderer.draw(bar, canvas, chin, feed);
    }

    /** Ambient belongs only to the selected Home tab, never another tab's feed. */
    public static boolean isHome(View bar) {
        View home = app.morphe.extension.tiktok.blockauthor.FeedVisibility.homeTabView(SeeThroughStatusBar.activityOf(bar));
        return home != null && home.isSelected() && home.isShown();
    }

    /** Clear the actual native footer slots on other tabs, not the Home background. */
    static boolean clearPageFooters(View mask) {
        View bar = NativeBottomBar.tabs(mask);
        if (!(mask instanceof ViewGroup) || !(bar instanceof ViewGroup) || bar.getParent() != mask) return false;
        View home = app.morphe.extension.tiktok.blockauthor.FeedVisibility.homeTabView(SeeThroughStatusBar.activityOf(bar));
        boolean selectedOther = false;
        int selectedIndex = -1;
        if (home != null && !home.isSelected()) {
            ViewGroup tabs = (ViewGroup) bar;
            for (int i = 0; i < tabs.getChildCount(); i++) if (tabs.getChildAt(i).isSelected()) { selectedOther = true; selectedIndex = i; }
        }
        boolean clear = false;
        if (isGlass()) {
            if (home != null && home.isSelected()) OTHER_TABS.put(mask, false);
            else if (selectedOther) OTHER_TABS.put(mask, true);
            // Selection can be briefly empty during a native tab transaction. Keep the
            // last confirmed mode; only an explicit Home selection restores its slots.
            clear = Boolean.TRUE.equals(OTHER_TABS.get(mask));
        } else OTHER_TABS.remove(mask);
        boolean changed = clearFriendsGap(mask, clear);
        if (!clear) {
            FOOTER_PASSES.remove(mask);
            for (View view : new java.util.ArrayList<>(FOOTERS.keySet())) {
                boolean belongs = false;
                for (android.view.ViewParent p = view.getParent(); p instanceof View; p = p.getParent())
                    if (p == mask) { belongs = true; break; }
                if (!belongs) continue;
                Footer before = FOOTERS.remove(view);
                ViewGroup.LayoutParams params = view.getLayoutParams();
                if (params != null && params.height == 0) {
                    params.height = before.height; view.setLayoutParams(params); changed = true;
                }
                if (view.getAlpha() == 0f) view.setAlpha(before.alpha);
            }
            return changed;
        }
        // Children can write layout params after the mask's onMeasure, then clear the
        // parent's layout-request flag before pre-draw. Recheck owned slots even when
        // the discovery cache is still valid.
        for (View view : new java.util.ArrayList<>(FOOTERS.keySet())) {
            boolean belongs = false;
            for (android.view.ViewParent parent = view.getParent(); parent instanceof View; parent = parent.getParent())
                if (parent == mask) { belongs = true; break; }
            if (!belongs) continue;
            Footer before = FOOTERS.get(view);
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params != null && params.height != 0) {
                if (params.height > 0) before.height = params.height;
                params.height = 0; view.setLayoutParams(params); changed = true;
            }
            if (view.getAlpha() != 0f) view.setAlpha(0f);
        }
        Integer pass = FOOTER_PASSES.get(mask);
        if (pass != null && pass == selectedIndex && !mask.isLayoutRequested()) return changed;
        FOOTER_PASSES.put(mask, selectedIndex);
        int wrapper = FOOTER_IDS.resolve(mask.getResources(), mask.getContext().getPackageName(), "47.4.3:wr4", false);
        int slot = FOOTER_IDS.resolve(mask.getResources(), mask.getContext().getPackageName(), "47.4.3:ch4", false);
        if (wrapper == 0 && slot == 0) return changed;
        // Footer slots sit near page roots. Do not walk every feed cell or list item.
        java.util.ArrayDeque<View> queue = new java.util.ArrayDeque<>(); queue.add(mask);
        for (int depth = 0; depth < 8 && !queue.isEmpty(); depth++) {
            int count = queue.size();
            while (count-- > 0) {
                View view = queue.removeFirst();
                if (view == bar) continue;
                if ((wrapper != 0 && view.getId() == wrapper) || (slot != 0 && view.getId() == slot)) {
                    ViewGroup.LayoutParams params = view.getLayoutParams();
                    if (params == null) continue;
                    Footer before = FOOTERS.get(view);
                    if (before == null && params.height <= 0) continue;
                    if (before == null) { before = new Footer(view); FOOTERS.put(view, before); }
                    else if (params.height > 0) before.height = params.height;
                    if (view.getAlpha() != 0f) view.setAlpha(0f);
                    if (params.height != 0) { params.height = 0; view.setLayoutParams(params); changed = true; }
                } else if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) queue.add(group.getChildAt(i));
                }
            }
        }
        return changed;
    }

    /** Friends' recommendation list has its own 58dp bottom margin, independently of
     * wr4. Let its native background/content continue behind the capsule; retain its
     * existing scroll padding so the final follow button can still scroll clear of it.
     */
    private static boolean clearFriendsGap(View mask, boolean clear) {
        if (!clear && FRIEND_GAPS.isEmpty()) return false;
        View list = FRIEND_LISTS.containsKey(mask) ? FRIEND_LISTS.get(mask).get() : null;
        if (list == null || list.getRootView() != mask.getRootView() || list.getParent() == null) {
            int id = FOOTER_IDS.resolve(mask.getResources(), mask.getContext().getPackageName(), "47.4.3:hj6", false);
            list = id == 0 ? null : mask.findViewById(id);
            if (list != null) FRIEND_LISTS.put(mask, new WeakReference<>(list));
        }
        if (list == null || !(list.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return false;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) list.getLayoutParams();
        Integer previous = FRIEND_GAPS.get(list);
        if (clear && params.height == ViewGroup.LayoutParams.MATCH_PARENT) {
            if (params.bottomMargin > 0) FRIEND_GAPS.put(list, params.bottomMargin);
            if (params.bottomMargin == 0) return false;
            params.bottomMargin = 0; list.setLayoutParams(params); return true;
        }
        if (previous == null) return false;
        FRIEND_GAPS.remove(list);
        if (params.bottomMargin != 0) return false;
        params.bottomMargin = previous; list.setLayoutParams(params); return true;
    }

    /** Only a main-feed bar in this view's own ancestry may expand its stable viewport. */
    public static int viewportHeight(View video, int fallback) {
        if (!isGlass()) return fallback;
        View bar = NativeBottomBar.tabs(video);
        if (bar == null || !(bar.getParent() instanceof View) || !bar.isShown()) return fallback;
        View host = (View) bar.getParent();
        for (android.view.ViewParent p = video.getParent(); p instanceof View; p = p.getParent()) {
            if (p == host) {
                int height = host.getMeasuredHeight();
                return height > 0 ? Math.max(fallback, height) : fallback;
            }
        }
        return fallback;
    }

    public static void release(View bar) { NativeGlassRenderer.release(bar); }

    static void resetForTests() { launchStyle = null; }
}
