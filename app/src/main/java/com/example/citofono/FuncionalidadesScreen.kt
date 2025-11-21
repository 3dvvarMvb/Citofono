package com.example.citofono

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun FuncionalidadesScreen(
    onCall: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // --- Estados UI ---
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }

    // Contacts
    var depto by remember { mutableStateOf("101") }
    var contactsParsed by remember { mutableStateOf(listOf<ContactItem>()) }
    var contactsRaw by remember { mutableStateOf("") }
    var showContactsJson by remember { mutableStateOf(false) }

    // Calls
    var phone by remember { mutableStateOf("+56 9 3333 3333") }
    var callMessage by remember { mutableStateOf("") }
    var callRaw by remember { mutableStateOf("") }
    var showCallJson by remember { mutableStateOf(false) }

    // Auth
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("admin123") }
    var role by remember { mutableStateOf("admin") }
    var sessionId by remember { mutableStateOf("") }
    var authMessage by remember { mutableStateOf("") }
    var authRaw by remember { mutableStateOf("") }
    var showAuthJson by remember { mutableStateOf(false) }

    // Admin
    var adminMessage by remember { mutableStateOf("") }
    var adminRaw by remember { mutableStateOf("") }
    var showAdminJson by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // -------------------- CONTACTS --------------------
        Text("Contactos", style = MaterialTheme.typography.h6)
        OutlinedTextField(
            value = depto,
            onValueChange = { depto = it.uppercase() },
            label = { Text("Departamento (101, 402, 802D, etc.)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                errorText = ""; contactsParsed = emptyList(); contactsRaw = ""
                isLoading = true
                scope.launch {
                    try {
                        val arr: JSONArray = EsbApi.searchContactsByDepto(depto.trim(), limit = 10)
                        contactsParsed = parseContacts(arr)
                        contactsRaw = arr.toString(2)
                    } catch (e: Exception) {
                        errorText = "ERROR Contacts: ${e.message}"
                    } finally { isLoading = false }
                }
            }) { Text("Buscar por depto") }

            Button(onClick = {
                errorText = ""; contactsParsed = emptyList(); contactsRaw = ""
                isLoading = true
                scope.launch {
                    try {
                        val arr: JSONArray = EsbApi.searchContactsFallback(depto.trim(), limit = 10)
                        contactsParsed = parseContacts(arr)
                        contactsRaw = arr.toString(2)
                    } catch (e: Exception) {
                        errorText = "ERROR Contacts (fallback): ${e.message}"
                    } finally { isLoading = false }
                }
            }) { Text("Fallback (q)") }
        }

        if (contactsParsed.isNotEmpty()) {
            ContactsCardList(contactsParsed, onCall = onCall)
        }
        if (contactsRaw.isNotEmpty()) {
            Row {
                Checkbox(checked = showContactsJson, onCheckedChange = { showContactsJson = it })
                Text("Ver JSON")
            }
            if (showContactsJson) {
                Text(contactsRaw, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth())
            }
        }

        Divider()

        // -------------------- CALLS --------------------
        Text("Llamadas (registro)", style = MaterialTheme.typography.h6)
        OutlinedTextField(
            value = phone, onValueChange = { phone = it },
            label = { Text("Teléfono destino") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onCall(phone) }) { Text("Llamar (ACTION_CALL)") }
            Button(onClick = {
                errorText = ""; callMessage = ""; callRaw = ""
                isLoading = true
                scope.launch {
                    try {
                        val caller = SessionManager.username(context).ifBlank { "android-device" }
                        val resp = EsbApi.recordCall(
                            destination = phone.trim(),
                            status = "attempted",
                            durationSec = 5,
                            callerId = caller
                        )
                        callRaw = resp.toString(2)
                        val status = resp.optString("status", "desconocido")
                        val id = resp.optString("id").ifBlank { resp.optString("_id") }
                        callMessage = "Registro OK (status=$status, id=${id.ifBlank { "s/d" }})"
                    } catch (e: Exception) {
                        errorText = "ERROR Calls: ${e.message}"
                    } finally { isLoading = false }
                }
            }) { Text("Registrar llamada") }
        }
        if (callMessage.isNotEmpty()) Text(callMessage)
        if (callRaw.isNotEmpty()) {
            Row {
                Checkbox(checked = showCallJson, onCheckedChange = { showCallJson = it })
                Text("Ver JSON")
            }
            if (showCallJson) Text(callRaw, fontFamily = FontFamily.Monospace)
        }

        Divider()

        // -------------------- AUTH --------------------
        Text("Autenticación", style = MaterialTheme.typography.h6)
        OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(role, { role = it }, label = { Text("Role (user/admin)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                errorText = ""; authMessage = ""; authRaw = ""
                isLoading = true
                scope.launch {
                    try {
                        val resp = EsbApi.authCreateUser(username.trim(), password.trim(), role.trim())
                        authRaw = resp.toString(2)
                        val u = resp.optString("username", username)
                        val r = resp.optString("role", role)
                        authMessage = "Usuario creado: $u ($r)"
                    } catch (e: Exception) {
                        errorText = "ERROR Auth.create: ${e.message}"
                    } finally { isLoading = false }
                }
            }) { Text("Crear usuario") }

            Button(onClick = {
                errorText = ""; authMessage = ""; authRaw = ""
                isLoading = true
                scope.launch {
                    try {
                        val resp = EsbApi.authLogin(username.trim(), password.trim())
                        authRaw = resp.toString(2)
                        val sid = resp.optString("session_id", "")
                        if (sid.isNotEmpty()) sessionId = sid
                        val ok = resp.optString("status", "unknown")
                        authMessage = "Login: $ok  (session_id=${sessionId.ifBlank { "s/d" }})"
                    } catch (e: Exception) {
                        errorText = "ERROR Auth.login: ${e.message}"
                    } finally { isLoading = false }
                }
            }) { Text("Login") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = sessionId, onValueChange = { sessionId = it },
                label = { Text("session_id") }, singleLine = true, modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                errorText = ""; authMessage = ""; authRaw = ""
                isLoading = true
                scope.launch {
                    try {
                        val resp = EsbApi.authValidate(sessionId.trim())
                        authRaw = resp.toString(2)
                        val ok = resp.optString("valid", resp.optString("status", "unknown"))
                        authMessage = "Validate: $ok"
                    } catch (e: Exception) {
                        errorText = "ERROR Auth.validate: ${e.message}"
                    } finally { isLoading = false }
                }
            }) { Text("Validate") }

            Button(onClick = {
                errorText = ""; authMessage = ""; authRaw = ""
                isLoading = true
                scope.launch {
                    try {
                        val resp = EsbApi.authLogout(sessionId.trim())
                        authRaw = resp.toString(2)
                        val ok = resp.optString("status", "ok")
                        authMessage = "Logout: $ok"
                    } catch (e: Exception) {
                        errorText = "ERROR Auth.logout: ${e.message}"
                    } finally { isLoading = false }
                }
            }) { Text("Logout") }
        }

        if (authMessage.isNotEmpty()) Text(authMessage)
        if (authRaw.isNotEmpty()) {
            Row {
                Checkbox(checked = showAuthJson, onCheckedChange = { showAuthJson = it })
                Text("Ver JSON")
            }
            if (showAuthJson) Text(authRaw, fontFamily = FontFamily.Monospace)
        }

        Divider()

        // -------------------- ADMIN --------------------
        Text("Administración", style = MaterialTheme.typography.h6)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                errorText = ""; adminMessage = ""; adminRaw = ""
                isLoading = true
                scope.launch {
                    try {
                        val resp = EsbApi.adminVerify(username.trim())
                        adminRaw = resp.toString(2)
                        val isAdmin = resp.optBoolean("is_admin", false)
                        adminMessage = "¿Es admin? $isAdmin"
                    } catch (e: Exception) {
                        errorText = "ERROR Admin.verify: ${e.message}"
                    } finally { isLoading = false }
                }
            }) { Text("Verificar admin") }

            Button(onClick = {
                errorText = ""; adminMessage = ""; adminRaw = ""
                isLoading = true
                scope.launch {
                    try {
                        val resp = EsbApi.adminGetUser(username.trim())
                        adminRaw = resp.toString(2)
                        val u = resp.optString("username", username)
                        val r = resp.optString("role", "s/d")
                        val active = resp.optBoolean("active", true)
                        adminMessage = "User: $u | role=$r | active=$active"
                    } catch (e: Exception) {
                        errorText = "ERROR Admin.getUser: ${e.message}"
                    } finally { isLoading = false }
                }
            }) { Text("Get user") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                errorText = ""; adminMessage = ""; adminRaw = ""
                isLoading = true
                scope.launch {
                    try {
                        val resp = EsbApi.adminUpdateUser(username.trim(), role = role.trim())
                        adminRaw = resp.toString(2)
                        adminMessage = "Rol actualizado a $role"
                    } catch (e: Exception) {
                        errorText = "ERROR Admin.update(role): ${e.message}"
                    } finally { isLoading = false }
                }
            }) { Text("Set role") }

            Button(onClick = {
                errorText = ""; adminMessage = ""; adminRaw = ""
                isLoading = true
                scope.launch {
                    try {
                        val resp = EsbApi.adminUpdateUser(username.trim(), newPassword = password.trim())
                        adminRaw = resp.toString(2)
                        adminMessage = "Password actualizado"
                    } catch (e: Exception) {
                        errorText = "ERROR Admin.changePassword: ${e.message}"
                    } finally { isLoading = false }
                }
            }) { Text("Cambiar password") }

            Button(onClick = {
                errorText = ""; adminMessage = ""; adminRaw = ""
                isLoading = true
                scope.launch {
                    try {
                        val resp = EsbApi.adminDeleteUser(username.trim())
                        adminRaw = resp.toString(2)
                        adminMessage = "Usuario desactivado/borrado"
                    } catch (e: Exception) {
                        errorText = "ERROR Admin.delete: ${e.message}"
                    } finally { isLoading = false }
                }
            }) { Text("Desactivar usuario") }
        }

        if (adminMessage.isNotEmpty()) Text(adminMessage)
        if (adminRaw.isNotEmpty()) {
            Row {
                Checkbox(checked = showAdminJson, onCheckedChange = { showAdminJson = it })
                Text("Ver JSON")
            }
            if (showAdminJson) Text(adminRaw, fontFamily = FontFamily.Monospace)
        }

        if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (errorText.isNotEmpty()) Text(errorText, color = MaterialTheme.colors.error)
    }
}

// --------- Modelos y helpers de UI para contactos ---------

private data class ContactItem(
    val departamento: String,
    val nombre: String?,
    val telefonos: List<String>
)

private fun parseContacts(arr: JSONArray): List<ContactItem> {
    val out = mutableListOf<ContactItem>()
    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val depto = obj.optString("departamento", "s/d")
        val nombre = obj.optString("nombre", null)
        val telefonos = extractPhones(obj)
        out += ContactItem(departamento = depto, nombre = nombre, telefonos = telefonos)
    }
    return out
}

private fun extractPhones(obj: JSONObject): List<String> {
    val out = mutableListOf<String>()
    when (val raw = obj.opt("telefono")) {
        is JSONArray -> for (i in 0 until raw.length()) out += raw.optString(i)
        is String -> raw.split(",", ";", "/", "|", " ").map { it.trim() }
            .filter { it.isNotBlank() }.forEach { out += it }
    }
    return out.distinct()
}

@Composable
private fun ContactsCardList(items: List<ContactItem>, onCall: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { c ->
            Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Depto: ${c.departamento}", style = MaterialTheme.typography.subtitle1)
                    if (!c.nombre.isNullOrBlank()) Text("Nombre: ${c.nombre}")
                    if (c.telefonos.isNotEmpty()) {
                        Text("Teléfonos:")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            c.telefonos.take(3).forEach { t ->
                                OutlinedButton(onClick = { onCall(t) }) { Text(t) }
                            }
                        }
                        if (c.telefonos.size > 3) Text("… y ${c.telefonos.size - 3} más")
                    } else {
                        Text("Sin teléfonos", color = MaterialTheme.colors.error)
                    }
                }
            }
        }
    }
}

// ==================== PREVIEWS ====================

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Funcionalidades Screen")
@Composable
fun FuncionalidadesScreenPreview() {
    MaterialTheme {
        FuncionalidadesScreen(onCall = {})
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Contacts Card List")
@Composable
fun ContactsCardListPreview() {
    val sampleContacts = listOf(
        ContactItem(
            departamento = "101",
            nombre = "Juan Pérez",
            telefonos = listOf("+56912345678", "+56987654321")
        ),
        ContactItem(
            departamento = "202",
            nombre = "María González",
            telefonos = listOf("+56911111111")
        )
    )
    MaterialTheme {
        ContactsCardList(items = sampleContacts, onCall = {})
    }
}
