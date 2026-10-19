/*
 * Copyright 2026 Buwin contributors
 * https://github.com/minh-quann/buwin-tiktok-patches
 */
package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.graphics.Color;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;

import android.os.Build;
import android.view.WindowManager;

import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;

/**
 * Optimized for tall aspect-ratio displays (Honor/MagicOS 19.55:9 and high-res screens).
 * Expands video surfaces to cover status bar areas seamlessly without leaving hairline edge artifacts.
 */
public final class SeeThroughStatusBar {
    /**
     * Increased fill scale threshold to accommodate ultra-tall aspect ratios (19.5:9 to 21:9).
     */
    static final float MAX_FILL_SCALE = 1.45f;
    /** Windows whose bar this cleared, with the color TikTok last gave it. */
    private static final Map<Window, Integer> CLEARED = new WeakHashMap<>();
    /** Video surfaces and players this grew, with the scale it wrote. */
    private static final Map<View, Float> FILLED = new WeakHashMap<>();

    private SeeThroughStatusBar() {
    }

    /** One layout pass: clears the bar while {@code wanted}, and gives its color back otherwise. */
    static void apply(Activity activity, boolean wanted) {
        Window window = activity.getWindow();
        if (window == null) return;
        int color = window.getStatusBarColor();
        if (wanted) {
            if (color == Color.TRANSPARENT) return;
            // TikTok's own color, kept even when it set a new one while the bar was clear.
            CLEARED.put(window, color);
            window.setStatusBarColor(Color.TRANSPARENT);
            return;
        }
        Integer own = CLEARED.remove(window);
        if (own != null && color == Color.TRANSPARENT) window.setStatusBarColor(own);
    }

    /** Whether this has cleared the bar of {@code activity}'s window. */
    static boolean isCleared(Activity activity) {
        Window window = activity.getWindow();
        return window != null && CLEARED.containsKey(window);
    }

    /**
     * One layout pass over a feed cell's player: while {@code wanted}, grows its video so a portrait
     * picture covers the player top to bottom; otherwise puts back the scale this wrote. Every pass
     * measures again, so a new video in a reused cell gets its own scale. A scale TikTok wrote
     * itself is left alone.
     *
     * <p>Only the first video after a cold start plays in a surface of the player's own (#164).
     * TikTok creates that one at launch, sized to the picture inside {@code player_view}. Every
     * later cell takes its video view from a factory that, depending on TikTok's experiments, hands
     * back a SurfaceView or a TextureView inside the player, or a SurfaceControl player (47.1.4's
     * {@code X.1T9e}) whose view is {@code player_view} itself: the size TikTok works out for the
     * picture goes on the player, and the picture is a layer that follows the player's place, size
     * and scale. With no surface inside it, the player is the picture, and it grows inside the space
     * around it instead. Fit the video to the screen wins: a picture it shrank to show whole isn't
     * grown back.
     */
    static void fill(View player, boolean wanted) {
        if (!(player instanceof ViewGroup)) return;
        if (wanted && fitWins()) wanted = false;
        ViewGroup group = (ViewGroup) player;
        boolean surfaces = false;
        for (int i = 0, count = group.getChildCount(); i < count; i++) {
            View surface = group.getChildAt(i);
            if (!(surface instanceof SurfaceView) && !(surface instanceof TextureView)) continue;
            surfaces = true;
            grow(surface, wanted ? fillScale(group.getHeight(), surface) : 1f);
        }
        // A player this grew keeps getting a pass after a surface shows up in it, so it goes back.
        if (surfaces && !FILLED.containsKey(player)) return;
        ViewParent space = player.getParent();
        boolean grows = wanted && !surfaces && space instanceof View;
        grow(player, grows ? fillScale(((View) space).getHeight(), player, true) : 1f);
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

        float offset = moved ? picture.getTranslationY() : 0f;
        float top = picture.getTop() + offset;
        float bottom = spaceHeight - picture.getBottom() - offset;
        if (moved) {
            if (top < 0f || bottom < 0f) return 1f;
            float scale = (Math.max(top, bottom) * 2f + height + 2f) / height;
            return scale > MAX_FILL_SCALE ? 1f : scale;
        }
        if (Math.abs(top - bottom) > 60f) return 1f;
        float scale = (Math.max(top, bottom) * 2f + height + 2f) / height;
        return scale > MAX_FILL_SCALE ? 1f : scale;
    }

    /** Whether a surface still carries a scale this wrote, so the pass runs to put it back. */
    static boolean anyFilled() {
        return !FILLED.isEmpty();
    }
}
