package app.tabguard.core.lock

import app.tabguard.core.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTest {
    private val min = 60_000L
    private val salt = "00112233445566778899aabbccddeeff"
    private fun cat(
        enabled: Boolean = true, keywords: List<String> = listOf("a", "b"), domains: Set<String> = setOf("x.com"),
        prefixes: List<String> = listOf("y.com/s/"), only: Set<String> = emptySet(),
    ) = Category(1, "C", enabled, keywords, domains, prefixes, only)

    @Test fun `unconfigured lock is open`() = assertTrue(Lock.isUnlocked(LockState(), 0))

    @Test fun `pause opens a 5 minute window after the countdown`() {
        var s = Lock.startCountdown(Lock.setPauseMode(LockState(), 20), 0)
        assertFalse(Lock.isUnlocked(Lock.settle(s, 19 * min), 19 * min))
        assertEquals(15 * min, Lock.remainingCountdownMs(s, 5 * min))
        s = Lock.settle(s, 20 * min)
        assertTrue(Lock.isUnlocked(s, 24 * min))
        assertFalse(Lock.isUnlocked(s, 25 * min + 1))
    }

    @Test fun `countdown survives restart`() {
        val s = Lock.startCountdown(Lock.setPauseMode(LockState(), 5), 1000)
        assertTrue(Lock.isUnlocked(Lock.settle(s.copy(), 1000 + 6 * min), 1000 + 6 * min))
    }

    @Test fun `cancel countdown keeps it locked`() {
        val s = Lock.cancelCountdown(Lock.startCountdown(Lock.setPauseMode(LockState(), 5), 0))
        assertFalse(Lock.isUnlocked(Lock.settle(s, 30 * min), 30 * min))
        assertNull(Lock.remainingCountdownMs(s, 0))
    }

    @Test fun `correct pin opens window and wrong does not`() {
        val s = Lock.setPartnerMode(LockState(), "1234", salt)
        assertNotEquals("1234", s.pinHash)
        val bad = Lock.tryPin(s, "0000", 0)
        assertFalse(bad.ok)
        val good = Lock.tryPin(bad.state, "1234", 0)
        assertTrue(good.ok)
        assertTrue(Lock.isUnlocked(good.state, 4 * min))
    }

    @Test fun `pin lockout ends after 30 minutes`() {
        var s = Lock.setPartnerMode(LockState(), "1234", salt)
        repeat(5) { s = Lock.tryPin(s, "9999", 0).state }
        assertFalse(Lock.tryPin(s, "1234", 29 * min).ok)
        assertTrue(Lock.tryPin(s, "1234", 30 * min + 1).ok)
    }

    @Test fun `invalid settings are rejected`() {
        assertThrows { Lock.setPauseMode(LockState(), 4) }
        assertThrows { Lock.setPauseMode(LockState(), 61) }
        assertThrows { Lock.setPartnerMode(LockState(), "123", salt) }
        assertThrows { Lock.setPartnerMode(LockState(), "12a4", salt) }
    }

    @Test fun `pin hash matches the extension`() =
        // Same algorithm as pc/extension/src/core/lock.js: sha256(salt + pin) as hex.
        assertEquals(Lock.hashPin("s", "1234"), java.security.MessageDigest.getInstance("SHA-256")
            .digest("s1234".toByteArray()).joinToString("") { "%02x".format(it) })

    @Test fun `weakening rules`() {
        assertTrue(Lock.isWeakening(cat(), null))
        assertTrue(Lock.isWeakening(cat(), cat(enabled = false)))
        assertTrue(Lock.isWeakening(cat(), cat(keywords = listOf("a"))))
        assertTrue(Lock.isWeakening(cat(), cat(domains = emptySet())))
        assertTrue(Lock.isWeakening(cat(), cat(prefixes = emptyList())))
        assertTrue(Lock.isWeakening(cat(), cat(only = setOf("youtube.com"))))
        assertFalse(Lock.isWeakening(cat(only = setOf("a.com")), cat(only = emptySet())))
        assertFalse(Lock.isWeakening(cat(enabled = false), cat()))
        assertFalse(Lock.isWeakening(cat(), cat(keywords = listOf("a", "b", "c"))))
        assertFalse(Lock.isWeakening(null, cat()))
    }

    private fun assertThrows(block: () -> Unit) {
        val threw = try { block(); false } catch (e: IllegalArgumentException) { true }
        assertTrue("expected IllegalArgumentException", threw)
    }
}
