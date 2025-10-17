package com.example.citofono

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.bson.types.ObjectId
import java.io.*
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.system.exitProcess

// Modelos de datos
data class BusMessage(
    val type: String,
    val sender: String? = null,
    val target: String? = null,
    val service: String? = null,
    val header: Header? = null,
    val payload: Payload? = null,
    val kind: String? = null,
    val client_id: String? = null,
    val status: String? = null,
    val event: String? = null,
    val data: Map<String, Any>? = null
)

data class Header(
    val correlationId: String? = null,
    val service: String? = null
)

data class Payload(
    val action: String? = null,
    val userId: String? = null,
    val senderObjId: String? = null,
    val receiverObjId: String? = null,
    val message: String? = null,
    val ok: Boolean? = null,
    val error: String? = null,
    val messageId: String? = null,
    val timestamp: String? = null,
    val messages: List<ConversationMessage>? = null,
    val hasMore: Boolean? = null,
    val conversationId: String? = null,
    val isTyping: Boolean? = null,
    val pong: Boolean? = null
)

data class ConversationMessage(
    val id: String,
    val from: String,
    val to: String,
    val text: String,
    val ts: String,
    val deliveryStatus: String? = null,
    val readStatus: String? = null
)

data class UserInfo(
    val user_id: String,
    val username: String
)

data class OnlineUser(
    val client_id: String,
    val user_id: String,
    val username: String
)

// Funciones auxiliares
fun sendJsonLine(sock: Socket, obj: Any) {
    val gson = Gson()
    val json = gson.toJson(obj) + "\n"
    sock.getOutputStream().write(json.toByteArray(Charsets.UTF_8))
    sock.getOutputStream().flush()
}

fun recvJsonLine(sock: Socket, timeout: Double = 5.0): Map<String, Any> {
    sock.soTimeout = (timeout * 1000).toInt()
    val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
    val line = reader.readLine()
    val gson = Gson()
    val type = object : TypeToken<Map<String, Any>>() {}.type
    return gson.fromJson<Map<String, Any>>(line, type) ?: emptyMap()
}

class InteractiveChatClient(
    val userId: String,
    val username: String,
    private val busHost: String = "localhost",
    private val busPort: Int = 5000
) {
    private var socket: Socket? = null
    private val clientId = "chat_${UUID.randomUUID().toString().take(8)}"
    var running = false
        private set
    private val events = mutableListOf<Map<String, Any>>()
    private val responseQueue = mutableListOf<Map<String, Any>>()
    private val queueLock = ReentrantLock()
    val conversationHistory = mutableListOf<Map<String, Any>>()
    var otherUserId: String? = null
    var otherUsername: String? = null
    var otherClientId: String? = null
    private var isOtherTyping = false

    // Lista de usuarios conectados
    private val onlineUsers = ConcurrentHashMap<String, UserInfo>() // client_id -> UserInfo
    private val usersLock = ReentrantLock()

    private val gson = Gson()

    fun connect(): Boolean {
        println("\n🔌 Conectando al BUS en $busHost:$busPort...")
        android.util.Log.d("ChatClient", "=== INICIANDO CONNECT ===")
        android.util.Log.d("ChatClient", "BUS Host: $busHost")
        android.util.Log.d("ChatClient", "BUS Port: $busPort")
        android.util.Log.d("ChatClient", "Client ID: $clientId")

        return try {
            android.util.Log.d("ChatClient", "Paso 1: Creando socket...")
            socket = Socket(busHost, busPort)
            android.util.Log.d("ChatClient", "✅ Socket creado exitosamente")
            android.util.Log.d("ChatClient", "Socket conectado: ${socket?.isConnected}")
            android.util.Log.d("ChatClient", "Socket cerrado: ${socket?.isClosed}")

            android.util.Log.d("ChatClient", "Paso 2: Enviando REGISTER...")
            sendJsonLine(socket!!, mapOf(
                "type" to "REGISTER",
                "kind" to "client",
                "client_id" to clientId
            ))
            android.util.Log.d("ChatClient", "✅ REGISTER enviado")

            android.util.Log.d("ChatClient", "Paso 3: Esperando REGISTER_ACK...")
            val ack = recvJsonLine(socket!!)
            android.util.Log.d("ChatClient", "✅ ACK recibido: $ack")

            if (ack["type"] == "REGISTER_ACK" && ack["status"] == "success") {
                println("✅ Registrado como $username (${userId.take(8)}...)")
                println("   Client ID: $clientId")
                android.util.Log.d("ChatClient", "✅ Registro exitoso")

                // Iniciar listener de eventos
                running = true
                android.util.Log.d("ChatClient", "Paso 4: Iniciando listener de eventos...")
                thread(isDaemon = true) { listenEvents() }
                Thread.sleep(500)

                // Conectar al servicio de mensajería
                try {
                    android.util.Log.d("ChatClient", "Paso 5: Conectando al servicio de mensajería...")
                    val response = sendAction("connect", mapOf("userId" to userId), optional = true)
                    if (response["ok"] == true) {
                        println("✅ Conectado al servicio de mensajería")
                        android.util.Log.d("ChatClient", "✅ Servicio de mensajería conectado")
                    } else {
                        android.util.Log.w("ChatClient", "⚠️ Respuesta del servicio: $response")
                    }
                } catch (e: Exception) {
                    println("⚠️ Error conectando al servicio: ${e.message}")
                    android.util.Log.e("ChatClient", "⚠️ Error en servicio de mensajería", e)
                }

                // Broadcast mi presencia
                android.util.Log.d("ChatClient", "Paso 6: Broadcasting presencia...")
                broadcastMyPresence()
                android.util.Log.d("ChatClient", "✅ Presencia enviada")

                android.util.Log.d("ChatClient", "🎉 CONEXIÓN COMPLETADA EXITOSAMENTE")
                true
            } else {
                println("❌ Error en registro: $ack")
                android.util.Log.e("ChatClient", "❌ Error en registro: $ack")
                false
            }
        } catch (e: Exception) {
            println("❌ Error conectando: ${e.message}")
            android.util.Log.e("ChatClient", "❌ EXCEPCIÓN EN CONNECT", e)
            android.util.Log.e("ChatClient", "Tipo de error: ${e.javaClass.simpleName}")
            android.util.Log.e("ChatClient", "Mensaje: ${e.message}")
            android.util.Log.e("ChatClient", "Stack trace: ${e.stackTraceToString()}")
            false
        }
    }

    fun broadcastMyPresence() {
        try {
            // Enviar `client_id` también a nivel superior para compatibilidad
            sendJsonLine(socket!!, mapOf(
                "type" to "BROADCAST",
                "event" to "user_presence",
                "client_id" to clientId,
                "data" to mapOf(
                    "client_id" to clientId,
                    "user_id" to userId,
                    "username" to username
                )
            ))
        } catch (_: Exception) {
            println("⚠️ Error enviando presencia")
        }
    }

    private fun listenEvents() {
        val reader = socket?.getInputStream()?.bufferedReader()
        while (running) {
            try {
                socket?.soTimeout = 1000
                val line = reader?.readLine()

                if (line == null) break
                if (line.isBlank()) continue

                @Suppress("UNCHECKED_CAST")
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val msg = gson.fromJson<Map<String, Any>>(line, type) ?: continue
                val msgType = msg["type"] as? String

                if (msgType == "DELIVERY_ACK") continue

                // Manejar BROADCAST
                if (msgType == "BROADCAST") {
                    handleBroadcast(msg)
                    continue
                }

                if (msgType == "DIRECT") {
                    @Suppress("UNCHECKED_CAST")
                    val payload = msg["payload"] as? Map<String, Any>

                    if (payload?.containsKey("event") == true) {
                        val event = payload["event"] as? String
                        @Suppress("UNCHECKED_CAST")
                        val data = payload["data"] as? Map<String, Any>
                        @Suppress("UNCHECKED_CAST")
                        events.add(mapOf("event" to event, "data" to data) as Map<String, Any>)
                        handleEvent(event ?: "", data ?: emptyMap())
                    } else {
                        queueLock.lock()
                        try {
                            responseQueue.add(msg)
                        } finally {
                            queueLock.unlock()
                        }
                    }
                }
            } catch (_: SocketTimeoutException) {
                continue
            } catch (e: Exception) {
                if (running) {
                    println("\n⚠️ Error en listener: ${e.message}")
                }
                break
            }
        }
    }

    private fun handleBroadcast(msg: Map<String, Any>) {
        // Extraer `data` y permitir que `client_id`, `user_id` y `username` vengan
        // ya sea a nivel superior o dentro de `data`.
        @Suppress("UNCHECKED_CAST")
        val data = msg["data"] as? Map<String, Any>
        val topClientId = msg["client_id"] as? String
        val clientIdFromData = data?.get("client_id") as? String
        val clientId = topClientId ?: clientIdFromData

        val topEvent = msg["event"] as? String
        val event = topEvent

        when (event) {
            "user_joined" -> {
                if (clientId != null && clientId != this.clientId) {
                    // Solicitar información del usuario: responder con presencia para que quien
                    // se unió nos conozca también.
                    broadcastMyPresence()
                }
            }
            "user_left" -> {
                val candidateClientId = clientId
                usersLock.lock()
                try {
                    if (candidateClientId != null && onlineUsers.containsKey(candidateClientId)) {
                        val userInfo = onlineUsers.remove(candidateClientId)
                        if (otherClientId == null) {
                            println("\n👋 ${userInfo?.username ?: candidateClientId.take(8)} se desconectó")
                        }
                    }
                } finally {
                    usersLock.unlock()
                }
            }
            "user_presence" -> {
                val effectiveClientId = clientId
                val userId = (data?.get("user_id") as? String) ?: (msg["user_id"] as? String) ?: ""
                val username = (data?.get("username") as? String) ?: (msg["username"] as? String) ?: "Usuario_${(effectiveClientId ?: "").take(8)}"

                if (effectiveClientId != null && effectiveClientId != this.clientId) {
                    usersLock.lock()
                    try {
                        onlineUsers[effectiveClientId] = UserInfo(
                            user_id = userId,
                            username = username
                        )
                    } finally {
                        usersLock.unlock()
                    }
                }
            }
        }
    }

    private fun handleEvent(event: String, data: Map<String, Any>) {
        when (event) {
            "new_message" -> {
                val msgFrom = data["from"] as? String ?: ""
                val msgText = data["text"] as? String ?: ""
                val timestamp = data["timestamp"] as? String ?: ""

                // Guardar en historial
                conversationHistory.add(mapOf(
                    "from" to msgFrom,
                    "text" to msgText,
                    "timestamp" to timestamp,
                    "direction" to "received"
                ))

                // Mostrar en consola
                println("\n💬 ${otherUsername ?: msgFrom.take(8)}: $msgText")
                print(if (isOtherTyping) "\n✍️  $otherUsername está escribiendo...\n> " else "> ")
            }
            "user_typing" -> {
                val userTyping = data["user_id"] as? String ?: ""
                val isTyping = data["is_typing"] as? Boolean ?: false

                if (userTyping == otherUserId) {
                    isOtherTyping = isTyping
                    if (isTyping) {
                        println("\n✍️  ${otherUsername ?: userTyping.take(8)} está escribiendo...")
                        print("> ")
                    }
                }
            }
            "message_read" -> {
                println("\n✓✓ Mensaje leído")
                print("> ")
            }
        }
    }

    private fun waitForResponse(correlationId: String, timeout: Double = 5.0): Map<String, Any> {
        val start = System.currentTimeMillis()

        while (System.currentTimeMillis() - start < timeout * 1000) {
            queueLock.lock()
            try {
                val iterator = responseQueue.iterator()
                while (iterator.hasNext()) {
                    val msg = iterator.next()
                    @Suppress("UNCHECKED_CAST")
                    val header = msg["header"] as? Map<String, Any>
                    if (header?.get("correlationId") == correlationId) {
                        iterator.remove()
                        return msg
                    }
                }
            } finally {
                queueLock.unlock()
            }
            Thread.sleep(100)
        }

        throw TimeoutException("No se recibió respuesta en el tiempo esperado")
    }

    private fun sendAction(action: String, payload: Map<String, Any>, optional: Boolean = false): Map<String, Any> {
        val correlationId = UUID.randomUUID().toString()

        val request = mapOf(
            "type" to "REQUEST",
            "service" to "Mensajeria",
            "sender" to clientId,
            "header" to mapOf(
                "correlationId" to correlationId,
                "service" to "Mensajeria"
            ),
            "payload" to (mapOf("action" to action) + payload)
        )

        sendJsonLine(socket!!, request)

        return try {
            val response = waitForResponse(correlationId, 5.0)
            @Suppress("UNCHECKED_CAST")
            response["payload"] as? Map<String, Any> ?: emptyMap()
        } catch (e: TimeoutException) {
            if (optional) {
                mapOf("ok" to false, "error" to "timeout")
            } else {
                throw e
            }
        }
    }

    fun sendMessage(receiverId: String, message: String): Boolean {
        return try {
            val payload = sendAction("send", mapOf(
                "senderObjId" to userId,
                "receiverObjId" to receiverId,
                "message" to message
            ))

            if (payload["ok"] == true) {
                // Guardar en historial
                conversationHistory.add(mapOf(
                    "from" to userId,
                    "text" to message,
                    "timestamp" to (payload["timestamp"]?.toString() ?: ""),
                    "direction" to "sent"
                ))
                true
            } else {
                println("\n❌ Error: ${payload["error"]}")
                false
            }
        } catch (e: Exception) {
            println("\n❌ Excepción: ${e.message}")
            false
        }
    }

    fun getConversation(otherUserId: String): List<Map<String, Any>> {
        return try {
            val payload = sendAction("getConversation", mapOf(
                "user1ObjId" to userId,
                "user2ObjId" to otherUserId,
                "limit" to 50
            ))

            if (payload["ok"] == true) {
                @Suppress("UNCHECKED_CAST")
                val messages = payload["messages"] as? List<Map<String, Any>> ?: emptyList()
                // Cargar historial
                for (msg in messages) {
                    conversationHistory.add(mapOf(
                        "from" to (msg["from"] as? String ?: ""),
                        "text" to (msg["text"] as? String ?: ""),
                        "timestamp" to (msg["ts"] as? String ?: ""),
                        "direction" to if (msg["from"] == userId) "sent" else "received"
                    ))
                }
                messages
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Suppress("unused")
    fun setTyping(conversationId: String, isTyping: Boolean) {
        try {
            sendAction("typing", mapOf(
                "userId" to userId,
                "conversationId" to conversationId,
                "isTyping" to isTyping
            ), optional = true)
        } catch (_: Exception) {
            // Ignorar errores
        }
    }

    fun getOnlineUsersList(): List<Pair<String, UserInfo>> {
        usersLock.lock()
        try {
            return onlineUsers.map { it.key to it.value }
        } finally {
            usersLock.unlock()
        }
    }

    fun showConversationSummary() {
        println("\n" + "=".repeat(80))
        println("📜 HISTORIAL DE CONVERSACIÓN - $username")
        println("=".repeat(80))

        if (conversationHistory.isEmpty()) {
            println("No hay mensajes en el historial")
        } else {
            conversationHistory.forEachIndexed { i, msg ->
                val direction = if (msg["direction"] == "sent") "➡️ " else "⬅️ "
                val sender = if (msg["direction"] == "sent") username else (otherUsername ?: (msg["from"] as? String)?.take(8) ?: "Desconocido")
                val timestamp = (msg["timestamp"] as? String)?.take(19) ?: ""
                println("${i + 1}. $direction[$sender] ($timestamp)")
                println("   ${msg["text"]}")
                println()
            }
        }

        println("=".repeat(80))
    }

    fun heartbeat() {
        try {
            sendAction("heartbeat", mapOf("userId" to userId), optional = true)
        } catch (e: Exception) {
            // Ignorar errores
        }
    }

    fun disconnect() {
        running = false
        socket?.let { sock ->
            try {
                sendAction("disconnect", mapOf("userId" to userId), optional = true)
                Thread.sleep(200)
            } catch (_: Exception) {
                // Ignorar
            }
            sock.close()
        }
    }
}

fun clearScreen() {
    if (System.getProperty("os.name")?.contains("Windows") == true) {
        ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor()
    } else {
        print("\u001b[H\u001b[2J")
        System.out.flush()
    }
}

fun showUserMenu(client: InteractiveChatClient): Map<String, String>? {
    while (client.running) {
        clearScreen()
        println("=".repeat(80))
        println("👤 ${client.username} - Selecciona con quién chatear")
        println("=".repeat(80))

        val users = client.getOnlineUsersList()

        if (users.isEmpty()) {
            println("\n⏳ Esperando que otros usuarios se conecten...")
            println("\nActualizando en 5 segundos... (Ctrl+C para salir)")
            try {
                Thread.sleep(5000)
            } catch (_: InterruptedException) {
                return null
            }
            continue
        }

        println("\n📋 Usuarios conectados:\n")
        users.forEachIndexed { idx, (_, userInfo) ->
            println("  ${idx + 1}. ${userInfo.username} (ID: ${userInfo.user_id.take(12)}...)")
        }

        println("\n  0. Actualizar lista")
        println("  Q. Salir")
        println("-".repeat(80))

        try {
            print("\n👉 Selecciona un número: ")
            val choice = readln().trim().lowercase()

            if (choice == "q") {
                return null
            }

            if (choice == "0") {
                continue
            }

            val idx = choice.toIntOrNull()?.minus(1)
            if (idx != null && idx in users.indices) {
                val (selectedClientId, selectedUser) = users[idx]
                return mapOf(
                    "client_id" to selectedClientId,
                    "user_id" to selectedUser.user_id,
                    "username" to selectedUser.username
                )
            } else {
                println("❌ Opción inválida")
                Thread.sleep(1000)
            }
        } catch (_: Exception) {
            return null
        }
    }
    return null
}

fun chatLoop(client: InteractiveChatClient, otherUser: Map<String, String>): Boolean {
    client.otherClientId = otherUser["client_id"]
    client.otherUserId = otherUser["user_id"]
    client.otherUsername = otherUser["username"]

    // Limpiar historial previo
    client.conversationHistory.clear()

    // Cargar conversación existente
    println("\n📥 Cargando historial de conversación...")
    client.getConversation(client.otherUserId!!)

    clearScreen()
    println("=".repeat(80))
    println("💬 Chat con ${client.otherUsername}")
    println("=".repeat(80))

    if (client.conversationHistory.isNotEmpty()) {
        println("\n📜 Últimos mensajes:")
        client.conversationHistory.takeLast(5).forEach { msg ->
            val direction = if (msg["direction"] == "sent") "Tú" else client.otherUsername
            println("  $direction: ${msg["text"]}")
        }
    }

    println("\n" + "-".repeat(80))
    println("Escribe tus mensajes y presiona Enter para enviar")
    println("Escribe '/menu' para volver al menú de usuarios")
    println("Ctrl+C para salir")
    println("-".repeat(80) + "\n")

    try {
        while (true) {
            print("> ")
            val message = readln().trim()

            if (message.isEmpty()) continue

            // Comando especial para volver al menú
            if (message.lowercase() == "/menu") {
                return true // Volver al menú
            }

            // Enviar mensaje
            if (client.sendMessage(client.otherUserId!!, message)) {
                println("✓ Enviado")
            } else {
                println("✗ Error al enviar")
            }
        }
    } catch (_: Exception) {
        return false // Salir completamente
    }
}

fun main() {
    println("=".repeat(80))
    println("💬 CHAT INTERACTIVO - Servicio de Mensajería")
    println("=".repeat(80))

    // Pedir datos del usuario
    print("\n👤 Tu nombre de usuario: ")
    var username = readln().trim()
    if (username.isEmpty()) {
        username = "Usuario_${UUID.randomUUID().toString().take(4)}"
    }

    // Pedir o generar user_id
    print("🆔 Tu User ID (ObjectId, Enter para generar uno nuevo): ")
    val userIdInput = readln().trim()
    val userId = if (userIdInput.isNotEmpty()) {
        try {
            ObjectId(userIdInput).toString()
        } catch (_: Exception) {
            println("⚠️ ID inválido, generando uno nuevo...")
            ObjectId().toString()
        }
    } else {
        ObjectId().toString()
    }

    println("\n✅ Tu User ID: $userId")
    println("⏳ Conectando y descubriendo usuarios...")

    // Crear cliente
    val client = InteractiveChatClient(userId, username)

    // Conectar
    if (!client.connect()) {
        println("❌ No se pudo conectar al servidor")
        exitProcess(1)
    }

    // Esperar un poco para recibir broadcasts de otros usuarios
    Thread.sleep(2000)

    // Thread para heartbeat
    thread(isDaemon = true) {
        while (client.running) {
            Thread.sleep(30000)
            client.heartbeat()
        }
    }

    // Thread para broadcast periódico de presencia
    thread(isDaemon = true) {
        while (client.running) {
            Thread.sleep(10000)
            client.broadcastMyPresence()
        }
    }

    try {
        while (true) {
            // Mostrar menú de usuarios
            val selectedUser = showUserMenu(client)

            if (selectedUser == null) {
                // Usuario quiere salir
                break
            }

            // Entrar al chat
            val continueMenu = chatLoop(client, selectedUser)

            if (!continueMenu) {
                // Salir completamente
                break
            }
        }
    } catch (_: Exception) {
        // Ignorar interrupciones
    } finally {
        println("\n\n👋 Cerrando chat...")
        if (client.conversationHistory.isNotEmpty()) {
            client.showConversationSummary()
        }
        client.disconnect()
        println("\n✅ Desconectado. ¡Hasta pronto!")
    }
}
