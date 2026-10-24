package app.tabguard.core.lock

import app.tabguard.core.ui.FakeNode
import app.tabguard.core.ui.UiNode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedScreensTest {
    private val settings = "com.android.settings"
    private fun text(t: String, cls: String = "android.widget.TextView") = FakeNode(className = cls, text = t)
    private fun screen(vararg nodes: UiNode) = FakeNode(children = nodes.toList())

    @Test fun `app info page with force stop is protected`() = assertTrue(
        ProtectedScreens.isProtected(settings, screen(
            text("Страж вкладок"), text("Версия 1.0.0"),
            text("Удалить", "android.widget.Button"), text("Остановить", "android.widget.Button"),
            text("Уведомления"), text("Разрешения"),
        )),
    )

    @Test fun `accessibility page of the service is protected`() = assertTrue(
        ProtectedScreens.isProtected(settings, screen(
            text("Страж вкладок"),
            FakeNode(children = listOf(text("Использовать Страж вкладок"), FakeNode(className = "android.widget.Switch"))),
            text("Быстрое включение"), text("Читает адрес и заголовок активной вкладки браузера"),
        )),
    )

    @Test fun `uninstall dialog is protected`() = assertTrue(
        ProtectedScreens.isProtected("com.google.android.packageinstaller", screen(
            text("Страж вкладок"), text("Удалить приложение?"), text("ОК", "android.widget.Button"), text("Отмена", "android.widget.Button"),
        )),
    )

    @Test fun `oem settings package is covered`() = assertTrue(
        ProtectedScreens.isProtected("com.transsion.phonemaster.settings", screen(
            text("Страж вкладок"), text("Принудительно остановить", "android.widget.Button"),
        )),
    )

    @Test fun `app list mentioning the name is not protected`() = assertFalse(
        ProtectedScreens.isProtected(settings, screen(
            text("Все приложения"), text("Камера"), text("Страж вкладок"), text("Telegram"), text("Часы"),
        )),
    )

    @Test fun `accessibility list with other switches is not protected`() = assertFalse(
        ProtectedScreens.isProtected(settings, screen(
            text("Доступность"), text("Меню спец. возможностей"), text("Страж вкладок"), text("Выкл"),
            FakeNode(children = listOf(text("Дополнительное уменьшение яркости"), FakeNode(className = "android.widget.Switch"))),
        )),
    )

    @Test fun `settings search result is not protected`() = assertFalse(
        ProtectedScreens.isProtected(settings, screen(
            FakeNode(className = "android.widget.EditText", text = "Страж", isFocused = true),
            text("Страж вкладок"), text("Приложения"),
        )),
    )

    @Test fun `other app info page is not protected`() = assertFalse(
        ProtectedScreens.isProtected(settings, screen(
            text("Telegram"), text("Удалить", "android.widget.Button"), text("Остановить", "android.widget.Button"),
        )),
    )

    @Test fun `browser page with the name is not protected`() = assertFalse(
        ProtectedScreens.isProtected("com.android.chrome", screen(text("Страж вкладок"), text("Удалить", "android.widget.Button"))),
    )
}
