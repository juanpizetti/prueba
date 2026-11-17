package ar.escusub.controlips

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** Pruebas de punta a punta: app (Robolectric) ↔ API ↔ PostgreSQL con supabase/schema.sql. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnlineTest {
    private lateinit var srv: PgRestFalso
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun iniciar() {
        assumeTrue("PostgreSQL local no disponible", PgRestFalso.disponible())
        srv = PgRestFalso()
        Api.guardarConfig(ctx, srv.url, PgRestFalso.CLAVE)
    }

    @After fun terminar() { if (::srv.isInitialized) srv.close() }

    // ------------------------------------------------------------ utilidades

    private fun esperar(desc: String, cond: () -> Boolean) {
        val fin = System.currentTimeMillis() + 15_000
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            if (System.currentTimeMillis() > fin) throw AssertionError("Tiempo agotado esperando: $desc")
            Thread.sleep(20)
        }
    }

    private fun mensajeDialogo(): String? =
        (ShadowDialog.getLatestDialog() as? AlertDialog)?.takeIf { it.isShowing }
            ?.findViewById<TextView>(android.R.id.message)?.text?.toString()

    private fun esperarDialogo(contiene: String): AlertDialog {
        esperar("diálogo con \"$contiene\" (último: ${mensajeDialogo()})") { mensajeDialogo()?.contains(contiene) == true }
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    private fun login(usuario: String) = runBlocking {
        val r = Api.rpc(ctx, "login", mapOf("p_usuario" to usuario, "p_password" to "clave")) as JSONObject
        Session.guardar(ctx, r.getString("token"), Api.tecnico(r.getJSONObject("usuario")))
    }

    private fun Activity.texto(id: Int, valor: String) = findViewById<EditText>(id).setText(valor)

    private fun formulario(): DeviceFormActivity {
        val a = Robolectric.buildActivity(DeviceFormActivity::class.java).setup().get()
        esperar("redes cargadas") { a.findViewById<View>(R.id.btnGuardar).isEnabled }
        return a
    }

    private fun elegirRed(a: DeviceFormActivity, pos: Int) {
        val et = a.findViewById<AutoCompleteTextView>(R.id.etRed)
        et.onItemClickListener.onItemClick(null, null, pos, 0)
    }

    private fun automatica(a: DeviceFormActivity) {
        a.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.swAutoIp).isChecked = true
    }

    private fun completar(a: DeviceFormActivity, nombre: String, mac: String) {
        a.texto(R.id.etNombre, nombre)
        a.texto(R.id.etMac, mac)
        a.texto(R.id.etOficina, "Sistemas")
        a.texto(R.id.etCorreo, "pc@escusub.mil.ar")
    }

    // ---------------------------------------------------------------- pruebas

    @Test fun loginCorrectoAbreLaPantallaPrincipal() {
        val a = Robolectric.buildActivity(LoginActivity::class.java).setup().get()
        a.texto(R.id.etUser, "pizetti")   // no distingue mayúsculas
        a.texto(R.id.etPass, "clave")
        a.findViewById<View>(R.id.btnLogin).performClick()
        esperar("abre MainActivity") { shadowOf(a).nextStartedActivity?.component?.className == MainActivity::class.java.name }
        assertNotNull(Session.token(ctx))
        assertEquals("JUAN PIZETTI", Session.usuario(ctx)!!.nombreCompleto)
    }

    @Test fun loginIncorrectoMuestraError() {
        val a = Robolectric.buildActivity(LoginActivity::class.java).setup().get()
        a.texto(R.id.etUser, "admin")
        a.texto(R.id.etPass, "mala")
        a.findViewById<View>(R.id.btnLogin).performClick()
        esperarDialogo("Usuario o contraseña incorrectos")
    }

    @Test fun cargarPcAsignaLaIpEnElServidorYLaInforma() {
        login("PIZETTI")
        val a = formulario()
        val etIp = a.findViewById<EditText>(R.id.etIp)
        assertTrue("por defecto la IP se escribe a mano", etIp.isEnabled)
        automatica(a)
        esperar("vista previa Red Principal") { etIp.text.toString() == "172.21.88.2" }
        assertTrue(!etIp.isEnabled)
        elegirRed(a, 2) // Wifi
        esperar("vista previa Red Wifi") { etIp.text.toString() == "192.168.66.2" }

        completar(a, "PC-01", "aa-bb-cc-dd-ee-01")
        a.findViewById<View>(R.id.btnGuardar).performClick()
        val dlg = esperarDialogo("IP asignada:  192.168.66.2")
        val msg = mensajeDialogo()!!
        assertTrue(msg, msg.contains("Red Wifi") && msg.contains("255.255.255.0") && msg.contains("192.168.66.1"))
        dlg.dismiss(); shadowOf(Looper.getMainLooper()).idle()
        assertTrue(a.isFinishing)

        assertEquals("pendiente|6|AA:BB:CC:DD:EE:01", srv.consulta(
            "select estado || '|' || tecnico_id || '|' || upper(direccion_mac::text) from cip.dispositivos where host(direccion_ip) = '192.168.66.2'"))

        // La siguiente PC recibe la IP siguiente.
        val b = formulario()
        automatica(b)
        elegirRed(b, 2)
        completar(b, "PC-02", "aa-bb-cc-dd-ee-02")
        b.findViewById<View>(R.id.btnGuardar).performClick()
        esperarDialogo("IP asignada:  192.168.66.3")
    }

    @Test fun macRepetidaConOtroFormatoSeRechaza() {
        login("PIZETTI")
        val a = formulario()
        automatica(a)
        completar(a, "PC-01", "aa-bb-cc-dd-ee-ff")
        a.findViewById<View>(R.id.btnGuardar).performClick()
        esperarDialogo("IP asignada").dismiss()

        val b = formulario()
        automatica(b)
        completar(b, "PC-02", "AABB.CCDD.EEFF")
        b.findViewById<View>(R.id.btnGuardar).performClick()
        esperarDialogo("La MAC AA:BB:CC:DD:EE:FF ya está asignada al equipo \"PC-01\"")
        assertEquals("1", srv.consulta("select count(*) from cip.dispositivos"))
    }

    @Test fun tecnicoCargaIpManualYPuedeVolverAAutomatica() {
        login("PIZETTI")
        val a = formulario()
        val etIp = a.findViewById<EditText>(R.id.etIp)
        val til = a.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilIp)
        esperar("sugerencia de IP libre") { til.helperText?.contains("primera libre: 172.21.88.2") == true }

        // Sin IP escrita no guarda.
        completar(a, "PC-M1", "00:00:00:00:0a:01")
        a.findViewById<View>(R.id.btnGuardar).performClick()
        assertTrue(til.error.toString(), til.error!!.contains("Escribí la IP"))

        // Manual dentro de la red: se respeta.
        a.texto(R.id.etIp, "172.21.89.100")
        a.findViewById<View>(R.id.btnGuardar).performClick()
        esperarDialogo("IP asignada:  172.21.89.100").dismiss()
        assertEquals("6", srv.consulta("select tecnico_id from cip.dispositivos where host(direccion_ip) = '172.21.89.100'"))

        // Manual con una IP ya usada: el servidor la rechaza.
        val b = formulario()
        completar(b, "PC-M2", "00:00:00:00:0a:02")
        b.texto(R.id.etIp, "172.21.89.100")
        b.findViewById<View>(R.id.btnGuardar).performClick()
        esperarDialogo("La IP 172.21.89.100 ya está asignada al equipo \"PC-M1\"")

        // Alternar a automática y volver conserva lo escrito.
        b.texto(R.id.etIp, "172.21.89.101")
        automatica(b)
        esperar("vista previa automática") { b.findViewById<EditText>(R.id.etIp).text.toString() == "172.21.88.2" }
        b.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.swAutoIp).isChecked = false
        assertEquals("172.21.89.101", b.findViewById<EditText>(R.id.etIp).text.toString())

        // Automática: toma la primera libre.
        automatica(b)
        b.findViewById<View>(R.id.btnGuardar).performClick()
        esperarDialogo("IP asignada:  172.21.88.2")
        assertTrue(etIp.isEnabled)
    }

    @Test fun adminIpManualFueraDeLaRedSeRechaza() {
        login("admin")
        val a = formulario()
        elegirRed(a, 1) // DMZ
        a.texto(R.id.etIp, "192.168.66.20")
        completar(a, "SRV", "11:22:33:44:55:66")
        a.findViewById<View>(R.id.btnGuardar).performClick()
        esperarDialogo("La IP 192.168.66.20 no pertenece a Red DMZ (172.23.28.0/26)")

        a.texto(R.id.etIp, "172.23.28.50")
        a.findViewById<View>(R.id.btnGuardar).performClick()
        esperarDialogo("IP asignada:  172.23.28.50")
    }

    @Test fun adminVeTodoYActivaEnVerde() {
        login("PIZETTI")
        val f = formulario()
        automatica(f)
        completar(f, "PC-TEC", "00:00:00:00:00:01")
        f.findViewById<View>(R.id.btnGuardar).performClick()
        esperarDialogo("IP asignada").dismiss()

        login("admin")
        val m = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val rv = m.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler)
        esperar("lista cargada") { (rv.adapter?.itemCount ?: 0) == 1 }
        rv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
        rv.layout(0, 0, 1080, 1920)
        rv.getChildAt(0).findViewById<View>(R.id.btnToggle).performClick()
        esperar("activado en la base") { srv.consulta("select estado from cip.dispositivos") == "activo" }
        esperar("contador verde") { m.findViewById<TextView>(R.id.tvCountGreen).text.toString().endsWith("1") }
        assertEquals("DEC", srv.consulta("select t.nombre_completo from cip.dispositivos d join cip.tecnicos t on t.id = d.activado_por"))
    }

    @Test fun tecnicoNoPuedeUsarFuncionesDeAdmin() {
        login("PIZETTI")
        val e = runCatching { runBlocking { Api.rpc(ctx, "tecnicos_listar", mapOf("p_token" to Session.token(ctx))) } }.exceptionOrNull()
        assertTrue(e is ApiException && e.status == 403)
    }

    @Test fun sesionVencidaVuelveAlLogin() {
        login("PIZETTI")
        srv.consulta("delete from cip.sesiones returning 1")
        val m = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        esperarDialogo("Sesión vencida").dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(LoginActivity::class.java.name, shadowOf(m).nextStartedActivity.component!!.className)
        assertEquals(null, Session.token(ctx))
    }

    @Test fun sinConexionAvisa() {
        Api.guardarConfig(ctx, "http://127.0.0.1:1", PgRestFalso.CLAVE)
        val a = Robolectric.buildActivity(LoginActivity::class.java).setup().get()
        a.texto(R.id.etUser, "admin")
        a.texto(R.id.etPass, "clave")
        a.findViewById<View>(R.id.btnLogin).performClick()
        esperarDialogo("Sin conexión con el servidor")
    }

    @Test fun claveDeApiIncorrecta() {
        Api.guardarConfig(ctx, srv.url, "otra-clave-0123456789xyz")
        val a = Robolectric.buildActivity(LoginActivity::class.java).setup().get()
        a.texto(R.id.etUser, "admin")
        a.texto(R.id.etPass, "clave")
        a.findViewById<View>(R.id.btnLogin).performClick()
        esperarDialogo("La clave del servidor no es válida")
    }

    @Test fun acercaDeMuestraLasRedes() {
        login("admin")
        val a = Robolectric.buildActivity(AboutActivity::class.java).setup().get()
        val tv = a.findViewById<TextView>(R.id.tvRedes)
        esperar("redes en Acerca de") { tv.text.contains("172.21.88.0/23") }
        assertTrue(tv.text.contains("172.23.28.0/26") && tv.text.contains("192.168.66.0/24"))
    }

    @Test fun abrenTodasLasPantallas() {
        login("admin")
        Robolectric.buildActivity(UsersActivity::class.java).setup()
        Robolectric.buildActivity(MainActivity::class.java).setup()
        Session.cerrar(ctx)
        Robolectric.buildActivity(LoginActivity::class.java).setup()
        Robolectric.buildActivity(AboutActivity::class.java).setup()
    }
}
