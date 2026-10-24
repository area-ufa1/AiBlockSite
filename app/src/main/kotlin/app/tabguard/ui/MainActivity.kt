package app.tabguard.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import app.tabguard.R
import app.tabguard.container
import app.tabguard.service.TabGuardAccessibilityService
import app.tabguard.core.support.SupportPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
                val vm = viewModel { MainViewModel(c.repository, c.settings, c.termux, { isServiceEnabled(this@MainActivity) }, { TabGuardAccessibilityService.running }) }
                LifecycleResumeEffect(Unit) {
                    vm.refresh()
                    onPauseOrDispose { }
                }
                App(vm)
            }
        }
    }

    /**
     * Some firmwares ignore excludeFromRecents; removing the task when the user leaves means there
     * is never a card to swipe away (swiping force-stops the app there, killing protection).
     */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finishAndRemoveTask()
    }

    @Composable
    private fun App(vm: MainViewModel) {
        val state by vm.state.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) {
            while (true) {
                vm.tick()
                delay(1000)
            }
        }
        if (state.needsSetup && state.categories.isNotEmpty()) {
            SetupScreen(state, vm::completeSetup)
            return
        }
        var changingMode by rememberSaveable { mutableStateOf(false) }
        if (changingMode && state.lockOpen) {
            SetupScreen(state) { mode, minutes, pin, porn, youtube ->
                vm.completeSetup(mode, minutes, pin, porn, youtube)
                changingMode = false
            }
            return
        }
        if (state.unlockRequested) {
            UnlockDialog(
                state = state,
                onStart = vm::startCountdown,
                onCancel = vm::cancelCountdown,
                onPin = vm::submitPin,
                onTalk = { openUrl(SupportPrompt.claudeUrl(getString(R.string.unlock_want_prompt))) },
                onClose = vm::dismissUnlock,
            )
        }
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
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LockCard(state = state, onChangeMode = { changingMode = true })
                        SetupCard(
                            state = state,
                            onEnableService = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            },
                            onClaudeToggle = vm::setClaudeEnabled,
                            onTestClaude = vm::testClaude,
                            onOpenInstructions = { showInstructions = true },
                        )
                        }
                    },
                    footer = {
                        LockFooter(
                            state = state,
                            version = versionName(),
                            onUpdates = { openUrl(RELEASES_URL) },
                            onWeaken = vm::requestUnlock,
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
                val bundledWords by produceState(emptyList<String>(), existing?.bundledList) {
                    value = existing?.bundledList?.let { name -> withContext(Dispatchers.IO) { container.lists.keywords(name) } } ?: emptyList()
                }
                CategoryEditScreen(
                    existing = existing,
                    bundledCount = bundledCount,
                    bundledWords = bundledWords,
                    onSave = { name, keywords, domains, prefixes, onlyHosts ->
                        vm.saveCategory(id, name, keywords, domains, prefixes, onlyHosts); nav.popBackStack()
                    },
                    onDelete = { id?.let(vm::deleteCategory); nav.popBackStack() },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun versionName(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"

    companion object {
        const val RELEASES_URL = "https://github.com/area-ufa1/AiBlockSite/releases/latest"

        fun isServiceEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, TabGuardAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
