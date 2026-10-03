package com.childprotect.app

/** Commands the owner can send by SMS. Every command requires the secret code. */
enum class Command {
    LOCATE, DATA_ON, DATA_OFF, LOC_ON, RING, STOP, LOCK, STATUS, HELP, NONE
}

object CommandRouter {

    /** Codes shorter than this are rejected, so a short code can't be guessed or
     *  accidentally appear in ordinary messages. */
    const val MIN_CODE_LEN = 4

    /**
     * Returns the command if [body] contains the secret [code] as a standalone
     * token, else [Command.NONE].
     *
     * The code must appear delimited by whitespace or message boundaries — not as
     * a substring of another word. This stops an ordinary text that happens to
     * contain the code (e.g. code "find" inside "did you find it?") from silently
     * triggering a locate and texting the phone's position back to the sender.
     * Code alone = LOCATE.
     */
    fun parse(body: String, code: String): Command {
        val c = code.trim()
        if (c.length < MIN_CODE_LEN) return Command.NONE
        if (!containsToken(body, c)) return Command.NONE

        val rest = stripToken(body, c).trim().uppercase()
        return when {
            rest.contains("DATA OFF") || rest.contains("DATAOFF") -> Command.DATA_OFF
            rest.contains("DATA ON") || rest.contains("DATAON") || rest == "DATA" -> Command.DATA_ON
            rest.contains("LOC ON") || rest.contains("LOCON") -> Command.LOC_ON
            rest.contains("LOCK") -> Command.LOCK
            rest.contains("STOP") || rest.contains("SILENCE") -> Command.STOP
            rest.contains("RING") || rest.contains("ALARM") -> Command.RING
            rest.contains("STATUS") -> Command.STATUS
            rest.contains("HELP") -> Command.HELP
            else -> Command.LOCATE
        }
    }

    /** True if [code] appears in [body] as a whole, delimited token (case-insensitive). */
    private fun containsToken(body: String, code: String): Boolean {
        val pattern = Regex(
            "(^|\\s)" + Regex.escape(code) + "($|\\s)",
            RegexOption.IGNORE_CASE,
        )
        return pattern.containsMatchIn(body)
    }

    /** Removes the first standalone occurrence of [code] from [body]. */
    private fun stripToken(body: String, code: String): String =
        Regex("(^|\\s)" + Regex.escape(code) + "($|\\s)", RegexOption.IGNORE_CASE)
            .replaceFirst(body, " ")
}
