package com.childprotect.app

import android.os.Process
import android.system.Os

/**
 * Executed by Shizuku INSIDE a process running as the shell user (UID 2000).
 * Everything here runs with ADB-shell privileges — enough to toggle mobile data
 * and location — without root. The app binds to this via Shizuku's UserService
 * API and calls [exec].
 *
 * Shizuku instantiates this with a no-arg constructor (or a Context one); keep it
 * dependency-free so it can load in the remote classloader.
 */
class ShizukuShellService : IShellService.Stub {

    @Suppress("unused")
    constructor()

    override fun exec(command: String): String {
        return try {
            val p = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out.trim()
        } catch (e: Exception) {
            "ERR: ${e.message}"
        }
    }

    override fun destroy() {
        // Kill this helper process. uid shown for debugging context.
        try {
            Os.getuid()
        } catch (_: Throwable) {
        }
        Process.killProcess(Process.myPid())
    }
}
