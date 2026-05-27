package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.LockScreenActivity
import com.example.SecureShieldApp
import kotlinx.coroutines.*

class AppCheckService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var monitorJob: Job? = null

    companion object {
        var isServiceRunning = false
            private set

        // Keep track of which app is currently unlocked by the user
        var currentlyUnlockedPackage: String? = null
        
        const val NOTIFICATION_ID = 404
        const val CHANNEL_ID = "secureshield_guard_channel"
        
        fun startService(context: Context) {
            val intent = Intent(context, AppCheckService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AppCheckService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()
        val notification = createNotification()
        
        // Start persistent foreground presence with type on Android 14+ / Q+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoringLoop()
        return START_STICKY
    }

    private fun startMonitoringLoop() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            val app = applicationContext as? SecureShieldApp
            if (app == null) {
                Log.e("AppCheckService", "Application context is not SecureShieldApp")
                return@launch
            }
            
            // Loop with small delay until app.repository is fully ready (lateinit check)
            var repo = try { app.repository } catch (e: Exception) { null }
            while (repo == null && isActive) {
                delay(200)
                repo = try { app.repository } catch (e: Exception) { null }
            }
            
            val repository = repo ?: return@launch
            var lastActiveApp: String? = null
            
            while (isActive) {
                try {
                    val currentApp = getForegroundAppPackage()
                    if (currentApp != null && currentApp != packageName) {
                        
                        // Detect app transition
                        if (currentApp != lastActiveApp) {
                            Log.d("AppCheckService", "Foreground app transition from $lastActiveApp to $currentApp")
                            lastActiveApp = currentApp
                            
                            // Reset unlocked package when switching away from it (excluding SystemUI, android system, and launcher)
                            if (currentApp != currentlyUnlockedPackage && 
                                currentApp != "com.android.systemui" && 
                                currentApp != "android" && 
                                !currentApp.contains("launcher")) {
                                currentlyUnlockedPackage = null
                            }
                        }

                        // Verify if package is locked in our SQL database
                        val isLocked = repository.isAppLocked(currentApp)
                        if (isLocked && currentApp != currentlyUnlockedPackage) {
                            Log.d("AppCheckService", "Triggering Shield Lock for: $currentApp")
                            
                            // Launch visual verification overlay screen
                            val lockIntent = Intent(applicationContext, LockScreenActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                putExtra("TARGET_PACKAGE", currentApp)
                            }
                            startActivity(lockIntent)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AppCheckService", "Error during foreground check loop", e)
                }
                
                // Sleep for 500ms to maintain optimal CPU cycles and responsive protection
                delay(500)
            }
        }
    }

    private fun getForegroundAppPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val timeNow = System.currentTimeMillis()
        
        // Query recent usage events in the last 15 seconds
        val usageEvents = usm.queryEvents(timeNow - 15000, timeNow) ?: return null
        val event = UsageEvents.Event()
        var lastResumedPackage: String? = null
        
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastResumedPackage = event.packageName
            }
        }
        
        // Fallback option in case event query lacks permissions or returned empty
        if (lastResumedPackage == null) {
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, timeNow - 1000 * 10, timeNow)
            if (!stats.isNullOrEmpty()) {
                lastResumedPackage = stats.maxByOrNull { it.lastTimeUsed }?.packageName
            }
        }
        
        return lastResumedPackage
    }

    private fun createNotification(): Notification {
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 101, mainIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SecureShield Sentinel")
            .setContentText("Persistent app locker protector running in background.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setColor(0xFF10B981.toInt())
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "SecureShield App Locker Service Running",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification to keep the SecureShield app locker active in the background."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        monitorJob?.cancel()
        serviceJob.cancel()
    }
}
