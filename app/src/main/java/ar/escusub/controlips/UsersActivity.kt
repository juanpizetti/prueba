package ar.escusub.controlips

import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ar.escusub.controlips.databinding.ActivityUsersBinding
import ar.escusub.controlips.databinding.DialogUserBinding
import ar.escusub.controlips.databinding.ItemUserBinding
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsersActivity : BaseActivity() {

    private lateinit var b: ActivityUsersBinding
    private lateinit var yo: Tecnico
    private val adapter = UserAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        yo = usuarioActual() ?: return
        if (!yo.esAdmin) { finish(); return }
        b = ActivityUsersBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar("Usuarios", "Sólo administradores", back = true)

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter
        b.fab.setOnClickListener { nuevoUsuario() }
        cargar()
    }

    private fun cargar() { adapter.items = db.tecnicos() }

    private fun nuevoUsuario() {
        val v = DialogUserBinding.inflate(layoutInflater)
        val dlg = dialogo().setTitle("Nuevo usuario").setView(v.root)
            .setPositiveButton("Crear", null).setNegativeButton("Cancelar", null).create()
        dlg.setOnShowListener {
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val nombre = v.etNombre.text.toString().trim()
                val usuario = v.etUsuario.text.toString().trim()
                val pass = v.etPass.text.toString()
                val rol = if (v.rbAdmin.isChecked) Tecnico.ROL_ADMIN else Tecnico.ROL_TECNICO
                when {
                    nombre.isEmpty() -> v.etNombre.error = "Obligatorio"
                    usuario.isEmpty() || usuario.contains(' ') -> v.etUsuario.error = "Obligatorio, sin espacios"
                    db.existeUsuario(usuario) -> v.etUsuario.error = "Ese usuario ya existe"
                    pass.length < 4 -> v.etPass.error = "Mínimo 4 caracteres"
                    else -> lifecycleScope.launch {
                        val res = withContext(Dispatchers.Default) {
                            try { db.crearTecnico(usuario, Passwords.hash(pass), nombre, rol); null }
                            catch (e: SQLiteConstraintException) { "Ese usuario ya existe" }
                        }
                        if (res != null) v.etUsuario.error = res
                        else { dlg.dismiss(); cargar(); Toast.makeText(this@UsersActivity, "Usuario creado", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
        }
        dlg.show()
    }

    private fun opciones(t: Tecnico) {
        val acciones = mutableListOf("Restablecer contraseña")
        acciones += if (t.esAdmin) "Cambiar a Técnico" else "Cambiar a Administrador"
        acciones += "Eliminar usuario"
        dialogo().setTitle("${t.nombreCompleto} (${t.usuario})")
            .setItems(acciones.toTypedArray()) { _, which ->
                when (which) {
                    0 -> resetPassword(t)
                    1 -> cambiarRol(t)
                    2 -> eliminar(t)
                }
            }.show()
    }

    private fun resetPassword(t: Tecnico) {
        val et = TextInputEditText(this).apply {
            hint = "Nueva contraseña (mín. 4)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val pad = (24 * resources.displayMetrics.density).toInt()
        val box = android.widget.FrameLayout(this).apply { setPadding(pad, pad / 3, pad, 0); addView(et) }
        dialogo().setTitle("Contraseña de ${t.usuario}").setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                val p = et.text.toString()
                if (p.length < 4) { aviso("Error", "Mínimo 4 caracteres."); return@setPositiveButton }
                lifecycleScope.launch {
                    withContext(Dispatchers.Default) { db.cambiarPassword(t.id, Passwords.hash(p)) }
                    Toast.makeText(this@UsersActivity, "Contraseña actualizada", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun cambiarRol(t: Tecnico) {
        if (t.esAdmin && db.cantidadAdmins() <= 1) { aviso("No permitido", "Debe quedar al menos un administrador."); return }
        db.cambiarRol(t.id, if (t.esAdmin) Tecnico.ROL_TECNICO else Tecnico.ROL_ADMIN)
        if (t.id == yo.id) { finish(); return } // el propio admin dejó de serlo
        cargar()
    }

    private fun eliminar(t: Tecnico) {
        if (t.id == yo.id) { aviso("No permitido", "No podés eliminar tu propio usuario."); return }
        if (t.esAdmin && db.cantidadAdmins() <= 1) { aviso("No permitido", "Debe quedar al menos un administrador."); return }
        dialogo().setTitle("Eliminar usuario")
            .setMessage("¿Eliminar a ${t.nombreCompleto}? Los equipos que cargó se conservan.")
            .setPositiveButton("Eliminar") { _, _ -> db.eliminarTecnico(t.id); cargar() }
            .setNegativeButton("Cancelar", null).show()
    }

    inner class UserAdapter : RecyclerView.Adapter<UserAdapter.VH>() {
        var items: List<Tecnico> = emptyList()
            set(value) { field = value; notifyDataSetChanged() }

        inner class VH(val b: ItemUserBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val t = items[position]
            h.b.tvNombre.text = t.nombreCompleto + if (t.id == yo.id) "  (vos)" else ""
            h.b.tvUsuario.text = "Usuario: ${t.usuario}"
            h.b.tvRol.text = if (t.esAdmin) "ADMIN" else "TÉCNICO"
            h.b.tvRol.setBackgroundColor(ContextCompat.getColor(this@UsersActivity, if (t.esAdmin) R.color.navy else R.color.green_logo))
            h.b.root.setOnClickListener { opciones(t) }
        }
    }
}
