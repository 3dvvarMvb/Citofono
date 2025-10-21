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
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.citofono.ui.theme.CitofonoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// Paleta (reutilizada por AuthActivity)
val customColor = Color(red = 250, green = 244, blue = 226, alpha = 255)
val customColor2 = Color(red = 192, green = 76, blue = 54, alpha = 255)

/* ------------------------- Secciones (Bottom Nav) ------------------------- */

private sealed class AdminSection(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    data object ImportExport : AdminSection("Import/Export", Icons.Default.ImportExport)
    data object RegLlamadas  : AdminSection("Reg. Llamadas", Icons.Default.Call)
    data object RegMsg       : AdminSection("Reg. Mensajería", Icons.Default.Message)
    data object Usuarios     : AdminSection("Usuarios", Icons.Default.People)
}

class AdminActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CitofonoTheme {
                var section by remember { mutableStateOf<AdminSection>(AdminSection.ImportExport) }
                var busy by remember { mutableStateOf(false) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Consola Administrador") },
                            actions = {
                                TextButton(onClick = {
                                    SessionManager.logoutAndGoToLogin(this@AdminActivity)
                                }) { Text("Salir", color = Color.White) }
                            }
                        )
                    },
                    bottomBar = {
                        BottomNavigation {
                            listOf(
                                AdminSection.ImportExport,
                                AdminSection.RegLlamadas,
                                AdminSection.RegMsg,
                                AdminSection.Usuarios
                            ).forEach { item ->
                                BottomNavigationItem(
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label, fontSize = 11.sp) },
                                    selected = section::class == item::class,
                                    onClick = { section = item }
                                )
                            }
                        }
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {

                        when (section) {
                            is AdminSection.ImportExport -> ImportExportScreen(
                                onImportFile = { uri, ext, onDone ->
                                    busy = true
                                    importContactsViaESB(this@AdminActivity, uri, ext) {
                                        busy = false
                                        onDone()
                                    }
                                },
                                onExportXlsx = {
                                    busy = true
                                    exportContactsXlsxToDownloads(this@AdminActivity) {
                                        busy = false
                                    }
                                }
                            )
                            is AdminSection.RegLlamadas  -> RegistroLlamadasScreen()
                            is AdminSection.RegMsg       -> RegistroMensajeriaScreen()
                            is AdminSection.Usuarios     -> UsuariosScreen()
                        }

                        if (busy) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }

    /* ========================= Import/Export helpers ========================= */

    private fun importContactsViaESB(
        context: Context,
        uri: Uri,
        extension: String,
        onDone: () -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val base64File = readStreamAsBase64(context, uri)
                val data = EsbApi.contactosImport(base64File, extension.lowercase())

                val inserted = data.optInt("inserted", 0)
                val updated  = data.optInt("updated", 0)
                val skipped  = data.optInt("skipped", -1)
                val imported = data.optInt("importedCount", inserted + updated)
                val errorsArr = data.optJSONArray("errors")

                val errors = buildString {
                    if (errorsArr != null && errorsArr.length() > 0) {
                        append("\nErrores:\n")
                        for (i in 0 until errorsArr.length()) {
                            append("• ").append(errorsArr.optString(i)).append('\n')
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    val msg = if (skipped >= 0)
                        "Importación: $imported (nuevos: $inserted, actualizados: $updated, omitidos: $skipped)$errors"
                    else
                        "Importación: $imported (nuevos: $inserted, actualizados: $updated)$errors"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al importar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) { onDone() }
            }
        }
    }

    private fun exportContactsXlsxToDownloads(context: Context, onDone: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = EsbApi.contactosExportXlsx()
                val base64File = data.optString("fileContent", "")
                val count = data.optInt("count", 0)
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
                    Toast.makeText(context, "Exportado $count contactos a Descargas: $fileName", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) { onDone() }
            }
        }
    }

    private fun readStreamAsBase64(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
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

/* ======================== UI SECTIONS (Composables) ======================== */

@Composable
private fun ImportExportScreen(
    onImportFile: (Uri, String, onDone: () -> Unit) -> Unit,
    onExportXlsx: (onDone: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            val ext = getFileExtension(context, uri)
            if (ext == "xlsx" || ext == "csv") {
                onImportFile(uri, ext) { busy = false }
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

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text("Gestión de Contactos", style = MaterialTheme.typography.h6)

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        putExtra(
                            Intent.EXTRA_MIME_TYPES, arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "text/csv", "application/csv", "text/comma-separated-values"
                            )
                        )
                    }
                    filePickerLauncher.launch(intent)
                    busy = true
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = customColor2)
            ) { Text("Importar XLSX/CSV", color = customColor) }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { onExportXlsx {} },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = customColor2)
            ) { Text("Exportar XLSX", color = customColor) }
        }
    }
}

// --- Modelo para cada llamada ---
data class CallLog(
    val id: String,
    val tsMillis: Long,
    val caller: String?,
    val depto: String?,
    val durationSec: Int,
    val status: String,
    val destination: String?
)

@Composable
private fun RegistroLlamadasScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Filtros básicos
    var q by remember { mutableStateOf("") }              // buscar por caller/depto/phone
    var range by remember { mutableStateOf("HOY") }       // HOY / SEMANA / TODO
    var busy by remember { mutableStateOf(false) }

    var items by remember { mutableStateOf(listOf<CallLog>()) }

    // Carga inicial y al cambiar filtros
    LaunchedEffect(q, range) {
        busy = true
        val (from, to) = when (range) {
            "HOY" -> dayBounds(System.currentTimeMillis())
            "SEMANA" -> weekBounds(System.currentTimeMillis())
            else -> 0L to System.currentTimeMillis()
        }
        runCatching {
            EsbApi.callsList(
                query = q.trim().ifBlank { null },
                fromMillis = from.takeIf { it > 0 },
                toMillis = to,
                limit = 500
            )
        }.onSuccess { items = it }
         .onFailure {
             Toast.makeText(ctx, "No se pudo cargar: ${it.message}", Toast.LENGTH_LONG).show()
             items = emptyList()
         }
        busy = false
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {

        // ---- Barra de filtros ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    label = { Text("Buscar (caller, depto, fono)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            IconButton(onClick = {
                scope.launch {
                    busy = true
                    val (from, to) = when (range) {
                        "HOY" -> dayBounds(System.currentTimeMillis())
                        "SEMANA" -> weekBounds(System.currentTimeMillis())
                        else -> 0L to System.currentTimeMillis()
                    }
                    runCatching {
                        EsbApi.callsList(q.trim().ifBlank { null }, from, to, 500)
                    }.onSuccess { items = it }
                     .onFailure {
                         Toast.makeText(ctx, "No se pudo recargar: ${it.message}", Toast.LENGTH_SHORT).show()
                     }
                    busy = false
                }
            }) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") }
        }

        Spacer(Modifier.height(8.dp))

        val tabs = listOf("HOY","SEMANA","TODO")
        var tabIndex by remember { mutableStateOf(tabs.indexOf(range).coerceAtLeast(0)) }
        TabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = tabIndex == idx,
                    onClick = { tabIndex = idx; range = tabs[idx] },
                    text = { Text(label) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- Encabezado de “tabla” ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            HCell("Fecha", 0.9f)
            HCell("Hora", 0.7f)
            HCell("Caller", 1.1f)
            HCell("Depto", 1.0f)
            HCell("Dur.", 0.6f)
            HCell("Estado", 0.9f)
        }
        Divider()

        Box(Modifier.fillMaxSize()) {
            if (busy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sin llamadas en el período seleccionado")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(items, key = { _, it -> it.id }) { _, it ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Cell(formatDate(it.tsMillis), 0.9f)
                            Cell(formatTime(it.tsMillis), 0.7f)
                            Cell(it.caller ?: "—", 1.1f)
                            Cell(it.depto ?: "—", 1.0f)
                            Cell(formatDuration(it.durationSec), 0.6f)
                            Row(Modifier.weight(0.9f), verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(colorForStatus(it.status))
                                Spacer(Modifier.width(6.dp))
                                Text(it.status.uppercase())
                            }
                        }
                        Divider()
                    }
                }
            }
        }
    }
}

/* --------- UI helpers para la "tabla" --------- */

// Ojo: extensión sobre RowScope para que el weight funcione
@Composable private fun RowScope.HCell(text: String, weight: Float) {
    Text(text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(weight))
}
@Composable private fun RowScope.Cell(text: String, weight: Float) {
    Text(text, modifier = Modifier.weight(weight))
}
@Composable private fun StatusDot(color: Color, sizeDp: Int = 10) {
    Box(Modifier.size(sizeDp.dp).background(color, CircleShape))
}

/* --------- Formateos/Filtros --------- */

private fun formatDate(ms: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        .format(java.util.Date(ms))

private fun formatTime(ms: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(ms))

private fun formatDuration(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}

private fun colorForStatus(status: String): Color = when (status.lowercase()) {
    "success", "answered", "ok", "completed" -> Color(0xFF2E7D32) // verde
    "missed", "failed", "busy", "canceled", "rejected" -> Color(0xFFC62828) // rojo
    "attempted", "dialing", "ringing", "pending" -> Color(0xFFF9A825) // ámbar
    else -> Color(0xFF546E7A) // gris
}

private fun dayBounds(nowMs: Long): Pair<Long, Long> {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    val start = cal.timeInMillis
    val end = start + 24L * 3600_000
    return start to end
}

private fun weekBounds(nowMs: Long): Pair<Long, Long> {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
    cal.firstDayOfWeek = java.util.Calendar.MONDAY
    cal.set(java.util.Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    val start = cal.timeInMillis
    val end = start + 7L * 24 * 3600_000
    return start to end
}

@Composable
private fun RegistroMensajeriaScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Registro de mensajería (por implementar)")
    }
}

/* ------------------------- helpers de archivos UI ------------------------- */

fun getFileExtension(context: Context, uri: Uri): String {
    var extension = ""
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                val fileName = it.getString(nameIndex) ?: ""
                extension = fileName.substringAfterLast('.', "")
            }
        }
    }
    return extension.lowercase()
}
@Composable
fun UsuariosScreen() {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // Crear usuario
    var newUser by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf("user") }
    var createMsg by remember { mutableStateOf("") }

    // Actualizar usuario
    var qUser by remember { mutableStateOf("") }
    var loadedRole by remember { mutableStateOf("user") }
    var loadedActive by remember { mutableStateOf(true) }
    var newPassword by remember { mutableStateOf("") }
    var updMsg by remember { mutableStateOf("") }
    var rawJson by remember { mutableStateOf("") }
    var showRaw by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Gestión de Usuarios", style = MaterialTheme.typography.h6)

        /* ---------- Crear ---------- */
        Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Crear usuario", style = MaterialTheme.typography.subtitle1)
                OutlinedTextField(newUser, { newUser = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(
                    newPass, { newPass = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                RoleSelector(selected = newRole, onSelect = { newRole = it })

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (newUser.isBlank() || newPass.isBlank()) {
                            Toast.makeText(ctx, "Completa usuario y password", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        createMsg = ""
                        scope.launch {
                            runCatching { EsbApi.authCreateUser(newUser.trim(), newPass.trim(), newRole) }
                                .onSuccess {
                                    createMsg = "Usuario creado: ${it.optString("username", newUser)} (${it.optString("role", newRole)})"
                                    newPass = ""
                                }
                                .onFailure { createMsg = "Error: ${it.message}" }
                        }
                    }) { Text("Crear") }
                }
                if (createMsg.isNotEmpty()) Text(createMsg)
            }
        }

        /* ---------- Actualizar ---------- */
        Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Actualizar usuario", style = MaterialTheme.typography.subtitle1)

                OutlinedTextField(qUser, { qUser = it }, label = { Text("Username") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (qUser.isBlank()) return@Button
                        updMsg = ""; rawJson = ""
                        scope.launch {
                            runCatching { EsbApi.adminGetUser(qUser.trim()) }
                                .onSuccess { resp ->
                                    rawJson = resp.toString(2)
                                    val u = resp.optJSONObject("user") ?: JSONObject()
                                    loadedRole = u.optString("role", "user")
                                    loadedActive = u.optBoolean("active", true)
                                    updMsg = "Cargado: role=$loadedRole, active=$loadedActive"
                                }
                                .onFailure { updMsg = "Error: ${it.message}" }
                        }
                    }) { Text("Cargar") }

                    Button(onClick = { // Desactivar
                        if (qUser.isBlank()) return@Button
                        updMsg = ""
                        scope.launch {
                            runCatching { EsbApi.adminDeleteUser(qUser.trim()) }
                                .onSuccess { updMsg = "Usuario desactivado" }
                                .onFailure { updMsg = "Error: ${it.message}" }
                        }
                    }) {
                        Icon(Icons.Default.Block, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Desactivar")
                    }
                }

                RoleSelector(selected = loadedRole, onSelect = { loadedRole = it })

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = loadedActive, onCheckedChange = { loadedActive = it })
                    Text("Activo")
                }

                OutlinedTextField(
                    value = newPassword, onValueChange = { newPassword = it },
                    label = { Text("Nuevo password (opcional)") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (qUser.isBlank()) return@Button
                        updMsg = ""
                        scope.launch {
                            runCatching {
                                EsbApi.adminUpdateUser(
                                    username = qUser.trim(),
                                    role = loadedRole,
                                    active = loadedActive,
                                    newPassword = newPassword.takeIf { it.isNotBlank() }
                                )
                            }
                                .onSuccess {
                                    updMsg = "Actualizado"
                                    newPassword = ""
                                }
                                .onFailure { updMsg = "Error: ${it.message}" }
                        }
                    }) { Text("Guardar cambios") }
                }

                if (updMsg.isNotEmpty()) Text(updMsg)
                Row {
                    Checkbox(checked = showRaw, onCheckedChange = { showRaw = it })
                    Text("Ver JSON")
                }
                if (showRaw && rawJson.isNotEmpty()) {
                    Text(rawJson, style = LocalTextStyle.current.copy(fontSize = 12.sp))
                }
            }
        }
    }
}
@Composable
fun RoleSelector(selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val roles = listOf("user", "admin")
    Box {
        OutlinedButton(onClick = { open = true }) {
            Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Rol: ${selected.uppercase()}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            roles.forEach { r ->
                DropdownMenuItem(onClick = { onSelect(r); open = false }) {
                    Text(r.uppercase())
                }
            }
        }
    }
}

