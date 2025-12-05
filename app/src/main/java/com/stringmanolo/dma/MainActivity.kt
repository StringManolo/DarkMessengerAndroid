package com.stringmanolo.dma

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import java.lang.Process
import java.util.concurrent.Executors

// Data classes (mantener las existentes y añadir nuevas)
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

  override fun onDestroy() {
    super.onDestroy()
    torManager.stopTor()
    executor.shutdown()
  }

  private fun loadUserData() {
    val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)

    val savedUsername = sharedPref.getString("username", null)
    val savedOnion = sharedPref.getString("onionAddress", null)
    val savedContacts = sharedPref.getString("contacts", null)

    // Cargar estado de Tor
    val torEnabled = sharedPref.getBoolean("tor_enabled", false)

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

    // Iniciar Tor si estaba habilitado
    if (torEnabled) {
      executor.execute {
        torManager.startTor()
      }
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

    // Nuevas funciones para Tor
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
      val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
      sharedPref.edit().putBoolean("tor_enabled", enabled).apply()
    }

    @JavascriptInterface
    fun isTorEnabled(): Boolean {
      val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
      return sharedPref.getBoolean("tor_enabled", false)
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

  private fun showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
  }
}

// Gestor de Tor
class TorManager(private val context: android.content.Context) {
  private var torProcess: Process? = null
  private val logs = mutableListOf<String>()
  private var isRunning = false
  private val maxLogs = 1000

  fun startTor(): Boolean {
    return try {
      // Detener si ya está corriendo
      stopTor()

      // Crear directorio para datos de Tor
      val dataDir = File(context.filesDir, "tor_data")
      if (!dataDir.exists()) {
        dataDir.mkdirs()
      }

      // Copiar binario de Tor para la arquitectura correcta
      val torBinary = copyTorBinary()

      // Crear archivo torrc
      val torrcFile = createTorrcFile(dataDir)

      // Iniciar proceso
      val command = listOf(
        torBinary.absolutePath,
        "-f", torrcFile.absolutePath
      )

      val processBuilder = ProcessBuilder(command)
      .directory(dataDir)
      .redirectErrorStream(true)

      torProcess = processBuilder.start()
      isRunning = true

      // Leer logs en un hilo separado
      Thread {
        val reader = BufferedReader(InputStreamReader(torProcess?.inputStream))
        var line: String?
        while (torProcess?.isAlive == true) {
          line = reader.readLine()
          if (line != null) {
            addLog("TOR: $line")
          }
        }
        addLog("TOR: Process ended")
        isRunning = false
      }.start()

      true
    } catch (e: Exception) {
      addLog("ERROR: ${e.message}")
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

  private fun addLog(message: String) {
    synchronized(logs) {
      logs.add("${System.currentTimeMillis()}: $message")
      if (logs.size > maxLogs) {
        logs.removeAt(0)
      }
    }
  }

  private fun copyTorBinary(): File {
    val abi = getABI()
    val assetPath = "tor/$abi/tor"

    val torFile = File(context.filesDir, "tor")

    // Copiar desde assets
    context.assets.open(assetPath).use { input ->
      FileOutputStream(torFile).use { output ->
        input.copyTo(output)
      }
    }

    // Dar permisos de ejecución
    torFile.setReadable(true)
    torFile.setWritable(true, true)
    torFile.setExecutable(true)

    return torFile
  }

  private fun getABI(): String {
    return when {
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
  }

  private fun createTorrcFile(dataDir: File): File {
    val torrcFile = File(dataDir, "torrc")

    if (!torrcFile.exists()) {
      // Leer torrc por defecto desde assets
      try {
        val defaultTorrc = context.assets.open("tor/torrc.default").bufferedReader().use { it.readText() }
        torrcFile.writeText(defaultTorrc)
      } catch (e: Exception) {
        // Crear torrc básico
        torrcFile.writeText("""
        SocksPort 9050
        ControlPort 9051
        DataDirectory ${dataDir.absolutePath}
        Log notice stdout
        AvoidDiskWrites 1
        """.trimIndent())
      }
    }

    return torrcFile
  }
}

// Gestor de datos por defecto
class DefaultDataManager(private val context: android.content.Context) {

  fun getAllDefaultData(): Map<String, Any> {
    return mapOf(
      "contacts" to getDefaultContactsFull(),
      "defaultChats" to getDefaultChats(),
      "defaultMessages" to getDefaultMessages(),
      "settings" to getDefaultSettingsMap()
    )
  }

  // Para UserData (solo name y onion)
  fun getDefaultContacts(): List<Contact> {
    return getDefaultContactsFull().map { map ->
      Contact(
        name = map["name"] as String,
        onion = map["onion"] as String
      )
    }
  }

  // Para JavaScript (todos los campos)
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
      // JSON de settings por defecto mínimo
      """{
        "darkmessenger": {
          "general": {
            "username": {"value": "Anonymous", "default": "Anonymous", "type": "text"},
            "onionAddress": {"value": "placeholder.onion", "default": "placeholder.onion", "type": "text"}
          }
        },
        "torrc": {}
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
