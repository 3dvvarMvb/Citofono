package com.example.citofono

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
                if (getFileExtension(context, it) == "csv") {
                    saveFileToInternalStorage(context, it, "contactos.csv")
                    uploadSuccess = true
                    Toast.makeText(context, "Archivo CSV actualizado correctamente", Toast.LENGTH_SHORT).show()
                } else if (getFileExtension(context, it) == "xlsx") {
                    val inputStream = context.contentResolver.openInputStream(it)
                    val outputFile = File(context.filesDir, "contactos.csv")
                    inputStream?.let { input ->
                        convertExcelToCsv(input, outputFile)
                        uploadSuccess = true
                        Toast.makeText(context, "Archivo Excel convertido y actualizado correctamente", Toast.LENGTH_SHORT).show()
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

                if (uploadSuccess) {
                    Text("Archivo subido y actualizado exitosamente.", color = MaterialTheme.colors.primary)
                }
            }

            FloatingActionButton(
                onClick = { isChangingKey = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = "Cambiar clave")
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
    val contentResolver = context.contentResolver
    val mimeType = contentResolver.getType(uri)
    return mimeType?.substringAfter("/").orEmpty()
}

fun convertExcelToCsv(inputStream: InputStream, outputFile: File) {
    val workbook: Workbook = XSSFWorkbook(inputStream)
    val sheet = workbook.getSheetAt(0)
    val writer = BufferedWriter(FileWriter(outputFile))

    for (row in sheet) {
        val rowData = StringBuilder()
        for (cell in row) {
            when (cell.cellType) {
                CellType.STRING -> rowData.append(cell.stringCellValue)
                CellType.NUMERIC -> rowData.append(cell.numericCellValue.toString())
                else -> rowData.append("")
            }
            rowData.append(";")
        }
        writer.write(rowData.toString().dropLast(1))
        writer.newLine()
    }

    writer.close()
    workbook.close()
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
