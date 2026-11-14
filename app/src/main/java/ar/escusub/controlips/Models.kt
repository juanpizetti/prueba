package ar.escusub.controlips

import org.json.JSONObject

data class Tecnico(
    val id: Long,
    val usuario: String,
    val nombreCompleto: String,
    val rol: String,
) {
    val esAdmin get() = rol == ROL_ADMIN

    fun toJson(): String = JSONObject().put("id", id).put("usuario", usuario)
        .put("nombre_completo", nombreCompleto).put("rol", rol).toString()

    companion object {
        const val ROL_ADMIN = "admin"
        const val ROL_TECNICO = "tecnico"
    }
}

data class Dispositivo(
    val id: Long = 0,
    val nombreEquipo: String,
    val direccionIp: String,
    val direccionMac: String,
    val oficina: String,
    val correoInterno: String,
    val tecnicoId: Long?,
    val tecnicoNombre: String? = null,
    val fechaRegistro: String? = null,
    val tipo: String = "Institucional",
    val estado: String = ESTADO_PENDIENTE,
    val activadoPorNombre: String? = null,
    val fechaActivacion: String? = null,
    val redId: Long? = null,
    val redNombre: String? = null,
    val redCidr: String? = null,
    val redMascara: String? = null,
    val redGateway: String? = null,
) {
    val activo get() = estado == ESTADO_ACTIVO

    fun toJson(): String = JSONObject().put("id", id).put("nombre_equipo", nombreEquipo)
        .put("direccion_ip", direccionIp).put("direccion_mac", direccionMac).put("oficina", oficina)
        .put("correo_interno", correoInterno).put("tecnico_id", tecnicoId ?: JSONObject.NULL)
        .put("tecnico_nombre", tecnicoNombre ?: JSONObject.NULL).put("fecha_registro", fechaRegistro ?: JSONObject.NULL)
        .put("tipo", tipo).put("estado", estado).put("activado_por_nombre", activadoPorNombre ?: JSONObject.NULL)
        .put("fecha_activacion", fechaActivacion ?: JSONObject.NULL).put("red_id", redId ?: JSONObject.NULL)
        .put("red_nombre", redNombre ?: JSONObject.NULL).put("red_cidr", redCidr ?: JSONObject.NULL)
        .put("red_mascara", redMascara ?: JSONObject.NULL).put("red_gateway", redGateway ?: JSONObject.NULL)
        .toString()

    companion object {
        const val ESTADO_PENDIENTE = "pendiente"
        const val ESTADO_ACTIVO = "activo"
        val TIPOS = listOf("Institucional", "Particular")
    }
}

/** Red / subred desde la que se asignan IPs automáticamente (datos calculados por el servidor). */
data class Red(
    val id: Long,
    val nombre: String,
    val gateway: String,
    val prefijo: Int,
    val cidr: String,
    val mascara: String,
    val broadcast: String,
    val ipInicio: String,
    val ipFin: String,
    val libres: Int = 0,
    val proxima: String? = null,
    val equipos: Int = 0,
) {
    override fun toString() = "$nombre  ($cidr)"
}
