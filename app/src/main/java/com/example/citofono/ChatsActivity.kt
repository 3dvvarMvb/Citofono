package com.example.citofono

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.citofono.ui.theme.CitofonoTheme


// Data class to represent a chat contact
data class Contact1(val name: String)

class ChatsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hardcoded list of contacts for demonstration
        val contacts = listOf(
            Contact1("Laura"),
            Contact1("Carlos"),
            Contact1("Ana"),
            Contact1("Admin")
        )
        setContent {
            CitofonoTheme {
                ChatsScreen(
                    contacts = contacts,
                    onBackPressed = { finish() },
                    onContactClick = { contactName ->
                        // Navigate to MessageActivity
                        val intent = Intent(this, MessageActivity::class.java).apply {
                            putExtra("CONTACT_NAME", contactName)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    contacts: List<Contact1>,
    onBackPressed: () -> Unit,
    onContactClick: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Chats",
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
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(contacts) { contact ->
                ContactListItem(
                    contact = contact,
                    onClick = { onContactClick(contact.name) }
                )
                Divider() // Add a line separator between items
            }
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
    val previewContacts = listOf(
        Contact1("Laura"),
        Contact1("Carlos"),
        Contact1("Ana"),
        Contact1("Admin")
    )
    CitofonoTheme {
        ChatsScreen(
            contacts = previewContacts,
            onBackPressed = {},
            onContactClick = {}
        )
    }
}
