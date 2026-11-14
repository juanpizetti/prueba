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

    companion object { private const val TODAS_LAS_REDES = 100_000 }

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
        b.chipsRed.setOnCheckedStateChangeListener { _, _ -> cargar() }
        b.etSearch.doAfterTextChanged { cargar() }
        b.fab.setOnClickListener { startActivity(Intent(this, DeviceFormActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        if (::b.isInitialized) {
            // Por si el admin cambió el rol o borró al usuario.
            yo = usuarioActual() ?: return
            invalidateOptionsMenu()
            armarChipsRed()
            cargar()
        }
    }

    /** Filtro por red: "Todas" + una opción por cada red configurada. */
    private fun armarChipsRed() {
        val seleccion = b.chipsRed.checkedChipId
        b.chipsRed.removeAllViews()
        fun chip(id: Int, texto: String) = com.google.android.material.chip.Chip(
            this, null, com.google.android.material.R.attr.chipStyle
        ).apply {
            this.id = id; text = texto; isCheckable = true
            setChipBackgroundColorResource(R.color.bg)
            chipStrokeWidth = 2f; setChipStrokeColorResource(R.color.sky)
        }
        b.chipsRed.addView(chip(TODAS_LAS_REDES, "Todas las redes"))
        for (r in db.redes()) b.chipsRed.addView(chip(r.id.toInt(), r.nombre))
        b.chipsRed.check(if (b.chipsRed.findViewById<View>(seleccion) != null) seleccion else TODAS_LAS_REDES)
    }

    private fun filtroRed(): Long? = b.chipsRed.checkedChipId.takeIf { it > 0 && it != TODAS_LAS_REDES }?.toLong()

    private fun filtroTecnico(): Long? = if (yo.esAdmin) null else yo.id

    private fun cargar() {
        val estado = when (b.chips.checkedChipId) {
            R.id.chipPend -> Dispositivo.ESTADO_PENDIENTE
            R.id.chipAct -> Dispositivo.ESTADO_ACTIVO
            else -> null
        }
        val lista = db.dispositivos(estado, b.etSearch.text.toString(), filtroTecnico(), filtroRed())
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
            Red: ${d.redNombre ?: "Sin red"}
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
        menu.findItem(R.id.action_redes).isVisible = yo.esAdmin
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_users -> startActivity(Intent(this, UsersActivity::class.java))
            R.id.action_export -> exportarCsv()
            R.id.action_redes -> gestionarRedes()
            R.id.action_about -> startActivity(Intent(this, AboutActivity::class.java))
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

    // ------------------------------------------------------------------ Redes

    private fun gestionarRedes() {
        val redes = db.redes()
        val items = redes.map { r ->
            "${r.nombre}\n${r.cidr} · GW ${r.gateway}\nRango ${r.ipInicio} – ${r.ipFin} · ${db.ipsLibres(r)} libres"
        }.toTypedArray()
        dialogo().setTitle("Redes")
            .setItems(items) { _, i -> editarRed(redes[i]) }
            .setPositiveButton("Nueva red") { _, _ -> editarRed(null) }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun editarRed(r: Red?) {
        val pad = (24 * resources.displayMetrics.density).toInt()
        fun campo(hint: String, valor: String, numerico: Boolean = true) =
            com.google.android.material.textfield.TextInputLayout(this).apply {
                this.hint = hint
                addView(com.google.android.material.textfield.TextInputEditText(context).apply {
                    setText(valor)
                    if (numerico) {
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.")
                    }
                })
            }
        val tilNombre = campo("Nombre", r?.nombre ?: "", numerico = false)
        val tilGw = campo("Gateway (ej: 172.21.88.1)", r?.gateway ?: "")
        val tilPref = campo("Prefijo / máscara (ej: 23)", r?.prefijo?.toString() ?: "24")
        val tilIni = campo("Primera IP a asignar", r?.ipInicio ?: "")
        val tilFin = campo("Última IP a asignar", r?.ipFin ?: "")
        val info = android.widget.TextView(this).apply {
            text = if (r != null) "Red ${r.cidr} · máscara ${Validators.mascaraTexto(r.prefijo)} · broadcast ${r.broadcast}\n" +
                "Si dejás vacías la primera/última IP se usa todo el rango de la red."
            else "Si dejás vacías la primera/última IP se usa todo el rango de la red (sin el gateway)."
            setPadding(0, pad / 2, 0, 0)
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad / 3, pad, 0)
            listOf(tilNombre, tilGw, tilPref, tilIni, tilFin, info).forEach { addView(it) }
        }
        val scroll = android.widget.ScrollView(this).apply { addView(box) }
        val builder = dialogo().setTitle(if (r == null) "Nueva red" else "Editar ${r.nombre}").setView(scroll)
            .setPositiveButton("Guardar", null).setNegativeButton("Cancelar", null)
        if (r != null) builder.setNeutralButton("Eliminar", null)
        val dlg = builder.create()
        dlg.setOnShowListener {
            dlg.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                val n = db.equiposEnRed(r!!.id)
                if (n > 0) aviso("No se puede eliminar", "${r.nombre} tiene $n equipo(s) cargado(s). Movelos o eliminalos primero.")
                else { db.eliminarRed(r.id); dlg.dismiss(); armarChipsRed(); cargar() }
            }
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                listOf(tilNombre, tilGw, tilPref, tilIni, tilFin).forEach { it.error = null }
                val nombre = tilNombre.editText!!.text.toString().trim()
                val gw = Validators.normalizarIp(tilGw.editText!!.text.toString())
                val pref = tilPref.editText!!.text.toString().trim().removePrefix("/").toIntOrNull()
                when {
                    nombre.isEmpty() -> { tilNombre.error = "Obligatorio"; return@setOnClickListener }
                    gw == null -> { tilGw.error = "IP inválida"; return@setOnClickListener }
                    pref == null || pref !in 16..30 -> { tilPref.error = "Entre 16 y 30"; return@setOnClickListener }
                }
                val base = Red(r?.id ?: 0, nombre, gw!!, pref!!, gw, gw)
                val ini = tilIni.editText!!.text.toString().let { if (it.isBlank()) base.primeraHost() else Validators.normalizarIp(it) }
                val fin = tilFin.editText!!.text.toString().let { if (it.isBlank()) base.ultimaHost() else Validators.normalizarIp(it) }
                when {
                    ini == null || !base.contiene(ini) -> tilIni.error = "Debe pertenecer a ${base.cidr}"
                    fin == null || !base.contiene(fin) -> tilFin.error = "Debe pertenecer a ${base.cidr}"
                    Validators.ipALong(ini) > Validators.ipALong(fin) -> tilFin.error = "Debe ser mayor o igual a la primera"
                    gw == base.direccionRed || gw == base.broadcast -> tilGw.error = "El gateway no puede ser la dirección de red ni el broadcast"
                    else -> try {
                        val guardada = base.copy(ipInicio = ini, ipFin = fin)
                        db.guardarRed(guardada)
                        dlg.dismiss(); armarChipsRed(); cargar()
                        aviso("Red guardada", "${guardada.nombre}: ${guardada.cidr}\nMáscara ${Validators.mascaraTexto(pref)} · GW $gw\n" +
                            "Asigna de $ini a $fin\nIPs libres: ${db.ipsLibres(guardada)}")
                    } catch (e: android.database.sqlite.SQLiteConstraintException) {
                        tilNombre.error = "Ya existe una red con ese nombre"
                    }
                }
            }
        }
        dlg.show()
    }

    /** Primera dirección utilizable de la red, salteando el gateway si coincide. */
    private fun Red.primeraHost(): String {
        val p = Validators.ipALong(direccionRed) + 1
        return Validators.longAIp(if (Validators.longAIp(p) == gateway) p + 1 else p)
    }

    private fun Red.ultimaHost(): String {
        val u = Validators.ipALong(broadcast) - 1
        return Validators.longAIp(if (Validators.longAIp(u) == gateway) u - 1 else u)
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
            append("id;nombre_equipo;red;direccion_ip;direccion_mac;oficina;correo_interno;tipo;estado;cargado_por;fecha_registro;activado_por;fecha_activacion\n")
            for (d in lista) {
                append(listOf(d.id.toString(), d.nombreEquipo, d.redNombre, d.direccionIp, d.direccionMac, d.oficina, d.correoInterno, d.tipo,
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
