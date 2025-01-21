package com.example.citofono

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.ContactsContract
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.citofono.ui.theme.CitofonoTheme
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.graphics.Color

val customColor = Color(red = 250, green = 244, blue = 226, alpha = 255)
val customColor2 = Color(red = 192, green = 76, blue = 54, alpha = 255)

class AdminActivity : ComponentActivity() {
    companion object {
        private const val REQUEST_WRITE_CONTACTS_PERMISSION = 1
    }

    private val devicePolicyManager by lazy {
        getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }
    private val adminComponentName by lazy {
        ComponentName(this, MyDeviceAdminReceiver::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CitofonoTheme {
                AdminScreen(this, onExitKioskClick = { stopKioskMode() })
            }
        }
    }

    private fun stopKioskMode() {
        try {
            stopLockTask()
            Toast.makeText(this, "Saliendo del modo Kiosk", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_WRITE_CONTACTS_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permiso para escribir contactos otorgado.", Toast.LENGTH_SHORT).show()
                updateContactsAfterUpload(this)
            } else {
                Toast.makeText(this, "Permiso para escribir contactos denegado.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun AdminScreen(adminActivity: AdminActivity, onExitKioskClick: () -> Unit) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
    var savedKey by remember { mutableStateOf(sharedPreferences.getString("admin_key", "1234") ?: "1234") }
    var key by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoggedIn by remember { mutableStateOf(false) }
    var isChangingKey by remember { mutableStateOf(false) }
    var uploadSuccess by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            uri?.let {
                val fileExtension = getFileExtension(context, it)

                if (fileExtension == "csv" || fileExtension == "xlsx") {
                    val outputFile = File(context.filesDir, "contactos.csv")
                    if (fileExtension == "csv") {
                        saveFileToInternalStorage(context, it, "contactos.csv")
                        uploadSuccess = true
                        Toast.makeText(context, "Archivo CSV actualizado correctamente", Toast.LENGTH_SHORT).show()
                    } else {
                        val inputStream = context.contentResolver.openInputStream(it)
                        inputStream?.let { input ->
                            convertExcelToCsv(input, outputFile)
                            uploadSuccess = true
                            Toast.makeText(context, "Archivo Excel convertido y actualizado correctamente", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Archivo inválido. Solo se permiten .csv o .xlsx", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = R.drawable.fondoapp2),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (!isLoggedIn) {
            LoginScreen(
                key = key,
                onKeyChange = { key = it },
                onLogin = {
                    if (key == savedKey) {
                        isLoggedIn = true
                        Toast.makeText(context, "Acceso exitoso", Toast.LENGTH_SHORT).show()
                    } else {
                        errorMessage = "Clave incorrecta. Intenta nuevamente."
                    }
                },
                errorMessage = errorMessage
            )
        } else if (isChangingKey) {
            ChangeKeyScreen(
                onSaveKey = { newKey ->
                    if (newKey.isNotEmpty()) {
                        with(sharedPreferences.edit()) {
                            putString("admin_key", newKey)
                            apply()
                        }
                        savedKey = newKey
                        isChangingKey = false
                        Toast.makeText(context, "Clave actualizada exitosamente", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "La clave no puede estar vacía", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Actualizar archivo de contactos",
                    style = MaterialTheme.typography.h6,
                    color = customColor
                    )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        checkAndRequestWriteContactsPermission(adminActivity) {
                            val intent = Intent(Intent.ACTION_GET_CONTENT)
                            intent.type = "*/*"
                            val mimeTypes = arrayOf("text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                            filePickerLauncher.launch(intent)
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp)
                        .fillMaxWidth()
                        .height(70.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = customColor2)
                ) {
                    Text(text = "Seleccionar archivo Excel",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = customColor)
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (uploadSuccess) {
                    updateContactsAfterUpload(context)
                    Toast.makeText(context, "Archivo subido y actualizado exitosamente.", Toast.LENGTH_SHORT).show()
                }

                Text("Boton para manejar android libremente",
                    style = MaterialTheme.typography.h6,
                    color = customColor
                    )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onExitKioskClick() },
                    modifier = Modifier.padding(top = 16.dp)
                        .fillMaxWidth()
                        .height(70.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = customColor2)
                ) {
                    Text(text = "Salir del modo Kiosk",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = customColor)
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = { isChangingKey = true },
                    backgroundColor = customColor2
                )
                {
                    Icon(Icons.Default.Lock,
                        contentDescription = "Cambiar clave",
                        tint = customColor)
                }
                FloatingActionButton(
                    onClick = {
                        exportFileToDownloads(context, "contactos.vcf")
                    },
                    backgroundColor = customColor2
                ) {
                    Icon(Icons.Default.List,
                        contentDescription = "Exportar VCF",
                        tint = customColor)
                }
            }
        }
    }
}

@Composable
fun ChangeKeyScreen(onSaveKey: (String) -> Unit) {
    var newKey by remember { mutableStateOf("") }
    var confirmKey by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    Image(
        painter = painterResource(id = R.drawable.fondoapp2),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Ingrese la nueva clave",
            style = MaterialTheme.typography.h3,
            color = customColor)

        Spacer(modifier = Modifier.height(16.dp))

        BasicTextField(
            value = newKey,
            onValueChange = { newKey = it },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, customColor2)
                .padding(16.dp),
            visualTransformation = PasswordVisualTransformation(),
            decorationBox = { innerTextField ->
                if (newKey.isEmpty()) {
                    Text("Nueva clave",
                        color = customColor,
                        )
                }
                innerTextField()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        BasicTextField(
            value = confirmKey,
            onValueChange = { confirmKey = it },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, customColor2)
                .padding(16.dp),
            visualTransformation = PasswordVisualTransformation(),
            decorationBox = { innerTextField ->
                if (confirmKey.isEmpty()) {
                    Text("Confirmar clave",
                        color = customColor)
                }
                innerTextField()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colors.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Button(onClick = {
            if (newKey == confirmKey) {
                onSaveKey(newKey)
            } else {
                errorMessage = "Las claves no coinciden. Inténtalo de nuevo."
            }
        },
            colors = ButtonDefaults.buttonColors(backgroundColor = customColor2),
                modifier = Modifier.padding(top = 16.dp)
                .fillMaxWidth()
                .height(70.dp),
        )
        {
            Text("Guardar Clave",
                color = customColor)
        }
    }
}

@Composable
fun LoginScreen(
    key: String,
    onKeyChange: (String) -> Unit,
    onLogin: () -> Unit,
    errorMessage: String
) {

    Image(
        painter = painterResource(id = R.drawable.fondoapp2),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Pantalla de Administración",
            style = MaterialTheme.typography.h3,
            color = customColor)

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = key,
            onValueChange = onKeyChange,
            label = { Text("Clave de administrador",
                color= customColor)},
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(1f)
                .height(80.dp) // Aumenta la altura
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = onLogin,
            modifier = Modifier.padding(top = 16.dp)
                .fillMaxWidth()
                .height(70.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = customColor2)
        ) {
            Text("Ingresar",
                fontSize = 20.sp, // Increase the text size
                fontWeight = FontWeight.Bold // Make the text bold
                ,color = customColor)
        }

        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colors.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

fun getFileExtension(context: Context, uri: Uri): String {
    var extension = ""

    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val fileName = it.getString(nameIndex)
            extension = fileName.substringAfterLast('.', "")
        }
    }
    return extension.lowercase()
}

fun convertExcelToCsv(inputStream: InputStream, outputFile: File) {
    try {
        val workbook: Workbook = XSSFWorkbook(inputStream)
        val sheet = workbook.getSheetAt(0)
        val writer = BufferedWriter(FileWriter(outputFile))

        for (row in sheet) {
            val rowData = StringBuilder()

            for (cell in row) {
                when (cell.cellType) {
                    CellType.STRING -> {
                        rowData.append(cell.stringCellValue)
                    }
                    CellType.NUMERIC -> {
                        val cellValue = cell.toString()

                        if (cellValue.contains("E")) {
                            val formattedValue = String.format("%.0f", cell.numericCellValue)
                            rowData.append(formattedValue)
                        } else {
                            rowData.append(cellValue)
                        }
                    }
                    else -> {
                        rowData.append("")
                    }
                }
                rowData.append(";")
            }

            writer.write(rowData.toString().dropLast(1))
            writer.newLine()
        }

        writer.close()
        workbook.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun saveFileToInternalStorage(context: Context, uri: Uri, fileName: String) {
    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
    val outputFile = File(context.filesDir, fileName)
    val outputStream = FileOutputStream(outputFile)

    inputStream?.use { input ->
        outputStream.use { output ->
            input.copyTo(output)
        }
    }
}

fun exportFileToDownloads(context: Context, fileName: String) {
    val inputFile = File(context.filesDir, fileName)
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val outputFile = File(downloadsDir, fileName)

    try {
        inputFile.inputStream().use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        Toast.makeText(context, "Archivo exportado a: ${outputFile.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error al exportar el archivo", Toast.LENGTH_SHORT).show()
    }
}

fun updateContactsAfterUpload(context: Context) {
    val csvFile = File(context.filesDir, "contactos.csv")
    if (!csvFile.exists()) {
        Toast.makeText(context, "Archivo CSV no encontrado.", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        // Eliminar todos los contactos antes de actualizar
        deleteAllContacts(context)

        val contacts = mutableListOf<Contact>()
        csvFile.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val parts = line.split(";")
                if (parts.size >= 2) {
                    val name = parts[0].trim()
                    val phoneNumbers = parts.drop(1).map { it.trim() }
                    contacts.add(Contact(contacts.size, name, phoneNumbers, name))
                }
            }
        }

        generateVcfFile(context, contacts)
        Toast.makeText(context, "Archivo VCF generado correctamente", Toast.LENGTH_SHORT).show()

        contacts.forEach { contact ->
            addContactToPhone(contact.name, contact.phoneNumber, context)
        }

        Toast.makeText(context, "Contactos importados al teléfono correctamente.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error al procesar contactos: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun generateVcfFile(context: Context, contacts: List<Contact>) {
    val vcfFile = File(context.filesDir, "contactos.vcf")
    try {
        FileWriter(vcfFile).use { writer ->
            contacts.forEach { contact ->
                writer.appendLine("BEGIN:VCARD")
                writer.appendLine("VERSION:3.0")
                writer.appendLine("FN:${contact.name}")
                contact.phoneNumber.forEach { phone ->
                    writer.appendLine("TEL:$phone")
                }
                writer.appendLine("END:VCARD")
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error al generar archivo VCF: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun addContactToPhone(name: String, phoneNumbers: List<String>, context: Context) {
    val contentResolver = context.contentResolver

    try {
        val contactUri = contentResolver.insert(ContactsContract.RawContacts.CONTENT_URI, ContentValues())
            ?: throw Exception("Error al insertar contacto")
        val rawContactId = ContentUris.parseId(contactUri)

        val nameValues = ContentValues().apply {
            put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
        }
        contentResolver.insert(ContactsContract.Data.CONTENT_URI, nameValues)

        phoneNumbers.forEach { number ->
            val cleanedNumber = cleanPhoneNumber(number)
            val phoneValues = ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Phone.NUMBER, cleanedNumber)
                put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            }
            contentResolver.insert(ContactsContract.Data.CONTENT_URI, phoneValues)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error al agregar contacto: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun cleanPhoneNumber(phoneNumber: String): String {
    return phoneNumber.replace("[^+\\d]".toRegex(), "")
}

private val REQUEST_WRITE_CONTACTS_PERMISSION = 1

fun checkAndRequestWriteContactsPermission(activity: Activity, onPermissionGranted: () -> Unit) {
    if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(android.Manifest.permission.WRITE_CONTACTS),
            REQUEST_WRITE_CONTACTS_PERMISSION
        )
    } else {
        onPermissionGranted()
    }
}

private fun deleteAllContacts(context: Context) {
    val resolver: ContentResolver = context.contentResolver
    val uri = ContactsContract.RawContacts.CONTENT_URI

    try {
        val rowsDeleted = resolver.delete(uri, null, null)
        if (rowsDeleted > 0) {
            Toast.makeText(context, "Actualizacion de contactos en proceso", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No hay contactos para eliminar", Toast.LENGTH_SHORT).show()
        }
    } catch (e: SecurityException) {
        e.printStackTrace()
        Toast.makeText(context, "Error al eliminar contactos", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error desconocido", Toast.LENGTH_SHORT).show()
    }
}