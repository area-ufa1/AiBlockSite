package app.tabguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tabguard.R
import app.tabguard.data.CategoryWithCount

/** Edits [existing], or creates a new category when it is null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditScreen(
    existing: CategoryWithCount?,
    bundledCount: Int,
    onSave: (name: String, keywords: String, domains: String) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(existing?.category?.name.orEmpty()) }
    var keywords by rememberSaveable { mutableStateOf(existing?.category?.keywords.orEmpty().joinToString("\n")) }
    var domains by rememberSaveable { mutableStateOf(existing?.category?.domains.orEmpty().sorted().joinToString("\n")) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(existing?.category?.name ?: stringResource(R.string.edit_new)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.edit_name)) }, singleLine = true)
            OutlinedTextField(keywords, { keywords = it }, Modifier.fillMaxWidth().heightIn(min = 140.dp), label = { Text(stringResource(R.string.edit_keywords)) })
            OutlinedTextField(domains, { domains = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text(stringResource(R.string.edit_domains)) })
            if (existing?.bundledList != null) {
                Text(stringResource(R.string.edit_bundled, bundledCount), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(name, keywords, domains) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.edit_save)) }
                if (existing != null) OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.edit_delete)) }
            }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.edit_delete_confirm, existing.category.name)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.edit_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.edit_cancel)) } },
        )
    }
}
