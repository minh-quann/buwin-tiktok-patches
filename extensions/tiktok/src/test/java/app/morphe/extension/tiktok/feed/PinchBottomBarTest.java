package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import app.morphe.extension.shared.ResourceIdCache;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.cleardisplay.RememberClearDisplayPatch;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w1320dp-h2868dp-mdpi", manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PinchBottomBarTest {
    /** Native cell draw boundary, independent of the texture's pinch transform. */
    static class Cell extends FrameLayout {
        final Resources names;
        Cell(Context context) {
            super(context);
            Resources base = super.getResources();
            names = new Resources(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration()) {
                @Override public String getResourceEntryName(int id) {
                    return id == 0x7f0a9ddb ? "view_rootview" : base.getResourceEntryName(id);
                }
            };
            setId(0x7f0a9ddb); setClipChildren(false);
        }
        @Override public Resources getResources() { return names == null ? super.getResources() : names; }
        @Override protected void dispatchDraw(Canvas canvas) {
            int save = canvas.save();
            HoldVideoViewport.clipCell(this, canvas);
            super.dispatchDraw(canvas);
            canvas.restoreToCount(save);
        }
    }
    private static void layout(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2868, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1320, 2868);
    }
    private static Bitmap render(View root) {
        Bitmap pixels = Bitmap.createBitmap(1320, 2868, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(pixels));
        return pixels;
    }
    @Test public void zoomCannotPaintOverTheNativeBlackRowEvenWhileItsButtonsFade() throws Exception {
        try (var controller = Robolectric.buildActivity(com.ss.android.ugc.aweme.main.MainActivity.class).setup()) {
            Activity activity = controller.get(); Utils.setContext(activity);
            SettingsStatus.videoOverlaysEnabled = true;
            Settings.FIT_VIDEO_TO_SCREEN.save(false); Settings.STATUS_BAR_STYLE.save(Settings.STATUS_BAR_STYLE_SCALE);
            RememberClearDisplayPatch.rememberClearDisplayEvent(new HoldVideoViewportTest.ClearEvent(false, 2));
            FrameLayout mask = new FrameLayout(activity); mask.setBackgroundColor(Color.BLACK);
            Cell cell = new Cell(activity); mask.addView(cell, new FrameLayout.LayoutParams(-1, -1));
            View video = new View(activity); video.setBackgroundColor(Color.BLUE);
            video.setPivotX(0); video.setPivotY(0); video.setScaleX(1.5f); video.setScaleY(1.5f);
            cell.addView(video, new FrameLayout.LayoutParams(1320, 2638));
            View tabs = new View(activity); tabs.setId(0x7f0a4ecc); tabs.setBackgroundColor(Color.BLACK);
            mask.addView(tabs, new FrameLayout.LayoutParams(-1, 230, Gravity.BOTTOM));
            FrameLayout clear = new FrameLayout(activity); clear.setId(0x7f0a3c1e);
            View button = new View(activity); button.setBackgroundColor(Color.WHITE);
            FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(128, 128);
            buttonParams.leftMargin = 38; buttonParams.topMargin = 28; clear.addView(button, buttonParams);
            mask.addView(clear, new FrameLayout.LayoutParams(-1, 230, Gravity.BOTTOM));
            clear.setVisibility(View.GONE); activity.setContentView(mask);
            layout(mask.getRootView()); layout(activity.findViewById(android.R.id.content));
            var field = NativeBottomBar.class.getDeclaredField("IDS"); field.setAccessible(true);
            ResourceIdCache ids = (ResourceIdCache) field.get(null);
            ids.putForTests(activity.getPackageName(), "47.4.3:l6f", clear.getId());
            ids.putForTests(activity.getPackageName(), "47.4.3:onp", tabs.getId());
            SeeThroughBottomBar.adjustBottomBarChin(tabs);
            var owner = new HoldVideoViewportTest.Edge(video);
            HoldVideoViewport.pinch(owner, true);
            tabs.setVisibility(View.GONE); clear.setVisibility(View.VISIBLE);
            RememberClearDisplayPatch.rememberClearDisplayEvent(new HoldVideoViewportTest.ClearEvent(true, 0));
            NativeBottomBar.measureFeed(mask); layout(mask);
            assertEquals(2638, clear.getTop()); assertEquals(230, clear.getHeight());
            assertEquals(3, mask.getChildCount()); assertEquals(1.5f, video.getScaleY(), 0);
            // The native row may fade in; its transparent phase must not expose zoom overflow.
            clear.setAlpha(0f);
            Bitmap faded = render(mask);
            assertEquals(Color.BLUE, faded.getPixel(660, 2637));
            for (int y = 2638; y < 2868; y++) assertEquals("y=" + y, Color.BLACK, faded.getPixel(660, y));
            clear.setAlpha(1f); clear.setBackgroundColor(Color.TRANSPARENT);
            NativeBottomBar.prepareFrame(tabs); // native theme rebind without another measure
            assertEquals(Color.BLACK, ((ColorDrawable) clear.getBackground()).getColor());
            Bitmap shown = render(mask);
            assertEquals(Color.BLACK, shown.getPixel(660, 2638));
            assertEquals(Color.WHITE, shown.getPixel(102, 2730));
            assertTrue(clear.getTop() + button.getTop() >= 2638);
            assertTrue(clear.getTop() + button.getBottom() <= 2868);
            HoldVideoViewport.pinch(owner, false);
            tabs.setVisibility(View.VISIBLE); clear.setVisibility(View.GONE);
            RememberClearDisplayPatch.rememberClearDisplayEvent(new HoldVideoViewportTest.ClearEvent(false, 2));
            SeeThroughStatusBar.nativeFillViewport(video);
            assertFalse(HoldVideoViewport.isHolding(video));
            Canvas canvas = new Canvas(Bitmap.createBitmap(1320, 2868, Bitmap.Config.ARGB_8888));
            HoldVideoViewport.clipCell(cell, canvas);
            assertEquals(new Rect(0, 0, 1320, 2868), canvas.getClipBounds());
            SettingsStatus.videoOverlaysEnabled = false; NativeBottomBar.measureFeed(mask);
            assertNull(clear.getBackground());
            ids.clear();
        } finally {
            SettingsStatus.videoOverlaysEnabled = false;
            RememberClearDisplayPatch.rememberClearDisplayEvent(new HoldVideoViewportTest.ClearEvent(false, 2));
        }
    }
}
