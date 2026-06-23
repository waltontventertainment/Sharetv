package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

// App Screen States
sealed class AppScreen {
    object DeviceSelection : AppScreen()
    object ActionSelection : AppScreen()
    object StorageConfig : AppScreen()
    object ActiveTransfer : AppScreen()
}

// Data models
data class DiscoveredDevice(
    val name: String,
    val ipAddress: String,
    val port: Int
)

data class TransferProgress(
    val isSender: Boolean,
    val fileName: String,
    val totalBytes: Long,
    val bytesTransferred: Long,
    val progressPercent: Int,
    val speedMbs: Double,
    val status: String,
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val errorMsg: String? = null
)

class FileSharingViewModel : ViewModel() {

    // Global settings & choices
    var isTvMode by mutableStateOf<Boolean?>(null)
    var isSenderMode by mutableStateOf(false)
    
    // Core Navigation
    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.DeviceSelection)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Storage Selection State
    var selectedDirectoryUri by mutableStateOf<Uri?>(null)
    var selectedDirectoryName by mutableStateOf("No directory selected")

    // Logs for UI diagnostic screen
    private val _systemLogs = MutableStateFlow<List<String>>(listOf("System Initialized"))
    val systemLogs: StateFlow<List<String>> = _systemLogs.asStateFlow()

    // Discovery UI States
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    // Active connection progress
    private val _activeTransfer = MutableStateFlow<TransferProgress?>(null)
    val activeTransfer: StateFlow<TransferProgress?> = _activeTransfer.asStateFlow()

    // IP Info
    var localIpAddress by mutableStateOf("0.0.0.0")
    var localDeviceName by mutableStateOf(Build.MODEL)

    // Sockets / Jobs refs
    private var tcpServerJob: Job? = null
    private var dnsRegistrationJob: Job? = null
    private var dnsDiscoveryJob: Job? = null
    
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    
    private val portNumber = 9090
    private val serviceType = "_sharebeam._tcp."

    init {
        viewModelScope.launch(Dispatchers.IO) {
            localIpAddress = retrieveLocalIpAddress()
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    // Helper to log visual status onto the transfer screen
    fun addLog(msg: String) {
        viewModelScope.launch(Dispatchers.Main) {
            Log.d("ShareBeamLogs", msg)
            val current = _systemLogs.value.toMutableList()
            if (current.size > 8) {
                current.removeAt(0)
            }
            current.add("[${System.currentTimeMillis() % 100000}] $msg")
            _systemLogs.value = current
        }
    }

    fun setDeviceMode(isTv: Boolean) {
        isTvMode = isTv
        addLog("Device environment selected: " + if (isTv) "Android/Google TV" else "Mobile Touch")
        navigateTo(AppScreen.ActionSelection)
    }

    fun setActionFlow(isSender: Boolean) {
        isSenderMode = isSender
        addLog("Selected action: " + if (isSender) "Send mode" else "Receive mode")
        if (isSender) {
            navigateTo(AppScreen.ActiveTransfer)
            startSenderMode()
        } else {
            navigateTo(AppScreen.StorageConfig)
        }
    }

    fun onDirectorySelected(context: Context, uri: Uri) {
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            
            selectedDirectoryUri = uri
            val folder = DocumentFile.fromTreeUri(context, uri)
            selectedDirectoryName = folder?.name ?: "Selected Directory"
            addLog("Storage location configured to: $selectedDirectoryName")
        } catch (e: Exception) {
            selectedDirectoryUri = uri
            selectedDirectoryName = uri.lastPathSegment ?: "Selected Folder"
            addLog("Session folder configured (safely): $selectedDirectoryName")
        }
        navigateTo(AppScreen.ActiveTransfer)
        startReceiverMode(context)
    }

    fun selectDefaultStorage(context: Context) {
        // Fallback or quick choice without manual picker (e.g. standard internal filesDir)
        val filesDir = context.getExternalFilesDir("Received") ?: context.filesDir
        selectedDirectoryUri = Uri.fromFile(filesDir)
        selectedDirectoryName = "Downloads/ShareBeam"
        addLog("Session folder set to quick storage: $selectedDirectoryName")
        navigateTo(AppScreen.ActiveTransfer)
        startReceiverMode(context)
    }

    // Network Utils
    private fun retrieveLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress) {
                        val sAddr = address.hostAddress
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4) {
                            return sAddr
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            addLog("Err scanning IP: ${ex.message}")
        }
        return "127.0.0.1"
    }

    // --- RECEIVE MODE: TCP Server + NSD Registration ---
    private fun startReceiverMode(context: Context) {
        addLog("Launching Receiver Mode...")
        stopNetworking() // Reset any prior jobs
        localIpAddress = retrieveLocalIpAddress()
        
        // 1. Fire up the TCP Server
        startTcpServer(context)
        
        // 2. Register local service on Wi-Fi (NSD)
        registerNsdService(context)
    }

    private fun startTcpServer(context: Context) {
        tcpServerJob = viewModelScope.launch(Dispatchers.IO) {
            var serverSocket: ServerSocket? = null
            try {
                addLog("Starting TCP Server on port $portNumber...")
                serverSocket = ServerSocket(portNumber)
                addLog("TCP Server active. Waiting for incoming file packets...")
                
                while (tcpServerJob?.isActive == true) {
                    val socket = serverSocket.accept()
                    addLog("Incoming socket connected from: ${socket.inetAddress.hostAddress}")
                    
                    // Handle file transfer in a safe isolated coroutine
                    handleIncomingTransfer(context, socket)
                }
            } catch (e: Exception) {
                addLog("Server Socket Idle or Closed: ${e.message}")
            } finally {
                try { serverSocket?.close() } catch (ignored: Exception) {}
            }
        }
    }

    private suspend fun handleIncomingTransfer(context: Context, socket: Socket) {
        withContext(Dispatchers.IO) {
            var dataInputStream: DataInputStream? = null
            var outputStream: java.io.OutputStream? = null
            try {
                dataInputStream = DataInputStream(socket.getInputStream())
                
                // Read Metadata header payload (Robust handshake)
                val fileName = dataInputStream.readUTF()
                val fileSize = dataInputStream.readLong()
                val mimeType = dataInputStream.readUTF()
                
                addLog("Receiving handshake: $fileName ($fileSize Bytes, $mimeType)")
                
                // Set initial progress
                _activeTransfer.value = TransferProgress(
                    isSender = false,
                    fileName = fileName,
                    totalBytes = fileSize,
                    bytesTransferred = 0,
                    progressPercent = 0,
                    speedMbs = 0.0,
                    status = "Downloading file"
                )

                // Resolve Target Scoped/USB storage tree
                val destUri = selectedDirectoryUri
                if (destUri != null) {
                    val documentDir = DocumentFile.fromTreeUri(context, destUri)
                    if (documentDir != null && documentDir.exists()) {
                        val fileToWrite = documentDir.createFile(mimeType, fileName)
                        if (fileToWrite != null) {
                            outputStream = context.contentResolver.openOutputStream(fileToWrite.uri)
                        }
                    }
                }
                
                // Fallback to internal storage stream if tree SAF writer was unavailable or denied
                if (outputStream == null) {
                    val fallbackFile = java.io.File(context.filesDir, fileName)
                    addLog("Stream writing to fallback internal directory: ${fallbackFile.absolutePath}")
                    outputStream = fallbackFile.outputStream()
                }

                if (outputStream != null) {
                    val buffer = ByteArray(8192)
                    var totalReceived = 0L
                    var lastUpdateTime = System.currentTimeMillis()
                    val startTime = System.currentTimeMillis()

                    while (totalReceived < fileSize) {
                        val toRead = java.lang.Math.min(buffer.size.toLong(), fileSize - totalReceived).toInt()
                        val bytesRead = dataInputStream.read(buffer, 0, toRead)
                        if (bytesRead == -1) break
                        
                        outputStream.write(buffer, 0, bytesRead)
                        totalReceived += bytesRead
                        
                        val now = System.currentTimeMillis()
                        // Throttle progress emissions to prevent UI layout frame drops
                        if (now - lastUpdateTime > 100 || totalReceived == fileSize) {
                            val elapsedSec = (now - startTime) / 1000.0
                            val speed = if (elapsedSec > 0.05) {
                                (totalReceived.toDouble() / (1024.0 * 1024.0)) / elapsedSec
                            } else 0.0
                            
                            val percent = ((totalReceived * 100) / fileSize).toInt()
                            _activeTransfer.value = _activeTransfer.value?.copy(
                                bytesTransferred = totalReceived,
                                progressPercent = percent,
                                speedMbs = speed
                            )
                            lastUpdateTime = now
                        }
                    }
                    outputStream.flush()
                    addLog("File streamed completely: $fileName")
                    _activeTransfer.value = _activeTransfer.value?.copy(
                        isCompleted = true,
                        status = "Transfer successful!"
                    )
                } else {
                    throw Exception("Could not open destination write file descriptor.")
                }
                
            } catch (e: Exception) {
                addLog("Download Error: ${e.message}")
                _activeTransfer.value = _activeTransfer.value?.copy(
                    isFailed = true,
                    errorMsg = e.message ?: "Connection closed unexpectedly"
                )
            } finally {
                try { outputStream?.close() } catch (ignored: Exception) {}
                try { dataInputStream?.close() } catch (ignored: Exception) {}
                try { socket.close() } catch (ignored: Exception) {}
            }
        }
    }

    private fun registerNsdService(context: Context) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
                
                val serviceInfo = NsdServiceInfo().apply {
                    serviceName = "ShareBeam_" + Build.MODEL.replace(" ", "_")
                    serviceType = this@FileSharingViewModel.serviceType
                    port = portNumber
                }
                
                registrationListener = object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(info: NsdServiceInfo) {
                        addLog("NSD Service Registered under: ${info.serviceName}")
                    }

                    override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                        addLog("NSD Service registration failed: error code $errorCode")
                    }

                    override fun onServiceUnregistered(info: NsdServiceInfo) {
                        addLog("NSD Service unregistered successfully")
                    }

                    override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                        addLog("NSD Service unregistration failed: error code $errorCode")
                    }
                }
                
                nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
            } catch (e: Exception) {
                addLog("NSD unavailable: ${e.message}")
            }
        }
    }


    // --- SENDER MODE: Client Discovery (NSD) + Socket Transmission ---
    private fun startSenderMode() {
        addLog("Launching Sender Mode...")
        stopNetworking()
        localIpAddress = retrieveLocalIpAddress()
        
        // Start device scanners
        discoverNsdServices()
    }

    private fun discoverNsdServices() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val context = getContextReference() ?: return@launch
                nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
                
                discoveryListener = object : NsdManager.DiscoveryListener {
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        addLog("NSD Start discovery failed: code $errorCode")
                        nsdManager?.stopServiceDiscovery(this)
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        addLog("NSD stop discovery failed: code $errorCode")
                    }

                    override fun onDiscoveryStarted(regType: String) {
                        addLog("Local device scanning activated (NSD type $regType)...")
                    }

                    override fun onDiscoveryStopped(serviceType: String) {
                        addLog("Discovery stopped.")
                    }

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        addLog("Service found: ${serviceInfo.serviceName}")
                        
                        // Only resolve custom service type
                        if (serviceInfo.serviceType.contains("sharebeam")) {
                            nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                                    addLog("Could not resolve service: $errorCode")
                                }

                                override fun onServiceResolved(info: NsdServiceInfo) {
                                    val ip = info.host.hostAddress ?: "127.0.0.1"
                                    viewModelScope.launch(Dispatchers.Main) {
                                        val device = DiscoveredDevice(
                                            name = info.serviceName.replace("ShareBeam_", "").replace("_", " "),
                                            ipAddress = ip,
                                            port = info.port
                                        )
                                        val updated = _discoveredDevices.value.filter { it.ipAddress != ip }.toMutableList()
                                        updated.add(device)
                                        _discoveredDevices.value = updated
                                        addLog("Verified remote host device ready at: $ip")
                                    }
                                }
                            })
                        }
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        addLog("Lost service: ${serviceInfo.serviceName}")
                    }
                }
                
                nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            } catch (e: Exception) {
                addLog("NSD discovery setup blocked: ${e.message}")
            }
        }
    }

    // Direct connect and send by manual IP or scanned device
    fun sendFileDirectly(context: Context, ipAddress: String, fileUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            var fileStream: InputStream? = null
            var socket: Socket? = null
            var dataOutputStream: DataOutputStream? = null
            
            try {
                addLog("Resolving selected file to transfer...")
                
                // Get filename, size & mime type precisely via ContentResolver
                var fileName = "document.bin"
                var fileSize = 0L
                val mimeType = context.contentResolver.getType(fileUri) ?: "application/octet-stream"
                
                context.contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) fileName = cursor.getString(nameIdx)
                        if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                    }
                }
                
                fileStream = context.contentResolver.openInputStream(fileUri)
                if (fileStream == null) {
                    addLog("CRITICAL: Failed to open file path stream.")
                    return@launch
                }

                if (fileSize == 0L) {
                    fileSize = fileStream.available().toLong()
                }

                _activeTransfer.value = TransferProgress(
                    isSender = true,
                    fileName = fileName,
                    totalBytes = fileSize,
                    bytesTransferred = 0,
                    progressPercent = 0,
                    speedMbs = 0.0,
                    status = "Connecting to receiver $ipAddress"
                )

                addLog("Attempting connection to $ipAddress:$portNumber...")
                socket = Socket(ipAddress, portNumber)
                addLog("Connection established! Initiating network payload streaming...")
                
                dataOutputStream = DataOutputStream(socket.getOutputStream())
                
                // Header packet handshake
                dataOutputStream.writeUTF(fileName)
                dataOutputStream.writeLong(fileSize)
                dataOutputStream.writeUTF(mimeType)
                dataOutputStream.flush()
                
                _activeTransfer.value = _activeTransfer.value?.copy(status = "Uploading file")
                
                // Buffer block streaming
                val buffer = ByteArray(8192)
                var bytesSent = 0L
                val startTime = System.currentTimeMillis()
                var lastUpdateTime = System.currentTimeMillis()
                
                while (bytesSent < fileSize) {
                    val bytesRead = fileStream.read(buffer)
                    if (bytesRead == -1) break
                    
                    dataOutputStream.write(buffer, 0, bytesRead)
                    bytesSent += bytesRead
                    
                    val now = System.currentTimeMillis()
                    if (now - lastUpdateTime > 100 || bytesSent == fileSize) {
                        val elapsedSec = (now - startTime) / 1000.0
                        val speed = if (elapsedSec > 0.05) {
                            (bytesSent.toDouble() / (1024.0 * 1024.0)) / elapsedSec
                        } else 0.0
                        
                        val percent = ((bytesSent * 100) / fileSize).toInt()
                        _activeTransfer.value = _activeTransfer.value?.copy(
                            bytesTransferred = bytesSent,
                            progressPercent = percent,
                            speedMbs = speed
                        )
                        lastUpdateTime = now
                    }
                }
                dataOutputStream.flush()
                addLog("Packet upload successful.")
                _activeTransfer.value = _activeTransfer.value?.copy(
                    isCompleted = true,
                    status = "File sent successfully!"
                )
                
            } catch (e: Exception) {
                addLog("Sender error: ${e.message}")
                _activeTransfer.value = _activeTransfer.value?.copy(
                    isFailed = true,
                    errorMsg = e.message ?: "Failed upload connection"
                )
            } finally {
                try { fileStream?.close() } catch (ignored: Exception) {}
                try { dataOutputStream?.close() } catch (ignored: Exception) {}
                try { socket?.close() } catch (ignored: Exception) {}
            }
        }
    }

    // In-memory demo files to prevent dead ends on headless platforms like TV if system picker fails
    fun sendDemoFile(context: Context, ipAddress: String, demoType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Generate simulated payload
            try {
                val fileName = when (demoType) {
                    "video" -> "Cinematic_Demo_Clip.mp4"
                    "pdf" -> "ShareBeam_System_UserGuide.pdf"
                    else -> "Mock_System_Update.apk"
                }
                
                val simulatedSize = when (demoType) {
                    "video" -> 15 * 1024 * 1024L // 15MB
                    "pdf" -> 2 * 1024 * 1024L    // 2MB
                    else -> 5 * 1024 * 1024L     // 5MB
                }
                
                val mimeType = when (demoType) {
                    "video" -> "video/mp4"
                    "pdf" -> "application/pdf"
                    else -> "application/vnd.android.package-archive"
                }

                _activeTransfer.value = TransferProgress(
                    isSender = true,
                    fileName = fileName,
                    totalBytes = simulatedSize,
                    bytesTransferred = 0,
                    progressPercent = 0,
                    speedMbs = 0.0,
                    status = "Generating demo file..."
                )

                addLog("Creating demo stream: $fileName ($simulatedSize Bytes)")
                addLog("Attempting connection to $ipAddress:$portNumber...")
                
                val socket = Socket(ipAddress, portNumber)
                addLog("Demo stream connected! Multiplexing packets...")
                
                val dataOutputStream = DataOutputStream(socket.getOutputStream())
                dataOutputStream.writeUTF(fileName)
                dataOutputStream.writeLong(simulatedSize)
                dataOutputStream.writeUTF(mimeType)
                dataOutputStream.flush()

                _activeTransfer.value = _activeTransfer.value?.copy(status = "Uploading standard demo file")

                val dummyBuffer = ByteArray(8192)
                // fill with visual characters
                for (i in dummyBuffer.indices) dummyBuffer[i] = (65 + (i % 26)).toByte()
                
                var bytesSent = 0L
                val startTime = System.currentTimeMillis()
                var lastUpdateTime = System.currentTimeMillis()

                while (bytesSent < simulatedSize) {
                    val chunk = java.lang.Math.min(dummyBuffer.size.toLong(), simulatedSize - bytesSent).toInt()
                    dataOutputStream.write(dummyBuffer, 0, chunk)
                    bytesSent += chunk
                    
                    val now = System.currentTimeMillis()
                    if (now - lastUpdateTime > 100 || bytesSent == simulatedSize) {
                        val elapsedSec = (now - startTime) / 1000.0
                        val speed = if (elapsedSec > 0.05) {
                            (bytesSent.toDouble() / (1024.0 * 1024.0)) / elapsedSec
                        } else 0.0
                        
                        val percent = ((bytesSent * 100) / simulatedSize).toInt()
                        _activeTransfer.value = _activeTransfer.value?.copy(
                            bytesTransferred = bytesSent,
                            progressPercent = percent,
                            speedMbs = speed
                        )
                        lastUpdateTime = now
                    }
                    // Delay slightly to simulate upload speeds for demo files over local wires
                    delay(1)
                }
                dataOutputStream.flush()
                dataOutputStream.close()
                socket.close()

                addLog("Demo file streamed cleanly.")
                _activeTransfer.value = _activeTransfer.value?.copy(
                    isCompleted = true,
                    status = "Demo file sent successfully!"
                )
            } catch (e: Exception) {
                addLog("Demo Sender issue: ${e.message}")
                _activeTransfer.value = _activeTransfer.value?.copy(
                    isFailed = true,
                    errorMsg = e.message ?: "Demo file transfer failed"
                )
            }
        }
    }

    // Clean tear down of socket resources
    fun stopNetworking() {
        tcpServerJob?.cancel()
        tcpServerJob = null
        
        try {
            if (nsdManager != null) {
                registrationListener?.let { nsdManager?.unregisterService(it) }
                discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
            }
        } catch (e: Exception) {
            // Service not registered or already stopped
        }
        
        registrationListener = null
        discoveryListener = null
        _discoveredDevices.value = emptyList()
        _activeTransfer.value = null
        addLog("Network connections stopped.")
    }

    fun resetFlow() {
        stopNetworking()
        _currentScreen.value = AppScreen.ActionSelection
    }

    // Memory helper context bridge
    private var activityContext: Context? = null
    fun setContextReference(context: Context) {
        activityContext = context.applicationContext
    }
    private fun getContextReference(): Context? = activityContext

    override fun onCleared() {
        stopNetworking()
        super.onCleared()
    }
}
