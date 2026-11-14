package ar.escusub.controlips

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Error devuelto por el servidor (mensaje legible) o por falta de conexión. */
class ApiException(val status: Int, message: String) : Exception(message) {
    val sesionVencida get() = status == 401
}

/**
 * Cliente de la base en línea (Supabase). Toda la lógica y la seguridad están
 * en las funciones SQL de supabase/schema.sql; acá sólo se las llama por HTTPS.
 */
object Api {
    private const val PREFS = "servidor"

    fun url(ctx: Context): String = normalizarUrl(
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("url", null) ?: BuildConfig.SUPABASE_URL)

    /** Acepta la URL del proyecto con o sin "/rest/v1/" y barras finales: https://xxxx.supabase.co */
    fun normalizarUrl(raw: String): String {
        var u = raw.trim().trimEnd('/')
        for (suf in listOf("/rest/v1", "/rest")) if (u.endsWith(suf, ignoreCase = true)) u = u.dropLast(suf.length)
        return u.trimEnd('/')
    }

    fun clave(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("clave", null) ?: BuildConfig.SUPABASE_KEY

    fun configurado(ctx: Context) = url(ctx).isNotBlank() && clave(ctx).isNotBlank()

    fun guardarConfig(ctx: Context, url: String, clave: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("url", normalizarUrl(url)).putString("clave", clave.trim()).apply()
    }

    /** Llama a una función de la API (POST /rest/v1/rpc/<funcion>) y devuelve el JSON. */
    suspend fun rpc(ctx: Context, funcion: String, params: Map<String, Any?> = emptyMap()): Any? =
        withContext(Dispatchers.IO) {
            if (!configurado(ctx)) throw ApiException(0, "Falta configurar el servidor (botón \"Servidor\" en la pantalla de ingreso).")
            val body = JSONObject().apply { params.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }.toString()
            val con = try {
                (URL("${url(ctx)}/rest/v1/rpc/$funcion").openConnection() as HttpURLConnection)
            } catch (e: Exception) {
                throw ApiException(0, "La dirección del servidor no es válida.")
            }
            try {
                con.requestMethod = "POST"
                con.connectTimeout = 15_000
                con.readTimeout = 30_000
                con.doOutput = true
                val key = clave(ctx)
                con.setRequestProperty("apikey", key)
                // Las claves "anon" viejas son JWT y van también como Bearer; las nuevas (sb_publishable_…) no.
                if (key.startsWith("eyJ")) con.setRequestProperty("Authorization", "Bearer $key")
                con.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                con.setRequestProperty("Accept", "application/json")
                con.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val code = con.responseCode
                val text = (if (code in 200..299) con.inputStream else con.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val msg = runCatching { JSONObject(text).optString("message") }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: "Error del servidor ($code)."
                    val status = if (code == 401 && !msg.startsWith("Sesión")) 400 else code
                    throw ApiException(status, traducir(msg))
                }
                if (text.isBlank()) null else JSONTokener(text).nextValue()
            } catch (e: IOException) {
                throw ApiException(0, "Sin conexión con el servidor. Revisá internet e intentá de nuevo.")
            } finally {
                con.disconnect()
            }
        }

    /** Mensajes de Supabase/PostgREST más comunes, en castellano. */
    private fun traducir(msg: String) = when {
        msg.contains("Invalid API key", true) || msg.contains("No API key", true) ->
            "La clave del servidor no es válida. Revisá la configuración."
        msg.contains("Could not find the function", true) ->
            "El servidor no tiene la base instalada. Ejecutá supabase/schema.sql en Supabase."
        else -> msg
    }

    // ------------------------------------------------------------- Mapeos

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k)
    private fun JSONObject.lng(k: String): Long? = if (isNull(k)) null else optLong(k)

    fun tecnico(o: JSONObject) = Tecnico(
        id = o.getLong("id"), usuario = o.getString("usuario"),
        nombreCompleto = o.getString("nombre_completo"), rol = o.getString("rol"),
    )

    fun dispositivo(o: JSONObject) = Dispositivo(
        id = o.getLong("id"),
        nombreEquipo = o.getString("nombre_equipo"),
        direccionIp = o.getString("direccion_ip"),
        direccionMac = o.getString("direccion_mac"),
        oficina = o.getString("oficina"),
        correoInterno = o.getString("correo_interno"),
        tecnicoId = o.lng("tecnico_id"),
        tecnicoNombre = o.str("tecnico_nombre"),
        fechaRegistro = o.str("fecha_registro"),
        tipo = o.str("tipo") ?: "Institucional",
        estado = o.getString("estado"),
        activadoPorNombre = o.str("activado_por_nombre"),
        fechaActivacion = o.str("fecha_activacion"),
        redId = o.lng("red_id"),
        redNombre = o.str("red_nombre"),
        redCidr = o.str("red_cidr"),
        redMascara = o.str("red_mascara"),
        redGateway = o.str("red_gateway"),
    )

    fun red(o: JSONObject) = Red(
        id = o.getLong("id"), nombre = o.getString("nombre"), gateway = o.getString("gateway"),
        prefijo = o.getInt("prefijo"), cidr = o.getString("cidr"), mascara = o.getString("mascara"),
        broadcast = o.getString("broadcast"), ipInicio = o.getString("ip_inicio"), ipFin = o.getString("ip_fin"),
        libres = o.optInt("libres"), proxima = o.str("proxima"), equipos = o.optInt("equipos"),
    )

    fun <T> lista(a: Any?, f: (JSONObject) -> T): List<T> =
        (a as? JSONArray)?.let { arr -> List(arr.length()) { f(arr.getJSONObject(it)) } } ?: emptyList()
}
