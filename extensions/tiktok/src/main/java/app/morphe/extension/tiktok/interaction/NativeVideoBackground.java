package app.morphe.extension.tiktok.interaction;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.feed.SeeThroughStatusBar;
import app.morphe.extension.shared.Logger;

/** Static blurred cover painted by the original player container, with TikTok's cover loader. */
public final class NativeVideoBackground {
    private static final Map<Object, Entry> ENTRIES = new WeakHashMap<>();
    private static final Map<Object, Entry> CALLBACKS = new WeakHashMap<>();
    private static final Map<Object, Watch> WATCHES = new WeakHashMap<>();
    private NativeVideoBackground() { }
    private static final class Watch implements View.OnLayoutChangeListener {
        final WeakReference<Object> component;
        final WeakReference<View> target;
        Watch(Object component, View target) {
            this.component = new WeakReference<>(component); this.target = new WeakReference<>(target);
        }
        @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                int oldLeft, int oldTop, int oldRight, int oldBottom) {
            Object owner = component.get();
            if (owner == null) { view.removeOnLayoutChangeListener(this); return; }
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) bind(owner);
        }
    }
    private static final class Entry {
        final String aid;
        final WeakReference<View> target;
        final Drawable original;
        Drawable painted;
        final Map<View, Drawable> gaps = new WeakHashMap<>();
        Entry(String aid, View view, Drawable original) {
            this.aid = aid; target = new WeakReference<>(view); this.original = original;
        }
    }

    /** Called by the native adaption component on each bind/result, including DM videos. */
    public static void bind(Object component) {
        Object aweme = Reflect.invoke(component, "Dp");
        Object nativeTarget = Reflect.readField(component, "LLJLLIL");
        View view = nativeTarget instanceof View ? (View) nativeTarget : null;
        // The native pager can expand after bind when its bottom strip is measured. Recheck
        // the smart crop threshold on that layout; an initially crop-safe 9:16 video may now
        // need ambient fill. Otherwise the later fitted frame leaves a black status/bar gap.
        Watch watch = WATCHES.get(component);
        if (watch == null || watch.target.get() != view) {
            if (watch != null && watch.target.get() != null) watch.target.get().removeOnLayoutChangeListener(watch);
            WATCHES.remove(component);
            if (view != null) {
                watch = new Watch(component, view); WATCHES.put(component, watch);
                view.addOnLayoutChangeListener(watch);
            }
        }
        Object video = Reflect.invoke(aweme, "getVideo");
        int width = number(Reflect.invoke(video, "getWidth")), height = number(Reflect.invoke(video, "getHeight"));
        float[] viewport = view == null ? null : SeeThroughStatusBar.nativeFillViewport(view);
        boolean fit = view != null && !StatusBarVideoFill.isPhoto(aweme) && width > 0 && height > 0
                && viewport != null && SeeThroughStatusBar.wantsNativeFill(view)
                && width <= height * 1.5 && !StatusBarVideoFill.cropAllowed(width, height, viewport[0], viewport[1]);
        // The visible-area child can start below native top padding. Paint its existing
        // video_container_area parent so the ambient cover also reaches the status bar.
        if (view != null && view.getParent() instanceof View) {
            View parent = (View) view.getParent();
            try {
                if ("video_container_area".equals(parent.getResources().getResourceEntryName(parent.getId()))) view = parent;
            } catch (android.content.res.Resources.NotFoundException ignored) { }
        }
        String aid = String.valueOf(Reflect.invoke(aweme, "getAid"));
        Entry previous = ENTRIES.get(component);
        if (fit && previous != null && previous.aid.equals(aid) && previous.target.get() == view) return;
        releaseEntry(component);
        if (!fit) return;
        Entry entry = new Entry(aid, view, view.getBackground());
        ENTRIES.put(component, entry);
        try {
            // fq is the native bitmap-cover path; LIZIZ on the same loader returns only a color.
            java.lang.reflect.Field loader = Reflect.field(component.getClass(), "LLLIIL");
            if (loader.get(component) == null)
                loader.set(component, Class.forName("X.0Y8N", false, component.getClass().getClassLoader())
                        .getDeclaredConstructor().newInstance());
            Reflect.invoke(component, "fq");
        } catch (Exception error) {
            release(component);
            Logger.printException(() -> "Could not request native video background", error);
        }
    }

    public static void rememberCover(Object callback) {
        Object component = Reflect.readField(callback, "LL");
        Entry entry = ENTRIES.get(component);
        if (entry != null) CALLBACKS.put(callback, entry);
    }

    /** A stale request is consumed without painting another post's cover on a recycled cell. */
    public static boolean cover(Object callback, Object value) {
        Entry entry = CALLBACKS.remove(callback);
        if (entry == null) return false;
        Object component = Reflect.readField(callback, "LL");
        View view = entry.target.get();
        if (ENTRIES.get(component) != entry || view == null || !(value instanceof Bitmap)) return true;
        Bitmap bitmap = (Bitmap) value;
        if (bitmap.isRecycled()) return true;
        Bitmap softenedCover = blur(bitmap);
        NativeVideoAmbient.cover(view, softenedCover);
        Drawable background = new Cover(softenedCover);
        entry.painted = background;
        for (String field : new String[]{"LLLJIL", "LLLJL"}) {
            Object gap = Reflect.readField(component, field);
            if (gap instanceof View && ((View) gap).getBackground() != null) {
                entry.gaps.put((View) gap, ((View) gap).getBackground());
                ((View) gap).setBackground(null);
            }
        }
        view.setBackground(background);
        return true;
    }

    public static void release(Object component) {
        Watch watch = WATCHES.remove(component);
        if (watch != null && watch.target.get() != null) watch.target.get().removeOnLayoutChangeListener(watch);
        releaseEntry(component);
    }

    private static void releaseEntry(Object component) {
        Entry entry = ENTRIES.remove(component);
        View view = entry == null ? null : entry.target.get();
        if (view != null) NativeVideoAmbient.forget(view);
        if (view != null && view.getBackground() == entry.painted) view.setBackground(entry.original);
        if (entry != null) for (Map.Entry<View, Drawable> gap : entry.gaps.entrySet()) {
            if (gap.getKey().getBackground() == null) gap.getKey().setBackground(gap.getValue());
        }
    }

    private static int number(Object value) { return value instanceof Number ? ((Number) value).intValue() : 0; }

    // Downsample first: three box passes approximate a Gaussian without retaining a full frame,
    // reading the playing texture or doing per-frame CPU/GPU work.
    static Bitmap blur(Bitmap source) {
        float scale = Math.min(1f, 64f / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, Math.round(source.getWidth() * scale));
        int height = Math.max(1, Math.round(source.getHeight() * scale));
        Bitmap small = Bitmap.createScaledBitmap(source, width, height, true);
        int[] pixels = new int[width * height], next = new int[pixels.length];
        small.getPixels(pixels, 0, width, 0, 0, width, height);
        // Separable sliding windows: each pass is linear in pixel count instead of
        // scanning 49 neighbours for every pixel on the UI thread.
        for (int pass = 0; pass < 3; pass++) {
            for (int y = 0; y < height; y++) {
                int red = 0, green = 0, blue = 0, row = y*width;
                for (int k = -3; k <= 3; k++) {
                    int c = pixels[row+Math.max(0,Math.min(width-1,k))];
                    red += c>>16&255; green += c>>8&255; blue += c&255;
                }
                for (int x = 0; x < width; x++) {
                    next[row+x] = 0xff000000 | (red/7)<<16 | (green/7)<<8 | blue/7;
                    int before = pixels[row+Math.max(0,x-3)], after = pixels[row+Math.min(width-1,x+4)];
                    red += (after>>16&255)-(before>>16&255);
                    green += (after>>8&255)-(before>>8&255); blue += (after&255)-(before&255);
                }
            }
            for (int x = 0; x < width; x++) {
                int red = 0, green = 0, blue = 0;
                for (int k = -3; k <= 3; k++) {
                    int c = next[Math.max(0,Math.min(height-1,k))*width+x];
                    red += c>>16&255; green += c>>8&255; blue += c&255;
                }
                for (int y = 0; y < height; y++) {
                    pixels[y*width+x] = 0xff000000 | (red/7)<<16 | (green/7)<<8 | blue/7;
                    int before = next[Math.max(0,y-3)*width+x], after = next[Math.min(height-1,y+4)*width+x];
                    red += (after>>16&255)-(before>>16&255);
                    green += (after>>8&255)-(before>>8&255); blue += (after&255)-(before&255);
                }
            }
        }
        Bitmap result = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        if (small != source) small.recycle();
        return result;
    }

    private static final class Cover extends Drawable {
        final Bitmap bitmap;
        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        final Paint dim = new Paint();
        Cover(Bitmap bitmap) { this.bitmap = bitmap; dim.setColor(0x55000000); }
        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float scale = Math.max(bounds.width() / (float) bitmap.getWidth(), bounds.height() / (float) bitmap.getHeight());
            float width = bitmap.getWidth() * scale, height = bitmap.getHeight() * scale;
            float x = bounds.left + (bounds.width() - width) / 2, y = bounds.top + (bounds.height() - height) / 2;
            canvas.drawBitmap(bitmap, null, new android.graphics.RectF(x, y, x + width, y + height), paint);
            canvas.drawRect(bounds, dim);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
