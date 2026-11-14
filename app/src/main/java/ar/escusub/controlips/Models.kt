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
