package ar.escusub.controlips

import android.os.Bundle
import android.view.View
import ar.escusub.controlips.databinding.ActivityAboutBinding
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
        if (Session.idActual(this) > 0 && db.tecnico(Session.idActual(this)) != null) {
            b.cardRedes.visibility = View.VISIBLE
            b.tvRedes.text = db.redes().joinToString("\n\n") { r ->
                "${r.nombre}\n  ${r.cidr}  ·  GW ${r.gateway}\n  Asigna ${r.ipInicio} – ${r.ipFin}"
            }
        }
    }
}
