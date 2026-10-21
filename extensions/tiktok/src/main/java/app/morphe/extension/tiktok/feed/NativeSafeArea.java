package app.morphe.extension.tiktok.feed;

import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.shared.ResourceIdCache;
import app.morphe.extension.shared.settings.BuwinPause;
import app.morphe.extension.tiktok.settings.SettingsStatus;

/** Shared bottom-safe-area policy consumed by the original keyboard and sheet components. */
public final class NativeSafeArea {
    private static final ResourceIdCache IDS = new ResourceIdCache();
    private static final Map<View, int[]> PADDING = new WeakHashMap<>();
    private static final Map<View, int[]> HEIGHTS = new WeakHashMap<>();
    private static final Map<View, java.lang.ref.WeakReference<View>> STRIP_OWNERS = new WeakHashMap<>();
    private NativeSafeArea() { }

    public static int minimumBottom(View view) {
        if (!SettingsStatus.videoOverlaysEnabled || BuwinPause.isPaused()
                || SeeThroughBottomBar.isSystemIndicatorActive(view)) return 0;
        WindowInsets insets = view.getRootWindowInsets();
        if (Build.VERSION.SDK_INT >= 30 && insets != null
                && insets.isVisible(WindowInsets.Type.ime())) return 0;
        if (Build.VERSION.SDK_INT < 30) {
            Rect visible = new Rect();
            view.getWindowVisibleDisplayFrame(visible);
            if (visible.bottom > 0 && view.getRootView().getHeight() - visible.bottom
                    > 160 * view.getResources().getDisplayMetrics().density) return 0;
        }
        int minimum = SeeThroughBottomBar.getChinHeight(view.getContext());
        for (android.view.ViewParent p = view.getParent(); p instanceof View; p = p.getParent()) {
            View parent = (View) p;
            if (PADDING.containsKey(parent) && parent.getPaddingBottom() >= minimum) return 0;
        }
        return minimum;
    }

    private static View child(View root, String name) {
        int id = IDS.resolve(root.getResources(), root.getContext().getPackageName(), name, false);
        return id == 0 ? null : root.findViewById(id);
    }

    /** Native chat keyboard root: its message list and composer measure against the same area. */
    public static void measureChat(View root) {
        pad(root, minimumBottom(root));
    }

    /** Native comment drawer: reserve inside its painted panel, not its transparent backdrop. */
    public static void measureCommentSheet(View root) {
        View panel = child(root, "47.4.3:ilv");
        if (panel != null) pad(panel, minimumBottom(root));
    }

    /** Shared Material bottom-sheet behavior also covers sheets outside the comment screen. */
    public static void measureGenericSheet(View sheet) {
        int minimum = minimumBottom(sheet);
        for (Map.Entry<View, int[]> entry : PADDING.entrySet()) {
            View child = entry.getKey();
            if (child == sheet || child.getPaddingBottom() < minimum || minimum == 0) continue;
            for (android.view.ViewParent p = child.getParent(); p instanceof View; p = p.getParent()) {
                if (p == sheet) { minimum = 0; break; }
            }
            if (minimum == 0) break;
        }
        pad(sheet, minimum);
    }

    /** The opened-video composer and its original pager spacer share the added height. */
    public static void measureDetailInput(View root) {
        View footer = child(root, "47.4.3:f2t");
        // DetailFeedQuickDMFragment reuses the same native keyboard root, but its real
        // input row is udg (there is no f2t child in that layout).
        if (footer == null) footer = child(root, "47.4.3:udg");
        if (footer == null || footer == root) return;
        int added = pad(footer, minimumBottom(root));
        View window = root.getRootView();
        int id = IDS.resolve(root.getResources(), root.getContext().getPackageName(), "47.4.3:cnc", false);
        int columnId = IDS.resolve(root.getResources(), root.getContext().getPackageName(), "viewpager_container", false);
        if (id == 0 || columnId == 0) return;
        for (View strip : VideoOverlayHider.viewsWithId(window, id)) {
            if (!(strip.getParent() instanceof View)) continue;
            View column = (View) strip.getParent();
            if (column.getId() != columnId) continue;
            if (!column.isShown()) continue;
            // A main-feed pager can remain inflated behind an opened post in the same window.
            // Its native tab component already owns that reserve; never resize its strip here.
            boolean mainFeed = false;
            for (android.view.ViewParent p = column.getParent(); p instanceof View; p = p.getParent()) {
                if ("com.ss.android.ugc.aweme.feed.ui.seekbar.VideoSeekBarMaskView".equals(p.getClass().getName())) {
                    int tabs = IDS.resolve(root.getResources(), root.getContext().getPackageName(), "47.4.3:onp", false);
                    mainFeed = tabs != 0 && ((View) p).findViewById(tabs) != null;
                    break;
                }
            }
            if (mainFeed) continue;
            ViewGroup.LayoutParams params = strip.getLayoutParams();
            if (params == null || params.height < 0) continue;
            int[] held = HEIGHTS.get(strip);
            int base = held != null && params.height == held[1] ? held[0] : params.height;
            int height = base + (root.getVisibility() == View.VISIBLE ? added : 0);
            HEIGHTS.put(strip, new int[]{base, height});
            STRIP_OWNERS.put(strip, new java.lang.ref.WeakReference<>(root));
            if (params.height != height) {
                params.height = height;
                strip.setLayoutParams(params);
            }
        }
    }

    public static void releaseDetailInput(View root) {
        for (View strip : new java.util.ArrayList<>(STRIP_OWNERS.keySet())) {
            java.lang.ref.WeakReference<View> owner = STRIP_OWNERS.get(strip);
            if (owner == null || owner.get() != root) continue;
            int[] held = HEIGHTS.remove(strip);
            STRIP_OWNERS.remove(strip);
            ViewGroup.LayoutParams params = strip.getLayoutParams();
            if (held != null && params != null && params.height == held[1]) {
                params.height = held[0];
                strip.setLayoutParams(params);
            }
        }
    }

    /** Returns only the space we added. Existing native safe padding is never added twice. */
    static int pad(View target, int minimum) {
        int[] held = PADDING.get(target);
        int current = target.getPaddingBottom();
        int base = held != null && current == held[1] ? held[0] : current;
        int bottom = Math.max(base, minimum);
        PADDING.put(target, new int[]{base, bottom});
        if (current != bottom) target.setPadding(target.getPaddingLeft(), target.getPaddingTop(),
                target.getPaddingRight(), bottom);
        return bottom - base;
    }
}
