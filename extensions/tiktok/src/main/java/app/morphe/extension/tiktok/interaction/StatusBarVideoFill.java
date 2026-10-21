/*
 * Copyright 2026 Buwin contributors
 * https://github.com/minh-quann/buwin-tiktok-patches
 */
package app.morphe.extension.tiktok.interaction;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.feed.SeeThroughStatusBar;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.shared.settings.BuwinPause;

/**
 * Fills the feed's status-bar viewport through TikTok's native adaption result. Both the
 * applier and the already-applied checker receive the same dimensions and offsets. No surface
 * scale is needed after the original-sized picture has appeared.
 */
public final class StatusBarVideoFill {
    private static final Map<View, Bound> BOUND = new WeakHashMap<>();
    private StatusBarVideoFill() {}

    /** Native feed adaption policy, before any surface receives its initial dimensions. */
    public static boolean useNativeCrop(String event) {
        if (!SettingsStatus.videoOverlaysEnabled || BuwinPause.isPaused()
                || Settings.FIT_VIDEO_TO_SCREEN.get()
                || !Settings.STATUS_BAR_STYLE_SCALE.equals(Settings.STATUS_BAR_STYLE.get())) return false;
        return "homepage_hot".equals(event) || "homepage_follow".equals(event)
                || "homepage_friends".equals(event) || "homepage_nearby".equals(event)
                || "homepage_fresh".equals(event) || "homepage_popular".equals(event);
    }

    /** Null means this isn't the main feed's scale mode, or the result cannot be copied. */
    static Object fitted(View video, Object original) {
        if (!SeeThroughStatusBar.wantsNativeFill(video)
                || !(video.getLayoutParams() instanceof FrameLayout.LayoutParams)) return null;
        float[] viewport = SeeThroughStatusBar.nativeFillViewport(video);
        int width = size(original, "getWidth"), height = size(original, "getHeight");
        if (width <= 0 || height <= 0) return null;
        Geometry geometry = viewport == null
                ? new Geometry(width, height, offset(original, "getTranslateX"), offset(original, "getTranslateY"))
                : geometry(width, height, viewport, (FrameLayout.LayoutParams) video.getLayoutParams());
        Object copy = VideoFit.copyOf(original, geometry.width, geometry.height,
                new float[]{geometry.x, geometry.y});
        if (copy == null) return null;
        SeeThroughStatusBar.releaseFillTransforms(video);
        VideoFit.uncentre(video);
        Bound bound = BOUND.get(video);
        if (bound == null) {
            bound = new Bound(video);
            BOUND.put(video, bound);
            video.addOnAttachStateChangeListener(bound);
            if (video.isAttachedToWindow()) video.getViewTreeObserver().addOnPreDrawListener(bound);
        }
        bound.sourceWidth = width;
        bound.sourceHeight = height;
        bound.sourceX = offset(original, "getTranslateX");
        bound.sourceY = offset(original, "getTranslateY");
        if (viewport != null) topLeft(video);
        return copy;
    }

    static final class Geometry {
        final int width, height;
        final float x, y;
        Geometry(int width, int height, float x, float y) {
            this.width = width;
            this.height = height;
            this.x = x;
            this.y = y;
        }
    }

    static Geometry geometry(int width, int height, float[] viewport, FrameLayout.LayoutParams params) {
        // Preserve the previous mode's crop limit and two-pixel edge coverage.
        double scale = Math.min(1.45, Math.max(1, Math.max(
                (viewport[0] + 2) / width, (viewport[1] + 2) / height)));
        int filledWidth = (int) Math.ceil(width * scale);
        int filledHeight = (int) Math.ceil(height * scale);
        return new Geometry(filledWidth, filledHeight,
                (viewport[0] - filledWidth) / 2f - viewport[2] - params.leftMargin,
                (viewport[1] - filledHeight) / 2f - viewport[3] - params.topMargin);
    }

    private static void topLeft(View video) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) video.getLayoutParams();
        if (params.gravity != (Gravity.TOP | Gravity.LEFT)) {
            params.gravity = Gravity.TOP | Gravity.LEFT;
            video.setLayoutParams(params);
        }
    }

    /** Is the player, or its rendering surface, already sized by the native hook? */
    public static boolean handles(View player) {
        for (View current = player; current != null;) {
            if (BOUND.containsKey(current)) return true;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        for (View nativeView : BOUND.keySet()) {
            for (ViewParent parent = nativeView.getParent(); parent instanceof View; parent = parent.getParent()) {
                if (parent == player) return true;
            }
        }
        return false;
    }

    static void release(View video) {
        Bound bound = BOUND.remove(video);
        if (bound == null) return;
        video.removeOnAttachStateChangeListener(bound);
        ViewTreeObserver observer = video.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnPreDrawListener(bound);
        if (video.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) video.getLayoutParams();
            if (params.gravity == (Gravity.TOP | Gravity.LEFT)) {
                params.gravity = bound.gravity;
                video.setLayoutParams(params);
            }
        }
    }

    private static int size(Object result, String getter) {
        Object value = Reflect.invoke(result, getter);
        return value instanceof Integer ? (Integer) value : 0;
    }

    private static float offset(Object result, String getter) {
        Object value = Reflect.invoke(result, getter);
        return value instanceof Float ? (Float) value : 0f;
    }

    /**
     * A recycled cell may be rebound before its final measure, or the bottom tabs may only
     * have been discovered on the subsequent layout. Finish that adjustment BEFORE drawing:
     * request a new measure and cancel this draw, rather than posting a visible resize later.
     */
    private static final class Bound implements ViewTreeObserver.OnPreDrawListener,
            View.OnAttachStateChangeListener {
        final WeakReference<View> video;
        final int gravity;
        int sourceWidth, sourceHeight;
        float sourceX, sourceY;

        Bound(View view) {
            video = new WeakReference<>(view);
            gravity = ((FrameLayout.LayoutParams) view.getLayoutParams()).gravity;
        }

        @Override public boolean onPreDraw() {
            View view = video.get();
            if (view == null || !view.isShown()) return true;
            if (!(view.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
                release(view);
                return true;
            }
            float[] viewport = SeeThroughStatusBar.nativeFillViewport(view);
            if (viewport == null && SeeThroughStatusBar.wantsNativeFill(view)) return true;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
            Geometry geometry = viewport == null
                    ? new Geometry(sourceWidth, sourceHeight, sourceX, sourceY)
                    : geometry(sourceWidth, sourceHeight, viewport, params);
            boolean resize = params.width != geometry.width || params.height != geometry.height;
            boolean gravityChanged = params.gravity != (viewport == null ? gravity : (Gravity.TOP | Gravity.LEFT));
            if (viewport == null) release(view);
            else topLeft(view);
            if (resize) {
                params.width = geometry.width;
                params.height = geometry.height;
                view.setLayoutParams(params);
            }
            if (view.getTranslationX() != geometry.x) view.setTranslationX(geometry.x);
            if (view.getTranslationY() != geometry.y) view.setTranslationY(geometry.y);
            boolean awaitingMeasure = view.getMeasuredWidth() != geometry.width
                    || view.getMeasuredHeight() != geometry.height;
            if (awaitingMeasure) view.requestLayout();
            return !(resize || gravityChanged || awaitingMeasure);
        }

        @Override public void onViewAttachedToWindow(View view) {
            view.getViewTreeObserver().addOnPreDrawListener(this);
        }
        @Override public void onViewDetachedFromWindow(View view) {
            view.getViewTreeObserver().removeOnPreDrawListener(this);
        }
    }
}
