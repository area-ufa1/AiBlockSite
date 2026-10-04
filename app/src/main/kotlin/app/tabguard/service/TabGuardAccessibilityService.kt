package app.tabguard.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.ServiceCompat
import app.tabguard.container
import app.tabguard.core.classify.ClassifierChain
import app.tabguard.core.pipeline.TabPipeline
import app.tabguard.core.ui.TabExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Watches the active tab of any browser and redirects to claude.ai when it matches a category. */
class TabGuardAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pipeline: TabPipeline? = null
    private var browsers: Set<String> = emptySet()

    override fun onServiceConnected() {
        startGuardForeground()
        val c = container
        browsers = queryBrowsers()
        val claudeEnabled = c.settings.claudeEnabled.stateIn(scope, SharingStarted.Eagerly, false)
        val action = BlockAction(this, c.repository)
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
        if (pkg !in browsers) return
        val p = pipeline ?: return
        val root = rootInActiveWindow ?: return
        TabExtractor.extract(pkg, AccessibilityUiNode(root))?.let(p::submit)
    }

    override fun onInterrupt() = Unit

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
        scope.cancel()
        super.onDestroy()
    }

    private fun queryBrowsers(): Set<String> =
        packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com")), PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet() - packageName
}
