package ar.escusub.controlips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun urlDelServidorNormalizada() {
        val ok = "https://orjagamijujyrwciagit.supabase.co"
        assertEquals(ok, Api.normalizarUrl("https://orjagamijujyrwciagit.supabase.co/rest/v1/"))
        assertEquals(ok, Api.normalizarUrl(" https://orjagamijujyrwciagit.supabase.co/ "))
        assertEquals(ok, Api.normalizarUrl(ok))
    }
}
