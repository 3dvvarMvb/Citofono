package com.example.citofono

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.citofono.ui.theme.CitofonoTheme
import kotlinx.coroutines.launch

class AuthActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CitofonoTheme {
                val scope = rememberCoroutineScope()
                var username by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                var error by remember { mutableStateOf("") }
                var busy by remember { mutableStateOf(false) }
                // Autologin si hay sesión válida
                LaunchedEffect(Unit) {
                    val skip = intent.getBooleanExtra("skip_auto_login", false)
                    if (skip) return@LaunchedEffect

                    val sid = SessionManager.sessionId(this@AuthActivity)
                    if (sid.isNotBlank()) {
                        busy = true
                        runCatching { EsbApi.authValidate(sid) }
                            .onSuccess { v ->
                                val isValid = v.optString("status") == "valid" || v.optBoolean("valid", false)
                                val role = v.optJSONObject("user")?.optString("role")
                                    ?: v.optString("role", SessionManager.role(this@AuthActivity))
                                if (isValid) {
                                    goToByRole(role)
                                } else {
                                    SessionManager.clear(this@AuthActivity)
                                }
                            }
                            .onFailure {
                                SessionManager.clear(this@AuthActivity)
                            }
                        busy = false
                    }
                }

                Box(Modifier.fillMaxSize()) {
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
                        Text("Autenticación", style = MaterialTheme.typography.h3, color = customColor)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = username, onValueChange = { username = it },
                            label = { Text("Usuario", color = customColor) },
                            singleLine = true, modifier = Modifier.fillMaxWidth().height(64.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password, onValueChange = { password = it },
                            label = { Text("Contraseña", color = customColor) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().height(64.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (username.isBlank() || password.isBlank()) {
                                    error = "Ingrese usuario y contraseña"
                                    return@Button
                                }
                                error = ""
                                busy = true
                                scope.launch {
                                    runCatching { EsbApi.authLogin(username.trim(), password.trim()) }
                                        .onSuccess { resp ->
                                            val status = resp.optString("status", "error")
                                            val sid = resp.optString("session_id", "").ifBlank { resp.optString("token", "") }
                                            val role = resp.optString("role", resp.optString("userType", "user"))
                                            val user = resp.optString("username", resp.optString("userId", username))

                                            if ((status.equals("ok", true) || status.equals("authenticated", true)) && sid.isNotBlank()) {
                                                SessionManager.save(this@AuthActivity, sid, user, role)
                                                goToByRole(role)
                                            } else {
                                                error = if (resp.optString("message").isNotBlank())
                                                    resp.optString("message")
                                                else
                                                    "Credenciales inválidas"
                                            }

                                        }
                                        .onFailure { error = "Error de login: ${it.message}" }
                                    busy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = customColor2)
                        ) { Text("Ingresar", color = customColor) }

                        if (error.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(error, color = MaterialTheme.colors.error)
                        }
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

    private fun goToByRole(role: String) {
        val next = if (role.equals("admin", ignoreCase = true))
            Intent(this, AdminActivity::class.java)
        else
            Intent(this, MainActivity::class.java)

        startActivity(next)
        finish()
        Toast.makeText(this, "Sesión iniciada como $role", Toast.LENGTH_SHORT).show()
    }
}
