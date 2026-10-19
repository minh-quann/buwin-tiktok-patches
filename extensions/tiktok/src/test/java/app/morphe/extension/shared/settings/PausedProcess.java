/*
 * Copyright 2026 Buwin contributors
 * https://github.com/minh-quann/buwin-tiktok-patches
 */
package app.morphe.extension.shared.settings;

/**
 * Puts the process into and out of the paused state for a test outside this package. Only
 * {@link BuwinPause} may do that in the app, so the switch stays package-private there.
 */
public final class PausedProcess {
    private PausedProcess() {
    }

    public static void set(boolean paused) {
        Setting.setPausedForProcess(paused);
    }
}
