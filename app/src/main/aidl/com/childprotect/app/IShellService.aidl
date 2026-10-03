package com.childprotect.app;

/**
 * Runs inside the Shizuku-spawned process (UID 2000 / shell). The app binds to
 * this and calls exec() to run privileged shell commands with no root.
 */
interface IShellService {
    /** Run a shell command, return combined stdout+stderr (best-effort). */
    String exec(String command) = 1;

    /** Shizuku calls this (reserved transaction id) to stop the remote process. */
    void destroy() = 16777114;
}
