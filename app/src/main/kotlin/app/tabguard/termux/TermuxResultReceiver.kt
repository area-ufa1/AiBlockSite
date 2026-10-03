package app.tabguard.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.tabguard.container

/** Receives Termux's RUN_COMMAND result and hands it to the waiting classification. */
class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getIntExtra(TermuxClaudeClassifier.EXTRA_REQUEST_ID, -1)
        val result = intent.getBundleExtra(TermuxClaudeClassifier.RESULT_BUNDLE) ?: return
        context.container.termux.deliver(requestId, result)
    }
}
