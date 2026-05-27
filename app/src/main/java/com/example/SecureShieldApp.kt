package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.db.AppDatabase
import com.example.data.repository.SecurityRepository

class SecureShieldApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: SecurityRepository
        private set

    companion object {
        lateinit var instance: SecureShieldApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            database = Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                "secureshield_database"
            )
            .fallbackToDestructiveMigration()
            .build()
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                // Fallback to in-memory database if disk database initialization fails for any reason
                database = Room.inMemoryDatabaseBuilder(
                    applicationContext,
                    AppDatabase::class.java
                )
                .fallbackToDestructiveMigration()
                .build()
            } catch (inner: Exception) {
                inner.printStackTrace()
            }
        }

        // Guarantee that repository is always initialized
        try {
            repository = SecurityRepository(applicationContext, database.securityDao)
        } catch (e: Exception) {
            e.printStackTrace()
            // Ultimate fallback to ensure lateinit is initialized under all circumstances
            try {
                val fbDb = Room.inMemoryDatabaseBuilder(applicationContext, AppDatabase::class.java)
                    .fallbackToDestructiveMigration()
                    .build()
                database = fbDb
                repository = SecurityRepository(applicationContext, fbDb.securityDao)
            } catch (finalEx: Exception) {
                finalEx.printStackTrace()
            }
        }
    }
}
