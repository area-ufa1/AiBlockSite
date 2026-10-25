package app.tabguard.core.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeShortsTest {
    private val yt = YouTubeShorts.PACKAGE
    private fun id(name: String) = "$yt:id/$name"
    private fun screen(vararg nodes: UiNode) = FakeNode(className = "android.widget.FrameLayout", children = nodes.toList())

    @Test fun `shorts player is recognized`() = assertTrue(
        YouTubeShorts.isShortsPlayer(yt, screen(
            FakeNode(viewId = id("reel_watch_player")),
            FakeNode(children = listOf(FakeNode(viewId = id("reel_progress_bar"), className = "android.widget.SeekBar"))),
            FakeNode(text = "Подписаться"),
        )),
    )

    @Test fun `feed with a shorts shelf is not the player`() = assertFalse(
        YouTubeShorts.isShortsPlayer(yt, screen(
            FakeNode(viewId = id("results")),
            FakeNode(text = "Shorts"),
            FakeNode(viewId = id("reel_item_thumbnail")),
            FakeNode(text = "Главная"),
        )),
    )

    @Test fun `ordinary video is not the player`() = assertFalse(
        YouTubeShorts.isShortsPlayer(yt, screen(
            FakeNode(viewId = id("watch_player")),
            FakeNode(viewId = id("time_bar"), className = "android.widget.SeekBar"),
            FakeNode(text = "Лекция по физике"),
        )),
    )

    @Test fun `other apps are ignored`() = assertFalse(
        YouTubeShorts.isShortsPlayer("com.example.player", screen(FakeNode(viewId = "com.example.player:id/reel_progress_bar"))),
    )
}
