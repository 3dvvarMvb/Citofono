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

data class ChatUser(
    val clientId: String,
    val userId: String,
    val username: String
)

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

class ChatViewModel : ViewModel() {
    private val _onlineUsers = MutableStateFlow<List<ChatUser>>(emptyList())
    val onlineUsers: StateFlow<List<ChatUser>> = _onlineUsers.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var client: InteractiveChatClient? = null
    private var currentUserId: String? = null
    private var currentUsername: String? = null

    fun connect(userId: String, username: String, busHost: String = "10.0.2.2", busPort: Int = 5000) {
        if (_connectionState.value is ConnectionState.Connected) return

        currentUserId = userId
        currentUsername = username

        viewModelScope.launch(Dispatchers.IO) {
            _connectionState.value = ConnectionState.Connecting

            android.util.Log.d("ChatViewModel", "=== INICIANDO CONEXIÓN ===")
            android.util.Log.d("ChatViewModel", "Host: $busHost")
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
                delay(3000) // Actualizar cada 3 segundos
            }
        }
    }

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

    fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            client?.disconnect()
            client = null
            _onlineUsers.value = emptyList()
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    fun sendMessage(receiverId: String, message: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = client?.sendMessage(receiverId, message) ?: false
            onResult(success)
        }
    }

    fun getConversation(otherUserId: String, onResult: (List<Map<String, Any>>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val messages = client?.getConversation(otherUserId) ?: emptyList()
            onResult(messages)
        }
    }

    fun getClient(): InteractiveChatClient? = client

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
