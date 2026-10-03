package app.tabguard.core.support

import app.tabguard.core.model.TabSnapshot
import java.net.URLEncoder

/** The supportive prompt opened on claude.ai after a block. */
object SupportPrompt {
    private const val MAX_PAGE = 200

    fun build(tab: TabSnapshot, categoryName: String): String {
        val page = (tab.title?.takeIf { it.isNotBlank() } ?: tab.url.orEmpty()).trim().take(MAX_PAGE)
        return "Я только что чуть не открыл страницу из категории «$categoryName»: «$page».\n" +
            "Я поставил себе приложение, чтобы не заходить на такие сайты. Пожалуйста, мягко и без осуждения " +
            "отговори меня возвращаться туда, поддержи и помоги справиться с тягой прямо сейчас: " +
            "предложи пару конкретных действий на ближайшие 10 минут."
    }

    fun claudeUrl(prompt: String): String =
        "https://claude.ai/new?q=" + URLEncoder.encode(prompt, Charsets.UTF_8).replace("+", "%20")
}
