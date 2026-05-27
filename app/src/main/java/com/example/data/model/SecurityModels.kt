package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locked_apps")
data class LockedApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isLocked: Boolean = true
)

@Entity(tableName = "vault_files")
data class VaultFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalPath: String,
    val encryptedPath: String,
    val fileName: String,
    val fileType: String, // "IMAGE", "VIDEO", "AUDIO", "DOCUMENT"
    val fileSize: Long,
    val encryptionIv: String, // Hex string of IV for decryption
    val isDeleted: Boolean = false, // True means in Recycle Bin
    val deletedTimestamp: Long = 0L
)

@Entity(tableName = "security_settings")
data class SecuritySetting(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "security_logs")
data class SecurityLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean = true
)
