package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.data.security.BiometricAuthManager
import com.example.data.security.SecurityManager
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class CreateLockActivity : FragmentActivity() {

    private lateinit var securityManager: SecurityManager
    private lateinit var biometricAuthManager: BiometricAuthManager
    private val activityScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.Job())

    override fun onDestroy() {
        super.onDestroy()
        activityScope.launch {
            // Cancel/cleanup if necessary
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        securityManager = SecurityManager(applicationContext)
        biometricAuthManager = BiometricAuthManager(applicationContext)

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF0F1728) // Cosmic slate deep black/blue background
                ) { innerPadding ->
                    SetupUI_Layout(
                        modifier = Modifier.padding(innerPadding),
                        securityManager = securityManager,
                        biometricAuthManager = biometricAuthManager,
                        onCompleted = {
                            // Sync Room database's configuration if appropriate
                            val selectedType = securityManager.getLockType()
                            lifecycleScopeLaunch {
                                try {
                                    val repo = (applicationContext as SecureShieldApp).repository
                                    if (selectedType == SecurityManager.LOCK_TYPE_PIN) {
                                        repo.setPIN(securityManager.hashString("DUMMY")) // Just as room backup signal
                                    } else if (selectedType == SecurityManager.LOCK_TYPE_PATTERN) {
                                        repo.setPattern(securityManager.hashString("DUMMY"))
                                    }
                                    repo.setBiometricEnabled(selectedType == SecurityManager.LOCK_TYPE_BIOMETRIC)
                                    repo.logEvent("Shield setup completed with: $selectedType")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            setResult(RESULT_OK)
                            finish()
                        },
                        onCancelled = {
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun lifecycleScopeLaunch(block: suspend () -> Unit) {
        // Run database calls in scope safely
        activityScope.launch {
            block()
        }
    }
}

@Composable
fun SetupUI_Layout(
    modifier: Modifier = Modifier,
    securityManager: SecurityManager,
    biometricAuthManager: BiometricAuthManager,
    onCompleted: () -> Unit,
    onCancelled: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()

    var activeLockType by remember { mutableStateOf(securityManager.getLockType()) }
    
    // PIN states
    var firstPinInput by remember { mutableStateOf("") }
    var secondPinInput by remember { mutableStateOf("") }
    var pinStage by remember { mutableStateOf(1) } // 1 = First input, 2 = Confirm input
    var pinInstruction by remember { mutableStateOf("Choose a new 4-digit security PIN") }

    // Pattern states
    var firstPatternInput by remember { mutableStateOf("") }
    var secondPatternInput by remember { mutableStateOf("") }
    var patternStage by remember { mutableStateOf(1) } // 1 = First path, 2 = Confirm path
    var patternInstruction by remember { mutableStateOf("Draw a pattern (connect min 4 dots)") }

    BackHandler {
        onCancelled()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top static header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(
                    onClick = onCancelled,
                    modifier = Modifier.testTag("setup_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Shield Crypt Setup",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Secure your protected applications from unauthorized intrusion",
                color = Color(0xFF64748B),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        // Mid section selector tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LockTypeSelectorCard(
                label = "PIN",
                icon = Icons.Default.Password,
                isSelected = activeLockType == SecurityManager.LOCK_TYPE_PIN,
                modifier = Modifier.weight(1f),
                onClick = {
                    activeLockType = SecurityManager.LOCK_TYPE_PIN
                    firstPinInput = ""
                    secondPinInput = ""
                    pinStage = 1
                    pinInstruction = "Choose a new 4-digit security PIN"
                }
            )

            LockTypeSelectorCard(
                label = "Pattern",
                icon = Icons.Default.Gesture,
                isSelected = activeLockType == SecurityManager.LOCK_TYPE_PATTERN,
                modifier = Modifier.weight(1f),
                onClick = {
                    activeLockType = SecurityManager.LOCK_TYPE_PATTERN
                    firstPatternInput = ""
                    secondPatternInput = ""
                    patternStage = 1
                    patternInstruction = "Draw a pattern (connect min 4 dots)"
                }
            )

            LockTypeSelectorCard(
                label = "Biometric",
                icon = Icons.Default.Fingerprint,
                isSelected = activeLockType == SecurityManager.LOCK_TYPE_BIOMETRIC,
                modifier = Modifier.weight(1f),
                onClick = {
                    val availability = biometricAuthManager.checkBiometricAvailability()
                    if (biometricAuthManager.isBiometricReady()) {
                        activeLockType = SecurityManager.LOCK_TYPE_BIOMETRIC
                    } else {
                        val errorStr = when (availability) {
                            BiometricAuthManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No fingerprints registered on device settings. Configure device biometrics first!"
                            BiometricAuthManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Biometric hardware is missing on this device."
                            else -> "Biometrics sensor unavailable or disabled."
                        }
                        Toast.makeText(context, errorStr, Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        // Active layout card
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            when (activeLockType) {
                SecurityManager.LOCK_TYPE_PIN -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (pinStage == 1) "STAGE 1: DEFINE KEY" else "STAGE 2: CONFIRM KEY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = pinInstruction,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            // Custom animated indicator circles
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val currentLen = if (pinStage == 1) firstPinInput.length else secondPinInput.length
                                for (i in 0..3) {
                                    val isFilled = i < currentLen
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .border(
                                                width = 2.dp,
                                                color = if (isFilled) Color(0xFF10B981) else Color(0xFF475569),
                                                shape = CircleShape
                                            )
                                            .background(if (isFilled) Color(0xFF10B981) else Color.Transparent)
                                    )
                                }
                            }
                        }

                        SetupNumericKeypad(
                            onDigitClick = { digit ->
                                if (pinStage == 1) {
                                    if (firstPinInput.length < 4) {
                                        firstPinInput += digit
                                        if (firstPinInput.length == 4) {
                                            pinStage = 2
                                            pinInstruction = "Re-enter the same 4-digit PIN to confirm"
                                        }
                                    }
                                } else {
                                    if (secondPinInput.length < 4) {
                                        secondPinInput += digit
                                        if (secondPinInput.length == 4) {
                                            if (firstPinInput == secondPinInput) {
                                                securityManager.savePIN(firstPinInput)
                                                securityManager.setLockType(SecurityManager.LOCK_TYPE_PIN)
                                                Toast.makeText(context, "PIN Locker Configured Successfully!", Toast.LENGTH_SHORT).show()
                                                onCompleted()
                                            } else {
                                                firstPinInput = ""
                                                secondPinInput = ""
                                                pinStage = 1
                                                pinInstruction = "PINs did not match! Try defining PIN again"
                                            }
                                        }
                                    }
                                }
                            },
                            onClearClick = {
                                if (pinStage == 1) {
                                    firstPinInput = ""
                                } else {
                                    secondPinInput = ""
                                }
                            },
                            onDeleteClick = {
                                if (pinStage == 1) {
                                    if (firstPinInput.isNotEmpty()) firstPinInput = firstPinInput.dropLast(1)
                                } else {
                                    if (secondPinInput.isNotEmpty()) secondPinInput = secondPinInput.dropLast(1)
                                }
                            }
                        )
                    }
                }

                SecurityManager.LOCK_TYPE_PATTERN -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (patternStage == 1) "STAGE 1: DRAW SHIELD PATH" else "STAGE 2: RE-DRAW TO CONFIRM",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = patternInstruction,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Reusable interactive pattern canvas
                        PatternGrid(
                            expectedPattern = "Any",
                            onSuccess = { path ->
                                if (patternStage == 1) {
                                    firstPatternInput = path
                                    patternStage = 2
                                    patternInstruction = "Re-draw your pattern path again to confirm"
                                } else {
                                    secondPatternInput = path
                                    if (firstPatternInput == secondPatternInput) {
                                        securityManager.savePattern(firstPatternInput)
                                        securityManager.setLockType(SecurityManager.LOCK_TYPE_PATTERN)
                                        Toast.makeText(context, "Shield Pattern Saved Successfully!", Toast.LENGTH_SHORT).show()
                                        onCompleted()
                                    } else {
                                        firstPatternInput = ""
                                        secondPatternInput = ""
                                        patternStage = 1
                                        patternInstruction = "Patterns did not match! Draw starting path again"
                                    }
                                }
                            },
                            onFailure = {
                                Toast.makeText(context, "Connect 4 or more dots!", Toast.LENGTH_SHORT).show()
                            }
                        )

                        Text(
                            text = "Simply swipe and drag continuous paths between nodes",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                SecurityManager.LOCK_TYPE_BIOMETRIC -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Shield Guard",
                            tint = Color(0xFF10B981),
                            modifier = Modifier
                                .size(72.dp)
                                .padding(bottom = 12.dp)
                        )
                        Text(
                            text = "Register Biometrics Shield",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Uses your device's registered fingerprint or face credentials for instantaneous unlocking",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Spacer(modifier = Modifier.height(28.dp))

                        Button(
                            onClick = {
                                activity?.let { act ->
                                    biometricAuthManager.showBiometricPrompt(
                                        activity = act,
                                        title = "Validate Active Fingerprint",
                                        subtitle = "Authenticate to sync biometric credentials",
                                        onSuccess = {
                                            securityManager.setLockType(SecurityManager.LOCK_TYPE_BIOMETRIC)
                                            Toast.makeText(context, "Biometric Shield Registered successfully!", Toast.LENGTH_SHORT).show()
                                            onCompleted()
                                        },
                                        onError = { code, msg ->
                                            Toast.makeText(context, "Biometric error ($code): $msg", Toast.LENGTH_LONG).show()
                                        },
                                        onFailed = {
                                            Toast.makeText(context, "Biometric authentication failed!", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(50.dp)
                                .testTag("validate_sensor_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Verify biometric prompt"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test & Enable Sensor", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Cancel footer
        TextButton(
            onClick = onCancelled,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(48.dp)
                .testTag("cancel_setup_button")
        ) {
            Text("Cancel Shield Setup", color = Color(0xFFEF4444), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun LockTypeSelectorCard(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF1E293B))
            .border(
                width = 2.dp,
                color = if (isSelected) Color(0xFF10B981) else Color(0xFF334155),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
            .testTag("tab_select_$label"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "$label Lock Icon",
                tint = if (isSelected) Color(0xFF10B981) else Color(0xFF94A3B8),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun SetupNumericKeypad(
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.6f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E293B))
                            .clickable {
                                when (key) {
                                    "C" -> onClearClick()
                                    "DEL" -> onDeleteClick()
                                    else -> onDigitClick(key)
                                }
                            }
                            .testTag("setup_key_$key"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "DEL") {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = "Delete character icon",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                text = key,
                                fontSize = 18.sp,
                                color = if (key == "C" || key == "DEL") Color(0xFFEF4444) else Color.White,
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
fun PatternGrid(
    expectedPattern: String,
    onSuccess: (String) -> Unit,
    onFailure: () -> Unit
) {
    var connectedNodes by remember { mutableStateOf(emptyList<Int>()) }
    var touchPoint by remember { mutableStateOf<Offset?>(null) }
    var drawColor by remember { mutableStateOf(Color(0xFF10B981)) }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .aspectRatio(1.05f)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E293B), RoundedCornerShape(20.dp))
                .border(2.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
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
            val outerRadius = cellWidth * 0.16f
            val innerRadius = cellWidth * 0.05f

            fun getNodeCenter(idx: Int): Offset {
                val row = idx / 3
                val col = idx % 3
                return Offset(
                    col * cellWidth + cellWidth / 2f,
                    row * cellHeight + cellHeight / 2f
                )
            }

            // Draw line connections
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
                            color = drawColor.copy(alpha = 0.5f),
                            start = lastNodeCenter,
                            end = currentPt,
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // Render 9 standard nodes
            for (i in 0..8) {
                val center = getNodeCenter(i)
                val isHighlighted = i in connectedNodes

                drawCircle(
                    color = if (isHighlighted) drawColor.copy(alpha = 0.2f) else Color(0xFF475569),
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
