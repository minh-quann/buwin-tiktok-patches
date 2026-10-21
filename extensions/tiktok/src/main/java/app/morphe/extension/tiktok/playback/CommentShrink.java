/*
 * Copyright 2026 Buwin contributors
 * https://github.com/minh-quann/buwin-tiktok-patches
 */
package app.morphe.extension.tiktok.playback;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;

/**
 * Controls whether video shrinking when comment sheet opens should be prevented.
 */
public final class CommentShrink {
    private CommentShrink() {}

    /**
     * Answers whether video shrink animation should be disabled.
     * Invoked directly by bytecode patch from VideoShrinkComponent.
     */
    public static boolean shouldDisableShrink() {
        return app.morphe.extension.tiktok.interaction.StatusBarVideoFill.keepFullSizeOnComments()
                || (SettingsStatus.commentShrinkControlEnabled && Settings.DISABLE_COMMENT_SHRINK.get());
    }
}
