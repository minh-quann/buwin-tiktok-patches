package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import app.morphe.extension.tiktok.cleardisplay.RememberClearDisplayPatch;
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
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {30, 35}, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NativeBottomBarTest {
    /** Exactly the two overrides injected into TikTok's real bottom component. */
    static class Tabs extends LinearLayout implements android.view.ViewTreeObserver.OnPreDrawListener {
        Tabs(Context context) { super(context); }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            getViewTreeObserver().addOnPreDrawListener(this);
        }
        @Override protected void onDetachedFromWindow() {
            getViewTreeObserver().removeOnPreDrawListener(this);
            super.onDetachedFromWindow();
        }
        @Override public boolean onPreDraw() { return NativeBottomBar.prepareFrame(this); }
        @Override protected void onMeasure(int width, int height) {
            super.onMeasure(width, NativeBottomBar.heightSpec(this, height));
        }
        @Override protected void dispatchDraw(Canvas canvas) {
            NativeBottomBar.drawBackground(this, canvas);
            super.dispatchDraw(canvas);
        }
    }

    @Before public void setup() throws Exception {
        Utils.setContext(RuntimeEnvironment.getApplication());
        SettingsStatus.videoOverlaysEnabled = true;
        Settings.Secure.putInt(RuntimeEnvironment.getApplication().getContentResolver(),
                "secure_gesture_navigation_immersive", 1);
        cache().clear();
    }
    @After public void reset() { SettingsStatus.videoOverlaysEnabled = false; }

    private ResourceIdCache cache() throws Exception {
        var field = NativeBottomBar.class.getDeclaredField("IDS");
        field.setAccessible(true);
        return (ResourceIdCache) field.get(null);
    }
    private void layout(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 240);
    }
    private Tabs tabs(FrameLayout root) {
        Tabs tabs = new Tabs(root.getContext());
        tabs.addView(new View(root.getContext()), new LinearLayout.LayoutParams(100, -1));
        root.addView(tabs, new FrameLayout.LayoutParams(-1, 54, Gravity.BOTTOM));
        return tabs;
    }
    private Bitmap render(FrameLayout root) {
        Bitmap bitmap = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        return bitmap;
    }

    @Test public void nativeMeasureOwnsTheReserveAndDoesNotMoveActivityContent() {
        FrameLayout root = new FrameLayout(RuntimeEnvironment.getApplication());
        root.setPadding(2, 3, 4, 7);
        Tabs tabs = tabs(root);
        layout(root);
        int chin = SeeThroughBottomBar.getChinHeight(root.getContext());
        assertEquals(54 + chin, tabs.getHeight());
        assertEquals(54, tabs.getChildAt(0).getHeight());
        assertEquals(chin, tabs.getPaddingBottom());
        assertEquals(7, root.getPaddingBottom());
        assertNull(root.getBackground());
        assertEquals(1, root.getChildCount());
        assertEquals(54, tabs.getLayoutParams().height);
    }

    @Test public void repeatedMeasureNeverAccumulatesHeightOrPadding() {
        FrameLayout root = new FrameLayout(RuntimeEnvironment.getApplication());
        Tabs tabs = tabs(root);
        tabs.setPadding(0, 0, 0, 5);
        int chin = SeeThroughBottomBar.getChinHeight(root.getContext());
        for (int i = 0; i < 100; i++) {
            tabs.forceLayout(); root.forceLayout(); layout(root);
        }
        assertEquals(54 + chin, tabs.getHeight());
        assertEquals(5 + chin, tabs.getPaddingBottom());
        Settings.Secure.putInt(root.getContext().getContentResolver(), "secure_gesture_navigation_immersive", 0);
        tabs.requestLayout();
        layout(root);
        assertEquals(54, tabs.getHeight());
        assertEquals(5, tabs.getPaddingBottom());
    }

    @Test public void pixelsAcrossTheNativeRowAndChinHaveNoSeamEvenAfterHostRecolors() {
        FrameLayout root = new FrameLayout(RuntimeEnvironment.getApplication());
        root.setBackgroundColor(Color.RED);
        Tabs tabs = tabs(root);
        layout(root);
        tabs.setBackgroundColor(Color.DKGRAY); // host theme rebind after measuring
        Bitmap bitmap = render(root);
        for (int y = tabs.getTop(); y < 240; y++) {
            assertEquals("y=" + y, Color.BLACK, bitmap.getPixel(200, y));
        }
        assertEquals(Color.RED, bitmap.getPixel(200, tabs.getTop() - 1));
    }

    @Test public void hidingOrRemovingTheNativeBarLeavesNoBlackAreaOnOtherPages() {
        FrameLayout root = new FrameLayout(RuntimeEnvironment.getApplication());
        root.setBackgroundColor(Color.RED);
        Tabs tabs = tabs(root);
        layout(root);
        assertEquals(Color.BLACK, render(root).getPixel(200, 239));
        tabs.setVisibility(View.GONE);
        layout(root);
        assertEquals(Color.RED, render(root).getPixel(200, 239));
        root.removeView(tabs);
        layout(root);
        assertEquals(Color.RED, render(root).getPixel(200, 239));
        assertEquals(0, root.getPaddingBottom());
    }

    @Test public void originalDividerIsCollapsedAndStaysCollapsedAfterAHostRebind() throws Exception {
        FrameLayout root = new FrameLayout(RuntimeEnvironment.getApplication());
        View divider = new View(root.getContext());
        divider.setId(0x7f0a0e9c);
        divider.setBackgroundColor(Color.WHITE);
        root.addView(divider, new FrameLayout.LayoutParams(-1, 1));
        cache().putForTests(root.getContext().getPackageName(), "47.4.3:cnj", divider.getId());
        tabs(root);
        layout(root);
        assertEquals(View.GONE, divider.getVisibility());
        assertEquals(0, divider.getLayoutParams().height);
        divider.getLayoutParams().height = 1;
        divider.setVisibility(View.VISIBLE);
        layout(root);
        root.getChildAt(1).forceLayout();
        root.forceLayout();
        layout(root);
        assertEquals(View.GONE, divider.getVisibility());
        assertEquals(0, divider.getLayoutParams().height);
    }

    @Test public void leavingFeedWithinTheSameActivityDoesNotControlTheNativeBlackBackground() {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            FrameLayout root = new FrameLayout(controller.get());
            controller.get().setContentView(root);
            Tabs tabs = tabs(root);
            layout(root);
            assertEquals(Color.BLACK, render(root).getPixel(200, 239));
            tabs.setVisibility(View.INVISIBLE);
            root.setBackgroundColor(Color.RED);
            assertEquals(Color.RED, render(root).getPixel(200, 239));
            tabs.setVisibility(View.VISIBLE);
            assertEquals(Color.BLACK, render(root).getPixel(200, 239));
        }
    }
    @Test public void nativePreDrawCatchesDividerRebindWithoutRemeasuringTheBar() throws Exception {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            FrameLayout root = new FrameLayout(controller.get());
            controller.get().setContentView(root);
            View divider = new View(root.getContext());
            divider.setId(0x7f0a0e9c);
            root.addView(divider, new FrameLayout.LayoutParams(-1, 1));
            cache().putForTests(root.getContext().getPackageName(), "47.4.3:cnj", divider.getId());
            Tabs tabs = tabs(root);
            layout(root);
            divider.getLayoutParams().height = 1;
            divider.setVisibility(View.VISIBLE);
            // No fresh native measure required: component's own callback runs before drawing.
            assertFalse(tabs.onPreDraw());
            assertEquals(View.GONE, divider.getVisibility());
            assertEquals(0, divider.getLayoutParams().height);
            layout(root);
            assertTrue(tabs.onPreDraw());
        }
    }

    @Test public void honorNativeControlsAndSeekTrackUseTheSameBottomBoundary() throws Exception {
        // Geometry read from the Honor's real 47.4.3 hierarchy, rather than a generic screen.
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            FrameLayout mask = new FrameLayout(controller.get());
            controller.get().setContentView(mask);
            FrameLayout cell = new FrameLayout(mask.getContext());
            mask.addView(cell, new FrameLayout.LayoutParams(1320, 2710));
            View widgets = new View(mask.getContext());
            widgets.setId(0x7f0aa089);
            FrameLayout.LayoutParams widgetParams = new FrameLayout.LayoutParams(1320, 2862, Gravity.BOTTOM);
            widgetParams.bottomMargin = -152;
            cell.addView(widgets, widgetParams);
            Tabs bar = tabs(mask);
            bar.setId(0x7f0a4ecc);
            bar.getLayoutParams().height = 158;
            View seek = new View(mask.getContext());
            seek.setId(0x7f0a9cc5);
            mask.addView(seek, new FrameLayout.LayoutParams(1320, 90, Gravity.BOTTOM));
            String pkg = mask.getContext().getPackageName();
            cache().putForTests(pkg, "47.4.3:onp", bar.getId());
            cache().putForTests(pkg, "widget_container", widgets.getId());
            cache().putForTests(pkg, "video_seek_bar", seek.getId());
            int chin = SeeThroughBottomBar.getChinHeight(mask.getContext());
            for (int i = 0; i < 3; i++) {
                NativeBottomBar.measureCell(cell);
                NativeBottomBar.measureFeed(mask);
                mask.measure(View.MeasureSpec.makeMeasureSpec(1320, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(2868, View.MeasureSpec.EXACTLY));
                mask.layout(0, 0, 1320, 2868);
                assertEquals(-chin, widgets.getTop());
                assertEquals(bar.getTop() - 1, seek.getTop() + seek.getHeight() / 2);
                // Next/preloaded page is a whole screen lower. Crop height must remain identical.
                assertEquals(bar.getTop(), SeeThroughBottomBar.getBottomBarTop(cell, 2710));
                cell.setTranslationY(2710);
                assertEquals(bar.getTop(), SeeThroughBottomBar.getBottomBarTop(cell, 2710));
                cell.setTranslationY(0);
            }
            bar.setVisibility(View.GONE);
            NativeBottomBar.measureCell(cell);
            assertEquals(0, widgetParams.topMargin);
            assertEquals(-152, widgetParams.bottomMargin);
        }
    }

    @Test public void holdKeepsCaptionAndSeekPixelsWhenTabsHideAndNativeParentsReflow() throws Exception {
        try (var controller = Robolectric.buildActivity(com.ss.android.ugc.aweme.main.MainActivity.class).setup()) {
            FrameLayout mask = new FrameLayout(controller.get());
            controller.get().setContentView(mask);
            app.morphe.extension.tiktok.settings.Settings.FIT_VIDEO_TO_SCREEN.save(false);
            app.morphe.extension.tiktok.settings.Settings.STATUS_BAR_STYLE.save(
                    app.morphe.extension.tiktok.settings.Settings.STATUS_BAR_STYLE_SCALE);
            FrameLayout cell = new FrameLayout(mask.getContext());
            mask.addView(cell, new FrameLayout.LayoutParams(1320, 2710));
            FrameLayout widgets = new FrameLayout(mask.getContext());
            widgets.setId(0x7f0aa089);
            FrameLayout.LayoutParams widgetParams = new FrameLayout.LayoutParams(1320, 2862, Gravity.BOTTOM);
            widgetParams.bottomMargin = -152;
            cell.addView(widgets, widgetParams);
            TextView title = new TextView(mask.getContext()), description = new TextView(mask.getContext());
            title.setText("Video title"); description.setText("Video description");
            FrameLayout.LayoutParams titleSlot = new FrameLayout.LayoutParams(800, 60, Gravity.BOTTOM);
            titleSlot.bottomMargin = 200;
            FrameLayout.LayoutParams descriptionSlot = new FrameLayout.LayoutParams(800, 100, Gravity.BOTTOM);
            descriptionSlot.bottomMargin = 100;
            widgets.addView(title, titleSlot); widgets.addView(description, descriptionSlot);
            View gesture = new View(mask.getContext());
            cell.addView(gesture, new FrameLayout.LayoutParams(10, 10));
            Tabs bar = tabs(mask);
            bar.setId(0x7f0a4ecc); bar.getLayoutParams().height = 158;
            View seek = new View(mask.getContext()); seek.setId(0x7f0a9cc5);
            mask.addView(seek, new FrameLayout.LayoutParams(1320, 90, Gravity.BOTTOM));
            String pkg = mask.getContext().getPackageName();
            cache().putForTests(pkg, "47.4.3:onp", bar.getId());
            cache().putForTests(pkg, "widget_container", widgets.getId());
            cache().putForTests(pkg, "video_seek_bar", seek.getId());
            for (int i = 0; i < 3; i++) {
                NativeBottomBar.measureCell(cell); NativeBottomBar.measureFeed(mask);
                layoutHonor(mask); NativeBottomBar.prepareFrame(bar);
            }
            layoutHonor(mask);
            int titleY = widgets.getTop() + title.getTop();
            int descriptionY = widgets.getTop() + description.getTop();
            int seekY = seek.getTop() + seek.getHeight() / 2;
            HoldVideoViewportTest.Edge owner = new HoldVideoViewportTest.Edge(gesture);
            HoldVideoViewport.begin(owner);
            assertTrue(HoldVideoViewport.isHolding(mask));
            bar.setVisibility(View.GONE);
            RememberClearDisplayPatch.rememberClearDisplayEvent(new VideoOverlayHiderTest.ClearEvent(true, 0));
            cell.getLayoutParams().height = 2868;
            seek.getLayoutParams().height = 160;
            ((FrameLayout.LayoutParams) seek.getLayoutParams()).bottomMargin = 0;
            widgetParams.topMargin = 0; widgetParams.bottomMargin = -152;
            layoutHonor(mask);
            // Native pre-draw cancels this reflow before a displaced caption can be drawn.
            assertFalse(NativeBottomBar.prepareFrame(bar));
            NativeBottomBar.measureCell(cell); NativeBottomBar.measureFeed(mask); layoutHonor(mask);
            assertEquals(titleY, widgets.getTop() + title.getTop());
            assertEquals(descriptionY, widgets.getTop() + description.getTop());
            assertEquals(seekY, seek.getTop() + seek.getHeight() / 2);
            assertTrue(NativeBottomBar.prepareFrame(bar));
            HoldVideoViewport.end(owner);
            bar.setVisibility(View.VISIBLE);
            RememberClearDisplayPatch.rememberClearDisplayEvent(new VideoOverlayHiderTest.ClearEvent(false, 2));
            SeeThroughStatusBar.nativeFillViewport(gesture);
            assertTrue("tabs alone returning must not unlock the still-expanded widget parent", HoldVideoViewport.isHolding(mask));
            assertEquals(titleY, widgets.getTop() + title.getTop());
            cell.getLayoutParams().height = 2710;
            seek.getLayoutParams().height = 90;
            layoutHonor(mask);
            SeeThroughStatusBar.nativeFillViewport(gesture);
            assertFalse(HoldVideoViewport.isHolding(mask));
            NativeBottomBar.prepareFrame(bar);
            NativeBottomBar.measureCell(cell); NativeBottomBar.measureFeed(mask); layoutHonor(mask);
            assertEquals(titleY, widgets.getTop() + title.getTop());
            assertEquals(descriptionY, widgets.getTop() + description.getTop());
            assertEquals(seekY, seek.getTop() + seek.getHeight() / 2);
        } finally {
            RememberClearDisplayPatch.rememberClearDisplayEvent(new VideoOverlayHiderTest.ClearEvent(false, 2));
        }
    }

    private void layoutHonor(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2868, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1320, 2868);
    }

}
