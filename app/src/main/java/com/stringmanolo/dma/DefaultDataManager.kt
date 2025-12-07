package com.stringmanolo.dma

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

class DefaultDataManager(private val context: Context) {

  fun getAllDefaultData(): Map<String, Any> {
    return mapOf(
      Pair("contacts", getDefaultContactsFull()),
      Pair("defaultChats", getDefaultChats()),
      Pair("defaultMessages", getDefaultMessages()),
      Pair("settings", getDefaultSettingsMap())
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
          Pair("id", obj.getInt("id")),
          Pair("name", obj.getString("name")),
          Pair("onion", obj.getString("onion")),
          Pair("status", obj.optString("status", "Online"))
        ))
      }
      contacts
    } catch (e: Exception) {
      e.printStackTrace()
      listOf(
        mapOf(
          Pair("id", 0),
          Pair("name", "StringManolo"),
          Pair("onion", "placeholder.onion"),
          Pair("status", "Online")
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
          Pair("id", obj.getInt("id")),
          Pair("contactId", obj.getInt("contactId")),
          Pair("lastMessage", obj.getString("lastMessage")),
          Pair("unread", obj.getBoolean("unread")),
          Pair("timestamp", obj.optString("timestamp", ""))
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
            Pair("id", obj.getInt("id")),
            Pair("senderId", obj.getInt("senderId")),
            Pair("text", obj.getString("text")),
            Pair("incoming", obj.getBoolean("incoming")),
            Pair("timestamp", obj.optString("timestamp", ""))
          ))
        }
        messages[key] = messageList
      }
      messages
    } catch (e: Exception) {
      e.printStackTrace()
      mapOf(
        Pair("1", listOf(
          mapOf(
            Pair("id", 1),
            Pair("senderId", 0),
            Pair("text", "Welcome to DarkMessenger. I am the app developer, you can chat with me for any questions about the app."),
            Pair("incoming", true),
            Pair("timestamp", "")
          )
        ))
      )
    }
  }

  fun getDefaultSettingsJson(): String {
    return try {
      loadAssetFile("data/default_settings.json")
    } catch (e: Exception) {
      e.printStackTrace()
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
            "enabled": {"value": true, "default": true, "type": "toggle"},
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
