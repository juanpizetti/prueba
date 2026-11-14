package ar.escusub.controlips

import android.content.Intent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Barra superior con el logo, presente en todas las pantallas. */
abstract class BaseActivity : AppCompatActivity() {

    protected val db by lazy { DbHelper.get(this) }

    protected fun setupToolbar(title: String, subtitle: String? = null, back: Boolean = false) {
        val tb = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(tb)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(back)
        if (back) tb.setNavigationOnClickListener { finish() }
        findViewById<TextView>(R.id.toolbarTitle).text = title
        findViewById<TextView>(R.id.toolbarSubtitle).text = subtitle ?: getString(R.string.org_name)
    }

    /** Devuelve el usuario logueado o redirige al login. */
    protected fun usuarioActual(): Tecnico? {
        val u = Session.idActual(this).takeIf { it > 0 }?.let { db.tecnico(it) }
        if (u == null) {
            Session.cerrar(this)
            startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
        return u
    }

    /** Diálogo con el logo institucional. */
    protected fun dialogo() = MaterialAlertDialogBuilder(this).setIcon(R.drawable.logo)

    protected fun aviso(titulo: String, mensaje: String) {
        dialogo().setTitle(titulo).setMessage(mensaje).setPositiveButton("Aceptar", null).show()
    }
}
