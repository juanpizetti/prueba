package ar.escusub.controlips

import android.content.Context
import org.json.JSONObject

/** Token de sesión del servidor y datos del usuario logueado. */
object Session {
    private const val PREFS = "session"

    fun guardar(ctx: Context, token: String, usuario: Tecnico) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("token", token).putString("usuario", usuario.toJson()).apply()

    fun actualizarUsuario(ctx: Context, usuario: Tecnico) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("usuario", usuario.toJson()).apply()

    fun token(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("token", null)

    fun usuario(ctx: Context): Tecnico? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("usuario", null)
            ?.let { runCatching { Api.tecnico(JSONObject(it)) }.getOrNull() }

    fun cerrar(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}
