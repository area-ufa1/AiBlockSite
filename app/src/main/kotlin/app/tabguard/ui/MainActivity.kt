package app.tabguard.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.tabguard.R
import app.tabguard.container
import app.tabguard.service.TabGuardAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        val c = container
        setContent {
            MaterialTheme {
                val vm = viewModel { MainViewModel(c.repository, c.settings, c.termux, { isServiceEnabled(this@MainActivity) }) }
                LifecycleResumeEffect(Unit) {
                    vm.refresh()
                    onPauseOrDispose { }
                }
                App(vm)
            }
        }
    }

    @Composable
    private fun App(vm: MainViewModel) {
        val state by vm.state.collectAsStateWithLifecycle()
        val nav = rememberNavController()
        var showInstructions by rememberSaveable { mutableStateOf(false) }
        if (showInstructions) {
            AlertDialog(
                onDismissRequest = { showInstructions = false },
                title = { Text(stringResource(R.string.setup_instructions)) },
                text = { Text(stringResource(R.string.setup_instructions_text)) },
                confirmButton = { TextButton(onClick = { showInstructions = false }) { Text(stringResource(R.string.ok)) } },
            )
        }
        NavHost(nav, startDestination = "categories") {
            composable("categories") {
                CategoriesScreen(
                    state = state,
                    onToggle = vm::toggleCategory,
                    onOpen = { nav.navigate("edit/$it") },
                    onAdd = { nav.navigate("edit/-1") },
                    setup = {
                        SetupCard(
                            state = state,
                            onEnableService = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            onClaudeToggle = vm::setClaudeEnabled,
                            onTestClaude = vm::testClaude,
                            onOpenInstructions = { showInstructions = true },
                        )
                    },
                )
            }
            composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id")?.takeIf { it >= 0 }
                val existing = state.categories.firstOrNull { it.category.id == id }
                val bundledCount by produceState(0, existing?.bundledList) {
                    value = existing?.bundledList?.let { name -> withContext(Dispatchers.IO) { container.lists.domains(name).size } } ?: 0
                }
                CategoryEditScreen(
                    existing = existing,
                    bundledCount = bundledCount,
                    onSave = { name, keywords, domains -> vm.saveCategory(id, name, keywords, domains); nav.popBackStack() },
                    onDelete = { id?.let(vm::deleteCategory); nav.popBackStack() },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }

    companion object {
        fun isServiceEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, TabGuardAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
