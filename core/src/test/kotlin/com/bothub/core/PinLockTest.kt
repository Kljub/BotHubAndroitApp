package com.bothub.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinLockTest {
    private class MapStore : PinLock.Store {
        val map = mutableMapOf<String, String>()
        override fun get(name: String) = map[name]
        override fun put(name: String, value: String?) { if (value == null) map.remove(name) else map[name] = value }
    }

    @Test
    fun `pin is hashed, checked and wrong tries cost time`() {
        var now = 1_000_000L
        val store = MapStore()
        val lock = PinLock(store) { now }
        assertFalse(lock.enabled)
        assertEquals(PinLock.Result.Ok, lock.check("0000"))
        lock.setPin("4711")
        assertTrue(lock.enabled)
        assertFalse(store.map.values.any { it.contains("4711") })
        assertEquals(PinLock.Result.Ok, lock.check("4711"))
        repeat(4) { assertEquals(PinLock.Result.Wrong(4 - it), lock.check("1234")) }
        assertEquals(PinLock.Result.Wait(30_000), lock.check("1234"))
        assertEquals(PinLock.Result.Wait(30_000), lock.check("4711")) // even the right PIN waits
        now += 30_000
        assertEquals(PinLock.Result.Wait(60_000), lock.check("0000")) // doubles
        now += 60_000
        assertEquals(PinLock.Result.Ok, lock.check("4711"))
        assertEquals(PinLock.Result.Wrong(4), lock.check("9999")) // count starts again
    }

    @Test
    fun `timeouts, biometrics and disabling`() {
        val lock = PinLock(MapStore())
        assertFalse(lock.shouldLock(10_000_000))
        lock.setPin("1234")
        lock.biometric = true
        assertTrue(lock.biometric)
        assertTrue(lock.shouldLock(0))
        lock.timeout = PinLock.Timeout.MINUTE
        assertFalse(lock.shouldLock(59_000))
        assertTrue(lock.shouldLock(60_000))
        assertFalse(PinLock.validPin("12a4") || PinLock.validPin("12345"))
        lock.disable()
        assertFalse(lock.enabled || lock.biometric)
    }
}
