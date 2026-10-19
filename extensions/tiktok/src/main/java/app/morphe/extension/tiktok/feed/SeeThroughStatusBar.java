/*
 * Copyright 2026 Buwin contributors
 * https://github.com/minh-quann/buwin-tiktok-patches
 */
package app.morphe.extension.tiktok.feed;

import android.app.Activity;
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
                // TikTok's own color, kept even when it set a new one while the bar was clear.
                CLEARED.put(window, color);
                window.setStatusBarColor(Color.TRANSPARENT);
            }
        } else {
            Integer own = CLEARED.remove(window);
            if (own != null && color == Color.TRANSPARENT) window.setStatusBarColor(own);
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
            if (scrim != null) {
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
            scrim.setVisibility(View.VISIBLE);
            scrim.bringToFront();
        }
    }

    private static int getStatusBarHeight(Activity activity) {
        int height = 0;
        int resId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            try {
                height = activity.getResources().getDimensionPixelSize(resId);
            } catch (Exception ignored) {
            }
        }
        if (height <= 0) {
            float density = activity.getResources().getDisplayMetrics().density;
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
     * One layout pass over a feed cell's player: manages video size and position
     * based on the selected {@link Settings#STATUS_BAR_STYLE}.
     */
    static void fill(View player, boolean wanted) {
        if (!(player instanceof ViewGroup)) return;
        if (wanted && fitWins()) wanted = false;
        String style = Settings.STATUS_BAR_STYLE.get();
        boolean doScale = wanted && Settings.STATUS_BAR_STYLE_SCALE.equals(style);
        boolean doShift = wanted && Settings.STATUS_BAR_STYLE_SHIFT.equals(style);

        ViewGroup group = (ViewGroup) player;
        boolean surfaces = false;
        for (int i = 0, count = group.getChildCount(); i < count; i++) {
            View surface = group.getChildAt(i);
            if (!(surface instanceof SurfaceView) && !(surface instanceof TextureView)) continue;
            surfaces = true;
            if (doScale) {
                shift(surface, 0f);
                grow(surface, fillScale(group.getHeight(), surface));
            } else if (doShift) {
                grow(surface, 1f);
                shift(surface, -computeShift(group.getHeight(), surface, false));
            } else {
                grow(surface, 1f);
                shift(surface, 0f);
            }
        }
        // A player this grew keeps getting a pass after a surface shows up in it, so it goes back.
        if (surfaces && !FILLED.containsKey(player) && !SHIFTED.containsKey(player)) return;
        ViewParent space = player.getParent();
        boolean spaceValid = space instanceof View;
        int spaceHeight = spaceValid ? ((View) space).getHeight() : 0;
        if (doScale) {
            shift(player, 0f);
            grow(player, (!surfaces && spaceValid) ? fillScale(spaceHeight, player, true) : 1f);
        } else if (doShift) {
            grow(player, 1f);
            shift(player, (!surfaces && spaceValid) ? -computeShift(spaceHeight, player, true) : 0f);
        } else {
            grow(player, 1f);
            shift(player, 0f);
        }
    }

    /** Whether Fit the video to the screen is on, so a picture it shrank stays whole. */
    private static boolean fitWins() {
        return SettingsStatus.videoFitEnabled && Settings.FIT_VIDEO_TO_SCREEN.get();
    }

    /** Writes {@code scale} on {@code view} and tracks it. */
    private static void grow(View view, float scale) {
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
        if (scale == 1f) {
            FILLED.remove(view);
            return;
        }
        FILLED.put(view, scale);
    }

    /** Writes {@code translationY} on {@code view} and tracks it. */
    private static void shift(View view, float translationY) {
        Float was = SHIFTED.get(view);
        float current = view.getTranslationY();
        if ((was == null ? 0f : was) == current) {
            if (translationY == current) return;
        }
        view.setTranslationY(translationY);
        if (translationY == 0f) {
            SHIFTED.remove(view);
            return;
        }
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
     * top edge meets the top of {@code spaceHeight} without scaling or cropping side content.
     */
    static float computeShift(int spaceHeight, View picture, boolean moved) {
        int width = picture.getWidth();
        int height = picture.getHeight();
        if (width <= 0 || height <= width) return 0f;
        if (spaceHeight <= height) return 0f;

        Float myShift = SHIFTED.get(picture);
        float otherOffset = moved ? (picture.getTranslationY() - (myShift != null ? myShift : 0f)) : 0f;
        float top = picture.getTop() + otherOffset;
        float bottom = spaceHeight - picture.getBottom() - otherOffset;
        if (top <= 0f) return 0f;
        if (moved) {
            if (bottom < 0f) return 0f;
            return top;
        }
        if (Math.abs(top - bottom) > 60f) return 0f;
        return top;
    }

    /** Whether a surface still carries a scale or translation this wrote, so the pass runs to put it back. */
    static boolean anyFilled() {
        return !FILLED.isEmpty() || !SHIFTED.isEmpty();
    }
}
