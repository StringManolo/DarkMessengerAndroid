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
      return gson.toJson(defaultDataManager.getDefaultContacts())
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
          val general = settings["darkmessenger"] as? Map<*, *>
          val generalSettings = general?.get("general") as? Map<*, *>

          val username = generalSettings?.get("username") as? Map<*, *>
          val onionAddress = generalSettings?.get("onionAddress") as? Map<*, *>

          if (username != null) {
            userData = userData.copy(username = username["value"] as? String ?: userData.username)
          }

          if (onionAddress != null) {
            userData = userData.copy(onionAddress = onionAddress["value"] as? String ?: userData.onionAddress)
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

  data class UserData(
    var username: String,
    var onionAddress: String,
    var contacts: List<Contact>
  )

  data class Contact(
    val name: String,
    val onion: String
  )
}

// Gestor de datos por defecto
class DefaultDataManager(private val context: android.content.Context) {

  fun getAllDefaultData(): Map<String, Any> {
    return mapOf(
      "contacts" to getDefaultContacts(),
      "chats" to getDefaultChats(),
      "settings" to JSONObject(getDefaultSettingsJson()).toMap()
    )
  }

  fun getDefaultContacts(): List<MainActivity.Contact> {
    return try {
      val json = loadAssetFile("data/default_contacts.json")
      val jsonArray = JSONArray(json)

      jsonArray.mapIndexed { index, item ->
        val obj = item as JSONObject
        MainActivity.Contact(
          name = obj.getString("name"),
          onion = obj.getString("onion")
        )
      }
    } catch (e: Exception) {
      e.printStackTrace()
      // Fallback básico
      listOf(
        MainActivity.Contact("StringManolo", "placeholder.onion")
      )
    }
  }

  fun getDefaultChats(): List<Map<String, Any>> {
    return try {
      val json = loadAssetFile("data/default_chats.json")
      val jsonArray = JSONArray(json)

      jsonArray.map { item ->
        val obj = item as JSONObject
        mapOf(
          "id" to obj.getInt("id"),
          "contactId" to obj.getInt("contactId"),
          "lastMessage" to obj.getString("lastMessage"),
          "unread" to obj.getBoolean("unread")
        )
      }
    } catch (e: Exception) {
      e.printStackTrace()
      emptyList()
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
}
