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

    private fun elegirRed(a: DeviceFormActivity, pos: Int) {
        val et = a.findViewById<android.widget.AutoCompleteTextView>(R.id.etRed)
        et.listSelection = pos
        et.onItemClickListener.onItemClick(null, null, pos, 0)
    }

    @Test fun redesIniciales() {
        val db = DbHelper.get(ApplicationProvider.getApplicationContext())
        val r = db.redes().associateBy { it.nombre }
        org.junit.Assert.assertEquals("172.21.88.0/23", r["Red Principal"]!!.cidr)
        org.junit.Assert.assertEquals("172.21.89.255", r["Red Principal"]!!.broadcast)
        org.junit.Assert.assertEquals(509, db.ipsLibres(r["Red Principal"]!!))   // 510 hosts - gateway
        org.junit.Assert.assertEquals("172.23.28.0/26", r["Red DMZ"]!!.cidr)
        org.junit.Assert.assertEquals(61, db.ipsLibres(r["Red DMZ"]!!))          // 62 hosts - gateway
        org.junit.Assert.assertEquals("192.168.66.0/24", r["Red Wifi"]!!.cidr)
        org.junit.Assert.assertEquals(253, db.ipsLibres(r["Red Wifi"]!!))
        org.junit.Assert.assertEquals("255.255.254.0", Validators.mascaraTexto(23))
        org.junit.Assert.assertEquals("255.255.255.192", Validators.mascaraTexto(26))
    }

    @Test fun asignaIpAutomaticaSegunLaRedYLaInforma() {
        login(6)
        val db = DbHelper.get(ApplicationProvider.getApplicationContext())

        // Por defecto: Red Principal, la primera IP después del gateway.
        val pre = Robolectric.buildActivity(DeviceFormActivity::class.java).setup().get()
        val etIp = pre.findViewById<android.widget.EditText>(R.id.etIp)
        org.junit.Assert.assertFalse(etIp.isEnabled)
        org.junit.Assert.assertEquals("172.21.88.2", etIp.text.toString())
        elegirRed(pre, 2)
        org.junit.Assert.assertEquals("192.168.66.2", etIp.text.toString())

        val a = cargar("PC-01", "aa-bb-cc-dd-ee-01")
        val dlg = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        val msg = dlg.findViewById<android.widget.TextView>(android.R.id.message)!!.text.toString()
        org.junit.Assert.assertTrue(msg, msg.contains("IP asignada:  172.21.88.2"))
        org.junit.Assert.assertTrue(msg, msg.contains("Red Principal"))
        org.junit.Assert.assertTrue(msg, msg.contains("255.255.254.0"))
        dlg.dismiss()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        org.junit.Assert.assertTrue(a.isFinishing)

        // DMZ reducida a 3 IPs para probar el rango completo.
        val dmz = db.redes().first { it.nombre == "Red DMZ" }
        db.guardarRed(dmz.copy(ipInicio = "172.23.28.2", ipFin = "172.23.28.4"))
        fun cargarEnDmz(n: String, mac: String) = Robolectric.buildActivity(DeviceFormActivity::class.java).setup().get().also { f ->
            elegirRed(f, 1)
            f.findViewById<android.widget.EditText>(R.id.etNombre).setText(n)
            f.findViewById<android.widget.EditText>(R.id.etMac).setText(mac)
            f.findViewById<android.widget.EditText>(R.id.etOficina).setText("DMZ")
            f.findViewById<android.widget.EditText>(R.id.etCorreo).setText("srv@escusub.mil.ar")
            f.findViewById<android.view.View>(R.id.btnGuardar).performClick()
        }
        cargarEnDmz("SRV-1", "00:00:00:00:00:01")
        cargarEnDmz("SRV-2", "00:00:00:00:00:02")
        db.eliminarDispositivo(db.dispositivos(null, "SRV-1", null).single().id)  // libera .2
        cargarEnDmz("SRV-3", "00:00:00:00:00:03")
        cargarEnDmz("SRV-4", "00:00:00:00:00:04")
        val ips = db.dispositivos(null, "", null, dmz.id).associate { it.nombreEquipo to it.direccionIp }
        org.junit.Assert.assertEquals(mapOf("SRV-2" to "172.23.28.3", "SRV-3" to "172.23.28.2", "SRV-4" to "172.23.28.4"), ips)
        org.junit.Assert.assertTrue(db.dispositivos(null, "", null).none { it.activo })

        val lleno = cargarEnDmz("SRV-5", "00:00:00:00:00:05")
        org.junit.Assert.assertFalse(lleno.isFinishing)
        org.junit.Assert.assertEquals(3, db.dispositivos(null, "", null, dmz.id).size)
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

    private fun formManual(ip: String): DeviceFormActivity {
        val a = Robolectric.buildActivity(DeviceFormActivity::class.java).setup().get()
        a.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.swAutoIp).isChecked = false
        elegirRed(a, 1) // DMZ
        a.findViewById<android.widget.EditText>(R.id.etIp).setText(ip)
        a.findViewById<android.widget.EditText>(R.id.etNombre).setText("SRV")
        a.findViewById<android.widget.EditText>(R.id.etMac).setText("11:22:33:44:55:66")
        a.findViewById<android.widget.EditText>(R.id.etOficina).setText("Sala")
        a.findViewById<android.widget.EditText>(R.id.etCorreo).setText("srv@escusub.mil.ar")
        a.findViewById<android.view.View>(R.id.btnGuardar).performClick()
        return a
    }

    @Test fun adminCargaIpManualDentroDeLaRed() {
        login(3)
        val db = DbHelper.get(ApplicationProvider.getApplicationContext())
        org.junit.Assert.assertFalse(formManual("192.168.66.20").isFinishing)  // otra red
        org.junit.Assert.assertFalse(formManual("172.23.28.1").isFinishing)    // gateway
        org.junit.Assert.assertFalse(formManual("172.23.28.63").isFinishing)   // broadcast
        org.junit.Assert.assertTrue(db.dispositivos(null, "", null).isEmpty())
        formManual("172.23.28.50")
        val d = db.dispositivos(null, "", null).single()
        org.junit.Assert.assertEquals("172.23.28.50", d.direccionIp)
        org.junit.Assert.assertEquals("Red DMZ", d.redNombre)
    }

    @Test fun abreAcercaDe() {
        login(3)
        val a = Robolectric.buildActivity(AboutActivity::class.java).setup().get()
        val t = a.findViewById<android.widget.TextView>(R.id.tvRedes).text.toString()
        org.junit.Assert.assertTrue(t, t.contains("172.21.88.0/23") && t.contains("172.23.28.0/26") && t.contains("192.168.66.0/24"))
        DbHelper.reiniciar(); Session.cerrar(a)
        Robolectric.buildActivity(AboutActivity::class.java).setup() // también sin sesión (desde el login)
    }

    @Test fun actualizaDesdeVersion1SinPerderDatos() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val f = ctx.getDatabasePath("control_ips.db").apply { parentFile!!.mkdirs(); delete() }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(f, null).use { v1 ->
            v1.execSQL("CREATE TABLE tecnicos (id INTEGER PRIMARY KEY AUTOINCREMENT, usuario TEXT NOT NULL UNIQUE COLLATE NOCASE, password TEXT NOT NULL, nombre_completo TEXT NOT NULL, rol TEXT NOT NULL DEFAULT 'tecnico')")
            v1.execSQL("CREATE TABLE dispositivos (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre_equipo TEXT NOT NULL, direccion_ip TEXT NOT NULL UNIQUE, direccion_mac TEXT NOT NULL UNIQUE COLLATE NOCASE, oficina TEXT NOT NULL, correo_interno TEXT NOT NULL, tecnico_id INTEGER, fecha_registro TEXT, tipo TEXT NOT NULL DEFAULT 'Institucional', estado TEXT NOT NULL DEFAULT 'pendiente', activado_por INTEGER, fecha_activacion TEXT)")
            v1.execSQL("INSERT INTO tecnicos VALUES (3,'admin','x','DEC','admin')")
            v1.execSQL("INSERT INTO dispositivos (nombre_equipo,direccion_ip,direccion_mac,oficina,correo_interno,tecnico_id) VALUES ('VIEJA','192.168.1.10','AA:AA:AA:AA:AA:AA','A','a@b.cc',3)")
            v1.execSQL("INSERT INTO dispositivos (nombre_equipo,direccion_ip,direccion_mac,oficina,correo_interno,tecnico_id) VALUES ('DMZ','172.23.28.5','BB:BB:BB:BB:BB:BB','A','a@b.cc',3)")
            v1.version = 1
        }
        val db = DbHelper.get(ctx)
        val d = db.dispositivos(null, "VIEJA", null).single()
        org.junit.Assert.assertEquals("Red DMZ", db.dispositivos(null, "DMZ", null).single().redNombre)
        org.junit.Assert.assertEquals("172.23.28.2", db.siguienteIpLibre(db.redes().first { it.nombre == "Red DMZ" }))
        org.junit.Assert.assertEquals("VIEJA", d.nombreEquipo)
        org.junit.Assert.assertNull(d.redId) // 192.168.1.10 no pertenece a ninguna de las 3 redes
        org.junit.Assert.assertEquals(3, db.redes().size)
    }

    @Test fun abreLogin() {
        Robolectric.buildActivity(LoginActivity::class.java).setup()
    }
}
