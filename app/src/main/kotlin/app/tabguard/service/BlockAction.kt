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
    suspend fun perform(tab: TabSnapshot, block: Verdict.Block) {
        val category = repository.get(block.categoryId) ?: return
        repository.recordBlock(category.id, block.source, clock())
        val prompt = SupportPrompt.build(tab, category.name)
        withContext(mainDispatcher) {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), prompt))
            val open = Intent(Intent.ACTION_VIEW, Uri.parse(SupportPrompt.claudeUrl(prompt)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(open)
            } catch (e: ActivityNotFoundException) {
                // No browser to open claude.ai; the prompt is still on the clipboard.
            }
            Toast.makeText(context, R.string.toast_redirect, Toast.LENGTH_LONG).show()
        }
    }
}
