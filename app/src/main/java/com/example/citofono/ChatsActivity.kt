package com.example.citofono

import android.content.Intent
import android.os.Bundle
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
    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Obtener datos del usuario (por ahora hardcoded, luego desde login)
        val userId = ObjectId().toString() // Generar o recuperar del login
        val username = "Usuario_Android" // Recuperar del login

        // Configurar reenvío de puertos ADB
        //setupPortForwarding()

        // Conectar al servicio de chat
        chatViewModel.connect(userId, username, busHost = "10.0.2.2", busPort = 5000)

        setContent {
            CitofonoTheme {
                val onlineUsers by chatViewModel.onlineUsers.collectAsState()
                val connectionState by chatViewModel.connectionState.collectAsState()

                ChatsScreen(
                    onlineUsers = onlineUsers,
                    connectionState = connectionState,
                    onBackPressed = {
                        chatViewModel.disconnect()
                        finish()
                    },
                    onContactClick = { user ->
                        // Navigate to MessageActivity
                        val intent = Intent(this, MessageActivity::class.java).apply {
                            putExtra("CONTACT_NAME", user.username)
                            putExtra("USER_ID", user.userId)
                            putExtra("CLIENT_ID", user.clientId)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
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
