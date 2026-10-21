package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {30, 35}, manifest = Config.NONE)
public class FeedBottomInsetTest {
    private FrameLayout root() {
        FrameLayout root = new FrameLayout(RuntimeEnvironment.getApplication());
        root.setId(android.R.id.content);
        return root;
    }

    private void layout(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 1000);
    }

    @Test public void nativePagerAndTabsShareReducedViewportWithoutGrowingTabs() {
        FrameLayout root = root();
        FrameLayout page = new FrameLayout(root.getContext());
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        View pager = new View(root.getContext());
        page.addView(pager, new FrameLayout.LayoutParams(-1, -1));
        View tabs = new View(root.getContext());
        page.addView(tabs, new FrameLayout.LayoutParams(-1, 54, Gravity.BOTTOM));
        assertTrue(FeedBottomInset.apply(root, 72));
        layout(root);
        assertEquals(928, page.getHeight());
        assertEquals(928, pager.getHeight());
        assertEquals(928, tabs.getBottom());
        assertEquals(54, tabs.getHeight());
        assertEquals(0f, tabs.getTranslationY(), 0f);
        assertEquals(1, root.getChildCount()); // no foreground black overlay
        assertEquals(Color.BLACK, ((ColorDrawable) root.getBackground()).getColor());
    }

    @Test public void repeatedPassesDoNotAccumulateAndTurningReserveOffRestoresGeometry() {
        FrameLayout root = root();
        root.setPadding(2, 3, 4, 7);
        Drawable original = new ColorDrawable(Color.RED);
        root.setBackground(original);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.addView(new View(root.getContext()), new FrameLayout.LayoutParams(-1, -1));
        assertTrue(FeedBottomInset.apply(root, 72));
        for (int i = 0; i < 100; i++) assertFalse(FeedBottomInset.apply(root, 72));
        assertEquals(79, root.getPaddingBottom());
        assertTrue(FeedBottomInset.apply(root, 40));
        assertEquals(47, root.getPaddingBottom());
        assertTrue(FeedBottomInset.apply(root, 0));
        layout(root);
        assertEquals(7, root.getPaddingBottom());
        assertEquals(2, root.getPaddingLeft());
        assertEquals(3, root.getPaddingTop());
        assertEquals(4, root.getPaddingRight());
        assertEquals(990, root.getChildAt(0).getHeight());
        assertSame(original, root.getBackground());
        assertFalse(root.getClipChildren());
        assertFalse(root.getClipToPadding());
        assertFalse(FeedBottomInset.apply(root, 0));
    }

    @Test public void clearDisplayUnclippingCannotOpenTheReservedBottomRegion() {
        FrameLayout root = root();
        FrameLayout cell = new FrameLayout(root.getContext());
        View surface = new View(root.getContext());
        root.addView(cell);
        cell.addView(surface);
        FeedBottomInset.apply(root, 72);
        SeeThroughStatusBar.unclipAncestors(surface, null);
        assertFalse(cell.getClipChildren());
        assertTrue(root.getClipChildren());
        assertTrue(root.getClipToPadding());
    }

    @Test public void restoringDoesNotOverwriteAnExternallyReplacedBackground() {
        FrameLayout root = root();
        FeedBottomInset.apply(root, 72);
        Drawable updated = new ColorDrawable(Color.BLUE);
        root.setBackground(updated);
        FeedBottomInset.apply(root, 0);
        assertSame(updated, root.getBackground());
    }
    @Test public void barHeightAndPaddingStayNativeAcrossRepeatedPasses() {
        FrameLayout root = root();
        View tabs = new View(root.getContext());
        root.addView(tabs, new FrameLayout.LayoutParams(-1, 54, Gravity.BOTTOM));
        tabs.setPadding(1, 2, 3, 4);
        for (int i = 0; i < 100; i++) SeeThroughBottomBar.adjustBottomBarChin(tabs);
        assertEquals(54, tabs.getLayoutParams().height);
        assertEquals(4, tabs.getPaddingBottom());
        assertEquals(1, tabs.getPaddingLeft());
    }

    @Test public void barBoundaryUsesCellCoordinatesAndIgnoresAnotherWindow() {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            FrameLayout root = root();
            activity.setContentView(root);
            FrameLayout cell = new FrameLayout(activity);
            root.addView(cell, new FrameLayout.LayoutParams(-1, 800));
            FrameLayout tabContainer = new FrameLayout(activity);
            FrameLayout.LayoutParams outer = new FrameLayout.LayoutParams(-1, 100);
            outer.topMargin = 700;
            root.addView(tabContainer, outer);
            View tabs = new View(activity);
            FrameLayout.LayoutParams inner = new FrameLayout.LayoutParams(-1, 54);
            inner.topMargin = 20;
            tabContainer.addView(tabs, inner);
            layout(root);
            SeeThroughBottomBar.adjustBottomBarChin(tabs);
            assertEquals(720, SeeThroughBottomBar.getBottomBarTop(cell, 800));
            FrameLayout otherWindow = root();
            assertEquals(800, SeeThroughBottomBar.getBottomBarTop(otherWindow, 800));
        }
    }

}
