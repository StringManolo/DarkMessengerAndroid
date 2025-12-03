package com.stringmanolo.dma

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.widget.Toast
import org.json.JSONObject
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

  private lateinit var webView: WebView
  private lateinit var userData: UserData

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    webView = findViewById(R.id.webview)
    userData = UserData() // Inicializar UserData
    setupWebView()
  }

  private fun setupWebView() {
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true
    webView.settings.allowFileAccess = true
    webView.settings.allowContentAccess = true

    // Habilitar depuración WebView (solo para debug)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      WebView.setWebContentsDebuggingEnabled(true)
    }

    webView.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        return false
      }

      override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // Inyectar datos iniciales cuando la página termine de cargar
        injectInitialData()
      }
    }

    webView.webChromeClient = WebChromeClient()
    webView.addJavascriptInterface(WebAppInterface(this), "dma")

    // Cargar la interfaz principal
    webView.loadUrl("file:///android_asset/dark-messenger-ui.html")
  }

  private fun injectInitialData() {
    // Inyectar datos iniciales en la página web
    val initScript = """
    // Datos iniciales
    window.initialData = {
      userData: ${userData.toJson()},
      chats: ${getInitialChats()},
      contacts: ${userData.getContactsJson()},
      settings: ${getInitialSettings()}
    };

    // Si la app está definida, inicializarla con estos datos
    if (typeof window.app !== 'undefined') {
      window.app.initializeWithData(window.initialData);
    }

    // Mostrar toast de bienvenida
    if (typeof dma !== 'undefined' && dma.toast) {
      setTimeout(function() {
        dma.toast("Dark Messenger Android Started");
      }, 500);
    }
    """.trimIndent()

    webView.post {
      webView.evaluateJavascript(initScript, null)
    }
  }

  private fun getInitialChats(): String {
    // Crear chat inicial con StringManolo
    val chats = JSONArray()
    val chat = JSONObject().apply {
      put("id", 1)
      put("contactId", 0) // ID de StringManolo
      put("contactName", "StringManolo")
      put("lastMessage", "Welcome to DarkMessenger. I am the app developer, you can chat with me for any questions about the app.")
      put("timestamp", System.currentTimeMillis())
      put("unread", true)
      put("lastMessageTime", "Just now")
    }
    chats.put(chat)
    return chats.toString()
  }

  private fun getInitialSettings(): String {
    val settings = JSONObject()

    // darkmessenger.conf
    val darkmessenger = JSONObject()
    val general = JSONObject().apply {
      put("username", JSONObject.NULL)
      put("onionAddress", "placeholder.onion")
      put("allowAddMe", true)
      put("addBack", true)
      put("alertOnNewMessages", true)
      put("checkNewMessagesSeconds", 20)
      put("verbose", true)
      put("debug", true)
      put("debugWithTime", true)
    }

    val cryptography = JSONObject().apply {
      put("useERK", false)
      put("offlineMessages", JSONObject().apply {
        put("ecies", true)
        put("rsa", true)
        put("crystalKyber", true)
      })
      put("onlineMessages", JSONObject().apply {
        put("ecies", true)
        put("rsa", true)
        put("crystalKyber", true)
      })
      put("addMe", JSONObject().apply {
        put("ecies", true)
        put("rsa", true)
        put("crystalKyber", true)
      })
    }

    darkmessenger.put("general", general)
    darkmessenger.put("cryptography", cryptography)

    // torrc.conf
    val torrc = JSONObject().apply {
      put("torPort", 9050)
      put("hiddenServicePort", 9001)
      put("logNoticeFile", "./logs/notices.log")
      put("controlPort", 9051)
      put("hashedControlPassword", false)
      put("orPort", 0)
      put("disableNetwork", 0)
      put("avoidDiskWrites", 1)
    }

    settings.put("darkmessenger", darkmessenger)
    settings.put("torrc", torrc)

    return settings.toString()
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

      // También actualizar settings en la WebView
      val updateScript = """
      if (typeof window.app !== 'undefined' && window.app.updateSettingsFromKotlin) {
        window.app.updateSettingsFromKotlin($jsonData);
      }
      """.trimIndent()

      webView.post {
        webView.evaluateJavascript(updateScript, null)
      }

      toast("Settings saved")
    }

    @JavascriptInterface
    fun updateContacts(jsonData: String) {
      userData.updateContacts(jsonData)

      // Actualizar contactos en la WebView
      val updateScript = """
      if (typeof window.app !== 'undefined' && window.app.updateContactsFromKotlin) {
        window.app.updateContactsFromKotlin($jsonData);
      }
      """.trimIndent()

      webView.post {
        webView.evaluateJavascript(updateScript, null)
      }

      toast("Contacts updated")
    }

    @JavascriptInterface
    fun sendMessage(chatId: Int, message: String, contactId: Int) {
      // Aquí implementarías la lógica real de envío de mensajes
      runOnUiThread {
        Toast.makeText(context, "Message sent to chat $chatId", Toast.LENGTH_SHORT).show()
      }
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
      json.put("username", if (username != null) username else JSONObject.NULL)
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

      return json.toString()
    }

    fun getContactsJson(): String {
      val contactsArray = JSONArray()
      contacts.forEachIndexed { index, contact ->
        val contactJson = JSONObject()
        contactJson.put("id", index)
        contactJson.put("name", contact.name)
        contactJson.put("onion", contact.onion)
        contactJson.put("status", "Online")
        contactsArray.put(contactJson)
      }
      return contactsArray.toString()
    }

    fun updateFromJson(jsonData: String) {
      val json = JSONObject(jsonData)
      username = if (json.has("username") && !json.isNull("username")) 
      json.getString("username") else null
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
        val contactsArray = json.getJSONArray("contacts")
        for (i in 0 until contactsArray.length()) {
          val contactJson = contactsArray.getJSONObject(i)
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
