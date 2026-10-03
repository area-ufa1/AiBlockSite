package app.tabguard.termux

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import app.tabguard.core.claude.ClassificationPrompt
import app.tabguard.core.classify.Classifier
import app.tabguard.core.model.Category
import app.tabguard.core.model.TabSnapshot
import app.tabguard.core.model.Verdict
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Classifies a tab by running `claude -p` in Termux via its RUN_COMMAND intent. */
class TermuxClaudeClassifier(
    private val context: Context,
    private val timeoutMs: Long = 90_000,
    private val sender: (Intent) -> Unit = { intent ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    },
) : Classifier {
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Bundle>>()
    private val nextId = AtomicInteger()

    fun isTermuxInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    override suspend fun classify(tab: TabSnapshot, categories: List<Category>): Verdict {
        val id = nextId.incrementAndGet()
        val reply = CompletableDeferred<Bundle>()
        pending[id] = reply
        try {
            try {
                sender(runCommandIntent(id, ClassificationPrompt.build(tab, categories)))
            } catch (e: Exception) {
                return Verdict.Unknown("termux: ${e.message ?: e.javaClass.simpleName}")
            }
            val result = withTimeoutOrNull(timeoutMs) { reply.await() } ?: return Verdict.Unknown("timeout")
            return interpret(result, categories.map { it.id }.toSet())
        } finally {
            pending.remove(id)
        }
    }

    /** Called by [TermuxResultReceiver] with Termux's result bundle. */
    fun deliver(requestId: Int, result: Bundle) {
        pending[requestId]?.complete(result)
    }

    private fun interpret(result: Bundle, ids: Set<Long>): Verdict {
        val err = result.getInt(RESULT_ERR, ERR_SUCCESS)
        val exitCode = result.getInt(RESULT_EXIT_CODE, 0)
        if (err != ERR_SUCCESS || exitCode != 0) {
            val message = result.getString(RESULT_ERRMSG)?.takeIf { it.isNotBlank() }
                ?: result.getString(RESULT_STDERR)?.takeIf { it.isNotBlank() }
                ?: "exit code $exitCode"
            return Verdict.Unknown("termux: ${message.trim().take(200)}")
        }
        return ClassificationPrompt.parse(result.getString(RESULT_STDOUT).orEmpty(), ids)
    }

    private fun runCommandIntent(requestId: Int, prompt: String): Intent {
        val callback = Intent(context, TermuxResultReceiver::class.java).putExtra(EXTRA_REQUEST_ID, requestId)
        val flags = PendingIntent.FLAG_ONE_SHOT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return Intent(ACTION_RUN_COMMAND)
            .setComponent(ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE))
            .putExtra(EXTRA_COMMAND_PATH, SCRIPT_PATH)
            .putExtra(EXTRA_ARGUMENTS, arrayOf(prompt))
            .putExtra(EXTRA_BACKGROUND, true)
            .putExtra(EXTRA_PENDING_INTENT, PendingIntent.getBroadcast(context, requestId, callback, flags))
    }

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
        const val SCRIPT_PATH = "/data/data/com.termux/files/home/.tabguard/classify.sh"

        const val RESULT_BUNDLE = "result"
        const val RESULT_STDOUT = "stdout"
        const val RESULT_STDERR = "stderr"
        const val RESULT_EXIT_CODE = "exitCode"
        const val RESULT_ERR = "err"
        const val RESULT_ERRMSG = "errmsg"
        /** Termux reports success as Activity.RESULT_OK. */
        const val ERR_SUCCESS = -1

        const val EXTRA_REQUEST_ID = "app.tabguard.TERMUX_REQUEST_ID"
    }
}
