package ar.escusub.controlips

import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
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

        // Limpia errores al escribir
        listOf(b.etNombre to b.tilNombre, b.etIp to b.tilIp, b.etMac to b.tilMac,
            b.etOficina to b.tilOficina, b.etCorreo to b.tilCorreo).forEach { (et, til) ->
            et.doAfterTextChanged { til.error = null }
        }

        b.btnGuardar.setOnClickListener { guardar() }
    }

    private fun guardar() {
        val nombre = b.etNombre.text.toString().trim()
        val ipRaw = b.etIp.text.toString()
        val macRaw = b.etMac.text.toString()
        val oficina = b.etOficina.text.toString().trim()
        val correo = b.etCorreo.text.toString().trim()
        val tipo = b.etTipo.text.toString().ifBlank { Dispositivo.TIPOS[0] }

        var ok = true
        if (nombre.isEmpty()) { b.tilNombre.error = "Obligatorio"; ok = false }
        val ip = Validators.normalizarIp(ipRaw)
        if (ip == null) { b.tilIp.error = "IP inválida (formato 0-255.0-255.0-255.0-255)"; ok = false }
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
        finish()
    }
}
