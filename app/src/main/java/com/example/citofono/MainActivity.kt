package com.example.citofono

import android.content.Context
import android.Manifest
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.citofono.ui.theme.CitofonoTheme
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.random.Random

data class Contact(
    val id: Int,
    val name: String,
    val phoneNumber: List<String>,
    val department: String
)

@Composable
fun SearchScreen(
    contacts: List<Contact>,
    onCallClick: (String, String) -> Unit
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
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                if (filteredDepartments.isNotEmpty()) {
                    items(filteredDepartments) { department ->
                        Text(
                            text = "Departamento: $department",
                            style = MaterialTheme.typography.body1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .clickable {
                                    val departmentContacts = contacts.filter { it.department == department }
                                    if (departmentContacts.isNotEmpty()) {
                                        val firstContact = departmentContacts.first()
                                        if (firstContact.phoneNumber.isNotEmpty()) {
                                            onCallClick(firstContact.phoneNumber[0], department)
                                        }
                                    }
                                }
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
                SearchScreen(
                    contacts = contacts,
                    onCallClick = { phoneNumber, department -> makeCall(phoneNumber) }
                )
            }
        }
    }

    private fun makeCall(phoneNumber: String) {
        val randomCallerId = generateRandomCallerId()
        Toast.makeText(this, "Llamando como: $randomCallerId", Toast.LENGTH_SHORT).show()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:" + validatePhoneNumber(phoneNumber))
            }
            startActivity(intent)
        } else {
            pendingPhoneNumber = phoneNumber
            requestPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun validatePhoneNumber(phoneNumber: String): String {
        return if (phoneNumber.startsWith("+")) phoneNumber else "+56$phoneNumber"
    }

    private fun generateRandomCallerId(): String {
        val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..9)
            .map { characters.random() }
            .joinToString("")
    }
}