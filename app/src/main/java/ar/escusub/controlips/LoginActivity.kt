package ar.escusub.controlips

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.lifecycleScope
import ar.escusub.controlips.databinding.ActivityLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : BaseActivity() {

    private lateinit var b: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Session.idActual(this) > 0 && db.tecnico(Session.idActual(this)) != null) {
            irAlInicio(); return
        }
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnLogin.setOnClickListener { ingresar() }
        b.btnAbout.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
        b.etPass.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { ingresar(); true } else false
        }
    }

    private fun ingresar() {
        val usuario = b.etUser.text.toString().trim()
        val pass = b.etPass.text.toString()
        if (usuario.isEmpty() || pass.isEmpty()) {
            aviso("Datos incompletos", "Ingresá usuario y contraseña."); return
        }
        cargando(true)
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.Default) {
                db.buscarUsuario(usuario)?.takeIf { (_, hash) -> Passwords.verificar(pass, hash) }?.first
            }
            cargando(false)
            if (ok == null) {
                aviso("Acceso denegado", "Usuario o contraseña incorrectos.")
            } else {
                Session.guardar(this@LoginActivity, ok.id)
                irAlInicio()
            }
        }
    }

    private fun cargando(si: Boolean) {
        b.btnLogin.isEnabled = !si
        b.progress.visibility = if (si) View.VISIBLE else View.GONE
    }

    private fun irAlInicio() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
