package com.example.citofono

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.citofono.ui.theme.CitofonoTheme
import java.io.BufferedReader
import java.io.InputStreamReader

data class Contact(
    val id: Int,
    val name: String,
    val phoneNumber: List<String>,
    val department: String
)

@Composable
fun SearchScreen(
    contacts: List<Contact>,
    onContactClick: (Contact) -> Unit,
    onCallClick: (String) -> Unit,
    onWhatsAppClick: (String) -> Unit,
    onSmsClick: (String) -> Unit,
    onDepartmentClick: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredDepartments = contacts
        .filter { it.department.contains(searchQuery, ignoreCase = true) }
        .map { it.department }
        .distinct()
        .sorted()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Buscar Departamento") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (searchQuery.isNotBlank()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (filteredDepartments.isNotEmpty()) {
                    items(filteredDepartments) { department ->
                        Text(
                            text = "Departamento: $department",
                            style = MaterialTheme.typography.body1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .clickable { onDepartmentClick(department) }
                        )
                    }
                } else {
                    item {
                        Text(
                            text = "No se encontraron resultados",
                            style = MaterialTheme.typography.body1,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DepartmentContactsScreen(
    department: String,
    contacts: List<Contact>,
    onContactClick: (Contact) -> Unit,
    onCallClick: (String) -> Unit,
    onWhatsAppClick: (String) -> Unit,
    onSmsClick: (String) -> Unit
) {
    val departmentContacts = contacts.filter { it.department == department }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Contactos del Departamento: $department",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            items(departmentContacts) { contact ->
                ContactItem(
                    contact = contact,
                    onCallClick = onCallClick,
                    onWhatsAppClick = onWhatsAppClick,
                    onSmsClick = onSmsClick
                )
            }
        }
    }
}

@Composable
fun ContactItem(
    contact: Contact,
    onCallClick: (String) -> Unit,
    onWhatsAppClick: (String) -> Unit,
    onSmsClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(text = "Departamento: ${contact.name}", style = MaterialTheme.typography.body1)

        contact.phoneNumber.forEachIndexed { index, phoneNumber ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Número ${index + 1}",
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.weight(1f)
                )

                Row {
                    IconButton(onClick = { onCallClick(phoneNumber) }) {
                        Icon(imageVector = Icons.Default.Call, contentDescription = "Llamar")
                    }
                    IconButton(onClick = { onWhatsAppClick(phoneNumber) }) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = "WhatsApp")
                    }
                    IconButton(onClick = { onSmsClick(phoneNumber) }) {
                        Icon(imageVector = Icons.Default.Email, contentDescription = "SMS")
                    }
                }
            }
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
            if (tokens.size >= 4) {
                val phoneNumber = listOf(tokens[1], tokens[2])
                val contact = Contact(
                    id = contacts.size,
                    name = tokens[0],
                    phoneNumber = phoneNumber.filter { it.isNotBlank() },
                    department = tokens[0]
                )
                contacts.add(contact)
            }
        }
    }
    return contacts
}

class MainActivity : ComponentActivity() {
    private val contacts = mutableStateListOf<Contact>()
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private var pendingPhoneNumber: String? = null
    private var selectedDepartment by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                pendingPhoneNumber?.let { makeCall(it) }
                pendingPhoneNumber = null
            } else {
                Toast.makeText(this, "Permiso denegado para realizar llamadas", Toast.LENGTH_SHORT).show()
            }
        }

        contacts.addAll(loadContactsFromCsv(this))

        setContent {
            CitofonoTheme {
                if (selectedDepartment == null) {
                    SearchScreen(
                        contacts = contacts,
                        onContactClick = { },
                        onCallClick = { phoneNumber -> makeCall(phoneNumber) },
                        onWhatsAppClick = { phoneNumber -> openWhatsApp(phoneNumber) },
                        onSmsClick = { phoneNumber -> sendSms(phoneNumber) },
                        onDepartmentClick = { department -> selectedDepartment = department }
                    )
                } else {
                    DepartmentContactsScreen(
                        department = selectedDepartment!!,
                        contacts = contacts,
                        onContactClick = { },
                        onCallClick = { phoneNumber -> makeCall(phoneNumber) },
                        onWhatsAppClick = { phoneNumber -> openWhatsApp(phoneNumber) },
                        onSmsClick = { phoneNumber -> sendSms(phoneNumber) }
                    )
                }
            }
        }
    }

    private fun makeCall(phoneNumber: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:+56$phoneNumber")
            }
            startActivity(intent)
        } else {
            pendingPhoneNumber = phoneNumber
            requestPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun openWhatsApp(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://wa.me/56$phoneNumber")
        }
        startActivity(intent)
    }

    private fun sendSms(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("sms:+56$phoneNumber")
        }
        startActivity(intent)
    }
}