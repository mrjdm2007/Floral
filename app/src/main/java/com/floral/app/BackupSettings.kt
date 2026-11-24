package com.floral.app

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.floral.app.data.FloralDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupSettings(currentAddress: String, db: FloralDatabase, onSaveAddress: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var address by remember(currentAddress) { mutableStateOf(currentAddress) }
    val scope = rememberCoroutineScope()
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { DataBackup.export(context, db, uri) } }
                .onSuccess { Toast.makeText(context, "Backup created successfully.", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(context, it.message ?: "Backup failed.", Toast.LENGTH_LONG).show() }
        }
    }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val confirmed = android.app.AlertDialog.Builder(context)
                .setTitle("Restore Floral backup?")
                .setMessage("Restoring replaces the current Floral records and app-managed photos with the contents of this backup. Export a backup of the current data first if you may need it.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore") { _, _ ->
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { DataBackup.restore(context, uri) } }
                            .onSuccess {
                                Toast.makeText(context, "Backup restored. Reloading Floral…", Toast.LENGTH_LONG).show()
                                (context as? Activity)?.recreate()
                            }
                            .onFailure { Toast.makeText(context, it.message ?: "Restore failed.", Toast.LENGTH_LONG).show() }
                    }
                }.create()
            confirmed.show()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFF3EAD2))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xCC1D3B2D)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Default FROM address", style = MaterialTheme.typography.titleMedium, color = Color(0xFFA8C9A7))
                Text("Paste the complete sender address exactly as you want it printed, including name, phone number, PIN code and all address lines.", color = Color(0xFFB5C5B8))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    placeholder = { Text("Paste complete sender address here") }
                )
                Button(
                    onClick = { onSaveAddress(address) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA8C9A7), contentColor = Color(0xFF0B1B13))
                ) { Text("Save sender address") }
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xCC1D3B2D)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Data backup", style = MaterialTheme.typography.titleLarge, color = Color(0xFFF3EAD2))
                Text("Create a ZIP backup of your orders, nursery profiles, plant and stock records, saved sender address, and every linked photo. Keep a copy somewhere safe.", color = Color(0xFFB5C5B8))
                Button(
                    onClick = { exportPicker.launch("Floral-backup.zip") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA8C9A7), contentColor = Color(0xFF0B1B13))
                ) { Text("Create backup") }
                OutlinedButton(
                    onClick = { importPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Restore from backup") }
                Text(
                    "Floral copies linked photos into the backup and restores them into app-managed storage. If a photo permission has expired, the backup will stop and tell you which record needs its photo selected again.",
                    color = Color(0xFFB5C5B8),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Text(
            "Floral copies linked photos into the backup and restores them into app-managed storage. If a photo permission has expired, the backup will stop and tell you which record needs its photo selected again.",
            color = Color(0xFFB5C5B8),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
