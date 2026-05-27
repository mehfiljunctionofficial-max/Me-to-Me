package com.example.data.repository

import android.content.Context
import com.example.data.db.SecurityDao
import com.example.data.model.LockedApp
import com.example.data.model.SecurityLog
import com.example.data.model.SecuritySetting
import com.example.data.model.VaultFile
import com.example.data.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

class SecurityRepository(
    private val context: Context,
    private val securityDao: SecurityDao,
    private val cryptoManager: CryptoManager = CryptoManager()
) {
    // --- Flows ---
    val allLockedApps: Flow<List<LockedApp>> = securityDao.getAllLockedApps()
    val activeVaultFiles: Flow<List<VaultFile>> = securityDao.getActiveVaultFiles()
    val deletedVaultFiles: Flow<List<VaultFile>> = securityDao.getDeletedVaultFiles()
    val securityLogs: Flow<List<SecurityLog>> = securityDao.getAllLogs()

    fun getVaultFilesByType(type: String): Flow<List<VaultFile>> = securityDao.getVaultFilesByType(type)

    // --- App Locker Settings & States ---
    suspend fun lockApp(packageName: String, appName: String) = withContext(Dispatchers.IO) {
        securityDao.insertLockedApp(LockedApp(packageName, appName, isLocked = true))
        logEvent("App locked: $appName ($packageName)")
    }

    suspend fun unlockApp(packageName: String) = withContext(Dispatchers.IO) {
        securityDao.isAppLocked(packageName).let { exists ->
            if (exists) {
                securityDao.deleteLockedApp(LockedApp(packageName, ""))
                logEvent("App unlocked: $packageName")
            }
        }
    }

    suspend fun isAppLocked(packageName: String): Boolean = withContext(Dispatchers.IO) {
        securityDao.isAppLocked(packageName)
    }

    // --- Security Settings ---
    suspend fun setPIN(pin: String) = withContext(Dispatchers.IO) {
        securityDao.insertSetting(SecuritySetting("auth_type", "PIN"))
        securityDao.insertSetting(SecuritySetting("pin_code", pin))
        logEvent("Authentication changed to PIN Lock")
    }

    suspend fun setPattern(pattern: String) = withContext(Dispatchers.IO) {
        securityDao.insertSetting(SecuritySetting("auth_type", "PATTERN"))
        securityDao.insertSetting(SecuritySetting("pattern_code", pattern))
        logEvent("Authentication changed to Pattern Lock")
    }

    suspend fun getAuthType(): String = withContext(Dispatchers.IO) {
        securityDao.getSetting("auth_type")?.value ?: "NONE"
    }

    suspend fun getSavedPIN(): String? = withContext(Dispatchers.IO) {
        securityDao.getSetting("pin_code")?.value
    }

    suspend fun getSavedPattern(): String? = withContext(Dispatchers.IO) {
        securityDao.getSetting("pattern_code")?.value
    }

    suspend fun isBiometricEnabled(): Boolean = withContext(Dispatchers.IO) {
        securityDao.getSetting("biometric_enabled")?.value?.toBoolean() ?: false
    }

    suspend fun setBiometricEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        securityDao.insertSetting(SecuritySetting("biometric_enabled", enabled.toString()))
        logEvent("Biometrics auth status set to $enabled")
    }

    // --- Safe File Vault Interactions (Cryptographic) ---

    /**
     * Imports and encrypts an existing local file or an input stream.
     * Original file is safely deleted if requested.
     */
    suspend fun importFile(
        sourceFile: File,
        fileType: String,
        deleteOriginal: Boolean = false
    ): VaultFile? = withContext(Dispatchers.IO) {
        try {
            val originalPath = sourceFile.absolutePath
            val fileName = sourceFile.name
            val fileSize = sourceFile.length()

            // Prepare directory
            val vaultDir = File(context.filesDir, "secureshield_vault")
            if (!vaultDir.exists()) vaultDir.mkdirs()

            val targetFile = File(vaultDir, "shield_${System.currentTimeMillis()}_${fileName}.enc")

            FileInputStream(sourceFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    val iv = cryptoManager.encrypt(input, output)
                    
                    val vaultFile = VaultFile(
                        originalPath = originalPath,
                        encryptedPath = targetFile.absolutePath,
                        fileName = fileName,
                        fileType = fileType,
                        fileSize = fileSize,
                        encryptionIv = byteArrayToHexString(iv),
                        isDeleted = false
                    )

                    val recordId = securityDao.insertVaultFile(vaultFile)

                    if (deleteOriginal) {
                        sourceFile.delete()
                    }

                    logEvent("Encrypted file imported: $fileName (${fileSize / 1024} KB)")
                    return@withContext vaultFile.copy(id = recordId.toInt())
                }
            }
        } catch (e: Exception) {
            logEvent("Encryption import error: ${e.message}", isSuccess = false)
            e.printStackTrace()
            null
        }
    }

    /**
     * Seed a demo simulated file for visual showing.
     */
    suspend fun seedDemoFile(fileName: String, fileType: String, content: String): VaultFile? = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(context.cacheDir, fileName)
            tempFile.writeText(content)
            val result = importFile(tempFile, fileType, deleteOriginal = true)
            return@withContext result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decrypts the vaulted file and restores it to its original path or system directory.
     */
    suspend fun exportAndRestoreFile(vaultFileId: Int, destinationDirectory: File? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = securityDao.getVaultFileById(vaultFileId) ?: return@withContext false
            val encryptedFile = File(file.encryptedPath)
            if (!encryptedFile.exists()) {
                logEvent("Restoration error: Encrypted payload not found on disk", isSuccess = false)
                return@withContext false
            }

            // Restore location
            val targetDir = destinationDirectory ?: File(file.originalPath).parentFile ?: context.cacheDir
            if (!targetDir.exists()) targetDir.mkdirs()
            
            val targetFile = File(targetDir, file.fileName)

            FileInputStream(encryptedFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    cryptoManager.decrypt(input, output)
                }
            }

            // Delete encrypted file from disk and DB record
            encryptedFile.delete()
            securityDao.deleteVaultFile(file)

            logEvent("Decrypted and restored file: ${file.fileName}")
            true
        } catch (e: Exception) {
            logEvent("Decryption error for file Id $vaultFileId: ${e.message}", isSuccess = false)
            e.printStackTrace()
            false
        }
    }

    /**
     * Moves a file to the temporary recycle bin.
     */
    suspend fun recycleFile(id: Int): Boolean = withContext(Dispatchers.IO) {
        val file = securityDao.getVaultFileById(id) ?: return@withContext false
        val updated = file.copy(
            isDeleted = true,
            deletedTimestamp = System.currentTimeMillis()
        )
        securityDao.updateVaultFile(updated)
        logEvent("File trashed to Recycle Bin: ${file.fileName}")
        true
    }

    /**
     * Restores a file from the trash back to active vault status.
     */
    suspend fun restoreFromRecycleBin(id: Int): Boolean = withContext(Dispatchers.IO) {
        val file = securityDao.getVaultFileById(id) ?: return@withContext false
        val updated = file.copy(
            isDeleted = false,
            deletedTimestamp = 0L
        )
        securityDao.updateVaultFile(updated)
        logEvent("File restored from Recycle Bin: ${file.fileName}")
        true
    }

    /**
     * Permanently purges a file from the disk.
     */
    suspend fun permanentlyDeleteFile(id: Int): Boolean = withContext(Dispatchers.IO) {
        val file = securityDao.getVaultFileById(id) ?: return@withContext false
        
        // Physically delete encrypted source on storage
        val encFile = File(file.encryptedPath)
        if (encFile.exists()) {
            encFile.delete()
        }
        
        // Remove DB entry
        securityDao.deleteVaultFile(file)
        logEvent("Permanently purged file: ${file.fileName}")
        true
    }

    // --- Logger Helpers ---
    suspend fun logEvent(message: String, isSuccess: Boolean = true) = withContext(Dispatchers.IO) {
        securityDao.insertLog(SecurityLog(message = message, isSuccess = isSuccess))
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        securityDao.clearLogs()
    }

    // --- Hex conversion utility ---
    private fun byteArrayToHexString(array: ByteArray): String {
        return array.joinToString("") { String.format("%02x", it) }
    }
}
