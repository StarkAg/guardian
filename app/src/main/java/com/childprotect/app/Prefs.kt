package com.childprotect.app

import android.content.Context

/** Tiny wrapper around SharedPreferences for the owner's settings. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("child_protect", Context.MODE_PRIVATE)

    var secretCode: String
        get() = sp.getString("secret_code", "") ?: ""
        set(v) = sp.edit().putString("secret_code", v).apply()

    /** Only reply to this number if set; blank = reply to whoever sent the trigger. */
    var allowedSender: String
        get() = sp.getString("allowed_sender", "") ?: ""
        set(v) = sp.edit().putString("allowed_sender", v).apply()

    // ---- Privilege backends -------------------------------------------------

    /** Use Shizuku (shell-UID helper) for privileged actions when available. */
    var useShizuku: Boolean
        get() = sp.getBoolean("use_shizuku", true)
        set(v) = sp.edit().putBoolean("use_shizuku", v).apply()

    /** Allow `su` fallback on rooted devices when Shizuku is unavailable. */
    var attemptRootData: Boolean
        get() = sp.getBoolean("attempt_root_data", true)
        set(v) = sp.edit().putBoolean("attempt_root_data", v).apply()

    // ---- Auto-enable on trigger --------------------------------------------

    /** Turn mobile data ON (via Shizuku/root) before responding. */
    var autoEnableData: Boolean
        get() = sp.getBoolean("auto_enable_data", true)
        set(v) = sp.edit().putBoolean("auto_enable_data", v).apply()

    /** Turn high-accuracy location ON (via Shizuku/root) before locating. */
    var autoEnableLocation: Boolean
        get() = sp.getBoolean("auto_enable_location", true)
        set(v) = sp.edit().putBoolean("auto_enable_location", v).apply()

    // ---- Feature toggles ----------------------------------------------------

    /** Allow the RING command to sound the find-phone alarm. */
    var ringEnabled: Boolean
        get() = sp.getBoolean("ring_enabled", true)
        set(v) = sp.edit().putBoolean("ring_enabled", v).apply()

    /** Auto-alert the allowed number when the SIM changes. */
    var simChangeAlert: Boolean
        get() = sp.getBoolean("sim_change_alert", true)
        set(v) = sp.edit().putBoolean("sim_change_alert", v).apply()

    /** Last-seen SIM ICCID, for change detection. */
    var simIccid: String
        get() = sp.getString("sim_iccid", "") ?: ""
        set(v) = sp.edit().putString("sim_iccid", v).apply()

    /** Auto-send a location report when the battery gets low. */
    var lowBatteryAlert: Boolean
        get() = sp.getBoolean("low_battery_alert", true)
        set(v) = sp.edit().putBoolean("low_battery_alert", v).apply()

    /** Epoch millis of the last auto-alert, to debounce repeats. */
    var lastAlertAt: Long
        get() = sp.getLong("last_alert_at", 0L)
        set(v) = sp.edit().putLong("last_alert_at", v).apply()

    /** Epoch millis of the last SIM-change alert, debounced separately. */
    var lastSimAlertAt: Long
        get() = sp.getLong("last_sim_alert_at", 0L)
        set(v) = sp.edit().putLong("last_sim_alert_at", v).apply()
}
