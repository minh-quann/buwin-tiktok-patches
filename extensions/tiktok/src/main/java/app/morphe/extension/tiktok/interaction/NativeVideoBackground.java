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
    private NativeVideoBackground() { }
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
        Object video = Reflect.invoke(aweme, "getVideo");
        int width = number(Reflect.invoke(video, "getWidth")), height = number(Reflect.invoke(video, "getHeight"));
        float[] viewport = view == null ? null : SeeThroughStatusBar.nativeFillViewport(view);
        boolean fit = view != null && viewport != null && SeeThroughStatusBar.wantsNativeFill(view)
                && !StatusBarVideoFill.isPhoto(aweme) && width > 0 && height > 0 && width <= height * 1.5
                && !StatusBarVideoFill.cropAllowed(width, height, viewport[0], viewport[1]);
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
        release(component);
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
        Drawable background = new Cover(blur(bitmap));
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
        Entry entry = ENTRIES.remove(component);
        View view = entry == null ? null : entry.target.get();
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
        for (int pass = 0; pass < 3; pass++) {
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int red = 0, green = 0, blue = 0, count = 0;
                for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) {
                    int color = pixels[Math.max(0, Math.min(height - 1, y + dy)) * width
                            + Math.max(0, Math.min(width - 1, x + dx))];
                    red += color >> 16 & 255; green += color >> 8 & 255; blue += color & 255; count++;
                }
                next[y * width + x] = 0xff000000 | (red / count) << 16 | (green / count) << 8 | blue / count;
            }
            int[] swap = pixels; pixels = next; next = swap;
        }
        Bitmap result = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        if (small != source) small.recycle();
        return result;
    }

    private static final class Cover extends Drawable {
        final Bitmap bitmap;
        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        Cover(Bitmap bitmap) { this.bitmap = bitmap; }
        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float scale = Math.max(bounds.width() / (float) bitmap.getWidth(), bounds.height() / (float) bitmap.getHeight());
            float width = bitmap.getWidth() * scale, height = bitmap.getHeight() * scale;
            float x = bounds.left + (bounds.width() - width) / 2, y = bounds.top + (bounds.height() - height) / 2;
            canvas.drawBitmap(bitmap, null, new android.graphics.RectF(x, y, x + width, y + height), paint);
            canvas.drawColor(0x55000000);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
