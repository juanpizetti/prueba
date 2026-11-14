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
    @org.junit.Before fun limpiar() = DbHelper.reiniciar()

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

    private fun cargar(nombre: String, mac: String): DeviceFormActivity {
        val a = Robolectric.buildActivity(DeviceFormActivity::class.java).setup().get()
        a.findViewById<android.widget.EditText>(R.id.etNombre).setText(nombre)
        a.findViewById<android.widget.EditText>(R.id.etMac).setText(mac)
        a.findViewById<android.widget.EditText>(R.id.etOficina).setText("Sistemas")
        a.findViewById<android.widget.EditText>(R.id.etCorreo).setText("pc@escusub.mil.ar")
        a.findViewById<android.view.View>(R.id.btnGuardar).performClick()
        return a
    }

    @Test fun asignaIpAutomaticaYLaInforma() {
        login(6)
        val db = DbHelper.get(ApplicationProvider.getApplicationContext())
        db.guardarRangoIp("10.0.0.1", "10.0.0.3")

        // El técnico no puede escribir la IP: se muestra la que se asignará.
        val pre = Robolectric.buildActivity(DeviceFormActivity::class.java).setup().get()
        val etIp = pre.findViewById<android.widget.EditText>(R.id.etIp)
        org.junit.Assert.assertFalse(etIp.isEnabled)
        org.junit.Assert.assertEquals("10.0.0.1", etIp.text.toString())

        val a = cargar("PC-01", "aa-bb-cc-dd-ee-01")
        val dlg = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        val msg = dlg.findViewById<android.widget.TextView>(android.R.id.message)!!.text.toString()
        org.junit.Assert.assertTrue(msg, msg.contains("IP asignada:  10.0.0.1"))
        dlg.dismiss()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        org.junit.Assert.assertTrue(a.isFinishing)

        cargar("PC-02", "aa-bb-cc-dd-ee-02")
        // Se borra la primera: su IP vuelve a quedar libre y se reutiliza.
        db.eliminarDispositivo(db.dispositivos(null, "PC-01", null).single().id)
        cargar("PC-03", "aa-bb-cc-dd-ee-03")
        cargar("PC-04", "aa-bb-cc-dd-ee-04")
        val ips = db.dispositivos(null, "", null).associate { it.nombreEquipo to it.direccionIp }
        org.junit.Assert.assertEquals(mapOf("PC-02" to "10.0.0.2", "PC-03" to "10.0.0.1", "PC-04" to "10.0.0.3"), ips)
        org.junit.Assert.assertTrue(db.dispositivos(null, "", null).none { it.activo })

        // Rango completo: no se guarda.
        val lleno = cargar("PC-05", "aa-bb-cc-dd-ee-05")
        org.junit.Assert.assertFalse(lleno.isFinishing)
        org.junit.Assert.assertEquals(3, db.dispositivos(null, "", null).size)
    }

    @Test fun macRepetidaEscritaDistintoSeRechaza() {
        login(6)
        val db = DbHelper.get(ApplicationProvider.getApplicationContext())
        cargar("PC-01", "aa-bb-cc-dd-ee-ff")
        org.junit.Assert.assertEquals("AA:BB:CC:DD:EE:FF", db.dispositivos(null, "", null).single().direccionMac)
        val b = cargar("PC-02", "AABBCCDDEEFF")
        org.junit.Assert.assertFalse(b.isFinishing)
        org.junit.Assert.assertEquals(1, db.dispositivos(null, "", null).size)
    }

    @Test fun adminPuedeCargarIpManual() {
        login(3)
        val a = Robolectric.buildActivity(DeviceFormActivity::class.java).setup().get()
        a.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.swAutoIp).isChecked = false
        a.findViewById<android.widget.EditText>(R.id.etIp).setText("172.16.0.5")
        a.findViewById<android.widget.EditText>(R.id.etNombre).setText("SRV")
        a.findViewById<android.widget.EditText>(R.id.etMac).setText("11:22:33:44:55:66")
        a.findViewById<android.widget.EditText>(R.id.etOficina).setText("Sala")
        a.findViewById<android.widget.EditText>(R.id.etCorreo).setText("srv@escusub.mil.ar")
        a.findViewById<android.view.View>(R.id.btnGuardar).performClick()
        val db = DbHelper.get(a)
        org.junit.Assert.assertEquals("172.16.0.5", db.dispositivos(null, "", null).single().direccionIp)
    }

    @Test fun actualizaDesdeVersion1SinPerderDatos() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val f = ctx.getDatabasePath("control_ips.db").apply { parentFile!!.mkdirs(); delete() }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(f, null).use { v1 ->
            v1.execSQL("CREATE TABLE tecnicos (id INTEGER PRIMARY KEY AUTOINCREMENT, usuario TEXT NOT NULL UNIQUE COLLATE NOCASE, password TEXT NOT NULL, nombre_completo TEXT NOT NULL, rol TEXT NOT NULL DEFAULT 'tecnico')")
            v1.execSQL("CREATE TABLE dispositivos (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre_equipo TEXT NOT NULL, direccion_ip TEXT NOT NULL UNIQUE, direccion_mac TEXT NOT NULL UNIQUE COLLATE NOCASE, oficina TEXT NOT NULL, correo_interno TEXT NOT NULL, tecnico_id INTEGER, fecha_registro TEXT, tipo TEXT NOT NULL DEFAULT 'Institucional', estado TEXT NOT NULL DEFAULT 'pendiente', activado_por INTEGER, fecha_activacion TEXT)")
            v1.execSQL("INSERT INTO tecnicos VALUES (3,'admin','x','DEC','admin')")
            v1.execSQL("INSERT INTO dispositivos (nombre_equipo,direccion_ip,direccion_mac,oficina,correo_interno,tecnico_id) VALUES ('VIEJA','192.168.1.10','AA:AA:AA:AA:AA:AA','A','a@b.cc',3)")
            v1.version = 1
        }
        val db = DbHelper.get(ctx)
        org.junit.Assert.assertEquals("VIEJA", db.dispositivos(null, "", null).single().nombreEquipo)
        org.junit.Assert.assertEquals("192.168.1.11", db.siguienteIpLibre())
    }

    @Test fun abreLogin() {
        Robolectric.buildActivity(LoginActivity::class.java).setup()
    }
}
