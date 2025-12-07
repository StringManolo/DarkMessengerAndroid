package com.stringmanolo.dma

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.*
import java.util.concurrent.CopyOnWriteArrayList

class TorManager(private val context: Context, private val mainActivity: MainActivity) {
    private var torProcess: Process? = null
    private val logs = CopyOnWriteArrayList<String>()
    private var isRunning = false
    private var generatedOnionAddress: String? = null
    private val maxLogs = 1000
    private val hiddenServiceDirName = "hidden_service"
    private var serverStarted = false
    
    companion object {
        private const val HIDDEN_SERVICE_PORT = 9001
        private const val TAG = "TorManager"
    }

    fun startTor(): Boolean {
        return try {
            // Detener si ya está corriendo
            stopTor()

            // Crear directorio para datos de Tor
            val dataDir = File(context.filesDir, "tor_data")
            if (!dataDir.exists()) {
                val created = dataDir.mkdirs()
                addLog("Created directory: ${dataDir.absolutePath}, success: $created")
            } else {
                addLog("Directory already exists: ${dataDir.absolutePath}")
            }

            // Verificar permisos del directorio
            dataDir.setReadable(true, false)
            dataDir.setWritable(true, false)
            dataDir.setExecutable(true, false)

            // Copiar binario de Tor
            val torBinary = copyTorBinary()
            
            // Crear archivo torrc
            val torrcFile = createTorrcFile(dataDir)
            
            // Verificar si ya existe un hidden service
            checkExistingHiddenService(dataDir)
            
            // Iniciar proceso
            val command = listOf(
                torBinary.absolutePath,
                "-f", torrcFile.absolutePath
            )

            addLog("Starting Tor with command: ${command.joinToString(" ")}")
            addLog("Working directory: ${dataDir.absolutePath}")

            val processBuilder = ProcessBuilder(command)
                .directory(dataDir)
                .redirectErrorStream(true)

            torProcess = processBuilder.start()
            isRunning = true

            // Leer logs en un hilo separado
            Thread {
                val reader = BufferedReader(InputStreamReader(torProcess?.inputStream))
                var line: String?
                try {
                    while (torProcess?.isAlive == true) {
                        line = reader.readLine()
                        if (line != null) {
                            addLog("TOR: $line")
                            
                            // Verificar si se menciona el hidden service en los logs
                            if (line.contains("hidden_service") || line.contains(".onion")) {
                                checkHiddenServiceStatus(dataDir)
                            }
                            
                            // Cuando Tor esté listo, iniciar el servidor HTTP
                            if ((line.contains("100%") || line.contains("Bootstrapped")) && !serverStarted) {
                                // Esperar un momento para que el hidden service esté listo
                                Thread.sleep(3000)
                                startHiddenServiceServer()
                                serverStarted = true
                            }
                        }
                    }
                } catch (e: IOException) {
                    addLog("ERROR reading Tor output: ${e.message}")
                }
                addLog("TOR: Process ended")
                isRunning = false
                serverStarted = false
            }.start()

            true
        } catch (e: Exception) {
            addLog("ERROR starting Tor: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun stopTor() {
        torProcess?.destroy()
        torProcess = null
        isRunning = false
        serverStarted = false
        addLog("TOR: Stopped")
    }

    fun isRunning(): Boolean = isRunning

    fun getLogs(): List<String> = logs.toList()

    fun clearLogs() {
        logs.clear()
    }

    fun getGeneratedOnionAddress(): String? = generatedOnionAddress

    private fun addLog(message: String) {
        synchronized(logs) {
            val timestamp = System.currentTimeMillis()
            val logEntry = "[$timestamp] $message"
            logs.add(logEntry)
            if (logs.size > maxLogs) {
                logs.removeAt(0)
            }
        }
        
        // También enviar a la actividad principal para que pueda mostrarlo en la WebView
        Handler(Looper.getMainLooper()).post {
            mainActivity.addLogToWebView(message)
        }
    }

    private fun copyTorBinary(): File {
        val abi = getABI()
        val assetPath = "tor/$abi/tor"
        
        val torFile = File(context.filesDir, "tor_binary")
        
        // Copiar desde assets
        context.assets.open(assetPath).use { input ->
            FileOutputStream(torFile).use { output ->
                input.copyTo(output)
            }
        }
        
        // Dar permisos de ejecución
        torFile.setReadable(true, false)
        torFile.setWritable(true, true)
        torFile.setExecutable(true, false)
        
        addLog("Copied Tor binary for ABI: $abi to ${torFile.absolutePath}")
        return torFile
    }

    private fun getABI(): String {
        return try {
            when {
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP -> {
                    android.os.Build.SUPPORTED_ABIS[0]
                }
                else -> android.os.Build.CPU_ABI
            }.let { abi ->
                when {
                    abi.contains("arm64") -> "arm64-v8a"
                    abi.contains("armeabi") -> "armeabi-v7a"
                    else -> "armeabi-v7a" // default
                }
            }
        } catch (e: Exception) {
            "armeabi-v7a"
        }
    }

    private fun createTorrcFile(dataDir: File): File {
        val torrcFile = File(dataDir, "torrc")
        
        // Directorio para el hidden service
        val hiddenServiceDir = File(dataDir, hiddenServiceDirName)
        if (!hiddenServiceDir.exists()) {
            hiddenServiceDir.mkdirs()
            addLog("Created hidden service directory: ${hiddenServiceDir.absolutePath}")
        }
        
        // Crear torrc con configuración de hidden service
        val torrcContent = """
            SocksPort 9050
            ControlPort 9051
            DataDirectory ${dataDir.absolutePath}
            Log notice stdout
            AvoidDiskWrites 1
            
            # Hidden Service Configuration
            HiddenServiceDir ${hiddenServiceDir.absolutePath}
            HiddenServicePort $HIDDEN_SERVICE_PORT 127.0.0.1:$HIDDEN_SERVICE_PORT
            
            # Configuración de logging
            Log notice file ${File(dataDir, "tor_notice.log").absolutePath}
            Log warn file ${File(dataDir, "tor_warn.log").absolutePath}
            Log err file ${File(dataDir, "tor_err.log").absolutePath}
            
            # Configuración de seguridad
            SafeLogging 1
            SafeSocks 1
            
            # Mejorar rendimiento en Android
            NumEntryGuards 1
            UseEntryGuards 1
            NewCircuitPeriod 15
            
            # Configuración de caché
            MaxMemInQueues 16 MB
            
            # Hidden Service optimizations
            HiddenServiceNonAnonymousMode 0
            HiddenServiceSingleHopMode 0
            HiddenServiceMaxStreams 0
        """.trimIndent()
        
        torrcFile.writeText(torrcContent)
        
        // Crear archivos de log
        File(dataDir, "tor_notice.log").createNewFile()
        File(dataDir, "tor_warn.log").createNewFile()
        File(dataDir, "tor_err.log").createNewFile()
        
        addLog("Created torrc file at: ${torrcFile.absolutePath}")
        return torrcFile
    }

    private fun checkExistingHiddenService(dataDir: File) {
        val hiddenServiceDir = File(dataDir, hiddenServiceDirName)
        val hostnameFile = File(hiddenServiceDir, "hostname")
        
        if (hostnameFile.exists()) {
            try {
                generatedOnionAddress = hostnameFile.readText().trim()
                addLog("Existing hidden service found: $generatedOnionAddress")
                
                // Notificar al contexto principal
                notifyOnionAddressGenerated(generatedOnionAddress!!)
            } catch (e: Exception) {
                addLog("ERROR reading existing hidden service: ${e.message}")
            }
        } else {
            addLog("No existing hidden service found. New one will be created.")
        }
    }

    private fun checkHiddenServiceStatus(dataDir: File) {
        val hiddenServiceDir = File(dataDir, hiddenServiceDirName)
        val hostnameFile = File(hiddenServiceDir, "hostname")
        
        if (hostnameFile.exists()) {
            try {
                val newOnionAddress = hostnameFile.readText().trim()
                if (newOnionAddress.isNotEmpty() && newOnionAddress != generatedOnionAddress) {
                    generatedOnionAddress = newOnionAddress
                    addLog("Hidden service created: $generatedOnionAddress")
                    
                    // Notificar al contexto principal
                    notifyOnionAddressGenerated(generatedOnionAddress!!)
                }
            } catch (e: Exception) {
                addLog("ERROR reading hidden service hostname: ${e.message}")
            }
        }
    }

    private fun startHiddenServiceServer() {
        // Iniciar servidor HTTP en el hilo principal
        Handler(Looper.getMainLooper()).post {
            val serverStarted = mainActivity.startHiddenServiceServer()
            if (serverStarted) {
                addLog("Hidden service HTTP server started on port 9001")
            } else {
                addLog("Failed to start hidden service HTTP server")
            }
        }
    }

    private fun notifyOnionAddressGenerated(onionAddress: String) {
        try {
            Handler(Looper.getMainLooper()).post {
                mainActivity.notifyNewOnionAddress(onionAddress)
            }
        } catch (e: Exception) {
            addLog("ERROR notifying onion address: ${e.message}")
        }
    }

}
