package com.example.citofono

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.text.Charsets

/**
 * ============================================================================
 * MessageViewModel - ViewModel para gestión de mensajes en un chat individual
 * ============================================================================
 *
 * FLUJO CORRECTO (según especificación):
 * 1. Al abrir el chat: Solicitar TODOS los mensajes de MongoDB entre sender y receiver
 * 2. Ordenar mensajes por timestamp (fecha y hora de envío)
 * 3. Mostrar en pantalla
 * 4. Limpiar eventos previos del cliente para evitar procesar eventos antiguos
 * 5. Escuchar SOLO nuevos eventos en tiempo real (desde el índice actual en adelante)
 * 6. Cada mensaje enviado se persiste inmediatamente en MongoDB
 * 7. Al cerrar y volver a abrir el chat, repetir el proceso (carga fresca desde BD)
 */

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isSent: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val deliveryStatus: String = "sending",
    val senderUserId: String = "",
    val receiverUserId: String = ""
)

sealed class ConversationState {
    object Loading : ConversationState()
    data class Loaded(val messages: List<ChatMessage>) : ConversationState()
    data class Error(val message: String) : ConversationState()
}

@SuppressLint("StaticFieldLeak")
class MessageViewModel(
    private val context: Context,
    private val chatClient: InteractiveChatClient,
    private val otherUserId: String,
    private val otherUsername: String
) : ViewModel() {

    private val TAG = "MessageViewModel"

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _conversationState = MutableStateFlow<ConversationState>(ConversationState.Loading)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    private val _isOtherTyping = MutableStateFlow(false)
    val isOtherTyping: StateFlow<Boolean> = _isOtherTyping.asStateFlow()

    // Set para rastrear IDs de mensajes ya procesados (evita duplicados en esta sesión)
    private val processedMessageIds = mutableSetOf<String>()

    // Índice desde el cual empezar a leer eventos nuevos
    private var eventStartIndex = 0

    init {
        // Configurar el chat actual
        chatClient.currentChatSenderId = chatClient.userId
        chatClient.currentChatReceiverId = otherUserId
        chatClient.otherUserId = otherUserId
        chatClient.otherUsername = otherUsername

        // PASO 1: Limpiar eventos previos para no procesar eventos antiguos
        chatClient.clearEvents()

        // PASO 2: Obtener índice actual de eventos (desde aquí escucharemos nuevos)
        eventStartIndex = chatClient.getEventsCount()

        Log.d(TAG, "=== INICIALIZANDO CHAT ===")
        Log.d(TAG, "Chat: ${chatClient.userId} <-> $otherUserId")
        Log.d(TAG, "Eventos limpiados, índice inicial: $eventStartIndex")

        // PASO 3: Cargar todos los mensajes desde MongoDB
        loadAllMessagesFromDatabase()

        // PASO 4: Iniciar listener de eventos en tiempo real (solo nuevos)
        startRealtimeMessageListener()
    }

    /**
     * loadAllMessagesFromDatabase: Carga TODOS los mensajes de la conversación desde MongoDB
     *
     * FLUJO:
     * 1. Solicita al BUS -> Servicio de Mensajería -> MongoDB todos los mensajes
     * 2. Recibe lista de mensajes entre user1 y user2
     * 3. Ordena por timestamp (fecha y hora de envío) ascendente
     * 4. Convierte a ChatMessage
     * 5. Marca como procesados para evitar duplicados con eventos en tiempo real
     * 6. Actualiza la UI
     */
    private fun loadAllMessagesFromDatabase() {
        viewModelScope.launch {
            try {
                _conversationState.value = ConversationState.Loading

                Log.d(TAG, "📥 Cargando mensajes desde MongoDB...")
                Log.d(TAG, "Entre: ${chatClient.userId} <-> $otherUserId")

                withContext(Dispatchers.IO) {
                    try {
                        // Solicitar todos los mensajes al servicio de mensajería
                        val serverMessages = chatClient.getConversation(otherUserId)

                        Log.d(TAG, "☁️ MongoDB retornó ${serverMessages.size} mensajes")
                        Log.d(TAG, "📋 chatClient.userId = ${chatClient.userId}")
                        Log.d(TAG, "📋 otherUserId = $otherUserId")

                        if (serverMessages.isEmpty()) {
                            _messages.value = emptyList()
                            _conversationState.value = ConversationState.Loaded(emptyList())
                            Log.d(TAG, "✅ Conversación vacía (sin mensajes previos)")
                            return@withContext
                        }

                        // Convertir y ordenar mensajes por timestamp
                        val convertedMessages = serverMessages.mapNotNull { msg ->
                            try {
                                val text = (msg["text"] as? String)
                                    ?: (msg["mensaje"] as? String)
                                    ?: return@mapNotNull null
                                val timestampStr = msg["ts"] as? String ?: ""
                                val timestamp = parseTimestamp(timestampStr)

                                val (senderCandidates, senderStableKey) = resolveSenderDetails(msg)
                                val (_, receiverDisplay) = resolveReceiverDetails(msg)
                                val senderDisplay = senderCandidates.names.firstOrNull()
                                    ?: senderStableKey.ifBlank { (msg["from"] as? String)?.trim().orEmpty() }

                                val senderIsCurrent = matchesUser(senderCandidates, chatClient.userId)
                                val senderIsOther = matchesUser(senderCandidates, otherUserId)

                                val fallbackFrom = (msg["from"] as? String)?.trim()
                                val fallbackHex = fallbackFrom
                                    ?.takeIf { it.isNotEmpty() && hex24Regex.matches(it) }
                                    ?.lowercase(Locale.ROOT)
                                val otherHash = deterministicObjectId(otherUserId)?.lowercase(Locale.ROOT)

                                val isSent = when {
                                    senderIsCurrent -> true
                                    senderIsOther -> false
                                    fallbackFrom == null -> true
                                    fallbackHex != null && otherHash != null -> fallbackHex != otherHash
                                    else -> !fallbackFrom.equals(otherUserId, ignoreCase = true)
                                }

                                val messageId = generateMessageId(
                                    (senderStableKey.ifBlank { senderDisplay.ifBlank { "unknown" } }),
                                    timestamp,
                                    text
                                )

                                processedMessageIds.add(messageId)

                                val senderUserIdForMessage = when {
                                    senderIsCurrent -> chatClient.userId
                                    senderIsOther -> otherUserId
                                    senderDisplay.isNotBlank() -> senderDisplay
                                    else -> senderStableKey.ifBlank { chatClient.userId }
                                }
                                val receiverUserIdForMessage = if (isSent) {
                                    receiverDisplay.ifBlank { otherUserId }
                                } else {
                                    chatClient.userId
                                }

                                ChatMessage(
                                    id = messageId,
                                    text = text,
                                    isSent = isSent,
                                    timestamp = timestamp,
                                    deliveryStatus = "delivered",
                                    senderUserId = senderUserIdForMessage,
                                    receiverUserId = receiverUserIdForMessage
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parseando mensaje", e)
                                null
                            }
                        }

                        // IMPORTANTE: Ordenar por timestamp ascendente (más antiguo primero)
                        val sortedMessages = convertedMessages.sortedBy { it.timestamp }

                        _messages.value = sortedMessages
                        _conversationState.value = ConversationState.Loaded(sortedMessages)

                        Log.d(TAG, "✅ ${sortedMessages.size} mensajes cargados y ordenados")
                        Log.d(TAG, "📊 IDs procesados: ${processedMessageIds.size}")

                        // Log DETALLADO del orden para depuración
                        if (sortedMessages.isNotEmpty()) {
                            sortedMessages.forEachIndexed { index, msg ->
                                Log.d(TAG, "[$index] timestamp=${msg.timestamp} text=${msg.text.take(15)}... isSent=${msg.isSent}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error consultando MongoDB", e)
                        _conversationState.value = ConversationState.Error(
                            e.message ?: "Error al cargar mensajes"
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error en loadAllMessagesFromDatabase", e)
                _conversationState.value = ConversationState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    /**
     * sendMessage: Envía un mensaje al otro usuario
     *
     * FLUJO:
     * 1. Crea mensaje local con estado "sending"
     * 2. Genera ID único
     * 3. Verifica que no esté duplicado
     * 4. Agrega a la UI inmediatamente (optimistic update)
     * 5. Envía al BUS -> Servicio -> MongoDB para persistir
     * 6. Actualiza estado del mensaje según respuesta
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            val timestamp = System.currentTimeMillis()
            val messageId = generateMessageId(chatClient.userId, timestamp, text)

            // Verificar duplicados
            if (processedMessageIds.contains(messageId)) {
                Log.w(TAG, "⚠️ Mensaje duplicado, ignorando: ${text.take(20)}...")
                return@launch
            }

            processedMessageIds.add(messageId)

            // Crear mensaje local
            val message = ChatMessage(
                id = messageId,
                text = text,
                isSent = true,
                timestamp = timestamp,
                deliveryStatus = "sending",
                senderUserId = chatClient.userId,
                receiverUserId = otherUserId
            )

            // Agregar a la UI inmediatamente (optimistic update)
            val currentMessages = _messages.value.toMutableList()
            currentMessages.add(message)
            // Mantener orden cronológico
            _messages.value = currentMessages.sortedBy { it.timestamp }

            Log.d(TAG, "📤 Enviando mensaje a MongoDB: ${text.take(30)}...")

            // Enviar al servidor para persistir en MongoDB
            withContext(Dispatchers.IO) {
                try {
                    val success = chatClient.sendMessage(otherUserId, text, otherUsername)

                    // Actualizar estado del mensaje
                    val updatedMessages = _messages.value.map {
                        if (it.id == messageId) {
                            it.copy(deliveryStatus = if (success) "sent" else "error")
                        } else {
                            it
                        }
                    }
                    _messages.value = updatedMessages

                    if (success) {
                        Log.d(TAG, "✅ Mensaje persistido en MongoDB")
                    } else {
                        Log.e(TAG, "❌ Error persistiendo en MongoDB")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Excepción enviando mensaje", e)

                    val updatedMessages = _messages.value.map {
                        if (it.id == messageId) {
                            it.copy(deliveryStatus = "error")
                        } else {
                            it
                        }
                    }
                    _messages.value = updatedMessages
                }
            }
        }
    }

    /**
     * startRealtimeMessageListener: Solución híbrida para recibir mensajes en tiempo real
     *
     * PROBLEMA ACTUAL: El servicio de mensajería NO envía eventos new_message cuando
     * otro usuario envía un mensaje. Solo responde al REQUEST de envío.
     *
     * SOLUCIÓN HÍBRIDA:
     * 1. Escuchar eventos del BUS (por si en el futuro el backend los envía)
     * 2. Polling ligero cada 3 segundos para detectar mensajes nuevos del otro usuario
     * 3. Los mensajes propios se agregan inmediatamente en sendMessage() (optimistic update)
     */
    private fun startRealtimeMessageListener() {
        Log.d(TAG, "🎧 Listener híbrido INICIADO (eventos + polling)")
        Log.d(TAG, "   Índice inicial de eventos: $eventStartIndex")

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                while (true) {
                    try {
                        kotlinx.coroutines.delay(3000) // Cada 3 segundos

                        // PARTE 1: Procesar eventos del BUS (si existen)
                        val currentEvents = chatClient.events.toList()
                        val eventsCount = currentEvents.size

                        if (eventsCount > eventStartIndex) {
                            val newEvents = currentEvents.subList(eventStartIndex, eventsCount)
                            Log.d(TAG, "📨 ${newEvents.size} evento(s) del BUS detectado(s)")

                            newEvents.forEach { eventMap ->
                                try {
                                    val eventType = eventMap["event"] as? String
                                    @Suppress("UNCHECKED_CAST")
                                    val eventData = eventMap["data"] as? Map<String, Any>

                                    if (eventType == "new_message" && eventData != null) {
                                        processNewMessageEvent(eventData)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error procesando evento", e)
                                }
                            }

                            eventStartIndex = eventsCount
                        }

                        // PARTE 2: Polling ligero para mensajes del OTRO usuario solamente
                        // (los propios ya se agregan en sendMessage con optimistic update)
                        checkForNewMessages()

                    } catch (e: Exception) {
                        Log.e(TAG, "Error en listener", e)
                    }
                }
            }
        }
    }

    /**
     * processNewMessageEvent: Procesa un evento new_message del BUS
     */
    private fun processNewMessageEvent(eventData: Map<String, Any>) {
        val text = eventData["text"] as? String ?: ""
        val timestampStr = eventData["timestamp"] as? String ?: ""

        if (text.isBlank()) {
            Log.d(TAG, "⚠️ Evento sin texto, ignorando")
            return
        }

        val (senderCandidates, senderKey) = resolveSenderDetails(eventData)
        val (receiverCandidates, _) = resolveReceiverDetails(eventData)
        val senderMatchesCurrent = matchesUser(senderCandidates, chatClient.userId)
        val senderMatchesOther = matchesUser(senderCandidates, otherUserId)
        val receiverMatchesCurrent = matchesUser(receiverCandidates, chatClient.userId)
        val receiverMatchesOther = matchesUser(receiverCandidates, otherUserId)

        if (!(senderMatchesCurrent || senderMatchesOther) || !(receiverMatchesCurrent || receiverMatchesOther)) {
            Log.d(TAG, "⏭️ Mensaje de otra conversación, ignorando")
            return
        }

        val timestamp = parseTimestampFromEvent(timestampStr)
        val messageId = generateMessageId(
            senderKey.ifBlank { (eventData["from"] as? String).orEmpty() },
            timestamp,
            text
        )

        if (processedMessageIds.contains(messageId)) {
            Log.d(TAG, "⚠️ Mensaje duplicado, ignorando")
            return
        }

        processedMessageIds.add(messageId)

        val newMessage = ChatMessage(
            id = messageId,
            text = text,
            isSent = senderMatchesCurrent,
            timestamp = timestamp,
            deliveryStatus = "delivered",
            senderUserId = when {
                senderMatchesCurrent -> chatClient.userId
                senderMatchesOther -> otherUserId
                senderKey.isNotBlank() -> senderKey
                else -> chatClient.userId
            },
            receiverUserId = if (senderMatchesCurrent) otherUserId else chatClient.userId
        )

        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(newMessage)
        _messages.value = currentMessages.sortedBy { it.timestamp }

        Log.d(TAG, "✅ Mensaje del evento agregado")
    }

    /**
     * checkForNewMessages: Consulta MongoDB por mensajes nuevos (solo del otro usuario)
     *
     * Esta función hace polling ligero para detectar mensajes que el otro usuario
     * envió pero que no llegaron como evento del BUS
     */
    private suspend fun checkForNewMessages() {
        try {
            val serverMessages = chatClient.getConversation(otherUserId)

            if (serverMessages.isEmpty()) return

            // Solo procesar mensajes DEL OTRO USUARIO que no hayamos visto
            serverMessages.forEach { msg ->
                try {
                    val (senderCandidates, senderKey) = resolveSenderDetails(msg)

                    // CRÍTICO: Solo agregar mensajes donde el remitente sea el otro usuario
                    if (!matchesUser(senderCandidates, otherUserId)) {
                        return@forEach
                    }

                    val text = (msg["text"] as? String)
                        ?: (msg["mensaje"] as? String)
                        ?: return@forEach
                    val timestampStr = msg["ts"] as? String ?: ""
                    val timestamp = parseTimestamp(timestampStr)

                    val messageId = generateMessageId(
                        senderKey.ifBlank { otherUserId },
                        timestamp,
                        text
                    )

                    // Solo agregar si es nuevo
                    if (!processedMessageIds.contains(messageId)) {
                        processedMessageIds.add(messageId)

                        val newMessage = ChatMessage(
                            id = messageId,
                            text = text,
                            isSent = false,
                            timestamp = timestamp,
                            deliveryStatus = "delivered",
                            senderUserId = senderCandidates.names.firstOrNull()
                                ?: senderKey.ifBlank { otherUserId },
                            receiverUserId = chatClient.userId
                        )

                        val currentMessages = _messages.value.toMutableList()
                        currentMessages.add(newMessage)
                        _messages.value = currentMessages.sortedBy { it.timestamp }

                        Log.d(TAG, "💬 ✅ Mensaje nuevo del otro usuario detectado: ${text.take(20)}...")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando mensaje", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando mensajes nuevos", e)
        }
    }

    private val hex24Regex = Regex("^[0-9a-fA-F]{24}$")

    private data class UserCandidates(
        val names: List<String>,
        val ids: List<String>
    )

    private fun collectUserCandidates(
        source: Map<String, Any?>,
        objectKey: String,
        fallbackKeys: List<String>
    ): UserCandidates {
        val names = LinkedHashSet<String>()
        val ids = LinkedHashSet<String>()

        fun addValue(value: Any?) {
            val raw = when (value) {
                is String -> value
                is Number -> value.toString()
                else -> null
            }?.trim()?.takeIf { it.isNotEmpty() } ?: return
            if (hex24Regex.matches(raw)) {
                ids += raw.lowercase(Locale.ROOT)
            } else {
                names += raw
            }
        }

        val obj = source[objectKey] as? Map<*, *>
        obj?.let {
            addValue(it["username"])
            addValue(it["name"])
            addValue(it["userId"])
            addValue(it["id"])
        }

        fallbackKeys.forEach { key ->
            addValue(source[key])
        }

        return UserCandidates(names.toList(), ids.toList())
    }

    private fun matchesUser(candidates: UserCandidates, userId: String): Boolean {
        if (userId.isBlank()) return false
        val normalized = userId.trim()
        if (candidates.names.any { it.equals(normalized, ignoreCase = true) }) {
            return true
        }
        val hashed = deterministicObjectId(normalized)?.lowercase(Locale.ROOT)
        return hashed != null && candidates.ids.any { it.equals(hashed, ignoreCase = true) }
    }

    private fun resolveSenderDetails(msg: Map<String, Any?>): Pair<UserCandidates, String> {
        val candidates = collectUserCandidates(
            msg,
            "sender",
            listOf(
                "from", "fromId", "senderId", "senderUserId",
                "senderUsername", "senderName", "senderRaw", "fromUserId"
            )
        )
        val stableKey = candidates.ids.firstOrNull()
            ?: candidates.names.firstOrNull()
            ?: (msg["from"] as? String)?.trim().orEmpty()
        return candidates to stableKey
    }

    private fun resolveReceiverDetails(msg: Map<String, Any?>): Pair<UserCandidates, String> {
        val candidates = collectUserCandidates(
            msg,
            "receiver",
            listOf(
                "to", "toId", "receiverId", "receiverUserId",
                "receiverUsername", "receiverName", "receiverRaw", "toUserId"
            )
        )
        val display = candidates.names.firstOrNull()
            ?: candidates.ids.firstOrNull()
            ?: (msg["to"] as? String)?.trim().orEmpty()
        return candidates to display
    }

    private fun deterministicObjectId(value: String): String? {
        if (value.isBlank()) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-1")
            val hash = digest.digest(value.trim().toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }.substring(0, 24)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * generateMessageId: Genera un ID único y consistente para un mensaje
     *
     * Formato: "{from}_{timestamp}_{textHash}"
     *
     * Este ID permite identificar el mismo mensaje aunque venga de diferentes fuentes
     * (MongoDB, eventos en tiempo real, etc.)
     */
    private fun generateMessageId(from: String, timestamp: Long, text: String): String {
        val textHash = text.hashCode().toString().replace("-", "n")
        return "${from}_${timestamp}_${textHash}"
    }

    /**
     * parseTimestamp: Convierte timestamp ISO 8601 del servidor a milisegundos
     *
     * Formato esperado: "yyyy-MM-dd'T'HH:mm:ss.SSSSSS" (con microsegundos, SIN 'Z')
     * Se truncan los microsegundos a milisegundos para el parseo
     */
    private fun parseTimestamp(ts: String): Long = parseIsoTimestampFlexible(ts)

    /**
     * parseTimestampFromEvent: Parsea timestamp de eventos en tiempo real
     *
     * Maneja múltiples formatos:
     * - Con zona horaria: "2025-10-20T21:33:32.692167-03:00"
     * - Con Z: "2025-10-20T21:33:32.692Z"
     * - Sin fracción: "2025-10-20T21:33:32"
     */
    private fun parseTimestampFromEvent(ts: String): Long = parseIsoTimestampFlexible(ts)

    private fun parseIsoTimestampFlexible(ts: String): Long {
        if (ts.isBlank()) return System.currentTimeMillis()

        val normalized = normalizeIsoTimestamp(ts.trim())
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss"
        )

        patterns.forEach { pattern ->
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                if (!pattern.contains("XXX")) {
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(normalized)?.time
                if (parsed != null) {
                    return parsed
                }
            } catch (_: Exception) {
            }
        }

        return System.currentTimeMillis()
    }

    private fun normalizeIsoTimestamp(raw: String): String {
        val idx = raw.indexOf('.')
        if (idx < 0) return raw
        val base = raw.substring(0, idx)
        val fractionalAndZone = raw.substring(idx + 1)
        val zoneMatch = Regex("([+-]\\d{2}:\\d{2}|Z)$").find(fractionalAndZone)
        val zone = zoneMatch?.value ?: ""
        val fractional = if (zone.isNotEmpty()) fractionalAndZone.removeSuffix(zone) else fractionalAndZone
        val truncatedFraction = when {
            fractional.length >= 3 -> fractional.substring(0, 3)
            fractional.isEmpty() -> "000"
            else -> fractional.padEnd(3, '0')
        }
        return "$base.$truncatedFraction$zone"
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel destruido - Limpiando recursos")
        // Resetear variables del chat actual
        chatClient.currentChatSenderId = null
        chatClient.currentChatReceiverId = null
    }
}

class MessageViewModelFactory(
    private val context: Context,
    private val chatClient: InteractiveChatClient,
    private val otherUserId: String,
    private val otherUsername: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MessageViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MessageViewModel(context, chatClient, otherUserId, otherUsername) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
