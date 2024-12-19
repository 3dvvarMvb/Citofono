// ImportContactsActivity.kt
package com.example.citofono

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.citofono.ui.theme.CitofonoTheme
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.BufferedReader

class ImportContactsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CitofonoTheme {
                ImportContactsScreen()
            }
        }
    }

    @Composable
    fun ImportContactsScreen() {
        val context = LocalContext.current
        var importStatus by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Importar Contactos", style = MaterialTheme.typography.h4)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                val contacts = loadContactsFromCsv(context)
                val resultIntent = Intent().apply {
                    putParcelableArrayListExtra("contacts", ArrayList(contacts))
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }) {
                Text(text = "Importar")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = importStatus)
        }
    }

    private fun loadContactsFromCsv(context: Context): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val file = File(context.filesDir, "contactos.csv")
        if (file.exists()) {
            val inputStream = FileInputStream(file)
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.useLines { lines ->
                lines.forEach { line ->
                    val tokens = line.split(";")
                    if (tokens.size >= 4) {
                        val phoneNumber = listOf(tokens[1], tokens[2])
                        val contact = Contact(
                            id = contacts.size,
                            name = tokens[0],
                            phoneNumber = phoneNumber.filter { it.isNotBlank() },
                            department = tokens[0]
                        )
                        contacts.add(contact)
                    }
                }
            }
        }
        return contacts
    }
}
#comentario para hacer el push Martuko weko
