package ar.escusub.controlips

import android.content.Intent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Barra superior con el logo (en todas las pantallas) y llamadas al servidor. */
abstract class BaseActivity : AppCompatActivity() {

    protected fun setupToolbar(title: String, subtitle: String? = null, back: Boolean = false) {
        val tb = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(tb)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(back)
        if (back) tb.setNavigationOnClickListener { finish() }
        findViewById<TextView>(R.id.toolbarTitle).text = title
        findViewById<TextView>(R.id.toolbarSubtitle).text = subtitle ?: getString(R.string.org_name)
    }

    /** Token de la sesión o null (y vuelve al login). */
    protected fun token(): String? = Session.token(this) ?: run { irAlLogin(); null }

    /** Usuario guardado en la sesión o null (y vuelve al login). */
    protected fun usuarioActual(): Tecnico? = Session.usuario(this)?.takeIf { Session.token(this) != null }
        ?: run { irAlLogin(); null }

    protected fun irAlLogin() {
        Session.cerrar(this)
        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    /** Llama a una función del servidor con el token de la sesión. */
    protected suspend fun rpc(funcion: String, vararg params: Pair<String, Any?>): Any? =
        Api.rpc(this, funcion, mapOf("p_token" to Session.token(this)) + params)

    /** Ejecuta trabajo contra el servidor mostrando los errores; si la sesión venció, vuelve al login. */
    protected fun conServidor(cargando: ((Boolean) -> Unit)? = null, bloque: suspend () -> Unit): Job =
        lifecycleScope.launch {
            cargando?.invoke(true)
            try {
                bloque()
            } catch (e: ApiException) {
                if (e.sesionVencida) {
                    aviso("Sesión vencida", e.message ?: "") { irAlLogin() }
                } else {
                    aviso(if (e.status == 0) "Sin conexión" else "No se pudo completar", e.message ?: "Error")
                }
            } finally {
                cargando?.invoke(false)
            }
        }

    /** Diálogo con el logo institucional. */
    protected fun dialogo() = MaterialAlertDialogBuilder(this).setIcon(R.drawable.logo)

    protected fun aviso(titulo: String, mensaje: String, alCerrar: (() -> Unit)? = null) {
        if (isFinishing || isDestroyed) return
        dialogo().setTitle(titulo).setMessage(mensaje).setPositiveButton("Aceptar", null)
            .setOnDismissListener { alCerrar?.invoke() }.show()
    }
}
