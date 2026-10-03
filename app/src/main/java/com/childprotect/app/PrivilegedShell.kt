package com.childprotect.app

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * Runs privileged shell commands with the best backend available, in order:
 *
 *   1. Shizuku  — a helper process running as the shell user (UID 2000). No root.
 *                 Started once by the owner via USB / wireless debugging.
 *   2. root     — `su -c <cmd>` on rooted devices.
 *   3. none     — [Result.Unavailable]; callers degrade gracefully.
 *
 * The shell user can do what a normal app cannot: `svc data enable` to turn on
 * mobile data, and `settings put secure location_mode 3` to turn on
 * high-accuracy location.
 */
object PrivilegedShell {

    private const val TAG = "PrivilegedShell"

    sealed class Result {
        data class Ok(val out: String, val via: String) : Result()
        object Unavailable : Result()
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun run(context: Context, prefs: Prefs, cmd: String): Result {
        if (prefs.useShizuku) {
            runCatching { runViaShizuku(cmd) }
                .getOrNull()
                ?.let { return Result.Ok(it, "shizuku") }
        }
        if (prefs.attemptRootData) {
            runViaRoot(cmd)?.let { return Result.Ok(it, "root") }
        }
        return Result.Unavailable
    }

    // ---- High-level helpers -------------------------------------------------

    suspend fun enableMobileData(context: Context, prefs: Prefs) =
        run(context, prefs, "svc data enable")

    suspend fun disableMobileData(context: Context, prefs: Prefs) =
        run(context, prefs, "svc data disable")

    /** location_mode 3 = high accuracy (GPS + network). */
    suspend fun enableHighAccuracyLocation(context: Context, prefs: Prefs) =
        run(context, prefs, "settings put secure location_mode 3")

    fun isShizukuReady(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    // ---- Shizuku ------------------------------------------------------------

    private val userServiceArgs: Shizuku.UserServiceArgs
        get() = Shizuku.UserServiceArgs(
            ComponentName(
                BuildConfig.APPLICATION_ID,
                ShizukuShellService::class.java.name
            )
        ).daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(1)

    private suspend fun runViaShizuku(cmd: String): String? {
        if (!isShizukuReady()) return null

        val args = userServiceArgs
        val bound = CompletableDeferred<IShellService?>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                bound.complete(
                    if (binder != null && binder.pingBinder()) {
                        IShellService.Stub.asInterface(binder)
                    } else null
                )
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (!bound.isCompleted) bound.complete(null)
            }
        }

        return try {
            withContext(Dispatchers.Main) { Shizuku.bindUserService(args, conn) }
            val svc = withTimeoutOrNull(10_000) { bound.await() } ?: return null
            withContext(Dispatchers.IO) { svc.exec(cmd) }
        } catch (e: Throwable) {
            Log.w(TAG, "Shizuku exec failed: ${e.message}")
            null
        } finally {
            runCatching {
                withContext(Dispatchers.Main) {
                    Shizuku.unbindUserService(args, conn, true)
                }
            }
        }
    }

    // ---- root ---------------------------------------------------------------

    private suspend fun runViaRoot(cmd: String): String? = withContext(Dispatchers.IO) {
        try {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out.trim()
        } catch (e: Exception) {
            Log.w(TAG, "root exec failed (not rooted?): ${e.message}")
            null
        }
    }
}
