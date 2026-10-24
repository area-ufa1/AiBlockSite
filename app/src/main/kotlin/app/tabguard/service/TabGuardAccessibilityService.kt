package app.tabguard.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.app.ServiceCompat
import app.tabguard.R
import app.tabguard.container
import app.tabguard.core.classify.ClassifierChain
import app.tabguard.core.lock.Lock
import app.tabguard.core.lock.LockState
import app.tabguard.core.lock.ProtectedScreens
import app.tabguard.core.pipeline.TabPipeline
import app.tabguard.core.ui.TabExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Watches the active tab of any browser and redirects to claude.ai when it matches a category. */
class TabGuardAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pipeline: TabPipeline? = null
    private var browsers: Set<String> = emptySet()
    private var lock: StateFlow<LockState>? = null
    private var lastToastAt = 0L
    private var lastProtectedAt = 0L

    override fun onServiceConnected() {
        running = true
        startGuardForeground()
        val c = container
        browsers = queryBrowsers()
        lock = c.settings.lock.stateIn(scope, SharingStarted.Eagerly, LockState())
        val claudeEnabled = c.settings.claudeEnabled.stateIn(scope, SharingStarted.Eagerly, false)
        val action = BlockAction(this, c.repository, goBack = { performGlobalAction(GLOBAL_ACTION_BACK) })
        pipeline = TabPipeline(
            scope = scope,
            classifier = ClassifierChain({ if (claudeEnabled.value) c.termux else null }),
            categories = c.repository::enabledCategories,
            onBlock = action::perform,
            onUnknown = { reason -> scope.launch { c.settings.setLastClaudeError(reason) } },
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in browsers) {
            guardSystemScreen(pkg)
            return
        }
        val p = pipeline ?: return
        val root = rootInActiveWindow ?: return
        TabExtractor.extract(pkg, AccessibilityUiNode(root))?.let(p::submit)
    }

    override fun onInterrupt() = Unit

    /** Leaves the settings/uninstall screens that would switch protection off while changes are closed. */
    private fun guardSystemScreen(pkg: String) {
        if (!pkg.endsWith("settings") && !pkg.endsWith("packageinstaller")) return
        val state = lock?.value ?: return
        val now = System.currentTimeMillis()
        if (state.mode == null || Lock.isUnlocked(Lock.settle(state, now), now)) return
        val root = rootInActiveWindow ?: return
        // The "Force stop?" dialog doesn't name the app: it counts right after a protected screen.
        val recent = now - lastProtectedAt < 5_000
        if (!ProtectedScreens.isProtected(pkg, AccessibilityUiNode(root), recent)) return
        lastProtectedAt = now
        performGlobalAction(GLOBAL_ACTION_BACK)
        if (now - lastToastAt > 3_000) {
            lastToastAt = now
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, R.string.protected_screen, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Foreground state keeps the firmware from killing us; if Android refuses, protection still runs. */
    private fun startGuardForeground() {
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(this, GuardNotification.ID, GuardNotification.build(this), type)
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException or a missing notification permission.
        }
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** True while Android actually has the service connected (same process as the UI). */
        @Volatile
        var running = false
            private set
    }

    private fun queryBrowsers(): Set<String> =
        packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com")), PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet() - packageName
}
