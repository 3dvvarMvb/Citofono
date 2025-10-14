@file:Suppress("DEPRECATION")

package com.example.citofono

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.MediaStore
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.citofono.ui.theme.CitofonoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import android.util.Base64
import androidx.compose.ui.graphics.Color

// Paleta usada en tu UI
val customColor = Color(red = 250, green = 244, blue = 226, alpha = 255)
val customColor2 = Color(red = 192, green = 76, blue = 54, alpha = 255)

/**
 * AdminActivity:
 * - Login con PIN local
 * - Importar contactos a BD (Contacts.import) enviando XLSX (o CSV->XLSX) en base64
 * - Exportar contactos desde BD (Contacts.export) y guardar XLSX en Descargas
 * - Salir de Kiosk
 */
class AdminActivity : ComponentActivity() {

    // Ajusta host/puerto a tu BUS
    private val esb = EsbClient(host = "10.0.2.2", port = 5000)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CitofonoTheme {
                AdminScreen(
                    onExitKioskClick = { stopKioskMode() },
                    onImportFile = { uri, ext ->
                        // Importación vía ESB
                        importContactsViaESB(this, uri, ext)
                    },
                    onExportXlsx = {
                        exportContactsXlsxToDownloads(this)
                    },
                    onBackToMain = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
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

    // --- Lógica ESB ---

    private suspend fun ensureConnected(): Boolean = withContext(Dispatchers.IO) {
        if (!esb.isConnected()) esb.connectAndRegister(kind = "client") else true
    }

    private fun importContactsViaESB(context: Context, uri: Uri, extension: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!ensureConnected()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No hay conexión al BUS", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val base64Xlsx = when (extension.lowercase()) {
                    "xlsx" -> readStreamAsBase64(context, uri)
                    "csv"  -> csvToXlsxBase64(context, uri)
                    else -> {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Formato no soportado: $extension", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                }

                val body = JSONObject().put("fileContent", base64Xlsx)
                val resp = esb.request(
                    service = "Contacts",
                    action = "import",
                    body = body,
                    timeoutMs = 15_000
                )

                val payload = resp.optJSONObject("payload") ?: resp
                val data = payload.optJSONObject("data") ?: payload
                val imported = data.optInt("importedCount", -1)
                val errors = data.optJSONArray("errors") ?: JSONArray()

                withContext(Dispatchers.Main) {
                    val msg = buildString {
                        append("Importación completada. ")
                        if (imported >= 0) append("Importados: $imported. ")
                        if (errors.length() > 0) append("Errores: $errors")
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al importar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportContactsXlsxToDownloads(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!ensureConnected()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No hay conexión al BUS", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val body = JSONObject().put("format", "xlsx")
                val resp = esb.request(
                    service = "Contacts",
                    action = "export",
                    body = body,
                    timeoutMs = 12_000
                )

                val payload = resp.optJSONObject("payload") ?: resp
                val data = payload.optJSONObject("data") ?: payload
                val base64File = data.optString("fileContent", "")

                if (base64File.isBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Respuesta inválida del servicio", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val fileName = "contactos_export_${System.currentTimeMillis()}.xlsx"
                saveBase64ToDownloads(
                    context = context,
                    base64 = base64File,
                    fileName = fileName,
                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Exportado a Descargas: $fileName", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- Utilidades de archivos ---

    private fun readStreamAsBase64(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun csvToXlsxBase64(context: Context, uri: Uri): String {
        val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() } ?: emptyList()
        val wb: Workbook = XSSFWorkbook()
        val sheet = wb.createSheet("Contactos")

        lines.forEachIndexed { rIdx, raw ->
            val row = sheet.createRow(rIdx)
            // Soporta ; o , como separador
            val cols = raw.split(';', ',')
            cols.forEachIndexed { cIdx, value -> row.createCell(cIdx).setCellValue(value.trim()) }
        }

        val baos = ByteArrayOutputStream()
        wb.use { it.write(baos) }
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun saveBase64ToDownloads(context: Context, base64: String, fileName: String, mimeType: String) {
        val bytes = Base64.decode(base64, Base64.DEFAULT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("No se pudo crear entrada en MediaStore")

            resolver.openOutputStream(uri)?.use { out -> out.write(bytes) }

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloads.exists()) downloads.mkdirs()
            val outFile = File(downloads, fileName)
            FileOutputStream(outFile).use { it.write(bytes) }
        }
    }
}

// ----------------- UI -----------------

@Composable
fun AdminScreen(
    onExitKioskClick: () -> Unit,
    onImportFile: (Uri, String) -> Unit,
    onExportXlsx: () -> Unit,
    onBackToMain: () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
    var savedKey by remember { mutableStateOf(sharedPreferences.getString("admin_key", "1234") ?: "1234") }
    var key by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoggedIn by remember { mutableStateOf(false) }
    var isChangingKey by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            val ext = getFileExtension(context, uri)
            if (ext == "xlsx" || ext == "csv") {
                busy = true
                // Llamar importación (manejo en IO y toasts dentro)
                onImportFile(uri, ext)
                // Espera simple para UX; el toast avisa resultado
                // Podrías reemplazar por estado compartido si prefieres
                busy = false
            } else {
                Toast.makeText(context, "Archivo inválido. Solo .xlsx o .csv", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                        Toast.makeText(context, "Clave actualizada exitosamente", Toast.LENGTH_SHORT).show()
                        isChangingKey = false
                        savedKey = newKey
                    } else {
                        Toast.makeText(context, "La clave no puede estar vacía", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Gestión de Contactos (BD)", style = MaterialTheme.typography.h5, color = customColor)

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "*/*"
                            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "text/csv",
                                "application/csv",
                                "text/comma-separated-values"
                            ))
                        }
                        filePickerLauncher.launch(intent)
                    },
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth().height(70.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = customColor2)
                ) {
                    Text("Importar XLSX/CSV al sistema", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = customColor)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { onExportXlsx() },
                    modifier = Modifier.fillMaxWidth().height(70.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = customColor2)
                ) {
                    Text("Exportar contactos (XLSX)", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = customColor)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("Botón para manejar Android libremente", style = MaterialTheme.typography.h6, color = customColor)

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onExitKioskClick,
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth().height(70.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = customColor2)
                ) {
                    Text("Salir del modo Kiosk", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = customColor)
                }
            }

            // Volver (arriba-izquierda)
            FloatingActionButton(
                onClick = onBackToMain,
                backgroundColor = customColor2,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver al inicio", tint = customColor) }

            // Acciones (arriba-derecha): cambiar PIN y exportar XLSX
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(onClick = { isChangingKey = true }, backgroundColor = customColor2) {
                    Icon(Icons.Default.Lock, contentDescription = "Cambiar PIN", tint = customColor)
                }
                FloatingActionButton(onClick = { onExportXlsx() }, backgroundColor = customColor2) {
                    Icon(Icons.Default.List, contentDescription = "Exportar XLSX", tint = customColor)
                }
            }
        }

        if (busy) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Ingrese la nueva clave", style = MaterialTheme.typography.h3, color = customColor)

        Spacer(modifier = Modifier.height(16.dp))

        BasicTextField(
            value = newKey,
            onValueChange = { newKey = it },
            modifier = Modifier.fillMaxWidth().border(1.dp, customColor2).padding(16.dp),
            visualTransformation = PasswordVisualTransformation(),
            decorationBox = { inner ->
                if (newKey.isEmpty()) Text("Nueva clave", color = customColor)
                inner()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        BasicTextField(
            value = confirmKey,
            onValueChange = { confirmKey = it },
            modifier = Modifier.fillMaxWidth().border(1.dp, customColor2).padding(16.dp),
            visualTransformation = PasswordVisualTransformation(),
            decorationBox = { inner ->
                if (confirmKey.isEmpty()) Text("Confirmar clave", color = customColor)
                inner()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (errorMessage.isNotEmpty()) {
            Text(text = errorMessage, color = MaterialTheme.colors.error, modifier = Modifier.padding(bottom = 16.dp))
        }

        Button(
            onClick = {
                if (newKey == confirmKey) onSaveKey(newKey) else errorMessage = "Las claves no coinciden. Inténtalo de nuevo."
            },
            colors = ButtonDefaults.buttonColors(backgroundColor = customColor2),
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth().height(70.dp),
        ) { Text("Guardar Clave", color = customColor) }
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
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Pantalla de Administración", style = MaterialTheme.typography.h3, color = customColor)

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = key,
            onValueChange = onKeyChange,
            label = { Text("Clave de administrador", color = customColor) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(1f).height(80.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onLogin,
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth().height(70.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = customColor2)
        ) { Text("Ingresar", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = customColor) }

        if (errorMessage.isNotEmpty()) {
            Text(text = errorMessage, color = MaterialTheme.colors.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// --- helpers UI/archivos ---

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
