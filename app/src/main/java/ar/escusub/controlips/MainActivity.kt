package ar.escusub.controlips

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import ar.escusub.controlips.databinding.ActivityMainBinding
import ar.escusub.controlips.databinding.DialogPasswordBinding
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class MainActivity : BaseActivity() {

    companion object { private const val TODAS_LAS_REDES = 100_000 }

    private lateinit var b: ActivityMainBinding
    private lateinit var yo: Tecnico
    private lateinit var adapter: DeviceAdapter
    private var redes: List<Red> = emptyList()
    private var carga: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        yo = usuarioActual() ?: return
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        mostrarTitulo()

        adapter = DeviceAdapter(yo.esAdmin, ::opciones, ::toggle)
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.chips.setOnCheckedStateChangeListener { _, _ -> cargar() }
        b.chipsRed.setOnCheckedStateChangeListener { _, _ -> cargar() }
        b.etSearch.doAfterTextChanged { cargar(demora = 350) }
        b.swipe.setOnRefreshListener { refrescarTodo() }
        b.swipe.setColorSchemeResources(R.color.navy, R.color.green_logo, R.color.sky)
        b.fab.setOnClickListener { startActivity(Intent(this, DeviceFormActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        if (::b.isInitialized) refrescarTodo()
    }

    private fun mostrarTitulo() = setupToolbar(
        if (yo.esAdmin) "Equipos registrados" else "Mis equipos",
        "${yo.nombreCompleto} · ${if (yo.esAdmin) "Administrador" else "Técnico"}"
    )

    /** Revalida el usuario (por si el admin le cambió el rol), recarga redes y equipos. */
    private fun refrescarTodo() {
        conServidor {
            val u = Api.tecnico(rpc("yo") as JSONObject)
            if (u.rol != yo.rol || u.nombreCompleto != yo.nombreCompleto) {
                Session.actualizarUsuario(this, u)
                recreate(); return@conServidor
            }
            redes = Api.lista(rpc("redes_listar"), Api::red)
            armarChipsRed()
            cargar()
        }.invokeOnCompletion { b.swipe.isRefreshing = false }
    }

    /** Filtro por red: "Todas" + una opción por cada red configurada. */
    private fun armarChipsRed() {
        val seleccion = b.chipsRed.checkedChipId
        b.chipsRed.setOnCheckedStateChangeListener(null)
        b.chipsRed.removeAllViews()
        fun chip(id: Int, texto: String) = Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
            this.id = id; text = texto; isCheckable = true
            setChipBackgroundColorResource(R.color.bg)
            chipStrokeWidth = 2f; setChipStrokeColorResource(R.color.sky)
        }
        b.chipsRed.addView(chip(TODAS_LAS_REDES, "Todas las redes"))
        for (r in redes) b.chipsRed.addView(chip(r.id.toInt(), r.nombre))
        b.chipsRed.check(if (b.chipsRed.findViewById<View>(seleccion) != null) seleccion else TODAS_LAS_REDES)
        b.chipsRed.setOnCheckedStateChangeListener { _, _ -> cargar() }
    }

    private fun filtroRed(): Long? = b.chipsRed.checkedChipId.takeIf { it > 0 && it != TODAS_LAS_REDES }?.toLong()

    private fun cargar(demora: Long = 0) {
        val estado = when (b.chips.checkedChipId) {
            R.id.chipPend -> Dispositivo.ESTADO_PENDIENTE
            R.id.chipAct -> Dispositivo.ESTADO_ACTIVO
            else -> null
        }
        val busqueda = b.etSearch.text.toString()
        val red = filtroRed()
        carga?.cancel()
        carga = conServidor({ b.progress.visibility = if (it) View.VISIBLE else View.GONE }) {
            if (demora > 0) delay(demora)
            val r = rpc("dispositivos_listar", "p_estado" to estado, "p_busqueda" to busqueda, "p_red_id" to red) as JSONObject
            val lista = Api.lista(r.getJSONArray("items"), Api::dispositivo)
            adapter.items = lista
            b.tvEmpty.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
            b.tvCountRed.text = "● Pendientes: ${r.getInt("pendientes")}"
            b.tvCountGreen.text = "● Activos: ${r.getInt("activos")}"
        }
    }

    private fun toggle(d: Dispositivo) {
        if (!yo.esAdmin) return
        fun cambiar(activo: Boolean) = conServidor {
            rpc("dispositivo_estado", "p_id" to d.id, "p_activo" to activo)
            if (activo) Toast.makeText(this, "${d.nombreEquipo} activado ✔", Toast.LENGTH_SHORT).show()
            cargar()
        }
        if (d.activo) {
            dialogo().setTitle("Desactivar equipo")
                .setMessage("¿Volver a poner ${d.nombreEquipo} en ROJO (pendiente)?")
                .setPositiveButton("Desactivar") { _, _ -> cambiar(false) }
                .setNegativeButton("Cancelar", null).show()
        } else cambiar(true)
    }

    private fun opciones(d: Dispositivo) {
        val detalle = """
            IP: ${d.direccionIp}
            MAC: ${d.direccionMac}
            Red: ${d.redNombre?.let { "$it (${d.redCidr})" } ?: "Sin red"}
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
                startActivity(Intent(this, DeviceFormActivity::class.java).putExtra(DeviceFormActivity.EXTRA_DISPOSITIVO, d.toJson()))
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
            .setMessage("¿Eliminar ${d.nombreEquipo} (${d.direccionIp} / ${d.direccionMac})? La IP vuelve a quedar libre.")
            .setPositiveButton("Eliminar") { _, _ -> conServidor { rpc("dispositivo_eliminar", "p_id" to d.id); refrescarTodo() } }
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
                val t = Session.token(this)
                // Cierra la sesión en el servidor sin esperar la respuesta.
                if (t != null) cerrarSesionEnServidor(t)
                irAlLogin()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private fun cerrarSesionEnServidor(token: String) {
        val ctx = applicationContext
        kotlinx.coroutines.GlobalScope.launch {
            runCatching { Api.rpc(ctx, "logout", mapOf("p_token" to token)) }
        }
    }

    // ------------------------------------------------------------------ Redes

    private fun gestionarRedes() {
        conServidor {
            redes = Api.lista(rpc("redes_listar"), Api::red)
            val items = redes.map { r ->
                "${r.nombre}\n${r.cidr} · GW ${r.gateway}\nAsigna ${r.ipInicio} – ${r.ipFin} · ${r.libres} libres · ${r.equipos} equipos"
            }.toTypedArray()
            dialogo().setTitle("Redes")
                .setItems(items) { _, i -> editarRed(redes[i]) }
                .setPositiveButton("Nueva red") { _, _ -> editarRed(null) }
                .setNegativeButton("Cerrar", null)
                .show()
        }
    }

    private fun editarRed(r: Red?) {
        val pad = (24 * resources.displayMetrics.density).toInt()
        fun campo(hint: String, valor: String, numerico: Boolean = true) = TextInputLayout(this).apply {
            this.hint = hint
            addView(TextInputEditText(context).apply {
                setText(valor)
                if (numerico) {
                    inputType = InputType.TYPE_CLASS_NUMBER
                    keyListener = DigitsKeyListener.getInstance("0123456789.")
                }
            })
        }
        val tilNombre = campo("Nombre", r?.nombre ?: "", numerico = false)
        val tilGw = campo("Gateway (ej: 172.21.88.1)", r?.gateway ?: "")
        val tilPref = campo("Prefijo / máscara (ej: 23)", r?.prefijo?.toString() ?: "24")
        val tilIni = campo("Primera IP a asignar", r?.ipInicio ?: "")
        val tilFin = campo("Última IP a asignar", r?.ipFin ?: "")
        val info = TextView(this).apply {
            text = (if (r != null) "Red ${r.cidr} · máscara ${r.mascara} · broadcast ${r.broadcast}\n" else "") +
                "Si dejás vacías la primera/última IP se usa todo el rango de la red."
            setPadding(0, pad / 2, 0, 0)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 3, pad, 0)
            listOf(tilNombre, tilGw, tilPref, tilIni, tilFin, info).forEach { addView(it) }
        }
        val builder = dialogo().setTitle(if (r == null) "Nueva red" else "Editar ${r.nombre}")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Guardar", null).setNegativeButton("Cancelar", null)
        if (r != null) builder.setNeutralButton("Eliminar", null)
        val dlg = builder.create()
        dlg.setOnShowListener {
            dlg.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                conServidor { rpc("red_eliminar", "p_id" to r!!.id); dlg.dismiss(); refrescarTodo() }
            }
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pref = tilPref.editText!!.text.toString().trim().removePrefix("/").toIntOrNull()
                if (pref == null) { tilPref.error = "Número entre 16 y 30"; return@setOnClickListener }
                conServidor {
                    val g = Api.red(rpc("red_guardar",
                        "p_id" to r?.id,
                        "p_nombre" to tilNombre.editText!!.text.toString(),
                        "p_gateway" to tilGw.editText!!.text.toString(),
                        "p_prefijo" to pref,
                        "p_ip_inicio" to tilIni.editText!!.text.toString(),
                        "p_ip_fin" to tilFin.editText!!.text.toString()) as JSONObject)
                    dlg.dismiss(); refrescarTodo()
                    aviso("Red guardada", "${g.nombre}: ${g.cidr}\nMáscara ${g.mascara} · GW ${g.gateway}\n" +
                        "Asigna de ${g.ipInicio} a ${g.ipFin}\nIPs libres: ${g.libres}")
                }
            }
        }
        dlg.show()
    }

    // ------------------------------------------------------------------ Otros

    private fun cambiarPassword() {
        val v = DialogPasswordBinding.inflate(layoutInflater)
        dialogo().setTitle("Cambiar mi contraseña").setView(v.root)
            .setPositiveButton("Guardar") { _, _ ->
                conServidor {
                    rpc("cambiar_mi_password", "p_actual" to v.etActual.text.toString(), "p_nueva" to v.etNueva.text.toString())
                    aviso("Listo", "Contraseña actualizada.")
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun exportarCsv() {
        conServidor {
            val r = rpc("dispositivos_listar", "p_estado" to null, "p_busqueda" to "", "p_red_id" to null) as JSONObject
            val lista = Api.lista(r.getJSONArray("items"), Api::dispositivo)
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
            val f = File(File(cacheDir, "exports").apply { mkdirs() }, "equipos_control_ips.csv")
            f.writeText(csv)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Exportar equipos"
            ))
        }
    }
}
