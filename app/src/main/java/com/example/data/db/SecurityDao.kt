package com.example.data.db

import androidx.room.*
import com.example.data.model.LockedApp
import com.example.data.model.SecurityLog
import com.example.data.model.SecuritySetting
import com.example.data.model.VaultFile
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityDao {

    // --- Locked Apps queries ---
    @Query("SELECT * FROM locked_apps ORDER BY appName ASC")
    fun getAllLockedApps(): Flow<List<LockedApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLockedApp(app: LockedApp)

    @Delete
    suspend fun deleteLockedApp(app: LockedApp)

    @Query("SELECT EXISTS(SELECT 1 FROM locked_apps WHERE packageName = :packageName LIMIT 1)")
    suspend fun isAppLocked(packageName: String): Boolean


    // --- Vault Files queries ---
    @Query("SELECT * FROM vault_files WHERE isDeleted = 0 ORDER BY id DESC")
    fun getActiveVaultFiles(): Flow<List<VaultFile>>

    @Query("SELECT * FROM vault_files WHERE isDeleted = 1 ORDER BY deletedTimestamp DESC")
    fun getDeletedVaultFiles(): Flow<List<VaultFile>>

    @Query("SELECT * FROM vault_files WHERE isDeleted = 0 AND fileType = :type ORDER BY id DESC")
    fun getVaultFilesByType(type: String): Flow<List<VaultFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultFile(file: VaultFile): Long

    @Update
    suspend fun updateVaultFile(file: VaultFile)

    @Delete
    suspend fun deleteVaultFile(file: VaultFile)

    @Query("SELECT * FROM vault_files WHERE id = :id LIMIT 1")
    suspend fun getVaultFileById(id: Int): VaultFile?


    // --- Settings queries ---
    @Query("SELECT * FROM security_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): SecuritySetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: SecuritySetting)

    @Query("DELETE FROM security_settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)


    // --- Security Logs queries ---
    @Query("SELECT * FROM security_logs ORDER BY timestamp DESC LIMIT 150")
    fun getAllLogs(): Flow<List<SecurityLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SecurityLog)

    @Query("DELETE FROM security_logs")
    suspend fun clearLogs()
}
