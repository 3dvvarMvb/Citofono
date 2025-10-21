package com.example.citofono

import android.util.Log

/**
 * Singleton para gestionar la instancia global del cliente de chat.
 * Permite compartir el mismo cliente entre ChatsActivity y MessageActivity.
 */
object ChatClientManager {
    private const val TAG = "ChatClientManager"

    @Volatile
    private var chatClient: InteractiveChatClient? = null

    /**
     * Establece la instancia del cliente de chat
     */
    fun setChatClient(client: InteractiveChatClient) {
        Log.d(TAG, "✅ ChatClient establecido - userId: ${client.userId}, username: ${client.username}")
        chatClient = client
    }

    /**
     * Obtiene la instancia actual del cliente de chat
     */
    fun getChatClient(): InteractiveChatClient? {
        if (chatClient == null) {
            Log.w(TAG, "⚠️ getChatClient() llamado pero chatClient es NULL")
        } else {
            Log.d(TAG, "✅ getChatClient() retornando cliente - running: ${chatClient?.running}")
        }
        return chatClient
    }

    /**
     * Limpia la instancia del cliente (cuando el usuario se desconecta)
     */
    fun clearChatClient() {
        Log.d(TAG, "🧹 Limpiando chatClient")
        chatClient?.disconnect()
        chatClient = null
    }

    /**
     * Verifica si hay un cliente conectado
     */
    fun isConnected(): Boolean {
        val connected = chatClient?.running ?: false
        Log.d(TAG, "isConnected() = $connected")
        return connected
    }
}

