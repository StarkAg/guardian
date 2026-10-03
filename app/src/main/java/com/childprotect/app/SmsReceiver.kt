package com.childprotect.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Listens for every incoming SMS, checks it against the secret code, and routes
 * the matched command to [LocateService]. We keep this fast: a BroadcastReceiver
 * has only a few seconds before Android kills it.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = Prefs(context)
        val code = prefs.secretCode.trim()
        if (code.isEmpty()) return

        // Reassemble multipart messages per sender.
        val bySender = HashMap<String, StringBuilder>()
        for (sms in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
            val from = sms.originatingAddress ?: continue
            bySender.getOrPut(from) { StringBuilder() }.append(sms.messageBody)
        }

        for ((sender, body) in bySender) {
            val command = CommandRouter.parse(body.toString(), code)
            if (command != Command.NONE) {
                if (!Authorizer.isAuthorized(prefs, sender, body.toString())) {
                    // Right code, but the sender isn't trusted and gave no valid PIN.
                    Log.w(TAG, "Unauthorized sender for $command; ignoring")
                    return
                }
                Log.i(TAG, "Command $command received") // sender/number intentionally not logged
                LocateService.start(context, replyTo = sender, command = command)
                return
            }
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
