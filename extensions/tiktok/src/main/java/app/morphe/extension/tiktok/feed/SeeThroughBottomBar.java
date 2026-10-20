/*
 * Copyright 2026 Buwin contributors
 * https://github.com/minh-quann/buwin-tiktok-patches
 */
package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import app.morphe.extension.tiktok.settings.preference.SettingsUi;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The state behind See-through bottom bar (#145). TikTok's bottom tab bar paints its own
 * background, and the room under the picture is kept for it by a spacer in each cell or, on the
 * S22, by a strip the bar's height under the main feed's pager, so the picture stops above the
 * bar. VideoOverlayHider collapses those through its ordinary hide path, lifts each cell's widget
 * layer by what the pager gained, and hands this class the bar: it swaps the bar's background for
 * a clear one and puts the original back, and it judges whether the collapse still pushed the
 * caption or the buttons under the bar.
 */
final class SeeThroughBottomBar {
    /** How far an overlay may reach into the bar, in pixels, before it counts as under it. */
    private static final float SLACK_PX = 2f;

    /** The clear stand-in, told apart from anything TikTok sets by its class. */
    private static final class ClearBackground extends ColorDrawable {
        ClearBackground() {
            super(Color.TRANSPARENT);
        }
    }

    /** The floating pill's fill: dark and see-through, so the video shows behind the tabs. */
    static final int PILL_COLOR = 0x99000000;
    /** The pill's hairline, light enough to find the pill's edge over a dark video. */
    static final int PILL_EDGE_COLOR = 0x40FFFFFF;
    /** How far the pill stays in from the bar's sides and bottom, and from its top, in dp. */
    static final int PILL_SIDE_DP = 12, PILL_BOTTOM_DP = 6, PILL_TOP_DP = 2;

    /**
     * Floating bottom bar (#124): a rounded see-through pill drawn inside the bar, in from its
     * sides and bottom, so the tabs float over the video. Only the background changes: the bar
     * keeps its place and size, so every tab is where TikTok put it and takes its taps.
     */
    static final class FloatingBackground extends InsetDrawable {
        FloatingBackground(float density) {
            super(pill(density), Math.round(PILL_SIDE_DP * density), Math.round(PILL_TOP_DP * density),
                    Math.round(PILL_SIDE_DP * density), Math.round(PILL_BOTTOM_DP * density));
        }

        /**
         * No padding: a background's padding becomes the view's, which would squeeze the tabs and
         * outlive the pill when TikTok's background comes back. The insets only place the drawing.
         */
        @Override
        public boolean getPadding(Rect padding) {
            padding.set(0, 0, 0, 0);
            return false;
        }

        private static GradientDrawable pill(float density) {
            GradientDrawable pill = new GradientDrawable();
            pill.setShape(GradientDrawable.RECTANGLE);
            pill.setCornerRadius(SettingsUi.RADIUS_ROUND_ENDS * density);
            pill.setColor(PILL_COLOR);
            pill.setStroke(Math.max(1, Math.round(density)), PILL_EDGE_COLOR);
            return pill;
        }
    }

    /** Bars whose background this class replaced, with the drawable each had. */
    private static final Map<View, Drawable> BACKGROUNDS = new WeakHashMap<>();

    /** Original heights and bottom paddings of views adjusted for chin. */
    private static final Map<View, Integer> ORIGINAL_BAR_HEIGHT = new WeakHashMap<>();
    private static final Map<View, Integer> ORIGINAL_BAR_PADDING_BOTTOM = new WeakHashMap<>();

    /**
     * Overlays (the caption frame, the buttons' column) measured with the spacer in place: true
     * when one sat clear of the bar then. Only an overlay that was fine before the collapse can
     * be blamed for being under the bar after it.
     */
    private static final Map<View, Boolean> CLEAR_BEFORE = new WeakHashMap<>();

    /** Set once a collapse put an overlay under the bar; the spacer then stays as TikTok has it. */
    private static boolean collapseRefused;

    private SeeThroughBottomBar() {
    }

    /**
     * Checks if the device's navigation indicator / navigation bar is active (visible).
     * Returns true if indicator is shown (gesture indicator or 3-button nav enabled).
     * Returns false if indicator is hidden (user disabled gesture indicator / immersive mode).
     */
    static boolean isSystemIndicatorActive(View view) {
        if (view == null) return false;
        Context ctx = view.getContext();
        try {
            int immersive = android.provider.Settings.Secure.getInt(
                    ctx.getContentResolver(), "secure_gesture_navigation_immersive", -1);
            if (immersive == 1) return false;
            if (immersive == 0) return true;
        } catch (Throwable ignored) {
        }
        WindowInsets insets = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            insets = view.getRootWindowInsets();
        }
        if (insets == null) {
            Activity act = SeeThroughStatusBar.activityOf(view);
            if (act != null && act.getWindow() != null && act.getWindow().getDecorView() != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    insets = act.getWindow().getDecorView().getRootWindowInsets();
                }
            }
        }
        if (insets != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return insets.isVisible(WindowInsets.Type.navigationBars())
                        && insets.getInsets(WindowInsets.Type.navigationBars()).bottom > 0;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return insets.getSystemWindowInsetBottom() > 0;
            }
        }
        return false;
    }

    /**
     * Returns the height (in pixels) of the navigation bar chin to reserve when the user
     * has disabled the system gesture indicator (~21-22dp).
     */
    static int getChinHeight(Context context) {
        if (context == null) return 0;
        float density = context.getResources().getDisplayMetrics().density;
        int resId = context.getResources().getIdentifier("navigation_bar_gesture_height", "dimen", "android");
        if (resId <= 0) {
            resId = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        }
        if (resId > 0) {
            try {
                int navH = context.getResources().getDimensionPixelSize(resId);
                if (navH > 0 && navH <= Math.round(48 * density)) {
                    return navH;
                }
            } catch (Exception ignored) {
            }
        }
        return Math.round(21 * density);
    }

    /**
     * Adjusts the bottom bar's height and bottom padding:
     * - When system indicator is disabled: adds the chin height so the bar is comfortably elevated
     *   and leaves a solid black border at the bottom edge of the screen (similar to iOS chin).
     * - When system indicator is enabled: does not add chin ("bật thì không chừa nữa").
     */
    static void adjustBottomBarChin(View bar) {
        if (bar == null) return;
        boolean indicatorActive = isSystemIndicatorActive(bar);
        int chin = indicatorActive ? 0 : getChinHeight(bar.getContext());

        ViewGroup.LayoutParams lp = bar.getLayoutParams();
        if (lp != null) {
            Integer origH = ORIGINAL_BAR_HEIGHT.get(bar);
            if (origH == null) {
                origH = lp.height > 0 ? lp.height : bar.getHeight();
                if (origH > 0) ORIGINAL_BAR_HEIGHT.put(bar, origH);
            }
            if (origH != null && origH > 0) {
                int targetH = origH + chin;
                if (lp.height != targetH) {
                    lp.height = targetH;
                    bar.setLayoutParams(lp);
                }
            }
        }

        Integer origPad = ORIGINAL_BAR_PADDING_BOTTOM.get(bar);
        if (origPad == null) {
            origPad = bar.getPaddingBottom();
            ORIGINAL_BAR_PADDING_BOTTOM.put(bar, origPad);
        }
        int targetPad = origPad + chin;
        if (bar.getPaddingBottom() != targetPad) {
            bar.setPadding(bar.getPaddingLeft(), bar.getPaddingTop(), bar.getPaddingRight(), targetPad);
        }
    }

    private static final String BLACK_CHIN_TAG = "buwin_black_chin_overlay";

    /**
     * Ensures a solid black chin is painted at the bottom edge of the screen when the system
     * navigation indicator is disabled ("bôi đen mép dưới màn hình"), preventing the next video
     * in ViewPager from peeking through underneath the elevated bottom bar.
     */
    static void updateChinOverlay(Activity activity, boolean clearDisplay) {
        if (activity == null || activity.isFinishing()) return;
        Window window = activity.getWindow();
        if (window == null) return;
        View decor = window.getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) decor;

        boolean indicatorActive = isSystemIndicatorActive(root);
        int chin = (indicatorActive || clearDisplay) ? 0 : getChinHeight(activity);

        View chinView = root.findViewWithTag(BLACK_CHIN_TAG);
        if (chin <= 0) {
            if (chinView != null && chinView.getVisibility() != View.GONE) {
                chinView.setVisibility(View.GONE);
            }
            return;
        }

        if (chinView == null) {
            chinView = new View(activity);
            chinView.setTag(BLACK_CHIN_TAG);
            chinView.setBackgroundColor(Color.BLACK);
            chinView.setClickable(false);
            chinView.setFocusable(false);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, chin, Gravity.BOTTOM);
            root.addView(chinView, lp);
        } else {
            ViewGroup.LayoutParams lp = chinView.getLayoutParams();
            if (lp != null && lp.height != chin) {
                lp.height = chin;
                chinView.setLayoutParams(lp);
            }
            if (chinView.getVisibility() != View.VISIBLE) {
                chinView.setVisibility(View.VISIBLE);
            }
        }
        chinView.bringToFront();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            chinView.setElevation(9999f);
        }
    }



    /** Whether the spacer under the video may be collapsed. */
    static boolean collapseAllowed() {
        return !collapseRefused;
    }

    /** True while a bar's background is held, so the layout pass keeps running to put it back. */
    static boolean holdsBackground() {
        return !BACKGROUNDS.isEmpty();
    }

    /** Forgets the verdict and the measurements once the switch is off. */
    static void reset() {
        collapseRefused = false;
        CLEAR_BEFORE.clear();
    }

    /**
     * Swaps the bar's background for a clear one, or puts back the one this class took. A
     * background TikTok set since is never overwritten on the way back.
     */
    static void setBackgroundClear(View bar, boolean clear) {
        setBackgroundClear(bar, clear, false);
    }

    /**
     * The same, with {@code floating} drawing the floating pill (#124) in place of the clear
     * background. A bar with no background of its own is a layout this wasn't written for, and
     * keeps TikTok's look.
     */
    static void setBackgroundClear(View bar, boolean clear, boolean floating) {
        if (bar == null) return;
        Drawable current = bar.getBackground();
        boolean ours = current instanceof ClearBackground || current instanceof FloatingBackground;
        if (clear) {
            if (current == null) return;
            if (ours && (current instanceof FloatingBackground) == floating) return;
            if (!ours) BACKGROUNDS.put(bar, current);
            bar.setBackground(floating
                    ? new FloatingBackground(bar.getResources().getDisplayMetrics().density)
                    : new ClearBackground());
            return;
        }
        if (!BACKGROUNDS.containsKey(bar)) {
            if (current instanceof ClearBackground) {
                bar.setBackgroundColor(Color.BLACK);
            }
            return;
        }
        Drawable original = BACKGROUNDS.remove(bar);
        if (ours) bar.setBackground(original != null ? original : new ColorDrawable(Color.BLACK));
    }

    /**
     * Compares the overlays with the bar. With the spacer in place ({@code collapsed} false) it
     * notes which overlays sit clear of the bar; once the spacer is collapsed it reports true, and
     * remembers, when one that was clear has moved under it. Views not laid out, hidden, or
     * entirely above or below the bar (a neighbouring cell) are not judged.
     */
    static boolean judge(View bar, List<View> overlays, boolean collapsed) {
        if (bar == null || !bar.isShown() || bar.getHeight() == 0) return false;
        float barTop = topInWindow(bar);
        for (View overlay : overlays) {
            if (overlay == null || !overlay.isShown() || overlay.getHeight() == 0) continue;
            float top = topInWindow(overlay);
            boolean under = reachesUnder(top, top + overlay.getHeight(), barTop);
            if (!collapsed) {
                CLEAR_BEFORE.put(overlay, !under);
            } else if (under && Boolean.TRUE.equals(CLEAR_BEFORE.get(overlay))) {
                collapseRefused = true;
                return true;
            }
        }
        return false;
    }

    /** An overlay that starts above the bar's top edge and ends below it. */
    static boolean reachesUnder(float top, float bottom, float barTop) {
        return top < barTop && bottom > barTop + SLACK_PX;
    }

    private static float topInWindow(View view) {
        float top = view.getTop() + view.getTranslationY();
        android.view.ViewParent parent = view.getParent();
        while (parent instanceof View) {
            View group = (View) parent;
            top += group.getTop() - group.getScrollY() + group.getTranslationY();
            parent = group.getParent();
        }
        return top;
    }
}
