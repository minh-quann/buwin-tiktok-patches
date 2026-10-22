package app.morphe.extension.tiktok.interaction;

import android.graphics.Bitmap;
import android.graphics.Color;
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
}
