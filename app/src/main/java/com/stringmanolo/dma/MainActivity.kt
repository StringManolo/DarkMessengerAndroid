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

class MainActivity : AppCompatActivity() {

  private lateinit var webView: WebView
  private val gson = Gson()
  private lateinit var userData: UserData

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    // Inicializar datos de usuario
    userData = UserData(
      username = "Anonymous",
      onionAddress = "placeholder.onion",
      contacts = emptyList()
    )

    webView = findViewById(R.id.webView)
    setupWebView()
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun setupWebView() {
    // Habilitar JavaScript
    webView.settings.javaScriptEnabled = true

    // Habilitar localStorage
    webView.settings.domStorageEnabled = true

    // Agregar interfaz JavaScript-Kotlin
    webView.addJavascriptInterface(WebAppInterface(), "dma")

    // Configurar WebViewClient para manejar URLs internas
    webView.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        // Manejar todas las URLs dentro del WebView
        return false
      }
    }

    // Configurar WebChromeClient para soporte de JavaScript avanzado
    webView.webChromeClient = WebChromeClient()

    // Cargar la interfaz HTML
    webView.loadUrl("file:///android_asset/index.html")
  }

  override fun onBackPressed() {
    if (webView.canGoBack()) {
      webView.goBack()
    } else {
      super.onBackPressed()
    }
  }

  /**
   * Clase que sirve como interfaz entre JavaScript y Kotlin
   * Los métodos anotados con @JavascriptInterface son accesibles desde JavaScript
   */
    inner class WebAppInterface {

      /**
       * Obtener datos del usuario para inyectar en la interfaz web
       * @return JSON con datos del usuario
       */
        @JavascriptInterface
        fun getUserData(): String {
          val userJson = gson.toJson(userData)
          return userJson
        }

        /**
         * Actualizar los contactos desde la interfaz web
         * @param contactsJson JSON con la lista de contactos
         */
        @JavascriptInterface
        fun updateContacts(contactsJson: String) {
          runOnUiThread {
            try {
              // Parsear los contactos recibidos desde JavaScript
              val contactsList = gson.fromJson(contactsJson, Map::class.java)
              val contacts = contactsList["contacts"] as? List<*>

              if (contacts != null) {
                // Actualizar los datos del usuario
                val newContacts = contacts.map { contactMap ->
                  val map = contactMap as Map<*, *>
                  Contact(
                    name = map["name"] as? String ?: "",
                    onion = map["onion"] as? String ?: ""
                  )
                }

                userData = userData.copy(contacts = newContacts)

                // Aquí puedes guardar los contactos en SharedPreferences o Room Database
                saveContactsToStorage(contactsJson)

                showToast("Contacts updated")
              }
            } catch (e: JsonSyntaxException) {
              showToast("Error parsing contacts: ${e.message}")
            } catch (e: Exception) {
              showToast("Error updating contacts: ${e.message}")
            }
          }
        }

        /**
         * Actualizar configuraciones desde la interfaz web
         * @param settingsJson JSON con las configuraciones
         */
        @JavascriptInterface
        fun updateSettings(settingsJson: String) {
          runOnUiThread {
            try {
              // Parsear configuraciones
              val settings = gson.fromJson(settingsJson, Map::class.java)

              // Actualizar datos del usuario con las configuraciones
              userData = userData.copy(
                username = settings["username"] as? String ?: userData.username,
                onionAddress = settings["onionAddress"] as? String ?: userData.onionAddress
              )

              // Aquí puedes guardar las configuraciones en SharedPreferences
              saveSettingsToStorage(settingsJson)

              showToast("Settings saved")
            } catch (e: Exception) {
              showToast("Error saving settings: ${e.message}")
            }
          }
        }

        /**
         * Mostrar un toast desde JavaScript
         * @param message Mensaje a mostrar
         */
        @JavascriptInterface
        fun toast(message: String) {
          runOnUiThread {
            showToast(message)
          }
        }

        /**
         * Obtener configuraciones guardadas
         * @return JSON con configuraciones
         */
        @JavascriptInterface
        fun getSettings(): String {
          // Aquí deberías cargar las configuraciones desde SharedPreferences
          val defaultSettings = mapOf(
            "username" to userData.username,
            "onionAddress" to userData.onionAddress,
            "allowAddMe" to true,
            "addBack" to true,
            "alertOnNewMessages" to true,
            "checkNewMessagesSeconds" to 20,
            "verbose" to true,
            "debug" to true,
            "debugWithTime" to true,
            "torConfig" to mapOf(
              "torPort" to 9050,
              "hiddenServicePort" to 9001,
              "logNoticeFile" to "./logs/notices.log",
              "controlPort" to 9051,
              "hashedControlPassword" to false,
              "orPort" to 0,
              "disableNetwork" to 0,
              "avoidDiskWrites" to 1
            )
          )
          return gson.toJson(defaultSettings)
        }

        /**
         * Obtener contactos guardados
         * @return JSON con contactos
         */
        @JavascriptInterface
        fun getContacts(): String {
          return gson.toJson(mapOf("contacts" to userData.contacts))
        }

        /**
         * Enviar un mensaje a través de la red Tor
         * @param messageJson JSON con datos del mensaje
         */
        @JavascriptInterface
        fun sendTorMessage(messageJson: String) {
          runOnUiThread {
            try {
              val messageData = gson.fromJson(messageJson, Map::class.java)
              val recipient = messageData["recipient"] as? String
              val message = messageData["message"] as? String

              if (recipient != null && message != null) {
                // Aquí implementarías el envío real a través de la red Tor
                // Por ahora solo mostramos un toast
                showToast("Message sent to $recipient")
              }
            } catch (e: Exception) {
              showToast("Error sending message: ${e.message}")
            }
          }
        }

        /**
         * Recibir mensajes de la red Tor (simulado para demo)
         * @return JSON con mensajes recibidos
         */
        @JavascriptInterface
        fun checkForMessages(): String {
          // En una implementación real, esto consultaría el daemon de Tor
          // Para la demo, devolvemos mensajes vacíos
          val messages = mapOf(
            "messages" to emptyList<Any>(),
            "timestamp" to System.currentTimeMillis()
          )
          return gson.toJson(messages)
        }
      }

      /**
       * Guardar contactos en almacenamiento persistente
       */
    private fun saveContactsToStorage(contactsJson: String) {
      val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
      with(sharedPref.edit()) {
        putString("contacts", contactsJson)
        apply()
      }
    }

    /**
     * Guardar configuraciones en almacenamiento persistente
     */
    private fun saveSettingsToStorage(settingsJson: String) {
      val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
      with(sharedPref.edit()) {
        putString("settings", settingsJson)
        apply()
      }
    }

    /**
     * Cargar datos guardados al iniciar la aplicación
     */
    private fun loadSavedData() {
      val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)

      val savedContacts = sharedPref.getString("contacts", null)
      val savedSettings = sharedPref.getString("settings", null)

      if (savedContacts != null) {
        try {
          val contactsList = gson.fromJson(savedContacts, Map::class.java)
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
          }
        } catch (e: Exception) {
          // Ignorar errores de parseo
        }
      }

      if (savedSettings != null) {
        try {
          val settings = gson.fromJson(savedSettings, Map::class.java)
          userData = userData.copy(
            username = settings["username"] as? String ?: userData.username,
            onionAddress = settings["onionAddress"] as? String ?: userData.onionAddress
          )
        } catch (e: Exception) {
          // Ignorar errores de parseo
        }
      }
    }

    /**
     * Mostrar un toast
     */
    private fun showToast(message: String) {
      Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Datos de la aplicación
     */
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
