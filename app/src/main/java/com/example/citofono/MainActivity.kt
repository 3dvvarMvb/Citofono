package com.example.citofono

import android.content.Context
import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.citofono.ui.theme.CitofonoTheme
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import androidx.core.net.toUri

data class Contact(
    val id: Int,
    val name: String,
    val phoneNumber: List<String>,
    val department: String
)

/**
 * Composable que muestra un teclado numérico personalizado con botones grandes.
 *
 * @param onKeyClick Función lambda que se invoca cuando se presiona una tecla, recibiendo el valor de la tecla presionada.
 *
 * El teclado incluye:
 * - Números del 0 al 9, distribuidos en filas de 3 columnas.
 * - Letras A, B, C y D en una fila separada.
 * - Cada botón tiene un diseño redondeado, sombra y colores personalizados.
 * - Al presionar cualquier botón, se llama a `onKeyClick` con el valor correspondiente.
 */
@Composable
fun NumericKeyboard(onKeyClick: (String) -> Unit) {
    val numberKeys = listOf(
        "1", "2", "3",
        "4", "5", "6",
        "7", "8", "9"
    )
    val letterKeys = listOf("A", "B", "C", "D")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        val numberRows = numberKeys.chunked(3)
        numberRows.forEach { rowKeys ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rowKeys.forEach { key ->
                    Button(
                        onClick = { onKeyClick(key) },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
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
        // Fila especial para el "0" centrado
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { onKeyClick("0") },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(50))
            ) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.h3,
                    color = Color.Black
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            letterKeys.forEach { key ->
                Button(
                    onClick = { onKeyClick(key) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
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
fun ResponsiveKeyboardBox(onKeyClick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f)
            .wrapContentHeight()
            .padding(8.dp)
    ) {
        NumericKeyboard(onKeyClick = onKeyClick)
    }
}
/**
 * Pantalla de búsqueda de departamentos y selección de teléfonos para llamar.
 *
 * @param contacts Lista de contactos disponibles.
 * @param onCallClick Función lambda que se invoca al seleccionar un número para llamar. Recibe el número y el departamento.
 * @param searchQuery Texto actual de búsqueda ingresado por el usuario.
 * @param onSearchQueryChange Función lambda que se invoca cuando cambia el texto de búsqueda.
 *
 * Características:
 * - Permite buscar departamentos por nombre.
 * - Muestra un teclado numérico personalizado para ingresar la búsqueda.
 * - Si el departamento existe y tiene varios teléfonos, muestra un diálogo para seleccionar cuál llamar.
 * - Muestra un Snackbar si el departamento no se encuentra.
 * - Permite limpiar la búsqueda y reiniciar el campo.
 */
@Composable
fun SearchScreen(
    contacts: List<Contact>,
    onCallClick: (String, String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var selectedPhoneNumbers by remember { mutableStateOf(listOf<String>()) }
    var selectedDepartment by remember { mutableStateOf("") }
    var selectedPhoneNumber by remember { mutableStateOf("") }
    var selectedPhoneIndex by remember { mutableIntStateOf(-1) }
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
                onValueChange = onSearchQueryChange,
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
                onClick = { onSearchQueryChange("") },
                modifier = Modifier.size(80.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color.Red
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Borrar")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ResponsiveKeyboardBox(onKeyClick = { key -> onSearchQueryChange(searchQuery + key) })

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (searchQuery.isNotBlank()) {
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
                                Text(text = "Teléfono ${index + 1}", style = MaterialTheme.typography.h5)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onCallClick(selectedPhoneNumber, selectedDepartment)
                            showDialog = false
                            selectedPhoneNumber = ""
                            selectedPhoneIndex = -1
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

/**
 * Carga los contactos desde un archivo CSV ubicado en el directorio de archivos internos de la aplicación.
 *
 * @param context Contexto de la aplicación.
 * @return Lista de contactos cargados desde el archivo CSV.
 */
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

/**
 * Actividad principal de la aplicación.
 *
 * Esta actividad se encarga de gestionar la interfaz de usuario y la lógica de negocio relacionada con los contactos.
 * También maneja el modo quiosco y las llamadas telefónicas.
 */
class MainActivity : ComponentActivity() {

    private val contacts = mutableStateListOf<Contact>()
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private var pendingPhoneNumber: String? = null
    private var searchQuery by mutableStateOf("")

    private val updateContactsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            contacts.clear()
            contacts.addAll(loadContactsFromCsv(context!!))
            Toast.makeText(context, "Contactos actualizados", Toast.LENGTH_SHORT).show()
        }
    }

    private val devicePolicyManager by lazy {
        getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }
    private val adminComponentName by lazy {
        ComponentName(this, MyDeviceAdminReceiver::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureLockTaskPackages()
        setLockTaskFeatures()
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

        contacts.addAll(loadContactsFromCsv(this))

        setContent {
            CitofonoTheme {
                val context = LocalContext.current
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SearchScreen(
                            contacts = contacts,
                            onCallClick = { phoneNumber, _ ->
                                makeCall(phoneNumber)
                                // Reinicia el query después de llamar
                                searchQuery = ""
                            },
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it }
                        )
                    }
                    FloatingActionButton(
                        onClick = {
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

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("com.example.citofono.UPDATE_CONTACTS")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateContactsReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateContactsReceiver, filter)
        }
        contacts.clear()
        contacts.addAll(loadContactsFromCsv(this))
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(updateContactsReceiver)
    }

    override fun onResume() {
        super.onResume()
        searchQuery = ""
    }

    private fun setLockTaskFeatures() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val context = applicationContext
            val dpm = context.getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.setLockTaskFeatures(
                adminComponentName,
                DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
                        DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                        DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                        DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
            )
        }
    }

    private fun configureLockTaskPackages() {
        try {
            devicePolicyManager.setLockTaskPackages(
                adminComponentName,
                arrayOf(
                    packageName,
                    "com.android.dialer",
                    "com.google.android.dialer",
                    "com.android.incallui",
                    "com.android.dialer.DialtactsActivity"
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

    private fun startKioskMode() {
        try {
            startLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun makeCall(phoneNumber: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = ("tel:" + validatePhoneNumber(phoneNumber)).toUri()
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

// Preview para NumericKeyboard
@Preview(showBackground = true, name = "NumericKeyboard Preview")
@Composable
fun PreviewNumericKeyboard() {
    CitofonoTheme {
        NumericKeyboard(onKeyClick = {})
    }
}

// Preview para SearchScreen
@Preview(showBackground = true, name = "SearchScreen Preview")
@Composable
fun PreviewSearchScreen() {
    val sampleContacts = listOf(
        Contact(1, "Juan", listOf("123456789", "987654321"), "Depto A"),
        Contact(2, "Ana", listOf("555555555"), "Depto B")
    )
    var searchQuery by remember { mutableStateOf("") }
    CitofonoTheme {
        SearchScreen(
            contacts = sampleContacts,
            onCallClick = { _, _ -> },
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it }
        )
    }
}