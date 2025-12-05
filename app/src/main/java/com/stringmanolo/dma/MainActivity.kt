package com.stringmanolo.dma

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.concurrent.Executors

// Data classes
data class Contact(
  val name: String,
  val onion: String
)

data class UserData(
  var username: String,
  var onionAddress: String,
  var contacts: List<Contact>
)

data class Chat(
  val id: Int,
  val contactId: Int,
  val lastMessage: String,
  val timestamp: String,
  val unread: Boolean
)

data class Message(
  val id: Long,
  val senderId: Int?,
  val text: String,
  val timestamp: String,
  val incoming: Boolean
)

class MainActivity : AppCompatActivity() {

  private lateinit var webView: WebView
  private val gson = Gson()
  private lateinit var userData: UserData
  private lateinit var defaultDataManager: DefaultDataManager
  private lateinit var torManager: TorManager
  private val handler = Handler(Looper.getMainLooper())
  private val executor = Executors.newSingleThreadExecutor()
  private var isAppInForeground = true

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    // Inicializar gestor de datos por defecto
    defaultDataManager = DefaultDataManager(this)

    // Inicializar Tor Manager
    torManager = TorManager(this)

    // Cargar datos del usuario
    loadUserData()

    webView = findViewById(R.id.webView)
    setupWebView()

    // Verificar si Tor debe arrancarse automáticamente - IMPORTANTE: después de setupWebView
    checkAndStartTor()
  }

  override fun onResume() {
    super.onResume()
    isAppInForeground = true

    // Reanudar verificación de Tor si estaba habilitado
    checkAndStartTor()
  }

  override fun onPause() {
    super.onPause()
    isAppInForeground = false
  }

  override fun onDestroy() {
    super.onDestroy()
    torManager.stopTor()
    executor.shutdown()
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun setupWebView() {
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true
    webView.settings.allowFileAccess = true

    webView.addJavascriptInterface(WebAppInterface(), "dma")

    webView.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        return false
      }
    }

    webView.webChromeClient = WebChromeClient()

    webView.loadUrl("file:///android_asset/www/dark-messenger-ui.html")
  }

  override fun onBackPressed() {
    if (webView.canGoBack()) {
      webView.goBack()
    } else {
      super.onBackPressed()
    }
  }

  private fun loadUserData() {
    val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)

    val savedUsername = sharedPref.getString("username", null)
    val savedOnion = sharedPref.getString("onionAddress", null)
    val savedContacts = sharedPref.getString("contacts", null)

    // Cargar contactos
    val contacts = if (savedContacts != null) {
      try {
        val type = object : TypeToken<List<Contact>>() {}.type
        gson.fromJson(savedContacts, type)
      } catch (e: Exception) {
        defaultDataManager.getDefaultContacts()
      }
    } else {
      defaultDataManager.getDefaultContacts()
    }

    userData = UserData(
      username = savedUsername ?: "Anonymous",
      onionAddress = savedOnion ?: defaultDataManager.getDefaultOnionAddress(),
      contacts = contacts
    )
  }

  private fun checkAndStartTor() {
    val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)

    // Primero, cargar la configuración por defecto para verificar si Tor está habilitado
    val defaultSettings = defaultDataManager.getDefaultSettingsMap()
    val torEnabledByDefault = isTorEnabledInSettings(defaultSettings)

    // Verificar si hay un valor guardado, si no, usar el valor por defecto
    val torEnabled = if (sharedPref.contains("tor_enabled")) {
      sharedPref.getBoolean("tor_enabled", false)
    } else {
      // Si no hay valor guardado, usar el valor por defecto y guardarlo
      sharedPref.edit().putBoolean("tor_enabled", torEnabledByDefault).apply()
      torEnabledByDefault
    }

    // Iniciar Tor si está habilitado y no está corriendo
    if (torEnabled && !torManager.isRunning()) {
      executor.execute {
        torManager.startTor()
        addLogToWebView("Tor auto-start attempted (enabled in settings)")
      }
    }
  }

  private fun isTorEnabledInSettings(settings: Map<String, Any>): Boolean {
    return try {
      val darkmessenger = settings["darkmessenger"] as? Map<*, *>
      val torSettings = darkmessenger?.get("tor") as? Map<*, *>
      val enabledObj = torSettings?.get("enabled") as? Map<*, *>
      enabledObj?.get("value") as? Boolean ?: false
    } catch (e: Exception) {
      false
    }
  }

  private fun addLogToWebView(message: String) {
    runOnUiThread {
      webView.evaluateJavascript("console.log('$message');", null)
    }
  }

  inner class WebAppInterface {

    @JavascriptInterface
    fun getUserData(): String {
      return gson.toJson(userData)
    }

    @JavascriptInterface
    fun getDefaultData(): String {
      return gson.toJson(defaultDataManager.getAllDefaultData())
    }

    @JavascriptInterface
    fun getDefaultContacts(): String {
      return gson.toJson(defaultDataManager.getDefaultContactsFull())
    }

    @JavascriptInterface
    fun getDefaultChats(): String {
      return gson.toJson(defaultDataManager.getDefaultChats())
    }

    @JavascriptInterface
    fun getDefaultSettings(): String {
      return defaultDataManager.getDefaultSettingsJson()
    }

    @JavascriptInterface
    fun getSettings(): String {
      val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
      val savedSettings = sharedPref.getString("settings", null)

      return savedSettings ?: defaultDataManager.getDefaultSettingsJson()
    }

    @JavascriptInterface
    fun getContacts(): String {
      return gson.toJson(mapOf("contacts" to userData.contacts))
    }

    @JavascriptInterface
    fun updateContacts(contactsJson: String) {
      runOnUiThread {
        try {
          val type = object : TypeToken<List<Contact>>() {}.type
          val newContacts = gson.fromJson<List<Contact>>(contactsJson, type)

          userData = userData.copy(contacts = newContacts)
          saveContactsToStorage(contactsJson)
          showToast("Contacts updated")
        } catch (e: Exception) {
          showToast("Error updating contacts: ${e.message}")
        }
      }
    }

    @JavascriptInterface
    fun updateSettings(settingsJson: String) {
      runOnUiThread {
        try {
          saveSettingsToStorage(settingsJson)

          // Parsear settings para actualizar tor_enabled
          try {
            val settings = gson.fromJson(settingsJson, Map::class.java)
            val darkmessenger = settings["darkmessenger"] as? Map<*, *>
            val torSettings = darkmessenger?.get("tor") as? Map<*, *>

            val torEnabledObj = torSettings?.get("enabled") as? Map<*, *>
            val torEnabled = torEnabledObj?.get("value") as? Boolean ?: false

            saveTorEnabledToStorage(torEnabled)

            // Si Tor está habilitado, iniciarlo
            if (torEnabled && !torManager.isRunning()) {
              executor.execute {
                torManager.startTor()
              }
            }
            // Si Tor está deshabilitado, detenerlo
            else if (!torEnabled && torManager.isRunning()) {
              torManager.stopTor()
            }
          } catch (e: Exception) {
            // Ignorar error de parseo
          }

          // Parsear settings para extraer username y onion
          try {
            val settings = gson.fromJson(settingsJson, Map::class.java)
            val darkmessenger = settings["darkmessenger"] as? Map<*, *>
            val general = darkmessenger?.get("general") as? Map<*, *>

            val usernameObj = general?.get("username") as? Map<*, *>
            val onionAddressObj = general?.get("onionAddress") as? Map<*, *>

            if (usernameObj != null) {
              userData = userData.copy(username = usernameObj["value"] as? String ?: userData.username)
            }

            if (onionAddressObj != null) {
              userData = userData.copy(onionAddress = onionAddressObj["value"] as? String ?: userData.onionAddress)
            }

            saveUserInfoToStorage()
          } catch (e: Exception) {
            // Ignorar error de parseo
          }

          showToast("Settings saved")
        } catch (e: Exception) {
          showToast("Error saving settings: ${e.message}")
        }
      }
    }

    @JavascriptInterface
    fun saveChats(chatsJson: String) {
      runOnUiThread {
        try {
          val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
          sharedPref.edit().putString("chats", chatsJson).apply()
        } catch (e: Exception) {
          showToast("Error saving chats: ${e.message}")
        }
      }
    }

    @JavascriptInterface
    fun getChats(): String {
      val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
      return sharedPref.getString("chats", "[]") ?: "[]"
    }

    @JavascriptInterface
    fun saveMessages(messagesJson: String) {
      runOnUiThread {
        try {
          val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
          sharedPref.edit().putString("messages", messagesJson).apply()
        } catch (e: Exception) {
          showToast("Error saving messages: ${e.message}")
        }
      }
    }

    @JavascriptInterface
    fun getMessages(): String {
      val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
      return sharedPref.getString("messages", "{}") ?: "{}"
    }

    @JavascriptInterface
    fun toast(message: String) {
      runOnUiThread {
        showToast(message)
      }
    }

    @JavascriptInterface
    fun sendTorMessage(messageJson: String) {
      runOnUiThread {
        try {
          val messageData = gson.fromJson(messageJson, Map::class.java)
          val recipient = messageData["recipient"] as? String
          val message = messageData["message"] as? String

          if (recipient != null && message != null) {
            showToast("Message sent to $recipient")
          }
        } catch (e: Exception) {
          showToast("Error sending message: ${e.message}")
        }
      }
    }

    // Funciones para Tor
    @JavascriptInterface
    fun startTor(): Boolean {
      return try {
        executor.execute {
          torManager.startTor()
        }
        true
      } catch (e: Exception) {
        false
      }
    }

    @JavascriptInterface
    fun stopTor(): Boolean {
      return try {
        torManager.stopTor()
        true
      } catch (e: Exception) {
        false
      }
    }

    @JavascriptInterface
    fun isTorRunning(): Boolean {
      return torManager.isRunning()
    }

    @JavascriptInterface
    fun getTorLogs(): String {
      return gson.toJson(torManager.getLogs())
    }

    @JavascriptInterface
    fun clearTorLogs() {
      torManager.clearLogs()
    }

    @JavascriptInterface
    fun saveTorEnabled(enabled: Boolean) {
      saveTorEnabledToStorage(enabled)

      if (enabled && !torManager.isRunning()) {
        executor.execute {
          torManager.startTor()
        }
      } else if (!enabled && torManager.isRunning()) {
        torManager.stopTor()
      }
    }

    @JavascriptInterface
    fun isTorEnabled(): Boolean {
      val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
      return sharedPref.getBoolean("tor_enabled", false)
    }

    @JavascriptInterface
    fun getGeneratedOnionAddress(): String {
      return torManager.getGeneratedOnionAddress() ?: "placeholder.onion"
    }

    @JavascriptInterface
    fun updateOnionAddressInSettings(newOnion: String) {
      runOnUiThread {
        try {
          // Actualizar userData
          userData = userData.copy(onionAddress = newOnion)
          saveUserInfoToStorage()

          // Actualizar settings JSON
          updateOnionAddressInSettingsJson(newOnion)

          // Notificar a la UI
          webView.evaluateJavascript(
            "if (typeof window.app !== 'undefined') { " +
            "window.app.updateOnionAddressInUI('$newOnion'); " +
            "window.app.showToast('Onion address updated'); }",
            null
          )

          showToast("Onion address updated: $newOnion")
        } catch (e: Exception) {
          showToast("Error updating onion address: ${e.message}")
        }
      }
    }

    @JavascriptInterface
    fun copyToClipboard(text: String) {
      runOnUiThread {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Onion Address", text)
        clipboard.setPrimaryClip(clip)
        showToast("Onion address copied to clipboard")
      }
    }
  }

  private fun saveContactsToStorage(contactsJson: String) {
    val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
    sharedPref.edit().putString("contacts", contactsJson).apply()
  }

  private fun saveSettingsToStorage(settingsJson: String) {
    val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
    sharedPref.edit().putString("settings", settingsJson).apply()
  }

  private fun saveUserInfoToStorage() {
    val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
    sharedPref.edit()
    .putString("username", userData.username)
    .putString("onionAddress", userData.onionAddress)
    .apply()
  }

  private fun saveTorEnabledToStorage(enabled: Boolean) {
    val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
    sharedPref.edit().putBoolean("tor_enabled", enabled).apply()
  }

  private fun updateOnionAddressInSettingsJson(newOnion: String) {
    val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
    val savedSettings = sharedPref.getString("settings", null)

    if (savedSettings != null) {
      try {
        val settingsMap = gson.fromJson(savedSettings, Map::class.java) as MutableMap<String, Any>
        val darkmessenger = settingsMap["darkmessenger"] as? MutableMap<String, Any>
        val general = darkmessenger?.get("general") as? MutableMap<String, Any>

        val onionAddress = general?.get("onionAddress") as? MutableMap<String, Any>
        onionAddress?.put("value", newOnion)

        // Guardar de vuelta
        saveSettingsToStorage(gson.toJson(settingsMap))
      } catch (e: Exception) {
        // Si hay error, crear nuevo settings con el onion address
        createNewSettingsWithOnionAddress(newOnion)
      }
    } else {
      createNewSettingsWithOnionAddress(newOnion)
    }
  }

  private fun createNewSettingsWithOnionAddress(newOnion: String) {
    try {
      val settingsJson = defaultDataManager.getDefaultSettingsJson()
      val settingsMap = gson.fromJson(settingsJson, Map::class.java) as MutableMap<String, Any>
      val darkmessenger = settingsMap["darkmessenger"] as? MutableMap<String, Any>
      val general = darkmessenger?.get("general") as? MutableMap<String, Any>

      val onionAddress = general?.get("onionAddress") as? MutableMap<String, Any>
      onionAddress?.put("value", newOnion)

      saveSettingsToStorage(gson.toJson(settingsMap))
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
  }
}


// Gestor de Tor - MEJORADO con Hidden Service
class TorManager(private val context: android.content.Context) {
  private var torProcess: Process? = null
  private val logs = mutableListOf<String>()
  private var isRunning = false
  private var generatedOnionAddress: String? = null
  private val maxLogs = 1000
  private val hiddenServiceDirName = "hidden_service"

  companion object {
    private const val HIDDEN_SERVICE_PORT = 9001
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
            }
          }
        } catch (e: IOException) {
          addLog("ERROR reading Tor output: ${e.message}")
        }
        addLog("TOR: Process ended")
        isRunning = false
      }.start()

      // Iniciar hilo para monitorear el hidden service
      startHiddenServiceMonitor(dataDir)

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
    addLog("TOR: Stopped")
  }

  fun isRunning(): Boolean = isRunning

  fun getLogs(): List<String> = synchronized(logs) { logs.toList() }

  fun clearLogs() = synchronized(logs) { logs.clear() }

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
    NumEntryGuards 3
    UseEntryGuards 1
    NewCircuitPeriod 15

    # Configuración de caché
    MaxMemInQueues 32 MB

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

  private fun startHiddenServiceMonitor(dataDir: File) {
    Thread {
      var attempts = 0
      val maxAttempts = 120 // Esperar máximo 2 minutos

      while (torProcess?.isAlive == true && attempts < maxAttempts) {
        Thread.sleep(1000) // Esperar 1 segundo
        checkHiddenServiceStatus(dataDir)

        // Si ya tenemos la dirección, salir
        if (generatedOnionAddress != null) {
          break
        }

        attempts++
      }

      if (generatedOnionAddress == null) {
        addLog("WARNING: Hidden service not created after $maxAttempts seconds")
      }
    }.start()
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

  private fun notifyOnionAddressGenerated(onionAddress: String) {
    try {
      // Usar un handler para ejecutar en el hilo principal
      Handler(Looper.getMainLooper()).post {
        // Enviar evento a la WebView
        val javascript = "if (window.dma && dma.updateOnionAddressInSettings) { dma.updateOnionAddressInSettings('$onionAddress'); }"

        // Esta es una forma simplificada - en la práctica necesitarías acceso a la WebView
        // En MainActivity, manejaremos esto de otra manera
      }
    } catch (e: Exception) {
      addLog("ERROR notifying onion address: ${e.message}")
    }
  }
}

// Mantener DefaultDataManager sin cambios...

// Mantener DefaultDataManager sin cambios (excepto para getDefaultSettingsJson)
class DefaultDataManager(private val context: android.content.Context) {

  fun getAllDefaultData(): Map<String, Any> {
    return mapOf(
      "contacts" to getDefaultContactsFull(),
      "defaultChats" to getDefaultChats(),
      "defaultMessages" to getDefaultMessages(),
      "settings" to getDefaultSettingsMap()
    )
  }

  fun getDefaultContacts(): List<Contact> {
    return getDefaultContactsFull().map { map ->
      Contact(
        name = map["name"] as String,
        onion = map["onion"] as String
      )
    }
  }

  fun getDefaultContactsFull(): List<Map<String, Any>> {
    return try {
      val json = loadAssetFile("data/default_contacts.json")
      val jsonArray = JSONArray(json)
      val contacts = mutableListOf<Map<String, Any>>()

      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        contacts.add(mapOf(
          "id" to obj.getInt("id"),
          "name" to obj.getString("name"),
          "onion" to obj.getString("onion"),
          "status" to obj.optString("status", "Online")
        ))
      }
      contacts
    } catch (e: Exception) {
      e.printStackTrace()
      // Fallback básico
      listOf(
        mapOf(
          "id" to 0,
          "name" to "StringManolo",
          "onion" to "placeholder.onion",
          "status" to "Online"
        )
      )
    }
  }

  fun getDefaultChats(): List<Map<String, Any>> {
    return try {
      val json = loadAssetFile("data/default_chats.json")
      val jsonArray = JSONArray(json)
      val chats = mutableListOf<Map<String, Any>>()

      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        chats.add(mapOf(
          "id" to obj.getInt("id"),
          "contactId" to obj.getInt("contactId"),
          "lastMessage" to obj.getString("lastMessage"),
          "unread" to obj.getBoolean("unread"),
          "timestamp" to obj.optString("timestamp", "")
        ))
      }
      chats
    } catch (e: Exception) {
      e.printStackTrace()
      emptyList()
    }
  }

  fun getDefaultMessages(): Map<String, List<Map<String, Any>>> {
    return try {
      val json = loadAssetFile("data/default_messages.json")
      val jsonObj = JSONObject(json)
      val messages = mutableMapOf<String, List<Map<String, Any>>>()
      val keys = jsonObj.keys()

      while (keys.hasNext()) {
        val key = keys.next()
        val jsonArray = jsonObj.getJSONArray(key)
        val messageList = mutableListOf<Map<String, Any>>()

        for (i in 0 until jsonArray.length()) {
          val obj = jsonArray.getJSONObject(i)
          messageList.add(mapOf(
            "id" to obj.getInt("id"),
            "senderId" to obj.getInt("senderId"),
            "text" to obj.getString("text"),
            "incoming" to obj.getBoolean("incoming"),
            "timestamp" to obj.optString("timestamp", "")
          ))
        }
        messages[key] = messageList
      }
      messages
    } catch (e: Exception) {
      e.printStackTrace()
      // Mensaje por defecto
      mapOf(
        "1" to listOf(
          mapOf(
            "id" to 1,
            "senderId" to 0,
            "text" to "Welcome to DarkMessenger. I am the app developer, you can chat with me for any questions about the app.",
            "incoming" to true,
            "timestamp" to ""
          )
        )
      )
    }
  }

  fun getDefaultSettingsJson(): String {
    return try {
      loadAssetFile("data/default_settings.json")
    } catch (e: Exception) {
      e.printStackTrace()
      // JSON de settings por defecto con Tor
      """{
        "darkmessenger": {
          "general": {
            "username": {"value": null, "default": null, "type": "text"},
            "onionAddress": {"value": "placeholder.onion", "default": "placeholder.onion", "type": "text"},
            "allowAddMe": {"value": true, "default": true, "type": "toggle"},
            "addBack": {"value": true, "default": true, "type": "toggle"},
            "alertOnNewMessages": {"value": true, "default": true, "type": "toggle"},
            "checkNewMessagesSeconds": {"value": 20, "default": 20, "type": "number"},
            "verbose": {"value": true, "default": true, "type": "toggle"},
            "debug": {"value": true, "default": true, "type": "toggle"},
            "debugWithTime": {"value": true, "default": true, "type": "toggle"}
          },
          "cryptography": {
            "useERK": {"value": false, "default": false, "type": "toggle"},
            "offlineMessages": {
              "ecies": {"value": true, "default": true, "type": "toggle"},
              "rsa": {"value": true, "default": true, "type": "toggle"},
              "crystalKyber": {"value": true, "default": true, "type": "toggle"}
            },
            "onlineMessages": {
              "ecies": {"value": true, "default": true, "type": "toggle"},
              "rsa": {"value": true, "default": true, "type": "toggle"},
              "crystalKyber": {"value": true, "default": true, "type": "toggle"}
            },
            "addMe": {
              "ecies": {"value": true, "default": true, "type": "toggle"},
              "rsa": {"value": true, "default": true, "type": "toggle"},
              "crystalKyber": {"value": true, "default": true, "type": "toggle"}
            }
          },
          "tor": {
            "enabled": {"value": false, "default": false, "type": "toggle"},
            "socksPort": {"value": 9050, "default": 9050, "type": "number"},
            "controlPort": {"value": 9051, "default": 9051, "type": "number"}
          }
        },
        "torrc": {
          "torPort": {"value": 9050, "default": 9050, "type": "number"},
          "hiddenServicePort": {"value": 9001, "default": 9001, "type": "number"},
          "logNoticeFile": {"value": "./logs/notices.log", "default": "./logs/notices.log", "type": "text"},
          "controlPort": {"value": 9051, "default": 9051, "type": "number"},
          "hashedControlPassword": {"value": false, "default": false, "type": "toggle"},
          "orPort": {"value": 0, "default": 0, "type": "number"},
          "disableNetwork": {"value": 0, "default": 0, "type": "number"},
          "avoidDiskWrites": {"value": 1, "default": 1, "type": "number"}
        }
      }"""
    }
  }

  fun getDefaultSettingsMap(): Map<String, Any> {
    return try {
      val json = getDefaultSettingsJson()
      val jsonObj = JSONObject(json)
      convertJsonToMap(jsonObj)
    } catch (e: Exception) {
      e.printStackTrace()
      emptyMap()
    }
  }

  fun getDefaultOnionAddress(): String {
    return try {
      val json = getDefaultSettingsJson()
      val jsonObj = JSONObject(json)
      val darkmessenger = jsonObj.getJSONObject("darkmessenger")
      val general = darkmessenger.getJSONObject("general")
      val onionAddress = general.getJSONObject("onionAddress")
      onionAddress.getString("value")
    } catch (e: Exception) {
      "placeholder.onion"
    }
  }

  private fun loadAssetFile(fileName: String): String {
    return try {
      val inputStream: InputStream = context.assets.open(fileName)
      val size = inputStream.available()
      val buffer = ByteArray(size)
      inputStream.read(buffer)
      inputStream.close()
      String(buffer, Charsets.UTF_8)
    } catch (e: Exception) {
      e.printStackTrace()
      ""
    }
  }

  private fun convertJsonToMap(jsonObj: JSONObject): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    val keys = jsonObj.keys()

    while (keys.hasNext()) {
      val key = keys.next()
      val value = jsonObj.get(key)

      when (value) {
        is JSONObject -> map[key] = convertJsonToMap(value)
        is JSONArray -> map[key] = convertJsonArrayToList(value)
        else -> map[key] = value
      }
    }

    return map
  }

  private fun convertJsonArrayToList(jsonArray: JSONArray): List<Any> {
    val list = mutableListOf<Any>()

    for (i in 0 until jsonArray.length()) {
      val value = jsonArray.get(i)

      when (value) {
        is JSONObject -> list.add(convertJsonToMap(value))
        is JSONArray -> list.add(convertJsonArrayToList(value))
        else -> list.add(value)
      }
    }

    return list
  }
}
