package app.tabguard.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    val lastClaudeError: Flow<String?> = context.dataStore.data.map { it[lastClaudeErrorKey] }

    suspend fun setLastClaudeError(v: String?) {
        context.dataStore.edit { if (v == null) it.remove(lastClaudeErrorKey) else it[lastClaudeErrorKey] = v }
    }
}
