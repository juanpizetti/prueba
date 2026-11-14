package ar.escusub.controlips

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreensTest {
    private fun login(id: Long) = Session.guardar(ApplicationProvider.getApplicationContext(), id)

    @Test fun abreFormularioTecnico() {
        login(6)
        Robolectric.buildActivity(DeviceFormActivity::class.java).setup()
    }

    @Test fun abrePantallasAdmin() {
        login(3)
        Robolectric.buildActivity(MainActivity::class.java).setup()
        Robolectric.buildActivity(DeviceFormActivity::class.java).setup()
        Robolectric.buildActivity(UsersActivity::class.java).setup()
    }

    @Test fun menuVisibleEnPantallaPrincipal() {
        login(3)
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        org.junit.Assert.assertNotNull(a.supportActionBar)
    }

    @Test fun cargaPcYQuedaPendiente() {
        login(6)
        val a = Robolectric.buildActivity(DeviceFormActivity::class.java).setup().get()
        a.findViewById<android.widget.EditText>(R.id.etNombre).setText("PC-01")
        a.findViewById<android.widget.EditText>(R.id.etIp).setText("192.168.1.10")
        a.findViewById<android.widget.EditText>(R.id.etMac).setText("aa-bb-cc-dd-ee-ff")
        a.findViewById<android.widget.EditText>(R.id.etOficina).setText("Sistemas")
        a.findViewById<android.widget.EditText>(R.id.etCorreo).setText("pc@escusub.mil.ar")
        a.findViewById<android.view.View>(R.id.btnGuardar).performClick()
        org.junit.Assert.assertTrue(a.isFinishing)
        val db = DbHelper.get(a)
        val d = db.dispositivos(null, "", null).single()
        org.junit.Assert.assertEquals("AA:BB:CC:DD:EE:FF", d.direccionMac)
        org.junit.Assert.assertFalse(d.activo)

        // Misma MAC escrita distinto: debe rechazarse
        val b = Robolectric.buildActivity(DeviceFormActivity::class.java).setup().get()
        b.findViewById<android.widget.EditText>(R.id.etNombre).setText("PC-02")
        b.findViewById<android.widget.EditText>(R.id.etIp).setText("192.168.1.11")
        b.findViewById<android.widget.EditText>(R.id.etMac).setText("AABBCCDDEEFF")
        b.findViewById<android.widget.EditText>(R.id.etOficina).setText("Sistemas")
        b.findViewById<android.widget.EditText>(R.id.etCorreo).setText("pc2@escusub.mil.ar")
        b.findViewById<android.view.View>(R.id.btnGuardar).performClick()
        org.junit.Assert.assertFalse(b.isFinishing)
        org.junit.Assert.assertEquals(1, db.dispositivos(null, "", null).size)
    }

    @Test fun abreLogin() {
        Robolectric.buildActivity(LoginActivity::class.java).setup()
    }
}
