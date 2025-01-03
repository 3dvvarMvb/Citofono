package com.example.citofono

import android.content.Context
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.registerForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.citofono.ui.theme.CitofonoTheme
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

data class Contact(
    val id: Int,
    val name: String,
    val phoneNumber: List<String>,
    val department: String
)

@Composable
fun NumericKeyboard(onKeyClick: (String) -> Unit) {
    val numberKeys = listOf(
        "1", "2", "3",
        "4", "5", "6",
        "7", "8", "9",
        "0"
    )
    val letterKeys = listOf("A", "B", "C", "D")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Teclado numérico
        numberKeys.chunked(3).forEach { rowKeys ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowKeys.forEach { key ->
                    Button(
                        onClick = { onKeyClick(key) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .width(150.dp)
                            .height(100.dp)
                            .shadow(elevation = 4.dp, shape = RoundedCornerShape(50))
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.h3,
                            color = Color.Black
                        )
                    }
                }
            }
        }

        // Teclas de letras
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            letterKeys.forEach { key ->
                Button(
                    onClick = { onKeyClick(key) },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color.White
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .width(130.dp)
                        .height(100.dp)
                        .shadow(elevation = 4.dp, shape = RoundedCornerShape(50))
                ) {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.h4,
                        color = Color.Black
                    )
                }
            }
        }
    }
}


@Composable
fun SearchScreen(
    contacts: List<Contact>,
    onCallClick: (String, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    var selectedPhoneNumbers by remember { mutableStateOf(listOf<String>()) }
    var selectedDepartment by remember { mutableStateOf("") }
    var selectedPhoneNumber by remember { mutableStateOf("") }
    var selectedPhoneIndex by remember { mutableStateOf(-1) }
    var departmentNotFound by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(departmentNotFound) {
        if (departmentNotFound) {
            snackbarHostState.showSnackbar(
                message = "DEPTO NO ENCONTRADO",
                duration = SnackbarDuration.Short
            )
            departmentNotFound = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SnackbarHost(hostState = snackbarHostState)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar Departamento") },
                textStyle = MaterialTheme.typography.h4,
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .focusRequester(focusRequester)
                    .focusProperties { canFocus = false }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { searchQuery = "" },
                modifier = Modifier.size(80.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color.Red
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Borrar")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        NumericKeyboard(onKeyClick = { key -> searchQuery += key })

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val departmentContacts = contacts.filter { it.department.contains(searchQuery, ignoreCase = true) }
                if (departmentContacts.isNotEmpty()) {
                    val firstContact = departmentContacts.first()
                    if (firstContact.phoneNumber.isNotEmpty()) {
                        selectedPhoneNumbers = firstContact.phoneNumber
                        selectedDepartment = firstContact.department
                        if (selectedPhoneNumbers.size > 1 && selectedPhoneNumbers[1].contains("-")) {
                            onCallClick(selectedPhoneNumbers[0], selectedDepartment)
                        } else {
                            showDialog = true
                        }
                        departmentNotFound = false
                    }
                } else {
                    departmentNotFound = true
                }
            },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .height(80.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Green)
        ) {
            Icon(Icons.Default.Phone, contentDescription = "Llamar")
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Seleccionar Teléfono",style = MaterialTheme.typography.h5) },
                text = {
                    Column {
                        Text("¿A qué número desea llamar?",style = MaterialTheme.typography.h5)
                        selectedPhoneNumbers.forEachIndexed { index, phone ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPhoneIndex = index
                                        selectedPhoneNumber = phone
                                    }
                                    .padding(8.dp)
                            ) {
                                RadioButton(
                                    selected = selectedPhoneIndex == index,
                                    onClick = {
                                        selectedPhoneIndex = index
                                        selectedPhoneNumber = phone
                                    }
                                )
                                Text(text = "Teléfono ${index + 1}",style = MaterialTheme.typography.h5)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onCallClick(selectedPhoneNumber, selectedDepartment)
                            showDialog = false
                        }
                    ) {
                        Text("Llamar", style = MaterialTheme.typography.h5)
                    }
                },
                dismissButton = {
                    Button(onClick = { showDialog = false }) {
                        Text("Cancelar", style = MaterialTheme.typography.h5)
                    }
                }
            )
        }
    }
}

fun loadContactsFromCsv(context: Context): List<Contact> {
    val contacts = mutableListOf<Contact>()

    val file = File(context.filesDir, "contactos.csv")
    if (file.exists()) {
        val inputStream = FileInputStream(file)
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
    }
    return contacts
}

class MainActivity : ComponentActivity() {

    private val contacts = mutableStateListOf<Contact>()
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private var pendingPhoneNumber: String? = null

    // BroadcastReceiver para actualizar contactos
    private val updateContactsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            contacts.clear()
            contacts.addAll(loadContactsFromCsv(context!!))
            Toast.makeText(context, "Contactos actualizados", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------
    // ADDED: Referencias para DevicePolicyManager
    // ---------------------------------------------
    private val devicePolicyManager by lazy {
        getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
    }
    private val adminComponentName by lazy {
        android.content.ComponentName(this, MyDeviceAdminReceiver::class.java)
    }
    // ---------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ADDED: Configurar la whitelist del modo kiosk (Lock Task) con el dialer
        configureLockTaskPackages()

        // Iniciar Lock Task Mode (Kiosk) al crear la actividad
        startKioskMode()

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

        // Cargar contactos al iniciar la app
        contacts.addAll(loadContactsFromCsv(this))

        setContent {
            CitofonoTheme {
                val context = LocalContext.current
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SearchScreen(
                            contacts = contacts,
                            onCallClick = { phoneNumber, department -> makeCall(phoneNumber) }
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            // Aquí podrías crear un método para salir del kiosk
                            // llamando a stopKioskMode() si gustas:
                            // stopKioskMode()
                            // O simplemente abrir AdminActivity:
                            val intent = Intent(context, AdminActivity::class.java)
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Ir a Admin")
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("com.example.citofono.UPDATE_CONTACTS")
        registerReceiver(updateContactsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        // Actualizar contactos al iniciar la actividad
        contacts.clear()
        contacts.addAll(loadContactsFromCsv(this))
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(updateContactsReceiver)
    }

    // ---------------------------------------------
    // ADDED: configurar la whitelist para modo kiosk
    // ---------------------------------------------
    private fun configureLockTaskPackages() {
        try {
            // El dialer varía según el dispositivo: "com.android.dialer", "com.google.android.dialer", etc.
            devicePolicyManager.setLockTaskPackages(
                adminComponentName,
                arrayOf(
                    packageName,
                    "com.android.dialer",
                    "com.google.android.dialer",
                    "com.android.incallui"// Ajusta según tu dispositivo
                )
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(
                this,
                "No se pudo configurar LockTaskPackages. ¿La app es Device Owner?",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    // ---------------------------------------------

    // ---------------------------------------------
    // Modo Kiosk (Lock Task): iniciar y detener
    // ---------------------------------------------
    private fun startKioskMode() {
        try {
            startLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopKioskMode() {
        try {
            stopLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ---------------------------------------------
    // Lógica para realizar llamadas telefónicas
    // ---------------------------------------------
    private fun makeCall(phoneNumber: String) {
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
}
//martuko puto