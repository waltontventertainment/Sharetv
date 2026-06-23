package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    private val viewModel: FileSharingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Pass application context to viewmodel safely for NSD helper lifecycle
                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    viewModel.setContextReference(context)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScaffold(viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        viewModel.stopNetworking()
        super.onDestroy()
    }
}

// Custom scale focus modifier specifically tuned for D-Pad Remote operations on Android TV and spring-touch on mobile
@Composable
fun Modifier.customTvFocusable(
    isTv: Boolean,
    onClick: () -> Unit
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    val isPressed = remember { mutableStateOf(false) }
    
    // Smooth responsive tactile scale responding to focus (TV) or tap/press (Mobile)
    val scale by animateFloatAsState(
        targetValue = when {
            isFocused && isTv -> 1.05f
            isPressed.value -> 0.94f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "interactive_scale"
    )
    
    val borderBrush = if (isFocused) {
        Brush.linearGradient(listOf(IndigoAccent, MintAccent))
    } else {
        Brush.linearGradient(listOf(Slate700.copy(alpha = 0.5f), Slate700.copy(alpha = 0.2f)))
    }

    return this
        .onFocusChanged { isFocused = it.isFocused }
        .scale(scale)
        .border(
            width = if (isFocused) 3.dp else 1.5.dp,
            brush = borderBrush,
            shape = RoundedCornerShape(24.dp)
        )
        .background(
            color = if (isFocused) Slate800.copy(alpha = 0.85f) else Slate800.copy(alpha = 0.5f),
            shape = RoundedCornerShape(24.dp)
        )
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = LocalIndication.current,
            onClick = onClick
        )
        .focusable()
}

@Composable
fun AmbientBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
            .drawBehind {
                // Large top-left soft Indigo gradient leak
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(IndigoAccent.copy(alpha = 0.16f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.15f),
                        radius = size.minDimension * 0.85f
                    ),
                    radius = size.minDimension * 0.85f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.15f)
                )

                // Large bottom-right soft Mint gradient leak
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(MintAccent.copy(alpha = 0.12f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.85f),
                        radius = size.minDimension * 0.95f
                    ),
                    radius = size.minDimension * 0.95f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.85f)
                )

                // Technical subgrid intersections for sophisticated luxury finish
                val gridStep = 48.dp.toPx()
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = Slate800.copy(alpha = 0.18f),
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    y += gridStep
                }
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = Slate800.copy(alpha = 0.18f),
                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                        end = androidx.compose.ui.geometry.Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                    x += gridStep
                }
            }
    ) {
        content()
    }
}

@Composable
fun MainAppScaffold(viewModel: FileSharingViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = Color.Transparent, // Drawn elegantly by AmbientBackdrop
        topBar = {
            HeaderBar(viewModel)
        }
    ) { innerPadding ->
        AmbientBackdrop {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                // Smooth Crossfade between active screen states
                Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
                    when (screen) {
                        AppScreen.DeviceSelection -> DeviceSelectionScreen(viewModel)
                        AppScreen.ActionSelection -> ActionSelectionScreen(viewModel)
                        AppScreen.StorageConfig -> StorageConfigScreen(viewModel)
                        AppScreen.ActiveTransfer -> ActiveTransferScreen(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderBar(viewModel: FileSharingViewModel) {
    val screen by viewModel.currentScreen.collectAsState()
    
    // Logo animated pulsing rings
    val infiniteTransition = rememberInfiniteTransition(label = "LogoPulse")
    val pulseSize by infiniteTransition.animateFloat(
        initialValue = 40.dp.value,
        targetValue = 46.dp.value,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // App Icon Graphic Logo with ambient glow ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(50.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(pulseSize.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(IndigoAccent.copy(alpha = 0.3f), Color.Transparent)))
                )
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(IndigoAccent, MintAccent))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "ShareBeam logo",
                        tint = TextWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                // Premium title with subtle gradient brush feel
                Text(
                    text = "ShareBeam",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 21.sp,
                    color = TextWhite,
                    letterSpacing = (-0.5).sp
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MintAccent)
                    )
                    Text(
                        text = if (viewModel.isTvMode == true) "TV ENGINE READY" else if (viewModel.isTvMode == false) "MOBILE ENGINE READY" else "MULTIDEVICE HYBRID",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = MintAccent,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Quick back/reset indicator
        if (screen != AppScreen.DeviceSelection) {
            IconButton(
                onClick = { viewModel.resetFlow() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Slate800.copy(alpha = 0.8f))
                    .border(1.dp, Slate700.copy(alpha = 0.7f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset setup",
                    tint = TextWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}


// SCREEN 1: Device environment detection
@Composable
fun DeviceSelectionScreen(viewModel: FileSharingViewModel) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val isLandscape = maxWidth > 640.dp
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Welcome to ShareBeam",
                fontSize = if (isLandscape) 32.sp else 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextWhite,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Select your current device environment below to optimize target remote focus",
                fontSize = 14.sp,
                color = TextGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.85f),
                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
                ) {
                    DeviceButtonCard(
                        title = "Mobile Phone",
                        description = "Touch-first navigation with standard layout options",
                        icon = Icons.Default.PhoneAndroid,
                        isTvTarget = false,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setDeviceMode(false) }
                    )
                    DeviceButtonCard(
                        title = "Android / Google TV",
                        description = "Remote-friendly navigation optimized for D-Pad focus scaling",
                        icon = Icons.Default.Tv,
                        isTvTarget = true,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setDeviceMode(true) }
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DeviceButtonCard(
                        title = "Mobile Phone",
                        description = "Touch-first navigation with standard layout options",
                        icon = Icons.Default.PhoneAndroid,
                        isTvTarget = false,
                        modifier = Modifier.fillMaxWidth(0.9f),
                        onClick = { viewModel.setDeviceMode(false) }
                    )
                    DeviceButtonCard(
                        title = "Android / Google TV",
                        description = "Remote-friendly navigation optimized for D-Pad focus scaling",
                        icon = Icons.Default.Tv,
                        isTvTarget = true,
                        modifier = Modifier.fillMaxWidth(0.9f),
                        onClick = { viewModel.setDeviceMode(true) }
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceButtonCard(
    title: String,
    description: String,
    icon: ImageVector,
    isTvTarget: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .customTvFocusable(isTv = true, onClick = onClick) // Always handle TV outline for safety
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (isTvTarget) CardGlow else Color(0x1F10B981)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(36.dp),
                    tint = if (isTvTarget) IndigoAccent else MintAccent
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = TextGray,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}


// SCREEN 2: Send vs Receive Action Selection
@Composable
fun ActionSelectionScreen(viewModel: FileSharingViewModel) {
    val isTv = viewModel.isTvMode ?: false
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val isLandscape = maxWidth > 600.dp
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "What would you like to do?",
                fontSize = if (isLandscape) 30.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.85f),
                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
                ) {
                    ActionCard(
                        title = "Send Files",
                        description = "Discover local receivers on this Wi-Fi network and transmit files immediately",
                        icon = Icons.Default.CloudUpload,
                        themeColor = MintAccent,
                        glowColor = Color(0x2210B981),
                        isTv = isTv,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setActionFlow(isSender = true) }
                    )
                    ActionCard(
                        title = "Receive Files",
                        description = "Launch local server socket and wait for incoming file transmissions",
                        icon = Icons.Default.CloudDownload,
                        themeColor = IndigoAccent,
                        glowColor = CardGlow,
                        isTv = isTv,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setActionFlow(isSender = false) }
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ActionCard(
                        title = "Send Files",
                        description = "Discover local receivers on this Wi-Fi network and transmit files immediately",
                        icon = Icons.Default.CloudUpload,
                        themeColor = MintAccent,
                        glowColor = Color(0x2210B981),
                        isTv = isTv,
                        modifier = Modifier.fillMaxWidth(0.9f),
                        onClick = { viewModel.setActionFlow(isSender = true) }
                    )
                    ActionCard(
                        title = "Receive Files",
                        description = "Launch local server socket and wait for incoming file transmissions",
                        icon = Icons.Default.CloudDownload,
                        themeColor = IndigoAccent,
                        glowColor = CardGlow,
                        isTv = isTv,
                        modifier = Modifier.fillMaxWidth(0.9f),
                        onClick = { viewModel.setActionFlow(isSender = false) }
                    )
                }
            }
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    themeColor: Color,
    glowColor: Color,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .customTvFocusable(isTv = isTv, onClick = onClick)
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(glowColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(32.dp),
                    tint = themeColor
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = themeColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                color = TextGray,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}


// SCREEN 3: Receive Storage Configuration (Only shown if "Receive" is selected)
@Composable
fun StorageConfigScreen(viewModel: FileSharingViewModel) {
    val context = LocalContext.current
    val isTv = viewModel.isTvMode ?: false

    // Register active SAF browser launcher tree
    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onDirectorySelected(context, uri)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 540.dp)
        ) {
            Text(
                text = "Configure File Storage",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose direct storage context folder. Selecting USB External storage allows Android TV Pendrive direct streaming.",
                fontSize = 13.sp,
                color = TextGray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Option A: Storage Access Framework System Picker
            StorageOptionCard(
                title = "Open Directory Picker (SAF)",
                description = "Choose Internal Folder or connected External USB Flash Pendrive via Android's Directory Selection Picker",
                icon = Icons.Default.FolderOpen,
                themeColor = IndigoAccent,
                isTv = isTv,
                onClick = {
                    try {
                        safFolderLauncher.launch(null)
                    } catch (e: Exception) {
                        viewModel.addLog("SAF unavailable: ${e.message}. Using fallback quick storage instead.")
                        viewModel.selectDefaultStorage(context)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Option B: Immediate Default Fallback (Highly recommended for Google TVs which might lack a default document picker app)
            StorageOptionCard(
                title = "Use Quick App Sandboxed Storage",
                description = "Instantly write into local application files environment (No Dialog Picker required. Safe & bypasses SAF)",
                icon = Icons.Default.Settings,
                themeColor = MintAccent,
                isTv = isTv,
                onClick = { viewModel.selectDefaultStorage(context) }
            )
        }
    }
}

@Composable
fun StorageOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    themeColor: Color,
    isTv: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .customTvFocusable(isTv = isTv, onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(themeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = themeColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = TextGray,
                    lineHeight = 14.sp
                )
            }
        }
    }
}


// SCREEN 4: Active Transfer & Discovery Screen
@Composable
fun ActiveTransferScreen(viewModel: FileSharingViewModel) {
    val context = LocalContext.current
    val isSender = viewModel.isSenderMode
    val isTv = viewModel.isTvMode ?: false
    val logs by viewModel.systemLogs.collectAsState()
    val activeTransfer by viewModel.activeTransfer.collectAsState()

    // Pick file state for senders
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = "Selected file"
            viewModel.addLog("File resolved successfully ready for network dispatch")
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val isLandscape = maxWidth > 640.dp

        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Left Panel: Actions & Connection Configuration
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (isSender) {
                        SenderPanel(
                            viewModel = viewModel,
                            isTv = isTv,
                            selectedFileUri = selectedFileUri,
                            selectedFileName = selectedFileName,
                            onPickFile = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ReceiverPanel(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Right Panel: Progress UI & Scrolling Tech Diagnostics logs
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    LogsPanel(logs = logs, modifier = Modifier.weight(1f))
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.weight(1.4f)
                ) {
                    if (isSender) {
                        SenderPanel(
                            viewModel = viewModel,
                            isTv = isTv,
                            selectedFileUri = selectedFileUri,
                            selectedFileName = selectedFileName,
                            onPickFile = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ReceiverPanel(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                LogsPanel(logs = logs, modifier = Modifier.weight(0.6f))
            }
        }

        // Overlay active transfer dialog modal on top when a transfer starts
        activeTransfer?.let { transfer ->
            TransferProgressOverlay(
                transfer = transfer,
                onDismiss = { viewModel.resetFlow() }
            )
        }
    }
}

@Composable
fun LogRow(log: String) {
    val tagText: String
    val tagColor: Color
    val content: String

    when {
        log.contains("NSD", ignoreCase = true) || log.contains("Discovered", ignoreCase = true) || log.contains("scan", ignoreCase = true) -> {
            tagText = "NSD"
            tagColor = MintAccent
            content = log.replace("NSD", "", ignoreCase = true).trim()
        }
        log.contains("TCP", ignoreCase = true) || log.contains("Server", ignoreCase = true) || log.contains("IP", ignoreCase = true) -> {
            tagText = "CONN"
            tagColor = IndigoAccent
            content = log.replace("TCP", "", ignoreCase = true).trim()
        }
        log.contains("File", ignoreCase = true) || log.contains("Payload", ignoreCase = true) || log.contains("dispatch", ignoreCase = true) || log.contains("Selected", ignoreCase = true) -> {
            tagText = "FILE"
            tagColor = Color(0xFFA855F7) // Purple Accent
            content = log
        }
        log.contains("Err", ignoreCase = true) || log.contains("failed", ignoreCase = true) -> {
            tagText = "ERR"
            tagColor = ErrorRed
            content = log
        }
        else -> {
            tagText = "SYS"
            tagColor = TextGray
            content = log
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(tagColor.copy(alpha = 0.12f))
                .border(0.5.dp, tagColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = tagText,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = tagColor
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = content,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = if (tagText == "ERR") ErrorRed else TextGray,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LogsPanel(logs: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Slate900.copy(alpha = 0.85f))
            .border(1.5.dp, Slate800, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MintAccent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SYSTEM ENGINE TERMINAL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextWhite,
                    letterSpacing = 1.sp
                )
            }
            
            Text(
                text = "LIVE FEED",
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = MintAccent.copy(alpha = 0.8f),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(MintAccent.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        
        HorizontalDivider(color = Slate800, modifier = Modifier.padding(bottom = 8.dp))
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(logs.reversed()) { log ->
                LogRow(log = log)
            }
        }
    }
}

@Composable
fun SenderPanel(
    viewModel: FileSharingViewModel,
    isTv: Boolean,
    selectedFileUri: Uri?,
    selectedFileName: String,
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    
    var manualIp by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Slate800.copy(alpha = 0.5f))
            .border(1.5.dp, Slate700.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        // Step Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "FILE DISPATCH COMPILER",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MintAccent,
                letterSpacing = 1.sp
            )
            
            Box(
                modifier = Modifier
                    .background(MintAccent.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (selectedFileUri != null) "FILE LOADED" else "WAITING PAYLOAD",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MintAccent
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Document Selection button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .customTvFocusable(isTv = isTv, onClick = onPickFile)
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MintAccent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Pick storage",
                        tint = MintAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selectedFileUri != null) "System Document Loaded" else "Browse Storage Files",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Text(
                        text = if (selectedFileUri != null) selectedFileUri.lastPathSegment ?: "URI resolved" else "Choose any document, video or app bundle to share",
                        fontSize = 10.sp,
                        color = TextGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Demo File triggers
        Text(
            text = "QUICK GENERATOR DEMO FILES (RECOMMENDED FOR TV)",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextGray,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Demo APK
            Box(
                modifier = Modifier
                    .weight(1f)
                    .customTvFocusable(isTv = isTv, onClick = {
                        val targetIp = if (manualIp.isNotEmpty()) manualIp else discoveredDevices.firstOrNull()?.ipAddress ?: "127.0.0.1"
                        viewModel.sendDemoFile(context, targetIp, "apk")
                    })
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Demo APK",
                        tint = MintAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("App Binary (5MB)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                }
            }

            // Demo Video
            Box(
                modifier = Modifier
                    .weight(1f)
                    .customTvFocusable(isTv = isTv, onClick = {
                        val targetIp = if (manualIp.isNotEmpty()) manualIp else discoveredDevices.firstOrNull()?.ipAddress ?: "127.0.0.1"
                        viewModel.sendDemoFile(context, targetIp, "video")
                    })
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = "Demo Video",
                        tint = MintAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Video Clip (15MB)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = Slate700.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(14.dp))

        // Discovered list representation or Custom IP form
        Text(
            text = "SCANNING ACTIVE TARGET NODES",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextWhite,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (discoveredDevices.isEmpty()) {
            // Pulsing radar scanning animation
            PulsingRadarView()
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(discoveredDevices) { device ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .customTvFocusable(isTv = isTv, onClick = {
                                if (selectedFileUri != null) {
                                    viewModel.sendFileDirectly(context, device.ipAddress, selectedFileUri)
                                } else {
                                    // Send demo update binary
                                    viewModel.sendDemoFile(context, device.ipAddress, "apk")
                                }
                            })
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MintAccent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = "Target device",
                                        tint = MintAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = device.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite
                                    )
                                    Text(
                                        text = "IP Host: ${device.ipAddress}",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextGray
                                    )
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MintAccent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = MintAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Direct connect via manual IP input field
        OutlinedTextField(
            value = manualIp,
            onValueChange = { manualIp = it },
            label = { Text("Or Type Receiver IP Direct", fontSize = 11.sp, color = TextGray) },
            singleLine = true,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = TextWhite),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { keyboardController?.hide() }
            ),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MintAccent,
                unfocusedBorderColor = Slate700.copy(alpha = 0.7f),
                focusedLabelColor = MintAccent,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite
            ),
            shape = RoundedCornerShape(16.dp),
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (manualIp.isNotEmpty()) {
                            keyboardController?.hide()
                            if (selectedFileUri != null) {
                                viewModel.sendFileDirectly(context, manualIp, selectedFileUri)
                            } else {
                                viewModel.sendDemoFile(context, manualIp, "apk")
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Connect and share",
                        tint = MintAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )
    }
}

@Composable
fun PulsingRadarView() {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    
    // Pulse animation
    val pulseRatio by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Ratio animate"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Alpha animate"
    )

    // Sweep rotation animation
    val sweepRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepRotation"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Slate900.copy(alpha = 0.7f))
            .border(1.5.dp, Slate800, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2.3f

            // Concentric pulsing expansion wave
            drawCircle(
                color = MintAccent,
                radius = maxRadius * pulseRatio,
                center = centerOffset,
                alpha = pulseAlpha,
                style = Stroke(width = 2.5.dp.toPx())
            )
            
            // Core grid rings
            drawCircle(
                color = Slate700.copy(alpha = 0.3f),
                radius = maxRadius * 0.35f,
                center = centerOffset,
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            )
            drawCircle(
                color = Slate700.copy(alpha = 0.5f),
                radius = maxRadius * 0.7f,
                center = centerOffset,
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            )
            drawCircle(
                color = MintAccent.copy(alpha = 0.2f),
                radius = maxRadius,
                center = centerOffset,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Dynamic laser crosshairs axes
            drawLine(
                color = Slate800.copy(alpha = 0.6f),
                start = androidx.compose.ui.geometry.Offset(centerOffset.x - maxRadius, centerOffset.y),
                end = androidx.compose.ui.geometry.Offset(centerOffset.x + maxRadius, centerOffset.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Slate800.copy(alpha = 0.6f),
                start = androidx.compose.ui.geometry.Offset(centerOffset.x, centerOffset.y - maxRadius),
                end = androidx.compose.ui.geometry.Offset(centerOffset.x, centerOffset.y + maxRadius),
                strokeWidth = 1.dp.toPx()
            )

            // Draw spinning laser sweep segment cone
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(Color.Transparent, MintAccent.copy(alpha = 0.4f), Color.Transparent),
                    center = centerOffset
                ),
                startAngle = sweepRotation,
                sweepAngle = 45f,
                useCenter = true,
                topLeft = androidx.compose.ui.geometry.Offset(centerOffset.x - maxRadius, centerOffset.y - maxRadius),
                size = androidx.compose.ui.geometry.Size(maxRadius * 2, maxRadius * 2)
            )

            // Simulated active devices scanning nodes (radar blips)
            val blips = listOf(
                androidx.compose.ui.geometry.Offset(centerOffset.x - maxRadius * 0.5f, centerOffset.y - maxRadius * 0.4f),
                androidx.compose.ui.geometry.Offset(centerOffset.x + maxRadius * 0.6f, centerOffset.y + maxRadius * 0.3f)
            )
            for (blip in blips) {
                drawCircle(
                    color = MintAccent,
                    radius = 4.dp.toPx(),
                    center = blip,
                    alpha = 0.45f
                )
                drawCircle(
                    color = MintAccent.copy(alpha = 0.2f),
                    radius = 8.dp.toPx(),
                    center = blip,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Pulsing radar target",
                tint = MintAccent,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "DISPATCHING NSD SCANNER",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextWhite,
                letterSpacing = 1.sp
            )
            Text(
                text = "Broadcasting service queries under subnets...",
                fontSize = 10.sp,
                color = TextGray
            )
        }
    }
}

@Composable
fun SimulatedQRCode(
    ipAddress: String,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val sizePx = size.minDimension
        val blocks = 17 // 17x17 grid
        val blockSize = sizePx / blocks

        // Background card body
        drawRoundRect(
            color = Color.White,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx(), 12.dp.toPx())
        )

        // Draw top-left, top-right, bottom-left Anchor boxes
        val anchors = listOf(
            androidx.compose.ui.geometry.Offset(0f, 0f),
            androidx.compose.ui.geometry.Offset((blocks - 5) * blockSize, 0f),
            androidx.compose.ui.geometry.Offset(0f, (blocks - 5) * blockSize)
        )

        for (anchor in anchors) {
            // Outer 5x5 Square
            drawRect(
                color = Slate950,
                topLeft = anchor + androidx.compose.ui.geometry.Offset(blockSize * 0.5f, blockSize * 0.5f),
                size = androidx.compose.ui.geometry.Size(blockSize * 4f, blockSize * 4f)
            )
            // Inner white 3x3 Space
            drawRect(
                color = Color.White,
                topLeft = anchor + androidx.compose.ui.geometry.Offset(blockSize * 1.5f, blockSize * 1.5f),
                size = androidx.compose.ui.geometry.Size(blockSize * 2f, blockSize * 2f)
            )
            // Core 1x1 block
            drawRect(
                color = Slate950,
                topLeft = anchor + androidx.compose.ui.geometry.Offset(blockSize * 2f, blockSize * 2f),
                size = androidx.compose.ui.geometry.Size(blockSize * 1f, blockSize * 1f)
            )
        }

        // Generate static layout deterministic on IP string
        val seed = ipAddress.hashCode()
        val random = java.util.Random(seed.toLong())

        for (col in 0 until blocks) {
            for (row in 0 until blocks) {
                // Ensure blocks are only drawn if outside the three anchor boxes
                val isTopLeftAnchor = col < 5 && row < 5
                val isTopRightAnchor = col >= (blocks - 5) && row < 5
                val isBottomLeftAnchor = col < 5 && row >= (blocks - 5)

                if (!isTopLeftAnchor && !isTopRightAnchor && !isBottomLeftAnchor) {
                    if (random.nextBoolean()) {
                        drawRect(
                            color = Slate950,
                            topLeft = androidx.compose.ui.geometry.Offset(col * blockSize + blockSize * 0.15f, row * blockSize + blockSize * 0.15f),
                            size = androidx.compose.ui.geometry.Size(blockSize * 0.7f, blockSize * 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReceiverPanel(
    viewModel: FileSharingViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Slate800.copy(alpha = 0.5f))
            .border(1.5.dp, Slate700.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "ACTIVE SERVER DISPATCHER",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = IndigoAccent,
                letterSpacing = 1.sp
            )
            
            // Heartbeat live tag
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val infiniteBlink = rememberInfiniteTransition(label = "Blink")
                val opacity by infiniteBlink.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "BlinkOpacity"
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MintAccent.copy(alpha = opacity))
                )
                Text(
                    text = "RECEIVING ACTIVE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MintAccent
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Folders Destination Details chip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Slate900.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                .border(1.dp, Slate800, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = "Active folder Destination",
                tint = IndigoAccent,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = "Target Storage Path:", fontSize = 10.sp, color = TextGray)
                Text(
                    text = viewModel.selectedDirectoryName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Double Card split: Left is IP code info, Right is Simulated QR Code
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Slate900.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                .border(1.dp, Slate800, RoundedCornerShape(20.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1.3f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "PAIRING CODE DIRECT",
                        fontSize = 10.sp,
                        color = TextGray,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = viewModel.localIpAddress,
                        fontSize = if (viewModel.localIpAddress.length > 12) 20.sp else 26.sp,
                        fontWeight = FontWeight.Black,
                        color = IndigoAccent,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Device Host: ${viewModel.localDeviceName}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Type IP on your sender device or scan QR code to establish secure stream.",
                        fontSize = 10.sp,
                        color = TextGray,
                        lineHeight = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Right layout QR Code inside a high contrast visual border card
                Box(
                    modifier = Modifier
                        .size(105.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(1.5.dp, IndigoAccent.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SimulatedQRCode(
                        ipAddress = viewModel.localIpAddress,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}


// SCREEN 4 - ACTIVE OVERLAY: Complete transfer popup modal
@Composable
fun TransferProgressOverlay(
    transfer: TransferProgress,
    onDismiss: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (transfer.totalBytes > 0) {
            transfer.bytesTransferred.toFloat() / transfer.totalBytes.toFloat()
        } else 0.0f,
        animationSpec = tween(150, easing = LinearEasing),
        label = "Progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable(enabled = false, onClick = {}) // Block background touches
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 440.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Slate800)
                .border(2.dp, if (transfer.isCompleted) MintAccent else IndigoAccent, RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        if (transfer.isCompleted) Color(0x2210B981)
                        else if (transfer.isFailed) Color(0x22F87171)
                        else CardGlow
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (transfer.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed successfully",
                        tint = MintAccent,
                        modifier = Modifier.size(38.dp)
                    )
                } else if (transfer.isFailed) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Failed",
                        tint = ErrorRed,
                        modifier = Modifier.size(38.dp)
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(48.dp),
                        color = IndigoAccent,
                        strokeWidth = 4.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Text
            Text(
                text = if (transfer.isSender) "Uploading Payload..." else "Downloading Payload...",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextGray
            )

            Text(
                text = transfer.fileName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = TextWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Percentage Meter
            Text(
                text = "${transfer.progressPercent}%",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = if (transfer.isCompleted) MintAccent else IndigoAccent
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (transfer.isCompleted) MintAccent else IndigoAccent,
                trackColor = Slate900
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Network Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "SPEED", fontSize = 9.sp, color = TextGray)
                    Text(
                        text = String.format("%.2f MB/s", transfer.speedMbs),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "TRANSFERRED", fontSize = 9.sp, color = TextGray)
                    Text(
                        text = String.format("%.1f/%.1f MB", transfer.bytesTransferred.toDouble() / (1024*1024), transfer.totalBytes.toDouble() / (1024*1024)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action / Dismiss
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (transfer.isCompleted) MintAccent else Slate900
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (transfer.isCompleted) "Done" else "Cancel & Go Back",
                    color = if (transfer.isCompleted) Slate900 else TextWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
