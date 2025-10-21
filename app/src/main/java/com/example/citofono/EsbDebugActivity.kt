package com.example.citofono

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray

class EsbDebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EsbDebugScreen() }
    }
}

@Composable
fun EsbDebugScreen() {
    val scope = rememberCoroutineScope()

    var depto by remember { mutableStateOf("101") }
    var phone by remember { mutableStateOf("+56 9 3333 3333") }

    // Auth/Admin state
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("admin123") }
    var role by remember { mutableStateOf("admin") }
    var sessionId by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }

    var contactsResult by remember { mutableStateOf("") }
    var callResult by remember { mutableStateOf("") }
    var authResult by remember { mutableStateOf("") }
    var adminResult by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ESB Debug") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // -------------------- CONTACTS --------------------
            Text("1) Contacts/search")
            OutlinedTextField(
                value = depto,
                onValueChange = { depto = it.uppercase() },
                label = { Text("Departamento (101, 402, etc.)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    errorText = ""; contactsResult = ""
                    isLoading = true
                    scope.launch {
                        try {
                            val arr: JSONArray = EsbApi.searchContactsByDepto(depto.trim(), limit = 10)
                            contactsResult = arr.toString(2)
                        } catch (e: Exception) {
                            errorText = "ERROR Contacts: ${e.message}"
                        } finally { isLoading = false }
                    }
                }) { Text("Buscar (search)") }

                Button(onClick = {
                    errorText = ""; contactsResult = ""
                    isLoading = true
                    scope.launch {
                        try {
                            val arr: JSONArray = EsbApi.searchContactsFallback(depto.trim(), limit = 10)
                            contactsResult = arr.toString(2)
                        } catch (e: Exception) {
                            errorText = "ERROR Contacts (fallback): ${e.message}"
                        } finally { isLoading = false }
                    }
                }) { Text("Fallback (q)") }
            }

            if (contactsResult.isNotEmpty()) {
                Text("Respuesta Contacts:", style = MaterialTheme.typography.subtitle1)
                Text(contactsResult, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth().padding(8.dp))
            }

            Divider()

            // -------------------- CALLS --------------------
            Text("2) Calls/record")
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Teléfono destino") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                errorText = ""; callResult = ""
                isLoading = true
                scope.launch {
                    try {
                        val resp = EsbApi.recordCall(
                            destination = phone.trim(),
                            status = "attempted",
                            durationSec = 5,
                            callerId = "android-device",
                            depto = depto.trim() // ← ahora se envía
                        )
                        callResult = resp.toString(2)
                    } catch (e: Exception) {
                        errorText = "ERROR Calls: ${e.message}"
                    } finally { isLoading = false }
                }
            }) { Text("Registrar llamada") }

            if (callResult.isNotEmpty()) {
                Text("Respuesta Calls:", style = MaterialTheme.typography.subtitle1)
                Text(callResult, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth().padding(8.dp))
            }

            Divider()

            // -------------------- AUTH --------------------
            Text("3) Autenticación")
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = role,
                onValueChange = { role = it },
                label = { Text("Role (user/admin)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    errorText = ""; authResult = ""
                    isLoading = true
                    scope.launch {
                        try {
                            val resp = EsbApi.authCreateUser(username.trim(), password.trim(), role.trim())
                            authResult = resp.toString(2)
                        } catch (e: Exception) {
                            errorText = "ERROR Auth.create: ${e.message}"
                        } finally { isLoading = false }
                    }
                }) { Text("Crear usuario") }

                Button(onClick = {
                    errorText = ""; authResult = ""
                    isLoading = true
                    scope.launch {
                        try {
                            val resp = EsbApi.authLogin(username.trim(), password.trim())
                            authResult = resp.toString(2)
                            val sid = resp.optString("session_id", "")
                            if (sid.isNotEmpty()) sessionId = sid
                        } catch (e: Exception) {
                            errorText = "ERROR Auth.login: ${e.message}"
                        } finally { isLoading = false }
                    }
                }) { Text("Login") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = sessionId,
                    onValueChange = { sessionId = it },
                    label = { Text("session_id") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    errorText = ""; authResult = ""
                    isLoading = true
                    scope.launch {
                        try {
                            val resp = EsbApi.authValidate(sessionId.trim())
                            authResult = resp.toString(2)
                        } catch (e: Exception) {
                            errorText = "ERROR Auth.validate: ${e.message}"
                        } finally { isLoading = false }
                    }
                }) { Text("Validate") }

                Button(onClick = {
                    errorText = ""; authResult = ""
                    isLoading = true
                    scope.launch {
                        try {
                            val resp = EsbApi.authLogout(sessionId.trim())
                            authResult = resp.toString(2)
                        } catch (e: Exception) {
                            errorText = "ERROR Auth.logout: ${e.message}"
                        } finally { isLoading = false }
                    }
                }) { Text("Logout") }
            }

            if (authResult.isNotEmpty()) {
                Text("Respuesta Auth:", style = MaterialTheme.typography.subtitle1)
                Text(authResult, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth().padding(8.dp))
            }

            Divider()

            // -------------------- ADMIN --------------------
            Text("4) Administración")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    errorText = ""; adminResult = ""
                    isLoading = true
                    scope.launch {
                        try {
                            val resp = EsbApi.adminVerify(username.trim())
                            adminResult = resp.toString(2)
                        } catch (e: Exception) {
                            errorText = "ERROR Admin.verify: ${e.message}"
                        } finally { isLoading = false }
                    }
                }) { Text("Verificar admin") }

                Button(onClick = {
                    errorText = ""; adminResult = ""
                    isLoading = true
                    scope.launch {
                        try {
                            val resp = EsbApi.adminGetUser(username.trim())
                            adminResult = resp.toString(2)
                        } catch (e: Exception) {
                            errorText = "ERROR Admin.getUser: ${e.message}"
                        } finally { isLoading = false }
                    }
                }) { Text("Get user") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    errorText = ""; adminResult = ""
                    isLoading = true
                    scope.launch {
                        try {
                            val resp = EsbApi.adminUpdateUser(username.trim(), role = role.trim())
                            adminResult = resp.toString(2)
                        } catch (e: Exception) {
                            errorText = "ERROR Admin.update(role): ${e.message}"
                        } finally { isLoading = false }
                    }
                }) { Text("Set role") }

                Button(onClick = {
                    errorText = ""; adminResult = ""
                    isLoading = true
                    scope.launch {
                        try {
                            val resp = EsbApi.adminUpdateUser(username.trim(), newPassword = password.trim())
                            adminResult = resp.toString(2)
                        } catch (e: Exception) {
                            errorText = "ERROR Admin.changePassword: ${e.message}"
                        } finally { isLoading = false }
                    }
                }) { Text("Cambiar password") }

                Button(onClick = {
                    errorText = ""; adminResult = ""
                    isLoading = true
                    scope.launch {
                        try {
                            val resp = EsbApi.adminDeleteUser(username.trim())
                            adminResult = resp.toString(2)
                        } catch (e: Exception) {
                            errorText = "ERROR Admin.delete: ${e.message}"
                        } finally { isLoading = false }
                    }
                }) { Text("Desactivar usuario") }
            }

            if (adminResult.isNotEmpty()) {
                Text("Respuesta Admin:", style = MaterialTheme.typography.subtitle1)
                Text(adminResult, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth().padding(8.dp))
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (errorText.isNotEmpty()) {
                Text(errorText, color = MaterialTheme.colors.error)
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "- Auth: create_user, authenticate_user, validate_session, logout.\n" +
                "- Admin: verify_admin, get_user_info, admin_update_user, admin_delete_user.\n" +
                "- Calls: record (con depto)."
            )
        }
    }
}
