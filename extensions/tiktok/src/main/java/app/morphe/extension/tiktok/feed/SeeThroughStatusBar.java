/*
 * Copyright 2026 Buwin contributors
 * https://github.com/minh-quann/buwin-tiktok-patches
 */
package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.widget.FrameLayout;

import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.tiktok.blockauthor.FeedVisibility;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;

/**
 * Optimized for tall aspect-ratio displays (Honor/MagicOS 19.55:9 and high-res screens).
 * Expands or shifts video surfaces to cover status bar areas seamlessly without losing side content,
 * or overlays a YouTube-style ambient gradient scrim.
 */
public final class SeeThroughStatusBar {
    static final String SCRIM_TAG = "buwin_status_bar_gradient";

    /**
     * Increased fill scale threshold to accommodate ultra-tall aspect ratios (19.5:9 to 21:9).
     */
    static final float MAX_FILL_SCALE = 1.45f;
    /** Windows whose bar this cleared, with the color TikTok last gave it. */
    private static final Map<Window, Integer> CLEARED = new WeakHashMap<>();
    /** Video surfaces and players this grew, with the scale it wrote. */
    private static final Map<View, Float> FILLED = new WeakHashMap<>();
    /** Video surfaces and players this shifted, with the translationY it wrote. */
    private static final Map<View, Float> SHIFTED = new WeakHashMap<>();

    private SeeThroughStatusBar() {
    }

    /** One layout pass: clears the bar while {@code wanted}, and gives its color back otherwise. */
    static void apply(Activity activity, boolean wanted) {
        Window window = activity.getWindow();
        if (window == null) return;
        int color = window.getStatusBarColor();
        String style = Settings.STATUS_BAR_STYLE.get();
        boolean isGradient = wanted && Settings.STATUS_BAR_STYLE_GRADIENT.equals(style);

        if (wanted) {
            if (color != Color.TRANSPARENT) {
                // Keep previous non-transparent color
                if (!CLEARED.containsKey(window)) {
                    CLEARED.put(window, color);
                }
                window.setStatusBarColor(Color.TRANSPARENT);
            }
        } else {
            Integer own = CLEARED.remove(window);
            if (own != null && color == Color.TRANSPARENT) {
                window.setStatusBarColor(own);
            }
        }

        updateGradientScrim(activity, isGradient);
    }

    private static void updateGradientScrim(Activity activity, boolean visible) {
        Window window = activity.getWindow();
        if (window == null) return;
        View decorView = window.getDecorView();
        if (!(decorView instanceof ViewGroup)) return;
        ViewGroup decor = (ViewGroup) decorView;

        View scrim = decor.findViewWithTag(SCRIM_TAG);
        if (!visible) {
            if (scrim != null && scrim.getVisibility() != View.GONE) {
                scrim.setVisibility(View.GONE);
            }
            return;
        }

        int statusBarHeight = getStatusBarHeight(activity);
        int scrimHeight = (int) (statusBarHeight * 1.8f);

        if (scrim == null) {
            scrim = new View(activity);
            scrim.setTag(SCRIM_TAG);
            scrim.setClickable(false);
            scrim.setFocusable(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            }

            GradientDrawable gradient = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x99000000, 0x66000000, 0x22000000, 0x00000000}
            );
            scrim.setBackground(gradient);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    scrimHeight,
                    Gravity.TOP
            );
            decor.addView(scrim, params);
        } else {
            ViewGroup.LayoutParams lp = scrim.getLayoutParams();
            if (lp != null && lp.height != scrimHeight) {
                lp.height = scrimHeight;
                scrim.setLayoutParams(lp);
            }
            if (scrim.getVisibility() != View.VISIBLE) {
                scrim.setVisibility(View.VISIBLE);
            }
        }
    }

    static int getStatusBarHeight(Context context) {
        if (context == null) return 0;
        int height = 0;
        int resId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            try {
                height = context.getResources().getDimensionPixelSize(resId);
            } catch (Exception ignored) {
            }
        }
        if (height <= 0) {
            float density = context.getResources().getDisplayMetrics().density;
            height = (int) (32 * density);
        }
        return height;
    }

    /** Whether this has cleared the bar of {@code activity}'s window. */
    static boolean isCleared(Activity activity) {
        Window window = activity.getWindow();
        return window != null && CLEARED.containsKey(window);
    }

    /**
     * Finds the Activity hosting the given view.
     */
    static Activity activityOf(View view) {
        if (view == null) return null;
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    /** Finds any child SurfaceView or TextureView within root. */
    static View findSurface(View root) {
        if (root instanceof SurfaceView || root instanceof TextureView) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0, count = vg.getChildCount(); i < count; i++) {
                View found = findSurface(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Finds the root view of the feed cell (view_rootview) or topmost laid-out ancestor. */
    static View findCellRoot(View view) {
        View current = view;
        View best = null;
        while (current != null) {
            if ("view_rootview".equals(getViewIdName(current))) {
                return current;
            }
            if (current.getHeight() > 0) {
                best = current;
            }
            ViewParent p = current.getParent();
            if (p instanceof View) {
                current = (View) p;
            } else {
                break;
            }
        }
        return best;
    }

    private static String getViewIdName(View v) {
        try {
            if (v.getId() > 0 && v.getResources() != null) {
                return v.getResources().getResourceEntryName(v.getId());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Calculates unshifted top coordinate of target relative to ancestor. */
    private static int topInParent(View target, View ancestor) {
        int top = 0;
        View current = target;
        while (current != null && current != ancestor) {
            top += current.getTop();
            ViewParent p = current.getParent();
            if (p instanceof View) {
                current = (View) p;
            } else {
                break;
            }
        }
        return top;
    }

    /** Ensures none of the ancestors clip children so the scaled video can extend to screen edges. */
    static void unclipAncestors(View view, View limit) {
        ViewParent p = view.getParent();
        while (p instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) p;
            if (vg.getClipChildren()) {
                vg.setClipChildren(false);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (vg.getClipToPadding()) {
                    vg.setClipToPadding(false);
                }
            }
            if (vg == limit) break;
            p = vg.getParent();
        }
    }

    /**
     * One layout pass over a feed cell's player: manages video size and position
     * based on the selected {@link Settings#STATUS_BAR_STYLE}.
     */
    static void fill(View player, boolean wanted) {
        if (!(player instanceof ViewGroup)) return;
        if (wanted && fitWins()) wanted = false;
        if (wanted) {
            Activity activity = activityOf(player);
            if (activity != null && FeedVisibility.isCommentSheetVisible(activity)) {
                wanted = false;
            }
        }

        ViewGroup group = (ViewGroup) player;
        View surface = findSurface(group);

        // If TikTok is actively scaling down the player/surface for comments or transition, yield immediately
        if (wanted) {
            if ((player.getScaleX() < 0.98f && !FILLED.containsKey(player))
                    || (surface != null && surface.getScaleX() < 0.98f && !FILLED.containsKey(surface))) {
                wanted = false;
            }
        }

        String style = Settings.STATUS_BAR_STYLE.get();
        boolean doScale = wanted && Settings.STATUS_BAR_STYLE_SCALE.equals(style);
        boolean doShift = wanted && Settings.STATUS_BAR_STYLE_SHIFT.equals(style);
        boolean doShiftSplit = wanted && Settings.STATUS_BAR_STYLE_SHIFT_SPLIT.equals(style);
        boolean doGradient = wanted && Settings.STATUS_BAR_STYLE_GRADIENT.equals(style);

        View cell = findCellRoot(player);
        int fullHeight = cell != null ? cell.getHeight() : 0;
        if (fullHeight <= 0) {
            ViewParent space = player.getParent();
            if (space instanceof View) {
                fullHeight = ((View) space).getHeight();
            }
        }
        if (fullHeight <= 0) {
            Activity activity = activityOf(player);
            if (activity != null && activity.getWindow() != null && activity.getWindow().getDecorView() != null) {
                fullHeight = activity.getWindow().getDecorView().getHeight();
            }
        }
        if (fullHeight <= 0) {
            fullHeight = player.getResources().getDisplayMetrics().heightPixels;
        }

        boolean indicatorActive = SeeThroughBottomBar.isSystemIndicatorActive(player);
        int chin = indicatorActive ? 0 : SeeThroughBottomBar.getChinHeight(player.getContext());
        int barTop = SeeThroughBottomBar.getBottomBarTop(player, fullHeight);
        int targetHeight = (barTop > 0 && barTop < fullHeight) ? barTop : (fullHeight - chin);
        if (targetHeight <= 0) targetHeight = fullHeight;

        if (doScale) {
            // Scale mode: center vertically and scale to fill from top to targetHeight (above black chin)
            View targetView = surface != null ? surface : player;
            int vWidth = targetView.getWidth();
            int vHeight = targetView.getHeight();

            if (vWidth > 0 && vHeight > vWidth) {
                unclipAncestors(targetView, cell);

                float neededScale = (float) targetHeight / (float) vHeight;
                if (neededScale < 1f) neededScale = 1f;
                if (neededScale > MAX_FILL_SCALE) neededScale = MAX_FILL_SCALE;

                float targetCenterY = targetHeight / 2f;
                float currentCenterY = topInParent(targetView, cell) + vHeight / 2f;
                float shiftY = targetCenterY - currentCenterY;

                android.util.Log.i("buwin", "ScaleToFill: scale=" + neededScale + ", shiftY=" + shiftY
                        + ", targetH=" + targetHeight + ", vH=" + vHeight + ", chin=" + chin);

                if (surface != null) {
                    shift(player, 0f);
                    grow(player, 1f);
                    shift(surface, shiftY);
                    grow(surface, neededScale);
                } else {
                    shift(player, shiftY);
                    grow(player, neededScale);
                }
            } else {
                shift(player, 0f);
                grow(player, 1f);
                if (surface != null) {
                    shift(surface, 0f);
                    grow(surface, 1f);
                }
            }
        } else if (doShift || doGradient) {
            // Shift mode or Ambient Gradient: keep 1:1 scale (zero horizontal crop), shift upwards so top reaches status bar
            if (surface != null) {
                grow(surface, 1f);
                if (surface.getTop() > 0 && group.getHeight() > surface.getHeight()) {
                    float sShift = computeShift(surface, group.getHeight());
                    shift(surface, -sShift);
                    shift(player, 0f);
                } else {
                    shift(surface, 0f);
                    float pShift = computeShift(player, fullHeight);
                    shift(player, -pShift);
                }
            } else {
                grow(player, 1f);
                float pShift = computeShift(player, fullHeight);
                shift(player, -pShift);
            }
        } else if (doShiftSplit) {
            // Shift Split mode: keep 1:1 scale, shift upwards halfway to balance top and bottom margins
            if (surface != null) {
                grow(surface, 1f);
                if (surface.getTop() > 0 && group.getHeight() > surface.getHeight()) {
                    float sShift = computeShift(surface, group.getHeight()) / 2f;
                    shift(surface, -sShift);
                    shift(player, 0f);
                } else {
                    shift(surface, 0f);
                    float pShift = computeShift(player, fullHeight) / 2f;
                    shift(player, -pShift);
                }
            } else {
                grow(player, 1f);
                float pShift = computeShift(player, fullHeight) / 2f;
                shift(player, -pShift);
            }
        } else {
            // Default or comments open: restore 1:1 scale and 0 translation if previously modified
            grow(player, 1f);
            shift(player, 0f);
            if (surface != null) {
                grow(surface, 1f);
                shift(surface, 0f);
            }
        }
    }

    /** Whether Fit the video to the screen is on, so a picture it shrank stays whole. */
    private static boolean fitWins() {
        return SettingsStatus.videoFitEnabled && Settings.FIT_VIDEO_TO_SCREEN.get();
    }

    /** Writes {@code scale} on {@code view} and tracks it. */
    private static void grow(View view, float scale) {
        if (scale == 1f) {
            Float was = FILLED.remove(view);
            if (was != null) {
                if (view.getWidth() > 0 && view.getHeight() > 0) {
                    view.setPivotX(view.getWidth() / 2f);
                    view.setPivotY(view.getHeight() / 2f);
                }
                view.setScaleX(1f);
                view.setScaleY(1f);
            }
            return;
        }
        Float was = FILLED.get(view);
        float current = view.getScaleY();
        if ((was == null ? 1f : was) == current && view.getScaleX() == current) {
            if (scale == current) return;
        }
        if (view.getWidth() > 0 && view.getHeight() > 0) {
            view.setPivotX(view.getWidth() / 2f);
            view.setPivotY(view.getHeight() / 2f);
        }
        view.setScaleX(scale);
        view.setScaleY(scale);
        FILLED.put(view, scale);
    }

    /** Writes {@code translationY} on {@code view} and tracks it. */
    private static void shift(View view, float translationY) {
        if (translationY == 0f) {
            Float was = SHIFTED.remove(view);
            if (was != null) {
                view.setTranslationY(0f);
            }
            return;
        }
        Float was = SHIFTED.get(view);
        float current = view.getTranslationY();
        if ((was == null ? 0f : was) == current) {
            if (translationY == current) return;
        }
        view.setTranslationY(translationY);
        SHIFTED.put(view, translationY);
    }

    /** {@link #fillScale(int, View, boolean)} for a surface TikTok centered by its layout. */
    static float fillScale(int playerHeight, View surface) {
        return fillScale(playerHeight, surface, false);
    }

    /**
     * How much {@code picture} grows to cover a space {@code spaceHeight} tall.
     */
    static float fillScale(int spaceHeight, View picture, boolean moved) {
        int width = picture.getWidth();
        int height = picture.getHeight();
        if (width <= 0 || height <= width) return 1f;
        if (spaceHeight <= height) return 1f;

        Float myShift = SHIFTED.get(picture);
        float otherOffset = moved ? (picture.getTranslationY() - (myShift != null ? myShift : 0f)) : 0f;
        float top = picture.getTop() + otherOffset;
        float bottom = spaceHeight - picture.getBottom() - otherOffset;
        if (moved) {
            if (top < 0f || bottom < 0f) return 1f;
            float scale = (Math.max(top, bottom) * 2f + height + 2f) / height;
            return scale > MAX_FILL_SCALE ? 1f : scale;
        }
        if (Math.abs(top - bottom) > 60f) return 1f;
        float scale = (Math.max(top, bottom) * 2f + height + 2f) / height;
        return scale > MAX_FILL_SCALE ? 1f : scale;
    }

    /**
     * Computes the vertical distance (pixels) to shift {@code picture} upwards so that its
     * top edge meets the top of the screen/container, leaving room at the bottom.
     */
    static float computeShift(View picture, int spaceHeight) {
        int width = picture.getWidth();
        int height = picture.getHeight();
        if (width <= 0 || height <= 0) return 0f;
        // Don't shift landscape videos (keep them centered)
        if (width > height) return 0f;

        Float myShift = SHIFTED.get(picture);
        float currentShift = myShift != null ? myShift : 0f;
        float actualTranslation = picture.getTranslationY() - currentShift;
        float top = picture.getTop() + actualTranslation;

        int statusBarHeight = getStatusBarHeight(picture.getContext());

        // If the view has an explicit top offset from its layout, shift by that top offset
        // so its top edge reaches 0 (the top of the container/screen).
        if (top > 0f && top < spaceHeight * 0.45f) {
            return top;
        }

        // If top offset is 0 or unmeasured, shift upwards by statusBarHeight to guarantee
        // the video expands into the status bar area, leaving a black border at the bottom.
        if (statusBarHeight > 0) {
            return (float) statusBarHeight;
        }

        return 100f;
    }

    /** Whether a surface still carries a scale or translation this wrote, so the pass runs to put it back. */
    static boolean anyFilled() {
        return !FILLED.isEmpty() || !SHIFTED.isEmpty();
    }
}
