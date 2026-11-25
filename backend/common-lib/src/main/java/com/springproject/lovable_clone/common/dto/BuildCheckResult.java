package com.springproject.lovable_clone.common.dto;

/**
 * Outcome of a test build.
 *
 * @param ok      true when the project builds (or the check could not run)
 * @param skipped true when the check could not run, for example without Node.js
 * @param output  the build errors, or why the check was skipped
 */
public record BuildCheckResult(boolean ok, boolean skipped, String output) {

    public static BuildCheckResult passed() {
        return new BuildCheckResult(true, false, "");
    }

    public static BuildCheckResult failed(String output) {
        return new BuildCheckResult(false, false, output);
    }

    public static BuildCheckResult skipped(String reason) {
        return new BuildCheckResult(true, true, reason);
    }
}
