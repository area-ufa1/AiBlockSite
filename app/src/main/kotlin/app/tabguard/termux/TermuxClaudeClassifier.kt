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
    private val timeoutMs: Long = 30_000,
    private val sender: (Intent) -> Unit = { context.startService(it) },
) : Classifier {
    fun isTermuxInstalled(): Boolean = TODO()
    override suspend fun classify(tab: TabSnapshot, categories: List<Category>): Verdict = TODO()
    fun deliver(requestId: Int, result: Bundle): Unit = TODO()

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
