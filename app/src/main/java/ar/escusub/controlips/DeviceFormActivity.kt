package ar.escusub.controlips

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import ar.escusub.controlips.databinding.ActivityDeviceFormBinding
import kotlinx.coroutines.Job
import org.json.JSONObject

class DeviceFormActivity : BaseActivity() {

    companion object { const val EXTRA_DISPOSITIVO = "dispositivo" }

    private lateinit var b: ActivityDeviceFormBinding
    private lateinit var yo: Tecnico
    private var editando: Dispositivo? = null
    private var redes: List<Red> = emptyList()
    private var red: Red? = null
    private var previa: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        yo = usuarioActual() ?: return
        b = ActivityDeviceFormBinding.inflate(layoutInflater)
        setContentView(b.root)

        editando = intent.getStringExtra(EXTRA_DISPOSITIVO)?.let { Api.dispositivo(JSONObject(it)) }
        val e = editando
        setupToolbar(if (e == null) "Cargar PC" else "Editar PC", yo.nombreCompleto, back = true)

        b.etTipo.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, Dispositivo.TIPOS))
        b.etTipo.setText(e?.tipo ?: Dispositivo.TIPOS[0], false)

        if (e != null) {
            b.etNombre.setText(e.nombreEquipo)
            b.etIp.setText(e.direccionIp)
            b.etMac.setText(e.direccionMac)
            b.etOficina.setText(e.oficina)
            b.etCorreo.setText(e.correoInterno)
            b.tvAviso.visibility = View.GONE
        }

        // IP automática:
        //  - Alta: siempre automática para el técnico; el admin puede pasar a manual.
        //  - Edición: se conserva la IP; sólo el admin puede cambiarla.
        b.swAutoIp.visibility = if (yo.esAdmin) View.VISIBLE else View.GONE
        b.swAutoIp.isChecked = e == null
        b.swAutoIp.setOnCheckedChangeListener { _, _ -> refrescarIp() }
        // El técnico no puede cambiar la red (ni la IP) de un equipo ya cargado.
        if (e != null && !yo.esAdmin) b.tilRed.isEnabled = false

        listOf(b.etNombre to b.tilNombre, b.etIp to b.tilIp, b.etMac to b.tilMac,
            b.etOficina to b.tilOficina, b.etCorreo to b.tilCorreo).forEach { (et, til) ->
            et.doAfterTextChanged { til.error = null }
        }
        b.btnGuardar.setOnClickListener { guardar() }
        b.btnGuardar.isEnabled = false
        cargarRedes()
    }

    private fun cargarRedes() {
        conServidor {
            redes = Api.lista(rpc("redes_listar"), Api::red)
            val e = editando
            red = if (e != null) redes.firstOrNull { it.id == e.redId } else redes.firstOrNull()
            b.etRed.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, redes.map { it.toString() }))
            red?.let { b.etRed.setText(it.toString(), false) }
            b.etRed.setOnItemClickListener { _, _, pos, _ -> red = redes[pos]; b.tilRed.error = null; refrescarIp() }
            b.btnGuardar.isEnabled = true
            refrescarIp()
        }
    }

    private val ipAutomatica get() = b.swAutoIp.isChecked
    private val sinCambioDeIp get() = editando != null && !yo.esAdmin

    /** Muestra la IP que se va a asignar (consultando al servidor) o habilita la edición manual. */
    private fun refrescarIp() {
        val e = editando
        b.tilIp.error = null
        previa?.cancel()
        when {
            sinCambioDeIp -> {
                b.etIp.isEnabled = false
                b.etIp.setText(e!!.direccionIp)
                b.tilIp.hint = "IP asignada"
                b.tilIp.helperText = "Sólo el administrador puede cambiar la IP"
            }
            ipAutomatica -> {
                val r = red
                b.etIp.isEnabled = false
                b.tilIp.hint = "IP que se asignará"
                b.etIp.setText("")
                if (r == null) { b.tilIp.helperText = "Elegí una red"; return }
                b.tilIp.helperText = "Consultando IP libre en ${r.nombre}…"
                previa = conServidor {
                    val p = rpc("vista_previa_ip", "p_red_id" to r.id) as JSONObject
                    val ip = if (p.isNull("ip")) null else p.getString("ip")
                    b.etIp.setText(ip ?: "")
                    b.tilIp.helperText = if (ip != null)
                        "Automática · ${r.nombre} ${r.cidr} · gateway ${r.gateway} · ${p.getInt("libres")} libres" else null
                    if (ip == null) b.tilIp.error = "No quedan IPs libres en ${r.nombre} (${r.ipInicio} – ${r.ipFin}). " +
                        if (yo.esAdmin) "Ampliá el rango desde el menú Redes." else "Pedile al administrador que lo amplíe."
                }
            }
            else -> {
                b.etIp.isEnabled = true
                b.etIp.setText(e?.direccionIp ?: "")
                b.tilIp.hint = "Dirección IP (manual)"
                b.tilIp.helperText = red?.let { "Debe pertenecer a ${it.cidr} (gateway ${it.gateway})" } ?: "Elegí una red"
            }
        }
    }

    private fun guardar() {
        val nombre = b.etNombre.text.toString().trim()
        val oficina = b.etOficina.text.toString().trim()
        val correo = b.etCorreo.text.toString().trim()
        val mac = Validators.normalizarMac(b.etMac.text.toString())
        val ipManual = if (!sinCambioDeIp && !ipAutomatica) Validators.normalizarIp(b.etIp.text.toString()) else null

        // Controles rápidos en el celular; el servidor vuelve a validar todo (IP/MAC únicas, red, permisos).
        var ok = true
        if (nombre.isEmpty()) { b.tilNombre.error = "Obligatorio"; ok = false }
        if (red == null && !sinCambioDeIp) { b.tilRed.error = "Elegí la red"; ok = false }
        if (!sinCambioDeIp && !ipAutomatica && ipManual == null) { b.tilIp.error = "IP inválida (formato 0-255.0-255.0-255.0-255)"; ok = false }
        if (mac == null) { b.tilMac.error = "MAC inválida (12 dígitos hexadecimales)"; ok = false }
        if (oficina.isEmpty()) { b.tilOficina.error = "Obligatorio"; ok = false }
        if (!Validators.correoValido(correo)) { b.tilCorreo.error = "Correo inválido"; ok = false }
        if (!ok) return

        val e = editando
        conServidor({ b.btnGuardar.isEnabled = !it }) {
            val r = rpc("dispositivo_guardar",
                "p_id" to e?.id,
                "p_nombre" to nombre,
                "p_mac" to mac,
                "p_oficina" to oficina,
                "p_correo" to correo,
                "p_tipo" to b.etTipo.text.toString(),
                "p_red_id" to (red?.id ?: e?.redId),
                // null = el servidor asigna la primera IP libre de la red.
                "p_ip" to if (sinCambioDeIp) e!!.direccionIp else ipManual,
            ) as JSONObject
            val d = Api.dispositivo(r)
            if (e != null && e.direccionIp == d.direccionIp) finish() else mostrarIpAsignada(d)
        }
    }

    /** Informa la IP asignada antes de cerrar el formulario. */
    private fun mostrarIpAsignada(d: Dispositivo) {
        val titulo = if (editando == null) "PC registrada" else "IP actualizada"
        val estado = if (editando == null) "\n\nQueda en ROJO (pendiente) hasta que el administrador la active." else ""
        dialogo().setTitle(titulo)
            .setMessage(buildString {
                append("Equipo: ${d.nombreEquipo}\n\nIP asignada:  ${d.direccionIp}\n")
                if (d.redNombre != null) append("Red:  ${d.redNombre} (${d.redCidr})\nMáscara:  ${d.redMascara}\nGateway:  ${d.redGateway}\n")
                append("MAC:  ${d.direccionMac}$estado")
            })
            .setCancelable(false)
            .setNeutralButton("Copiar IP") { _, _ ->
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("IP", d.direccionIp))
                Toast.makeText(this, "IP ${d.direccionIp} copiada", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("Aceptar", null)
            .setOnDismissListener { finish() }
            .show()
    }
}
