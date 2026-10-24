package app.tabguard.core.support

import app.tabguard.core.model.TabSnapshot
import java.net.URLEncoder

/** Which supportive text to use. */
enum class PromptKind { ADULT, YOUTUBE }

/** The supportive prompt opened on claude.ai after a block. */
object SupportPrompt {
    private const val MAX_PAGE = 200

    fun build(tab: TabSnapshot, categoryName: String, kind: PromptKind = PromptKind.ADULT): String {
        val page = (tab.title?.takeIf { it.isNotBlank() } ?: tab.url.orEmpty()).trim().take(MAX_PAGE)
        if (kind == PromptKind.YOUTUBE) {
            return "Я только что чуть не залип в развлекательный YouTube: «$page».\n" +
                "Я поставил себе приложение, чтобы не тратить время на бесконечную ленту. Пожалуйста, мягко и без осуждения " +
                "помоги вернуться к делу и справиться с тягой к бесконечной ленте прямо сейчас: " +
                "предложи пару конкретных действий на ближайшие 10 минут."
        }
        return "Я только что чуть не открыл страницу из категории «$categoryName»: «$page».\n" +
            "Я поставил себе приложение, чтобы не заходить на такие сайты. Пожалуйста, мягко и без осуждения " +
            "отговори меня возвращаться туда, поддержи и помоги справиться с тягой прямо сейчас: " +
            "предложи пару конкретных действий на ближайшие 10 минут."
    }

    fun claudeUrl(prompt: String): String =
        "https://claude.ai/new?q=" + URLEncoder.encode(prompt, Charsets.UTF_8).replace("+", "%20")
}
