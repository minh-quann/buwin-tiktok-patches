/*
 * Copyright 2026 Buwin contributors
 * https://github.com/minh-quann/buwin-tiktok-patches
 */
package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import app.morphe.extension.shared.ResourceIdCache;
import app.morphe.extension.tiktok.blockauthor.FeedVisibility;

/** The opened-video page with TikTok's native quick-DM composer, not the chat/profile page. */
public final class MessageVideoPage {
    private static final ResourceIdCache IDS = new ResourceIdCache();
    private MessageVideoPage() { }

    static boolean active(Activity activity) {
        if (!FeedVisibility.isDetailPager(activity)) return false;
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return false;
        int id = IDS.resolve(content.getResources(), activity.getPackageName(), "47.4.3:kyn", false);
        View composer = id == 0 ? null : content.findViewById(id);
        if (!(composer instanceof ViewGroup) || ((ViewGroup) composer).getChildCount() == 0) return false;
        for (View current = composer; current != null;) {
            if (current.getVisibility() != View.VISIBLE) return HoldVideoViewport.hasPendingHold(composer);
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        // A freshly inflated composer may be queried before its first measure.
        return composer.getHeight() == 0 || composer.getGlobalVisibleRect(new Rect())
                || HoldVideoViewport.hasPendingHold(composer);
    }

    /** Stable native pager strip, including its added safe area, for every preloaded cell. */
    static int bottom(View cell, int fullHeight) {
        int columnId = IDS.resolve(cell.getResources(), cell.getContext().getPackageName(), "viewpager_container", false);
        int stripId = IDS.resolve(cell.getResources(), cell.getContext().getPackageName(), "47.4.3:cnc", false);
        if (columnId == 0 || stripId == 0) return fullHeight;
        View column = null;
        for (View current = cell; current != null;) {
            if (current.getId() == columnId) { column = current; break; }
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        if (column == null) column = cell.getRootView().findViewById(columnId);
        if (column instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) column;
            for (int i = 0; i < group.getChildCount(); i++) {
                View strip = group.getChildAt(i);
                if (strip.getId() != stripId || strip.getVisibility() == View.GONE) continue;
                ViewGroup.LayoutParams params = strip.getLayoutParams();
                int height = params != null && params.height >= 0 ? params.height : strip.getMeasuredHeight();
                return height > 0 && height < fullHeight ? fullHeight - height : fullHeight;
            }
        }
        return fullHeight;
    }
}
