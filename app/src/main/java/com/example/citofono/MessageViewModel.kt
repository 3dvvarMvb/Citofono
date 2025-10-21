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
import java.text.SimpleDateFormat
import java.util.*

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
    @Suppress("unused") private val otherUsername: String
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

                        if (serverMessages.isEmpty()) {
                            _messages.value = emptyList()
                            _conversationState.value = ConversationState.Loaded(emptyList())
                            Log.d(TAG, "✅ Conversación vacía (sin mensajes previos)")
                            return@withContext
                        }

                        // Convertir y ordenar mensajes por timestamp
                        val convertedMessages = serverMessages.mapNotNull { msg ->
                            try {
                                val from = msg["from"] as? String ?: return@mapNotNull null
                                val text = msg["text"] as? String ?: return@mapNotNull null
                                val timestampStr = msg["ts"] as? String ?: ""
                                val timestamp = parseTimestamp(timestampStr)

                                // Generar ID único consistente
                                val messageId = generateMessageId(from, timestamp, text)

                                // Marcar como procesado
                                processedMessageIds.add(messageId)

                                ChatMessage(
                                    id = messageId,
                                    text = text,
                                    isSent = from == chatClient.userId,
                                    timestamp = timestamp,
                                    deliveryStatus = "delivered",
                                    senderUserId = from,
                                    receiverUserId = if (from == chatClient.userId) otherUserId else chatClient.userId
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
                    val success = chatClient.sendMessage(otherUserId, text)

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
     * startRealtimeMessageListener: Escucha eventos en tiempo real
     *
     * FLUJO:
     * - Lee la lista de eventos del chatClient
     * - SOLO procesa eventos NUEVOS desde eventStartIndex
     * - Procesa:
     *   - new_message: Nuevo mensaje recibido del otro usuario
     *   - user_typing: Estado de escritura del otro usuario
     *   - message_read: Mensaje fue leído
     * - Evita duplicados usando processedMessageIds
     * - Mantiene orden cronológico de mensajes
     */
    private fun startRealtimeMessageListener() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                var lastProcessedIndex = eventStartIndex

                while (true) {
                    try {
                        kotlinx.coroutines.delay(300)

                        val currentEventsSize = chatClient.getEventsCount()

                        // Solo procesar eventos nuevos
                        if (currentEventsSize > lastProcessedIndex) {
                            val newEvents = chatClient.events.drop(lastProcessedIndex)

                            newEvents.forEach { eventMap ->
                                val event = eventMap["event"] as? String ?: ""
                                @Suppress("UNCHECKED_CAST")
                                val data = eventMap["data"] as? Map<String, Any> ?: emptyMap()

                                when (event) {
                                    "new_message" -> {
                                        val from = data["from"] as? String ?: ""
                                        val text = data["text"] as? String ?: ""
                                        val timestampStr = data["timestamp"] as? String ?: ""
                                        val timestamp = parseTimestampFromEvent(timestampStr)

                                        // SOLO procesar mensajes del otro usuario en ESTE chat
                                        if (from == otherUserId) {
                                            val messageId = generateMessageId(from, timestamp, text)

                                            // Verificar duplicados
                                            if (processedMessageIds.contains(messageId)) {
                                                Log.d(TAG, "⚠️ Mensaje duplicado ignorado (evento)")
                                                return@forEach
                                            }

                                            val newMessage = ChatMessage(
                                                id = messageId,
                                                text = text,
                                                isSent = false,
                                                timestamp = timestamp,
                                                deliveryStatus = "delivered",
                                                senderUserId = from,
                                                receiverUserId = chatClient.userId
                                            )

                                            processedMessageIds.add(messageId)

                                            val currentMessages = _messages.value.toMutableList()
                                            currentMessages.add(newMessage)
                                            // Mantener orden cronológico
                                            _messages.value = currentMessages.sortedBy { it.timestamp }

                                            Log.d(TAG, "💬 Nuevo mensaje en tiempo real de $from")
                                        }
                                    }

                                    "user_typing" -> {
                                        val userTyping = data["user_id"] as? String ?: ""
                                        val isTyping = data["is_typing"] as? Boolean ?: false

                                        if (userTyping == otherUserId) {
                                            _isOtherTyping.value = isTyping
                                        }
                                    }

                                    "message_read" -> {
                                        val updatedMessages = _messages.value.map {
                                            if (it.isSent && it.deliveryStatus != "read") {
                                                it.copy(deliveryStatus = "read")
                                            } else {
                                                it
                                            }
                                        }
                                        _messages.value = updatedMessages
                                    }
                                }
                            }

                            lastProcessedIndex = currentEventsSize
                        }

                    } catch (e: Exception) {

                    }
                }
            }
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
    private fun parseTimestamp(ts: String): Long {
        return try {
            if (ts.isEmpty()) {
                System.currentTimeMillis()
            } else {
                // MongoDB devuelve timestamps con MICROSEGUNDOS (6 dígitos) y SIN 'Z'
                // Ejemplo: "2025-10-21T00:33:37.567000"
                // Necesitamos truncar a milisegundos (3 dígitos): "2025-10-21T00:33:37.567"
                val truncatedTs = if (ts.contains(".")) {
                    val parts = ts.split(".")
                    val fractionalPart = parts[1]
                    if (fractionalPart.length > 3) {
                        // Truncar microsegundos a milisegundos
                        "${parts[0]}.${fractionalPart.substring(0, 3)}"
                    } else {
                        ts
                    }
                } else {
                    ts
                }

                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val parsed = sdf.parse(truncatedTs)?.time ?: System.currentTimeMillis()

                Log.d(TAG, "✓ Parsed timestamp: $ts -> $parsed")
                parsed
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parseando timestamp: $ts", e)
            System.currentTimeMillis()
        }
    }

    /**
     * parseTimestampFromEvent: Parsea timestamp de eventos en tiempo real
     *
     * Maneja múltiples formatos:
     * - Con zona horaria: "2025-10-20T21:33:32.692167-03:00"
     * - Con Z: "2025-10-20T21:33:32.692Z"
     * - Sin fracción: "2025-10-20T21:33:32"
     */
    private fun parseTimestampFromEvent(ts: String): Long {
        return try {
            if (ts.isEmpty()) return System.currentTimeMillis()

            // Truncar microsegundos si existen
            var processedTs = ts
            if (ts.contains(".")) {
                val parts = ts.split(".")
                val fractionalAndZone = parts[1]

                // Extraer parte fraccionaria y zona horaria
                val zoneMatch = Regex("([+-]\\d{2}:\\d{2}|Z)").find(fractionalAndZone)
                val zone = zoneMatch?.value ?: ""
                val fractional = fractionalAndZone.replace(zone, "")

                // Truncar a 3 dígitos
                val truncatedFractional = if (fractional.length > 3) {
                    fractional.substring(0, 3)
                } else {
                    fractional
                }

                processedTs = "${parts[0]}.${truncatedFractional}${zone}"
            }

            // Intentar parsear con zona horaria
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
                val parsed = sdf.parse(processedTs)?.time
                if (parsed != null) {
                    Log.d(TAG, "✓ Parsed event timestamp: $ts -> $parsed")
                    return parsed
                }
            } catch (e: Exception) {
                // Continuar con otros formatos
            }

            // Intentar con 'Z'
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val parsed = sdf.parse(processedTs)?.time
                if (parsed != null) {
                    Log.d(TAG, "✓ Parsed event timestamp: $ts -> $parsed")
                    return parsed
                }
            } catch (e: Exception) {
                // Continuar
            }

            // Intentar sin fracción
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val parsed = sdf.parse(processedTs)?.time
                if (parsed != null) {
                    Log.d(TAG, "✓ Parsed event timestamp: $ts -> $parsed")
                    return parsed
                }
            } catch (e: Exception) {
                // Continuar
            }

            Log.e(TAG, "❌ No se pudo parsear timestamp del evento: $ts")
            System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parseando timestamp del evento: $ts", e)
            System.currentTimeMillis()
        }
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
