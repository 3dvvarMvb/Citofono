package com.example.citofono

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.bson.types.ObjectId

/**
 * ============================================================================
 * ChatViewModel - ViewModel para gestionar la conexión al BUS y usuarios online
 * ============================================================================
 *
 * Este ViewModel es responsable de:
 * - Gestionar la conexión del cliente al BUS de mensajería
 * - Mantener la lista de usuarios online actualizada
 * - Gestionar el estado de la conexión (Connecting, Connected, Error, Disconnected)
 * - Mantener el cliente de chat activo para compartirlo con MessageActivity
 * - Ejecutar heartbeats periódicos y broadcast de presencia
 *
 * Usa Kotlin Coroutines y StateFlow para actualizar la UI reactivamente
 */

/**
 * ChatUser: Representa un usuario online en la interfaz
 * @property clientId ID de sesión del cliente en el BUS
 * @property userId ID del usuario en la base de datos
 * @property username Nombre de usuario para mostrar
 */
data class ChatUser(
    val clientId: String,
    val userId: String,
    val username: String
)

/**
 * ConnectionState: Estados posibles de la conexión al BUS
 * - Disconnected: No hay conexión activa
 * - Connecting: Intentando conectar al BUS
 * - Connected: Conexión establecida y funcionando
 * - Error: Hubo un error en la conexión (incluye mensaje de error)
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

class ChatViewModel : ViewModel() {
    // Lista de usuarios online - Observable por la UI
    private val _onlineUsers = MutableStateFlow<List<ChatUser>>(emptyList())
    val onlineUsers: StateFlow<List<ChatUser>> = _onlineUsers.asStateFlow()

    // Estado de la conexión - Observable por la UI
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Cliente de chat (compartido con MessageActivity a través de ChatClientManager)
    private var client: InteractiveChatClient? = null

    // Exponer el cliente para que MessageActivity pueda usarlo
    val chatClient: InteractiveChatClient?
        get() = client

    // Datos del usuario actual
    private var currentUserId: String? = null
    private var currentUsername: String? = null

    /**
     * connect: Conecta el cliente al BUS de mensajería
     * @param userId ID único del usuario en MongoDB
     * @param username Nombre de usuario para mostrar
     * @param busHost Dirección del BUS (default: 127.0.0.1 por adb reverse)
     * @param busPort Puerto del BUS (default: 5000)
     *
     * Este método:
     * 1. Verifica que no haya una conexión activa
     * 2. Crea el InteractiveChatClient con los parámetros dados
     * 3. Establece la conexión TCP al BUS
     * 4. Registra el cliente en el ChatClientManager (Singleton)
     * 5. Inicia los threads de actualización de usuarios, heartbeat y broadcast
     *
     * Se ejecuta en Dispatchers.IO para no bloquear el UI thread
     */
    fun connect(userId: String, username: String, busHost: String = "127.0.0.1", busPort: Int = 5000) {
        if (_connectionState.value is ConnectionState.Connected) return

        currentUserId = userId
        currentUsername = username

        viewModelScope.launch(Dispatchers.IO) {
            _connectionState.value = ConnectionState.Connecting

            android.util.Log.d("ChatViewModel", "=== INICIANDO CONEXIÓN ===")
            android.util.Log.d("ChatViewModel", "Host: $busHost")
            android.util.Log.d("ChatViewModel", "IP del dispositivo Android: ${java.net.InetAddress.getLocalHost()}")
            android.util.Log.d("ChatViewModel", "Intentando conectar a: $busHost:$busPort")
            android.util.Log.d("ChatViewModel", "Port: $busPort")
            android.util.Log.d("ChatViewModel", "UserId: $userId")
            android.util.Log.d("ChatViewModel", "Username: $username")

            try {
                android.util.Log.d("ChatViewModel", "Creando InteractiveChatClient...")
                client = InteractiveChatClient(userId, username, busHost, busPort)

                android.util.Log.d("ChatViewModel", "Cliente creado, llamando a connect()...")
                val connected = client?.connect() ?: false

                android.util.Log.d("ChatViewModel", "Resultado de conexión: $connected")

                if (connected) {
                    _connectionState.value = ConnectionState.Connected
                    android.util.Log.d("ChatViewModel", "✅ Conexión exitosa")

                    // Registrar el cliente en el Singleton para compartirlo con otras actividades
                    client?.let { ChatClientManager.setChatClient(it) }

                    // Iniciar actualización periódica de usuarios
                    startUserListUpdates()

                    // Heartbeat periódico
                    startHeartbeat()

                    // Broadcast de presencia periódico
                    startPresenceBroadcast()
                } else {
                    android.util.Log.e("ChatViewModel", "❌ Conexión falló")
                    _connectionState.value = ConnectionState.Error("No se pudo conectar al servidor")
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "❌ Excepción durante conexión", e)
                android.util.Log.e("ChatViewModel", "Stack trace: ${e.stackTraceToString()}")
                _connectionState.value = ConnectionState.Error("Error: ${e.message}")
            }
        }
    }

    /**
     * startUserListUpdates: Inicia un bucle que actualiza la lista de usuarios online
     *
     * Cada 10 segundos consulta la lista de usuarios conectados del cliente
     * y actualiza el StateFlow _onlineUsers para que la UI se actualice reactivamente
     *
     * Se ejecuta en Dispatchers.IO en una coroutine del ViewModel
     */
    private fun startUserListUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            while (client?.running == true) {
                try {
                    val users = client?.getOnlineUsersList() ?: emptyList()
                    _onlineUsers.value = users.map { (clientId, userInfo) ->
                        ChatUser(
                            clientId = clientId,
                            userId = userInfo.user_id,
                            username = userInfo.username
                        )
                    }
                } catch (e: Exception) {
                    // Ignorar errores temporales
                }
                delay(10000) // Actualizar cada 10 segundos
            }
        }
    }

    /**
     * startHeartbeat: Envía pings periódicos al servicio
     *
     * Cada 30 segundos envía un heartbeat para mantener la conexión viva
     * y notificar al servicio que el cliente sigue activo
     *
     * Se ejecuta en Dispatchers.IO en una coroutine del ViewModel
     */
    private fun startHeartbeat() {
        viewModelScope.launch(Dispatchers.IO) {
            while (client?.running == true) {
                delay(30000) // Cada 30 segundos
                try {
                    client?.heartbeat()
                } catch (e: Exception) {
                    // Ignorar errores
                }
            }
        }
    }

    /**
     * startPresenceBroadcast: Envía broadcasts de presencia periódicos
     *
     * Cada 10 segundos hace broadcast de la presencia del usuario
     * para que los nuevos clientes conectados lo puedan ver
     *
     * Se ejecuta en Dispatchers.IO en una coroutine del ViewModel
     */
    private fun startPresenceBroadcast() {
        viewModelScope.launch(Dispatchers.IO) {
            while (client?.running == true) {
                delay(10000) // Cada 10 segundos
                try {
                    client?.broadcastMyPresence()
                } catch (e: Exception) {
                    // Ignorar errores
                }
            }
        }
    }

    /**
     * disconnect: Desconecta el cliente del BUS
     *
     * Limpia el cliente del Singleton, lo desconecta del BUS,
     * limpia la lista de usuarios y marca el estado como Disconnected
     *
     * Se ejecuta en Dispatchers.IO para no bloquear el UI thread
     */
    fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.d("ChatViewModel", "🔌 Desconectando cliente...")
            ChatClientManager.clearChatClient()
            client?.disconnect()
            client = null
            _onlineUsers.value = emptyList()
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    /**
     * sendMessage: Envía un mensaje a otro usuario (DEPRECATED - usar MessageViewModel)
     * @param receiverId ID del usuario destinatario
     * @param message Texto del mensaje
     * @param onResult Callback con el resultado (true si se envió correctamente)
     *
     * Esta función está aquí por retrocompatibilidad, pero se recomienda
     * usar MessageViewModel.sendMessage() que maneja mejor la persistencia local
     */
    fun sendMessage(receiverId: String, message: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = client?.sendMessage(receiverId, message) ?: false
            onResult(success)
        }
    }

    /**
     * getConversation: Obtiene el historial de conversación (DEPRECATED - usar MessageViewModel)
     * @param otherUserId ID del otro usuario
     * @param onResult Callback con la lista de mensajes
     *
     * Esta función está aquí por retrocompatibilidad, pero se recomienda
     * usar MessageViewModel que maneja caché local y evita duplicados
     */
    fun getConversation(otherUserId: String, onResult: (List<Map<String, Any>>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val messages = client?.getConversation(otherUserId) ?: emptyList()
            onResult(messages)
        }
    }

    /**
     * getClient: Obtiene el cliente de chat (DEPRECATED - usar ChatClientManager)
     * @return El cliente de chat o null si no está conectado
     */
    fun getClient(): InteractiveChatClient? = client

    /**
     * onCleared: Callback llamado cuando el ViewModel es destruido
     *
     * Automáticamente desconecta el cliente para liberar recursos
     */
    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
