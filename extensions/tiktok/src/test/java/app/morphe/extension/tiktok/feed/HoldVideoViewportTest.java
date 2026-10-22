package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.cleardisplay.RememberClearDisplayPatch;
import app.morphe.extension.tiktok.interaction.VideoFit;
import app.morphe.extension.tiktok.interaction.StatusBarVideoFillTest.Result;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {30, 35}, manifest = Config.NONE)
public class HoldVideoViewportTest {
    public static final class ClearEvent {
        public final boolean LIZ;
        public final int LIZIZ;
        ClearEvent(boolean clear, int type) { LIZ = clear; LIZIZ = type; }
    }
    public static final class Edge {
        final View view;
        Edge(View view) { this.view = view; }
        public View getContainerView() { return view; }
    }
    private void layout(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2868, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1320, 2868);
    }
    private void same(Result expected, Result actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        assertEquals(expected.getTranslateX(), actual.getTranslateX());
        assertEquals(expected.getTranslateY(), actual.getTranslateY());
    }

    @Test public void nativeHoldKeepsCropThroughHiddenTabsAndClearModeUntilReleaseLayoutSettles() {
        checkGesture(false);
    }

    @Test public void nativePinchKeepsTheFilledBaseCropWhenCleanUiHidesTabs() {
        checkGesture(true);
    }

    private void checkGesture(boolean pinch) {
        try (var controller = Robolectric.buildActivity(com.ss.android.ugc.aweme.main.MainActivity.class).setup()) {
            Activity activity = controller.get();
            Utils.setContext(activity);
            SettingsStatus.videoOverlaysEnabled = true;
            Settings.FIT_VIDEO_TO_SCREEN.save(false);
            Settings.STATUS_BAR_STYLE.save(Settings.STATUS_BAR_STYLE_SCALE);
            RememberClearDisplayPatch.rememberClearDisplayEvent(new ClearEvent(false, 2));
            FrameLayout root = new FrameLayout(activity), cell = new FrameLayout(activity);
            View player = new View(activity), gesture = new View(activity), bar = new View(activity);
            cell.addView(player, new FrameLayout.LayoutParams(720, 1280));
            cell.addView(gesture, new FrameLayout.LayoutParams(10, 10));
            root.addView(cell, new FrameLayout.LayoutParams(-1, -1));
            root.addView(bar, new FrameLayout.LayoutParams(-1, 230, Gravity.BOTTOM));
            activity.setContentView(root);
            View content = activity.findViewById(android.R.id.content);
            layout(content);
            SeeThroughBottomBar.adjustBottomBarChin(bar);
            Result source = new Result(720, 1440);
            Result before = (Result) VideoFit.fitted(player, source);
            Edge owner = new Edge(gesture);
            for (int i = 0; i < 5; i++) {
                if (pinch) HoldVideoViewport.pinch(owner, true);
                else HoldVideoViewport.begin(owner);
                bar.setVisibility(View.GONE);
                RememberClearDisplayPatch.rememberClearDisplayEvent(new ClearEvent(true, 0));
                layout(content);
                same(before, (Result) VideoFit.fitted(player, source));
                if (pinch) HoldVideoViewport.pinch(owner, false);
                else HoldVideoViewport.end(owner);
                // Release arrives before the host restores tabs: do not expand for that frame.
                same(before, (Result) VideoFit.fitted(player, source));
                bar.setVisibility(View.VISIBLE);
                RememberClearDisplayPatch.rememberClearDisplayEvent(new ClearEvent(false, 2));
                layout(content);
                same(before, (Result) VideoFit.fitted(player, source));
            }
            // A deliberate Clear display outside the gesture still gets its own full viewport.
            RememberClearDisplayPatch.rememberClearDisplayEvent(new ClearEvent(true, 0));
            Result clear = (Result) VideoFit.fitted(player, source);
            assertTrue(clear.getHeight() > before.getHeight());
            assertEquals(1f, player.getScaleX(), 0f);
            assertEquals(1f, player.getScaleY(), 0f);
        } finally {
            RememberClearDisplayPatch.rememberClearDisplayEvent(new ClearEvent(false, 2));
            SettingsStatus.videoOverlaysEnabled = false;
        }
    }
}
