package com.example.citofono

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.bson.types.ObjectId
import java.io.*
import java.net.Socket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * ============================================================================
 * MODELOS DE DATOS - Estructuras para comunicación con el BUS y servicios
 * ============================================================================
 */

/**
 * BusMessage: Representa un mensaje completo que se envía/recibe del BUS
 * - type: Tipo de mensaje (REGISTER, REQUEST, BROADCAST, DIRECT, etc.)
 * - sender: ID del cliente que envía el mensaje
 * - target: ID del cliente destino (para mensajes DIRECT)
 * - service: Nombre del servicio al que se dirige (ej: "Mensajeria")
 * - header: Encabezado con metadatos del mensaje
 * - payload: Carga útil con los datos específicos de la acción
 * - kind: Tipo de cliente ("client" o "service")
 * - client_id: Identificador único del cliente
 * - status: Estado de la respuesta ("success", "error")
 * - event: Nombre del evento (para BROADCAST)
 * - data: Datos adicionales del evento
 */
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

/**
 * Header: Encabezado de un mensaje con metadatos
 * - correlationId: ID único para correlacionar request/response
 * - service: Nombre del servicio destino
 */
data class Header(
    val correlationId: String? = null,
    val service: String? = null
)

/**
 * Payload: Carga útil de un mensaje con los datos de la acción
 * - action: Acción a ejecutar (send, connect, getConversation, etc.)
 * - userId: ID del usuario que realiza la acción
 * - senderObjId: ID del remitente del mensaje
 * - receiverObjId: ID del destinatario del mensaje
 * - message: Texto del mensaje a enviar
 * - ok: Indicador de éxito de la operación
 * - error: Mensaje de error (si ok=false)
 * - messageId: ID único del mensaje
 * - timestamp: Marca de tiempo del mensaje
 * - messages: Lista de mensajes en una conversación
 * - hasMore: Indica si hay más mensajes disponibles
 * - conversationId: ID de la conversación
 * - isTyping: Indica si el usuario está escribiendo
 * - pong: Respuesta a un ping/heartbeat
 */
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

/**
 * ConversationMessage: Representa un mensaje individual en una conversación
 * - id: ID único del mensaje
 * - from: ID del usuario remitente
 * - to: ID del usuario destinatario
 * - text: Contenido del mensaje
 * - ts: Timestamp en formato ISO 8601
 * - deliveryStatus: Estado de entrega ("sent", "delivered")
 * - readStatus: Estado de lectura ("read", "unread")
 */
data class ConversationMessage(
    val id: String,
    val from: String,
    val to: String,
    val text: String,
    val ts: String,
    val deliveryStatus: String? = null,
    val readStatus: String? = null
)

/**
 * UserInfo: Información básica de un usuario
 * - user_id: ID único del usuario en la base de datos
 * - username: Nombre de usuario para mostrar
 */
data class UserInfo(
    val user_id: String,
    val username: String
)

/**
 * OnlineUser: Usuario conectado en el sistema
 * - client_id: ID de la sesión/conexión del cliente
 * - user_id: ID del usuario en la base de datos
 * - username: Nombre de usuario
 */
data class OnlineUser(
    val client_id: String,
    val user_id: String,
    val username: String
)

/**
 * ============================================================================
 * FUNCIONES AUXILIARES DE COMUNICACIÓN
 * ============================================================================
 */

/**
 * sendJsonLine: Envía un objeto como JSON serializado al socket
 * @param sock Socket de comunicación
 * @param obj Objeto a serializar y enviar
 *
 * Convierte el objeto a JSON usando Gson y lo envía con un salto de línea
 * al final para que el receptor pueda delimitar los mensajes
 */
fun sendJsonLine(sock: Socket, obj: Any) {
    val gson = Gson()
    val json = gson.toJson(obj) + "\n"
    sock.getOutputStream().write(json.toByteArray(Charsets.UTF_8))
    sock.getOutputStream().flush()
}

/**
 * recvJsonLine: Recibe una línea JSON del socket y la deserializa
 * @param sock Socket de comunicación
 * @param timeout Tiempo máximo de espera en segundos (default: 5.0)
 * @return Map con los datos deserializados
 *
 * Lee una línea del socket (hasta encontrar '\n') y la parsea como JSON
 * usando Gson, retornando un Map con los datos
 */
fun recvJsonLine(sock: Socket, timeout: Double = 5.0): Map<String, Any> {
    sock.soTimeout = (timeout * 1000).toInt()
    val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
    val line = reader.readLine()
    val gson = Gson()
    val type = object : TypeToken<Map<String, Any>>() {}.type
    return gson.fromJson<Map<String, Any>>(line, type) ?: emptyMap()
}

/**
 * ============================================================================
 * CLASE PRINCIPAL: InteractiveChatClient
 * ============================================================================
 *
 * Cliente de chat interactivo que se conecta al BUS de mensajería y permite:
 * - Conectarse al BUS de comunicación
 * - Registrarse en el servicio de mensajería
 * - Ver usuarios online
 * - Enviar y recibir mensajes en tiempo real
 * - Manejar eventos de presencia y typing
 * - Persistir mensajes en MongoDB a través del servicio
 *
 * @property userId ID único del usuario en la base de datos
 * @property username Nombre de usuario para mostrar
 * @property busHost Dirección del BUS (default: 127.0.0.1)
 * @property busPort Puerto del BUS (default: 5000)
 */
class InteractiveChatClient(
    val userId: String,
    val username: String,
    private val busHost: String = "127.0.0.1",
    private val busPort: Int = 5000
) {
    // Socket de conexión con el BUS
    private var socket: Socket? = null

    // ID único de esta sesión de cliente
    private val clientId = "chat_${UUID.randomUUID().toString().take(8)}"

    // Flag que indica si el cliente está activo
    var running = false
        private set

    // Lista de eventos recibidos (accesible para el ViewModel)
    val events = mutableListOf<Map<String, Any>>()
    private val eventsLock = ReentrantLock()

    /**
     * clearEvents: Limpia la lista de eventos acumulados
     *
     * Debe llamarse al abrir un nuevo chat para evitar procesar eventos antiguos
     * de conversaciones anteriores
     */
    fun clearEvents() {
        eventsLock.lock()
        try {
            events.clear()
            android.util.Log.d("ChatClient", "🧹 Eventos limpiados")
        } finally {
            eventsLock.unlock()
        }
    }

    /**
     * getEventsCount: Obtiene el número actual de eventos
     *
     * Útil para que el listener sepa desde dónde empezar a leer eventos nuevos
     */
    fun getEventsCount(): Int {
        eventsLock.lock()
        try {
            return events.size
        } finally {
            eventsLock.unlock()
        }
    }

    // Cola de respuestas pendientes de procesar
    private val responseQueue = mutableListOf<Map<String, Any>>()
    private val queueLock = ReentrantLock()

    // Historial de mensajes de la conversación actual
    val conversationHistory = mutableListOf<Map<String, Any>>()

    // Información del otro usuario en el chat actual
    var otherUserId: String? = null
    var otherUsername: String? = null
    var otherClientId: String? = null
    private var isOtherTyping = false

    // Variables temporales para gestión del chat actual
    // Se usan para rastrear el último mensaje y evitar duplicados
    var currentChatSenderId: String? = null
    var currentChatReceiverId: String? = null
    var lastMessageTimestamp: String? = null

    // Lista de usuarios conectados al sistema
    private val onlineUsers = ConcurrentHashMap<String, UserInfo>()
    private val usersLock = ReentrantLock()

    private val gson = Gson()

    /**
     * connect: Establece conexión con el BUS y registra el cliente
     * @return true si la conexión fue exitosa, false en caso contrario
     *
     * Proceso de conexión:
     * 1. Crea el socket TCP al BUS
     * 2. Envía mensaje REGISTER con el tipo "client"
     * 3. Espera confirmación REGISTER_ACK
     * 4. Inicia el listener de eventos en un thread separado
     * 5. Se conecta al servicio de mensajería
     * 6. Broadcast de presencia para que otros usuarios lo vean
     */
    fun connect(): Boolean {
        println("\n🔌 Conectando al BUS en $busHost:$busPort...")
        android.util.Log.d("ChatClient", "=== INICIANDO CONNECT ===")
        android.util.Log.d("ChatClient", "BUS Host: $busHost")
        android.util.Log.d("ChatClient", "BUS Port: $busPort")
        android.util.Log.d("ChatClient", "Client ID: $clientId")

        return try {
            android.util.Log.d("ChatClient", "Paso 1: Creando socket...")
            // Usar connect con timeout para evitar bloqueos indefinidos
            socket = Socket()
            socket?.connect(InetSocketAddress(busHost, busPort), 10000) // 10s timeout
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

    /**
     * broadcastMyPresence: Envía un mensaje BROADCAST con la presencia del usuario
     *
     * Notifica a todos los clientes conectados que este usuario está online
     * Incluye: client_id, user_id y username
     */
    fun broadcastMyPresence() {
        try {
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

    /**
     * listenEvents: Hilo que escucha continuamente mensajes del BUS
     *
     * Este método corre en un thread separado y procesa:
     * - Mensajes BROADCAST (presencia de usuarios, notificaciones globales)
     * - Mensajes DIRECT (mensajes privados, eventos específicos)
     * - Respuestas a peticiones REQUEST
     *
     * Los mensajes DIRECT con eventos se agregan a la lista 'events'
     * Las respuestas a REQUEST se agregan a 'responseQueue' para procesamiento
     */
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

    /**
     * handleBroadcast: Procesa mensajes BROADCAST recibidos
     * @param msg Mapa con el mensaje BROADCAST
     *
     * Maneja eventos:
     * - user_joined: Nuevo usuario conectado (respondemos con nuestra presencia)
     * - user_left: Usuario desconectado (lo removemos de la lista)
     * - user_presence: Presencia de un usuario (lo agregamos a la lista)
     */
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

    /**
     * handleEvent: Procesa eventos DIRECT específicos del chat
     * @param event Nombre del evento
     * @param data Datos asociados al evento
     *
     * Maneja:
     * - new_message: Nuevo mensaje recibido (lo agrega al historial)
     * - user_typing: Usuario está escribiendo (actualiza flag)
     * - message_read: Mensaje fue leído (muestra confirmación)
     */
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

    /**
     * waitForResponse: Espera una respuesta con un correlationId específico
     * @param correlationId ID de correlación del request
     * @param timeout Tiempo máximo de espera en segundos
     * @return Mapa con la respuesta recibida
     * @throws TimeoutException si no se recibe respuesta en el tiempo especificado
     *
     * Busca en responseQueue un mensaje que coincida con el correlationId
     * y lo retorna, removiéndolo de la cola
     */
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

    /**
     * sendAction: Envía una acción al servicio de mensajería y espera respuesta
     * @param action Nombre de la acción (send, getConversation, connect, etc.)
     * @param payload Datos adicionales de la acción
     * @param optional Si es true, no lanza excepción en caso de timeout
     * @return Mapa con el payload de la respuesta
     *
     * Crea un REQUEST con correlationId único, lo envía al servicio "Mensajeria"
     * y espera la respuesta correspondiente usando waitForResponse
     */
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

    /**
     * sendMessage: Envía un mensaje a otro usuario
     * @param receiverId ID del usuario destinatario
     * @param message Texto del mensaje
     * @return true si el mensaje se envió correctamente
     *
     * Envía la acción "send" al servicio de mensajería con:
     * - senderObjId: ID del remitente
     * - receiverObjId: ID del destinatario
     * - message: Texto del mensaje
     *
     * El mensaje se persiste en MongoDB a través del servicio
     * NO lo agrega al historial local (lo hace MessageViewModel para evitar duplicados)
     */
    fun sendMessage(receiverId: String, message: String): Boolean {
        return try {
            val payload = sendAction("send", mapOf(
                "senderObjId" to userId,
                "receiverObjId" to receiverId,
                "message" to message
            ))

            if (payload["ok"] == true) {
                // NO agregar al historial aquí - MessageViewModel ya lo maneja
                // Esto evita duplicados al recargar desde MongoDB
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

    /**
     * getConversation: Obtiene el historial de conversación con otro usuario
     * @param otherUserId ID del otro usuario
     * @return Lista de mensajes de la conversación desde MongoDB
     *
     * Solicita al servicio de mensajería los últimos 50 mensajes entre
     * el usuario actual y el otro usuario especificado
     *
     * NO los agrega al historial local - solo los retorna
     * MessageViewModel se encarga de manejarlos y evitar duplicados
     */
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
                // NO agregar al historial aquí - MessageViewModel ya lo maneja
                // Esto evita duplicados al recargar desde MongoDB
                messages
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * getMessagesSince: Obtiene mensajes nuevos desde un timestamp específico
     * @param timestamp Timestamp ISO 8601 desde el cual obtener mensajes
     * @return Lista de mensajes posteriores al timestamp
     *
     * Útil para obtener solo mensajes nuevos sin cargar toda la conversación
     * Los agrega al historial local de conversationHistory
     */
    fun getMessagesSince(timestamp: String): List<Map<String, Any>> {
        return try {
            val payload = sendAction("getMessagesSince", mapOf(
                "userId" to userId,
                "timestamp" to timestamp,
                "limit" to 50
            ))

            if (payload["ok"] == true) {
                @Suppress("UNCHECKED_CAST")
                val messages = payload["messages"] as? List<Map<String, Any>> ?: emptyList()
                // Agregar al historial solo los mensajes nuevos
                conversationHistory.addAll(messages)
                messages
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * setTyping: Notifica al otro usuario que estamos escribiendo
     * @param conversationId ID de la conversación
     * @param isTyping true si está escribiendo, false si dejó de escribir
     *
     * Envía la acción "typing" al servicio de mensajería
     * Es opcional, no lanza excepciones si falla
     */
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

    /**
     * getOnlineUsersList: Obtiene la lista de usuarios online
     * @return Lista de pares (client_id, UserInfo)
     *
     * Thread-safe: usa lock para acceder a onlineUsers
     */
    fun getOnlineUsersList(): List<Pair<String, UserInfo>> {
        usersLock.lock()
        try {
            return onlineUsers.map { it.key to it.value }
        } finally {
            usersLock.unlock()
        }
    }

    /**
     * showConversationSummary: Muestra el historial de conversación en consola
     *
     * Imprime todos los mensajes del historial con formato:
     * - Dirección (➡️ enviado, ⬅️ recibido)
     * - Remitente
     * - Timestamp
     * - Texto del mensaje
     */
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

    /**
     * heartbeat: Envía un ping al servicio para mantener la conexión viva
     *
     * Se debe llamar periódicamente (cada 30 segundos recomendado)
     * Es opcional, no lanza excepciones si falla
     */
    fun heartbeat() {
        try {
            sendAction("heartbeat", mapOf("userId" to userId), optional = true)
        } catch (e: Exception) {
            // Ignorar errores
        }
    }

    /**
     * disconnect: Desconecta el cliente del BUS
     *
     * 1. Marca running = false para detener el listener
     * 2. Envía acción "disconnect" al servicio
     * 3. Cierra el socket
     */
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

/**
 * ============================================================================
 * FUNCIONES DE UTILIDAD PARA CONSOLA
 * ============================================================================
 */

/**
 * clearScreen: Limpia la pantalla de la consola
 * Detecta el sistema operativo y ejecuta el comando apropiado
 */
fun clearScreen() {
    if (System.getProperty("os.name")?.contains("Windows") == true) {
        ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor()
    } else {
        print("\u001b[H\u001b[2J")
        System.out.flush()
    }
}

/**
 * showUserMenu: Muestra un menú interactivo para seleccionar con quién chatear
 * @param client Cliente de chat
 * @return Mapa con client_id, user_id y username del usuario seleccionado, o null si cancela
 *
 * Muestra la lista de usuarios online y permite:
 * - Seleccionar un usuario por número
 * - Actualizar la lista (0)
 * - Salir (Q)
 *
 * Si no hay usuarios, espera 5 segundos y actualiza automáticamente
 */
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

/**
 * chatLoop: Bucle principal de chat con otro usuario
 * @param client Cliente de chat
 * @param otherUser Mapa con client_id, user_id y username del otro usuario
 * @return true si se debe volver al menú de usuarios, false si se sale completamente
 *
 * 1. Establece el otro usuario en el cliente
 * 2. Limpia y carga el historial de conversación
 * 3. Muestra el encabezado del chat
 * 4. Entra en un bucle para leer y enviar mensajes
 * 5. Permite salir al menú con el comando '/menu'
 */
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

/**
 * main: Función principal del programa
 *
 * 1. Muestra el encabezado del chat interactivo
 * 2. Pide y establece el username y userId del cliente
 * 3. Crea el cliente de chat y se conecta al BUS
 * 4. Inicia los threads de heartbeat y broadcast de presencia
 * 5. Entra en un bucle para mostrar el menú de usuarios y manejar chats
 * 6. Al salir, muestra el resumen de conversación y desconecta el cliente
 */
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
