package app.tabguard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tabguard.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    state: MainState,
    onToggle: (Long, Boolean) -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    setup: @Composable () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd) { Text("+ " + stringResource(R.string.category_add)) }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { setup() }
            item { Text(stringResource(R.string.categories_title), style = MaterialTheme.typography.titleMedium) }
            items(state.categories, key = { it.category.id }) { row ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(row.category.id) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(row.category.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.category_blocked, row.blockCount),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(checked = row.category.enabled, onCheckedChange = { onToggle(row.category.id, it) })
                    }
                }
            }
        }
    }
}
