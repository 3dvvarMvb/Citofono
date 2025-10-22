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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.tooling.preview.Preview
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
                            backgroundColor = customColor2,
                            contentColor = Color.White,
                            actions = {
                                TextButton(onClick = {
                                    SessionManager.logoutAndGoToLogin(this@AdminActivity)
                                }) { Text("Salir", color = customColor) }
                            }
                        )
                    },
                    bottomBar = {
                        BottomNavigation(
                            backgroundColor = customColor2,
                            contentColor = Color.White
                        ) {
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
                                    onClick = { section = item },
                                    selectedContentColor = Color.White,
                                    unselectedContentColor = Color.White.copy(alpha = 0.6f)
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
            Text("Gestión de Contactos", style = MaterialTheme.typography.h6, color = customColor)

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

    var allItems by remember { mutableStateOf(listOf<CallLog>()) }
    var items by remember { mutableStateOf(listOf<CallLog>()) }

    // Carga inicial
    LaunchedEffect(Unit) {
        busy = true
        runCatching {
            // Cargar todas las llamadas sin filtro de fecha (últimos 30 días para no saturar)
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            EsbApi.callsList(
                query = null,
                fromMillis = thirtyDaysAgo,
                toMillis = System.currentTimeMillis(),
                limit = 1000
            )
        }.onSuccess { allItems = it }
         .onFailure {
             Toast.makeText(ctx, "No se pudo cargar: ${it.message}", Toast.LENGTH_LONG).show()
             allItems = emptyList()
         }
        busy = false
    }

    // Filtrar localmente cuando cambian los filtros
    LaunchedEffect(q, range, allItems) {
        // Obtener la fecha/hora actual en la zona horaria local
        val now = System.currentTimeMillis()
        val (from, to) = when (range) {
            "HOY" -> dayBounds(now)
            "SEMANA" -> weekBounds(now)
            else -> 0L to Long.MAX_VALUE
        }

        // Debug: Log para verificar rangos
        android.util.Log.d("RegistroLlamadas", "Rango: $range")
        android.util.Log.d("RegistroLlamadas", "From: ${formatDate(from)} ${formatTime(from)} ($from)")
        android.util.Log.d("RegistroLlamadas", "To: ${formatDate(to)} ${formatTime(to)} ($to)")
        android.util.Log.d("RegistroLlamadas", "Total items: ${allItems.size}")

        // Filtrar por rango de fecha
        val filteredByDate = if (range == "TODO") {
            allItems
        } else {
            allItems.filter { call ->
                val inRange = call.tsMillis in from..to
                // Debug: Log para las primeras 5 llamadas
                if (allItems.indexOf(call) < 5) {
                    android.util.Log.d("RegistroLlamadas",
                        "Call ${call.id}: ${formatDate(call.tsMillis)} ${formatTime(call.tsMillis)} " +
                        "(${call.tsMillis}) - In range: $inRange")
                }
                inRange
            }
        }

        android.util.Log.d("RegistroLlamadas", "Filtered by date: ${filteredByDate.size}")

        // Filtrar por búsqueda de texto
        items = if (q.isBlank()) {
            filteredByDate
        } else {
            val query = q.trim().lowercase()
            filteredByDate.filter { call ->
                call.caller?.lowercase()?.contains(query) == true ||
                call.depto?.lowercase()?.contains(query) == true ||
                call.destination?.lowercase()?.contains(query) == true
            }
        }

        android.util.Log.d("RegistroLlamadas", "Final items: ${items.size}")
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
                    runCatching {
                        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                        EsbApi.callsList(
                            query = null,
                            fromMillis = thirtyDaysAgo,
                            toMillis = System.currentTimeMillis(),
                            limit = 1000
                        )
                    }.onSuccess { allItems = it }
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
        TabRow(selectedTabIndex = tabIndex, backgroundColor = customColor2,) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = tabIndex == idx,
                    onClick = { tabIndex = idx; range = tabs[idx] },
                    text = { Text(label,color=customColor) }
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

private fun colorForMessageStatus(deliveryStatus: String, readStatus: String): Color = when {
    readStatus.lowercase() == "leido" -> Color(0xFF2E7D32) // verde - mensaje leído
    deliveryStatus.lowercase() == "enviado" -> Color(0xFF1976D2) // azul - mensaje enviado pero no leído
    deliveryStatus.lowercase() == "pendiente" -> Color(0xFFF9A825) // ámbar - pendiente
    deliveryStatus.lowercase() == "fallido" || deliveryStatus.lowercase() == "error" -> Color(0xFFC62828) // rojo - error
    else -> Color(0xFF546E7A) // gris - desconocido
}

private fun dayBounds(nowMs: Long): Pair<Long, Long> {
    // Obtener la zona horaria del sistema
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getDefault()).apply {
        timeInMillis = nowMs
    }
    // Establecer al inicio del día (00:00:00.000)
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    val start = cal.timeInMillis

    // Establecer al final del día (23:59:59.999)
    cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
    cal.set(java.util.Calendar.MINUTE, 59)
    cal.set(java.util.Calendar.SECOND, 59)
    cal.set(java.util.Calendar.MILLISECOND, 999)
    val end = cal.timeInMillis

    return start to end
}

private fun weekBounds(nowMs: Long): Pair<Long, Long> {
    // Obtener la zona horaria del sistema
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getDefault()).apply {
        timeInMillis = nowMs
    }
    // Configurar para que la semana empiece en lunes
    cal.firstDayOfWeek = java.util.Calendar.MONDAY

    // Retroceder al lunes de esta semana
    cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    val start = cal.timeInMillis

    // Avanzar al domingo de esta semana (final del día)
    cal.add(java.util.Calendar.DAY_OF_YEAR, 6) // +6 días desde lunes = domingo
    cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
    cal.set(java.util.Calendar.MINUTE, 59)
    cal.set(java.util.Calendar.SECOND, 59)
    cal.set(java.util.Calendar.MILLISECOND, 999)
    val end = cal.timeInMillis

    return start to end
}

@Composable
private fun RegistroMensajeriaScreen() {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // Estado para todos los mensajes sin procesar
    var allMessages by remember { mutableStateOf<List<MessageRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    // Estado para el chat seleccionado
    var selectedChat by remember { mutableStateOf<ChatPair?>(null) }

    // Obtener lista de chats únicos (pares de usuarios)
    val chatPairs = remember(allMessages) {
        extractUniqueChatPairs(allMessages)
    }

    // Cargar mensajes al inicio
    LaunchedEffect(Unit) {
        loading = true
        errorMsg = ""
        scope.launch {
            runCatching { EsbApi.messagesList(limit = 1000) }
                .onSuccess { jsonArray ->
                    val messages = mutableListOf<MessageRecord>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)

                        // Parsear la fecha ISO o usar timestamp
                        val timestamp = if (obj.has("fecha")) {
                            // Intentar parsear la fecha ISO
                            try {
                                val fechaStr = obj.optString("fecha", "")
                                val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault())
                                formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                formatter.parse(fechaStr)?.time ?: 0L
                            } catch (e: Exception) {
                                obj.optLong("timestamp", 0L)
                            }
                        } else {
                            obj.optLong("timestamp", 0L)
                        }

                        // Parsear sender y receiver (pueden ser ObjectId strings o usernames)
                        val senderId = obj.optString("sender", "?")
                        val receiverId = obj.optString("receiver", "?")

                        messages.add(
                            MessageRecord(
                                id = obj.optString("_id", obj.optString("id", "")),
                                sender = senderId,
                                receiver = receiverId,
                                text = obj.optString("mensaje", obj.optString("message", obj.optString("text", ""))),
                                timestamp = timestamp,
                                deliveryStatus = obj.optString("deliveryStatus", "unknown"),
                                readStatus = obj.optString("readStatus", "unknown")
                            )
                        )
                    }
                    allMessages = messages.sortedBy { it.timestamp }
                }
                .onFailure {
                    errorMsg = "Error al cargar mensajes: ${it.message}"
                }
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Registro de Mensajería", style = MaterialTheme.typography.h6)

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        if (errorMsg.isNotEmpty()) {
            Text(errorMsg, color = MaterialTheme.colors.error)
        }

        if (!loading && selectedChat == null) {
            // Mostrar listado de chats
            Text(
                "Chats disponibles (${chatPairs.size})",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.SemiBold
            )

            if (chatPairs.isEmpty() && !loading) {
                Text("No hay mensajes registrados", color = Color.Gray)
            }

            chatPairs.forEach { chatPair ->
                Card(
                    elevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedChat = chatPair }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Chat entre: ${chatPair.user1} ↔ ${chatPair.user2}",
                                style = MaterialTheme.typography.body1,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${chatPair.messageCount} mensajes",
                                style = MaterialTheme.typography.caption,
                                color = Color.Gray
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Ver chat",
                            tint = customColor
                        )
                    }
                }
            }
        }

        // Mostrar mensajes del chat seleccionado
        selectedChat?.let { chat ->
            Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Chat: ${chat.user1} ↔ ${chat.user2}",
                                style = MaterialTheme.typography.subtitle1,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${chat.messageCount} mensajes",
                                style = MaterialTheme.typography.caption,
                                color = Color.Gray
                            )
                        }
                        IconButton(onClick = { selectedChat = null }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = customColor
                            )
                        }
                    }

                    Divider(Modifier.padding(vertical = 8.dp))

                    // Tabla de mensajes
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(customColor2)
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        HCell("Fecha", 0.7f)
                        HCell("Hora", 0.5f)
                        HCell("De", 0.9f)
                        HCell("Para", 0.9f)
                        HCell("Mensaje", 1.8f)
                        HCell("Estado", 0.7f)
                    }
                    Divider()

                    // Filtrar mensajes de este chat
                    val chatMessages = allMessages.filter {
                        (it.sender == chat.user1 && it.receiver == chat.user2) ||
                        (it.sender == chat.user2 && it.receiver == chat.user1)
                    }.sortedBy { it.timestamp }

                    chatMessages.forEach { msg ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Cell(formatDate(msg.timestamp), 0.7f)
                            Cell(formatTime(msg.timestamp), 0.5f)
                            Cell(msg.sender.takeLast(8), 0.9f) // Mostrar últimos 8 chars del ID
                            Cell(msg.receiver.takeLast(8), 0.9f)
                            Cell(msg.text, 1.8f)
                            Row(Modifier.weight(0.7f), verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(colorForMessageStatus(msg.deliveryStatus, msg.readStatus))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (msg.readStatus == "leido") "Leído"
                                    else msg.deliveryStatus.capitalize(),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Divider()
                    }
                }
            }
        }
    }
}

// Estructura para representar un mensaje
private data class MessageRecord(
    val id: String,
    val sender: String,
    val receiver: String,
    val text: String,
    val timestamp: Long,
    val deliveryStatus: String = "unknown",
    val readStatus: String = "unknown"
)

// Estructura para representar un par de chat único
private data class ChatPair(
    val user1: String,
    val user2: String,
    val messageCount: Int
)

// Extraer pares únicos de usuarios que han intercambiado mensajes
private fun extractUniqueChatPairs(messages: List<MessageRecord>): List<ChatPair> {
    val pairMap = mutableMapOf<String, Int>()

    messages.forEach { msg ->
        // Ordenar alfabéticamente para evitar duplicados (user1-user2 == user2-user1)
        val sortedPair = listOf(msg.sender, msg.receiver).sorted()
        val key = "${sortedPair[0]}|${sortedPair[1]}"
        pairMap[key] = (pairMap[key] ?: 0) + 1
    }

    return pairMap.map { (key, count) ->
        val users = key.split("|")
        ChatPair(users[0], users[1], count)
    }.sortedByDescending { it.messageCount }
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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
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
                    },
                        colors=ButtonDefaults.buttonColors(backgroundColor = customColor2)
                        
                        ) { Text("Crear",color=customColor) }
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
                    },colors=ButtonDefaults.buttonColors(backgroundColor = customColor2)

                        ) { Text("Cargar", color=customColor) }

                    Button(onClick = { // Desactivar
                        if (qUser.isBlank()) return@Button
                        updMsg = ""
                        scope.launch {
                            runCatching { EsbApi.adminDeleteUser(qUser.trim()) }
                                .onSuccess { updMsg = "Usuario desactivado" }
                                .onFailure { updMsg = "Error: ${it.message}" }
                        }
                    },
                        colors=ButtonDefaults.buttonColors(backgroundColor = customColor2)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, tint=customColor)
                        Spacer(Modifier.width(6.dp))
                        Text("Desactivar", color=customColor)
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
                    },colors=ButtonDefaults.buttonColors(backgroundColor = customColor2)

                        ) { Text("Guardar cambios", color=customColor) }
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
            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint=customColor2)
            Spacer(Modifier.width(6.dp))
            Text("Rol: ${selected.uppercase()}", color = customColor2)
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

// ==================== PREVIEWS ====================

@Preview(showBackground = true, name = "Selector de Rol")
@Composable
fun RoleSelectorPreview() {
    CitofonoTheme {
        RoleSelector(selected = "admin", onSelect = {})
    }
}

@Preview(showBackground = true, name = "Status Dot")
@Composable
private fun StatusDotPreview() {
    CitofonoTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            StatusDot(color = Color.Green, sizeDp = 10)
            StatusDot(color = Color.Red, sizeDp = 10)
            StatusDot(color = Color.Yellow, sizeDp = 10)
        }
    }
}

@Preview(showBackground = true, name = "Import/Export Screen")
@Composable
private fun ImportExportScreenPreview() {
    CitofonoTheme {
        ImportExportScreen(
            onImportFile = { _, _, _ -> },
            onExportXlsx = {}
        )
    }
}

@Preview(showBackground = true, name = "Registro Llamadas Screen")
@Composable
private fun RegistroLlamadasScreenPreview() {
    CitofonoTheme {
        RegistroLlamadasScreen()
    }
}

@Preview(showBackground = true, name = "Registro Mensajería Screen")
@Composable
private fun RegistroMensajeriaScreenPreview() {
    CitofonoTheme {
        RegistroMensajeriaScreen()
    }
}

@Preview(showBackground = true, name = "Usuarios Screen")
@Composable
fun UsuariosScreenPreview() {
    CitofonoTheme {
        UsuariosScreen()
    }
}

@Preview(showBackground = true, name = "TopAppBar Admin")
@Composable
private fun AdminTopAppBarPreview() {
    CitofonoTheme {
        TopAppBar(
            title = { Text("Consola Administrador") },
            backgroundColor = customColor2,
            contentColor = Color.White,
            actions = {
                TextButton(onClick = { }) {
                    Text("Salir", color = customColor)
                }
            }
        )
    }
}

@Preview(showBackground = true, name = "BottomNavigation Admin")
@Composable
private fun AdminBottomNavigationPreview() {
    CitofonoTheme {
        var selectedIndex by remember { mutableStateOf(0) }
        val sections = listOf(
            AdminSection.ImportExport,
            AdminSection.RegLlamadas,
            AdminSection.RegMsg,
            AdminSection.Usuarios
        )

        BottomNavigation(
            backgroundColor = customColor2,
            contentColor = Color.White
        ) {
            sections.forEachIndexed { index, item ->
                BottomNavigationItem(
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label, fontSize = 11.sp) },
                    selected = selectedIndex == index,
                    onClick = { selectedIndex = index },
                    selectedContentColor = Color.White,
                    unselectedContentColor = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Admin Screen Completa")
@Composable
private fun AdminScreenCompletePreview() {
    CitofonoTheme {
        var selectedIndex by remember { mutableStateOf(0) }
        val sections = listOf(
            AdminSection.ImportExport,
            AdminSection.RegLlamadas,
            AdminSection.RegMsg,
            AdminSection.Usuarios
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Consola Administrador") },
                    backgroundColor = customColor2,
                    contentColor = Color.White,
                    actions = {
                        TextButton(onClick = { }) {
                            Text("Salir", color = customColor)
                        }
                    }
                )
            },
            bottomBar = {
                BottomNavigation(
                    backgroundColor = customColor2,
                    contentColor = Color.White
                ) {
                    sections.forEachIndexed { index, item ->
                        BottomNavigationItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, fontSize = 11.sp) },
                            selected = selectedIndex == index,
                            onClick = { selectedIndex = index },
                            selectedContentColor = Color.White,
                            unselectedContentColor = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Contenido de la sección: ${sections[selectedIndex].label}")
            }
        }
    }
}

