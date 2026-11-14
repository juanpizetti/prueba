package ar.escusub.controlips

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Base de datos local SQLite. Estructura basada en control_ips.sql (MariaDB),
 * con el agregado del campo `estado` (pendiente = rojo / activo = verde).
 */
class DbHelper private constructor(ctx: Context) :
    SQLiteOpenHelper(ctx.applicationContext, "control_ips.db", null, 1) {

    companion object {
        @Volatile private var instance: DbHelper? = null
        fun get(ctx: Context): DbHelper =
            instance ?: synchronized(this) { instance ?: DbHelper(ctx).also { instance = it } }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE tecnicos (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              usuario TEXT NOT NULL UNIQUE COLLATE NOCASE,
              password TEXT NOT NULL,
              nombre_completo TEXT NOT NULL,
              rol TEXT NOT NULL DEFAULT 'tecnico' CHECK (rol IN ('admin','tecnico'))
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE dispositivos (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              nombre_equipo TEXT NOT NULL,
              direccion_ip TEXT NOT NULL UNIQUE,
              direccion_mac TEXT NOT NULL UNIQUE COLLATE NOCASE,
              oficina TEXT NOT NULL,
              correo_interno TEXT NOT NULL,
              tecnico_id INTEGER REFERENCES tecnicos(id) ON DELETE SET NULL,
              fecha_registro TEXT DEFAULT (datetime('now','localtime')),
              tipo TEXT NOT NULL DEFAULT 'Institucional',
              estado TEXT NOT NULL DEFAULT 'pendiente' CHECK (estado IN ('pendiente','activo')),
              activado_por INTEGER REFERENCES tecnicos(id) ON DELETE SET NULL,
              fecha_activacion TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_disp_tecnico ON dispositivos(tecnico_id)")

        // Usuarios importados de control_ips.sql (mismas contraseñas que en el sistema web).
        val seed = listOf(
            arrayOf(3L, "admin", "\$2y\$12\$DRJt1xoG9XslUCLXKxJtM.iL3ka9.F/ZkcPB26DaLJzF9N/UaBn9O", "DEC", "admin"),
            arrayOf(6L, "PIZETTI", "\$2y\$12\$GOKdkXsMGI8o/iuWTMgg6.avPwpIxp1lcnQqmX22n4qXaNVhtYkhm", "JUAN PIZETTI", "tecnico"),
            arrayOf(8L, "GOMEZ", "\$2y\$12\$HsNrvAYk28vGDGJctZyfS.3m7y2AeSxgjozRfMvt1GMHhxJOSaROu", "JUAN DE DIOS GOMEZ", "tecnico"),
        )
        for (s in seed) {
            db.insert("tecnicos", null, ContentValues().apply {
                put("id", s[0] as Long); put("usuario", s[1] as String); put("password", s[2] as String)
                put("nombre_completo", s[3] as String); put("rol", s[4] as String)
            })
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // ------------------------------------------------------------------ Técnicos

    private fun Cursor.toTecnico() = Tecnico(
        id = getLong(getColumnIndexOrThrow("id")),
        usuario = getString(getColumnIndexOrThrow("usuario")),
        nombreCompleto = getString(getColumnIndexOrThrow("nombre_completo")),
        rol = getString(getColumnIndexOrThrow("rol")),
    )

    /** Devuelve (tecnico, hash) para el usuario indicado. */
    fun buscarUsuario(usuario: String): Pair<Tecnico, String>? =
        readableDatabase.rawQuery("SELECT * FROM tecnicos WHERE usuario = ?", arrayOf(usuario.trim())).use { c ->
            if (c.moveToFirst()) c.toTecnico() to c.getString(c.getColumnIndexOrThrow("password")) else null
        }

    fun hashDe(id: Long): String? =
        readableDatabase.rawQuery("SELECT password FROM tecnicos WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    fun tecnico(id: Long): Tecnico? =
        readableDatabase.rawQuery("SELECT * FROM tecnicos WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.toTecnico() else null
        }

    fun tecnicos(): List<Tecnico> =
        readableDatabase.rawQuery("SELECT * FROM tecnicos ORDER BY rol, nombre_completo", null).use { c ->
            buildList { while (c.moveToNext()) add(c.toTecnico()) }
        }

    fun existeUsuario(usuario: String) = buscarUsuario(usuario) != null

    fun crearTecnico(usuario: String, hash: String, nombre: String, rol: String): Long =
        writableDatabase.insertOrThrow("tecnicos", null, ContentValues().apply {
            put("usuario", usuario.trim()); put("password", hash)
            put("nombre_completo", nombre.trim()); put("rol", rol)
        })

    fun cambiarPassword(id: Long, hash: String) {
        writableDatabase.update("tecnicos", ContentValues().apply { put("password", hash) }, "id = ?", arrayOf(id.toString()))
    }

    fun cambiarRol(id: Long, rol: String) {
        writableDatabase.update("tecnicos", ContentValues().apply { put("rol", rol) }, "id = ?", arrayOf(id.toString()))
    }

    fun cantidadAdmins(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM tecnicos WHERE rol = 'admin'", null).use { c ->
            c.moveToFirst(); c.getInt(0)
        }

    fun eliminarTecnico(id: Long) {
        writableDatabase.delete("tecnicos", "id = ?", arrayOf(id.toString()))
    }

    // ------------------------------------------------------------- Dispositivos

    private val selectDisp = """
        SELECT d.*, t.nombre_completo AS tecnico_nombre, a.nombre_completo AS activador_nombre
        FROM dispositivos d
        LEFT JOIN tecnicos t ON t.id = d.tecnico_id
        LEFT JOIN tecnicos a ON a.id = d.activado_por
    """.trimIndent()

    private fun Cursor.str(col: String): String? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getString(it) }

    private fun Cursor.toDispositivo() = Dispositivo(
        id = getLong(getColumnIndexOrThrow("id")),
        nombreEquipo = str("nombre_equipo")!!,
        direccionIp = str("direccion_ip")!!,
        direccionMac = str("direccion_mac")!!,
        oficina = str("oficina")!!,
        correoInterno = str("correo_interno")!!,
        tecnicoId = getColumnIndexOrThrow("tecnico_id").let { if (isNull(it)) null else getLong(it) },
        tecnicoNombre = str("tecnico_nombre"),
        fechaRegistro = str("fecha_registro"),
        tipo = str("tipo") ?: "Institucional",
        estado = str("estado")!!,
        activadoPorNombre = str("activador_nombre"),
        fechaActivacion = str("fecha_activacion"),
    )

    /**
     * @param estado null = todos
     * @param soloTecnico si no es null, sólo los cargados por ese técnico
     */
    fun dispositivos(estado: String?, busqueda: String, soloTecnico: Long?): List<Dispositivo> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (estado != null) { where += "d.estado = ?"; args += estado }
        if (soloTecnico != null) { where += "d.tecnico_id = ?"; args += soloTecnico.toString() }
        val q = busqueda.trim()
        if (q.isNotEmpty()) {
            where += "(d.nombre_equipo LIKE ? OR d.direccion_ip LIKE ? OR d.direccion_mac LIKE ? OR d.oficina LIKE ? OR d.correo_interno LIKE ?)"
            repeat(5) { args += "%$q%" }
        }
        val sql = selectDisp +
            (if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")) +
            " ORDER BY CASE d.estado WHEN 'pendiente' THEN 0 ELSE 1 END, d.fecha_registro DESC, d.id DESC"
        return readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            buildList { while (c.moveToNext()) add(c.toDispositivo()) }
        }
    }

    fun dispositivo(id: Long): Dispositivo? =
        readableDatabase.rawQuery("$selectDisp WHERE d.id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.toDispositivo() else null
        }

    /** Equipo que ya usa esa IP (excluyendo el que se está editando). */
    fun duenoDeIp(ip: String, excluirId: Long): Dispositivo? =
        readableDatabase.rawQuery("$selectDisp WHERE d.direccion_ip = ? AND d.id <> ?", arrayOf(ip, excluirId.toString())).use { c ->
            if (c.moveToFirst()) c.toDispositivo() else null
        }

    fun duenoDeMac(mac: String, excluirId: Long): Dispositivo? =
        readableDatabase.rawQuery("$selectDisp WHERE d.direccion_mac = ? AND d.id <> ?", arrayOf(mac, excluirId.toString())).use { c ->
            if (c.moveToFirst()) c.toDispositivo() else null
        }

    fun contar(estado: String, soloTecnico: Long?): Int {
        val sql = "SELECT COUNT(*) FROM dispositivos WHERE estado = ?" + if (soloTecnico != null) " AND tecnico_id = ?" else ""
        val args = if (soloTecnico != null) arrayOf(estado, soloTecnico.toString()) else arrayOf(estado)
        return readableDatabase.rawQuery(sql, args).use { c -> c.moveToFirst(); c.getInt(0) }
    }

    private fun Dispositivo.values() = ContentValues().apply {
        put("nombre_equipo", nombreEquipo); put("direccion_ip", direccionIp); put("direccion_mac", direccionMac)
        put("oficina", oficina); put("correo_interno", correoInterno); put("tipo", tipo)
    }

    /** Lanza SQLiteConstraintException si la IP o la MAC ya existen. */
    fun insertarDispositivo(d: Dispositivo): Long =
        writableDatabase.insertOrThrow("dispositivos", null, d.values().apply {
            put("tecnico_id", d.tecnicoId); put("estado", Dispositivo.ESTADO_PENDIENTE)
        })

    fun actualizarDispositivo(d: Dispositivo) {
        writableDatabase.updateWithOnConflict("dispositivos", d.values(), "id = ?", arrayOf(d.id.toString()), SQLiteDatabase.CONFLICT_ABORT)
    }

    fun cambiarEstado(id: Long, activo: Boolean, adminId: Long) {
        val cv = ContentValues().apply {
            if (activo) {
                put("estado", Dispositivo.ESTADO_ACTIVO); put("activado_por", adminId)
                put("fecha_activacion", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
            } else {
                put("estado", Dispositivo.ESTADO_PENDIENTE); putNull("activado_por"); putNull("fecha_activacion")
            }
        }
        writableDatabase.update("dispositivos", cv, "id = ?", arrayOf(id.toString()))
    }

    fun eliminarDispositivo(id: Long) {
        writableDatabase.delete("dispositivos", "id = ?", arrayOf(id.toString()))
    }
}
