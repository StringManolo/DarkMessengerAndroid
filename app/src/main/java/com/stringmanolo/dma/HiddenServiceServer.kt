package com.stringmanolo.dma

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.File
import java.util.*

class HiddenServiceServer(
    private val context: Context,
    private val port: Int = 9001,
    private val messageCallback: (MessageData) -> Unit,
    private val contactCallback: (ContactData) -> Unit
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "HiddenServiceServer"
        private const val ALLOWED_METHODS = "GET, POST, OPTIONS"
    }

    data class MessageData(
        val from: String,
        val message: String,
        val onionAddress: String
    )

    data class ContactData(
        val alias: String,
        val address: String
    )

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        
        Log.d(TAG, "Request: $method $uri from ${session.remoteIpAddress}")

        // Configurar headers CORS
        val headers = session.headers
        val origin = headers["origin"] ?: "*"
        
        val responseHeaders = mapOf(
            "Access-Control-Allow-Origin" to origin,
            "Access-Control-Allow-Methods" to ALLOWED_METHODS,
            "Access-Control-Allow-Headers" to "Content-Type",
            "Access-Control-Allow-Credentials" to "true"
        )

        // Manejar preflight OPTIONS
        if (method == Method.OPTIONS) {
            return newFixedLengthResponse(
                Response.Status.OK,
                MIME_PLAINTEXT,
                ""
            ).apply {
                responseHeaders.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
        }

        return when {
            uri == "/" && method == Method.GET -> handleRoot(responseHeaders)
            uri == "/wcdyu" && method == Method.GET -> handleWcdyu(responseHeaders)
            uri == "/crypto" && method == Method.GET -> handleCrypto(responseHeaders)
            uri == "/addme" && method == Method.POST -> handleAddMe(session, responseHeaders)
            uri == "/send" && method == Method.POST -> handleSend(session, responseHeaders)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
                .applyHeaders(responseHeaders)
        }
    }

    private fun handleRoot(headers: Map<String, String>): Response {
        val response = mapOf(
            "service" to "Dark Messenger Hidden Service",
            "version" to "1.0.0",
            "status" to "online",
            "timestamp" to Date().toString()
        )
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            JSONObject(response).toString()
        ).applyHeaders(headers)
    }

    private fun handleWcdyu(headers: Map<String, String>): Response {
        val gson = Gson()
        
        // Leer configuración desde SharedPreferences
        val sharedPref = context.getSharedPreferences("DarkMessengerPrefs", Context.MODE_PRIVATE)
        val settingsJson = sharedPref.getString("settings", null)
        
        return try {
            val settings = if (settingsJson != null) {
                gson.fromJson(settingsJson, Map::class.java)
            } else {
                // Configuración por defecto
                mapOf(
                    "darkmessenger" to mapOf(
                        "cryptography" to mapOf(
                            "useERK" to mapOf("value" to false, "default" to false, "type" to "toggle"),
                            "offlineMessages" to mapOf(
                                "ecies" to mapOf("value" to true, "default" to true, "type" to "toggle"),
                                "rsa" to mapOf("value" to true, "default" to true, "type" to "toggle"),
                                "crystalKyber" to mapOf("value" to true, "default" to true, "type" to "toggle")
                            ),
                            "onlineMessages" to mapOf(
                                "ecies" to mapOf("value" to true, "default" to true, "type" to "toggle"),
                                "rsa" to mapOf("value" to true, "default" to true, "type" to "toggle"),
                                "crystalKyber" to mapOf("value" to true, "default" to true, "type" to "toggle")
                            ),
                            "addMe" to mapOf(
                                "ecies" to mapOf("value" to true, "default" to true, "type" to "toggle"),
                                "rsa" to mapOf("value" to true, "default" to true, "type" to "toggle"),
                                "crystalKyber" to mapOf("value" to true, "default" to true, "type" to "toggle")
                            )
                        )
                    )
                )
            }

            val darkmessenger = (settings["darkmessenger"] as? Map<*, *>)
            val cryptography = darkmessenger?.get("cryptography") as? Map<*, *>
            
            val response = mapOf(
                "use_erk" to ((cryptography?.get("useERK") as? Map<*, *>)?.get("value") ?: false),
                "offline_messages" to mapOf(
                    "ecies" to ((cryptography?.get("offlineMessages") as? Map<*, *>)?.get("ecies") as? Map<*, *>)?.get("value") ?: true,
                    "rsa" to ((cryptography?.get("offlineMessages") as? Map<*, *>)?.get("rsa") as? Map<*, *>)?.get("value") ?: true,
                    "crystal_kyber" to ((cryptography?.get("offlineMessages") as? Map<*, *>)?.get("crystalKyber") as? Map<*, *>)?.get("value") ?: true
                ),
                "online_messages" to mapOf(
                    "ecies" to ((cryptography?.get("onlineMessages") as? Map<*, *>)?.get("ecies") as? Map<*, *>)?.get("value") ?: true,
                    "rsa" to ((cryptography?.get("onlineMessages") as? Map<*, *>)?.get("rsa") as? Map<*, *>)?.get("value") ?: true,
                    "crystal_kyber" to ((cryptography?.get("onlineMessages") as? Map<*, *>)?.get("crystalKyber") as? Map<*, *>)?.get("value") ?: true
                ),
                "add_me" to mapOf(
                    "ecies" to ((cryptography?.get("addMe") as? Map<*, *>)?.get("ecies") as? Map<*, *>)?.get("value") ?: true,
                    "rsa" to ((cryptography?.get("addMe") as? Map<*, *>)?.get("rsa") as? Map<*, *>)?.get("value") ?: true,
                    "crystal_kyber" to ((cryptography?.get("addMe") as? Map<*, *>)?.get("crystalKyber") as? Map<*, *>)?.get("value") ?: true
                )
            )

            newFixedLengthResponse(
                Response.Status.OK, 
                "application/json", 
                JSONObject(response).toString()
            ).applyHeaders(headers)
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleWcdyu", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error")
                .applyHeaders(headers)
        }
    }

    private fun handleCrypto(headers: Map<String, String>): Response {
        return try {
            // Si usamos ERK, aquí devolveríamos la clave pública
            // Por ahora, devolvemos un mensaje indicando que no se usa ERK
            val response = mapOf(
                "use_erk" to false,
                "message" to "ERK not enabled in this client"
            )
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                JSONObject(response).toString()
            ).applyHeaders(headers)
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleCrypto", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error")
                .applyHeaders(headers)
        }
    }

    private fun handleAddMe(session: IHTTPSession, headers: Map<String, String>): Response {
        return try {
            // Parsear cuerpo JSON
            val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
            val buffer = ByteArray(contentLength)
            session.inputStream.read(buffer)
            val postData = String(buffer, Charsets.UTF_8)
            
            Log.d(TAG, "AddMe request data: $postData")

            val json = try {
                JSONObject(postData)
            } catch (e: Exception) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid JSON")
                    .applyHeaders(headers)
            }
            
            val alias = json.optString("alias", "")
            val address = json.optString("address", "")

            // Validaciones
            if (alias.isEmpty() || address.isEmpty()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Alias and address are required")
                    .applyHeaders(headers)
            }

            if (!isValidAlias(alias)) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, 
                    "Alias can only use alphanumeric characters and be 1 to 99 characters long. Allowed characters: - _ . @")
                    .applyHeaders(headers)
            }

            if (!isValidOnionAddress(address)) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, 
                    "Invalid onion address format")
                    .applyHeaders(headers)
            }

            // Verificar si allowAddMe está habilitado
            val sharedPref = context.getSharedPreferences("DarkMessengerPrefs", Context.MODE_PRIVATE)
            val settingsJson = sharedPref.getString("settings", null)
            val allowAddMe = if (settingsJson != null) {
                val settings = Gson().fromJson(settingsJson, Map::class.java)
                val darkmessenger = (settings["darkmessenger"] as? Map<*, *>)
                val general = darkmessenger?.get("general") as? Map<*, *>
                val allowAddMeObj = general?.get("allowAddMe") as? Map<*, *>
                allowAddMeObj?.get("value") as? Boolean ?: true
            } else {
                true // Por defecto permitir
            }

            if (!allowAddMe) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, 
                    "This server doesn't allow remote contact addition")
                    .applyHeaders(headers)
            }

            // Notificar al callback para agregar el contacto
            contactCallback(ContactData(alias, address))

            newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Contact added successfully")
                .applyHeaders(headers)
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleAddMe", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error")
                .applyHeaders(headers)
        }
    }

    private fun handleSend(session: IHTTPSession, headers: Map<String, String>): Response {
        return try {
            // Parsear cuerpo JSON
            val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
            val buffer = ByteArray(contentLength)
            session.inputStream.read(buffer)
            val postData = String(buffer, Charsets.UTF_8)
            
            Log.d(TAG, "Send request data: $postData")

            val json = try {
                JSONObject(postData)
            } catch (e: Exception) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid JSON")
                    .applyHeaders(headers)
            }
            
            val from = json.optString("from", "")
            val message = json.optString("message", "")
            val onionAddress = from // En el protocolo original, from es el onion address

            // Validaciones
            if (from.isEmpty() || message.isEmpty()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "From and message are required")
                    .applyHeaders(headers)
            }

            // Decodificar el mensaje (viene en base64 según el código original)
            val decodedMessage = try {
                android.util.Base64.decode(message, android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
            } catch (e: Exception) {
                // Si no es base64 válido, usar como texto plano
                message
            }

            // Notificar al callback para procesar el mensaje
            messageCallback(MessageData(from, decodedMessage, onionAddress))

            newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Message received")
                .applyHeaders(headers)
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleSend", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error")
                .applyHeaders(headers)
        }
    }

    private fun isValidAlias(alias: String): Boolean {
        val regex = "^[a-zA-Z0-9\\-_.@]{1,99}$".toRegex()
        return regex.matches(alias)
    }

    private fun isValidOnionAddress(address: String): Boolean {
        val regex = "^(?:[a-z2-7]{16}|[a-z2-7]{56})\\.onion$".toRegex()
        return regex.matches(address)
    }

    private fun Response.applyHeaders(headers: Map<String, String>): Response {
        headers.forEach { (key, value) ->
            addHeader(key, value)
        }
        return this
    }

    fun startServer(): Boolean {
        return try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.d(TAG, "Hidden service server started on port $port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting hidden service server", e)
            false
        }
    }

    fun stopServer() {
        stop()
        Log.d(TAG, "Hidden service server stopped")
    }
}