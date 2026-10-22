package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.content.Context;
import android.graphics.Insets;
import android.provider.Settings;
import android.view.View;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import app.morphe.extension.shared.ResourceIdCache;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.SettingsStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {30, 35, 37}, manifest = Config.NONE)
public class NativeSafeAreaTest {
    static class Root extends FrameLayout {
        WindowInsets insets = new WindowInsets.Builder().build();
        Root(Context context) { super(context); }
        @Override public WindowInsets getRootWindowInsets() { return insets; }
    }
    @Before public void setup() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        SettingsStatus.videoOverlaysEnabled = true;
        Settings.Secure.putInt(Utils.getContext().getContentResolver(), "secure_gesture_navigation_immersive", 1);
    }
    @After public void reset() { SettingsStatus.videoOverlaysEnabled = false; }

    @Test public void chatKeepsNativePaddingAndReleasesExtraSpaceForKeyboard() {
        Root chat = new Root(Utils.getContext());
        chat.setPadding(3, 4, 5, 8);
        int chin = SeeThroughBottomBar.getChinHeight(chat.getContext());
        NativeSafeArea.measureChat(chat);
        NativeSafeArea.measureChat(chat);
        assertEquals(chin, chat.getPaddingBottom());
        chat.insets = new WindowInsets.Builder().setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, 900))
                .setVisible(WindowInsets.Type.ime(), true).build();
        NativeSafeArea.measureChat(chat);
        assertEquals(8, chat.getPaddingBottom());
        assertEquals(4, chat.getPaddingTop());
        chat.insets = new WindowInsets.Builder().build();
        NativeSafeArea.measureChat(chat);
        assertEquals(chin, chat.getPaddingBottom());
    }

    @Test public void nestedSheetNeverAddsTheSameSafeAreaTwice() {
        Root sheet = new Root(Utils.getContext());
        Root chat = new Root(Utils.getContext());
        sheet.addView(chat);
        int chin = SeeThroughBottomBar.getChinHeight(sheet.getContext());
        NativeSafeArea.measureChat(chat);
        NativeSafeArea.measureGenericSheet(sheet);
        assertEquals(chin, sheet.getPaddingBottom() + chat.getPaddingBottom());
        for (int i = 0; i < 3; i++) {
            NativeSafeArea.measureChat(chat);
            NativeSafeArea.measureGenericSheet(sheet);
            assertEquals(chin, sheet.getPaddingBottom() + chat.getPaddingBottom());
        }
    }

    @Test public void openedVideoComposerAndPagerReserveExactlyTheSameExtraHeight() throws Exception {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            FrameLayout window = new FrameLayout(controller.get());
            controller.get().setContentView(window);
            LinearLayout column = new LinearLayout(controller.get());
            column.setId(0x7f0a9ea4);
            View strip = new View(controller.get());
            strip.setId(0x7f0a0e95);
            column.addView(strip, new LinearLayout.LayoutParams(1320, 158));
            window.addView(column);
            Root composer = new Root(controller.get());
            View footer = new View(controller.get());
            footer.setId(0x7f0a1b82);
            composer.addView(footer);
            window.addView(composer);
            var field = NativeSafeArea.class.getDeclaredField("IDS");
            field.setAccessible(true);
            ResourceIdCache ids = (ResourceIdCache) field.get(null);
            String pkg = composer.getContext().getPackageName();
            ids.putForTests(pkg, "47.4.3:f2t", footer.getId());
            ids.putForTests(pkg, "47.4.3:cnc", strip.getId());
            ids.putForTests(pkg, "viewpager_container", column.getId());
            ids.putForTests(pkg, "video_seek_bar", 0x7f0a9cc5);
            int chin = SeeThroughBottomBar.getChinHeight(composer.getContext());
            for (int i = 0; i < 3; i++) NativeSafeArea.measureDetailInput(composer);
            assertEquals(chin, footer.getPaddingBottom());
            assertEquals(158 + chin, strip.getLayoutParams().height);
            NativeSafeArea.releaseDetailInput(composer);
            assertEquals(158, strip.getLayoutParams().height);
            NativeSafeArea.measureDetailInput(composer);
            assertEquals(158 + chin, strip.getLayoutParams().height);
            composer.insets = new WindowInsets.Builder().setVisible(WindowInsets.Type.ime(), true).build();
            NativeSafeArea.measureDetailInput(composer);
            assertEquals(0, footer.getPaddingBottom());
            assertEquals(158, strip.getLayoutParams().height);
            ids.clear();
        }
    }

    @Test public void profileFooterKeepsCaptionAndSeekAboveItsMeasuredDividerAndSafePadding() throws Exception {
        try (var controller = Robolectric.buildActivity(com.ss.android.ugc.aweme.detail.ui.DetailActivity.class).setup()) {
            Activity activity = controller.get();
            FrameLayout window = new FrameLayout(activity), cell = new FrameLayout(activity);
            LinearLayout column = new LinearLayout(activity); column.setOrientation(LinearLayout.VERTICAL);
            column.setId(0x7f0a9ea4);
            View strip = new View(activity); strip.setId(0x7f0a0e95);
            column.addView(cell, new LinearLayout.LayoutParams(-1, 0, 1));
            column.addView(strip, new LinearLayout.LayoutParams(-1, 158));
            window.addView(column, new FrameLayout.LayoutParams(-1, -1));
            FrameLayout widgets = new FrameLayout(activity);
            FrameLayout.LayoutParams widgetParams = new FrameLayout.LayoutParams(-1, -1); widgetParams.bottomMargin = -152;
            cell.addView(widgets, widgetParams);
            View caption = new View(activity);
            FrameLayout.LayoutParams captionParams = new FrameLayout.LayoutParams(-1, 245, Gravity.BOTTOM);
            captionParams.bottomMargin = 151; widgets.addView(caption, captionParams);
            Root composer = new Root(activity); composer.setId(0x7f0a59aa);
            LinearLayout footer = new LinearLayout(activity); footer.setOrientation(LinearLayout.VERTICAL); footer.setId(0x7f0a1b82);
            footer.addView(new View(activity), new LinearLayout.LayoutParams(-1, 2));
            View input = new View(activity); footer.addView(input, new LinearLayout.LayoutParams(-1, 158));
            composer.addView(footer, new FrameLayout.LayoutParams(-1, -2));
            window.addView(composer, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            FrameLayout seekSlot = new FrameLayout(activity); seekSlot.setId(0x7f0a74c2);
            View seek = new View(activity); seek.setId(0x7f0a9cc5); seekSlot.addView(seek, new FrameLayout.LayoutParams(-1, 90));
            window.addView(seekSlot, new FrameLayout.LayoutParams(-1, 206, Gravity.BOTTOM));
            activity.setContentView(window);
            var field = NativeSafeArea.class.getDeclaredField("IDS"); field.setAccessible(true);
            ResourceIdCache ids = (ResourceIdCache) field.get(null);
            String pkg = activity.getPackageName();
            ids.putForTests(pkg, "47.4.3:qox", composer.getId());
            ids.putForTests(pkg, "47.4.3:f2t", footer.getId());
            ids.putForTests(pkg, "47.4.3:cnc", strip.getId());
            ids.putForTests(pkg, "47.4.3:vrd", seekSlot.getId());
            ids.putForTests(pkg, "viewpager_container", column.getId());
            ids.putForTests(pkg, "video_seek_bar", 0x7f0a9cc5);
            int chin = SeeThroughBottomBar.getChinHeight(activity);
            NativeBottomBar.measureFeed(window);
            measureWindow(window);
            NativeSafeArea.finishDetailInput(composer);
            measureWindow(window);
            int expectedHeight = 160 + chin;
            assertEquals(expectedHeight, composer.getHeight());
            assertEquals(expectedHeight, strip.getLayoutParams().height);
            assertEquals(composer.getTop(), cell.getBottom());
            assertEquals(composer.getTop() + 1, widgets.getTop() + caption.getBottom());
            assertEquals(composer.getTop() - 12, seekSlot.getTop() + seek.getHeight() / 2);
            // Native screen adaption can overwrite its strip without remeasuring the input.
            for (int i = 0; i < 4; i++) {
                strip.getLayoutParams().height = 158;
                NativeSafeArea.measureDetailPage(strip);
                NativeSafeArea.measureDetailInput(composer);
                NativeSafeArea.finishDetailInput(composer);
                measureWindow(window);
                assertEquals(expectedHeight, strip.getLayoutParams().height);
                assertEquals(composer.getTop(), cell.getBottom());
                assertEquals(composer.getTop() - 12, seekSlot.getTop() + seek.getHeight() / 2);
            }
            composer.insets = new WindowInsets.Builder().setVisible(WindowInsets.Type.ime(), true).build();
            NativeSafeArea.measureDetailPage(window);
            assertEquals(158, strip.getLayoutParams().height);
            assertEquals(0, ((FrameLayout.LayoutParams) seekSlot.getLayoutParams()).bottomMargin);
            NativeSafeArea.releaseDetailInput(composer);
            ids.clear();
        }
    }

    @Test public void quickDmFixedHeightGrowsWithoutSqueezingItsMatchParentInput() throws Exception {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            FrameLayout window = new FrameLayout(activity), outer = new FrameLayout(activity);
            Root composer = new Root(activity); composer.setId(0x7f0a6d6a);
            LinearLayout row = new LinearLayout(activity); row.setId(0x7f0a6d66);
            row.addView(new View(activity), new LinearLayout.LayoutParams(-1, -1));
            composer.addView(row, new FrameLayout.LayoutParams(-1, -1));
            outer.addView(composer, new FrameLayout.LayoutParams(-1, 158));
            window.addView(outer, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            activity.setContentView(window);
            var field = NativeSafeArea.class.getDeclaredField("IDS"); field.setAccessible(true);
            ResourceIdCache ids = (ResourceIdCache) field.get(null);
            String pkg = activity.getPackageName(); ids.putForTests(pkg, "47.4.3:udg", row.getId());
            int chin = SeeThroughBottomBar.getChinHeight(activity);
            for (int i = 0; i < 5; i++) {
                NativeSafeArea.measureDetailInput(composer); measureWindow(window);
                NativeSafeArea.finishDetailInput(composer); measureWindow(window);
                assertEquals(158 + chin, composer.getHeight());
                assertEquals(158 + chin, outer.getHeight());
                assertEquals(158, row.getChildAt(0).getHeight());
            }
            composer.insets = new WindowInsets.Builder().setVisible(WindowInsets.Type.ime(), true).build();
            NativeSafeArea.measureDetailInput(composer); measureWindow(window);
            assertEquals(158, composer.getHeight());
            assertEquals(158, row.getChildAt(0).getHeight());
            ids.clear();
        }
    }

    @Test public void quickDmUsesMeasuredInputWhenNativeStripAlreadyReservesExtraRoom() throws Exception {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            FrameLayout window = new FrameLayout(activity), cell = new FrameLayout(activity);
            LinearLayout column = new LinearLayout(activity); column.setOrientation(LinearLayout.VERTICAL);
            column.setId(View.generateViewId());
            View strip = new View(activity); strip.setId(View.generateViewId());
            column.addView(cell, new LinearLayout.LayoutParams(-1, 0, 1));
            column.addView(strip, new LinearLayout.LayoutParams(-1, 316));
            window.addView(column, new FrameLayout.LayoutParams(-1, -1));
            Root composer = new Root(activity);
            LinearLayout row = new LinearLayout(activity); row.setId(View.generateViewId());
            row.addView(new View(activity), new LinearLayout.LayoutParams(-1, -1));
            composer.addView(row, new FrameLayout.LayoutParams(-1, -1));
            window.addView(composer, new FrameLayout.LayoutParams(-1, 158, Gravity.BOTTOM));
            FrameLayout seekSlot = new FrameLayout(activity); seekSlot.setId(View.generateViewId());
            View track = new View(activity); track.setId(View.generateViewId());
            seekSlot.addView(track, new FrameLayout.LayoutParams(-1, 90));
            window.addView(seekSlot, new FrameLayout.LayoutParams(-1, 206, Gravity.BOTTOM));
            activity.setContentView(window);
            var field = NativeSafeArea.class.getDeclaredField("IDS"); field.setAccessible(true);
            ResourceIdCache ids = (ResourceIdCache) field.get(null);
            ids.clear();
            String pkg = activity.getPackageName();
            ids.putForTests(pkg, "47.4.3:udg", row.getId());
            ids.putForTests(pkg, "47.4.3:cnc", strip.getId());
            ids.putForTests(pkg, "viewpager_container", column.getId());
            ids.putForTests(pkg, "47.4.3:vrd", seekSlot.getId());
            ids.putForTests(pkg, "video_seek_bar", track.getId());
            int expected = 158 + SeeThroughBottomBar.getChinHeight(activity);
            for (int i = 0; i < 4; i++) {
                // Native screen adaption rewrites its own reserve, including a row already.
                strip.getLayoutParams().height = 316;
                NativeSafeArea.measureDetailInput(composer);
                measureWindow(window);
                NativeSafeArea.finishDetailInput(composer);
                measureWindow(window);
                assertEquals(expected, strip.getLayoutParams().height);
                assertEquals(composer.getTop(), cell.getBottom());
                assertEquals(composer.getTop() - 12, seekSlot.getTop() + track.getHeight() / 2);
                // Entry of the next pass must not grow it again before finishDetailInput.
                NativeSafeArea.measureDetailInput(composer);
                assertEquals(expected, strip.getLayoutParams().height);
            }
            NativeSafeArea.releaseDetailInput(composer);
            ids.clear();
        }
    }

    private static void measureWindow(View window) {
        window.measure(View.MeasureSpec.makeMeasureSpec(1320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2868, View.MeasureSpec.EXACTLY));
        window.layout(0, 0, 1320, 2868);
    }
}
