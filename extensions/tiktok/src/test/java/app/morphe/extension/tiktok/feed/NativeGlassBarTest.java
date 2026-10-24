package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.graphics.*;
import android.os.Build;
import android.view.*;
import android.widget.*;
import app.morphe.extension.shared.ResourceIdCache;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.glass.NativeGlassRenderer;
import app.morphe.extension.tiktok.glass.NativeGlassBackdrop;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.ChoicePreference;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {30, 35, 37}, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NativeGlassBarTest {
    @Before public void setup() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        SettingsStatus.videoOverlaysEnabled = true;
        Settings.BOTTOM_BAR_STYLE.save("black");
        NativeGlassBar.resetForTests();
        android.provider.Settings.Secure.putInt(Utils.getContext().getContentResolver(),
                "secure_gesture_navigation_immersive", 1);
    }
    @After public void reset() {
        Settings.BOTTOM_BAR_STYLE.resetToDefault(); NativeGlassBar.resetForTests();
        SettingsStatus.videoOverlaysEnabled = false;
    }

    @Test public void changingStyleWaitsForRestartAndPreferenceExplainsIt() {
        assertEquals("black", NativeGlassBar.style());
        Settings.BOTTOM_BAR_STYLE.save("glass");
        assertEquals("glass", Settings.BOTTOM_BAR_STYLE.savedValue());
        assertEquals("black", NativeGlassBar.style());
        NativeGlassBar.resetForTests(); // A fresh process applies the saved choice.
        assertEquals("glass", NativeGlassBar.style());
        assertTrue(Settings.BOTTOM_BAR_STYLE.rebootApp);
        ChoicePreference row = new ChoicePreference(Utils.getContext(), "Bottom bar style", Settings.BOTTOM_BAR_STYLE,
                new String[]{"TikTok original", "Solid black", "Liquid Glass — Kyant"},
                new String[]{"original", "black", "glass"});
        assertTrue(row.getSummary().toString().toLowerCase().contains("restart"));
    }

    @Test public void glassMeasuresNativeTabsWithoutAccumulatingPaddingOrReplacingClicks() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 33);
        Settings.BOTTOM_BAR_STYLE.save("glass"); NativeGlassBar.resetForTests();
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            LinearLayout bar = new LinearLayout(activity);
            bar.setPadding(2, 3, 4, 5);
            View icon = new View(activity); bar.addView(icon, new LinearLayout.LayoutParams(60, 80));
            int[] clicks = {0}; icon.setOnClickListener(v -> clicks[0]++);
            activity.setContentView(bar);
            int spec = View.MeasureSpec.makeMeasureSpec(158, View.MeasureSpec.EXACTLY);
            int chin = SeeThroughBottomBar.getChinHeight(activity);
            float d = activity.getResources().getDisplayMetrics().density;
            for (int i = 0; i < 4; i++) {
                int adjusted = NativeBottomBar.heightSpec(bar, spec);
                assertEquals(Math.round(72 * d), View.MeasureSpec.getSize(adjusted));
                assertEquals(2 + Math.round(16 * d), bar.getPaddingLeft());
                assertEquals(3 + Math.round(8 * d), bar.getPaddingTop());
                assertEquals(5 + Math.round(8 * d), bar.getPaddingBottom());
                assertEquals(Color.TRANSPARENT, ((android.graphics.drawable.ColorDrawable)bar.getBackground()).getColor());
            }
            assertTrue(icon.performClick()); assertEquals(1, clicks[0]);
            NativeGlassBar.release(bar);
        }
    }

    @Test public void originalLeavesNativeHeightPaddingAndBackgroundAlone() {
        Settings.BOTTOM_BAR_STYLE.save("original"); NativeGlassBar.resetForTests();
        View bar = new View(Utils.getContext()); bar.setPadding(2, 3, 4, 5); bar.setBackgroundColor(Color.BLUE);
        int spec = View.MeasureSpec.makeMeasureSpec(158, View.MeasureSpec.EXACTLY);
        assertEquals(spec, NativeBottomBar.heightSpec(bar, spec));
        SeeThroughBottomBar.adjustBottomBarChin(bar);
        assertEquals(5, bar.getPaddingBottom()); assertEquals(2, bar.getPaddingLeft());
        assertEquals(Color.BLUE, ((android.graphics.drawable.ColorDrawable)bar.getBackground()).getColor());
    }

    @Test public void glassUsesFixedAncestorViewportForPreloadedCellsAndKeepsCropLimit() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 33);
        Settings.BOTTOM_BAR_STYLE.save("glass"); NativeGlassBar.resetForTests();
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            FrameLayout host = new FrameLayout(activity), cell = new FrameLayout(activity);
            View video = new View(activity), bar = new View(activity); bar.setId(0x7f0a4bca);
            cell.addView(video); host.addView(cell, new FrameLayout.LayoutParams(1320, 2638));
            host.addView(bar, new FrameLayout.LayoutParams(1320, 230, Gravity.BOTTOM));
            activity.setContentView(host);
            var idsField = NativeBottomBar.class.getDeclaredField("IDS"); idsField.setAccessible(true);
            ((ResourceIdCache)idsField.get(null)).putForTests(activity.getPackageName(), "47.4.3:onp", bar.getId());
            host.measure(View.MeasureSpec.makeMeasureSpec(1320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2868, View.MeasureSpec.EXACTLY));
            host.layout(0, 0, 1320, 2868);
            assertEquals(2638, NativeGlassBar.viewportHeight(video, 0));
            cell.setTranslationY(2868);
            assertEquals(2638, NativeGlassBar.viewportHeight(video, 0));
            assertTrue("Ordinary 16:9 must fill the status bar before any pinch gesture",
                    app.morphe.extension.tiktok.interaction.StatusBarVideoFill.cropAllowed(1080, 1920,
                            1320, NativeGlassBar.viewportHeight(video, 0)));
            assertFalse(app.morphe.extension.tiktok.interaction.StatusBarVideoFill.cropAllowed(1080, 1920, 1320, 2868));
            bar.setVisibility(View.GONE);
            assertEquals(0, NativeGlassBar.viewportHeight(video, 0));
        }
    }

    @Test @Config(qualifiers = "xxxhdpi")
    public void glassFillsBeforePinchAndSharesTheLoweredSeekBoundary() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 33);
        Settings.BOTTOM_BAR_STYLE.save("glass"); NativeGlassBar.resetForTests();
        Settings.STATUS_BAR_STYLE.save(Settings.STATUS_BAR_STYLE_SCALE);
        Settings.FIT_VIDEO_TO_SCREEN.save(false);
        try (var controller = Robolectric.buildActivity(com.ss.android.ugc.aweme.main.MainActivity.class).setup()) {
            Activity activity = controller.get();
            FrameLayout mask = new FrameLayout(activity), cell = new FrameLayout(activity);
            View video = new View(activity), bar = new View(activity), seek = new View(activity);
            bar.setId(0x7f0a4bca); seek.setId(0x7f0a1101);
            cell.addView(video, new FrameLayout.LayoutParams(1320, 2347));
            mask.addView(cell, new FrameLayout.LayoutParams(-1, -1));
            mask.addView(seek, new FrameLayout.LayoutParams(-1, 90, Gravity.BOTTOM));
            mask.addView(bar, new FrameLayout.LayoutParams(-1, NativeGlassBar.reserve(bar), Gravity.BOTTOM));
            activity.setContentView(mask);
            var field = NativeBottomBar.class.getDeclaredField("IDS"); field.setAccessible(true);
            var ids = (ResourceIdCache) field.get(null);
            ids.putForTests(activity.getPackageName(), "47.4.3:onp", bar.getId());
            ids.putForTests(activity.getPackageName(), "video_seek_bar", seek.getId());
            mask.measure(View.MeasureSpec.makeMeasureSpec(1320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2868, View.MeasureSpec.EXACTLY));
            mask.layout(0, 0, 1320, 2868);
            NativeBottomBar.measureFeed(mask);
            mask.measure(View.MeasureSpec.makeMeasureSpec(1320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2868, View.MeasureSpec.EXACTLY));
            mask.layout(0, 0, 1320, 2868);
            assertEquals("Seek must touch the picture side of the same native boundary", bar.getTop()-1, seek.getTop()+seek.getHeight()/2);
            var original = new app.morphe.extension.tiktok.interaction.StatusBarVideoFillTest.Result(1320,2347);
            var before = (app.morphe.extension.tiktok.interaction.StatusBarVideoFillTest.Result)
                    app.morphe.extension.tiktok.interaction.VideoFit.fitted(video, original);
            assertTrue("Fill must work before the first gesture", before.getTranslateY() <= 0);
            assertTrue(before.getHeight() >= bar.getTop());
            var owner = new HoldVideoViewportTest.Edge(video);
            HoldVideoViewport.pinch(owner, true);
            bar.setVisibility(View.GONE);
            var hidden = (app.morphe.extension.tiktok.interaction.StatusBarVideoFillTest.Result)
                    app.morphe.extension.tiktok.interaction.VideoFit.fitted(video, original);
            assertEquals(before.getHeight(), hidden.getHeight());
            assertEquals(before.getTranslateY(), hidden.getTranslateY());
            bar.setVisibility(View.VISIBLE);
            HoldVideoViewport.pinch(owner, false);
            var released = (app.morphe.extension.tiktok.interaction.StatusBarVideoFillTest.Result)
                    app.morphe.extension.tiktok.interaction.VideoFit.fitted(video, original);
            assertEquals(before.getHeight(), released.getHeight());
            assertEquals(before.getTranslateY(), released.getTranslateY());
        }
    }

    @Test public void glassDoesNotReserveTabsInAWindowThatHasNoBottomBar() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 33);
        Settings.BOTTOM_BAR_STYLE.save("glass"); NativeGlassBar.resetForTests();
        FrameLayout cell = new FrameLayout(Utils.getContext());
        View widgets = new View(Utils.getContext()); widgets.setId(0x7f0aa089);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -1); params.bottomMargin = -152;
        cell.addView(widgets, params);
        var idsField = NativeBottomBar.class.getDeclaredField("IDS"); idsField.setAccessible(true);
        ((ResourceIdCache)idsField.get(null)).putForTests(cell.getContext().getPackageName(), "widget_container", widgets.getId());
        NativeBottomBar.measureCell(cell);
        assertEquals(-152, params.bottomMargin);
    }

    @Test public void glassExpandsTheNativePagerBehindTabsAndRestoresItsStrip() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 33);
        Settings.BOTTOM_BAR_STYLE.save("glass"); NativeGlassBar.resetForTests();
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            FrameLayout mask = new FrameLayout(activity), cell = new FrameLayout(activity);
            LinearLayout pager = new LinearLayout(activity); pager.setOrientation(LinearLayout.VERTICAL);
            pager.setId(0x7f0a9ea4);
            pager.addView(cell, new LinearLayout.LayoutParams(-1, 0, 1));
            View strip = new View(activity); strip.setId(0x7f0a0e95);
            pager.addView(strip, new LinearLayout.LayoutParams(-1, 158));
            mask.addView(pager, new FrameLayout.LayoutParams(-1, -1));
            View widgets = new View(activity); widgets.setId(0x7f0aa089);
            cell.addView(widgets, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout bar = new LinearLayout(activity) {
                @Override protected void onMeasure(int width, int height) {
                    super.onMeasure(width, NativeBottomBar.heightSpec(this, height));
                }
            };
            bar.setId(0x7f0a4ecc);
            mask.addView(bar, new FrameLayout.LayoutParams(-1, 158, Gravity.BOTTOM));
            View seek = new View(activity); seek.setId(0x7f0a9cc5);
            mask.addView(seek, new FrameLayout.LayoutParams(-1, 90, Gravity.BOTTOM));
            activity.setContentView(mask);
            var field = NativeBottomBar.class.getDeclaredField("IDS"); field.setAccessible(true);
            ResourceIdCache ids = (ResourceIdCache) field.get(null);
            String pkg = activity.getPackageName();
            ids.putForTests(pkg, "47.4.3:onp", bar.getId());
            ids.putForTests(pkg, "47.4.3:cnc", strip.getId());
            ids.putForTests(pkg, "viewpager_container", pager.getId());
            ids.putForTests(pkg, "widget_container", widgets.getId());
            ids.putForTests(pkg, "video_seek_bar", seek.getId());
            for (int i = 0; i < 3; i++) {
                NativeBottomBar.measureFeed(mask);
                NativeBottomBar.measureCell(cell);
                mask.measure(View.MeasureSpec.makeMeasureSpec(1320, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(2868, View.MeasureSpec.EXACTLY));
                mask.layout(0, 0, 1320, 2868);
            }
            assertEquals(0, strip.getLayoutParams().height);
            assertEquals(2868, cell.getHeight());
            assertEquals(bar.getTop(), widgets.getBottom());
            assertEquals(bar.getTop() - 1, seek.getTop() + seek.getHeight() / 2);
            assertTrue(NativeBottomBar.prepareFrame(bar));
            Settings.BOTTOM_BAR_STYLE.save("black"); NativeGlassBar.resetForTests();
            NativeBottomBar.measureFeed(mask);
            assertEquals(158, strip.getLayoutParams().height);
        }
    }

    @Test public void newStyleLabelsExistInEveryBundledLanguageAndNoticeMatchesSource() throws Exception {
        String[] keys = {"Bottom bar style", "TikTok original", "Solid black", "Liquid Glass — Kyant",
                "Liquid Glass floats the native tabs over the video. Glass effects require Android 13 or newer."};
        var lookup = app.morphe.extension.tiktok.settings.L10nTranslations.class.getDeclaredMethod("of", String.class);
        lookup.setAccessible(true);
        for (String language : new String[]{"az", "de", "es", "in", "it", "pt-rbr", "ru", "tr", "zh-hans"}) {
            java.util.Map<?, ?> table = (java.util.Map<?, ?>) lookup.invoke(null, language);
            for (String key : keys) assertNotNull(language + ": " + key, table.get(key));
        }
        assertEquals(java.nio.file.Files.readString(java.nio.file.Path.of("NOTICE")).trim(),
                app.morphe.extension.tiktok.settings.preference.LicenseNotice.TEXT.trim());
        assertTrue(app.morphe.extension.tiktok.settings.preference.LicenseNotice.TEXT.contains("Copyright 2025 Kyant"));
    }

    @Test public void glassBackgroundDoesNotPaintOutsideItsFloatingPill() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 33);
        View bar = new View(Utils.getContext()); bar.layout(0, 0, 1000, 240);
        Bitmap frame = Bitmap.createBitmap(1000, 240, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(frame); canvas.drawColor(Color.RED);
        NativeGlassRenderer.draw(bar, canvas, 60, false);
        assertEquals(Color.RED, frame.getPixel(0, 120));
        assertEquals(Color.RED, frame.getPixel(500, 239));
        assertEquals("A non-ViewGroup has no fake solid-color glass", Color.RED, frame.getPixel(500, 80));
        NativeGlassRenderer.release(bar); frame.recycle();
    }

    @Test public void pillTracksNativeTabsAndPreservesNativeClicks() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 33);
        Settings.BOTTOM_BAR_STYLE.save("glass"); NativeGlassBar.resetForTests();
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            LinearLayout bar = new LinearLayout(activity);
            int[] clicks = new int[5];
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                View tab = new View(activity);
                tab.setOnClickListener(v -> clicks[idx]++);
                bar.addView(tab, new LinearLayout.LayoutParams(100, 100));
            }
            activity.setContentView(bar);
            bar.layout(0, 0, 1000, 200);

            NativeGlassBar.prepare(bar);
            for (int i = 0; i < 5; i++) {
                assertTrue("Child " + i + " must perform native click", bar.getChildAt(i).performClick());
                assertEquals(1, clicks[i]);
            }
            NativeGlassBar.release(bar);
        }
    }

    @Test public void originalComposeComponentAttachesWithoutReplacingNativeTouchAndRestoresOnRelease() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 33);
        Settings.BOTTOM_BAR_STYLE.save("glass"); NativeGlassBar.resetForTests();
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            LinearLayout bar = new LinearLayout(activity) {
                @Override public int getChildCount() { return NativeGlassBar.nativeChildCount(this, super.getChildCount()); }
            };
            bar.setOrientation(LinearLayout.HORIZONTAL);
            int[] touches = {0}, clicks = {0};
            for (int i = 0; i < 5; i++) {
                View tab = new View(activity);
                tab.setOnClickListener(v -> clicks[0]++);
                tab.setOnTouchListener((v, event) -> { touches[0]++; return false; });
                bar.addView(tab, new LinearLayout.LayoutParams(0, 80, 1));
            }
            View first = bar.getChildAt(0); first.setSelected(true);
            activity.setContentView(bar);
            bar.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY));
            bar.layout(0, 0, 1000, 240);
            NativeGlassRenderer.draw(bar, new Canvas(), 0, false);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertNotNull("The original component is attached INSIDE the native tabs", bar.getChildAt(5));
            assertEquals(bar.getWidth(), bar.getChildAt(5).getWidth());
            assertEquals("The press/shadow area must not be clipped to a 56dp child", bar.getHeight(), bar.getChildAt(5).getHeight());
            assertFalse(bar.getClipChildren()); assertFalse(bar.getClipToPadding());
            assertEquals("Native measure/layout and tab controllers must still see five tabs", 5, bar.getChildCount());
            assertEquals(0f, first.getAlpha(), 0f);
            // TikTok rebinds tab alpha after navigation/fade animations. It must not bring
            // the original icon/label back underneath the visible Compose copy.
            first.setAlpha(1f);
            NativeGlassRenderer.draw(bar, new Canvas(), 0, false);
            assertEquals("A host rebind must not draw two copies of each tab", 0f, first.getAlpha(), 0f);
            assertEquals(View.VISIBLE, first.getVisibility());
            var fade = VideoOverlayHider.class.getDeclaredMethod("fadeTabIcons", View.class, int.class);
            fade.setAccessible(true);
            int width = first.getWidth(), height = first.getHeight();
            for (int level : new int[]{100, 0, 35, 100}) {
                fade.invoke(null, bar, level);
                assertEquals("Fading Glass must not reveal the native copy", 0f, first.getAlpha(), 0f);
                assertEquals(View.VISIBLE, first.getVisibility());
                assertEquals(width, first.getWidth()); assertEquals(height, first.getHeight());
            }
            assertEquals("The bridge never replaces the native touch listener", true, first.performClick());
            assertEquals(1, clicks[0]);
            for (int action : new int[]{android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP}) {
                android.view.MotionEvent event = android.view.MotionEvent.obtain(0, 10, action, 30, 20, 0);
                bar.dispatchTouchEvent(event);
                NativeGlassRenderer.touch(bar, event);
                event.recycle();
            }
            assertEquals(2, touches[0]);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals("Mirrored Compose taps must not navigate twice", 2, clicks[0]);
            for (int action : new int[]{android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP}) {
                android.view.MotionEvent event = android.view.MotionEvent.obtain(20, 30, action, 300, 20, 0);
                bar.dispatchTouchEvent(event);
                NativeGlassRenderer.touch(bar, event);
                event.recycle();
            }
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals("Selecting a different tab also navigates only once", 3, clicks[0]);
            NativeGlassRenderer.release(bar);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(5, bar.getChildCount());
            assertEquals(1f, first.getAlpha(), 0f);
            assertTrue(bar.getClipChildren()); assertTrue(bar.getClipToPadding());
        }
    }

    @Test public void backdropRecordsCurrentPageWithoutCapturingItselfOrDrawingThePlayer() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.WHITE);
        View page = new View(activity); page.setBackgroundColor(Color.BLUE);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        View stripe = new View(activity); stripe.setBackgroundColor(Color.GREEN);
        FrameLayout.LayoutParams stripeParams = new FrameLayout.LayoutParams(100, 100);
        stripeParams.leftMargin = 50; stripeParams.topMargin = 200;
        root.addView(stripe, stripeParams);
        int[] outsideDraws = {0};
        View outside = new View(activity) {
            @Override public void draw(Canvas canvas) { outsideDraws[0]++; super.draw(canvas); }
        };
        root.addView(outside, new FrameLayout.LayoutParams(100, 100));
        SurfaceView player = new SurfaceView(activity) {
            @Override public void draw(Canvas canvas) { throw new AssertionError("Backdrop must not draw a player surface"); }
        };
        FrameLayout.LayoutParams playerParams = new FrameLayout.LayoutParams(100, 100);
        playerParams.leftMargin = 200; playerParams.topMargin = 200;
        root.addView(player, playerParams);
        FrameLayout bar = new FrameLayout(activity); bar.setBackgroundColor(Color.BLACK);
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(-1, 88);
        barParams.topMargin = 200; root.addView(bar, barParams);
        activity.setContentView(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 300);
        int[] location = new int[2]; root.getLocationInWindow(location);
        Bitmap bitmap = Bitmap.createBitmap(400, 88, Bitmap.Config.ARGB_8888);
        int background = NativeGlassBackdrop.draw(root, bar, new Canvas(bitmap),
                new Rect(location[0], location[1]+200, location[0]+400, location[1]+288), null, null, Color.WHITE);
        assertEquals(Color.BLUE, bitmap.getPixel(25, 10));
        assertEquals(Color.GREEN, bitmap.getPixel(75, 10));
        assertEquals("A player without a copied frame leaves the page background", Color.BLUE, bitmap.getPixel(225, 10));
        assertEquals("The tab bar's own black background must never feed back into its glass", Color.BLUE, background);
        assertEquals("Views outside the small backdrop region must not be redrawn", 0, outsideDraws[0]);
        bitmap.recycle();
    }

    @org.robolectric.annotation.Implements(android.view.TextureView.class)
    public static class GpuTextureShadow extends org.robolectric.shadows.ShadowTextureView {
        static int draws;
        @org.robolectric.annotation.Implementation protected void draw(Canvas canvas) {
            assertTrue("Video must be recorded on the GPU",canvas.isHardwareAccelerated());
            draws++; canvas.drawColor(Color.GREEN);
        }
    }
    @Test @org.robolectric.annotation.Config(shadows = GpuTextureShadow.class)
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void textureBackdropRecordsGpuLayerWithoutBitmapReadback() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 33);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        GpuTextureShadow.draws = 0;
        android.view.TextureView texture = new android.view.TextureView(activity) {
            @Override public Bitmap getBitmap(int width, int height) {
                throw new AssertionError("No synchronous video readback");
            }
        };
        View bar = new View(activity);
        root.addView(texture, new FrameLayout.LayoutParams(400,100));
        root.addView(bar, new FrameLayout.LayoutParams(400,100));
        activity.setContentView(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(400,1073741824),View.MeasureSpec.makeMeasureSpec(100,1073741824));
        root.layout(0,0,400,100);
        android.graphics.RenderNode node = new android.graphics.RenderNode("testGpuVideo");
        Canvas gpu = (Canvas)node.getClass().getMethod("beginRecording",int.class,int.class).invoke(node,400,100);
        try { NativeGlassBackdrop.draw(root,bar,gpu,new android.graphics.Rect(0,0,400,100),texture,null,Color.BLACK); }
        finally { node.endRecording(); }
        assertEquals(1,GpuTextureShadow.draws);
        Bitmap output = Bitmap.createBitmap(400,100,Bitmap.Config.ARGB_8888);
        NativeGlassBackdrop.draw(root,bar,new Canvas(output),new android.graphics.Rect(0,0,400,100),texture,null,Color.BLACK);
        assertEquals("Never fall back to a software video snapshot",1,GpuTextureShadow.draws);
        node.discardDisplayList(); output.recycle();
    }

    @Test(timeout = 30000) public void nativeContentPassSkipsGlassAndDoesNotTraverseContentTwice() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 33);
        Settings.BOTTOM_BAR_STYLE.save("glass"); NativeGlassBar.resetForTests();
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            int[] sourceDraws = {0}, barDraws = {0};
            FrameLayout mask = new FrameLayout(activity);
            View source = new View(activity) {
                @Override public void draw(Canvas canvas) { sourceDraws[0]++; canvas.drawColor(Color.BLUE); }
            };
            LinearLayout bar = new LinearLayout(activity) {
                @Override public int getChildCount() { return NativeGlassBar.nativeChildCount(this,super.getChildCount()); }
                @Override public void draw(Canvas canvas) { barDraws[0]++; }
            };
            for (int i = 0; i < 5; i++) bar.addView(new View(activity),new LinearLayout.LayoutParams(80,60));
            bar.getChildAt(1).setSelected(true);
            bar.setId(0x7f0a4ecc);
            mask.addView(source,new FrameLayout.LayoutParams(400,400));
            mask.addView(bar,new FrameLayout.LayoutParams(400,88,Gravity.BOTTOM));
            activity.setContentView(mask);
            var idsField = NativeBottomBar.class.getDeclaredField("IDS"); idsField.setAccessible(true);
            ((ResourceIdCache)idsField.get(null)).putForTests(activity.getPackageName(),"47.4.3:onp",bar.getId());
            mask.measure(View.MeasureSpec.makeMeasureSpec(400,1073741824),View.MeasureSpec.makeMeasureSpec(400,1073741824));
            mask.layout(0,0,400,400);
            NativeGlassRenderer.draw(bar,new Canvas(),0,false);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
            sourceDraws[0] = 0;
            android.graphics.RenderNode output = new android.graphics.RenderNode("nativeMaskTest");
            Canvas gpu = (Canvas)output.getClass().getMethod("beginRecording",int.class,int.class).invoke(output,400,400);
            Canvas pass = NativeGlassRenderer.beginScene(mask,gpu);
            assertNotSame("Hardware content must be recorded into a shared display list",gpu,pass);
            assertFalse(NativeGlassRenderer.skipSceneChild(mask,source));
            assertTrue("Never capture the glass into its own background",NativeGlassRenderer.skipSceneChild(mask,bar));
            source.draw(pass); // Simulates the ORIGINAL parent's single content traversal.
            assertSame(bar,NativeGlassRenderer.endScene(mask,gpu));
            assertFalse(NativeGlassRenderer.skipSceneChild(mask,bar));
            bar.draw(gpu); // The original drawChild draws tabs once after the content.
            output.endRecording();
            assertEquals(1,sourceDraws[0]); assertEquals(1,barDraws[0]);
            android.view.SurfaceView surface = new android.view.SurfaceView(activity);
            mask.addView(surface,new FrameLayout.LayoutParams(400,200)); surface.layout(0,0,400,200);
            var layout = android.view.ViewTreeObserver.class.getDeclaredMethod("dispatchOnGlobalLayout");
            layout.setAccessible(true); layout.invoke(mask.getViewTreeObserver());
            android.graphics.RenderNode surfaceOutput = new android.graphics.RenderNode("surfaceSafety");
            Canvas surfaceCanvas = (Canvas)surfaceOutput.getClass().getMethod("beginRecording",int.class,int.class).invoke(surfaceOutput,400,400);
            assertSame("Never replay a SurfaceView compositor node into refracting glass",surfaceCanvas,NativeGlassRenderer.beginScene(mask,surfaceCanvas));
            assertNull(NativeGlassRenderer.endScene(mask,surfaceCanvas));
            surfaceOutput.endRecording(); surfaceOutput.discardDisplayList();
            Canvas software = new Canvas(Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888));
            assertSame("Software rendering must keep the original native path",software,NativeGlassRenderer.beginScene(mask,software));
            assertNull(NativeGlassRenderer.endScene(mask,software));
            NativeGlassRenderer.release(bar); output.discardDisplayList();
        }
    }

    @Test public void otherTabsRemoveNativeFooterWhileHomeKeepsItsBlackSlot() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 33);
        Settings.BOTTOM_BAR_STYLE.save("glass"); NativeGlassBar.resetForTests();
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            FrameLayout mask = new FrameLayout(activity), page = new FrameLayout(activity);
            page.setBackgroundColor(Color.GREEN);
            View footer = new View(activity); footer.setId(0x7f0a7a11); footer.setBackgroundColor(Color.BLACK);
            page.addView(footer,new FrameLayout.LayoutParams(-1,80,Gravity.BOTTOM));
            mask.addView(page,new FrameLayout.LayoutParams(-1,-1));
            LinearLayout bar = new LinearLayout(activity); bar.setId(0x7f0a4ecc);
            View home = new View(activity), friends = new View(activity);
            home.setId(0x7f0a4ec4); bar.addView(home); bar.addView(friends);
            mask.addView(bar,new FrameLayout.LayoutParams(-1,88,Gravity.BOTTOM)); activity.setContentView(mask);
            var field = NativeBottomBar.class.getDeclaredField("IDS"); field.setAccessible(true);
            ((ResourceIdCache)field.get(null)).putForTests(activity.getPackageName(),"47.4.3:onp",bar.getId());
            field = NativeGlassBar.class.getDeclaredField("FOOTER_IDS"); field.setAccessible(true);
            ((ResourceIdCache)field.get(null)).putForTests(activity.getPackageName(),"47.4.3:wr4",footer.getId());
            field = app.morphe.extension.tiktok.blockauthor.FeedVisibility.class.getDeclaredField("IDS"); field.setAccessible(true);
            ((ResourceIdCache)field.get(null)).putForTests(activity.getPackageName(),"47.4.3:onh",home.getId());
            mask.measure(View.MeasureSpec.makeMeasureSpec(400,1073741824),View.MeasureSpec.makeMeasureSpec(500,1073741824));
            mask.layout(0,0,400,500);
            home.setSelected(true);
            assertFalse(NativeGlassBar.clearPageFooters(mask));
            assertEquals("Home must retain its black footer",80,footer.getLayoutParams().height);
            assertEquals(1f,footer.getAlpha(),0f);
            home.setSelected(false); friends.setSelected(true);
            assertTrue(NativeBottomBar.measureFeed(mask));
            assertEquals(0,footer.getLayoutParams().height); assertEquals(0f,footer.getAlpha(),0f);
            mask.measure(View.MeasureSpec.makeMeasureSpec(400,1073741824),View.MeasureSpec.makeMeasureSpec(500,1073741824));
            mask.layout(0,0,400,500);
            Bitmap output = Bitmap.createBitmap(400,500,Bitmap.Config.ARGB_8888);
            mask.draw(new Canvas(output));
            assertEquals("Content must continue below the middle of the floating bar",Color.GREEN,output.getPixel(200,470));
            assertFalse("An unchanged frame must not request another layout",NativeGlassBar.clearPageFooters(mask));
            // A late native writer can change params after layout has already cleared
            // the mask's request flag. The cached discovery must still enforce the slot.
            assertFalse(mask.isLayoutRequested());
            footer.getLayoutParams().height = 70; footer.setAlpha(1f);
            assertTrue(NativeGlassBar.clearPageFooters(mask));
            assertEquals(0,footer.getLayoutParams().height); assertEquals(0f,footer.getAlpha(),0f);
            ViewGroup.LayoutParams params = footer.getLayoutParams(); params.height = 90; footer.setLayoutParams(params);
            assertTrue("A native rebind must not bring the black slot back",NativeGlassBar.clearPageFooters(mask));
            friends.setSelected(false); home.setSelected(true);
            assertTrue(NativeGlassBar.clearPageFooters(mask));
            assertEquals("Home restores the latest native height",90,footer.getLayoutParams().height);
            assertEquals(1f,footer.getAlpha(),0f);
            assertFalse(NativeGlassBar.clearPageFooters(mask));
            output.recycle();
        }
    }

    @Test public void friendsListExtendsUnderBarAndSurvivesLateLayoutAndEmptySelection() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 33);
        Settings.BOTTOM_BAR_STYLE.save("glass"); NativeGlassBar.resetForTests();
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            FrameLayout mask = new FrameLayout(activity); mask.setBackgroundColor(Color.GREEN);
            FrameLayout list = new FrameLayout(activity); list.setId(0x7f0a28c2); list.setBackgroundColor(Color.BLUE);
            list.setPadding(0,0,0,70);
            FrameLayout.LayoutParams listParams = new FrameLayout.LayoutParams(-1,-1);
            listParams.topMargin = 50; listParams.bottomMargin = 58; mask.addView(list,listParams);
            LinearLayout bar = new LinearLayout(activity); bar.setId(0x7f0a4ecc);
            View home = new View(activity), friends = new View(activity); home.setId(0x7f0a4ec4);
            bar.addView(home); bar.addView(friends); friends.setSelected(true);
            mask.addView(bar,new FrameLayout.LayoutParams(-1,88,Gravity.BOTTOM)); activity.setContentView(mask);
            var field = NativeBottomBar.class.getDeclaredField("IDS"); field.setAccessible(true);
            ((ResourceIdCache)field.get(null)).putForTests(activity.getPackageName(),"47.4.3:onp",bar.getId());
            field = NativeGlassBar.class.getDeclaredField("FOOTER_IDS"); field.setAccessible(true);
            ((ResourceIdCache)field.get(null)).putForTests(activity.getPackageName(),"47.4.3:hj6",list.getId());
            field = app.morphe.extension.tiktok.blockauthor.FeedVisibility.class.getDeclaredField("IDS"); field.setAccessible(true);
            ((ResourceIdCache)field.get(null)).putForTests(activity.getPackageName(),"47.4.3:onh",home.getId());
            mask.measure(View.MeasureSpec.makeMeasureSpec(400,1073741824),View.MeasureSpec.makeMeasureSpec(500,1073741824));
            mask.layout(0,0,400,500);
            assertEquals(442,list.getBottom());
            assertTrue(NativeGlassBar.clearPageFooters(mask));
            mask.measure(View.MeasureSpec.makeMeasureSpec(400,1073741824),View.MeasureSpec.makeMeasureSpec(500,1073741824));
            mask.layout(0,0,400,500);
            Bitmap output = Bitmap.createBitmap(400,500,Bitmap.Config.ARGB_8888); mask.draw(new Canvas(output));
            assertEquals("The list's own background must continue below the bar centre",Color.BLUE,output.getPixel(200,470));
            assertEquals(50,list.getTop()); assertEquals(70,list.getPaddingBottom());
            assertFalse(mask.isLayoutRequested());
            listParams.bottomMargin = 58; // Late native mutation with no layout-request flag.
            assertTrue(NativeGlassBar.clearPageFooters(mask)); assertEquals(0,listParams.bottomMargin);
            friends.setSelected(false); // Native tab transaction has no selection for one frame.
            NativeGlassBar.clearPageFooters(mask);
            assertEquals("An empty selection must not restore a black gap",0,listParams.bottomMargin);
            home.setSelected(true); assertTrue(NativeGlassBar.clearPageFooters(mask));
            assertEquals("Home keeps native geometry",58,listParams.bottomMargin);
            output.recycle();
        }
    }

    @Test(timeout = 30000) public void glassOwnsPointerBeforeOverlappingSeekAndKeepsItOutsideTheCapsule() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 33);
        Settings.BOTTOM_BAR_STYLE.save("glass"); NativeGlassBar.resetForTests();
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            FrameLayout mask = new FrameLayout(activity) {
                @Override public boolean dispatchTouchEvent(MotionEvent event) {
                    if (NativeGlassBar.dispatchFromMask(this,event)) return true;
                    return super.dispatchTouchEvent(event);
                }
            };
            LinearLayout bar = new LinearLayout(activity) {
                @Override public int getChildCount() { return NativeGlassBar.nativeChildCount(this,super.getChildCount()); }
                @Override public boolean dispatchTouchEvent(MotionEvent event) {
                    return NativeGlassBar.consumeTouch(this,event) || super.dispatchTouchEvent(event);
                }
            };
            bar.setId(0x7f0a4ecc);
            int[] clicks = {0}, seeks = {0};
            for (int i = 0; i < 5; i++) {
                final int index = i;
                View tab = new View(activity);
                tab.setOnClickListener(v -> {
                    clicks[0]++;
                    for (int j = 0; j < 5; j++) bar.getChildAt(j).setSelected(j == index);
                });
                tab.setSelected(i == 0);
                bar.addView(tab,new LinearLayout.LayoutParams(80,56));
            }
            mask.addView(bar,new FrameLayout.LayoutParams(-1,NativeGlassBar.reserve(bar),Gravity.BOTTOM));
            View seek = new View(activity); seek.setOnTouchListener((v,event) -> { seeks[0]++; return true; });
            mask.addView(seek,new FrameLayout.LayoutParams(-1,180,Gravity.BOTTOM));
            activity.setContentView(mask);
            var field = NativeBottomBar.class.getDeclaredField("IDS"); field.setAccessible(true);
            ((ResourceIdCache)field.get(null)).putForTests(activity.getPackageName(),"47.4.3:onp",bar.getId());
            mask.measure(View.MeasureSpec.makeMeasureSpec(400,1073741824),View.MeasureSpec.makeMeasureSpec(500,1073741824));
            mask.layout(0,0,400,500);
            NativeGlassRenderer.draw(bar,new Canvas(),0,false);
            var looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper());
            looper.idleFor(java.time.Duration.ofMillis(500));
            long down = android.os.SystemClock.uptimeMillis();
            float density = bar.getResources().getDisplayMetrics().density;
            float homeX = bar.getLeft()+16*density+(bar.getWidth()-32*density)*0.1f;
            float capsuleY = bar.getTop()+36*density;
            float seekY = bar.getTop()-10*density;
            float beyondX = bar.getLeft()+bar.getWidth()+50;
            // The lowered capsule overlaps native seek. Claim it before native dispatch.
            MotionEvent event = MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,homeX,capsuleY,0);
            assertTrue(mask.dispatchTouchEvent(event)); event.recycle();
            looper.idleFor(java.time.Duration.ofMillis(160));
            for (float x : new float[]{bar.getWidth()*0.3f,bar.getWidth()*0.5f,bar.getWidth()*0.8f,beyondX}) {
                event = MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x,capsuleY,0);
                assertTrue("An owned drag stays with the pill outside its bounds",mask.dispatchTouchEvent(event)); event.recycle();
                looper.idleFor(java.time.Duration.ofMillis(16));
            }
            event = MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,beyondX,capsuleY,0);
            assertTrue(mask.dispatchTouchEvent(event)); event.recycle();
            looper.idleFor(java.time.Duration.ofMillis(250));
            assertEquals("No part of the capsule gesture may reach seek",0,seeks[0]);
            assertTrue("The original Compose drag must navigate through native actions",clicks[0] > 0);
            assertEquals("A drag must select only once",1,clicks[0]);
            float friendX = bar.getLeft()+16*density+(bar.getWidth()-32*density)*0.3f;
            down = android.os.SystemClock.uptimeMillis();
            event = MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,friendX,capsuleY,0);
            assertTrue(mask.dispatchTouchEvent(event)); event.recycle();
            looper.idleFor(java.time.Duration.ofMillis(32));
            event = MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,friendX,capsuleY,0);
            assertTrue(mask.dispatchTouchEvent(event)); event.recycle();
            looper.idleFor(java.time.Duration.ofMillis(100));
            assertTrue("A plain tap must still select its native tab",bar.getChildAt(1).isSelected());
            assertEquals("A tap must invoke native navigation exactly once",2,clicks[0]);
            assertEquals(0,seeks[0]);
            event = MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_DOWN,homeX,seekY,0);
            assertTrue(mask.dispatchTouchEvent(event)); event.recycle();
            assertTrue("A down above the capsule must still reach the original seek",seeks[0] > 0);
            event = MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,homeX,seekY,0);
            mask.dispatchTouchEvent(event); event.recycle();
            NativeGlassRenderer.release(bar); looper.idle();
        }
    }

    @Test public void ambientBelongsOnlyToTheSelectedHomeTab() throws Exception {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            LinearLayout bar = new LinearLayout(activity);
            View home = new View(activity), friends = new View(activity);
            home.setId(0x7f0a4eb7); friends.setId(0x7f0a4eb6);
            bar.addView(home); bar.addView(friends); activity.setContentView(bar);
            var field = app.morphe.extension.tiktok.blockauthor.FeedVisibility.class.getDeclaredField("IDS"); field.setAccessible(true);
            ((ResourceIdCache)field.get(null)).putForTests(activity.getPackageName(),"47.4.3:onh",home.getId());
            home.setSelected(true); assertTrue(NativeGlassBar.isHome(bar));
            home.setSelected(false); friends.setSelected(true);
            assertFalse("Friends must never receive the Home gradient",NativeGlassBar.isHome(bar));
            friends.setSelected(false); assertFalse(NativeGlassBar.isHome(bar));
        }
    }

    @Test public void backdropSleepsOnStaticPagesAndWakesOnScroll() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 33);
        Settings.BOTTOM_BAR_STYLE.save("glass"); NativeGlassBar.resetForTests();
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            LinearLayout bar = new LinearLayout(activity) {
                @Override public int getChildCount() { return NativeGlassBar.nativeChildCount(this, super.getChildCount()); }
            };
            bar.addView(new View(activity), new LinearLayout.LayoutParams(100, 60));
            activity.setContentView(bar);
            bar.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(88, View.MeasureSpec.EXACTLY));
            bar.layout(0, 0, 400, 88);
            NativeGlassRenderer.draw(bar, new Canvas(), 0, false);
            var looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper());
            looper.idleFor(java.time.Duration.ofSeconds(2));
            var field = Class.forName("app.morphe.extension.tiktok.glass.NativeLiquidTabs").getDeclaredField("hosts");
            field.setAccessible(true);
            Object host = ((java.util.Map<?, ?>)field.get(null)).get(bar);
            var version = host.getClass().getDeclaredMethod("getBackdropVersion"); version.setAccessible(true);
            int idleVersion = (int)version.invoke(host);
            looper.idleFor(java.time.Duration.ofSeconds(2));
            assertEquals("Static pages must stop recording/invalidation", idleVersion, (int)version.invoke(host));
            var scroll = android.view.ViewTreeObserver.class.getDeclaredMethod("dispatchOnScrollChanged");
            scroll.setAccessible(true); scroll.invoke(bar.getViewTreeObserver());
            looper.idle();
            assertTrue("Scrolling must refresh the native page behind the glass", (int)version.invoke(host) > idleVersion);
            NativeGlassRenderer.release(bar);
            looper.idle();
        }
    }
}
