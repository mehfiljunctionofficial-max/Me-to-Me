package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

class SecurityManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "secure_shield_auth_prefs"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PATTERN_HASH = "pattern_hash"
        private const val KEY_LOCK_TYPE = "lock_type" // "PIN", "PATTERN", "BIOMETRIC", "NONE"
        
        // Salt for SHA-256 hashing
        private const val SALT = "SecUreSh1eld_Salt_#2026"
        
        const val LOCK_TYPE_PIN = "PIN"
        const val LOCK_TYPE_PATTERN = "PATTERN"
        const val LOCK_TYPE_BIOMETRIC = "BIOMETRIC"
        const val LOCK_TYPE_NONE = "NONE"
    }

    /**
     * Generates a secure SHA-256 hash of a given input salted with a constant key.
     */
    fun hashString(input: String): String {
        return try {
            val saltedInput = input + SALT
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(saltedInput.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { String.format("%02x", it) }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback (should never be reached in modern JVM/Android)
            input
        }
    }

    /**
     * Hash and securely save PIN code.
     */
    fun savePIN(pin: String): Boolean {
        if (pin.length != 4) return false
        val hashed = hashString(pin)
        return prefs.edit().putString(KEY_PIN_HASH, hashed).commit()
    }

    /**
     * Validates if entered PIN hashes to the same stored hash.
     */
    fun verifyPIN(pin: String): Boolean {
        val savedHash = prefs.getString(KEY_PIN_HASH, null) ?: hashString("1234") // default fallback: 1234
        return hashString(pin) == savedHash
    }

    /**
     * Hash and securely save Pattern path.
     */
    fun savePattern(pattern: String): Boolean {
        if (pattern.isEmpty()) return false
        val hashed = hashString(pattern)
        return prefs.edit().putString(KEY_PATTERN_HASH, hashed).commit()
    }

    /**
     * Validates if entered Pattern path matches stored pattern hash.
     */
    fun verifyPattern(pattern: String): Boolean {
        val savedHash = prefs.getString(KEY_PATTERN_HASH, null)
        if (savedHash == null) {
            // If none set, check if default pattern can be validated.
            return false
        }
        return hashString(pattern) == savedHash
    }

    /**
     * Save currently active Lock Type selection.
     */
    fun setLockType(lockType: String) {
        prefs.edit().putString(KEY_LOCK_TYPE, lockType).apply()
    }

    /**
     * Get currently active Lock Type selection.
     */
    fun getLockType(): String {
        return prefs.getString(KEY_LOCK_TYPE, LOCK_TYPE_PIN) ?: LOCK_TYPE_PIN
    }

    /**
     * Clean all stored security parameters (e.g. for factory reset).
     */
    fun clearAuth() {
        prefs.edit().clear().apply()
    }

    /**
     * Check if a lock credential exists.
     */
    fun isLockConfigured(): Boolean {
        val type = getLockType()
        return when (type) {
            LOCK_TYPE_PIN -> prefs.contains(KEY_PIN_HASH)
            LOCK_TYPE_PATTERN -> prefs.contains(KEY_PATTERN_HASH)
            LOCK_TYPE_BIOMETRIC -> true // Biometrics always configured on device level if chosen
            else -> false
        }
    }
}
