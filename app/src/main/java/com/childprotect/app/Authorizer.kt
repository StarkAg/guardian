package com.childprotect.app

/**
 * Decides whether an incoming SMS may run a command, under the dual
 * trusted-contacts + PIN model (modeled on Find My Device's flexible scheme):
 *
 *  1. A sender in the trusted-contacts list is always allowed — no PIN needed.
 *  2. Any other sender is allowed only when PIN mode is on AND the message
 *     carries the PIN as a standalone token.
 *  3. If no trusted contacts are configured and PIN mode is off, the secret
 *     code alone authorizes (the backward-compatible "reply to whoever sent the
 *     trigger" behavior).
 *
 * The secret code (checked separately by [CommandRouter]) still gates every
 * command, so the PIN is a genuine second factor for anonymous access, not a
 * replacement for the code.
 */
object Authorizer {

    fun isAuthorized(prefs: Prefs, sender: String, body: String): Boolean {
        val trusted = prefs.trustedList()

        // 1. Trusted contacts never need the PIN.
        if (trusted.any { sameNumber(it, sender) }) return true

        // 2. PIN (anonymous) mode: non-trusted senders must include the PIN.
        if (prefs.pinEnabled) {
            val pin = prefs.pin.trim()
            return pin.isNotEmpty() && CommandRouter.hasToken(body, pin)
        }

        // 3. No trusted list and no PIN mode -> code alone is enough (legacy).
        return trusted.isEmpty()
    }

    /**
     * True if two phone numbers refer to the same line, ignoring formatting
     * (spaces, dashes, country-code prefix). Compares up to the last 10 digits.
     */
    fun sameNumber(a: String, b: String): Boolean {
        val da = a.filter(Char::isDigit)
        val db = b.filter(Char::isDigit)
        if (da.isEmpty() || db.isEmpty()) return false
        val n = minOf(da.length, db.length, 10)
        if (n < 7) return da == db
        return da.takeLast(n) == db.takeLast(n)
    }
}
