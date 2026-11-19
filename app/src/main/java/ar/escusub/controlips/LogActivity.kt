package ar.escusub.controlips

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ar.escusub.controlips.databinding.ActivityLogBinding
import ar.escusub.controlips.databinding.ItemLogBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Registro de actividad (sólo administradores): quién hizo qué, cuándo, desde qué IP y celular. */
class LogActivity : BaseActivity() {

    data class Entrada(
        val id: Long, val fecha: String, val usuario: String?, val nombre: String?, val accion: String,
        val detalle: String?, val ipOrigen: String?, val dispositivo: String?,
    )

    companion object {
        private const val PAGINA = 50

        val ACCIONES = mapOf(
            "LOGIN" to "Ingreso", "LOGIN_FALLIDO" to "Ingreso fallido", "LOGOUT" to "Cierre de sesión",
            "PC_ALTA" to "PC cargada", "PC_EDICION" to "PC modificada", "PC_ELIMINADA" to "PC eliminada",
            "PC_ACTIVADA" to "PC activada", "PC_DESACTIVADA" to "PC desactivada",
            "USUARIO_ALTA" to "Usuario creado", "USUARIO_PASSWORD" to "Contraseña restablecida",
            "USUARIO_ROL" to "Rol cambiado", "USUARIO_ELIMINADO" to "Usuario eliminado",
            "PASSWORD_PROPIA" to "Cambio de contraseña",
            "RED_ALTA" to "Red creada", "RED_EDICION" to "Red modificada", "RED_ELIMINADA" to "Red eliminada",
        )

        fun color(accion: String) = when {
            accion == "LOGIN_FALLIDO" || accion.endsWith("ELIMINADA") || accion.endsWith("ELIMINADO") ||
                accion == "PC_DESACTIVADA" -> R.color.estado_pendiente
            accion == "PC_ACTIVADA" || accion.endsWith("_ALTA") -> R.color.estado_activo
            accion == "LOGIN" || accion == "LOGOUT" -> R.color.sky
            else -> R.color.navy
        }
    }

    private lateinit var b: ActivityLogBinding
    private val items = mutableListOf<Entrada>()
    private val adapter = LogAdapter()
    private var carga: Job? = null
    private var hayMas = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val yo = usuarioActual() ?: return
        if (!yo.esAdmin) { finish(); return }
        b = ActivityLogBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar("Registro de actividad", "Sólo administradores", back = true)

        val lm = LinearLayoutManager(this)
        b.recycler.layoutManager = lm
        b.recycler.adapter = adapter
        b.recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                // Carga la página siguiente al llegar cerca del final.
                if (dy > 0 && hayMas && carga?.isActive != true && lm.findLastVisibleItemPosition() >= items.size - 10) cargar(reiniciar = false)
            }
        })
        b.chips.setOnCheckedStateChangeListener { _, _ -> cargar(reiniciar = true) }
        b.etSearch.doAfterTextChanged { cargar(reiniciar = true, demora = 350) }
        b.swipe.setColorSchemeResources(R.color.navy, R.color.green_logo, R.color.sky)
        b.swipe.setOnRefreshListener { cargar(reiniciar = true) }
        cargar(reiniciar = true)
    }

    private fun categoria(): String? = when (b.chips.checkedChipId) {
        R.id.chipIngresos -> "ingresos"
        R.id.chipPcs -> "pcs"
        R.id.chipUsuarios -> "usuarios"
        R.id.chipRedes -> "redes"
        else -> null
    }

    private fun cargar(reiniciar: Boolean, demora: Long = 0) {
        if (reiniciar) carga?.cancel()
        val antes = if (reiniciar) null else items.lastOrNull()?.id
        val busqueda = b.etSearch.text.toString()
        val cat = categoria()
        carga = conServidor({ b.progress.visibility = if (it) View.VISIBLE else View.GONE }) {
            if (demora > 0) delay(demora)
            val nuevos = Api.lista(rpc("log_listar", "p_busqueda" to busqueda, "p_categoria" to cat,
                "p_antes_id" to antes, "p_limite" to PAGINA)) { o: JSONObject ->
                fun JSONObject.s(k: String) = if (isNull(k)) null else getString(k)
                Entrada(o.getLong("id"), o.getString("fecha"), o.s("usuario"), o.s("nombre"), o.getString("accion"),
                    o.s("detalle"), o.s("ip_origen"), o.s("dispositivo"))
            }
            if (reiniciar) items.clear()
            items += nuevos
            hayMas = nuevos.size == PAGINA
            adapter.notifyDataSetChanged()
            b.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }.also { it.invokeOnCompletion { b.swipe.isRefreshing = false } }
    }

    inner class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {
        inner class VH(val b: ItemLogBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val e = items[position]
            val c = ContextCompat.getColor(this@LogActivity, color(e.accion))
            h.b.stripe.setBackgroundColor(c)
            h.b.tvAccion.setTextColor(c)
            h.b.tvAccion.text = ACCIONES[e.accion] ?: e.accion
            h.b.tvFecha.text = e.fecha
            h.b.tvUsuario.text = listOfNotNull(e.usuario, e.nombre?.let { "($it)" }).joinToString(" ").ifBlank { "—" }
            h.b.tvDetalle.text = e.detalle.orEmpty()
            h.b.tvDetalle.visibility = if (e.detalle.isNullOrBlank()) View.GONE else View.VISIBLE
            h.b.tvOrigen.text = "Desde " + listOfNotNull(e.ipOrigen?.let { "IP $it" }, e.dispositivo)
                .joinToString(" · ").ifBlank { "origen desconocido" }
        }
    }
}
