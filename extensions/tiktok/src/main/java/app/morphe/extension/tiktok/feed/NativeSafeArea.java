package app.morphe.extension.tiktok.feed;

import android.graphics.Rect;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.shared.ResourceIdCache;
import app.morphe.extension.shared.settings.BuwinPause;
import app.morphe.extension.tiktok.blockauthor.FeedVisibility;
import app.morphe.extension.tiktok.settings.SettingsStatus;

/** Shared bottom-safe-area policy consumed by the original keyboard and sheet components. */
public final class NativeSafeArea {
    /** Physical pixels: raise the track 16 above the input, just 1 above its black boundary. */
    private static final int DETAIL_SEEK_OFFSET_PX = 16;
    private static final int SEEK_EDGE_INSET_PX = 1;
    private static final int DETAIL_TOP_BAND_PX = DETAIL_SEEK_OFFSET_PX - SEEK_EDGE_INSET_PX;
    private static final ResourceIdCache IDS = new ResourceIdCache();
    private static final Map<View, int[]> PADDING = new WeakHashMap<>();
    private static final Map<View, int[]> HEIGHTS = new WeakHashMap<>();
    private static final Map<View, java.lang.ref.WeakReference<View>> STRIP_OWNERS = new WeakHashMap<>();
    private static final Map<View, int[]> SEEK_MARGINS = new WeakHashMap<>();
    private static final Map<View, Drawable> STRIP_BACKGROUNDS = new WeakHashMap<>();
    private NativeSafeArea() { }

    public static int minimumBottom(View view) {
        if (!SettingsStatus.videoOverlaysEnabled || BuwinPause.isPaused()
                || SeeThroughBottomBar.isSystemIndicatorActive(view)) return 0;
        WindowInsets insets = view.getRootWindowInsets();
        if (Build.VERSION.SDK_INT >= 30 && insets != null
                && insets.isVisible(WindowInsets.Type.ime())) return 0;
        if (Build.VERSION.SDK_INT < 30) {
            Rect visible = new Rect();
            view.getWindowVisibleDisplayFrame(visible);
            if (visible.bottom > 0 && view.getRootView().getHeight() - visible.bottom
                    > 160 * view.getResources().getDisplayMetrics().density) return 0;
        }
        int minimum = SeeThroughBottomBar.getChinHeight(view.getContext());
        for (android.view.ViewParent p = view.getParent(); p instanceof View; p = p.getParent()) {
            View parent = (View) p;
            if (PADDING.containsKey(parent) && parent.getPaddingBottom() >= minimum) return 0;
        }
        return minimum;
    }

    private static View child(View root, String name) {
        int id = IDS.resolve(root.getResources(), root.getContext().getPackageName(), name, false);
        return id == 0 ? null : root.findViewById(id);
    }

    /** Native chat keyboard root: its message list and composer measure against the same area. */
    public static void measureChat(View root) {
        pad(root, minimumBottom(root));
    }

    /** Native comment drawer: reserve inside its painted panel, not its transparent backdrop. */
    public static void measureCommentSheet(View root) {
        View panel = child(root, "47.4.3:ilv");
        if (panel != null) pad(panel, minimumBottom(root));
    }

    /** Shared Material bottom-sheet behavior also covers sheets outside the comment screen. */
    public static void measureGenericSheet(View sheet) {
        int minimum = minimumBottom(sheet);
        for (Map.Entry<View, int[]> entry : PADDING.entrySet()) {
            View child = entry.getKey();
            if (child == sheet || child.getPaddingBottom() < minimum || minimum == 0) continue;
            for (android.view.ViewParent p = child.getParent(); p instanceof View; p = p.getParent()) {
                if (p == sheet) { minimum = 0; break; }
            }
            if (minimum == 0) break;
        }
        pad(sheet, minimum);
    }

    /** The opened-video composer and its original pager spacer share the added height. */
    public static void measureDetailInput(View root) {
        syncDetailInput(root, false);
    }

    public static int detailHeightSpec(View root, int spec) {
        View footer = child(root, "47.4.3:udg");
        ViewGroup.LayoutParams params = root.getLayoutParams();
        return footer != null && params != null && params.height > 0
                && View.MeasureSpec.getMode(spec) == View.MeasureSpec.EXACTLY
                ? View.MeasureSpec.makeMeasureSpec(params.height, View.MeasureSpec.EXACTLY) : spec;
    }

    /** Complete the reserve from the actual native measurement, including its divider/padding. */
    public static void finishDetailInput(View root) {
        syncDetailInput(root, true);
    }

    /** Called by the native seek mask and after the screen-adaption component writes its strip. */
    public static void measureDetailPage(View page) {
        if (page == null || !FeedVisibility.isDetailPager(SeeThroughStatusBar.activityOf(page))) return;
        View window = page.getRootView();
        View composer = child(window, "47.4.3:qox");
        if (composer == null || !composer.isShown()) composer = child(window, "47.4.3:udk");
        if (composer != null && composer.isShown()) measureDetailInput(composer);
    }

    private static void syncDetailInput(View root, boolean measured) {
        View footer = child(root, "47.4.3:f2t");
        // DetailFeedQuickDMFragment reuses the same native keyboard root, but its real
        // input row is udg (there is no f2t child in that layout).
        if (footer == null) footer = child(root, "47.4.3:udg");
        if (footer == null || footer == root) return;
        int added = pad(footer, minimumBottom(root));
        // Quick DM sets udk to a fixed 49dp in onViewCreated. Its MATCH_PARENT input
        // is otherwise squeezed by precisely the new padding (158 -> 86px on Honor).
        if (child(root, "47.4.3:udg") == footer) resizeFixedComposer(root, added);
        View window = root.getRootView();
        int id = IDS.resolve(root.getResources(), root.getContext().getPackageName(), "47.4.3:cnc", false);
        int columnId = IDS.resolve(root.getResources(), root.getContext().getPackageName(), "viewpager_container", false);
        if (id == 0 || columnId == 0) return;
        for (View strip : VideoOverlayHider.viewsWithId(window, id)) {
            if (!(strip.getParent() instanceof View)) continue;
            View column = (View) strip.getParent();
            if (column.getId() != columnId) continue;
            if (!column.isShown()) continue;
            // A main-feed pager can remain inflated behind an opened post in the same window.
            // Its native tab component already owns that reserve; never resize its strip here.
            boolean mainFeed = false;
            for (android.view.ViewParent p = column.getParent(); p instanceof View; p = p.getParent()) {
                if ("com.ss.android.ugc.aweme.feed.ui.seekbar.VideoSeekBarMaskView".equals(p.getClass().getName())) {
                    int tabs = IDS.resolve(root.getResources(), root.getContext().getPackageName(), "47.4.3:onp", false);
                    mainFeed = tabs != 0 && ((View) p).findViewById(tabs) != null;
                    break;
                }
            }
            if (mainFeed) continue;
            ViewGroup.LayoutParams params = strip.getLayoutParams();
            if (params == null || params.height < 0) continue;
            int[] held = HEIGHTS.get(strip);
            int base = held != null && params.height == held[1] ? held[0] : params.height;
            int extra = root.getVisibility() == View.VISIBLE ? added : 0;
            int band = extra > 0 ? DETAIL_TOP_BAND_PX : 0;
            int height = base + extra + band;
            int measuredReserve = 0;
            if (extra > 0) {
                // Screen adaption may already include the input/safe area in its new strip.
                // Adding extra to that value and taking max() reserves the same room twice.
                // The measured native input is the boundary, even when smaller than the
                // strip TikTok just wrote. Carry it across measure entry too: otherwise each
                // pass alternates between base+extra and the actual input height.
                if (measured && root.getMeasuredHeight() > 0) measuredReserve = root.getMeasuredHeight();
                else if (held != null && held.length > 3 && held[2] == extra) measuredReserve = held[3];
                if (measuredReserve > 0) height = measuredReserve + band;
            }
            paintDetailStrip(strip, band > 0);
            HEIGHTS.put(strip, new int[]{base, height, extra, measuredReserve});
            STRIP_OWNERS.put(strip, new java.lang.ref.WeakReference<>(root));
            if (params.height != height) {
                params.height = height;
                strip.setLayoutParams(params);
            }
            moveDetailSeek(window, strip.getVisibility() == View.GONE ? 0 : extra,
                    strip.getVisibility() == View.GONE ? 0 : height);
        }
    }

    /** Paint the existing native pager spacer; it owns the same edge the player and seek use. */
    private static void paintDetailStrip(View strip, boolean black) {
        if (black) {
            if (!STRIP_BACKGROUNDS.containsKey(strip)) STRIP_BACKGROUNDS.put(strip, strip.getBackground());
            Drawable background = strip.getBackground();
            if (!(background instanceof ColorDrawable) || ((ColorDrawable) background).getColor() != Color.BLACK)
                strip.setBackground(new ColorDrawable(Color.BLACK));
        } else if (STRIP_BACKGROUNDS.containsKey(strip)) {
            Drawable original = STRIP_BACKGROUNDS.remove(strip);
            strip.setBackground(original);
        }
    }

    private static void resizeFixedComposer(View root, int extra) {
        ViewGroup.LayoutParams params = root.getLayoutParams();
        if (params == null || params.height < 0) return;
        int[] held = HEIGHTS.get(root);
        int base = held != null && params.height == held[1] ? held[0] : params.height;
        int height = base + extra;
        HEIGHTS.put(root, new int[]{base, height, extra});
        if (params.height != height) {
            params.height = height;
            root.setLayoutParams(params);
        }
    }

    /** The detail seek slot is a sibling of the pager/composer, so it needs the same reserve. */
    private static void moveDetailSeek(View window, int extra, int reserve) {
        View slot = child(window, "47.4.3:vrd");
        if (slot == null || !(slot.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)
                || HoldVideoViewport.isHolding(slot)) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) slot.getLayoutParams();
        int[] held = SEEK_MARGINS.get(slot);
        int top = held != null && params.topMargin == held[3] ? held[0] : params.topMargin;
        int bottom = held != null && params.bottomMargin == held[4] ? held[1] : params.bottomMargin;
        int targetTop = top - extra, targetBottom = bottom + extra;
        View track = child(slot, "video_seek_bar");
        if (extra > 0 && reserve > 0 && params instanceof android.widget.FrameLayout.LayoutParams
                && (((android.widget.FrameLayout.LayoutParams) params).gravity & android.view.Gravity.VERTICAL_GRAVITY_MASK)
                    == android.view.Gravity.BOTTOM && track != null) {
            int slotHeight = params.height > 0 ? params.height : slot.getMeasuredHeight();
            int trackHeight = track.getMeasuredHeight();
            if (slotHeight > 0 && trackHeight > 0) {
                // The touch row is shorter than the 206px seek slot. Anchoring the entire
                // slot above the input puts the real line through captions. Anchor the track
                // itself, one pixel above the black strip's upper edge. The strip includes
                // the top band, so both the video viewport and the track move together.
                targetTop = top;
                targetBottom = reserve + track.getTop() + trackHeight / 2 - slotHeight + SEEK_EDGE_INSET_PX;
            }
        }
        SEEK_MARGINS.put(slot, new int[]{top, bottom, extra, targetTop, targetBottom});
        if (params.topMargin != targetTop || params.bottomMargin != targetBottom) {
            params.topMargin = targetTop;
            params.bottomMargin = targetBottom;
            slot.setLayoutParams(params);
        }
    }

    public static void releaseDetailInput(View root) {
        boolean released = false;
        for (View strip : new java.util.ArrayList<>(STRIP_OWNERS.keySet())) {
            java.lang.ref.WeakReference<View> owner = STRIP_OWNERS.get(strip);
            if (owner == null || owner.get() != root) continue;
            released = true;
            int[] held = HEIGHTS.remove(strip);
            STRIP_OWNERS.remove(strip);
            paintDetailStrip(strip, false);
            ViewGroup.LayoutParams params = strip.getLayoutParams();
            if (held != null && params != null && params.height == held[1]) {
                params.height = held[0];
                strip.setLayoutParams(params);
            }
        }
        if (released) moveDetailSeek(root.getRootView(), 0, 0);
        resizeFixedComposer(root, 0);
    }

    /** Returns only the space we added. Existing native safe padding is never added twice. */
    static int pad(View target, int minimum) {
        int[] held = PADDING.get(target);
        int current = target.getPaddingBottom();
        int base = held != null && current == held[1] ? held[0] : current;
        int bottom = Math.max(base, minimum);
        PADDING.put(target, new int[]{base, bottom});
        if (current != bottom) target.setPadding(target.getPaddingLeft(), target.getPaddingTop(),
                target.getPaddingRight(), bottom);
        return bottom - base;
    }
}
