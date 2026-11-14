package ar.escusub.controlips

import android.content.Context

object Session {
    private const val PREFS = "session"
    private const val KEY_ID = "user_id"

    fun guardar(ctx: Context, id: Long) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_ID, id).apply()

    fun idActual(ctx: Context): Long =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_ID, -1)

    fun cerrar(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}
