package com.floral.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.floral.app.data.Order

@Composable
fun PrintAddresses(orders: List<Order>, fromAddress: String, onBack: () -> Unit, onGenerate: (List<Order>) -> Unit) {
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val chosen = orders.filter { it.id in selected }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Print addresses", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text("Select orders in the order they appear in your saved order list. Each order controls its own FROM address.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${chosen.size} selected")
            TextButton(onClick = { selected = if (selected.size == orders.size) emptySet() else orders.map { it.id }.toSet() }) {
                Text(if (selected.size == orders.size && orders.isNotEmpty()) "Clear all" else "Select all")
            }
        }
        if (chosen.any { it.includeFromAddress } && fromAddress.isBlank()) {
            Text("Some selected orders request a FROM address, but none is saved in Settings. Those labels will print without FROM details.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (chosen.any { it.address.isNullOrBlank() }) {
            Text("Some selected orders have no delivery address. Check those orders before printing.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (orders.isEmpty()) Text("No orders available.")
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(orders, key = { it.id }) { order ->
                Card {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(order.plantName?.takeIf { it.isNotBlank() } ?: "Unnamed plant")
                            Text(order.customerName ?: "Customer name missing", style = MaterialTheme.typography.bodySmall)
                            Text(if (order.includeFromAddress && fromAddress.isNotBlank()) "FROM address included" else "TO address only", style = MaterialTheme.typography.labelSmall)
                        }
                        Checkbox(checked = order.id in selected, onCheckedChange = { checked -> selected = if (checked) selected + order.id else selected - order.id })
                    }
                }
            }
        }
        Button(onClick = { onGenerate(chosen) }, enabled = chosen.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("Generate A4 PDF · ${chosen.size} labels")
        }
    }
}

@Composable
fun SenderAddressSettings(current: String, onSave: (String) -> Unit) {
    var draft by remember(current) { mutableStateOf(current) }
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text("Default FROM address", style = MaterialTheme.typography.titleMedium)
        Text("Paste the complete sender address exactly as you want it printed, including name, phone number, PIN code and all address lines. Floral will preserve the text as entered.")
        OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth().weight(1f, fill = false), minLines = 5, maxLines = 12, placeholder = { Text("Paste complete sender address here") })
        Button(onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth()) { Text("Save sender address") }
    }
}
