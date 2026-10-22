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
    private static final Map<View, Boolean> LANDSCAPE = new WeakHashMap<>();
    private static final WeakIdentityTable SOURCES = new WeakIdentityTable();
    private static final WeakIdentityTable RESULTS = new WeakIdentityTable();
    private StatusBarVideoFill() {}

    /** Native params/results are data classes: equal values must not alias different videos. */
    private static final class WeakIdentityTable {
        final java.lang.ref.ReferenceQueue<Object> queue = new java.lang.ref.ReferenceQueue<>();
        final Map<Key, Source> values = new java.util.HashMap<>();
        static final class Key extends WeakReference<Object> {
            final int hash;
            Key(Object value, java.lang.ref.ReferenceQueue<Object> queue) {
                super(value, queue);
                hash = System.identityHashCode(value);
            }
            @Override public int hashCode() { return hash; }
            @Override public boolean equals(Object other) {
                return this == other || (other instanceof Key && get() != null && get() == ((Key) other).get());
            }
        }
        private void sweep() {
            java.lang.ref.Reference<?> dead;
            while ((dead = queue.poll()) != null) values.remove(dead);
        }
        synchronized void put(Object key, Source value) {
            sweep();
            values.put(new Key(key, queue), value);
        }
        synchronized Source get(Object key) {
            if (key == null) return null;
            sweep();
            return values.get(new Key(key, null));
        }
    }

    private static final class Source {
        final int width, height, containerWidth, containerHeight;
        final boolean feed, photo;
        Source(Object params) {
            width = size(params, "getVideoWidth");
            height = size(params, "getVideoHeight");
            Object operator = Reflect.invoke(params, "getParamsOperator");
            feed = feedEvent(Reflect.readField(operator, "eventType"));
            containerWidth = size(params, "getContainerWidth");
            containerHeight = size(params, "getContainerHeight");
            Source previous = SOURCES.get(operator);
            photo = previous != null && previous.photo;
        }
        Source(Object operator, Object aweme) {
            Object video = Reflect.invoke(aweme, "getVideo");
            width = size(video, "getWidth"); height = size(video, "getHeight");
            containerWidth = containerHeight = 0;
            feed = feedEvent(Reflect.readField(operator, "eventType"));
            photo = isPhoto(aweme);
        }
    }

    public static boolean isPhoto(Object aweme) {
        Object images = Reflect.invoke(aweme, "getImageInfos");
        return (images instanceof java.util.List && !((java.util.List<?>) images).isEmpty())
                || Reflect.invoke(aweme, "getPhotoModeImageInfo") != null
                || Reflect.invoke(aweme, "getPhotoModeTextInfo") != null;
    }

    public static void rememberAweme(Object operator, Object aweme) {
        SOURCES.put(operator, new Source(operator, aweme));
    }

    /** Original stream dimensions, recorded before any crop strategy can change their ratio. */
    public static void rememberParams(Object params) {
        Object operator = Reflect.invoke(params, "getParamsOperator");
        if (operator != null) SOURCES.put(operator, new Source(params));
    }

    public static void rememberResult(Object result, Object params) {
        if (result != null && params != null) RESULTS.put(result, new Source(params));
    }

    public static boolean keepFullSizeOnComments() {
        return SettingsStatus.videoOverlaysEnabled && !BuwinPause.isPaused()
                && !Settings.FIT_VIDEO_TO_SCREEN.get()
                && Settings.STATUS_BAR_STYLE_SCALE.equals(Settings.STATUS_BAR_STYLE.get());
    }

    /** Native feed adaption policy, before any surface receives its initial dimensions. */
    public static boolean useNativeCrop(Object operator) {
        Source source = SOURCES.get(operator);
        return keepFullSizeOnComments() && source != null && source.feed
                && !source.photo && source.width > 0 && source.height >= source.width
                && cropAllowed(source.width, source.height, source.containerWidth, source.containerHeight);
    }

    private static boolean feedEvent(Object event) {
        return "homepage_hot".equals(event) || "homepage_follow".equals(event)
                || "homepage_friends".equals(event) || "homepage_nearby".equals(event)
                || "homepage_fresh".equals(event) || "homepage_popular".equals(event);
    }

    /** Null means this isn't the main feed's scale mode, or the result cannot be copied. */
    static Object fitted(View video, Object original) {
        if (!SeeThroughStatusBar.wantsNativeFill(video)) {
            LANDSCAPE.remove(video);
            return null;
        }
        Source source = RESULTS.get(original);
        if (source == null) {
            Object operator = Reflect.invoke(original, "getResultOperator");
            Object params = Reflect.invoke(operator, "getVideoAdaptionParams");
            if (params != null) source = new Source(params);
        }
        int width = size(original, "getWidth"), height = size(original, "getHeight");
        if (source != null && source.photo) {
            release(video);
            LANDSCAPE.put(video, Boolean.TRUE);
            SeeThroughStatusBar.releaseFillTransforms(video);
            return original;
        }
        if ((source != null && source.width > source.height * 1.5) || (source == null && width > height * 1.5)) {
            LANDSCAPE.put(video, Boolean.TRUE);
            SeeThroughStatusBar.releaseFillTransforms(video);
            return null;
        }
        LANDSCAPE.remove(video);
        if (!(video.getLayoutParams() instanceof FrameLayout.LayoutParams)) return null;
        float[] viewport = SeeThroughStatusBar.nativeFillViewport(video);
        if (width <= 0 || height <= 0) return null;
        int ratioWidth = source != null && source.width > 0 ? source.width : width;
        int ratioHeight = source != null && source.height > 0 ? source.height : height;
        Geometry geometry = viewport == null
                ? new Geometry(width, height, offset(original, "getTranslateX"), offset(original, "getTranslateY"))
                : smartGeometry(ratioWidth, ratioHeight, viewport, (FrameLayout.LayoutParams) video.getLayoutParams());
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
        bound.ratioWidth = ratioWidth;
        bound.ratioHeight = ratioHeight;
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
        // Cover every edge from the ORIGINAL stream ratio. A crop cap can leave gaps; deriving
        // the ratio from an already cropped result compounds rounding on recycled cells.
        double scale = Math.max((viewport[0] + 4.0) / width, (viewport[1] + 4.0) / height);
        int filledWidth = (int) Math.ceil(width * scale);
        int filledHeight = (int) Math.ceil(height * scale);
        return new Geometry(filledWidth, filledHeight,
                (float) Math.floor((viewport[0] - filledWidth) / 2) - viewport[2] - params.leftMargin,
                (float) Math.floor((viewport[1] - filledHeight) / 2) - viewport[3] - params.topMargin);
    }

    /** At most 16% of the source may be discarded; this is a content-loss limit, not a scale. */
    public static boolean cropAllowed(int width, int height, float viewportWidth, float viewportHeight) {
        if (width <= 0 || height <= 0 || width > height) return false;
        if (viewportWidth <= 0 || viewportHeight <= 0) return height > width * 1.5;
        double sourceRatio = (double) width / height, targetRatio = viewportWidth / viewportHeight;
        return Math.min(sourceRatio / targetRatio, targetRatio / sourceRatio) >= 0.84;
    }

    static Geometry smartGeometry(int width, int height, float[] viewport, FrameLayout.LayoutParams params) {
        if (cropAllowed(width, height, viewport[0], viewport[1])) return geometry(width, height, viewport, params);
        double scale = Math.min(viewport[0] / width, viewport[1] / height);
        int fittedWidth = Math.max(1, (int) Math.round(width * scale));
        int fittedHeight = Math.max(1, (int) Math.round(height * scale));
        return new Geometry(fittedWidth, fittedHeight,
                (float) Math.floor((viewport[0] - fittedWidth) / 2) - viewport[2] - params.leftMargin,
                (float) Math.floor((viewport[1] - fittedHeight) / 2) - viewport[3] - params.topMargin);
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
            if (BOUND.containsKey(current) || LANDSCAPE.containsKey(current)) return true;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        java.util.Set<View> controlled = new java.util.HashSet<>(BOUND.keySet());
        controlled.addAll(LANDSCAPE.keySet());
        for (View nativeView : controlled) {
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
        int ratioWidth, ratioHeight;
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
                    : smartGeometry(ratioWidth, ratioHeight, viewport, params);
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
