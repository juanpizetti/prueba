package ar.escusub.controlips

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import ar.escusub.controlips.databinding.ActivityLoginBinding
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONObject

class LoginActivity : BaseActivity() {

    private lateinit var b: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Session.token(this) != null && Session.usuario(this) != null) {
            irAlInicio(); return
        }
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnLogin.setOnClickListener { ingresar() }
        b.etPass.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { ingresar(); true } else false
        }
        b.btnAbout.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
        b.btnServidor.setOnClickListener { configurarServidor() }
        mostrarServidor()
        if (!Api.configurado(this)) configurarServidor()
    }

    private fun mostrarServidor() {
        b.tvServidor.text = if (Api.configurado(this))
            "Servidor: " + Api.url(this).removePrefix("https://").removePrefix("http://")
        else "Servidor sin configurar"
    }

    private fun ingresar() {
        val usuario = b.etUser.text.toString().trim()
        val pass = b.etPass.text.toString()
        if (usuario.isEmpty() || pass.isEmpty()) {
            aviso("Datos incompletos", "Ingresá usuario y contraseña."); return
        }
        conServidor(::cargando) {
            val r = Api.rpc(this, "login", mapOf("p_usuario" to usuario, "p_password" to pass)) as JSONObject
            Session.guardar(this, r.getString("token"), Api.tecnico(r.getJSONObject("usuario")))
            irAlInicio()
        }
    }

    /** URL del proyecto Supabase y clave pública (anon / publishable). */
    private fun configurarServidor() {
        val pad = (24 * resources.displayMetrics.density).toInt()
        fun campo(hint: String, valor: String) = TextInputLayout(this).apply {
            this.hint = hint
            addView(TextInputEditText(context).apply {
                setText(valor)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            })
        }
        val tilUrl = campo("URL del proyecto (https://xxxx.supabase.co)", Api.url(this))
        val tilKey = campo("Clave pública (anon / publishable)", Api.clave(this))
        val info = TextView(this).apply {
            text = "Se obtienen en Supabase → Project Settings → API. Pedíselos al administrador."
            setPadding(0, pad / 2, 0, 0)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 3, pad, 0)
            addView(tilUrl); addView(tilKey); addView(info)
        }
        val dlg = dialogo().setTitle("Servidor de la base de datos").setView(box)
            .setPositiveButton("Guardar", null).setNegativeButton("Cancelar", null).create()
        dlg.setOnShowListener {
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = tilUrl.editText!!.text.toString().trim()
                val key = tilKey.editText!!.text.toString().trim()
                when {
                    !url.startsWith("https://") && !url.startsWith("http://") -> tilUrl.error = "Debe empezar con https://"
                    key.length < 20 -> tilKey.error = "Clave inválida"
                    else -> { Api.guardarConfig(this, url, key); mostrarServidor(); dlg.dismiss() }
                }
            }
        }
        dlg.show()
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
