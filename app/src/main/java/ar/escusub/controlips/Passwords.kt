package ar.escusub.controlips

import org.mindrot.jbcrypt.BCrypt

/** BCrypt compatible con los hashes $2y$ generados por PHP (password_hash). */
object Passwords {
    fun hash(plain: String): String = BCrypt.hashpw(plain, BCrypt.gensalt(10))

    fun verificar(plain: String, hash: String): Boolean = try {
        // jBCrypt sólo entiende el prefijo $2a$; $2y$/$2b$ son equivalentes.
        val h = if (hash.startsWith("\$2y\$") || hash.startsWith("\$2b\$")) "\$2a\$" + hash.substring(4) else hash
        BCrypt.checkpw(plain, h)
    } catch (e: Exception) {
        false
    }
}
