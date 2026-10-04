package app.tabguard.service

import android.app.Application
import android.app.Notification
import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import app.tabguard.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeepAliveTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `main activity is hidden from recents so it cannot be swiped away`() {
        val info = app.packageManager.getActivityInfo(ComponentName(app, MainActivity::class.java), 0)
        assertTrue(info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
    }

    @Test
    fun `guard notification is ongoing, silent and opens the app`() {
        val n = GuardNotification.build(app)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(GuardNotification.CHANNEL_ID, n.channelId)
        assertEquals("Страж вкладок защищает", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(MainActivity::class.java.name, org.robolectric.Shadows.shadowOf(n.contentIntent).savedIntent.component?.className)
    }

    @Test
    fun `leaving the app removes its task from recents`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        controller.pause().stop()
        assertTrue(controller.get().isFinishing)
    }
}
