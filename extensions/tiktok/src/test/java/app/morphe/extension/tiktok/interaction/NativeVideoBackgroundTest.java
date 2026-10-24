package app.morphe.extension.tiktok.interaction;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.FrameLayout;
import app.morphe.extension.shared.Utils;
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
@Config(sdk = {30,35}, manifest = Config.NONE)
public class NativeVideoBackgroundTest {
    public static class Video {
        int width = 1080, height = 1080;
        public int getWidth() { return width; }
        public int getHeight() { return height; }
    }
    public static class Post {
        String id = "square";
        Video video = new Video();
        public Video getVideo() { return video; }
        public String getAid() { return id; }
    }
    public static class Component {
        public View LLJLLIL;
        public Object LLLIIL = new Object();
        Post post = new Post();
        int requests;
        public Post Dp() { return post; }
        public void fq() { requests++; }
    }
    public static class Callback {
        public Component LL;
        Callback(Component component) { LL = component; NativeVideoBackground.rememberCover(this); }
    }
    @Test public void nativeCoverPaintsOnlyCurrentCellAndRestoresBackgroundWhenRecycled() {
        try (var controller = Robolectric.buildActivity(com.ss.android.ugc.aweme.main.MainActivity.class).setup()) {
            var activity = controller.get(); Utils.setContext(activity);
            SettingsStatus.videoOverlaysEnabled = true;
            Settings.STATUS_BAR_STYLE.save(Settings.STATUS_BAR_STYLE_SCALE);
            Settings.FIT_VIDEO_TO_SCREEN.save(false);
            FrameLayout cell = new FrameLayout(activity), area = new FrameLayout(activity);
            cell.addView(area, new FrameLayout.LayoutParams(-1,-1)); activity.setContentView(cell);
            cell.measure(View.MeasureSpec.makeMeasureSpec(1320,1073741824), View.MeasureSpec.makeMeasureSpec(2638,1073741824));
            cell.layout(0,0,1320,2638);
            Component component = new Component(); component.LLJLLIL = area;
            ColorDrawable original = new ColorDrawable(Color.BLACK); area.setBackground(original);
            NativeVideoBackground.bind(component); NativeVideoBackground.bind(component);
            assertEquals(1, component.requests);
            Callback current = new Callback(component), stale = new Callback(component);
            Bitmap cover = Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888); cover.eraseColor(Color.BLUE);
            assertTrue(NativeVideoBackground.cover(current,cover));
            assertNotSame(original,area.getBackground());
            component.post.id = "portrait"; component.post.video.height = 2160;
            NativeVideoBackground.bind(component);
            assertSame(original,area.getBackground());
            assertTrue(NativeVideoBackground.cover(stale,cover));
            assertSame(original,area.getBackground());
            assertFalse(cover.isRecycled());
            SettingsStatus.videoOverlaysEnabled = false;
        }
    }
    @Test public void blurIsBoundedAndNeverMutatesTheNativeCachedCover() {
        Bitmap source = Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.RED);
        Bitmap blurred = NativeVideoBackground.blur(source);
        assertEquals(64,blurred.getWidth()); assertEquals(64,blurred.getHeight());
        assertEquals(Color.RED,blurred.getPixel(20,20)); assertFalse(source.isRecycled());
    }
    @Test @Config(sdk = {30,35,37})
    public void nativePagerExpansionRechecksAmbientFillWithoutRebindingThePost() {
        try (var controller = Robolectric.buildActivity(com.ss.android.ugc.aweme.main.MainActivity.class).setup()) {
            var activity = controller.get(); Utils.setContext(activity);
            SettingsStatus.videoOverlaysEnabled = true;
            Settings.STATUS_BAR_STYLE.save(Settings.STATUS_BAR_STYLE_SCALE);
            Settings.FIT_VIDEO_TO_SCREEN.save(false);
            FrameLayout cell = new FrameLayout(activity), area = new FrameLayout(activity);
            cell.addView(area, new FrameLayout.LayoutParams(-1,-1)); activity.setContentView(cell);
            cell.measure(View.MeasureSpec.makeMeasureSpec(1320,1073741824), View.MeasureSpec.makeMeasureSpec(2638,1073741824));
            cell.layout(0,0,1320,2638);
            Component component = new Component(); component.LLJLLIL = area;
            component.post.video.height = 1920;
            NativeVideoBackground.bind(component);
            assertEquals(0, component.requests);
            cell.measure(View.MeasureSpec.makeMeasureSpec(1320,1073741824), View.MeasureSpec.makeMeasureSpec(2868,1073741824));
            cell.layout(0,0,1320,2868);
            assertEquals(1, component.requests);
            NativeVideoBackground.bind(component);
            assertEquals(1, component.requests);
            NativeVideoBackground.release(component);
            cell.layout(0,0,1320,2638); cell.layout(0,0,1320,2868);
            assertEquals(1, component.requests);
            SettingsStatus.videoOverlaysEnabled = false;
        }
    }

    @Test @Config(sdk = {35,37}) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void ambientUsesCurrentVideoColorsOnlyInTheGapAndReusesUnchangedSamples() {
        try (var controller = Robolectric.buildActivity(android.app.Activity.class).setup()) {
            var activity = controller.get(); Utils.setContext(activity);
            FrameLayout root = new FrameLayout(activity);
            View source = new View(activity), next = new View(activity), bar = new View(activity);
            root.addView(source, new FrameLayout.LayoutParams(400, 300));
            root.addView(next, new FrameLayout.LayoutParams(400, 300));
            FrameLayout.LayoutParams slot = new FrameLayout.LayoutParams(400, 100); slot.topMargin = 300;
            root.addView(bar, slot); activity.setContentView(root);
            root.measure(View.MeasureSpec.makeMeasureSpec(400,1073741824), View.MeasureSpec.makeMeasureSpec(400,1073741824));
            root.layout(0,0,400,400);
            Bitmap video = Bitmap.createBitmap(30,20,Bitmap.Config.ARGB_8888);
            for (int y = 0; y < 20; y++) for (int x = 0; x < 30; x++)
                video.setPixel(x,y,x < 10 ? Color.RED : x < 20 ? Color.GREEN : Color.BLUE);
            assertTrue(NativeVideoAmbient.frame(bar,source,video));
            assertFalse("An unchanged sample must not rebuild shaders/redraw", NativeVideoAmbient.frame(bar,source,video));
            Bitmap output = Bitmap.createBitmap(400,100,Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output); canvas.drawColor(Color.MAGENTA);
            NativeVideoAmbient.draw(bar,canvas);
            int left = output.getPixel(10,5), middle = output.getPixel(200,5), right = output.getPixel(390,5);
            assertTrue(Color.red(left) > Color.blue(left));
            assertTrue(Color.green(middle) > Color.red(middle));
            assertTrue(Color.blue(right) > Color.red(right));
            assertTrue("Ambient fades downwards", Color.red(output.getPixel(10,95)) < Color.red(left));
            source.layout(0,0,400,200); // Landscape picture far above the bar, as in the reported screenshot.
            canvas.drawColor(Color.MAGENTA); NativeVideoAmbient.draw(bar,canvas);
            assertFalse(NativeVideoAmbient.reachesBar(bar, source));
            assertEquals("Distant letterbox gaps must remain unchanged",Color.MAGENTA,output.getPixel(10,5));
            source.layout(0,0,400,299);
            assertTrue("A picture at the bar edge may extend its colors",NativeVideoAmbient.reachesBar(bar,source));
            source.layout(0,0,400,400); // The playing picture now covers the complete bar.
            canvas.drawColor(Color.MAGENTA); NativeVideoAmbient.draw(bar,canvas);
            assertEquals("Never paint over real video pixels",Color.MAGENTA,output.getPixel(10,5));
            NativeVideoAmbient.select(bar,next);
            canvas.drawColor(Color.MAGENTA); NativeVideoAmbient.draw(bar,canvas);
            assertEquals("Never carry a previous video's palette to a new cell",Color.MAGENTA,output.getPixel(10,5));
            NativeVideoAmbient.release(bar);
            assertFalse(video.isRecycled());
            video.recycle(); output.recycle();
        }
    }
}
