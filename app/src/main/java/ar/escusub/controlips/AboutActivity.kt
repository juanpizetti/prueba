package ar.escusub.controlips

import android.os.Bundle
import android.view.View
import ar.escusub.controlips.databinding.ActivityAboutBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Calendar

/** Pantalla "Acerca de": app diseñada por ESCUSUB – Sector TICOM. */
class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupToolbar("Acerca de", "ESCUSUB · Sector TICOM", back = true)

        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        b.tvVersion.text = "Versión $version"
        b.tvCopyright.text = "© ${Calendar.getInstance().get(Calendar.YEAR)} ESCUSUB · Sector TICOM\nUso interno"

        // Los datos de las redes sólo se muestran con una sesión iniciada.
        if (Session.token(this) != null) {
            lifecycleScope.launch {
                val redes = runCatching {
                    Api.lista(Api.rpc(this@AboutActivity, "redes_listar", mapOf("p_token" to Session.token(this@AboutActivity))), Api::red)
                }.getOrNull() ?: return@launch
                b.cardRedes.visibility = View.VISIBLE
                b.tvRedes.text = redes.joinToString("\n\n") { r ->
                    "${r.nombre}\n  ${r.cidr}  ·  GW ${r.gateway}\n  Asigna ${r.ipInicio} – ${r.ipFin}\n  ${r.libres} IPs libres"
                }
            }
        }
        b.tvServidor.text = if (Api.configurado(this)) "Base de datos en línea: ${Api.url(this)}" else "Servidor sin configurar"
    }
}
