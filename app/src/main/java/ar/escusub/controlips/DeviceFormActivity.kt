package ar.escusub.controlips

import android.content.ClipData
import android.content.ClipboardManager
import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import ar.escusub.controlips.databinding.ActivityDeviceFormBinding

class DeviceFormActivity : BaseActivity() {

    companion object { const val EXTRA_ID = "id" }

    private lateinit var b: ActivityDeviceFormBinding
    private lateinit var yo: Tecnico
    private var editando: Dispositivo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        yo = usuarioActual() ?: return
        b = ActivityDeviceFormBinding.inflate(layoutInflater)
        setContentView(b.root)

        val id = intent.getLongExtra(EXTRA_ID, 0)
        editando = if (id > 0) db.dispositivo(id) else null
        val e = editando
        // Un técnico sólo puede editar sus propios equipos pendientes.
        if (e != null && !yo.esAdmin && (e.activo || e.tecnicoId != yo.id)) { finish(); return }

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
        //  - Edición: se conserva la IP; sólo el admin puede cambiarla (manual o pidiendo una nueva automática).
        b.swAutoIp.visibility = if (yo.esAdmin) View.VISIBLE else View.GONE
        b.swAutoIp.isChecked = e == null
        b.swAutoIp.setOnCheckedChangeListener { _, _ -> refrescarIp() }
        refrescarIp()

        // Limpia errores al escribir
        listOf(b.etNombre to b.tilNombre, b.etIp to b.tilIp, b.etMac to b.tilMac,
            b.etOficina to b.tilOficina, b.etCorreo to b.tilCorreo).forEach { (et, til) ->
            et.doAfterTextChanged { til.error = null }
        }

        b.btnGuardar.setOnClickListener { guardar() }
    }

    private val ipAutomatica get() = b.swAutoIp.isChecked

    /** Muestra la IP que se va a asignar (o habilita la edición manual). */
    private fun refrescarIp() {
        val e = editando
        b.tilIp.error = null
        when {
            ipAutomatica -> {
                val (ini, fin) = db.rangoIp()
                val libre = db.siguienteIpLibre()
                b.etIp.isEnabled = false
                b.etIp.setText(libre ?: "")
                b.tilIp.hint = "IP que se asignará"
                b.tilIp.helperText = if (libre != null) "Automática · rango $ini – $fin · ${db.ipsLibres()} libres" else null
                if (libre == null) b.tilIp.error = "No quedan IPs libres en el rango $ini – $fin. " +
                    if (yo.esAdmin) "Ampliá el rango desde el menú." else "Pedile al administrador que lo amplíe."
            }
            e != null && !yo.esAdmin -> {
                b.etIp.isEnabled = false
                b.etIp.setText(e.direccionIp)
                b.tilIp.hint = "IP asignada"
                b.tilIp.helperText = "Sólo el administrador puede cambiar la IP"
            }
            else -> {
                b.etIp.isEnabled = true
                if (e != null) b.etIp.setText(e.direccionIp) else b.etIp.setText("")
                b.tilIp.hint = "Dirección IP (manual)"
                b.tilIp.helperText = "Ej: 192.168.1.10"
            }
        }
    }

    private fun guardar() {
        val nombre = b.etNombre.text.toString().trim()
        val macRaw = b.etMac.text.toString()
        val oficina = b.etOficina.text.toString().trim()
        val correo = b.etCorreo.text.toString().trim()
        val tipo = b.etTipo.text.toString().ifBlank { Dispositivo.TIPOS[0] }

        // La IP automática se recalcula al guardar, por si cambió algo mientras se completaba el formulario.
        val ip = if (ipAutomatica) db.siguienteIpLibre() else Validators.normalizarIp(b.etIp.text.toString())

        var ok = true
        if (nombre.isEmpty()) { b.tilNombre.error = "Obligatorio"; ok = false }
        if (ip == null) {
            if (ipAutomatica) refrescarIp() else b.tilIp.error = "IP inválida (formato 0-255.0-255.0-255.0-255)"
            ok = false
        }
        val mac = Validators.normalizarMac(macRaw)
        if (mac == null) { b.tilMac.error = "MAC inválida (12 dígitos hexadecimales)"; ok = false }
        if (oficina.isEmpty()) { b.tilOficina.error = "Obligatorio"; ok = false }
        if (!Validators.correoValido(correo)) { b.tilCorreo.error = "Correo inválido"; ok = false }
        if (!ok) return

        val excluir = editando?.id ?: 0
        // Control de duplicados: ni la IP ni la MAC pueden repetirse.
        db.duenoDeIp(ip!!, excluir)?.let {
            b.tilIp.error = "IP ya registrada"
            aviso("IP repetida", "La IP $ip ya está asignada al equipo \"${it.nombreEquipo}\" (${it.oficina}).")
            return
        }
        db.duenoDeMac(mac!!, excluir)?.let {
            b.tilMac.error = "MAC ya registrada"
            aviso("MAC repetida", "La MAC $mac ya está asignada al equipo \"${it.nombreEquipo}\" (${it.oficina}).")
            return
        }

        val d = Dispositivo(
            id = excluir, nombreEquipo = nombre, direccionIp = ip, direccionMac = mac,
            oficina = oficina, correoInterno = correo, tecnicoId = editando?.tecnicoId ?: yo.id, tipo = tipo,
        )
        try {
            if (editando == null) db.insertarDispositivo(d) else db.actualizarDispositivo(d)
        } catch (ex: SQLiteConstraintException) {
            aviso("Duplicado", "La IP o la MAC ya están registradas en otro equipo.")
            return
        }

        if (editando != null && editando?.direccionIp == ip) { finish(); return }
        mostrarIpAsignada(d)
    }

    /** Informa la IP asignada antes de cerrar el formulario. */
    private fun mostrarIpAsignada(d: Dispositivo) {
        val titulo = if (editando == null) "PC registrada" else "IP actualizada"
        val estado = if (editando == null) "\n\nQueda en ROJO (pendiente) hasta que el administrador la active." else ""
        dialogo().setTitle(titulo)
            .setMessage("Equipo: ${d.nombreEquipo}\n\nIP asignada:  ${d.direccionIp}\nMAC:  ${d.direccionMac}$estado")
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
