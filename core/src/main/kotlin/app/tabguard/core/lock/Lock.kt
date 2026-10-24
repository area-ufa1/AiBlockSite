package app.tabguard.core.lock

import app.tabguard.core.model.Category
import java.security.MessageDigest

enum class LockMode { PAUSE, PARTNER }

/** Protection state; stored by the app. Same fields and rules as pc/extension/src/core/lock.js. */
data class LockState(
    val mode: LockMode? = null,
    val pauseMinutes: Int = 20,
    val pinHash: String? = null,
    val salt: String? = null,
    val countdownStartedAt: Long? = null,
    val unlockedUntil: Long = 0,
    val failedAttempts: Int = 0,
    val pinLockedUntil: Long = 0,
)

data class PinResult(val state: LockState, val ok: Boolean)

/** Protection against weakening the blocker: a pause before changes, or a partner's PIN. */
object Lock {
    private const val MIN = 60_000L
    const val WINDOW_MS = 5 * MIN
    const val PIN_LOCKOUT_MS = 30 * MIN
    const val MAX_PIN_ATTEMPTS = 5

    /** Typed (not pasted) before a pause can start: a moment to notice what is happening. */
    const val PAUSE_PHRASE = "Я осознанно ослабляю свою защиту"

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private fun simplify(s: String) = s.lowercase().replace('ё', 'е').replace(NON_WORD, " ").trim()

    /** Case, spacing and punctuation do not matter. */
    fun phraseMatches(text: String): Boolean = simplify(text) == simplify(PAUSE_PHRASE)

    /** Weakening changes are allowed: not configured yet, or inside the window. */
    fun isUnlocked(state: LockState, now: Long): Boolean = state.mode == null || now < state.unlockedUntil

    /** Turns a finished countdown into an open window (works after any gap, e.g. a restart). */
    fun settle(state: LockState, now: Long): LockState {
        val started = state.countdownStartedAt ?: return state
        val end = started + state.pauseMinutes * MIN
        return if (now < end) state else state.copy(countdownStartedAt = null, unlockedUntil = end + WINDOW_MS)
    }

    fun startCountdown(state: LockState, now: Long): LockState {
        require(state.mode == LockMode.PAUSE) { "not in pause mode" }
        return state.copy(countdownStartedAt = state.countdownStartedAt ?: now)
    }

    fun cancelCountdown(state: LockState): LockState = state.copy(countdownStartedAt = null)

    fun remainingCountdownMs(state: LockState, now: Long): Long? =
        state.countdownStartedAt?.let { maxOf(0, it + state.pauseMinutes * MIN - now) }

    fun setPauseMode(state: LockState, minutes: Int): LockState {
        require(minutes in 5..60) { "pause must be 5..60 minutes" }
        return state.copy(
            mode = LockMode.PAUSE, pauseMinutes = minutes, pinHash = null, salt = null,
            countdownStartedAt = null, unlockedUntil = 0,
        )
    }

    fun hashPin(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    fun setPartnerMode(state: LockState, pin: String, salt: String): LockState {
        require(Regex("^\\d{4,8}$").matches(pin)) { "PIN must be 4-8 digits" }
        return state.copy(
            mode = LockMode.PARTNER, pinHash = hashPin(salt, pin), salt = salt,
            countdownStartedAt = null, unlockedUntil = 0, failedAttempts = 0, pinLockedUntil = 0,
        )
    }

    fun tryPin(state: LockState, pin: String, now: Long): PinResult {
        if (state.mode != LockMode.PARTNER || now < state.pinLockedUntil) return PinResult(state, false)
        if (hashPin(state.salt.orEmpty(), pin) == state.pinHash) {
            return PinResult(state.copy(unlockedUntil = now + WINDOW_MS, failedAttempts = 0, pinLockedUntil = 0), true)
        }
        val failed = state.failedAttempts + 1
        if (failed >= MAX_PIN_ATTEMPTS) return PinResult(state.copy(failedAttempts = 0, pinLockedUntil = now + PIN_LOCKOUT_MS), false)
        return PinResult(state.copy(failedAttempts = failed), false)
    }

    /** True if saving [after] over [before] weakens protection (delete, disable, remove rules, narrow word scope). */
    fun isWeakening(before: Category?, after: Category?): Boolean {
        if (before == null) return false
        if (after == null) return true
        if (before.enabled && !after.enabled) return true
        if (!after.keywords.containsAll(before.keywords) || !after.domains.containsAll(before.domains)) return true
        if (!after.urlPrefixes.containsAll(before.urlPrefixes)) return true
        if (after.onlyHosts.isEmpty()) return false
        return before.onlyHosts.isEmpty() || !after.onlyHosts.containsAll(before.onlyHosts)
    }
}
