package com.stringmanolo.dma

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

// Data classes fuera de MainActivity para mejor acceso
data class Contact(
  val name: String,
  val onion: String
)

data class UserData(
  var username: String,
  var onionAddress: String,
  var contacts: List<Contact>
)

class MainActivity : AppCompatActivity() {

  private lateinit var webView: WebView
  private val gson = Gson()
  private lateinit var userData: UserData
  private lateinit var defaultDataManager: DefaultDataManager

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    // Inicializar gestor de datos por defecto
    defaultDataManager = DefaultDataManager(this)

    // Cargar datos del usuario (mezcla de guardados y por defecto)
    loadUserData()

    webView = findViewById(R.id.webView)
    setupWebView()
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun setupWebView() {
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true

    webView.addJavascriptInterface(WebAppInterface(), "dma")

    webView.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        return false
      }
    }

    webView.webChromeClient = WebChromeClient()

    // Cargar desde la nueva ubicación
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

    // Cargar contactos (guardados o por defecto)
    val contacts = if (savedContacts != null) {
      try {
        val contactsData = gson.fromJson(savedContacts, Array<Contact>::class.java)
        contactsData.toList()
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
          val contactsList = gson.fromJson(contactsJson, Map::class.java)
          val contacts = contactsList["contacts"] as? List<*>

          if (contacts != null) {
            val newContacts = contacts.map { contactMap ->
              val map = contactMap as Map<*, *>
              Contact(
                name = map["name"] as? String ?: "",
                onion = map["onion"] as? String ?: ""
              )
            }

            userData = userData.copy(contacts = newContacts)
            saveContactsToStorage(gson.toJson(newContacts))
            showToast("Contacts updated")
          }
        } catch (e: Exception) {
          showToast("Error updating contacts: ${e.message}")
        }
      }
    }

    @JavascriptInterface
    fun updateSettings(settingsJson: String) {
      runOnUiThread {
        try {
          val settings = gson.fromJson(settingsJson, Map::class.java)

          // Extraer username y onion si están presentes
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

          saveSettingsToStorage(settingsJson)
          saveUserInfoToStorage()
          showToast("Settings saved")
        } catch (e: Exception) {
          showToast("Error saving settings: ${e.message}")
        }
      }
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

// Gestor de datos por defecto
class DefaultDataManager(private val context: android.content.Context) {
  private val gson = Gson()

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
          "status" to obj.getString("status")
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
          "unread" to obj.getBoolean("unread")
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
            "incoming" to obj.getBoolean("incoming")
          ))
        }
        messages[key] = messageList
      }
      messages
    } catch (e: Exception) {
      e.printStackTrace()
      mapOf(
        "1" to listOf(
          mapOf(
            "id" to 1,
            "senderId" to 0,
            "text" to "Welcome to DarkMessenger. I am the app developer, you can chat with me for any questions about the app.",
            "incoming" to true
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
      "{}"
    }
  }

  fun getDefaultSettingsMap(): Map<String, Any> {
    return try {
      val json = loadAssetFile("data/default_settings.json")
      val jsonObj = JSONObject(json)
      convertJsonToMap(jsonObj)
    } catch (e: Exception) {
      e.printStackTrace()
      emptyMap()
    }
  }

  fun getDefaultOnionAddress(): String {
    return try {
      val json = loadAssetFile("data/default_settings.json")
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
    val inputStream: InputStream = context.assets.open(fileName)
    val size = inputStream.available()
    val buffer = ByteArray(size)
    inputStream.read(buffer)
    inputStream.close()
    return String(buffer, Charsets.UTF_8)
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
