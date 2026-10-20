/*
 * Copyright 2026 Buwin contributors
 * https://github.com/minh-quann/buwin-tiktok-patches
 */
package app.morphe.patches.tiktok.interaction.videofit

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.addInstruction
import app.morphe.util.addInstructionsWithLabels

private const val SWITCH = "Lapp/morphe/extension/tiktok/playback/CommentShrink;->shouldDisableShrink()Z"
private const val SHRINK_COMPONENT = "Lcom/ss/android/ugc/feed/platform/cell/shrink/VideoShrinkComponent;"

private object VideoShrinkStateFingerprint : Fingerprint(
    definingClass = SHRINK_COMPONENT,
    returnType = "V",
    parameters = listOf("I", "I"),
)

private object VideoShrinkAnimateFingerprint : Fingerprint(
    definingClass = SHRINK_COMPONENT,
    returnType = "V",
    parameters = listOf("J", "Z", "Z"),
)

@Suppress("unused")
val commentVideoShrinkPatch = bytecodePatch(
    name = "Disable video shrink on comments",
    description = "Keeps video full screen when the comment sheet opens instead of shrinking it. " +
        "Prevents status bar black scrim and screen flickering. Turn it on/off in Buwin settings > Playback.",
) {
    category("Playback")
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktokAndPineDrama())

    execute {
        val stateMethod = VideoShrinkStateFingerprint.method
        val animateMethod = VideoShrinkAnimateFingerprint.method
        val settingsStatus = SettingsStatusLoadFingerprint.method

        stateMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static {}, $SWITCH
                move-result v0
                if-eqz v0, :morphe_through_state
                return-void
                :morphe_through_state
                nop
            """,
        )

        animateMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static {}, $SWITCH
                move-result v0
                if-eqz v0, :morphe_through_anim
                return-void
                :morphe_through_anim
                nop
            """,
        )

        settingsStatus.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableCommentShrinkControl()V",
        )
    }
}
