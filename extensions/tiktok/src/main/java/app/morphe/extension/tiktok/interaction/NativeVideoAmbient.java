package app.morphe.extension.tiktok.interaction;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import app.morphe.extension.tiktok.feed.NativeBottomBar;
import app.morphe.extension.tiktok.feed.SeeThroughStatusBar;

/** Small per-video color samples painted inside the ORIGINAL tabs, never an Activity overlay. */
public final class NativeVideoAmbient {
    private static final Map<View, State> STATES = new WeakHashMap<>();
    private static final Map<View, int[]> COVERS = new WeakHashMap<>();
    private NativeVideoAmbient() { }
    private static final class State {
        WeakReference<View> cell = new WeakReference<>(null);
        WeakReference<View> source = new WeakReference<>(null);
        int[] colors;
        int width, height;
        final Paint color = new Paint(), shade = new Paint();
        final Rect picture = new Rect();
        final Rect visible = new Rect();
        final int[] sourceAt = new int[2], barAt = new int[2];
        boolean dirty = true;
    }
    private static View cell(View source) {
        View cell = SeeThroughStatusBar.findCellRoot(source);
        if (cell != null) {
            try {
                if ("view_rootview".equals(cell.getResources().getResourceEntryName(cell.getId()))) return cell;
            } catch (android.content.res.Resources.NotFoundException ignored) { }
        }
        // Never identify unrelated videos by the shared Activity-content fallback.
        return source;
    }
    public static void cover(View area, Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return;
        View cell = cell(area);
        int[] colors = sample(bitmap);
        COVERS.put(cell, colors);
        View bar = NativeBottomBar.tabs(area);
        State state = bar == null ? null : STATES.get(bar);
        if (state != null && state.cell.get() == cell && state.colors == null) update(bar, state, colors);
    }
    public static void select(View bar, View source) {
        State state = STATES.computeIfAbsent(bar, unused -> new State());
        View cell = cell(source);
        if (state.cell.get() != cell) {
            state.cell = new WeakReference<>(cell);
            state.colors = COVERS.get(cell);
            state.dirty = true;
            bar.invalidate();
        }
        state.source = new WeakReference<>(source);
    }
    public static boolean frame(View bar, View source, Bitmap bitmap) {
        select(bar, source);
        return update(bar, STATES.get(bar), sample(bitmap));
    }
    private static boolean update(View bar, State state, int[] colors) {
        if (Arrays.equals(state.colors, colors)) return false;
        state.colors = colors; state.dirty = true;
        bar.invalidate();
        return true;
    }
    public static boolean hasColors(View bar) { State state = STATES.get(bar); return state != null && state.colors != null; }
    public static void forget(View area) {
        View cell = cell(area); COVERS.remove(cell);
        for (Map.Entry<View, State> entry : STATES.entrySet()) if (entry.getValue().cell.get() == cell) {
            entry.getValue().colors = null; entry.getValue().dirty = true; entry.getKey().invalidate();
        }
    }
    /** At most 96 sampled pixels; no full-frame blur, readback or shader allocation in draw. */
    static int[] sample(Bitmap bitmap) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] result = new int[3];
        for (int band = 0; band < 3; band++) {
            long red = 0, green = 0, blue = 0;
            for (int y = 0; y < 4; y++) for (int x = 0; x < 8; x++) {
                int px = Math.min(width-1, (band*width*8 + x*width + width/2)/24);
                int py = Math.min(height-1, height*2/3 + y*Math.max(1, height/12));
                int color = bitmap.getPixel(px, py);
                red += Color.red(color); green += Color.green(color); blue += Color.blue(color);
            }
            result[band] = Color.rgb((int)(red/32), (int)(green/32), (int)(blue/32));
        }
        return result;
    }
    /** Only a picture touching the bar (or within 8dp) may extend its colors below it. */
    public static boolean reachesBar(View bar, View source) {
        if (source == null || !source.isShown() || source.getRootView() != bar.getRootView()) return false;
        State state = STATES.computeIfAbsent(bar, unused -> new State());
        if (!source.getGlobalVisibleRect(state.visible)) return false;
        bar.getLocationOnScreen(state.barAt);
        int tolerance = Math.round(8 * bar.getResources().getDisplayMetrics().density);
        return state.visible.bottom >= state.barAt[1] - tolerance
                && state.visible.top < state.barAt[1] + bar.getHeight()
                && state.visible.right > state.barAt[0]
                && state.visible.left < state.barAt[0] + bar.getWidth();
    }
    public static void draw(View bar, Canvas canvas) {
        State state = STATES.get(bar);
        View source = state == null ? null : state.source.get();
        if (state == null || state.colors == null || source == null || !source.isShown()
                || source.getRootView() != bar.getRootView() || bar.getWidth() <= 0 || bar.getHeight() <= 0) return;
        if (!reachesBar(bar, source)) return;
        source.getLocationInWindow(state.sourceAt); bar.getLocationInWindow(state.barAt);
        int x = state.sourceAt[0]-state.barAt[0], y = state.sourceAt[1]-state.barAt[1];
        state.picture.set(x, y, x+source.getWidth(), y+source.getHeight());
        if (state.picture.contains(0, 0, bar.getWidth(), bar.getHeight())) return;
        if (state.dirty || state.width != bar.getWidth() || state.height != bar.getHeight()) {
            state.width = bar.getWidth(); state.height = bar.getHeight();
            state.color.setShader(new LinearGradient(0, 0, state.width, 0, state.colors, null, Shader.TileMode.CLAMP));
            state.shade.setShader(new LinearGradient(0, 0, 0, state.height, 0x11000000, 0x77000000, Shader.TileMode.CLAMP));
            state.dirty = false;
        }
        int save = canvas.save();
        // Leave every pixel already covered by the playing picture untouched.
        if (android.os.Build.VERSION.SDK_INT >= 26) canvas.clipOutRect(state.picture);
        else canvas.clipRect(state.picture, android.graphics.Region.Op.DIFFERENCE);
        canvas.drawRect(0, 0, state.width, state.height, state.color);
        canvas.drawRect(0, 0, state.width, state.height, state.shade);
        canvas.restoreToCount(save);
    }
    public static void release(View bar) { STATES.remove(bar); }
}
