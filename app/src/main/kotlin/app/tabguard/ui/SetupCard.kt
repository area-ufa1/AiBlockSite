package app.tabguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tabguard.R

@Composable
fun SetupCard(
    state: MainState,
    onEnableService: () -> Unit,
    onClaudeToggle: (Boolean) -> Unit,
    onTestClaude: () -> Unit,
    onOpenInstructions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (state.serviceEnabled) R.string.setup_service_on else R.string.setup_service_off),
                    Modifier.weight(1f),
                    color = if (state.serviceEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
                if (!state.serviceEnabled) Button(onClick = onEnableService) { Text(stringResource(R.string.setup_enable)) }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.setup_claude), Modifier.weight(1f))
                Switch(checked = state.claudeEnabled, onCheckedChange = onClaudeToggle, enabled = state.termuxInstalled)
            }
            Text(
                stringResource(if (state.termuxInstalled) R.string.setup_claude_hint else R.string.setup_termux_missing),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onTestClaude, enabled = state.termuxInstalled) { Text(stringResource(R.string.setup_test)) }
                TextButton(onClick = onOpenInstructions) { Text(stringResource(R.string.setup_instructions)) }
            }
            state.claudeStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
