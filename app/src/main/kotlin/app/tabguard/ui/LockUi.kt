package app.tabguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.tabguard.R
import app.tabguard.core.lock.Lock
import app.tabguard.core.lock.LockMode

private fun minutesLeft(ms: Long) = ((ms + 59_999) / 60_000).toInt()

/** Status line and actions of the protection, shown on the main screen. */
@Composable
fun LockCard(state: MainState, onChangeMode: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleSmall)
            val lock = state.lock
            val left = state.countdownLeftMs
            Text(
                when {
                    lock.mode == null -> stringResource(R.string.lock_not_set)
                    state.lockOpen -> stringResource(R.string.lock_open, minutesLeft(lock.unlockedUntil - System.currentTimeMillis()))
                    left != null -> stringResource(R.string.lock_countdown, minutesLeft(left))
                    lock.mode == LockMode.PAUSE -> stringResource(R.string.lock_pause, lock.pauseMinutes)
                    else -> stringResource(R.string.lock_partner)
                },
            )
            if (state.lockOpen) TextButton(onClick = onChangeMode) { Text(stringResource(R.string.lock_change_mode)) }
        }
    }
}

/** Bottom of the main screen: version, updates and — deliberately out of the way — weakening. */
@Composable
fun LockFooter(state: MainState, version: String, onUpdates: () -> Unit, onWeaken: () -> Unit) {
    val small = MaterialTheme.typography.bodySmall
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 88.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.version_line, version), style = small)
            TextButton(onClick = onUpdates) { Text(stringResource(R.string.check_updates), style = small) }
        }
        if (state.lock.mode != null && !state.lockOpen) {
            TextButton(onClick = onWeaken) {
                Text(stringResource(R.string.lock_weaken_small), style = small, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/** Shown when a weakening change was refused: pause countdown or partner PIN. */
@Composable
fun UnlockDialog(
    state: MainState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onPin: (String) -> Unit,
    onTalk: () -> Unit,
    onClose: () -> Unit,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var phrase by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.unlock_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.lock.mode == LockMode.PAUSE) {
                    Text(stringResource(R.string.unlock_pause_text))
                    state.countdownLeftMs?.let {
                        Text(stringResource(R.string.lock_countdown, minutesLeft(it)), style = MaterialTheme.typography.titleMedium)
                    }
                    if (state.countdownLeftMs == null) {
                        Text(stringResource(R.string.unlock_phrase_hint, Lock.PAUSE_PHRASE))
                        OutlinedTextField(
                            phrase,
                            // Typed, not pasted: a jump of several characters at once is ignored.
                            { if (it.length <= phrase.length + 2) phrase = it },
                            singleLine = true,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.countdownLeftMs == null) {
                            Button(onClick = { onStart(); phrase = "" }, enabled = Lock.phraseMatches(phrase)) {
                                Text(stringResource(R.string.unlock_start))
                            }
                        } else {
                            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.unlock_cancel)) }
                        }
                    }
                    TextButton(onClick = onTalk) { Text(stringResource(R.string.unlock_talk)) }
                } else {
                    Text(stringResource(R.string.unlock_partner_text))
                    OutlinedTextField(
                        pin, { pin = it.filter(Char::isDigit).take(8) },
                        label = { Text(stringResource(R.string.unlock_pin)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                    )
                    state.pinError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { onPin(pin); pin = "" }) { Text(stringResource(R.string.unlock_confirm)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.unlock_close)) } },
    )
}

/** First-run wizard: what to block and how protection is unlocked. */
@Composable
fun SetupScreen(state: MainState, onDone: (LockMode, Int, String?, Boolean, Boolean) -> Unit) {
    var porn by rememberSaveable { mutableStateOf(state.categories.firstOrNull { it.bundledList == "adult" }?.category?.enabled ?: true) }
    var youtube by rememberSaveable { mutableStateOf(state.categories.firstOrNull { it.bundledList == "youtube-fun" }?.category?.enabled ?: false) }
    var mode by rememberSaveable { mutableStateOf(state.lock.mode ?: LockMode.PAUSE) }
    var minutes by rememberSaveable { mutableIntStateOf(state.lock.pauseMinutes) }
    var pin by rememberSaveable { mutableStateOf("") }
    var pin2 by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.setup_what), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(porn, { porn = it }); Text(stringResource(R.string.setup_porn))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(youtube, { youtube = it }); Text(stringResource(R.string.setup_youtube))
        }
        Text(stringResource(R.string.setup_protection), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.setup_protection_hint), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(mode == LockMode.PAUSE, { mode = LockMode.PAUSE }); Text(stringResource(R.string.setup_mode_pause))
        }
        if (mode == LockMode.PAUSE) {
            Text(stringResource(R.string.setup_minutes, minutes))
            Slider(minutes.toFloat(), { minutes = (it / 5).toInt() * 5 }, valueRange = 5f..60f, steps = 10)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(mode == LockMode.PARTNER, { mode = LockMode.PARTNER }); Text(stringResource(R.string.setup_mode_partner))
        }
        if (mode == LockMode.PARTNER) {
            val pinField: @Composable (String, (String) -> Unit, Int) -> Unit = { v, set, label ->
                OutlinedTextField(
                    v, { set(it.filter(Char::isDigit).take(8)) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
            }
            pinField(pin, { pin = it }, R.string.setup_pin)
            pinField(pin2, { pin2 = it }, R.string.setup_pin2)
        }
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            error = when {
                mode == LockMode.PARTNER && pin != pin2 -> R.string.setup_pin_mismatch
                mode == LockMode.PARTNER && pin.length !in 4..8 -> R.string.setup_pin_invalid
                else -> null
            }
            if (error == null) onDone(mode, minutes, pin.takeIf { mode == LockMode.PARTNER }, porn, youtube)
        }) { Text(stringResource(R.string.setup_done)) }
    }
}
