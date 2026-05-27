package com.example.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.model.LockedApp
import com.example.data.model.SecurityLog
import com.example.data.model.SecuritySetting
import com.example.data.model.VaultFile

@Database(
    entities = [
        LockedApp::class,
        VaultFile::class,
        SecuritySetting::class,
        SecurityLog::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract val securityDao: SecurityDao
}
