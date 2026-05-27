package com.example.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CryptoManager {
    companion object {
        private const val ALIAS = "secureshield_key_alias"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }

    private val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    private fun getKey(): SecretKey {
        val existingKey = keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: generateKey()
    }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts the [inputStream] data and saves it to [outputStream].
     * Prepends [IV length: 1 byte] + [IV bytes] to make the resulting file self-contained.
     */
    fun encrypt(inputStream: InputStream, outputStream: OutputStream): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val iv = cipher.iv

        outputStream.write(iv.size)
        outputStream.write(iv)

        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            val encryptedBytes = cipher.update(buffer, 0, bytesRead)
            if (encryptedBytes != null) {
                outputStream.write(encryptedBytes)
            }
        }
        val finalBytes = cipher.doFinal()
        if (finalBytes != null) {
            outputStream.write(finalBytes)
        }
        return iv
    }

    /**
     * Decrypts the [inputStream] (holding self-contained IV header) and saves it to [outputStream].
     */
    fun decrypt(inputStream: InputStream, outputStream: OutputStream) {
        val ivSize = inputStream.read()
        if (ivSize <= 0 || ivSize > 100) {
            throw IllegalArgumentException("Invalid IV size read from file body: $ivSize")
        }
        val iv = ByteArray(ivSize)
        inputStream.read(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), spec)

        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            val decryptedBytes = cipher.update(buffer, 0, bytesRead)
            if (decryptedBytes != null) {
                outputStream.write(decryptedBytes)
            }
        }
        val finalBytes = cipher.doFinal()
        if (finalBytes != null) {
            outputStream.write(finalBytes)
        }
    }
}
