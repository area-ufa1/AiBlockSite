package app.tabguard.termux

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import app.tabguard.core.model.Category
import app.tabguard.core.model.TabSnapshot
import app.tabguard.core.model.Verdict
import app.tabguard.core.model.VerdictSource
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TermuxClaudeClassifierTest {
    private val sent = mutableListOf<Intent>()
    private val tab = TabSnapshot("b", "x.com", "Видео")
    private val cats = listOf(Category(3, "Порно", true, listOf("porn"), emptySet()))

    private fun classifier(timeoutMs: Long = 5_000, sender: (Intent) -> Unit = { sent += it }) =
        TermuxClaudeClassifier(ApplicationProvider.getApplicationContext(), timeoutMs, sender)

    private fun result(stdout: String = "", stderr: String = "", exitCode: Int = 0, err: Int = -1, errmsg: String? = null) =
        Bundle().apply {
            putString("stdout", stdout); putString("stderr", stderr)
            putInt("exitCode", exitCode); putInt("err", err); putString("errmsg", errmsg)
        }

    /** Starts classification, waits until the intent is sent, delivers [bundle] for its request id. */
    private fun classifyWith(bundle: Bundle): Verdict = runBlocking {
        val c = classifier()
        val job = async { c.classify(tab, cats) }
        while (sent.isEmpty()) yield()
        val callback = sent.single().extras!!.get(TermuxClaudeClassifier.EXTRA_PENDING_INTENT) as PendingIntent
        c.deliver(shadowOf(callback).savedIntent.getIntExtra(TermuxClaudeClassifier.EXTRA_REQUEST_ID, -1), bundle)
        job.await()
    }

    @Test
    fun `sends run command intent with script path and prompt`() {
        classifyWith(result(stdout = "0"))
        val i = sent.single()
        assertEquals("com.termux.RUN_COMMAND", i.action)
        assertEquals("com.termux", i.component?.packageName)
        assertEquals("com.termux.app.RunCommandService", i.component?.className)
        assertEquals("/data/data/com.termux/files/home/.tabguard/classify.sh", i.getStringExtra("com.termux.RUN_COMMAND_PATH"))
        val args = i.getStringArrayExtra("com.termux.RUN_COMMAND_ARGUMENTS")!!
        assertEquals(1, args.size)
        assertTrue(args[0].contains("3: Порно") && args[0].contains("Заголовок: Видео"))
        assertTrue(i.getBooleanExtra("com.termux.RUN_COMMAND_BACKGROUND", false))
        assertNotNull(i.extras?.get("com.termux.RUN_COMMAND_PENDING_INTENT") as? android.app.PendingIntent)
    }

    @Test
    fun `stdout 0 yields Allow`() = assertEquals(Verdict.Allow, classifyWith(result(stdout = "0\n")))

    @Test
    fun `stdout with category id yields Block CLAUDE`() =
        assertEquals(Verdict.Block(3, VerdictSource.CLAUDE), classifyWith(result(stdout = "3")))

    @Test
    fun `nonzero exit yields Unknown with stderr`() {
        val v = classifyWith(result(stderr = "claude: command not found", exitCode = 127))
        assertEquals(Verdict.Unknown("termux: claude: command not found"), v)
    }

    @Test
    fun `termux error yields Unknown with errmsg`() {
        val v = classifyWith(result(err = 2, errmsg = "allow-external-apps is not true"))
        assertEquals(Verdict.Unknown("termux: allow-external-apps is not true"), v)
    }

    @Test
    fun `timeout yields Unknown`() = runBlocking {
        assertEquals(Verdict.Unknown("timeout"), classifier(timeoutMs = 100).classify(tab, cats))
    }

    @Test
    fun `sender exception yields Unknown`() = runBlocking {
        val v = classifier { throw SecurityException("no permission") }.classify(tab, cats)
        assertEquals(Verdict.Unknown("termux: no permission"), v)
    }

    @Test
    fun `termux not installed in test environment`() =
        assertEquals(false, classifier().isTermuxInstalled())

}
