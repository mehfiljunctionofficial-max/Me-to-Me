package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SecureShieldApp
import com.example.data.model.LockedApp
import com.example.data.model.SecurityLog
import com.example.data.model.VaultFile
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as SecureShieldApp).repository

    // --- State Flows ---
    val activeVaultFiles: StateFlow<List<VaultFile>> = repository.activeVaultFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedVaultFiles: StateFlow<List<VaultFile>> = repository.deletedVaultFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lockedAppsFromDb: StateFlow<List<LockedApp>> = repository.allLockedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val securityLogs: StateFlow<List<SecurityLog>> = repository.securityLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _systemAppsList = MutableStateFlow<List<AppItem>>(emptyList())
    val systemAppsList: StateFlow<List<AppItem>> = _systemAppsList.asStateFlow()

    private val _currentAuthType = MutableStateFlow("PIN")
    val currentAuthType: StateFlow<String> = _currentAuthType.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(false)
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    init {
        loadSettingsAndApps()
        seedInitialDemoFilesIfNeeded()
    }

    fun loadSettingsAndApps() {
        viewModelScope.launch {
            _currentAuthType.value = repository.getAuthType()
            if (_currentAuthType.value == "NONE") {
                // Set default initial PIN
                repository.setPIN("1234")
                _currentAuthType.value = "PIN"
            }
            _isBiometricEnabled.value = repository.isBiometricEnabled()
            loadQueryableApplications()
        }
    }

    private fun loadQueryableApplications() {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val pm = context.packageManager
            
            // Query actual system apps
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val appList = mutableListOf<AppItem>()
            
            try {
                val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)
                resolveInfos.forEach { info ->
                    val pkgName = info.activityInfo.packageName
                    val name = info.loadLabel(pm).toString()
                    if (pkgName != context.packageName) {
                        appList.add(AppItem(packageName = pkgName, appName = name))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Fallback seed list for visual testing in emulators
            val demoPackages = listOf(
                AppItem("com.whatsapp", "WhatsApp"),
                AppItem("com.facebook.katana", "Facebook"),
                AppItem("com.google.android.youtube", "YouTube"),
                AppItem("com.google.android.apps.photos", "Google Photos"),
                AppItem("com.google.android.gm", "Gmail"),
                AppItem("com.android.settings", "System Settings"),
                AppItem("com.chrome.android", "Google Chrome"),
                AppItem("com.spotify.music", "Spotify")
            )

            // Combine actual apps with some recognizable demo targets if list is small/empty
            val combined = (appList + demoPackages).distinctBy { it.packageName }.sortedBy { it.appName }
            _systemAppsList.value = combined
        }
    }

    private fun seedInitialDemoFilesIfNeeded() {
        viewModelScope.launch {
            // Check if active files are empty on first launch
            repository.activeVaultFiles.first().let { currentList ->
                if (currentList.isEmpty()) {
                    // Seed standard dummy files
                    repository.seedDemoFile("Tax_Statement_2026.pdf", "DOCUMENT", "SECURE DATA // SecureShield Cryto Payload // Confidential tax declarations to keep shielded.")
                    repository.seedDemoFile("Selfie_Protected.png", "IMAGE", "SIMULATED IMAGE BYTES // SecureShield encrypted picture payload for private photo.")
                    repository.seedDemoFile("Private_Vlog.mp4", "VIDEO", "SIMULATED VIDEO CONTAINER BYTES // SecureShield encrypted visual stream of secret memories.")
                    repository.seedDemoFile("Wallet_Back_Phrases.txt", "DOCUMENT", "BIP-39 mnemonic wallet index: secure shield alpha omega laser core lock safety.")
                    repository.seedDemoFile("Secret_Recording.mp3", "AUDIO", "SIMULATED AUDIO RECORDING CONTAINER // Recorded private speech.")
                }
            }
        }
    }

    // --- App Locks Actions ---
    fun toggleAppLock(packageName: String, appName: String, isCurrentlyLocked: Boolean) {
        viewModelScope.launch {
            if (isCurrentlyLocked) {
                repository.unlockApp(packageName)
            } else {
                repository.lockApp(packageName, appName)
            }
        }
    }

    // --- File Encryption Actions ---
    fun importLocalFile(file: File, type: String, deleteOriginal: Boolean = true) {
        viewModelScope.launch {
            repository.importFile(file, type, deleteOriginal)
        }
    }

    fun importSimulatedAsset(name: String, type: String, textContent: String) {
        viewModelScope.launch {
            repository.seedDemoFile(name, type, textContent)
        }
    }

    fun decryptAndRestore(id: Int) {
        viewModelScope.launch {
            repository.exportAndRestoreFile(id)
        }
    }

    fun recycleVaultFile(id: Int) {
        viewModelScope.launch {
            repository.recycleFile(id)
        }
    }

    fun restoreFileFromTrash(id: Int) {
        viewModelScope.launch {
            repository.restoreFromRecycleBin(id)
        }
    }

    fun permanentlyPurgeFile(id: Int) {
        viewModelScope.launch {
            repository.permanentlyDeleteFile(id)
        }
    }

    // --- Settings Actions ---
    fun updateAuthenticationPIN(newPin: String) {
        viewModelScope.launch {
            repository.setPIN(newPin)
            _currentAuthType.value = "PIN"
        }
    }

    fun updateAuthenticationPattern(newPattern: String) {
        viewModelScope.launch {
            repository.setPattern(newPattern)
            _currentAuthType.value = "PATTERN"
        }
    }

    fun toggleBiometrics(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBiometricEnabled(enabled)
            _isBiometricEnabled.value = enabled
        }
    }

    fun clearSecurityAuditTrail() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }
}

data class AppItem(
    val packageName: String,
    val appName: String
)
