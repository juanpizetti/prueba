package ar.escusub.controlips

data class Tecnico(
    val id: Long,
    val usuario: String,
    val nombreCompleto: String,
    val rol: String,
) {
    val esAdmin get() = rol == ROL_ADMIN

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
    val redId: Long? = null,
    val redNombre: String? = null,
    val estado: String = ESTADO_PENDIENTE,
    val activadoPorNombre: String? = null,
    val fechaActivacion: String? = null,
) {
    val activo get() = estado == ESTADO_ACTIVO

    companion object {
        const val ESTADO_PENDIENTE = "pendiente"
        const val ESTADO_ACTIVO = "activo"
        val TIPOS = listOf("Institucional", "Particular")
    }
}

/** Red / subred desde la que se asignan IPs automáticamente. */
data class Red(
    val id: Long = 0,
    val nombre: String,
    val gateway: String,
    val prefijo: Int,
    val ipInicio: String,
    val ipFin: String,
) {
    private val mascara get() = Validators.mascara(prefijo)
    val direccionRed get() = Validators.longAIp(Validators.ipALong(gateway) and mascara)
    val broadcast get() = Validators.longAIp((Validators.ipALong(gateway) and mascara) or (mascara.inv() and 0xFFFFFFFFL))
    val cidr get() = "$direccionRed/$prefijo"

    /** true si la IP pertenece a esta subred. */
    fun contiene(ip: String) = (Validators.ipALong(ip) and mascara) == (Validators.ipALong(gateway) and mascara)

    /** true si la IP se puede asignar a un equipo (no es red, broadcast ni gateway). */
    fun asignable(ip: String) = contiene(ip) && ip != direccionRed && ip != broadcast && ip != gateway

    override fun toString() = nombre
}
