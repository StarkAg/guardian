package com.childprotect.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * On ACTION_BATTERY_LOW, send one location report to the owner so the phone can
 * be found before it dies. Debounced to at most once per 30 minutes.
 */
class LowBatteryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BATTERY_LOW) return
        val prefs = Prefs(context)
        if (!prefs.lowBatteryAlert) return

        val to = prefs.ownerNumber
        if (to.isEmpty()) return

        val now = System.currentTimeMillis()
        if (now - prefs.lastAlertAt < DEBOUNCE_MS) return
        prefs.lastAlertAt = now

        Log.i(TAG, "Battery low -> auto-locate to $to")
        LocateService.start(
            context,
            replyTo = to,
            command = Command.LOCATE,
            prefix = "🔋 Battery low. Last location:",
        )
    }

    companion object {
        private const val TAG = "LowBatteryReceiver"
        private const val DEBOUNCE_MS = 30 * 60 * 1000L
    }
}
