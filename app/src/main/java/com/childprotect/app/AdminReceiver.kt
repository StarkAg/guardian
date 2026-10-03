package com.childprotect.app

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Device-admin receiver. While this admin is *active*, Android will not let the
 * app be uninstalled until admin is first deactivated — which requires getting
 * past the lock screen / into Settings. That is the anti-theft uninstall
 * protection: a thief who grabs the phone can't just long-press and remove it.
 *
 * The legitimate owner can always turn it off from
 * Settings → Security → Device admin apps, then uninstall normally.
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled — uninstall protection active")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.w(TAG, "Device admin disabled — uninstall protection removed")
    }

    /**
     * Shown in the system dialog when someone tries to deactivate admin. A thief
     * seeing this is a deterrent; it does not block the owner.
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off removes anti-theft uninstall protection. " +
            "If this phone is lost or stolen, you may not be able to find it."

    companion object {
        private const val TAG = "AdminReceiver"

        fun component(context: Context): ComponentName =
            ComponentName(context, AdminReceiver::class.java)

        fun dpm(context: Context): DevicePolicyManager =
            context.getSystemService(DevicePolicyManager::class.java)

        fun isActive(context: Context): Boolean =
            dpm(context).isAdminActive(component(context))

        /** Remotely lock the screen. Requires active admin + force-lock policy. */
        fun lockNow(context: Context): Boolean = runCatching {
            if (!isActive(context)) return false
            dpm(context).lockNow()
            true
        }.getOrElse {
            Log.w(TAG, "lockNow failed: ${it.message}")
            false
        }
    }
}
