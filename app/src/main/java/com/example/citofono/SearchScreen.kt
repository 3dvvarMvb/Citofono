package com.example.citofono

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// ----------------- UI: Teclado -----------------
@Composable
fun NumericKeyboard(onKeyClick: (String) -> Unit) {
    val numberKeys = listOf("1","2","3","4","5","6","7","8","9")
    val letterKeys = listOf("A","B","C","D")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        numberKeys.chunked(3).forEach { rowKeys ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rowKeys.forEach { key ->
                    Button(
                        onClick = { onKeyClick(key) },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .shadow(4.dp, RoundedCornerShape(50))
                    ) {
                        Text(text = key, style = MaterialTheme.typography.h3, color = Color.Black)
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onKeyClick("0") },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .shadow(4.dp, RoundedCornerShape(50))
            ) { Text("0", style = MaterialTheme.typography.h3, color = Color.Black) }
            Spacer(Modifier.weight(1f))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            letterKeys.forEach { key ->
                Button(
                    onClick = { onKeyClick(key) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .shadow(4.dp, RoundedCornerShape(50))
                ) { Text(key, style = MaterialTheme.typography.h4, color = Color.Black) }
            }
        }
    }
}

@Composable
fun ResponsiveKeyboardBox(onKeyClick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f)
            .wrapContentHeight()
            .padding(8.dp)
    ) {
        NumericKeyboard(onKeyClick = onKeyClick)
    }
}

// ----------------- Pantalla de búsqueda -----------------
@Composable
fun SearchScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    resolveDepto: suspend (String) -> ResolvedContact?, // <- suspend
    onCallClick: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var selectedPhoneNumbers by remember { mutableStateOf(listOf<String>()) }
    var selectedDepartment by remember { mutableStateOf("") }
    var selectedPhoneNumber by remember { mutableStateOf("") }
    var selectedPhoneIndex by remember { mutableStateOf(-1) }
    var departmentNotFound by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(departmentNotFound) {
        if (departmentNotFound) {
            snackbarHostState.showSnackbar("DEPTO NO ENCONTRADO", duration = SnackbarDuration.Short)
            departmentNotFound = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SnackbarHost(hostState = snackbarHostState)

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("Buscar Departamento (ej: 802D)") },
                textStyle = MaterialTheme.typography.h4,
                modifier = Modifier.weight(1f).height(80.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onSearchQueryChange("") },
                modifier = Modifier.height(56.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)
            ) { Icon(Icons.Default.Delete, contentDescription = "Borrar") }
        }

        Spacer(modifier = Modifier.height(16.dp))
        ResponsiveKeyboardBox(onKeyClick = { key -> onSearchQueryChange(searchQuery + key) })
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (searchQuery.isBlank()) {
                    departmentNotFound = true
                    return@Button
                }
                scope.launch {
                    val result = resolveDepto(searchQuery)
                    if (result == null || result.phones.isEmpty()) {
                        departmentNotFound = true
                        return@launch
                    }
                    selectedDepartment = result.department
                    selectedPhoneNumbers = result.phones
                    if (result.phones.size == 1) {
                        // 👉 ahora delega todo (incluido el registro) a makeCall
                        onCallClick(result.phones.first(), result.department)
                    } else {
                        showDialog = true
                    }
                }
            },
            modifier = Modifier.padding(8.dp).fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50))
        ) {
            Icon(Icons.Default.Phone, contentDescription = "Llamar")
            Spacer(Modifier.width(8.dp))
            Text("Llamar", style = MaterialTheme.typography.h5, textAlign = TextAlign.Center)
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Seleccionar Teléfono", style = MaterialTheme.typography.h6) },
                text = {
                    Column {
                        Text("¿A qué número desea llamar?", style = MaterialTheme.typography.subtitle1)
                        selectedPhoneNumbers.forEachIndexed { index, phone ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPhoneIndex = index
                                        selectedPhoneNumber = phone
                                    }
                                    .padding(8.dp)
                            ) {
                                RadioButton(
                                    selected = selectedPhoneIndex == index,
                                    onClick = {
                                        selectedPhoneIndex = index
                                        selectedPhoneNumber = phone
                                    }
                                )
                                Text(text = "Teléfono ${index + 1}: $phone", style = MaterialTheme.typography.body1)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (selectedPhoneIndex >= 0) onCallClick(selectedPhoneNumber, selectedDepartment)
                        showDialog = false
                        selectedPhoneNumber = ""
                        selectedPhoneIndex = -1
                    }) { Text("Llamar") }
                },
                dismissButton = { Button(onClick = { showDialog = false }) { Text("Cancelar") } }
            )
        }
    }
}
