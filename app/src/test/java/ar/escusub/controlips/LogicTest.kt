package ar.escusub.controlips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    @Test fun ipNormalizada() {
        assertEquals("192.168.1.10", Validators.normalizarIp("192.168.001.010"))
        assertEquals("10.0.0.1", Validators.normalizarIp(" 10.0.0.1 "))
        assertNull(Validators.normalizarIp("256.1.1.1"))
        assertNull(Validators.normalizarIp("1.2.3"))
        assertNull(Validators.normalizarIp("1.2.3.4.5"))
        assertNull(Validators.normalizarIp("a.b.c.d"))
    }

    @Test fun macNormalizada() {
        val esperado = "AA:BB:CC:DD:EE:0F"
        assertEquals(esperado, Validators.normalizarMac("aa:bb:cc:dd:ee:0f"))
        assertEquals(esperado, Validators.normalizarMac("AA-BB-CC-DD-EE-0F"))
        assertEquals(esperado, Validators.normalizarMac("aabb.ccdd.ee0f"))
        assertEquals(esperado, Validators.normalizarMac("AABBCCDDEE0F"))
        assertNull(Validators.normalizarMac("AA:BB:CC:DD:EE"))
        assertNull(Validators.normalizarMac("GG:BB:CC:DD:EE:FF"))
    }

    @Test fun passwordCompatibleConPhp() {
        val h = Passwords.hash("secreta")
        assertTrue(Passwords.verificar("secreta", h))
        assertFalse(Passwords.verificar("otra", h))
        // Mismo hash con prefijo de PHP password_hash()
        val php = "\$2y\$" + h.substring(4)
        assertTrue(Passwords.verificar("secreta", php))
        assertFalse(Passwords.verificar("x", "basura"))
    }
}
