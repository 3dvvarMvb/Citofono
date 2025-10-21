package com.example.citofono

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.citofono.ui.theme.CitofonoTheme
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class ResolvedContact(val department: String, val phones: List<String>)

class MainActivity : ComponentActivity() {

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private var pendingPhoneNumber: String? = null
    private var pendingDepartment: String? = null
    private var searchQuery by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Guard: si no hay sesión válida, vuelve a Auth
        val sid = SessionManager.sessionId(this)
        if (sid.isBlank()) {
            startActivity(Intent(this, AuthActivity::class.java)); finish(); return
        }

        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                val num = pendingPhoneNumber
                val dep = pendingDepartment
                pendingPhoneNumber = null
                pendingDepartment = null
                if (num != null) makeCall(num, dep)
            } else {
                Toast.makeText(this, "Permiso denegado para realizar llamadas", Toast.LENGTH_SHORT).show()
            }
        }

        setContent {
            CitofonoTheme {
                var selectedTab by remember { mutableStateOf(0) } // 0: Marcador, 1: Mensajería (placeholder)

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Citófono") },
                            actions = {
                                TextButton(onClick = { SessionManager.logoutAndGoToLogin(this@MainActivity) }) {
                                    Text("Salir", color = MaterialTheme.colors.onPrimary)
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Marcador") })
                            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Mensajería") })
                        }
                        Box(Modifier.fillMaxSize()) {
                            when (selectedTab) {
                                0 -> SearchScreen(
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { searchQuery = it },
                                    resolveDepto = { depto -> resolveDeptoToContact(depto) },
                                    onCallClick = { phoneNumber, department ->
                                        makeCall(phoneNumber, department)
                                        searchQuery = ""
                                    }
                                )
                                1 -> MensajeriaPlaceholder()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // si sesión cayó, vuelve a Auth
        if (!SessionManager.isLoggedIn(this)) {
            startActivity(Intent(this, AuthActivity::class.java)); finish()
        }
        searchQuery = ""
    }

    private fun logoutAndExit() {
        val sid = SessionManager.sessionId(this)
        SessionManager.clear(this)
        lifecycleScope.launch {
            runCatching { EsbApi.authLogout(sid) } // best effort
        }
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }

    // -------- Resolución de depto --------
    private suspend fun resolveDeptoToContact(depto: String): ResolvedContact? {
        val primary = try { EsbApi.searchContactsByDepto(depto.trim(), limit = 5) } catch (_: Exception) { JSONArray() }
        val target = if (primary.length() > 0) primary else runCatching {
            EsbApi.searchContactsFallback(depto.trim(), limit = 5)
        }.getOrElse { JSONArray() }

        if (target.length() == 0) return null
        val c0 = target.getJSONObject(0)
        val department = c0.optString("departamento", depto)
        val phones = extractPhones(c0)
        return if (phones.isEmpty()) null else ResolvedContact(department, phones)
    }

    private fun extractPhones(obj: JSONObject): List<String> {
        val out = mutableListOf<String>()
        when (val raw = obj.opt("telefono")) {
            is org.json.JSONArray -> for (i in 0 until raw.length()) out += raw.optString(i)
            is String -> raw.split(",", ";", "/", "|", " ")
                .map { it.trim() }.filter { it.isNotBlank() }.forEach { out += it }
        }
        return out.distinct()
    }

    // -------- Llamadas --------
    private fun makeCall(phoneNumber: String, department: String? = null) {
        val final = if (phoneNumber.startsWith("+")) phoneNumber else "+56$phoneNumber"

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            lifecycleScope.launch {
                runCatching {
                    EsbApi.recordCall(
                        destination = final,
                        status = "attempted",
                        durationSec = 5,
                        callerId = "android-device",
                        depto = department
                    )
                }
            }
            val intent = Intent(Intent.ACTION_CALL).apply { data = ("tel:$final").toUri() }
            startActivity(intent)
        } else {
            pendingPhoneNumber = final
            pendingDepartment = department
            requestPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }
}

@Composable
private fun MensajeriaPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Mensajería irá aquí (equipo de tu compañero).")
    }
}
