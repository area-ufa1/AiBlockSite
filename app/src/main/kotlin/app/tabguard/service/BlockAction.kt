package app.tabguard.service

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import app.tabguard.R
import app.tabguard.core.model.TabSnapshot
import app.tabguard.core.model.Verdict
import app.tabguard.core.support.SupportPrompt
import app.tabguard.data.CategoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Counts the block, puts the supportive prompt on the clipboard and opens claude.ai with it. */
class BlockAction(
    private val context: Context,
    private val repository: CategoryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    suspend fun perform(tab: TabSnapshot, block: Verdict.Block): Unit = TODO()
}
