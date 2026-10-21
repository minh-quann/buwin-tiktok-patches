package app.morphe.extension.tiktok.feed;

import android.view.View;
import android.widget.FrameLayout;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import com.ss.android.ugc.aweme.main.MainActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/** An APK without the optional native hook must still never draw the reset scale. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {30, 35}, manifest = Config.NONE)
public class StatusBarDrawFillTest {
    @Before public void setup() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        SettingsStatus.videoOverlaysEnabled = true;
        Settings.FIT_VIDEO_TO_SCREEN.save(false);
        Settings.STATUS_BAR_STYLE.save(Settings.STATUS_BAR_STYLE_SCALE);
    }
    @After public void reset() { SettingsStatus.videoOverlaysEnabled = false; }

    @Test public void hostScaleResetWithoutGlobalLayoutIsRepairedBeforeDraw() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            FrameLayout cell = new FrameLayout(controller.get());
            FrameLayout player = new FrameLayout(controller.get());
            cell.addView(player, new FrameLayout.LayoutParams(1000, 1600));
            controller.get().setContentView(cell);
            View content = controller.get().findViewById(android.R.id.content);
            content.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2100, View.MeasureSpec.EXACTLY));
            content.layout(0, 0, 1000, 2100);
            SeeThroughStatusBar.fill(player, true);
            float filled = player.getScaleY();
            assertTrue(filled > 1f);
            for (int i = 0; i < 10; i++) {
                player.setScaleX(1f);
                player.setScaleY(1f);
                // setScale does not cause a global layout; the old listener missed this.
                player.getViewTreeObserver().dispatchOnPreDraw();
                assertEquals(filled, player.getScaleX(), 0f);
                assertEquals(filled, player.getScaleY(), 0f);
            }
        }
    }
}
