package com.example.citofono

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
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

// Modelo de datos
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
                pendingPhoneNumber?.let { makeCallWithContact(it, "") }
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
                    onCallClick = { phoneNumber, department -> makeCallWithContact(phoneNumber, department) }
                )
            }
        }
    }

    private fun makeCallWithContact(phoneNumber: String, department: String) {
        val contactId = createTemporaryContact(department, phoneNumber)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:" + validatePhoneNumber(phoneNumber))
            }
            startActivity(intent)
            deleteContactWithDelay(contactId)
        } else {
            pendingPhoneNumber = phoneNumber
            requestPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun validatePhoneNumber(phoneNumber: String): String {
        return if (phoneNumber.startsWith("+")) phoneNumber else "+56$phoneNumber"
    }

    private fun createTemporaryContact(name: String, phoneNumber: String): String {
        return try {
            val ops = ArrayList<ContentProviderOperation>()
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            )
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )
            val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            results[0].uri?.lastPathSegment ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun deleteContactWithDelay(contactId: String) {
        Handler(Looper.getMainLooper()).postDelayed({
            val uri = Uri.withAppendedPath(ContactsContract.RawContacts.CONTENT_URI, contactId)
            contentResolver.delete(uri, null, null)
        }, 5000) // Eliminar después de 5 segundos
    }
}
