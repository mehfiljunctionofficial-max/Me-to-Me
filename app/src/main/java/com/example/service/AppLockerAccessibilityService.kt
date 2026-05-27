package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.room.Room
import com.example.data.db.AppDatabase
import com.example.SecureShieldApp
import com.example.data.repository.SecurityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppLockerAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var securityRepository: SecurityRepository

    companion object {
        var isServiceRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        try {
            securityRepository = (applicationContext as SecureShieldApp).repository
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = android.accessibilityservice.AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            }
            this.serviceInfo = info
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isServiceRunning = true
        serviceScope.launch {
            try {
                if (::securityRepository.isInitialized) {
                    securityRepository.logEvent("Shield Accessibility Service connected successfully.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            // Prevent overlay loop on our own package
            if (packageName == this.packageName) return

            // Handle track of currentlyUnlockedPackage transitions on accessibility state updates
            val unlockedPkg = com.example.service.AppCheckService.currentlyUnlockedPackage
            if (unlockedPkg != null && 
                packageName != unlockedPkg && 
                packageName != "com.android.systemui" && 
                packageName != "android" && 
                !packageName.contains("launcher")) {
                com.example.service.AppCheckService.currentlyUnlockedPackage = null
            }

            serviceScope.launch {
                try {
                    val isLocked = securityRepository.isAppLocked(packageName)
                    if (isLocked && packageName != com.example.service.AppCheckService.currentlyUnlockedPackage) {
                        Log.d("AppLockerService", "Detected launch of locked app: $packageName")
                        
                        // Launch compose overlay locking activity
                        val lockIntent = Intent(applicationContext, com.example.LockScreenActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            putExtra("TARGET_PACKAGE", packageName)
                        }
                        startActivity(lockIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w("AppLockerService", "Accessibility Service Interrupted.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isServiceRunning = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceJob.cancel()
    }
}
