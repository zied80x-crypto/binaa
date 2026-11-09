package tn.binaa.app.security

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Optional correction PIN. When set, any change to a PAID week requires it
 * (in addition to a written reason, which is always required).
 */
class AuthStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    val hasPin: Boolean get() = prefs.contains("pin_hash")

    private fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    fun setPin(pin: String) {
        require(pin.length >= 4 && pin.all { it.isDigit() }) { "الرمز يجب أن يكون 4 أرقام على الأقل" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        prefs.edit().putString("pin_salt", salt).putString("pin_hash", hash(pin, salt)).apply()
    }

    fun verify(pin: String): Boolean {
        val salt = prefs.getString("pin_salt", null) ?: return true
        return hash(pin, salt) == prefs.getString("pin_hash", null)
    }

    fun clearPin() { prefs.edit().remove("pin_hash").remove("pin_salt").apply() }
}
