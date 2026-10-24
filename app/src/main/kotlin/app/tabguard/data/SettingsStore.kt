package app.tabguard.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.tabguard.core.lock.LockMode
import app.tabguard.core.lock.LockState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val claudeEnabledKey = booleanPreferencesKey("claude_enabled")
    private val lastClaudeErrorKey = stringPreferencesKey("last_claude_error")

    val claudeEnabled: Flow<Boolean> = context.dataStore.data.map { it[claudeEnabledKey] ?: false }

    suspend fun setClaudeEnabled(v: Boolean) {
        context.dataStore.edit { it[claudeEnabledKey] = v }
    }

    private val lockMode = stringPreferencesKey("lock_mode")
    private val lockPause = intPreferencesKey("lock_pause_minutes")
    private val lockPinHash = stringPreferencesKey("lock_pin_hash")
    private val lockSalt = stringPreferencesKey("lock_salt")
    private val lockCountdown = longPreferencesKey("lock_countdown_started")
    private val lockUnlockedUntil = longPreferencesKey("lock_unlocked_until")
    private val lockFailed = intPreferencesKey("lock_failed_attempts")
    private val lockPinLockedUntil = longPreferencesKey("lock_pin_locked_until")

    /** Protection state (see [LockState]); a missing mode means setup has not been done. */
    val lock: Flow<LockState> = context.dataStore.data.map { p ->
        LockState(
            mode = p[lockMode]?.let { runCatching { LockMode.valueOf(it) }.getOrNull() },
            pauseMinutes = p[lockPause] ?: 20,
            pinHash = p[lockPinHash],
            salt = p[lockSalt],
            countdownStartedAt = p[lockCountdown]?.takeIf { it >= 0 },
            unlockedUntil = p[lockUnlockedUntil] ?: 0,
            failedAttempts = p[lockFailed] ?: 0,
            pinLockedUntil = p[lockPinLockedUntil] ?: 0,
        )
    }

    suspend fun saveLock(s: LockState) {
        context.dataStore.edit { p ->
            if (s.mode == null) p.remove(lockMode) else p[lockMode] = s.mode.name
            p[lockPause] = s.pauseMinutes
            if (s.pinHash == null) p.remove(lockPinHash) else p[lockPinHash] = s.pinHash
            if (s.salt == null) p.remove(lockSalt) else p[lockSalt] = s.salt
            p[lockCountdown] = s.countdownStartedAt ?: -1
            p[lockUnlockedUntil] = s.unlockedUntil
            p[lockFailed] = s.failedAttempts
            p[lockPinLockedUntil] = s.pinLockedUntil
        }
    }

    val lastClaudeError: Flow<String?> = context.dataStore.data.map { it[lastClaudeErrorKey] }

    suspend fun setLastClaudeError(v: String?) {
        context.dataStore.edit { if (v == null) it.remove(lastClaudeErrorKey) else it[lastClaudeErrorKey] = v }
    }
}
