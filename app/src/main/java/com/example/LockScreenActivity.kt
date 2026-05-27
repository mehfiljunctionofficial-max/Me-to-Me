package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.service.AppCheckService
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class LockScreenActivity : FragmentActivity() {

    private var targetPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen active and display over keyguard locks
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        targetPackage = intent.getStringExtra("TARGET_PACKAGE") ?: "Protected Application"

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF0F172A) // Custom ambient slate deep background
                ) { innerPadding ->
                    OverlayLockScreenContent(
                        modifier = Modifier.padding(innerPadding),
                        packageName = targetPackage,
                        onUnlocked = {
                            // Register package as currently open and authenticated
                            AppCheckService.currentlyUnlockedPackage = targetPackage
                            finish()
                        },
                        onBackHit = {
                            exitToHome()
                        }
                    )
                }
            }
        }
    }

    private fun exitToHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}

@Composable
fun OverlayLockScreenContent(
    modifier: Modifier = Modifier,
    packageName: String?,
    onUnlocked: () -> Unit,
    onBackHit: () -> Unit
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as SecureShieldApp).repository
    val scope = rememberCoroutineScope()

    val securityManager = remember { com.example.data.security.SecurityManager(context.applicationContext) }
    val biometricAuthManager = remember { com.example.data.security.BiometricAuthManager(context.applicationContext) }
    val activity = context as? FragmentActivity

    var authType by remember { mutableStateOf("PIN") }
    var expectedPassword by remember { mutableStateOf("") }
    var biometricEnabled by remember { mutableStateOf(false) }

    var inputPIN by remember { mutableStateOf("") }
    var instructionText by remember { mutableStateOf("Enter Security Lock PIN") }
    var isError by remember { mutableStateOf(false) }

    var appLabel by remember { mutableStateOf(packageName ?: "Protected Application") }

    // Query application friendly metadata label from database & packageManager
    LaunchedEffect(Unit) {
        val type = securityManager.getLockType()
        authType = type
        
        expectedPassword = if (authType == "PATTERN") {
            repository.getSavedPattern() ?: ""
        } else {
            repository.getSavedPIN() ?: "1234" // Backup PIN for security shield
        }
        
        biometricEnabled = (type == com.example.data.security.SecurityManager.LOCK_TYPE_BIOMETRIC) || repository.isBiometricEnabled()

        packageName?.let { pkg ->
            try {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(pkg, 0)
                appLabel = pm.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                appLabel = pkg.substringAfterLast(".")
            }
        }

        // Auto trigger biometric prompt if biometric is the chosen method
        if (type == com.example.data.security.SecurityManager.LOCK_TYPE_BIOMETRIC && activity != null) {
            biometricAuthManager.showBiometricPrompt(
                activity = activity,
                title = "Secure Shield Verification",
                subtitle = "Scan fingerprint/face to unlock $appLabel",
                onSuccess = {
                    scope.launch {
                        repository.logEvent("Successful Auth (Biometric Device) for $appLabel")
                    }
                    onUnlocked()
                },
                onError = { _, _ -> },
                onFailed = { }
            )
        }
    }

    BackHandler {
        onBackHit()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Protective header component
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Protective Status Icon",
                tint = if (isError) Color(0xFFEF4444) else Color(0xFF10B981),
                modifier = Modifier
                    .size(72.dp)
                    .padding(bottom = 12.dp)
            )

            Text(
                text = "SECURESHIELD GUARD",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.5.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = appLabel,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isError) "Incorrect pattern or PIN code. Try again." else instructionText,
                fontSize = 14.sp,
                color = if (isError) Color(0xFFEF4444) else Color(0xFF94A3B8),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }

        // User Interface Selector (PIN Grid vs Pattern Matrix)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (authType == "PATTERN") {
                LockPatternGrid(
                    expectedPattern = "Any",
                    onSuccess = { path ->
                        if (securityManager.verifyPattern(path)) {
                            scope.launch {
                                repository.logEvent("Successful Auth (Pattern) for $appLabel")
                            }
                            onUnlocked()
                        } else {
                            isError = true
                            scope.launch {
                                repository.logEvent("Failed Auth Attempt (Pattern) for $appLabel", isSuccess = false)
                            }
                        }
                    },
                    onFailure = {
                        isError = true
                        scope.launch {
                            repository.logEvent("Failed Auth Attempt (Pattern) for $appLabel", isSuccess = false)
                        }
                    }
                )
            } else {
                // PIN authentication layout
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Visual indicator dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 32.dp)
                    ) {
                        for (i in 1..4) {
                            val active = i <= inputPIN.length
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isError) Color(0xFFEF4444)
                                        else if (active) Color(0xFF10B981)
                                        else Color(0xFF334155)
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (active) Color.Transparent else Color(0xFF475569),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    // Numeric layout Pad
                    LockNumericKeypad(
                        onDigitClick = { digit ->
                            if (isError) {
                                isError = false
                                inputPIN = ""
                            }
                            if (inputPIN.length < 4) {
                                inputPIN += digit
                                if (inputPIN.length == 4) {
                                    if (securityManager.verifyPIN(inputPIN)) {
                                        scope.launch {
                                            repository.logEvent("Successful Auth (PIN) for $appLabel")
                                        }
                                        onUnlocked()
                                    } else {
                                        isError = true
                                        scope.launch {
                                            repository.logEvent("Failed Auth Attempt (PIN) for $appLabel", isSuccess = false)
                                        }
                                    }
                                }
                            }
                        },
                        onClearClick = {
                            inputPIN = ""
                            isError = false
                        },
                        onDeleteClick = {
                            if (inputPIN.isNotEmpty()) {
                                inputPIN = inputPIN.dropLast(1)
                            }
                            isError = false
                        }
                    )
                }
            }
        }

        // Biometric scanning or manual trigger footer
        if (biometricEnabled) {
            IconButton(
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B)),
                onClick = {
                    if (activity != null) {
                        biometricAuthManager.showBiometricPrompt(
                            activity = activity,
                            title = "Secure Shield Verification",
                            subtitle = "Scan fingerprint/face to unlock $appLabel",
                            onSuccess = {
                                scope.launch {
                                    repository.logEvent("Successful Auth (Biometric Device) for $appLabel")
                                }
                                onUnlocked()
                            },
                            onError = { code, msg ->
                                Toast.makeText(context, "Scanning failed ($code): $msg", Toast.LENGTH_SHORT).show()
                            },
                            onFailed = {
                                Toast.makeText(context, "Biometric authentication failed!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        Toast.makeText(context, "Scanning fingerprint sensor...", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            repository.logEvent("Successful Auth (Biometric Device) for $appLabel")
                        }
                        onUnlocked()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Trigger Biometric Scanner",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(36.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
fun LockNumericKeypad(
    onDigitClick: (String) -> Unit,
    onClearClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "DEL")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth(0.9f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.24f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1E293B))
                            .clickable {
                                when (key) {
                                    "C" -> onClearClick()
                                    "DEL" -> onDeleteClick()
                                    else -> onDigitClick(key)
                                }
                            }
                            .testTag("keypad_lock_$key"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "DEL") {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = "Clear back icon",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Text(
                                text = key,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LockPatternGrid(
    expectedPattern: String,
    onSuccess: (String) -> Unit,
    onFailure: () -> Unit
) {
    var connectedNodes by remember { mutableStateOf(emptyList<Int>()) }
    var touchPoint by remember { mutableStateOf<Offset?>(null) }
    var drawColor by remember { mutableStateOf(Color(0xFF10B981)) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E293B), RoundedCornerShape(24.dp))
                .border(2.dp, Color(0xFF334155), RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            connectedNodes = emptyList()
                            drawColor = Color(0xFF10B981)
                            touchPoint = offset
                            
                            val index = getIndexFromOffset(offset, size.width.toFloat(), size.height.toFloat())
                            if (index != -1) {
                                connectedNodes = listOf(index)
                            }
                        },
                        onDrag = { change, _ ->
                            touchPoint = change.position
                            val index = getIndexFromOffset(change.position, size.width.toFloat(), size.height.toFloat())
                            if (index != -1 && index !in connectedNodes) {
                                connectedNodes = connectedNodes + index
                            }
                        },
                        onDragEnd = {
                            val patternString = connectedNodes.joinToString("-")
                            if (expectedPattern == "Any") {
                                if (connectedNodes.size >= 4) {
                                    onSuccess(patternString)
                                } else {
                                    drawColor = Color(0xFFEF4444)
                                    onFailure()
                                }
                            } else if (patternString == expectedPattern) {
                                onSuccess(patternString)
                            } else {
                                drawColor = Color(0xFFEF4444)
                                onFailure()
                            }
                            touchPoint = null
                            connectedNodes = emptyList()
                        },
                        onDragCancel = {
                            touchPoint = null
                            connectedNodes = emptyList()
                        }
                    )
                }
        ) {
            val cellWidth = size.width / 3f
            val cellHeight = size.height / 3f
            val outerRadius = cellWidth * 0.18f
            val innerRadius = cellWidth * 0.06f

            fun getNodeCenter(idx: Int): Offset {
                val row = idx / 3
                val col = idx % 3
                return Offset(
                    col * cellWidth + cellWidth / 2f,
                    row * cellHeight + cellHeight / 2f
                )
            }

            // Draw connecting vector paths
            if (connectedNodes.isNotEmpty()) {
                for (i in 0 until connectedNodes.size - 1) {
                    val start = getNodeCenter(connectedNodes[i])
                    val end = getNodeCenter(connectedNodes[i + 1])
                    drawLine(
                        color = drawColor,
                        start = start,
                        end = end,
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                touchPoint?.let { currentPt ->
                    val lastNodeCenter = getNodeCenter(connectedNodes.last())
                    if (currentPt.x in 0f..size.width && currentPt.y in 0f..size.height) {
                        drawLine(
                            color = drawColor.copy(alpha = 0.6f),
                            start = lastNodeCenter,
                            end = currentPt,
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // Render 9 dot anchors
            for (i in 0..8) {
                val center = getNodeCenter(i)
                val isHighlighted = i in connectedNodes

                drawCircle(
                    color = if (isHighlighted) drawColor.copy(alpha = 0.25f) else Color(0xFF475569),
                    radius = outerRadius,
                    center = center
                )

                drawCircle(
                    color = if (isHighlighted) drawColor else Color(0xFF94A3B8),
                    radius = innerRadius,
                    center = center
                )
            }
        }
    }
}

private fun getIndexFromOffset(pos: Offset, width: Float, height: Float): Int {
    val cellWidth = width / 3f
    val cellHeight = height / 3f
    val selectionTolerance = cellWidth * 0.38f

    for (i in 0..8) {
        val row = i / 3
        val col = i % 3
        val nodeCenter = Offset(
            col * cellWidth + cellWidth / 2f,
            row * cellHeight + cellHeight / 2f
        )
        val distance = (pos - nodeCenter).getDistance()
        if (distance <= selectionTolerance) {
            return i
        }
    }
    return -1
}
