package ar.escusub.controlips

import android.util.Patterns

object Validators {

    /** Devuelve la IPv4 normalizada (sin ceros a la izquierda) o null si es inválida. */
    fun normalizarIp(raw: String): String? {
        val parts = raw.trim().split(".")
        if (parts.size != 4) return null
        val nums = parts.map { p ->
            if (p.isEmpty() || p.length > 3 || !p.all { it.isDigit() }) return null
            p.toInt().also { if (it > 255) return null }
        }
        return nums.joinToString(".")
    }

    /**
     * Acepta AA:BB:CC:DD:EE:FF, AA-BB-CC-DD-EE-FF, AABB.CCDD.EEFF o AABBCCDDEEFF
     * y devuelve siempre AA:BB:CC:DD:EE:FF en mayúsculas; null si es inválida.
     */
    fun normalizarMac(raw: String): String? {
        val hex = raw.trim().uppercase().replace(Regex("[:\\-.\\s]"), "")
        if (hex.length != 12 || !hex.all { it in '0'..'9' || it in 'A'..'F' }) return null
        return hex.chunked(2).joinToString(":")
    }

    fun ipALong(ip: String): Long =
        ip.split(".").fold(0L) { acc, p -> (acc shl 8) or p.toLong() }

    fun longAIp(v: Long): String =
        listOf(24, 16, 8, 0).joinToString(".") { ((v shr it) and 0xFF).toString() }

    fun correoValido(raw: String) = Patterns.EMAIL_ADDRESS.matcher(raw.trim()).matches()
}
