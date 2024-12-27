package com.example.citofono

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.citofono.ui.theme.CitofonoTheme
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.*

class AdminActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CitofonoTheme {
                AdminScreen()
            }
        }
    }
}

@Composable
fun AdminScreen() {
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                Text("Actualizar archivo de contactos", style = MaterialTheme.typography.h6)

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_GET_CONTENT)
                        intent.type = "*/*"
                        val mimeTypes = arrayOf("text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                        filePickerLauncher.launch(intent)

                    }
                ) {
                    Text("Seleccionar archivo CSV o Excel")
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (uploadSuccess) {
                    updateContactsAfterUpload(context)
                    Toast.makeText(context, "Archivo subido y actualizado exitosamente.", Toast.LENGTH_SHORT).show()
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = { isChangingKey = true }
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Cambiar clave")
                }

                FloatingActionButton(
                    onClick = {
                        exportFileToDownloads(context, "contactos.csv")
                    }
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = "Exportar archivo")
                }
                FloatingActionButton(
                    onClick = {
                        exportFileToDownloads(context, "contactos.vcf")
                    }
                ) {
                    Icon(Icons.Default.List, contentDescription = "Exportar VCF")
                }
            }
        }
    }
}

@Composable
fun ChangeKeyScreen(onSaveKey: (String) -> Unit) {
    var newKey by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Ingrese la nueva clave", style = MaterialTheme.typography.h6)

        Spacer(modifier = Modifier.height(16.dp))

        BasicTextField(
            value = newKey,
            onValueChange = { newKey = it },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colors.primary)
                .padding(16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { onSaveKey(newKey) }) {
            Text("Guardar Clave")
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Pantalla de Administración", style = MaterialTheme.typography.h5)

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = key,
            onValueChange = onKeyChange,
            label = { Text("Clave de administrador") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = onLogin, modifier = Modifier.padding(top = 16.dp)) {
            Text("Ingresar")
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
    if (!csvFile.exists()) return
    try {
        val contacts = mutableListOf<Contact>()
        csvFile.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val parts = line.split(";")
                if (parts.size >= 2) {
                    val name = parts[0].trim()
                    val phoneNumbers = parts.drop(1).map { it.trim() }

                    val id = contacts.size 
                    val department = name 
                    contacts.add(Contact(id, name, phoneNumbers, department))
                }

            }
        }
        generateVcfFile(context, contacts)
        Toast.makeText(context, "Archivo VCF generado correctamente", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun generateVcfFile(context: Context, contacts: List<Contact>) {
    val vcfFile = File(context.filesDir, "contactos.vcf")
    val writer = FileWriter(vcfFile)
    try {
        contacts.forEach { contact ->
            writer.appendLine("BEGIN:VCARD")
            writer.appendLine("VERSION:3.0")
            writer.appendLine("FN:${contact.name}")
            contact.phoneNumber.forEach { phone ->
                writer.appendLine("TEL:$phone")
            }
            writer.appendLine("END:VCARD")
        }
    } finally {
        writer.close()
    }
}