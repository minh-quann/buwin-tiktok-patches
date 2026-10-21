package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.content.Context;
import android.graphics.Insets;
import android.provider.Settings;
import android.view.View;
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
@Config(sdk = {30, 35}, manifest = Config.NONE)
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
}
