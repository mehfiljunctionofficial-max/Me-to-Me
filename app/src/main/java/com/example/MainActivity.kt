package com.example

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.LockedApp
import com.example.data.model.VaultFile
import com.example.service.AppLockerAccessibilityService
import com.example.LockPatternGrid
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppItem
import com.example.ui.viewmodel.MainViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF0B0F19) // Custom super dark canvas space
                ) { innerPadding ->
                    MainNavigationHub(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Simple helper block to refresh state when user returns from settings toggle
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigationHub(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel()

    var currentScreen by remember { mutableStateOf("VAULT") } // Screen states: "VAULT", "APPS", "TRASH", "SETTINGS"
    var isAccessibilityServiceConnected by remember { mutableStateOf(false) }
    var isSentinelRunning by remember { mutableStateOf(false) }

    // Periodically verify checker states
    LaunchedEffect(currentScreen) {
        isAccessibilityServiceConnected = AppLockerAccessibilityService.isServiceRunning
        isSentinelRunning = com.example.service.AppCheckService.isServiceRunning
        viewModel.loadSettingsAndApps()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Shield Logo",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "SecureShield",
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 0.5.sp,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    val isProtectionActive = isAccessibilityServiceConnected || isSentinelRunning
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isProtectionActive) Color(0xFF047857).copy(alpha = 0.3f)
                                else Color(0xFFB91C1C).copy(alpha = 0.2f)
                            )
                            .border(
                                1.dp,
                                if (isProtectionActive) Color(0xFF10B981) else Color(0xFFEF4444),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isProtectionActive) "SHIELD ACTIVE" else "SHIELD OFF",
                            color = if (isProtectionActive) Color(0xFF34D399) else Color(0xFFFCA5A5),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                tonalElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                NavigationBarItem(
                    selected = currentScreen == "VAULT",
                    onClick = { currentScreen = "VAULT" },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Vault Files") },
                    label = { Text("Files Vault") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF10B981),
                        selectedTextColor = Color(0xFF10B981),
                        indicatorColor = Color(0xFF1E293B),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    )
                )
                NavigationBarItem(
                    selected = currentScreen == "APPS",
                    onClick = { currentScreen = "APPS" },
                    icon = { Icon(Icons.Default.Lock, contentDescription = "App Lockers") },
                    label = { Text("App Lock") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF10B981),
                        selectedTextColor = Color(0xFF10B981),
                        indicatorColor = Color(0xFF1E293B),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    )
                )
                NavigationBarItem(
                    selected = currentScreen == "TRASH",
                    onClick = { currentScreen = "TRASH" },
                    icon = { Icon(Icons.Default.Delete, contentDescription = "Trash Recovery") },
                    label = { Text("Trash Bin") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF10B981),
                        selectedTextColor = Color(0xFF10B981),
                        indicatorColor = Color(0xFF1E293B),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    )
                )
                NavigationBarItem(
                    selected = currentScreen == "SETTINGS",
                    onClick = { currentScreen = "SETTINGS" },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Security Dashboard") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF10B981),
                        selectedTextColor = Color(0xFF10B981),
                        indicatorColor = Color(0xFF1E293B),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    )
                )
            }
        }
    ) { padValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padValues)
                .background(Color(0xFF0B0F19))
        ) {
            when (currentScreen) {
                "VAULT" -> VaultLayout(viewModel = viewModel)
                "APPS" -> AppLockWarningController(viewModel = viewModel, isConnected = isAccessibilityServiceConnected)
                "TRASH" -> TrashRecoveryLayout(viewModel = viewModel)
                "SETTINGS" -> SecuritySettingsLayout(viewModel = viewModel)
            }
        }
    }
}

// ==================== SCREEN 1: FILE VAULT ====================
@Composable
fun VaultLayout(viewModel: MainViewModel) {
    val activeFiles by viewModel.activeVaultFiles.collectAsState()
    val context = LocalContext.current

    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "IMAGE", "VIDEO", "AUDIO", "DOCUMENT"
    var showAddDialog by remember { mutableStateOf(false) }

    // Filter file list reactively
    val filteredFiles = remember(activeFiles, selectedFilter) {
        if (selectedFilter == "ALL") {
            activeFiles
        } else {
            activeFiles.filter { it.fileType == selectedFilter }
        }
    }

    // Standard local system file selector launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            try {
                // Copy stream to temporary file then import of cryptographic algorithm
                val tempFile = File(context.cacheDir, "picked_shield_${System.currentTimeMillis()}")
                context.contentResolver.openInputStream(fileUri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Inspect type
                val mimeType = context.contentResolver.getType(fileUri) ?: ""
                val type = when {
                    mimeType.startsWith("image/") -> "IMAGE"
                    mimeType.startsWith("video/") -> "VIDEO"
                    mimeType.startsWith("audio/") -> "AUDIO"
                    else -> "DOCUMENT"
                }
                
                viewModel.importLocalFile(tempFile, type, deleteOriginal = true)
                Toast.makeText(context, "Encrypted and Imported Successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Encryption Picker Error: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Category Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    Triple("ALL", Icons.Default.Folder, "All"),
                    Triple("IMAGE", Icons.Default.Folder, "Images"),
                    Triple("VIDEO", Icons.Default.Folder, "Videos"),
                    Triple("DOCUMENT", Icons.Default.Folder, "Docs")
                )

                filters.forEach { (filterType, _, label) ->
                    val isSelected = selectedFilter == filterType
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF10B981) else Color(0xFF1E293B))
                            .clickable { selectedFilter = filterType }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Warning and Intro Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.5f)),
                border = BoxBorderDefaults.cardBorder()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info icon",
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Any imported files are encrypted with hardware AES-256 and removed from your public gallery instantly.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 16.sp
                    )
                }
            }

            // Encrypted Items List
            if (filteredFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Empty Folder",
                            tint = Color(0xFF334155),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Encrypted Files Found",
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap the '+' button below to secure your first file.",
                            color = Color(0xFF475569),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredFiles) { vaultFile ->
                        VaultFileGridItem(
                            file = vaultFile,
                            onRestore = { viewModel.decryptAndRestore(vaultFile.id) },
                            onTrash = { viewModel.recycleVaultFile(vaultFile.id) }
                        )
                    }
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("fab_import_file"),
            onClick = { showAddDialog = true },
            containerColor = Color(0xFF10B981),
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add Content Option Selection", modifier = Modifier.size(26.dp))
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Secure New Document") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "How would you like to secure your files in SecureShield?",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8)
                        )
                        
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            onClick = {
                                showAddDialog = false
                                filePickerLauncher.launch("*/*")
                            }
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = "picker button")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Local File System Object")
                        }

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            onClick = {
                                showAddDialog = false
                                // Create a demo dynamic simulated asset
                                val names = listOf("Bank_Card_Secret.txt", "Passport_Scan_Safe.png", "Home_WiFi_Passphrase.txt", "Bitcoin_Alpha_Key.txt")
                                val chosenName = names.random()
                                val type = if (chosenName.endsWith(".png")) "IMAGE" else "DOCUMENT"
                                viewModel.importSimulatedAsset(
                                    name = chosenName,
                                    type = type,
                                    textContent = "SECURESHIELD CRYPTOGRAPHIC DATA // Created at: ${Date()}"
                                )
                                Toast.makeText(context, "Encrypted Mock file: $chosenName seeded!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = "seeder button")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Simulate Sandbox Shield Seed")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
                },
                containerColor = Color(0xFF1E293B),
                titleContentColor = Color.White,
                textContentColor = Color.White
            )
        }
    }
}

@Composable
fun VaultFileGridItem(
    file: VaultFile,
    onRestore: () -> Unit,
    onTrash: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    val icon = when (file.fileType) {
        "IMAGE" -> Icons.Default.Folder
        "VIDEO" -> Icons.Default.Folder
        "AUDIO" -> Icons.Default.Folder
        else -> Icons.Default.Folder
    }

    val glowColor = when (file.fileType) {
        "IMAGE" -> Color(0xFF10B981)
        "VIDEO" -> Color(0xFF3B82F6)
        "AUDIO" -> Color(0xFFA855F7)
        else -> Color(0xFFE2E8F0)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF2D3748), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(glowColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "File indicator category icon",
                        tint = glowColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { expandedMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu controls selector",
                            tint = Color(0xFF94A3B8)
                        )
                    }

                    DropdownMenu(
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Decrypt & Restore", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = "restore icon", tint = Color(0xFF10B981)) },
                            onClick = {
                                expandedMenu = false
                                onRestore()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("View Encrypted Meta", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = "info icon", tint = Color(0xFF3B82F6)) },
                            onClick = {
                                expandedMenu = false
                                showDetailsDialog = true
                            }
                        )
                        Divider(color = Color(0xFF334155))
                        DropdownMenuItem(
                            text = { Text("Move to Trash", color = Color(0xFFEF4444)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "trash icon", tint = Color(0xFFEF4444)) },
                            onClick = {
                                expandedMenu = false
                                onTrash()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = file.fileName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${file.fileSize / 1024} KB",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )

                Text(
                    text = file.fileType,
                    fontSize = 9.sp,
                    color = glowColor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(glowColor.copy(alpha = 0.1f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }

    if (showDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text("Encrypted Payload Data") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Original Location: \n${file.originalPath}", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Text("Secure SecureStorage Path: \n${file.encryptedPath}", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Text("Hardware Key: SECURESHIELD_AES_256_GCM", fontSize = 12.sp, color = Color(0xFF10B981), fontFamily = FontFamily.Monospace)
                    Text("Cipher IV Vector: \n${file.encryptionIv}", fontSize = 11.sp, color = Color(0xFF94A3B8), fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) { Text("OK") }
            },
            containerColor = Color(0xFF1E293B),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}


// ==================== SCREEN 2: APP LOCKER WARNING INTERFACE ====================
fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager ?: return false
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    }
    return mode == android.app.AppOpsManager.MODE_ALLOWED
}

@Composable
fun AppLockWarningController(viewModel: MainViewModel, isConnected: Boolean) {
    val context = LocalContext.current
    var hasUsageStats by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var hasOverlayPerm by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isSentinelRunning by remember { mutableStateOf(com.example.service.AppCheckService.isServiceRunning) }

    // Periodically sync permissions on resume / interaction
    LaunchedEffect(Unit) {
        while (true) {
            hasUsageStats = hasUsageStatsPermission(context)
            hasOverlayPerm = Settings.canDrawOverlays(context)
            isSentinelRunning = com.example.service.AppCheckService.isServiceRunning
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Sentinel Status Board card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = BoxBorderDefaults.cardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SENTINEL PROTECTION COMPASS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981),
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Permission item 1: Draw overlays
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (hasOverlayPerm) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = "Status",
                            tint = if (hasOverlayPerm) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Draw Above Apps (Overlays)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Needed to render passcode interface", color = Color(0xFF64748B), fontSize = 11.sp)
                        }
                    }
                    if (!hasOverlayPerm) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    context.startActivity(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("GRANT", fontSize = 11.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("GRANTED", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Permission item 2: Usage statistics
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (hasUsageStats) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = "Status",
                            tint = if (hasUsageStats) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Usage Statistics access", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Needed to detect app switches", color = Color(0xFF64748B), fontSize = 11.sp)
                        }
                    }
                    if (!hasUsageStats) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("GRANT", fontSize = 11.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("GRANTED", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(12.dp))

                // Service Controls Loop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Persistent Sentinel Guard", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (isSentinelRunning) "Active Foreground Protection on" else "Protection offline (standby)",
                            color = if (isSentinelRunning) Color(0xFF34D399) else Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = isSentinelRunning,
                        onCheckedChange = { start ->
                            if (start) {
                                if (hasUsageStats && hasOverlayPerm) {
                                    com.example.service.AppCheckService.startService(context)
                                    isSentinelRunning = true
                                } else {
                                    Toast.makeText(context, "Please grant the necessary usage/overlay rules first!", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                com.example.service.AppCheckService.stopService(context)
                                isSentinelRunning = false
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF10B981)
                        )
                    )
                }
            }
        }

        // Accessibility warning fallback notice
        if (!isConnected && !isSentinelRunning) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFF2E2E).copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alert",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Both Protection Sentinel and Accessibility are inactive. Secure locks will not lock automatically. Please toggle 'Persistent Sentinel Guard' above.",
                        color = Color(0xFFFCA5A5),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Render App List
        Box(modifier = Modifier.weight(1f)) {
            AppListLayout(viewModel = viewModel)
        }
    }
}

@Composable
fun AppListLayout(viewModel: MainViewModel) {
    val dbLockedApps by viewModel.lockedAppsFromDb.collectAsState()
    val appsList by viewModel.systemAppsList.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(appsList, searchQuery) {
        if (searchQuery.trim().isEmpty()) {
            appsList
        } else {
            appsList.filter { it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Application Shielding",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Select components details to lock. Secured targets request verification on start.",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Search text interface
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            placeholder = { Text("Search installed packages...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search bar") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredApps) { app ->
                val isLocked = dbLockedApps.any { it.packageName == app.packageName }
                AppItemCard(
                    app = app,
                    isLocked = isLocked,
                    onToggle = { viewModel.toggleAppLock(app.packageName, app.appName, isLocked) }
                )
            }
        }
    }
}

@Composable
fun AppItemCard(
    app: AppItem,
    isLocked: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isLocked) Color(0xFF10B981).copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isLocked) Color(0xFF10B981).copy(alpha = 0.05f) else Color(0xFF1E293B)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.Shield,
                        contentDescription = "Lock element status illustration item",
                        tint = if (isLocked) Color(0xFF10B981) else Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = app.appName,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = app.packageName,
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Switch(
                checked = isLocked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF10B981),
                    uncheckedThumbColor = Color(0xFF94A3B8),
                    uncheckedTrackColor = Color(0xFF334155)
                )
            )
        }
    }
}


// ==================== SCREEN 3: TRASH / RECYCLE BIN RECOVERY ====================
@Composable
fun TrashRecoveryLayout(viewModel: MainViewModel) {
    val trashedFiles by viewModel.deletedVaultFiles.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Secure Recycle Bin",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Trashed assets are preserved safely as cryptokeys, allowing temporary backups to avert accidents. Recovery restores them easily.",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (trashedFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Empty trash symbol",
                        tint = Color(0xFF334155),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Recycle Bin is Empty",
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(trashedFiles) { trashFile ->
                    TrashedFileCard(
                        file = trashFile,
                        onRecover = { viewModel.restoreFileFromTrash(trashFile.id) },
                        onPurge = { viewModel.permanentlyPurgeFile(trashFile.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun TrashedFileCard(
    file: VaultFile,
    onRecover: () -> Unit,
    onPurge: () -> Unit
) {
    var showPurgeConfirmation by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Type: ${file.fileType} • Size: ${file.fileSize / 1024} KB",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF047857).copy(alpha = 0.2f))
                        .clickable { onRecover() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Recover selected file",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFB91C1C).copy(alpha = 0.15f))
                        .clickable { showPurgeConfirmation = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Permanently shred file",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showPurgeConfirmation) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmation = false },
            title = { Text("Military-Grade Shredding") },
            text = {
                Text(
                    "WARNING: This action is irreversible. The encrypted file '${file.fileName}' will be physically shredded from the storage, and cannot be recovered even by SecureShield's specialists.",
                    color = Color(0xFFEF4444)
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    onClick = {
                        showPurgeConfirmation = false
                        onPurge()
                    }
                ) {
                    Text("Permanently Purge")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmation = false }) { Text("Cancel") }
            },
            containerColor = Color(0xFF1E293B),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}


// ==================== SCREEN 4: SETTINGS & MONITOR PORTAL ====================
@Composable
fun SecuritySettingsLayout(viewModel: MainViewModel) {
    val currentType by viewModel.currentAuthType.collectAsState()
    val isBioEnabled by viewModel.isBiometricEnabled.collectAsState()
    val auditTrailLogs by viewModel.securityLogs.collectAsState()
    val context = LocalContext.current

    var showPasswordReset by remember { mutableStateOf(false) }
    var securityInputType by remember { mutableStateOf("PIN") } // Current temporary dialog setup flow: "PIN", "PATTERN"
    var tempPINState by remember { mutableStateOf("") }
    var tempPatternState by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Security Control Panel",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Authentication Methods Configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BoxBorderDefaults.cardBorder(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Shield Key Lock Options",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val intent = Intent(context, CreateLockActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("setup_wizard_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Shield Settings",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Setup Auth Methods (PIN/Pattern/Biometric)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Default Authentication Protocol", color = Color.White, fontSize = 13.sp)
                        Text(
                            text = "Currently secured by: $currentType",
                            color = Color(0xFF10B981),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row {
                        Button(
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentType == "PIN") Color(0xFF10B981) else Color(0xFF1E293B)
                            ),
                            onClick = {
                                securityInputType = "PIN"
                                showPasswordReset = true
                            }
                        ) {
                            Text("PIN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentType == "PATTERN") Color(0xFF10B981) else Color(0xFF1E293B)
                            ),
                            onClick = {
                                securityInputType = "PATTERN"
                                showPasswordReset = true
                            }
                        ) {
                            Text("Pattern", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Integrate System Biometrics", color = Color.White, fontSize = 13.sp)
                        Text("Allow Face/Fingerprint verification overlay", color = Color(0xFF64748B), fontSize = 11.sp)
                    }

                    Switch(
                        checked = isBioEnabled,
                        onCheckedChange = { viewModel.toggleBiometrics(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF10B981)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Security logs console
        Text(
            text = "Active Security Audit Trail",
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = BoxBorderDefaults.cardBorder()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONSOLE SYSTEM LOG",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "CLEAR LOGS",
                        fontSize = 11.sp,
                        color = Color(0xFFEF4444),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { viewModel.clearSecurityAuditTrail() }
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Divider(color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(6.dp))

                if (auditTrailLogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "> No logs filed.",
                            color = Color(0xFF475569),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    val sdf = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(auditTrailLogs) { log ->
                            val timestamp = remember(log.timestamp) { sdf.format(Date(log.timestamp)) }
                            Text(
                                text = "[$timestamp] ${if (log.isSuccess) "✔" else "✖"} ${log.message}",
                                color = if (log.isSuccess) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPasswordReset) {
        AlertDialog(
            onDismissRequest = {
                tempPINState = ""
                tempPatternState = ""
                showPasswordReset = false
            },
            title = { Text(if (securityInputType == "PIN") "Set 4-Digit Security PIN" else "Draw Unlock Pattern") },
            text = {
                if (securityInputType == "PIN") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Input your secure numerical block sequence:", fontSize = 13.sp, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = tempPINState.padEnd(4, '•'),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF10B981),
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 12.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Mini numeric pad inside dialog
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("1", "2", "3", "4").forEach { digit ->
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    onClick = { if (tempPINState.length < 4) tempPINState += digit }
                                ) { Text(digit) }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("5", "6", "7", "8").forEach { digit ->
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    onClick = { if (tempPINState.length < 4) tempPINState += digit }
                                ) { Text(digit) }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("9", "0", "C").forEach { control ->
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    onClick = {
                                        if (control == "C") tempPINState = ""
                                        else if (tempPINState.length < 4) tempPINState += control
                                    }
                                ) { Text(control) }
                            }
                        }
                    }
                } else {
                    // Pattern Drawer Inside Dialog
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Connect 4 or more dots to define your protective shield path:", fontSize = 13.sp, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (tempPatternState.isNotEmpty()) {
                            Text(
                                text = "Recorded Path: $tempPatternState",
                                color = Color(0xFF10B981),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Text(
                                text = "Draw your sequence below (at least 4 dots)",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Box(modifier = Modifier.size(240.dp)) {
                            LockPatternGrid(
                                expectedPattern = "Any", // Custom recording adapter Mode
                                onSuccess = { recordedPath ->
                                    tempPatternState = recordedPath
                                    Toast.makeText(context, "Path Recorded!", Toast.LENGTH_SHORT).show()
                                },
                                onFailure = {
                                    Toast.makeText(context, "Pattern too short! Connect >= 4 dots.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        Text(
                            text = "Simply draw directly on the canvas to record coordinates.",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                if (securityInputType == "PIN") {
                    TextButton(
                        onClick = {
                            if (tempPINState.length == 4) {
                                viewModel.updateAuthenticationPIN(tempPINState)
                                tempPINState = ""
                                showPasswordReset = false
                                Toast.makeText(context, "Shield PIN Saved Successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "PIN must be exactly 4 digits!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) { Text("Save Key") }
                } else {
                    TextButton(
                        onClick = {
                            if (tempPatternState.isNotEmpty()) {
                                viewModel.updateAuthenticationPattern(tempPatternState)
                                tempPatternState = ""
                                showPasswordReset = false
                                Toast.makeText(context, "Shield Pattern Saved Successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Please draw a valid pattern (min 4 dots) first!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) { Text("Save Key") }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        tempPINState = ""
                        tempPatternState = ""
                        showPasswordReset = false
                    }
                ) { Text("Cancel") }
            },
            containerColor = Color(0xFF1E293B),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}

// ==================== DESIGN BORDER SYSTEM DEFINITIONS ====================
object BoxBorderDefaults {
    fun cardBorder(): androidx.compose.foundation.BorderStroke = androidx.compose.foundation.BorderStroke(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF334155),
                Color(0xFF1E293B)
            )
        )
    )
}
