package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.graphics.Insets;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import app.morphe.extension.shared.ResourceIdCache;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.interaction.VideoFit;
import app.morphe.extension.tiktok.interaction.StatusBarVideoFillTest.Result;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {30, 35, 37}, qualifiers = "w1320dp-h2868dp-mdpi", manifest = Config.NONE)
public class MessageVideoPageTest {
    public static class Edge {
        final View view;
        Edge(View view) { this.view = view; }
        public View getContainerView() { return view; }
    }
    static class Keyboard extends LinearLayout {
        WindowInsets insets = new WindowInsets.Builder().build();
        Keyboard(Activity activity) { super(activity); setOrientation(VERTICAL); }
        @Override public WindowInsets getRootWindowInsets() { return insets; }
    }
    private ResourceIdCache cache(Class<?> owner) throws Exception {
        var f = owner.getDeclaredField("IDS"); f.setAccessible(true); return (ResourceIdCache) f.get(null);
    }
    private void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2868, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1320, 2868);
    }
    @Test public void shortenedAndPreloadedCellsDoNotSubtractTheComposerTwice() throws Exception {
        var context = org.robolectric.RuntimeEnvironment.getApplication();
        LinearLayout column = new LinearLayout(context); column.setOrientation(LinearLayout.VERTICAL);
        column.setId(View.generateViewId());
        View cell = new View(context), strip = new View(context); strip.setId(View.generateViewId());
        column.addView(cell, new LinearLayout.LayoutParams(-1, 0, 1));
        column.addView(strip, new LinearLayout.LayoutParams(-1, 230));
        var ids = cache(MessageVideoPage.class);
        ids.putForTests(context.getPackageName(), "viewpager_container", column.getId());
        ids.putForTests(context.getPackageName(), "47.4.3:cnc", strip.getId());
        try {
            layout(column);
            assertEquals(2638, MessageVideoPage.bottom(cell, 2868));
            assertEquals(2638, MessageVideoPage.bottom(cell, cell.getHeight()));
            cell.offsetTopAndBottom(-2638);
            assertEquals(2638, MessageVideoPage.bottom(cell, cell.getHeight()));
            cell.offsetTopAndBottom(5276);
            assertEquals(2638, MessageVideoPage.bottom(cell, cell.getHeight()));
        } finally { ids.clear(); }
    }
    @Test public void sharedVideoFillsAboveSafeComposerWhileHeaderAndLandscapeStayNative() throws Exception {
        try (var controller = Robolectric.buildActivity(com.ss.android.ugc.aweme.detail.ui.DetailActivity.class).setup()) {
            Activity activity = controller.get(); Utils.setContext(activity);
            SettingsStatus.videoOverlaysEnabled = true;
            Settings.Secure.putInt(activity.getContentResolver(), "secure_gesture_navigation_immersive", 1);
            app.morphe.extension.tiktok.settings.Settings.FIT_VIDEO_TO_SCREEN.save(false);
            app.morphe.extension.tiktok.settings.Settings.STATUS_BAR_STYLE.save("scale");
            FrameLayout root = new FrameLayout(activity), cell = new FrameLayout(activity);
            LinearLayout column = new LinearLayout(activity); column.setOrientation(LinearLayout.VERTICAL);
            column.setId(0x7f0a9ea4);
            View strip = new View(activity); strip.setId(0x7f0a0e95);
            column.addView(cell, new LinearLayout.LayoutParams(-1, 0, 1));
            column.addView(strip, new LinearLayout.LayoutParams(-1, 158));
            root.addView(column, new FrameLayout.LayoutParams(-1, -1));
            FrameLayout playerParent = new FrameLayout(activity);
            FrameLayout.LayoutParams slot = new FrameLayout.LayoutParams(-1, -1); slot.topMargin = 144;
            cell.addView(playerParent, slot);
            View player = new View(activity); playerParent.addView(player, new FrameLayout.LayoutParams(1320, 2346));
            View header = new View(activity); FrameLayout.LayoutParams headerSlot = new FrameLayout.LayoutParams(-1, 158);
            headerSlot.topMargin = 144; root.addView(header, headerSlot);
            FrameLayout message = new FrameLayout(activity); message.setId(0x7f0a3afe);
            Keyboard keyboard = new Keyboard(activity); keyboard.setPadding(0, 0, 0, 16);
            LinearLayout row = new LinearLayout(activity); row.setOrientation(LinearLayout.VERTICAL); row.setId(0x7f0a6d66);
            View input = new View(activity); row.addView(input, new LinearLayout.LayoutParams(-1, 142));
            keyboard.addView(row, new LinearLayout.LayoutParams(-1, -2));
            message.addView(keyboard, new FrameLayout.LayoutParams(-1, -2));
            root.addView(message, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            activity.setContentView(root);
            String pkg = activity.getPackageName();
            for (Class<?> type : new Class<?>[]{NativeSafeArea.class, MessageVideoPage.class}) {
                var ids = cache(type);
                ids.putForTests(pkg, "47.4.3:kyn", message.getId());
                ids.putForTests(pkg, "47.4.3:udg", row.getId());
                ids.putForTests(pkg, "47.4.3:cnc", strip.getId());
                ids.putForTests(pkg, "viewpager_container", column.getId());
            }
            int chin = SeeThroughBottomBar.getChinHeight(activity);
            for (int i = 0; i < 4; i++) NativeSafeArea.measureDetailInput(keyboard);
            layout(root.getRootView()); layout(activity.findViewById(android.R.id.content));
            assertEquals(chin, row.getPaddingBottom());
            assertEquals(158 + chin, strip.getLayoutParams().height);
            assertEquals(chin + 16, 2868 - message.getTop() - keyboard.getTop() - row.getTop() - input.getBottom());
            assertTrue(MessageVideoPage.active(activity));
            float[] viewport = SeeThroughStatusBar.nativeFillViewport(player);
            assertEquals(2868 - 158 - chin, viewport[1], 0f);
            Result portrait = (Result) VideoFit.fitted(player, new Result(1320, 2346));
            assertTrue(portrait.getHeight() >= viewport[1]);
            assertTrue(portrait.getTranslateY() + playerParent.getTop() <= 0);
            assertEquals(144, header.getTop());
            assertEquals(0f, header.getTranslationY(), 0f);
            Edge edge = new Edge(player);
            HoldVideoViewport.begin(edge);
            message.setVisibility(View.GONE); strip.setVisibility(View.GONE);
            assertTrue(SeeThroughStatusBar.wantsNativeFill(player));
            assertEquals(viewport[1], SeeThroughStatusBar.nativeFillViewport(player)[1], 0f);
            HoldVideoViewport.end(edge);
            message.setVisibility(View.VISIBLE); strip.setVisibility(View.VISIBLE);
            SeeThroughStatusBar.nativeFillViewport(player);
            assertFalse(HoldVideoViewport.isHolding(player));
            Result landscape = new Result(1920, 1080);
            assertSame(landscape, VideoFit.fitted(player, landscape));
            View guide = new View(activity); FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(600, 80);
            gp.topMargin = 20; gp.bottomMargin = 30; root.addView(guide, gp);
            for (int i = 0; i < 4; i++) HoldVideoViewport.positionGuide(guide);
            assertEquals(32, gp.topMargin); assertEquals(18, gp.bottomMargin);
            keyboard.insets = new WindowInsets.Builder().setInsets(WindowInsets.Type.ime(), Insets.of(0,0,0,900))
                    .setVisible(WindowInsets.Type.ime(), true).build();
            NativeSafeArea.measureDetailInput(keyboard);
            assertEquals(0, row.getPaddingBottom()); assertEquals(158, strip.getLayoutParams().height);
            message.setVisibility(View.GONE);
            assertFalse(MessageVideoPage.active(activity));
            assertFalse(SeeThroughStatusBar.wantsNativeFill(player));
            NativeSafeArea.releaseDetailInput(keyboard);
        } finally {
            cache(NativeSafeArea.class).clear(); cache(MessageVideoPage.class).clear();
            SettingsStatus.videoOverlaysEnabled = false;
        }
    }
}
