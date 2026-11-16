package com.childprotect.app

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled — uninstall protection active")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.w(TAG, "Device admin disabled — uninstall protection removed")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.device_admin_disable_warning)

    companion object {
        private const val TAG = "AdminReceiver"
        fun component(context: Context): ComponentName = ComponentName(context, AdminReceiver::class.java)
        fun dpm(context: Context): DevicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)
        fun isActive(context: Context): Boolean = dpm(context).isAdminActive(component(context))
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
