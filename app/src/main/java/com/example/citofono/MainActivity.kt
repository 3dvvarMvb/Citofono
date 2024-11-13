package com.example.citofono

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.citofono.ui.theme.CitofonoTheme
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CitofonoTheme {
        Greeting("Android")
    }
}

data class Contact(
    val id: Int,
    val name: String,
    val phoneNumber: String
)

@Composable
fun ContactList(contacts: List<Contact>, onAddContact: () -> Unit) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddContact) {
                Text("+")
            }
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            items(contacts) { contact ->
                ContactItem(contact)
            }
        }
    }
}

@Composable
fun ContactItem(contact: Contact) {
    Text(text = "${contact.name}: ${contact.phoneNumber}")
}

@Composable
fun AddContactForm(onAddContact: (Contact) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }

    Column {
        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") }
        )
        TextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Phone Number") }
        )
        Button(onClick = {
            val newContact = Contact(id = 0, name = name, phoneNumber = phoneNumber)
            onAddContact(newContact)
        }) {
            Text("Add Contact")
        }
    }
}

fun loadContactsFromCsv(context: Context): List<Contact> {
    val contacts = mutableListOf<Contact>()
    val inputStream = context.resources.openRawResource(R.raw.contactos)
    val reader = BufferedReader(InputStreamReader(inputStream))

    reader.useLines { lines ->
        lines.forEach { line ->
            val tokens = line.split(";")
            if (tokens.size >= 3) {
                val contact = Contact(
                    id = contacts.size,
                    name = tokens[0],
                    phoneNumber = tokens[1] // Aquí solo usa el primer número de celular
                )
                contacts.add(contact)
            }
        }
    }
    return contacts
}

class MainActivity : ComponentActivity() {
    private val contacts = mutableStateListOf<Contact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contacts.addAll(loadContactsFromCsv(this)) // Cargar contactos desde el CSV

        setContent {
            CitofonoTheme {
                var showAddContactForm by remember { mutableStateOf(false) }

                if (showAddContactForm) {
                    AddContactForm { newContact ->
                        contacts.add(newContact)
                        showAddContactForm = false
                    }
                } else {
                    ContactList(contacts) {
                        showAddContactForm = true
                    }
                }
            }
        }
    }
}