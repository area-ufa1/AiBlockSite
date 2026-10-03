package app.tabguard.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextTest {
    @Test
    fun `normalize lowercases and folds yo`() = assertEquals("ежик", normalize("  ЁЖИК "))

    @Test
    fun `tokenize splits on non letters`() =
        assertEquals(listOf("free", "porn", "видео"), tokenize("Free-PORN | Видео!"))

    @Test
    fun `extractHost handles missing scheme and www`() {
        assertEquals("pornhub.com", extractHost("www.pornhub.com/view?x=1"))
        assertEquals("m.example.org", extractHost("https://m.example.org"))
        assertNull(extractHost("поиск в google"))
    }

    @Test
    fun `extractHost strips port and userinfo`() {
        assertEquals("example.com", extractHost("http://user@Example.com:8080/a"))
    }
}
