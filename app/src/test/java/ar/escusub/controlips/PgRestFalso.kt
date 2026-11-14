package ar.escusub.controlips

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.postgresql.util.PSQLException
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types

/**
 * Imita el endpoint /rest/v1/rpc/<función> de Supabase (PostgREST) sobre un
 * PostgreSQL local con supabase/schema.sql cargado, para probar la app de punta a punta.
 */
class PgRestFalso : AutoCloseable {
    companion object {
        const val JDBC = "jdbc:postgresql://127.0.0.1:5432/controlips_test"
        const val CLAVE = "clave-de-prueba-0123456789"

        fun disponible(): Boolean = runCatching {
            Class.forName("org.postgresql.Driver")
            DriverManager.getConnection(JDBC, "postgres", "test").close(); true
        }.getOrDefault(false)
    }

    val db: Connection = DriverManager.getConnection(JDBC, "postgres", "test")
    private val server = MockWebServer()
    val url get() = server.url("/").toString().trimEnd('/')
    var llamadas = 0

    init {
        // Base limpia: esquema recién creado + contraseñas conocidas para las pruebas.
        db.createStatement().use { st ->
            st.execute("drop schema if exists cip cascade")
            st.execute(File("../supabase/schema.sql").readText())
            st.execute("update cip.tecnicos set password = extensions.crypt('clave', extensions.gen_salt('bf', 4))")
        }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val (code, body) = synchronized(this@PgRestFalso) {
                    llamadas++
                    if (request.getHeader("apikey") != CLAVE) 401 to """{"message":"Invalid API key"}"""
                    else ejecutar(request.path!!.substringAfterLast('/'), JSONObject(request.body.readUtf8()))
                }
                return MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
    }

    private fun ejecutar(fn: String, params: JSONObject): Pair<Int, String> {
        require(fn.matches(Regex("[a-z_]+")))
        val keys = params.keys().asSequence().toList()
        val sql = "select public.$fn(${keys.joinToString { "$it := ?" }})::text"
        return try {
            db.prepareStatement(sql).use { ps ->
                keys.forEachIndexed { i, k ->
                    val v = params.get(k)
                    if (v == JSONObject.NULL) ps.setNull(i + 1, Types.OTHER) else ps.setObject(i + 1, v.toString(), Types.OTHER)
                }
                ps.executeQuery().use { rs -> rs.next(); 200 to (rs.getString(1) ?: "null") }
            }
        } catch (e: PSQLException) {
            val state = e.serverErrorMessage?.sqlState ?: ""
            val code = if (state.startsWith("PT")) state.drop(2).toInt() else 400
            code to JSONObject().put("code", state).put("message", e.serverErrorMessage?.message ?: e.message).toString()
        }
    }

    fun consulta(sql: String): String? =
        db.createStatement().use { st -> st.executeQuery(sql).use { rs -> if (rs.next()) rs.getString(1) else null } }

    override fun close() {
        server.shutdown()
        db.close()
    }
}
