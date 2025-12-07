package com.stringmanolo.dma

import android.annotation.SuppressLint
import android.content.Context
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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

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
  private lateinit var hiddenServiceServer: HiddenServiceServer
  private val handler = Handler(Looper.getMainLooper())
  private val executor = Executors.newSingleThreadExecutor()
  private var isAppInForeground = true
  private var torStartAttempted = false

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    Log.d("DarkMessenger", "MainActivity.onCreate()")

    defaultDataManager = DefaultDataManager(this)
    torManager = TorManager(this, this)
    hiddenServiceServer = HiddenServiceServer(
      context = this,
      port = 9001,
      messageCallback = { messageData ->
        handleIncomingMessage(messageData)
      },
      contactCallback = { contactData ->
        handleIncomingContact(contactData)
      }
    )

    loadUserData()

    webView = findViewById(R.id.webView)
    setupWebView()

    checkAndStartTor()
  }

  override fun onResume() {
    super.onResume()
    isAppInForeground = true
    Log.d("DarkMessenger", "MainActivity.onResume()")

    if (!torStartAttempted) {
      checkAndStartTor()
    }
  }

  override fun onPause() {
    super.onPause()
    isAppInForeground = false
    Log.d("DarkMessenger", "MainActivity.onPause()")
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.d("DarkMessenger", "MainActivity.onDestroy()")
    torManager.stopTor()
    hiddenServiceServer.stopServer()
    executor.shutdown()
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun setupWebView() {
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true
    webView.settings.allowFileAccess = true
    webView.settings.allowContentAccess = true

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

    Log.d("DarkMessenger", "User data loaded: ${userData.username}, ${userData.onionAddress}, ${userData.contacts.size} contacts")
  }

  private fun checkAndStartTor() {
    val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)

    torStartAttempted = true

    val defaultSettings = defaultDataManager.getDefaultSettingsMap()
    val torEnabledByDefault = isTorEnabledInSettings(defaultSettings)

    val torEnabled = if (sharedPref.contains("tor_enabled")) {
      sharedPref.getBoolean("tor_enabled", false)
    } else {
      sharedPref.edit().putBoolean("tor_enabled", torEnabledByDefault).apply()
      torEnabledByDefault
    }

    Log.d("DarkMessenger", "Tor enabled in settings: $torEnabled, isRunning: ${torManager.isRunning()}")

    if (torEnabled && !torManager.isRunning()) {
      executor.execute {
        Log.d("DarkMessenger", "Starting Tor from checkAndStartTor()")
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
      Log.e("DarkMessenger", "Error checking tor enabled in settings", e)
      false
    }
  }

  fun addLogToWebView(message: String) {
    runOnUiThread {
      webView.evaluateJavascript("console.log('$message');", null)
    }
  }

  fun notifyNewOnionAddress(onionAddress: String) {
    runOnUiThread {
      userData = userData.copy(onionAddress = onionAddress)
      updateOnionAddressInSettingsJson(onionAddress)
      saveUserInfoToStorage()
      
      webView.evaluateJavascript(
        "if (typeof window.app !== 'undefined') { " +
        "window.app.updateOnionAddressInUI('$onionAddress'); " +
        "window.app.showToast('New onion address generated'); }",
        null
      )
      
      showToast("New onion address: $onionAddress")
    }
  }

  private fun handleIncomingMessage(messageData: HiddenServiceServer.MessageData) {
    runOnUiThread {
      try {
        Log.d("DarkMessenger", "Incoming message from: ${messageData.from}, message: ${messageData.message}")

        val existingContact = userData.contacts.find { it.onion == messageData.onionAddress }

        if (existingContact != null) {
          val existingChat = getOrCreateChatForContact(existingContact)

          val newMessage = Message(
            id = System.currentTimeMillis(),
            senderId = existingContact.name.hashCode(),
            text = messageData.message,
            timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()).format(Date()),
            incoming = true
          )

          saveIncomingMessage(existingChat.id, newMessage)
          updateChatLastMessage(existingChat.id, messageData.message)
          notifyNewMessage(existingChat.id, existingContact.name, messageData.message)

          showToast("New message from ${existingContact.name}")
        } else {
          Log.d("DarkMessenger", "Unknown sender, creating temporary contact")
          createTemporaryContactAndChat(messageData)
        }
      } catch (e: Exception) {
        Log.e("DarkMessenger", "Error handling incoming message", e)
      }
    }
  }

  private fun createTemporaryContactAndChat(messageData: HiddenServiceServer.MessageData) {
    try {
      val tempContactName = messageData.from.ifEmpty { "Unknown" }
      val tempContact = Contact(
        name = tempContactName,
        onion = messageData.onionAddress
      )

      val updatedContacts = userData.contacts.toMutableList()
      updatedContacts.add(tempContact)
      userData = userData.copy(contacts = updatedContacts)

      val newChatId = loadChatsFromStorage().maxByOrNull { it.id }?.id?.plus(1) ?: 1
      val newChat = Chat(
        id = newChatId,
        contactId = tempContact.name.hashCode(),
        lastMessage = messageData.message,
        timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()).format(Date()),
        unread = true
      )

      val updatedChats = loadChatsFromStorage().toMutableList()
      updatedChats.add(newChat)
      saveChatsToStorage(gson.toJson(updatedChats))

      val newMessage = Message(
        id = System.currentTimeMillis(),
        senderId = tempContact.name.hashCode(),
        text = messageData.message,
        timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()).format(Date()),
        incoming = true
      )

      saveIncomingMessage(newChatId, newMessage)
      notifyNewMessage(newChatId, tempContactName, messageData.message)

      showToast("New message from unknown contact")
    } catch (e: Exception) {
      Log.e("DarkMessenger", "Error creating temporary contact", e)
    }
  }

  private fun handleIncomingContact(contactData: HiddenServiceServer.ContactData) {
    runOnUiThread {
      try {
        Log.d("DarkMessenger", "Incoming contact request: ${contactData.alias} (${contactData.address})")

        val existingContact = userData.contacts.find {
          it.name == contactData.alias || it.onion == contactData.address
        }

        if (existingContact == null) {
          val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
          val settingsJson = sharedPref.getString("settings", null)
          val addBackEnabled = if (settingsJson != null) {
            val settings = gson.fromJson(settingsJson, Map::class.java)
            val darkmessenger = (settings["darkmessenger"] as? Map<*, *>)
            val general = darkmessenger?.get("general") as? Map<*, *>
            val addBackObj = general?.get("addBack") as? Map<*, *>
            addBackObj?.get("value") as? Boolean ?: true
          } else {
            true
          }

          if (addBackEnabled) {
            val newContact = Contact(
              name = contactData.alias,
              onion = contactData.address
            )

            val updatedContacts = userData.contacts.toMutableList()
            updatedContacts.add(newContact)
            userData = userData.copy(contacts = updatedContacts)

            saveContactsToStorage(gson.toJson(updatedContacts))
            notifyNewContact(newContact)

            showToast("New contact added: ${contactData.alias}")
          } else {
            Log.d("DarkMessenger", "addBack disabled, ignoring contact request")
          }
        } else {
          Log.d("DarkMessenger", "Contact ${contactData.alias} already exists")
        }
      } catch (e: Exception) {
        Log.e("DarkMessenger", "Error handling incoming contact", e)
      }
    }
  }

  private fun getOrCreateChatForContact(contact: Contact): Chat {
    val existingChats = loadChatsFromStorage()
    val existingChat = existingChats.find { it.contactId == contact.name.hashCode() }

    return if (existingChat != null) {
      existingChat
    } else {
      val newChatId = existingChats.maxByOrNull { it.id }?.id?.plus(1) ?: 1
      val newChat = Chat(
        id = newChatId,
        contactId = contact.name.hashCode(),
        lastMessage = "New contact",
        timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()).format(Date()),
        unread = true
      )

      val updatedChats = existingChats.toMutableList()
      updatedChats.add(newChat)
      saveChatsToStorage(gson.toJson(updatedChats))

      newChat
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun saveIncomingMessage(chatId: Int, message: Message) {
    try {
      val messagesJson = getMessagesFromStorage()
      val messages = if (messagesJson.isNotEmpty()) {
        gson.fromJson(messagesJson, Map::class.java) as MutableMap<String, Any>
      } else {
        mutableMapOf<String, Any>()
      }

      val chatIdStr = chatId.toString()
      val chatMessages = (messages[chatIdStr] as? MutableList<MutableMap<String, Any>>)
        ?: mutableListOf<MutableMap<String, Any>>()

      val messageMap = mutableMapOf<String, Any>()
      messageMap["id"] = message.id
      messageMap["senderId"] = message.senderId ?: -1
      messageMap["text"] = message.text
      messageMap["timestamp"] = message.timestamp
      messageMap["incoming"] = message.incoming

      chatMessages.add(messageMap)
      messages[chatIdStr] = chatMessages

      saveMessagesToStorage(gson.toJson(messages))
    } catch (e: Exception) {
      Log.e("DarkMessenger", "Error saving incoming message", e)
    }
  }

  private fun updateChatLastMessage(chatId: Int, lastMessage: String) {
    try {
      val chatsJson = getChatsFromStorage()
      val chats = if (chatsJson.isNotEmpty()) {
        gson.fromJson(chatsJson, Array<Chat>::class.java).toMutableList()
      } else {
        mutableListOf()
      }

      val chatIndex = chats.indexOfFirst { it.id == chatId }
      if (chatIndex != -1) {
        val updatedChat = chats[chatIndex].copy(
          lastMessage = lastMessage,
          timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()).format(Date()),
          unread = true
        )
        chats[chatIndex] = updatedChat
        saveChatsToStorage(gson.toJson(chats))
      }
    } catch (e: Exception) {
      Log.e("DarkMessenger", "Error updating chat last message", e)
    }
  }

  private fun loadChatsFromStorage(): List<Chat> {
    val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
    val chatsJson = sharedPref.getString("chats", "[]")
    return if (chatsJson != null && chatsJson.isNotEmpty()) {
      try {
        val type = object : TypeToken<List<Chat>>() {}.type
        gson.fromJson(chatsJson, type)
      } catch (e: Exception) {
        emptyList()
      }
    } else {
      emptyList()
    }
  }

  private fun getChatsFromStorage(): String {
    val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
    return sharedPref.getString("chats", "[]") ?: "[]"
  }

  private fun getMessagesFromStorage(): String {
    val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
    return sharedPref.getString("messages", "{}") ?: "{}"
  }

  private fun saveChatsToStorage(chatsJson: String) {
    val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
    sharedPref.edit().putString("chats", chatsJson).apply()
  }

  private fun saveMessagesToStorage(messagesJson: String) {
    val sharedPref = getSharedPreferences("DarkMessengerPrefs", MODE_PRIVATE)
    sharedPref.edit().putString("messages", messagesJson).apply()
  }

  private fun notifyNewMessage(chatId: Int, senderName: String, message: String) {
    val escapedMessage = message.replace("'", "\\'").replace("\n", "\\n")
    val escapedSender = senderName.replace("'", "\\'")

    webView.evaluateJavascript("""
      if (window.app && window.app.handleIncomingMessage) {
        window.app.handleIncomingMessage($chatId, '$escapedSender', '$escapedMessage');
      }
    """, null)
  }

  private fun notifyNewContact(contact: Contact) {
    val escapedName = contact.name.replace("'", "\\'")
    val escapedOnion = contact.onion.replace("'", "\\'")

    webView.evaluateJavascript("""
      if (window.app && window.app.handleNewContact) {
        window.app.handleNewContact('$escapedName', '$escapedOnion');
      }
    """, null)
  }

  fun startHiddenServiceServer(): Boolean {
    return try {
      hiddenServiceServer.startServer()
    } catch (e: Exception) {
      Log.e("DarkMessenger", "Error starting hidden service server", e)
      false
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

          try {
            val settings = gson.fromJson(settingsJson, Map::class.java)
            val darkmessenger = settings["darkmessenger"] as? Map<*, *>
            val torSettings = darkmessenger?.get("tor") as? Map<*, *>

            val torEnabledObj = torSettings?.get("enabled") as? Map<*, *>
            val torEnabled = torEnabledObj?.get("value") as? Boolean ?: false

            saveTorEnabledToStorage(torEnabled)

            if (torEnabled && !torManager.isRunning()) {
              executor.execute {
                torManager.startTor()
              }
            } else if (!torEnabled && torManager.isRunning()) {
              torManager.stopTor()
            }
          } catch (e: Exception) {
          }

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
          userData = userData.copy(onionAddress = newOnion)
          saveUserInfoToStorage()
          updateOnionAddressInSettingsJson(newOnion)

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

    @JavascriptInterface
    fun testIncomingMessage() {
      runOnUiThread {
        val testMessage = HiddenServiceServer.MessageData(
          from = "TestUser",
          message = "This is a test message from another user",
          onionAddress = "testuser1234567890.onion"
        )
        handleIncomingMessage(testMessage)
        showToast("Test message sent")
      }
    }

    @JavascriptInterface
    fun testIncomingContact() {
      runOnUiThread {
        val testContact = HiddenServiceServer.ContactData(
          alias = "TestFriend",
          address = "testfriend0987654321.onion"
        )
        handleIncomingContact(testContact)
        showToast("Test contact request sent")
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

        saveSettingsToStorage(gson.toJson(settingsMap))
      } catch (e: Exception) {
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