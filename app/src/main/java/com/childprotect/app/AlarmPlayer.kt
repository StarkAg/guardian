package com.childprotect.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Sounds a loud alarm to physically locate the phone, even if it's on silent. */
object AlarmPlayer {

    private const val TAG = "AlarmPlayer"
    private var ringtone: Ringtone? = null

    /** Plays the alarm on the ALARM stream at max volume for [seconds]. */
    suspend fun ring(context: Context, seconds: Int = 30) = withContext(Dispatchers.Main) {
        val am = context.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val previous = am.getStreamVolume(AudioManager.STREAM_ALARM)
        am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)

        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, uri).also {
                // Force the ALARM usage so it sounds on the alarm channel and
                // bypasses silent / DND where alarms are allowed.
                it.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.isLooping = true
                it.play()
            }
            vibrate(context, seconds * 1000L)
            delay(seconds * 1000L)
        } catch (e: Exception) {
            Log.w(TAG, "Alarm failed: ${e.message}")
        } finally {
            stop()
            am.setStreamVolume(AudioManager.STREAM_ALARM, previous, 0)
        }
    }

    fun stop() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    private fun vibrate(context: Context, ms: Long) {
        val v = context.getSystemService(Vibrator::class.java) ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") v.vibrate(ms)
        }
    }
}
