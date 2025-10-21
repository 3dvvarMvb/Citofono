package com.example.citofono

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.citofono.ui.theme.CitofonoTheme
import org.bson.types.ObjectId

// Data class to represent a chat contact
data class Contact1(val name: String)

class ChatsActivity : ComponentActivity() {
    private val TAG = "ChatsActivity"
    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "=== ChatsActivity onCreate INICIADO ===")

        // Obtener datos del usuario (por ahora hardcoded, luego desde login)
        val userId = ObjectId().toString() // Generar o recuperar del login
        val username = "Usuario_Android" // Recuperar del login

        Log.d(TAG, "userId generado: $userId")
        Log.d(TAG, "username: $username")

        // Conectar al servicio de chat
        Log.d(TAG, "Llamando a chatViewModel.connect()...")
        // Usar 127.0.0.1 porque configuramos adb reverse desde el dispositivo al host
        chatViewModel.connect(userId, username, busHost = "127.0.0.1", busPort = 5000)

        setContent {
            CitofonoTheme {
                val onlineUsers by chatViewModel.onlineUsers.collectAsState()
                val connectionState by chatViewModel.connectionState.collectAsState()

                ChatsScreen(
                    onlineUsers = onlineUsers,
                    connectionState = connectionState,
                    onBackPressed = {
                        Log.d(TAG, "Usuario presionó botón atrás")
                        chatViewModel.disconnect()
                        finish()
                    },
                    onContactClick = { user ->
                        Log.d(TAG, "=== Usuario hizo click en contacto ===")
                        Log.d(TAG, "  - username: ${user.username}")
                        Log.d(TAG, "  - userId: ${user.userId}")
                        Log.d(TAG, "  - clientId: ${user.clientId}")

                        // Navigate to MessageActivity
                        val intent = Intent(this, MessageActivity::class.java).apply {
                            putExtra("CONTACT_NAME", user.username)
                            putExtra("USER_ID", user.userId)
                            putExtra("CLIENT_ID", user.clientId)
                        }

                        Log.d(TAG, "Intent creado, iniciando MessageActivity...")
                        startActivity(intent)
                        Log.d(TAG, "startActivity() ejecutado")
                    }
                )
            }
        }

        Log.d(TAG, "=== ChatsActivity onCreate COMPLETADO ===")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy() - desconectando...")
        chatViewModel.disconnect()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onlineUsers: List<ChatUser>,
    connectionState: ConnectionState,
    onBackPressed: () -> Unit,
    onContactClick: (ChatUser) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Chats",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        // Mostrar estado de conexión
                        Text(
                            text = when (connectionState) {
                                is ConnectionState.Connected -> "🟢 Conectado"
                                is ConnectionState.Connecting -> "🟡 Conectando..."
                                is ConnectionState.Disconnected -> "⚪ Desconectado"
                                is ConnectionState.Error -> "🔴 Error"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = customColor2,
                    titleContentColor = customColor,
                    navigationIconContentColor = customColor
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                connectionState is ConnectionState.Connecting -> {
                    // Mostrar indicador de carga
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Conectando al servidor...")
                    }
                }
                connectionState is ConnectionState.Error -> {
                    // Mostrar error
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "❌ ${(connectionState as ConnectionState.Error).message}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                onlineUsers.isEmpty() -> {
                    // No hay usuarios conectados
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "⏳ Esperando que otros usuarios se conecten...",
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Actualizando automáticamente",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    // Mostrar lista de usuarios
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(onlineUsers) { user ->
                            OnlineUserListItem(
                                user = user,
                                onClick = { onContactClick(user) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnlineUserListItem(
    user: ChatUser,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indicador de estado online
        Text(
            text = "🟢",
            fontSize = 24.sp,
            modifier = Modifier.padding(end = 12.dp)
        )

        Column {
            Text(
                text = user.username,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ContactListItem(
    contact: Contact1,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = contact.name,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(showBackground = true, name = "Pantalla de Chats")
@Composable
fun ChatsScreenPreview() {
    val previewUsers = listOf(
        ChatUser("client_1", "507f1f77bcf86cd799439011", "Laura"),
        ChatUser("client_2", "507f1f77bcf86cd799439012", "Carlos"),
        ChatUser("client_3", "507f1f77bcf86cd799439013", "Ana")
    )
    CitofonoTheme {
        ChatsScreen(
            onlineUsers = previewUsers,
            connectionState = ConnectionState.Connected,
            onBackPressed = {},
            onContactClick = {}
        )
    }
}

private fun setupPortForwarding() {
    try {
        Runtime.getRuntime().exec("adb reverse tcp:5000 tcp:5000")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
