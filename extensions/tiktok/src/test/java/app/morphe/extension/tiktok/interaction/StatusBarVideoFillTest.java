package app.morphe.extension.tiktok.interaction;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.feed.SeeThroughStatusBar;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {30, 35}, manifest = Config.NONE)
public class StatusBarVideoFillTest {
    public static class Result {
        final int width, height;
        final Float x, y;
        public Result(int width, int height) { this(width, height, 0f, 0f); }
        public Result(int width, int height, Float x, Float y) {
            this.width = width; this.height = height; this.x = x; this.y = y;
        }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public Float getTranslateX() { return x; }
        public Float getTranslateY() { return y; }
        public Object getResultOperator() { return null; }
        public Result copy(int w, int h, Float x, Float y, Object operator) {
            return new Result(w, h, x, y);
        }
    }

    @Before public void setup() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        SettingsStatus.videoOverlaysEnabled = true;
        SettingsStatus.videoFitEnabled = true;
        Settings.FIT_VIDEO_TO_SCREEN.save(false);
        Settings.FILL_VIDEO_TO_SCREEN.save(false);
        Settings.STATUS_BAR_STYLE.save(Settings.STATUS_BAR_STYLE_SCALE);
    }

    @After public void reset() {
        SettingsStatus.videoOverlaysEnabled = false;
        SettingsStatus.videoFitEnabled = false;
        Settings.FIT_VIDEO_TO_SCREEN.save(false);
    }

    private void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static class Scene {
        final FrameLayout content, cell, player;
        Scene(Activity activity) {
            cell = new FrameLayout(activity);
            player = new FrameLayout(activity);
            cell.addView(player, new FrameLayout.LayoutParams(1000, 1600));
            activity.setContentView(cell);
            content = activity.findViewById(android.R.id.content);
        }
        void apply(Result result) {
            ViewGroup.LayoutParams params = player.getLayoutParams();
            params.width = result.width; params.height = result.height;
            player.setLayoutParams(params);
            player.setTranslationX(result.x); player.setTranslationY(result.y);
        }
    }

    @Test public void nativeApplyAndCheckerAgreeBeforeDrawingAndKeepSurfaceScaleOne() {
        try (var controller = Robolectric.buildActivity(com.ss.android.ugc.aweme.main.MainActivity.class).setup()) {
            Scene scene = new Scene(controller.get());
            layout(scene.content, 1000, 2100);
            Result original = new Result(1000, 1600);
            Result applied = (Result) VideoFit.fitted(scene.player, original);
            Result checked = (Result) VideoFit.fitted(scene.player, original);
            assertNotSame(original, applied);
            assertEquals(applied.width, checked.width);
            assertEquals(applied.height, checked.height);
            assertEquals(applied.x, checked.x);
            assertEquals(applied.y, checked.y);
            assertTrue(applied.height >= 2100);
            assertEquals(1000, original.width);
            assertEquals(1600, original.height);
            scene.apply(applied);
            layout(scene.content, 1000, 2100);
            scene.player.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals(1f, scene.player.getScaleX(), 0f);
            assertEquals(1f, scene.player.getScaleY(), 0f);
            assertTrue(StatusBarVideoFill.handles(scene.player));
        }
    }

    @Test public void bindBeforeMeasureFinishesBeforeFirstDrawWithoutPostingResize() {
        try (var controller = Robolectric.buildActivity(com.ss.android.ugc.aweme.main.MainActivity.class).setup()) {
            Scene scene = new Scene(controller.get());
            Result source = new Result(1000, 1600);
            scene.apply((Result) VideoFit.fitted(scene.player, source));
            layout(scene.content, 1000, 2100);
            assertTrue(scene.player.getViewTreeObserver().dispatchOnPreDraw()); // cancels draw
            assertTrue(scene.player.getLayoutParams().height >= 2100);
            layout(scene.content, 1000, 2100);
            assertFalse(scene.player.getViewTreeObserver().dispatchOnPreDraw()); // stable: draw allowed
            assertEquals(1f, scene.player.getScaleY(), 0f);
        }
    }

    @Test public void rebindingRecycledPlayerUsesNewVideoAspectRatioWithoutAccumulation() {
        try (var controller = Robolectric.buildActivity(com.ss.android.ugc.aweme.main.MainActivity.class).setup()) {
            Scene scene = new Scene(controller.get());
            layout(scene.content, 1000, 2100);
            for (int i = 0; i < 20; i++) {
                Result original = new Result(1000, i % 2 == 0 ? 1600 : 1900);
                Result next = (Result) VideoFit.fitted(scene.player, original);
                scene.apply(next);
                layout(scene.content, 1000, 2100);
                assertFalse(scene.player.getViewTreeObserver().dispatchOnPreDraw());
                assertTrue(next.height >= 2100);
                assertEquals(1f, scene.player.getScaleY(), 0f);
            }
        }
    }

    @Test public void fitAndOtherStylesRetainTheirOwnBehavior() {
        try (var controller = Robolectric.buildActivity(com.ss.android.ugc.aweme.main.MainActivity.class).setup()) {
            Scene scene = new Scene(controller.get());
            layout(scene.content, 1000, 2100);
            Settings.FIT_VIDEO_TO_SCREEN.save(true);
            assertNull(StatusBarVideoFill.fitted(scene.player, new Result(1000, 1600)));
            Settings.FIT_VIDEO_TO_SCREEN.save(false);
            Settings.STATUS_BAR_STYLE.save(Settings.STATUS_BAR_STYLE_SHIFT);
            assertNull(StatusBarVideoFill.fitted(scene.player, new Result(1000, 1600)));
        }
    }

    @Test public void nativeFillDoesNotTakeOverWhenOverlayPatchIsOff() {
        try (var controller = Robolectric.buildActivity(com.ss.android.ugc.aweme.main.MainActivity.class).setup()) {
            Scene scene = new Scene(controller.get());
            layout(scene.content, 1000, 2100);
            SettingsStatus.videoOverlaysEnabled = false;
            Result original = new Result(1000, 1600);
            assertSame(original, VideoFit.fitted(scene.player, original));
            assertFalse(StatusBarVideoFill.handles(scene.player));
        }
    }

    @Test public void offsetsAccountForContainerPositionAndCoverEveryEdge() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(1000, 1600);
        params.leftMargin = 4; params.topMargin = 7;
        var geometry = StatusBarVideoFill.geometry(1000, 1600, new float[]{1000, 2100, 10, 100}, params);
        assertEquals((float) Math.floor((1000 - geometry.width) / 2f) - 14, geometry.x, 0f);
        assertEquals((float) Math.floor((2100 - geometry.height) / 2f) - 107, geometry.y, 0f);
        assertTrue(geometry.height >= 2104);
    }

    public static class Operator {
        public String eventType = "homepage_hot";
        @Override public boolean equals(Object other) { return other instanceof Operator; }
        @Override public int hashCode() { return 1; }
    }
    public static class Params {
        final Operator operator;
        final int width, height;
        Params(Operator operator, int width, int height) {
            this.operator = operator; this.width = width; this.height = height;
        }
        public Operator getParamsOperator() { return operator; }
        public int getVideoWidth() { return width; }
        public int getVideoHeight() { return height; }
    }

    @Test public void nativePolicyUsesOriginalOrientationAndIdentityInsteadOfCroppedResult() {
        Operator portrait = new Operator(), landscape = new Operator();
        StatusBarVideoFill.rememberParams(new Params(portrait, 720, 1280));
        StatusBarVideoFill.rememberParams(new Params(landscape, 1920, 1080));
        assertTrue(StatusBarVideoFill.useNativeCrop(portrait));
        assertFalse(StatusBarVideoFill.useNativeCrop(landscape));
        assertTrue(StatusBarVideoFill.keepFullSizeOnComments());
        try (var controller = Robolectric.buildActivity(com.ss.android.ugc.aweme.main.MainActivity.class).setup()) {
            Scene scene = new Scene(controller.get());
            layout(scene.content, 1000, 2100);
            Result alreadyCropped = new Result(1000, 2100);
            StatusBarVideoFill.rememberResult(alreadyCropped, new Params(landscape, 1920, 1080));
            assertSame(alreadyCropped, VideoFit.fitted(scene.player, alreadyCropped));
            assertTrue(StatusBarVideoFill.handles(scene.player));
            assertEquals(1f, scene.player.getScaleY(), 0f);
        }
    }

    @Test public void uncappedPortraitCoverageIsStableAtOddPixelDimensions() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(720, 1280);
        var frame = StatusBarVideoFill.geometry(720, 1280, new float[]{1321, 2639, 0, 0}, params);
        assertTrue(frame.width >= 1325);
        assertTrue(frame.height >= 2643);
        assertTrue(frame.x <= -2 && frame.y <= -2);
        assertTrue(frame.x + frame.width >= 1323);
        assertTrue(frame.y + frame.height >= 2641);
    }

    @Test public void liveSurfaceMeasureCoversStatusAreaWithoutScalingItsSiblingCard() throws Exception {
        try (var controller = Robolectric.buildActivity(com.ss.android.ugc.aweme.main.MainActivity.class).setup()) {
            Scene scene = new Scene(controller.get());
            scene.cell.setId(0x7f0a9ddb);
            ((FrameLayout.LayoutParams) scene.player.getLayoutParams()).topMargin = 144;
            scene.player.setPadding(7, 9, 0, 0);
            View surface = new View(controller.get());
            scene.player.addView(surface, new FrameLayout.LayoutParams(1000, 1600));
            View card = new View(controller.get());
            scene.cell.addView(card, new FrameLayout.LayoutParams(800, 300));
            layout(scene.content, 1000, 2100);
            var field = NativeLivePreview.class.getDeclaredField("IDS");
            field.setAccessible(true);
            ((app.morphe.extension.shared.ResourceIdCache) field.get(null)).putForTests(
                    controller.get().getPackageName(), "view_rootview", scene.cell.getId());
            long measured = NativeLivePreview.measure(surface, 720, 1280);
            assertTrue((int) (measured >>> 32) >= 1004);
            assertTrue((int) measured >= 2104);
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) surface.getLayoutParams();
            assertTrue(params.topMargin + 144 + 9 <= -2);
            assertTrue(params.leftMargin + 7 <= -2);
            assertEquals(1f, card.getScaleX(), 0f);
            assertEquals(300, card.getLayoutParams().height);
            assertEquals(0, NativeLivePreview.measure(surface, 1920, 1080));
            assertEquals(1600, surface.getLayoutParams().height);
        }
    }
}
