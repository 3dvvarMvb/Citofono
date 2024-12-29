package com.example.citofono

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.citofono.ui.theme.CitofonoTheme
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

// -------------------------------------------------
// DATA CLASS
// -------------------------------------------------
data class Contact(
    val id: Int,
    val name: String,
    val phoneNumber: List<String>,
    val department: String
)

// -------------------------------------------------
// COMPOSABLES
// -------------------------------------------------
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
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        numberKeys.chunked(3).forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowKeys.forEach { key ->
                    Button(
                        onClick = { onKeyClick(key) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .padding(8.dp)
                            .size(95.dp)
                    ) {
                        Text(text = key, style = MaterialTheme.typography.h4)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            letterKeys.forEach { key ->
                Button(
                    onClick = { onKeyClick(key) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(8.dp)
                        .size(60.dp)
                ) {
                    Text(text = key, style = MaterialTheme.typography.h4)
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
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { searchQuery = "" },
                modifier = Modifier.size(80.dp)
            ) {
                Text(text = "Borrar")
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
                        showDialog = true
                        departmentNotFound = false
                    }
                } else {
                    departmentNotFound = true
                }
            },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .height(80.dp)
        ) {
            Text(text = "Llamar")
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Seleccionar Teléfono") },
                text = {
                    Column {
                        Text("¿A qué número desea llamar?")
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
                                Text(text = "Teléfono ${index + 1}")
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
                        Text("Llamar")
                    }
                },
                dismissButton = {
                    Button(onClick = { showDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

// -------------------------------------------------
// FUNCION PARA CARGAR CONTACTOS
// -------------------------------------------------
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

// -------------------------------------------------
// MAIN ACTIVITY
// -------------------------------------------------
class MainActivity : ComponentActivity() {

    private val contacts = mutableStateListOf<Contact>()
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private var pendingPhoneNumber: String? = null

    // TelephonyManager para escuchar estado de llamada
    private lateinit var telephonyManager: TelephonyManager

    // Permiso para leer el estado de llamadas
    private lateinit var phoneStatePermissionLauncher: ActivityResultLauncher<String>

    // Escucha de estado de llamadas
    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            super.onCallStateChanged(state, phoneNumber)
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    // La llamada está en curso (saliente o atendida)
                    stopKioskMode()
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    // No hay llamada o se finalizó la llamada
                    startKioskMode()
                }
                TelephonyManager.CALL_STATE_RINGING -> {
                    // Sonando (entrante), opcionalmente puedes
                    // llamar a stopKioskMode() si quieres liberar la UI en este punto
                }
            }
        }
    }

    // BroadcastReceiver para actualizar contactos
    private val updateContactsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            contacts.clear()
            contacts.addAll(loadContactsFromCsv(context!!))
            Toast.makeText(context, "Contactos actualizados", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Instanciar TelephonyManager
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        // Launcher para solicitar READ_PHONE_STATE
        phoneStatePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                Toast.makeText(this, "Permiso READ_PHONE_STATE denegado", Toast.LENGTH_SHORT).show()
            } else {
                // Ya tenemos permiso, podemos escuchar estado de llamadas
                startListeningCallState()
            }
        }

        // Revisa si ya se tiene READ_PHONE_STATE, si no, lo pide
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        } else {
            // Ya tienes el permiso, comienzas a escuchar
            startListeningCallState()
        }

        // Iniciar Lock Task Mode (Kiosk) al crear la actividad
        startKioskMode()

        // Lógica para permisos de llamada (CALL_PHONE)
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

        // Cargar contactos al iniciar
        contacts.addAll(loadContactsFromCsv(this))

        // Renderizar UI
        setContent {
            CitofonoTheme {
                val context = LocalContext.current
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SearchScreen(
                            contacts = contacts,
                            onCallClick = { phoneNumber, department ->
                                makeCall(phoneNumber)
                            }
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            // Ej. abrir AdminActivity, etc.
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
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(updateContactsReceiver)

        // Dejar de escuchar cuando la Activity no esté en primer plano
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
    }

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
    // Iniciar escucha estado de llamadas
    // ---------------------------------------------
    private fun startListeningCallState() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
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
