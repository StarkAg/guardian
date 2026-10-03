package com.childprotect.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Fires on boot and on SIM state changes. If the SIM fingerprint differs from
 * the stored one, it means the SIM was swapped — the classic sign of a stolen
 * phone — so we auto-text the owner the new SIM info plus the location.
 *
 * Note: a manifest-declared SIM_STATE_CHANGED receiver is unreliable on Android
 * 8+ (implicit-broadcast limits), so BOOT_COMPLETED is the dependable trigger —
 * a swapped SIM is detected on the next reboot, which a thief almost always does.
 */
class BootSimReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        if (!prefs.simChangeAlert) return

        val current = simFingerprint(context)
        if (current.isBlank()) return // no SIM / can't read yet

        val stored = prefs.simIccid
        if (stored.isBlank()) {
            // First run: remember this SIM, don't alert.
            prefs.simIccid = current
            return
        }
        if (stored == current) return

        // Debounce: SIM_STATE_CHANGED can fire several times around a swap/boot.
        val now = System.currentTimeMillis()
        if (now - prefs.lastSimAlertAt < DEBOUNCE_MS) {
            prefs.simIccid = current
            return
        }

        Log.w(TAG, "SIM fingerprint changed")
        prefs.simIccid = current
        prefs.lastSimAlertAt = now
        val to = prefs.ownerNumber
        if (to.isNotEmpty()) {
            LocateService.start(
                context,
                replyTo = to,
                command = Command.LOCATE,
                prefix = "⚠️ SIM CHANGED on this phone.\nNow: $current",
            )
        }
    }

    /**
     * Stable identity for the installed SIM(s). Uses SubscriptionManager so we
     * cover dual-SIM and still work on Android 10+ where the raw ICCID/serial is
     * redacted. We combine, per active subscription, the network code (MCC+MNC)
     * and carrier name, sorted so slot order doesn't matter. A swap to a SIM on
     * the same carrier still changes the MCC/MNC pairing in most real cases; when
     * it doesn't, the carrier-name/slot-count change usually will.
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    private fun simFingerprint(context: Context): String {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return legacyFingerprint(context)
        }

        val sm = context.getSystemService(SubscriptionManager::class.java)
            ?: return legacyFingerprint(context)

        val subs = runCatching { sm.activeSubscriptionInfoList }.getOrNull()
        if (subs.isNullOrEmpty()) return legacyFingerprint(context)

        val parts = subs.mapNotNull { info ->
            val mccMnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${info.mccString.orEmpty()}${info.mncString.orEmpty()}"
            } else {
                @Suppress("DEPRECATION")
                "${info.mcc}${info.mnc}"
            }
            val carrier = info.carrierName?.toString()?.trim().orEmpty()
            val piece = "$mccMnc:$carrier".trim(':')
            piece.ifBlank { null }
        }.sorted()

        return if (parts.isEmpty()) legacyFingerprint(context) else parts.joinToString("|")
    }

    /** Pre-permission / fallback identity from TelephonyManager operator fields. */
    @SuppressLint("MissingPermission", "HardwareIds")
    private fun legacyFingerprint(context: Context): String {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return ""
        val op = runCatching { tm.simOperator }.getOrNull().orEmpty()
        val name = runCatching { tm.simOperatorName }.getOrNull().orEmpty()
        val fp = "$op:$name".trim(':')
        return if (fp.isBlank()) "" else fp
    }

    companion object {
        private const val TAG = "BootSimReceiver"
        private const val DEBOUNCE_MS = 5 * 60 * 1000L
    }
}
