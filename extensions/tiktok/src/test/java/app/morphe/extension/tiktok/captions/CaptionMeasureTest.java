package app.morphe.extension.tiktok.captions;

import static org.junit.Assert.*;

import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.widget.FrameLayout;
import app.morphe.extension.shared.Utils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CaptionMeasureTest {
    private int captionId;

    /** The native expanded-caption path: a Layout canvas inside a WRAP_CONTENT strip. */
    public static final class LayoutCanvas extends View {
        Layout text;
        int nativeLimit;
        LayoutCanvas() { super(Utils.getContext()); }
        public Layout getTextLayout() { return text; }
        @Override protected void onMeasure(int width, int height) {
            int clipped = Math.min(text.getHeight() + getPaddingTop() + getPaddingBottom(), nativeLimit);
            setMeasuredDimension(text.getWidth(), CaptionStyle.measuredHeight(this, clipped));
        }
    }

    @Before public void setup() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        captionId = View.generateViewId();
        CaptionStyle.resolveForTests("47.4.3:dm6", captionId);
    }

    private static Layout cue(String text, int size) {
        TextPaint paint = new TextPaint();
        paint.setTextSize(size);
        return new StaticLayout(text, paint, 240, Layout.Alignment.ALIGN_NORMAL, 1, 0, true);
    }

    @Test public void wrappedCueAndNativeStripMeasureEveryLineAtLargeFontSize() {
        LayoutCanvas canvas = new LayoutCanvas();
        canvas.setId(captionId);
        canvas.setPadding(6, 7, 4, 9);
        canvas.text = cue("đã đưa iPhone Duo vào loạt thử nghiệm khắc nghiệt", 32);
        assertTrue(canvas.text.getLineCount() > 1);
        canvas.nativeLimit = canvas.text.getLineBottom(0) + 16;
        FrameLayout strip = new FrameLayout(Utils.getContext());
        strip.addView(canvas, new FrameLayout.LayoutParams(-2, -2));
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        strip.measure(unspecified, unspecified);
        int required = canvas.text.getHeight() + 16;
        assertEquals(required, canvas.getMeasuredHeight());
        assertEquals("native background must contain the last line", required, strip.getMeasuredHeight());
    }

    @Test public void sameCanvasRemeasuresLongAndShortCuesWithoutAccumulatingHeight() {
        LayoutCanvas canvas = new LayoutCanvas();
        canvas.setId(captionId);
        canvas.nativeLimit = 20;
        canvas.text = cue("first line\nsecond line\nthird line", 24);
        int width = View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST);
        canvas.measure(width, height);
        int tall = canvas.getMeasuredHeight();
        canvas.text = cue("short cue", 24);
        canvas.requestLayout();
        canvas.measure(width, height);
        assertEquals(canvas.text.getHeight(), canvas.getMeasuredHeight());
        assertTrue(canvas.getMeasuredHeight() < tall);
        canvas.text = cue("one\ntwo", 36);
        canvas.requestLayout();
        canvas.measure(width, height);
        assertEquals(canvas.text.getHeight(), canvas.getMeasuredHeight());
    }

    @Test public void sharedLayoutViewOutsideCaptionsKeepsItsNativeLimit() {
        LayoutCanvas other = new LayoutCanvas();
        other.setId(View.generateViewId());
        other.text = cue("first\nsecond\nthird", 32);
        assertEquals(22, CaptionStyle.measuredHeight(other, 22));
    }

    @Test public void absentEmptyOrAlreadyRoomyLayoutKeepsNativeMeasurement() {
        LayoutCanvas canvas = new LayoutCanvas();
        canvas.setId(captionId);
        assertEquals(31, CaptionStyle.measuredHeight(canvas, 31));
        canvas.text = cue("", 24);
        assertEquals(0, CaptionStyle.measuredHeight(canvas, 0));
        canvas.text = cue("a cue", 24);
        assertEquals(400, CaptionStyle.measuredHeight(canvas, 400));
    }
}
