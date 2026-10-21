package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
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

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {30, 35}, manifest = Config.NONE)
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
}
