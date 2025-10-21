package com.example.citofono

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MessageActivity : ComponentActivity() {
    private val TAG = "MessageActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "=== MessageActivity onCreate INICIADO ===")

        val contactName = intent.getStringExtra("CONTACT_NAME") ?: "Contacto"
        val otherUserId = intent.getStringExtra("USER_ID") ?: ""
        val otherClientId = intent.getStringExtra("CLIENT_ID") ?: ""

        Log.d(TAG, "Datos recibidos del Intent:")
        Log.d(TAG, "  - contactName: $contactName")
        Log.d(TAG, "  - otherUserId: $otherUserId")
        Log.d(TAG, "  - otherClientId: $otherClientId")

        // Obtener el cliente de chat desde el Singleton
        Log.d(TAG, "Obteniendo chatClient desde ChatClientManager...")
        val chatClient = ChatClientManager.getChatClient()

        if (chatClient == null) {
            Log.e(TAG, "❌ ERROR: chatClient es NULL - No se puede continuar")
            Log.e(TAG, "El usuario debe conectarse primero desde ChatsActivity")
            finish()
            return
        }

        Log.d(TAG, "✅ chatClient obtenido correctamente")
        Log.d(TAG, "  - userId: ${chatClient.userId}")
        Log.d(TAG, "  - username: ${chatClient.username}")
        Log.d(TAG, "  - running: ${chatClient.running}")

        if (otherUserId.isEmpty()) {
            Log.e(TAG, "❌ ERROR: otherUserId está vacío")
            finish()
            return
        }

        Log.d(TAG, "Creando MessageViewModel...")
        // Crear el ViewModel de mensajes
        val messageViewModel: MessageViewModel by viewModels {
            MessageViewModelFactory(
                context = applicationContext,
                chatClient = chatClient,
                otherUserId = otherUserId,
                otherUsername = contactName
            )
        }

        Log.d(TAG, "✅ MessageViewModel creado correctamente")

        Log.d(TAG, "Configurando UI con Compose...")
        setContent {
            _root_ide_package_.com.example.citofono.ui.theme.CitofonoTheme {
                val messages by messageViewModel.messages.collectAsState()
                val conversationState by messageViewModel.conversationState.collectAsState()
                val isOtherTyping by messageViewModel.isOtherTyping.collectAsState()

                MessageScreen(
                    contactName = contactName,
                    messages = messages,
                    conversationState = conversationState,
                    isOtherTyping = isOtherTyping,
                    onSendMessage = { messageViewModel.sendMessage(it) },
                    onBackPressed = { finish() }
                )
            }
        }

        Log.d(TAG, "=== MessageActivity onCreate COMPLETADO ===")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume() llamado")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause() llamado")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy() llamado")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    contactName: String,
    messages: List<ChatMessage>,
    conversationState: ConversationState,
    isOtherTyping: Boolean,
    onSendMessage: (String) -> Unit,
    onBackPressed: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll cuando llegan nuevos mensajes
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = contactName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isOtherTyping) {
                            Text(
                                text = "✍️ escribiendo...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
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
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        placeholder = { Text("Escribe un mensaje...") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4
                    )

                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                onSendMessage(messageText)
                                messageText = ""
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = customColor2
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Enviar",
                            tint = customColor
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (conversationState) {
                is ConversationState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is ConversationState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "❌ Error cargando conversación",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = conversationState.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is ConversationState.Loaded -> {
                    if (messages.isEmpty()) {
                        // Estado vacío
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "💬",
                                fontSize = 48.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No hay mensajes aún",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Envía un mensaje para comenzar la conversación",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Lista de mensajes
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                MessageBubble(message = message)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isSent) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isSent) 16.dp else 4.dp,
                bottomEnd = if (message.isSent) 4.dp else 16.dp
            ),
            color = if (message.isSent) {
                customColor2
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.text,
                    color = if (message.isSent) customColor else Color.Black
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimestamp(message.timestamp),
                        fontSize = 10.sp,
                        color = if (message.isSent) {
                            customColor.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    // Indicador de estado para mensajes enviados
                    if (message.isSent) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (message.deliveryStatus) {
                                "sending" -> "🕐"
                                "sent" -> "✓"
                                "delivered" -> "✓✓"
                                "read" -> "✓✓"
                                "error" -> "❌"
                                else -> ""
                            },
                            fontSize = 10.sp,
                            color = if (message.deliveryStatus == "read") {
                                Color(0xFF34B7F1)
                            } else {
                                customColor.copy(alpha = 0.7f)
                            }
                        )
                    }
                }
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val now = Calendar.getInstance()
    val messageTime = Calendar.getInstance().apply {
        timeInMillis = timestamp
    }

    return when {
        // Hoy
        now.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR) &&
        now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        // Ayer
        now.get(Calendar.DAY_OF_YEAR) - messageTime.get(Calendar.DAY_OF_YEAR) == 1 &&
        now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) -> {
            "Ayer ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))}"
        }
        // Misma semana
        now.get(Calendar.WEEK_OF_YEAR) == messageTime.get(Calendar.WEEK_OF_YEAR) &&
        now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) -> {
            SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        // Más antiguo
        else -> {
            SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }
}

@Preview(showBackground = true, name = "Pantalla de Mensajes")
@Composable
fun MessageScreenPreview() {
    _root_ide_package_.com.example.citofono.ui.theme.CitofonoTheme {
        MessageScreen(
            contactName = "Laura",
            messages = listOf(
                ChatMessage(text = "Hola! ¿Cómo estás?", isSent = false, timestamp = System.currentTimeMillis() - 3600000),
                ChatMessage(text = "¡Hola Laura! Todo bien, ¿y tú?", isSent = true, timestamp = System.currentTimeMillis() - 3500000, deliveryStatus = "read"),
                ChatMessage(text = "Muy bien gracias 😊", isSent = false, timestamp = System.currentTimeMillis() - 3400000),
                ChatMessage(text = "Me alegro mucho", isSent = true, timestamp = System.currentTimeMillis(), deliveryStatus = "sent")
            ),
            conversationState = ConversationState.Loaded(emptyList()),
            isOtherTyping = false,
            onSendMessage = {},
            onBackPressed = {}
        )
    }
}
