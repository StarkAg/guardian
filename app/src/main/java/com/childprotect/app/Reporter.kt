package com.childprotect.app

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Reporter {
    private const val TAG = "Reporter"
    private val RETRY_BACKOFF_MS = longArrayOf(3_000L, 8_000L, 20_000L, 45_000L)
    private const val SENT_TIMEOUT_MS = 30_000L

    suspend fun sendSms(context: Context, to: String, text: String): Boolean {
        if (to.isBlank()) return false
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "No SEND_SMS permission")
            return false
        }
        val sm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java) else {
            @Suppress("DEPRECATION") SmsManager.getDefault()
        }
        val parts = sm.divideMessage(text)
        val maxAttempts = RETRY_BACKOFF_MS.size + 1
        for (attempt in 0 until maxAttempts) {
            if (sendOnce(context, sm, to, parts)) {
                Log.i(TAG, "Reply sent (${parts.size} part(s)) on attempt ${attempt + 1}")
                return true
            }
            if (attempt < RETRY_BACKOFF_MS.size) {
                Log.w(TAG, "SMS send failed (no service?); retry in ${RETRY_BACKOFF_MS[attempt]}ms")
                delay(RETRY_BACKOFF_MS[attempt])
            }
        }
        Log.w(TAG, "SMS send gave up after $maxAttempts attempts")
        return false
    }

    private suspend fun sendOnce(context: Context, sm: SmsManager, to: String, parts: ArrayList<String>): Boolean {
        val action = "${context.packageName}.SMS_SENT.${System.nanoTime()}"
        val total = parts.size.coerceAtLeast(1)
        return withTimeoutOrNull(SENT_TIMEOUT_MS) {
            suspendCancellableCoroutine<Boolean> { cont ->
                var received = 0
                var allOk = true
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, i: Intent) {
                        received++
                        if (resultCode != Activity.RESULT_OK) allOk = false
                        if (received >= total && cont.isActive) {
                            runCatching { context.unregisterReceiver(this) }
                            cont.resume(allOk)
                        }
                    }
                }
                ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
                val sentIntents = ArrayList<PendingIntent>(parts.size)
                for (idx in parts.indices) sentIntents.add(PendingIntent.getBroadcast(context, idx, Intent(action).setPackage(context.packageName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
                try { sm.sendMultipartTextMessage(to, null, parts, sentIntents, null) }
                catch (e: Exception) {
                    Log.w(TAG, "sendMultipartTextMessage threw: ${e.message}")
                    if (cont.isActive) { runCatching { context.unregisterReceiver(receiver) }; cont.resume(false) }
                }
            }
        } ?: false
    }

    suspend fun locationReport(context: Context, loc: Location?, prefix: String = ""): String {
        if (loc == null) return prefix + context.getString(R.string.sms_location_unavailable, battery(context))
        val link = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
        val addr = reverseGeocode(context, loc)
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(loc.time))
        return buildString {
            if (prefix.isNotEmpty()) append(prefix).append('\n')
            append(link)
            append(context.getString(R.string.sms_location_details, loc.accuracy.toInt(), time, battery(context)))
            if (addr != null) append('\n').append(addr)
        }
    }

    fun statusReport(context: Context, prefs: Prefs): String {
        val priv = if (PrivilegedShell.isShizukuReady()) "Shizuku" else context.getString(R.string.sms_no_privilege)
        val yes = context.getString(R.string.sms_yes)
        val no = context.getString(R.string.sms_no)
        return context.getString(R.string.sms_status_title) + "\n" +
            context.getString(R.string.sms_battery, battery(context)) + "\n" +
            context.getString(R.string.sms_privileged, priv) + "\n" +
            context.getString(R.string.sms_uninstall_protected, if (AdminReceiver.isActive(context)) yes else no) + "\n" +
            context.getString(R.string.sms_location_permission, if (Locator.hasPermission(context)) yes else no) + "\n" +
            context.getString(R.string.sms_auto_data_location, prefs.autoEnableData, prefs.autoEnableLocation)
    }

    fun helpText(context: Context): String = listOf(
        R.string.sms_help_title, R.string.sms_help_locate, R.string.sms_help_ring,
        R.string.sms_help_stop, R.string.sms_help_lock, R.string.sms_help_data,
        R.string.sms_help_location, R.string.sms_help_status, R.string.sms_help_help,
    ).joinToString("\n") { context.getString(it) }

    private fun battery(context: Context): Int {
        val bm = context.getSystemService(BatteryManager::class.java)
        val fromMgr = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (fromMgr in 0..100) return fromMgr
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) level * 100 / scale else -1
    }

    private suspend fun reverseGeocode(context: Context, loc: Location): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        try {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.getAddressLine(0)
        } catch (e: Exception) {
            Log.w(TAG, "Geocode failed: ${e.message}")
            null
        }
    }
}
