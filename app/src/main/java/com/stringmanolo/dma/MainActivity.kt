package com.stringmanolo.dma

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.widget.Toast
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

  private lateinit var webView: WebView

  // Datos de ejemplo que se sincronizarán con la interfaz web
  private val userData = UserData()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    webView = findViewById(R.id.webview)
    setupWebView()
  }

  private fun setupWebView() {
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true
    webView.settings.allowFileAccess = true

    webView.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        return false
      }
    }

    webView.webChromeClient = WebChromeClient()
    webView.addJavascriptInterface(WebAppInterface(this), "dma")

    // Cargar la interfaz principal
    webView.loadUrl("file:///android_asset/dark-messenger-ui.html")
  }

  // Interfaz JavaScript
  inner class WebAppInterface(private val context: android.content.Context) {

    @JavascriptInterface
    fun toast(message: String) {
      runOnUiThread {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
      }
    }

    @JavascriptInterface
    fun getUserData(): String {
      return userData.toJson()
    }

    @JavascriptInterface
    fun updateUserData(jsonData: String) {
      userData.updateFromJson(jsonData)
      toast("User data updated")
    }

    @JavascriptInterface
    fun updateSettings(jsonData: String) {
      userData.updateSettings(jsonData)
      toast("Settings saved")
    }

    @JavascriptInterface
    fun updateContacts(jsonData: String) {
      userData.updateContacts(jsonData)
      toast("Contacts updated")
    }
  }

  override fun onBackPressed() {
    if (webView.canGoBack()) {
      webView.goBack()
    } else {
      super.onBackPressed()
    }
  }

  // Clase para manejar datos del usuario
  class UserData {
    var username: String? = null
    var onionAddress: String = "placeholder.onion"
    var allowAddMe: Boolean = true
    var addBack: Boolean = true
    var alertOnNewMessages: Boolean = true
    var checkNewMessagesSeconds: Int = 20
    var verbose: Boolean = true
    var debug: Boolean = true
    var debugWithTime: Boolean = true

    // Configuración Tor
    var torPort: Int = 9050
    var hiddenServicePort: Int = 9001
    var logNoticeFile: String = "./logs/notices.log"
    var controlPort: Int = 9051
    var hashedControlPassword: Boolean = false
    var orPort: Int = 0
    var disableNetwork: Int = 0
    var avoidDiskWrites: Int = 1

    // Contactos iniciales
    val contacts = mutableListOf(
      Contact("StringManolo", "placeholder.onion")
    )

    fun toJson(): String {
      val json = JSONObject()
      json.put("username", username)
      json.put("onionAddress", onionAddress)
      json.put("allowAddMe", allowAddMe)
      json.put("addBack", addBack)
      json.put("alertOnNewMessages", alertOnNewMessages)
      json.put("checkNewMessagesSeconds", checkNewMessagesSeconds)
      json.put("verbose", verbose)
      json.put("debug", debug)
      json.put("debugWithTime", debugWithTime)

      val torConfig = JSONObject()
      torConfig.put("torPort", torPort)
      torConfig.put("hiddenServicePort", hiddenServicePort)
      torConfig.put("logNoticeFile", logNoticeFile)
      torConfig.put("controlPort", controlPort)
      torConfig.put("hashedControlPassword", hashedControlPassword)
      torConfig.put("orPort", orPort)
      torConfig.put("disableNetwork", disableNetwork)
      torConfig.put("avoidDiskWrites", avoidDiskWrites)
      json.put("torConfig", torConfig)

      val contactsArray = JSONObject()
      contacts.forEachIndexed { index, contact ->
        val contactJson = JSONObject()
        contactJson.put("name", contact.name)
        contactJson.put("onion", contact.onion)
        contactsArray.put(index.toString(), contactJson)
      }
      json.put("contacts", contactsArray)

      return json.toString()
    }

    fun updateFromJson(jsonData: String) {
      val json = JSONObject(jsonData)
      username = if (json.has("username")) json.getString("username") else null
      onionAddress = json.optString("onionAddress", "placeholder.onion")
      allowAddMe = json.optBoolean("allowAddMe", true)
      addBack = json.optBoolean("addBack", true)
      alertOnNewMessages = json.optBoolean("alertOnNewMessages", true)
      checkNewMessagesSeconds = json.optInt("checkNewMessagesSeconds", 20)
      verbose = json.optBoolean("verbose", true)
      debug = json.optBoolean("debug", true)
      debugWithTime = json.optBoolean("debugWithTime", true)
    }

    fun updateSettings(jsonData: String) {
      val json = JSONObject(jsonData)

      // Actualizar configuración general
      updateFromJson(jsonData)

      // Actualizar configuración Tor
      if (json.has("torConfig")) {
        val torConfig = json.getJSONObject("torConfig")
        torPort = torConfig.optInt("torPort", 9050)
        hiddenServicePort = torConfig.optInt("hiddenServicePort", 9001)
        logNoticeFile = torConfig.optString("logNoticeFile", "./logs/notices.log")
        controlPort = torConfig.optInt("controlPort", 9051)
        hashedControlPassword = torConfig.optBoolean("hashedControlPassword", false)
        orPort = torConfig.optInt("orPort", 0)
        disableNetwork = torConfig.optInt("disableNetwork", 0)
        avoidDiskWrites = torConfig.optInt("avoidDiskWrites", 1)
      }
    }

    fun updateContacts(jsonData: String) {
      val json = JSONObject(jsonData)
      if (json.has("contacts")) {
        contacts.clear()
        val contactsJson = json.getJSONObject("contacts")
        for (i in 0 until contactsJson.length()) {
          val contactJson = contactsJson.getJSONObject(i.toString())
          contacts.add(
            Contact(
              contactJson.getString("name"),
              contactJson.getString("onion")
            )
          )
        }
      }
    }
  }

  data class Contact(val name: String, val onion: String)
}
