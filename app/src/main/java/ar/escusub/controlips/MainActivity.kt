package ar.escusub.controlips

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ar.escusub.controlips.databinding.ActivityMainBinding
import ar.escusub.controlips.databinding.DialogPasswordBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : BaseActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var yo: Tecnico
    private lateinit var adapter: DeviceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        yo = usuarioActual() ?: return
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar(
            if (yo.esAdmin) "Equipos registrados" else "Mis equipos",
            "${yo.nombreCompleto} · ${if (yo.esAdmin) "Administrador" else "Técnico"}"
        )

        adapter = DeviceAdapter(yo.esAdmin, ::opciones, ::toggle)
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.chips.setOnCheckedStateChangeListener { _, _ -> cargar() }
        b.etSearch.doAfterTextChanged { cargar() }
        b.fab.setOnClickListener { startActivity(Intent(this, DeviceFormActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        if (::b.isInitialized) {
            // Por si el admin cambió el rol o borró al usuario.
            yo = usuarioActual() ?: return
            invalidateOptionsMenu()
            cargar()
        }
    }

    private fun filtroTecnico(): Long? = if (yo.esAdmin) null else yo.id

    private fun cargar() {
        val estado = when (b.chips.checkedChipId) {
            R.id.chipPend -> Dispositivo.ESTADO_PENDIENTE
            R.id.chipAct -> Dispositivo.ESTADO_ACTIVO
            else -> null
        }
        val lista = db.dispositivos(estado, b.etSearch.text.toString(), filtroTecnico())
        adapter.items = lista
        b.tvEmpty.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        val rojos = db.contar(Dispositivo.ESTADO_PENDIENTE, filtroTecnico())
        val verdes = db.contar(Dispositivo.ESTADO_ACTIVO, filtroTecnico())
        b.tvCountRed.text = "● Pendientes: $rojos"
        b.tvCountGreen.text = "● Activos: $verdes"
    }

    private fun toggle(d: Dispositivo) {
        if (!yo.esAdmin) return
        if (d.activo) {
            dialogo().setTitle("Desactivar equipo")
                .setMessage("¿Volver a poner ${d.nombreEquipo} en ROJO (pendiente)?")
                .setPositiveButton("Desactivar") { _, _ -> db.cambiarEstado(d.id, false, yo.id); cargar() }
                .setNegativeButton("Cancelar", null).show()
        } else {
            db.cambiarEstado(d.id, true, yo.id)
            Toast.makeText(this, "${d.nombreEquipo} activado ✔", Toast.LENGTH_SHORT).show()
            cargar()
        }
    }

    private fun opciones(d: Dispositivo) {
        val detalle = """
            IP: ${d.direccionIp}
            MAC: ${d.direccionMac}
            Oficina: ${d.oficina}
            Correo: ${d.correoInterno}
            Tipo: ${d.tipo}
            Estado: ${if (d.activo) "ACTIVO (verde)" else "PENDIENTE (rojo)"}
            Cargado por: ${d.tecnicoNombre ?: "—"} el ${d.fechaRegistro ?: "—"}
        """.trimIndent() + (if (d.activo) "\nActivado por: ${d.activadoPorNombre ?: "—"} el ${d.fechaActivacion ?: "—"}" else "")

        val puedeEditar = yo.esAdmin || (!d.activo && d.tecnicoId == yo.id)
        val dlg = dialogo().setTitle(d.nombreEquipo).setMessage(detalle)
        if (puedeEditar) {
            dlg.setPositiveButton("Editar") { _, _ ->
                startActivity(Intent(this, DeviceFormActivity::class.java).putExtra(DeviceFormActivity.EXTRA_ID, d.id))
            }
            dlg.setNegativeButton("Eliminar") { _, _ -> confirmarEliminar(d) }
            dlg.setNeutralButton("Cerrar", null)
        } else {
            if (!yo.esAdmin) dlg.setMessage("$detalle\n\nEl equipo ya fue activado: sólo el administrador puede modificarlo.")
            dlg.setPositiveButton("Cerrar", null)
        }
        dlg.show()
    }

    private fun confirmarEliminar(d: Dispositivo) {
        dialogo().setTitle("Eliminar equipo")
            .setMessage("¿Eliminar ${d.nombreEquipo} (${d.direccionIp} / ${d.direccionMac})? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ -> db.eliminarDispositivo(d.id); cargar() }
            .setNegativeButton("Cancelar", null).show()
    }

    // ------------------------------------------------------------------ Menú

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_users).isVisible = yo.esAdmin
        menu.findItem(R.id.action_export).isVisible = yo.esAdmin
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_users -> startActivity(Intent(this, UsersActivity::class.java))
            R.id.action_export -> exportarCsv()
            R.id.action_password -> cambiarPassword()
            R.id.action_logout -> {
                Session.cerrar(this)
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun cambiarPassword() {
        val v = DialogPasswordBinding.inflate(layoutInflater)
        dialogo().setTitle("Cambiar mi contraseña").setView(v.root)
            .setPositiveButton("Guardar") { _, _ ->
                val actual = v.etActual.text.toString()
                val nueva = v.etNueva.text.toString()
                if (nueva.length < 4) { aviso("Error", "La nueva contraseña debe tener al menos 4 caracteres."); return@setPositiveButton }
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.Default) {
                        val hash = db.hashDe(yo.id)
                        if (hash != null && Passwords.verificar(actual, hash)) {
                            db.cambiarPassword(yo.id, Passwords.hash(nueva)); true
                        } else false
                    }
                    if (ok) aviso("Listo", "Contraseña actualizada.") else aviso("Error", "La contraseña actual no es correcta.")
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun exportarCsv() {
        val lista = db.dispositivos(null, "", null)
        fun esc(s: String?) = "\"" + (s ?: "").replace("\"", "\"\"") + "\""
        val csv = buildString {
            append("﻿") // BOM para que Excel respete los acentos
            append("id;nombre_equipo;direccion_ip;direccion_mac;oficina;correo_interno;tipo;estado;cargado_por;fecha_registro;activado_por;fecha_activacion\n")
            for (d in lista) {
                append(listOf(d.id.toString(), d.nombreEquipo, d.direccionIp, d.direccionMac, d.oficina, d.correoInterno, d.tipo,
                    d.estado, d.tecnicoNombre, d.fechaRegistro, d.activadoPorNombre, d.fechaActivacion).joinToString(";") { esc(it) })
                append("\n")
            }
        }
        val dir = File(cacheDir, "exports").apply { mkdirs() }
        val f = File(dir, "equipos_control_ips.csv")
        f.writeText(csv)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "Exportar equipos"
        ))
    }
}
