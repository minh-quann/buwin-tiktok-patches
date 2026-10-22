package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {30, 35, 37}, manifest = Config.NONE)
public class NativeSafeAreaTest {
    static class Root extends FrameLayout {
        WindowInsets insets = new WindowInsets.Builder().build();
        Root(Context context) { super(context); }
        @Override public WindowInsets getRootWindowInsets() { return insets; }
    }
    static class Keyboard extends LinearLayout {
        WindowInsets insets = new WindowInsets.Builder().build();
        Keyboard(Context context) { super(context); setOrientation(VERTICAL); }
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

    @Test public void commentDrawerDoesNotLoseChinHeightWhenImeOpens() {
        Root drawer = new Root(Utils.getContext());
        int chin = SeeThroughBottomBar.getChinHeight(drawer.getContext());
        int width = View.MeasureSpec.makeMeasureSpec(1320, View.MeasureSpec.EXACTLY);
        int closed = View.MeasureSpec.makeMeasureSpec(2700, View.MeasureSpec.EXACTLY);
        assertEquals(closed, NativeSafeArea.commentHeightSpec(drawer, width, closed));
        drawer.insets = new WindowInsets.Builder().setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, 900))
                .setVisible(WindowInsets.Type.ime(), true).build();
        int opened = View.MeasureSpec.makeMeasureSpec(2700 - chin, View.MeasureSpec.EXACTLY);
        for (int i = 0; i < 4; i++) {
            assertEquals(closed, NativeSafeArea.commentHeightSpec(drawer, width, opened));
            assertEquals(0, NativeSafeArea.minimumBottom(drawer));
        }
        // A real keyboard resize, rotation or change of constraint must remain native.
        int resized = View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY);
        assertEquals(resized, NativeSafeArea.commentHeightSpec(drawer, width, resized));
        int rotated = View.MeasureSpec.makeMeasureSpec(2700, View.MeasureSpec.EXACTLY);
        assertEquals(opened, NativeSafeArea.commentHeightSpec(drawer, rotated, opened));
        int atMost = View.MeasureSpec.makeMeasureSpec(2700 - chin, View.MeasureSpec.AT_MOST);
        assertEquals(atMost, NativeSafeArea.commentHeightSpec(drawer, width, atMost));
        drawer.insets = new WindowInsets.Builder().build();
        assertEquals(closed, NativeSafeArea.commentHeightSpec(drawer, width, closed));
        assertEquals(chin, NativeSafeArea.minimumBottom(drawer));
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
            assertEquals(158 + chin + 15, strip.getLayoutParams().height);
            NativeSafeArea.releaseDetailInput(composer);
            assertEquals(158, strip.getLayoutParams().height);
            NativeSafeArea.measureDetailInput(composer);
            assertEquals(158 + chin + 15, strip.getLayoutParams().height);
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
            Keyboard composer = new Keyboard(activity); composer.setId(0x7f0a59aa);
            LinearLayout footer = new LinearLayout(activity); footer.setOrientation(LinearLayout.VERTICAL); footer.setId(0x7f0a1b82);
            View divider = new View(activity); divider.setId(0x7f0a8213);
            footer.addView(divider, new LinearLayout.LayoutParams(-1, 2));
            View input = new View(activity); footer.addView(input, new LinearLayout.LayoutParams(-1, 158));
            composer.addView(new View(activity), new LinearLayout.LayoutParams(-1, 0, 1));
            composer.addView(footer, new LinearLayout.LayoutParams(-1, -2));
            window.addView(composer, new FrameLayout.LayoutParams(-1, -1));
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
            ids.putForTests(pkg, "47.4.3:y9i", divider.getId());
            ids.putForTests(pkg, "viewpager_container", column.getId());
            ids.putForTests(pkg, "video_seek_bar", 0x7f0a9cc5);
            int chin = SeeThroughBottomBar.getChinHeight(activity);
            NativeBottomBar.measureFeed(window);
            measureWindow(window);
            NativeSafeArea.finishDetailInput(composer);
            measureWindow(window);
            int expectedHeight = 160 + chin + 15;
            assertEquals(expectedHeight, footer.getHeight());
            assertEquals(window.getHeight(), composer.getHeight());
            assertEquals(expectedHeight - 2, strip.getLayoutParams().height);
            assertEquals(composer.getTop() + footer.getTop() + 2, cell.getBottom());
            assertEquals(cell.getBottom() + 1, widgets.getTop() + caption.getBottom());
            assertEquals(composer.getTop() + footer.getTop() + divider.getHeight() / 2, seekSlot.getTop() + seek.getHeight() / 2);
            assertEquals(16, input.getTop() - divider.getHeight() / 2);
            assertEquals(15, ((LinearLayout.LayoutParams) divider.getLayoutParams()).bottomMargin);
            assertEquals(strip.getTop() - 1, seekSlot.getTop() + seek.getHeight() / 2);
            // Native screen adaption can overwrite its strip without remeasuring the input.
            for (int i = 0; i < 4; i++) {
                strip.getLayoutParams().height = 158;
                NativeSafeArea.measureDetailPage(strip);
                NativeSafeArea.measureDetailInput(composer);
                NativeSafeArea.finishDetailInput(composer);
                measureWindow(window);
                assertEquals(expectedHeight - 2, strip.getLayoutParams().height);
                assertEquals(composer.getTop() + footer.getTop() + 2, cell.getBottom());
                assertEquals(composer.getTop() + footer.getTop() + divider.getHeight() / 2, seekSlot.getTop() + seek.getHeight() / 2);
                assertEquals(16, input.getTop() - divider.getHeight() / 2);
                assertEquals(15, ((LinearLayout.LayoutParams) divider.getLayoutParams()).bottomMargin);
                assertEquals(strip.getTop() - 1, seekSlot.getTop() + seek.getHeight() / 2);
            }
            composer.insets = new WindowInsets.Builder().setVisible(WindowInsets.Type.ime(), true).build();
            NativeSafeArea.measureDetailPage(window);
            assertEquals(158, strip.getLayoutParams().height);
            assertEquals(0, ((FrameLayout.LayoutParams) seekSlot.getLayoutParams()).bottomMargin);
            NativeSafeArea.releaseDetailInput(composer);
            assertEquals(0, ((LinearLayout.LayoutParams) divider.getLayoutParams()).bottomMargin);
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

    @Test @Config(qualifiers = "xxhdpi")
    public void quickDmUsesMeasuredInputWhenNativeStripAlreadyReservesExtraRoom() throws Exception {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            FrameLayout window = new FrameLayout(activity), cell = new FrameLayout(activity);
            LinearLayout column = new LinearLayout(activity); column.setOrientation(LinearLayout.VERTICAL);
            column.setId(View.generateViewId());
            View strip = new View(activity); strip.setId(View.generateViewId());
            strip.setBackgroundColor(Color.BLUE);
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
                track.getLayoutParams().height = i % 2 == 0 ? 90 : 140;
                NativeSafeArea.measureDetailInput(composer);
                measureWindow(window);
                NativeSafeArea.finishDetailInput(composer);
                measureWindow(window);
                assertEquals(expected, composer.getHeight());
                assertEquals(expected + 15, strip.getLayoutParams().height);
                assertEquals(composer.getTop() - 15, cell.getBottom());
                assertEquals(composer.getTop() - 16, seekSlot.getTop() + track.getHeight() / 2);
                assertEquals(strip.getTop() - 1, seekSlot.getTop() + track.getHeight() / 2);
                assertEquals(Color.BLACK, ((ColorDrawable) strip.getBackground()).getColor());
                // Entry of the next pass must not grow it again before finishDetailInput.
                NativeSafeArea.measureDetailInput(composer);
                assertEquals(expected + 15, strip.getLayoutParams().height);
            }
            composer.insets = new WindowInsets.Builder().setVisible(WindowInsets.Type.ime(), true).build();
            NativeSafeArea.measureDetailInput(composer);
            assertEquals(Color.BLUE, ((ColorDrawable) strip.getBackground()).getColor());
            NativeSafeArea.releaseDetailInput(composer);
            assertEquals(Color.BLUE, ((ColorDrawable) strip.getBackground()).getColor());
            ids.clear();
        }
    }

    private static void measureWindow(View window) {
        window.measure(View.MeasureSpec.makeMeasureSpec(1320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2868, View.MeasureSpec.EXACTLY));
        window.layout(0, 0, 1320, 2868);
    }

    @Test public void searchRebindKeepsRelatedQueryAndCaptionAboveDetailFooter() throws Exception {
        try (var controller = Robolectric.buildActivity(com.ss.android.ugc.aweme.detail.ui.DetailActivity.class).setup()) {
            Activity activity = controller.get();
            FrameLayout cell = new FrameLayout(activity);
            android.widget.RelativeLayout widgets = new android.widget.RelativeLayout(activity);
            widgets.setId(0x7f0aa089);
            FrameLayout.LayoutParams widgetParams = new FrameLayout.LayoutParams(-1, -1);
            widgetParams.bottomMargin = -152;
            cell.addView(widgets, widgetParams);
            View query = new View(activity); query.setId(0x7f0a3e20);
            android.widget.RelativeLayout.LayoutParams queryParams = new android.widget.RelativeLayout.LayoutParams(-1, 100);
            queryParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM);
            widgets.addView(query, queryParams);
            View caption = new View(activity);
            android.widget.RelativeLayout.LayoutParams captionParams = new android.widget.RelativeLayout.LayoutParams(-1, 200);
            captionParams.addRule(android.widget.RelativeLayout.ABOVE, query.getId());
            widgets.addView(caption, captionParams);
            activity.setContentView(cell);
            var cacheField = NativeBottomBar.class.getDeclaredField("IDS"); cacheField.setAccessible(true);
            ResourceIdCache cache = (ResourceIdCache) cacheField.get(null);
            String pkg = activity.getPackageName();
            cache.putForTests(pkg, "widget_container", widgets.getId());
            cache.putForTests(pkg, "47.4.3:ljb", query.getId());
            for (int i = 0; i < 4; i++) {
                // Screen adaption/rebind can remove the query's compensating bottom margin.
                queryParams.bottomMargin = 0;
                widgetParams.bottomMargin = -152 - i * 10;
                NativeBottomBar.measureCell(cell);
                measureWindow(cell);
                assertEquals(cell.getHeight() - 1, widgets.getTop() + query.getBottom());
                assertEquals(query.getTop(), caption.getBottom());
                assertTrue(widgets.getTop() + caption.getBottom() < cell.getHeight());
            }
            query.setVisibility(View.GONE);
            NativeBottomBar.measureCell(cell);
            assertEquals(0, queryParams.bottomMargin);
        }
    }

}
