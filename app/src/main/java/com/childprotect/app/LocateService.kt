package com.childprotect.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LocateService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        val prefs = Prefs(this)
        val sender = intent?.getStringExtra(EXTRA_REPLY_TO).orEmpty()
        val command = runCatching { Command.valueOf(intent?.getStringExtra(EXTRA_COMMAND) ?: Command.LOCATE.name) }.getOrDefault(Command.LOCATE)
        val prefix = intent?.getStringExtra(EXTRA_PREFIX).orEmpty()
        val triggerBody = intent?.getStringExtra(EXTRA_BODY).orEmpty()
        scope.launch {
            try { handle(command, sender, prefix, prefs) }
            catch (e: Exception) { Log.e(TAG, "handle failed", e) }
            finally { stealthWipe(sender, triggerBody, prefs); stopSelf() }
        }
        return START_NOT_STICKY
    }

    private suspend fun stealthWipe(sender: String, body: String, prefs: Prefs) {
        if (body.isBlank()) return
        kotlinx.coroutines.delay(1500)
        val r = PrivilegedShell.deleteSms(this, prefs, sender, body)
        Log.i(TAG, "Stealth wipe of trigger SMS: ${if (r is PrivilegedShell.Result.Ok) "done" else "unavailable"}")
    }

    private suspend fun handle(command: Command, sender: String, prefix: String, prefs: Prefs) {
        when (command) {
            Command.HELP -> Reporter.sendSms(this, sender, Reporter.helpText(this))
            Command.STATUS -> Reporter.sendSms(this, sender, Reporter.statusReport(this, prefs))
            Command.DATA_ON -> Reporter.sendSms(this, sender, resultLine(getString(R.string.sms_mobile_data_on), PrivilegedShell.enableMobileData(this, prefs)))
            Command.DATA_OFF -> Reporter.sendSms(this, sender, resultLine(getString(R.string.sms_mobile_data_off), PrivilegedShell.disableMobileData(this, prefs)))
            Command.LOC_ON -> Reporter.sendSms(this, sender, resultLine(getString(R.string.sms_location_on), PrivilegedShell.enableHighAccuracyLocation(this, prefs)))
            Command.RING -> if (prefs.ringEnabled) {
                val ringJob = scope.launch { AlarmPlayer.ring(this@LocateService, 30) }
                Reporter.sendSms(this, sender, getString(R.string.sms_ring_started))
                ringJob.join()
            } else Reporter.sendSms(this, sender, getString(R.string.sms_ring_disabled))
            Command.STOP -> {
                AlarmPlayer.stop()
                Reporter.sendSms(this, sender, getString(R.string.sms_alarm_stopped))
            }
            Command.LOCK -> {
                val locked = AdminReceiver.lockNow(this)
                Reporter.sendSms(this, sender, getString(if (locked) R.string.sms_phone_locked else R.string.sms_phone_lock_failed))
            }
            Command.LOCATE, Command.NONE -> {
                if (prefs.autoEnableLocation) PrivilegedShell.enableHighAccuracyLocation(this, prefs)
                if (prefs.autoEnableData) PrivilegedShell.enableMobileData(this, prefs)
                Reporter.sendSms(this, sender, Reporter.locationReport(this, Locator.current(this), prefix))
            }
        }
    }

    private fun resultLine(action: String, r: PrivilegedShell.Result): String = when (r) {
        is PrivilegedShell.Result.Ok -> getString(R.string.sms_action_succeeded, action, r.via)
        PrivilegedShell.Result.Unavailable -> getString(R.string.sms_action_failed, action)
    }

    private fun buildNotification(): Notification {
        val mgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notification_channel_locator), NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val EXTRA_REPLY_TO = "reply_to"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_PREFIX = "prefix"
        const val EXTRA_BODY = "body"
        private const val TAG = "LocateService"
        private const val CHANNEL = "locator"
        private const val NOTIF_ID = 42

        fun start(context: android.content.Context, replyTo: String, command: Command, prefix: String = "", triggerBody: String = "") {
            val i = Intent(context, LocateService::class.java).apply {
                putExtra(EXTRA_REPLY_TO, replyTo)
                putExtra(EXTRA_COMMAND, command.name)
                putExtra(EXTRA_PREFIX, prefix)
                putExtra(EXTRA_BODY, triggerBody)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i) else context.startService(i)
        }
    }
}
