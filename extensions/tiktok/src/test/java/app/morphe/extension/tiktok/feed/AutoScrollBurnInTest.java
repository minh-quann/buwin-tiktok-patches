package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.os.SystemClock;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.widget.FrameLayout;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;

import java.time.Duration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {30, 35}, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AutoScrollBurnInTest {
    @Test public void floatingIconAndLabelDimOutsideCellsAndRestoreOnTouch() {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            Utils.setContext(activity);
            SettingsStatus.videoOverlaysEnabled = true;
            BurnInGuard.resetForTests();
            Settings.FADE_CONTROLS_OPACITY.save(100);
            Settings.BURN_IN_IDLE_LEVEL.save(25);
            Settings.BURN_IN_GUARD.save(BurnInGuard.DIM);
            FrameLayout root = new FrameLayout(activity);
            View icon = new View(activity), label = new View(activity);
            icon.setId(0x7f0a0896);
            label.setId(0x7f0a089d);
            root.addView(icon);
            root.addView(label);
            activity.setContentView(root);
            VideoOverlayHider.resolveForTests("47.4.3:biv", icon.getId());
            VideoOverlayHider.resolveForTests("47.4.3:bj2", label.getId());
            VideoOverlayHider.applyTo(activity);
            ShadowLooper main = Shadow.extract(Looper.getMainLooper());
            main.idleFor(Duration.ofMillis(BurnInGuard.IDLE_AFTER_MS));
            assertEquals(0.25f, icon.getAlpha(), 0.001f);
            assertEquals(0.25f, label.getAlpha(), 0.001f);
            activity.getWindow().getCallback().onWindowFocusChanged(true);
            main.idle();
            assertEquals(1f, icon.getAlpha(), 0f);
            assertEquals(1f, label.getAlpha(), 0f);
        } finally {
            Settings.BURN_IN_GUARD.resetToDefault();
            Settings.BURN_IN_IDLE_LEVEL.resetToDefault();
            SettingsStatus.videoOverlaysEnabled = false;
            BurnInGuard.resetForTests();
            VideoOverlayHider.resolveForTests("47.4.3:biv", 0);
            VideoOverlayHider.resolveForTests("47.4.3:bj2", 0);
        }
    }
    @Test public void allNineBurnInPositionsKeepEveryVideoEdgeCoveredAndShiftNestedControlsOnce() {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            Utils.setContext(activity);
            BurnInGuard.resetForTests();
            Settings.BURN_IN_GUARD.save(BurnInGuard.DIM_AND_SHIFT);
            FrameLayout root = new FrameLayout(activity);
            root.setBackgroundColor(Color.BLUE);
            FrameLayout controls = new FrameLayout(activity);
            View label = new View(activity);
            label.setBackgroundColor(Color.WHITE);
            controls.addView(label, new FrameLayout.LayoutParams(10, 10));
            FrameLayout.LayoutParams slot = new FrameLayout.LayoutParams(30, 30);
            slot.leftMargin = 40; slot.topMargin = 80;
            root.addView(controls, slot);
            activity.setContentView(root);
            View content = activity.findViewById(android.R.id.content);
            ShadowLooper main = Shadow.extract(Looper.getMainLooper());
            for (int i = 0; i < 9; i++) {
                BurnInGuard.pass(activity, 100, true);
                BurnInGuard.control(controls);
                BurnInGuard.control(label);
                BurnInGuard.finishControls();
                content.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
                content.layout(0, 0, 100, 200);
                int[] at = BurnInGuard.placeAt(SystemClock.uptimeMillis());
                float step = BurnInGuard.STEP_DP * activity.getResources().getDisplayMetrics().density;
                assertEquals(at[0] * step, controls.getTranslationX(), 0.001f);
                assertEquals(at[1] * step, controls.getTranslationY(), 0.001f);
                assertEquals(0f, label.getTranslationX(), 0f);
                assertEquals(0f, label.getTranslationY(), 0f);
                assertEquals(0f, content.getTranslationX(), 0f);
                assertEquals(0f, content.getTranslationY(), 0f);
                Bitmap frame = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888);
                content.draw(new Canvas(frame));
                for (int x = 0; x < 100; x++) {
                    assertEquals(Color.BLUE, frame.getPixel(x, 0));
                    assertEquals(Color.BLUE, frame.getPixel(x, 199));
                }
                for (int y = 0; y < 200; y++) {
                    assertEquals(Color.BLUE, frame.getPixel(0, y));
                    assertEquals(Color.BLUE, frame.getPixel(99, y));
                }
                frame.recycle();
                main.idleFor(Duration.ofMillis(BurnInGuard.SHIFT_EVERY_MS));
            }
            // A recycled control adopts the host's new position and is restored when it leaves.
            controls.setTranslationX(17f);
            controls.setTranslationY(19f);
            BurnInGuard.pass(activity, 100, true);
            BurnInGuard.control(controls);
            BurnInGuard.finishControls();
            BurnInGuard.pass(activity, 100, false);
            BurnInGuard.finishControls();
            assertEquals(17f, controls.getTranslationX(), 0f);
            assertEquals(19f, controls.getTranslationY(), 0f);
        } finally {
            Settings.BURN_IN_GUARD.resetToDefault();
            BurnInGuard.resetForTests();
        }
    }

}
