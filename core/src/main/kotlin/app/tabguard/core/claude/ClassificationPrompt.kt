package app.tabguard.core.claude

import app.tabguard.core.model.Category
import app.tabguard.core.model.TabSnapshot
import app.tabguard.core.model.Verdict
import app.tabguard.core.model.VerdictSource

/** Prompt for `claude -p` that asks for a category number, and parsing of its reply. */
object ClassificationPrompt {
    private const val MAX_FIELD = 300
    private val NUMBER = Regex("-?\\d+")

    fun build(tab: TabSnapshot, categories: List<Category>): String {
        val lines = categories.filter { it.enabled }.joinToString("\n") { c ->
            "${c.id}: ${c.name} (слова: ${c.keywords.filter { it.isNotBlank() }.joinToString(", ")})"
        }
        return """
            |Ты фильтр самоконтроля. Определи по заголовку и адресу вкладки браузера, относится ли страница к одной из категорий.
            |Категории:
            |$lines
            |Заголовок: ${tab.title.field()}
            |Адрес: ${tab.url.field()}
            |Ответь ровно одним числом: номером категории или 0, если ни одна не подходит. Никакого другого текста.
        """.trimMargin()
    }

    fun parse(reply: String, validIds: Set<Long>): Verdict {
        val n = NUMBER.find(reply)?.value?.toLongOrNull() ?: return Verdict.Unknown("unparsable reply")
        return when {
            n == 0L -> Verdict.Allow
            n in validIds -> Verdict.Block(n, VerdictSource.CLAUDE)
            else -> Verdict.Unknown("unparsable reply")
        }
    }

    private fun String?.field() = this?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_FIELD) ?: "нет"
}
