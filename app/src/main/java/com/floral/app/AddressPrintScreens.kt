package com.floral.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.floral.app.data.Order

private val PrintForest = Color(0xFF173C2B)
private val PrintLeaf = Color(0xFFA8C9A7)
private val PrintCream = Color(0xFFF3EAD2)
private val PrintPanel = Color(0xFF142D20)
private val PrintMuted = Color(0xFFB5C5B8)

@Composable
fun PrintAddresses(orders: List<Order>, fromAddress: String, onBack: () -> Unit, onGenerate: (List<Order>) -> Unit) {
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val chosen = orders.filter { it.id in selected }
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Print addresses", style = MaterialTheme.typography.headlineSmall, color = PrintCream)
                Text("Prepare parcel address pages", style = MaterialTheme.typography.bodySmall, color = PrintMuted)
            }
            TextButton(onClick = onBack) { Text("Back", color = PrintLeaf) }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = PrintForest),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("${chosen.size} selected", color = PrintCream, style = MaterialTheme.typography.titleMedium)
                    Text("Orders are printed in their saved list order.", color = PrintLeaf, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(
                    onClick = {
                        selected = if (selected.size == orders.size) emptySet() else orders.map { it.id }.toSet()
                    }
                ) {
                    Text(if (selected.size == orders.size && orders.isNotEmpty()) "Clear all" else "Select all", color = PrintCream)
                }
            }
        }
        if (chosen.any { it.includeFromAddress } && fromAddress.isBlank()) {
            Text(
                "Some selected orders request a FROM address, but none is saved in Settings. Those pages will show TO details only.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (chosen.any { it.address.isNullOrBlank() }) {
            Text(
                "Some selected orders have no delivery address. Check those orders before printing.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (orders.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = PrintPanel), shape = RoundedCornerShape(16.dp)) {
                Text("No orders available yet.", Modifier.padding(18.dp), color = PrintMuted)
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(orders, key = { it.id }) { order ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PrintPanel),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    order.plantName?.takeIf { it.isNotBlank() } ?: "Unnamed plant",
                                    color = PrintCream,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(order.customerName?.takeIf { it.isNotBlank() } ?: "Customer name missing", color = PrintMuted, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (order.includeFromAddress && fromAddress.isNotBlank()) "FROM + TO address" else "TO address only",
                                    color = PrintLeaf,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Checkbox(
                                checked = order.id in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + order.id else selected - order.id
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = PrintLeaf,
                                    uncheckedColor = PrintMuted,
                                    checkmarkColor = PrintForest
                                )
                            )
                        }
                    }
                }
            }
        }
        Button(
            onClick = { onGenerate(chosen) },
            enabled = chosen.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrintLeaf,
                contentColor = Color(0xFF0B1B13),
                disabledContainerColor = PrintPanel,
                disabledContentColor = PrintMuted
            )
        ) {
            Text(if (chosen.isEmpty()) "Select orders to preview" else "Preview PDF · ${chosen.size} orders")
        }
    }
}

@Composable
fun SenderAddressSettings(current: String, onSave: (String) -> Unit) {
    var draft by remember(current) { mutableStateOf(current) }
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, color = PrintCream)
        Text("Default FROM address", style = MaterialTheme.typography.titleMedium, color = PrintLeaf)
        Text(
            "Paste the complete sender address exactly as you want it printed, including name, phone number, PIN code and all address lines.",
            color = PrintMuted
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            minLines = 5,
            maxLines = 12,
            placeholder = { Text("Paste complete sender address here") }
        )
        Button(
            onClick = { onSave(draft) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PrintLeaf, contentColor = Color(0xFF0B1B13))
        ) { Text("Save sender address") }
    }
}
