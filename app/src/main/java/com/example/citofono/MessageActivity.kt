package com.example.citofono

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

class MessageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            _root_ide_package_.com.example.citofono.ui.theme.CitofonoTheme {
                MessageScreen(
                    contactName = intent.getStringExtra("CONTACT_NAME") ?: "Contacto",
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    contactName: String,
    onBackPressed: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = contactName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                                messages = messages + ChatMessage(
                                    text = messageText,
                                    isSent = true,
                                    timestamp = System.currentTimeMillis()
                                )
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message = message)
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isSent) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (message.isSent) {
                customColor2
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                color = if (message.isSent) customColor else Color.Black
            )
        }
    }
}

@Preview(showBackground = true, name = "Pantalla de Mensajes")
@Composable
fun MessageScreenPreview() {
    _root_ide_package_.com.example.citofono.ui.theme.CitofonoTheme {
        MessageScreen(
            contactName = "Laura",
            onBackPressed = {}
        )
    }
}

@Preview(showBackground = true, name = "Burbujas de Mensajes")
@Composable
fun MessageBubblesPreview() {
    _root_ide_package_.com.example.citofono.ui.theme.CitofonoTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MessageBubble(
                message = ChatMessage(
                    text = "Laura no está, Laura se fue\n" +
                            "Laura se escapa de mi vida\n" +
                            "Y tú, que sí estás, preguntas por qué\n" +
                            "La amo a pesar de las heridas",
                    isSent = true,
                    timestamp = System.currentTimeMillis()
                )
            )
            MessageBubble(
                message = ChatMessage(
                    text = "Lo ocupa todo su recuerdo\n" +
                            "No consigo olvidar\n" +
                            "El peso de su cuerpo",
                    isSent = false,
                    timestamp = System.currentTimeMillis()
                )
            )

            MessageBubble(
                message = ChatMessage(
                    text = "Lo ocupa todo su recuerdo",
                    isSent = false,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }
}

data class ChatMessage(
    val text: String,
    val isSent: Boolean,
    val timestamp: Long
)
