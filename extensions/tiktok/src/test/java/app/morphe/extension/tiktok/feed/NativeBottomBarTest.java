package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
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

}
