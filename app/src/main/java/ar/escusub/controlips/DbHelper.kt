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
    SQLiteOpenHelper(ctx.applicationContext, "control_ips.db", null, 3) {

    companion object {
        /** Redes iniciales (gateway = .1, se asigna desde la siguiente IP hasta la última antes del broadcast). */
        val REDES_INICIALES = listOf(
            Red(1, "Red Principal", "172.21.88.1", 23, "172.21.88.2", "172.21.89.254"),
            Red(2, "Red DMZ", "172.23.28.1", 26, "172.23.28.2", "172.23.28.62"),
            Red(3, "Red Wifi", "192.168.66.1", 24, "192.168.66.2", "192.168.66.254"),
        )

        @Volatile private var instance: DbHelper? = null
        fun get(ctx: Context): DbHelper =
            instance ?: synchronized(this) { instance ?: DbHelper(ctx).also { instance = it } }

        /** Sólo para pruebas: descarta la instancia compartida. */
        internal fun reiniciar() = synchronized(this) { instance?.close(); instance = null }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        crearRedes(db)
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
              fecha_activacion TEXT,
              red_id INTEGER REFERENCES redes(id) ON DELETE SET NULL
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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            // v3: varias redes. Cada equipo existente se asocia a la red que contiene su IP.
            crearRedes(db)
            db.execSQL("ALTER TABLE dispositivos ADD COLUMN red_id INTEGER REFERENCES redes(id) ON DELETE SET NULL")
            db.rawQuery("SELECT id, direccion_ip FROM dispositivos", null).use { c ->
                while (c.moveToNext()) {
                    val red = REDES_INICIALES.firstOrNull { it.contiene(c.getString(1)) } ?: continue
                    db.execSQL("UPDATE dispositivos SET red_id = ? WHERE id = ?", arrayOf(red.id, c.getLong(0)))
                }
            }
        }
    }

    private fun crearRedes(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS redes (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              nombre TEXT NOT NULL UNIQUE COLLATE NOCASE,
              gateway TEXT NOT NULL,
              prefijo INTEGER NOT NULL,
              ip_inicio TEXT NOT NULL,
              ip_fin TEXT NOT NULL
            )
            """.trimIndent()
        )
        for (r in REDES_INICIALES) db.insertWithOnConflict("redes", null, r.valores().apply { put("id", r.id) }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    // ----------------------------------------------------------------- Redes

    private fun Red.valores() = ContentValues().apply {
        put("nombre", nombre.trim()); put("gateway", gateway); put("prefijo", prefijo)
        put("ip_inicio", ipInicio); put("ip_fin", ipFin)
    }

    private fun Cursor.toRed() = Red(
        id = getLong(getColumnIndexOrThrow("id")),
        nombre = getString(getColumnIndexOrThrow("nombre")),
        gateway = getString(getColumnIndexOrThrow("gateway")),
        prefijo = getInt(getColumnIndexOrThrow("prefijo")),
        ipInicio = getString(getColumnIndexOrThrow("ip_inicio")),
        ipFin = getString(getColumnIndexOrThrow("ip_fin")),
    )

    fun redes(): List<Red> =
        readableDatabase.rawQuery("SELECT * FROM redes ORDER BY id", null).use { c ->
            buildList { while (c.moveToNext()) add(c.toRed()) }
        }

    fun red(id: Long): Red? = redes().firstOrNull { it.id == id }

    /** Alta (id = 0) o modificación. Lanza SQLiteConstraintException si el nombre ya existe. */
    fun guardarRed(r: Red): Long =
        if (r.id == 0L) writableDatabase.insertOrThrow("redes", null, r.valores())
        else { writableDatabase.updateWithOnConflict("redes", r.valores(), "id = ?", arrayOf(r.id.toString()), SQLiteDatabase.CONFLICT_ABORT); r.id }

    fun equiposEnRed(id: Long): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM dispositivos WHERE red_id = ?", arrayOf(id.toString())).use { c ->
            c.moveToFirst(); c.getInt(0)
        }

    fun eliminarRed(id: Long) {
        writableDatabase.delete("redes", "id = ?", arrayOf(id.toString()))
    }

    private fun ipsUsadas(): Set<String> =
        readableDatabase.rawQuery("SELECT direccion_ip FROM dispositivos", null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

    /** Primera IP libre del rango de la red, o null si está completo. */
    fun siguienteIpLibre(red: Red): String? {
        val usadas = ipsUsadas()
        var v = Validators.ipALong(red.ipInicio)
        val end = Validators.ipALong(red.ipFin)
        while (v <= end) {
            val ip = Validators.longAIp(v)
            if (ip !in usadas && red.asignable(ip)) return ip
            v++
        }
        return null
    }

    /** Cantidad de IPs libres en el rango asignable de la red. */
    fun ipsLibres(red: Red): Int {
        val a = Validators.ipALong(red.ipInicio); val b = Validators.ipALong(red.ipFin)
        val usadas = ipsUsadas()
        var libres = 0
        var v = a
        while (v <= b) { val ip = Validators.longAIp(v); if (ip !in usadas && red.asignable(ip)) libres++; v++ }
        return libres
    }

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
        SELECT d.*, t.nombre_completo AS tecnico_nombre, a.nombre_completo AS activador_nombre, r.nombre AS red_nombre
        FROM dispositivos d
        LEFT JOIN redes r ON r.id = d.red_id
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
        redId = getColumnIndexOrThrow("red_id").let { if (isNull(it)) null else getLong(it) },
        redNombre = str("red_nombre"),
        estado = str("estado")!!,
        activadoPorNombre = str("activador_nombre"),
        fechaActivacion = str("fecha_activacion"),
    )

    /**
     * @param estado null = todos
     * @param soloTecnico si no es null, sólo los cargados por ese técnico
     */
    fun dispositivos(estado: String?, busqueda: String, soloTecnico: Long?, redId: Long? = null): List<Dispositivo> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (redId != null) { where += "d.red_id = ?"; args += redId.toString() }
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
        put("red_id", redId)
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
