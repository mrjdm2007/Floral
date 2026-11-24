package com.floral.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.floral.app.data.Customer
import com.floral.app.data.Order
import com.floral.app.data.OrderPlant

private val CustomerCream = Color(0xFFF3EAD2)
private val CustomerLeaf = Color(0xFFA8C9A7)
private val CustomerMuted = Color(0xFFB5C5B8)
private val CustomerGlass = Color(0xCC1D3B2D)
private val CustomerBorder = Color.White.copy(alpha = 0.10f)

@Composable
fun CustomerManagementScreen(
    customers: List<Customer>,
    orders: List<Order>,
    plants: List<OrderPlant>,
    onSave: (Customer) -> Unit,
    onDelete: (Customer) -> Unit,
    openOrder: (Order) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Customer?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Customer?>(null) }

    val filtered = customers.filter { customer ->
        query.isBlank() || listOf(customer.name, customer.phone, customer.address, customer.notes)
            .any { it.contains(query, ignoreCase = true) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Customers", color = CustomerCream, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("${customers.size} saved customer profile(s)", color = CustomerMuted, fontSize = 11.sp)
            }
            FilledTonalButton(
                onClick = { editing = null; creating = true },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = CustomerLeaf, contentColor = Color(0xFF0B1B13))
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search name, phone, address or notes") }
        )
        if (filtered.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CustomerGlass),
                border = androidx.compose.foundation.BorderStroke(1.dp, CustomerBorder),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Default.Person, null, tint = CustomerLeaf)
                    Text(if (customers.isEmpty()) "No customers saved yet" else "No matching customers", color = CustomerCream, fontWeight = FontWeight.SemiBold)
                    Text("Save customer contact details, delivery address and notes here. Existing order customers are imported into this list automatically.", color = CustomerMuted, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
                items(filtered, key = { it.id }) { customer ->
                    val digits = customer.phone.filter(Char::isDigit)
                    val linkedOrders = orders.filter { order ->
                        if (digits.isNotBlank()) order.phone.orEmpty().filter(Char::isDigit) == digits
                        else order.customerName.orEmpty().trim().equals(customer.name.trim(), ignoreCase = true)
                    }.sortedByDescending { it.createdAt }
                    val unpaid = linkedOrders.sumOf { order ->
                        plants.filter { it.orderId == order.id && !it.isPaid }
                            .sumOf { (it.finalPrice ?: it.sellingPrice) ?: 0.0 }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CustomerGlass),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CustomerBorder),
                        shape = RoundedCornerShape(19.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null, tint = CustomerLeaf)
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(customer.name, color = CustomerCream, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text(customer.phone.ifBlank { "No phone number" }, color = CustomerMuted, fontSize = 11.sp)
                                }
                                IconButton(onClick = { editing = customer; creating = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit customer", tint = CustomerLeaf)
                                }
                                TextButton(onClick = { deleteTarget = customer }) { Text("Delete", color = CustomerMuted, fontSize = 11.sp) }
                            }
                            if (customer.address.isNotBlank()) Text(customer.address, color = CustomerMuted, fontSize = 12.sp)
                            if (customer.notes.isNotBlank()) Text(customer.notes, color = CustomerMuted, fontSize = 11.sp)
                            HorizontalDivider(color = CustomerBorder)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${linkedOrders.size} order(s)", color = CustomerLeaf, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                if (unpaid > 0.0) Text("Unpaid ${customerMoney(unpaid)}", color = Color(0xFFFFD98A), fontSize = 11.sp)
                                else Text("No unpaid plant balance", color = CustomerMuted, fontSize = 10.sp)
                            }
                            linkedOrders.take(3).forEach { order ->
                                TextButton(onClick = { openOrder(order) }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 1.dp)) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(plants.filter { it.orderId == order.id }.joinToString(", ") { it.name }
                                            .ifBlank { order.plantName?.takeIf { it.isNotBlank() } ?: "Plant order" },
                                            color = CustomerCream, fontSize = 12.sp)
                                        Text("${customerDate(order.createdAt)} · ${if (order.isReceived) "Received" else "Not received"}",
                                            color = CustomerMuted, fontSize = 10.sp)
                                    }
                                }
                            }
                            if (linkedOrders.size > 3) Text("+ ${linkedOrders.size - 3} more order(s)", color = CustomerMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        CustomerEditorDialog(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { customer -> onSave(customer); creating = false; editing = null }
        )
    }
    deleteTarget?.let { customer ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete customer profile?") },
            text = { Text("This removes the saved profile and its notes. Existing orders will not be deleted.") },
            confirmButton = {
                TextButton(onClick = { onDelete(customer); deleteTarget = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CustomerEditorDialog(initial: Customer?, onDismiss: () -> Unit, onSave: (Customer) -> Unit) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var phone by remember(initial?.id) { mutableStateOf(initial?.phone.orEmpty()) }
    var address by remember(initial?.id) { mutableStateOf(initial?.address.orEmpty()) }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    val valid = name.isNotBlank() && (phone.isBlank() || phone.count { it.isDigit() } >= 7)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add customer" else "Edit customer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Customer name *") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone number") }, singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text("Default delivery address") }, minLines = 2, maxLines = 4)
                OutlinedTextField(notes, { notes = it }, label = { Text("Customer notes") }, minLines = 2, maxLines = 4)
                if (!valid) Text("Enter a name and, if provided, a valid phone number.", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val now = System.currentTimeMillis()
                onSave(Customer(
                    id = initial?.id ?: 0,
                    name = name.trim(),
                    phone = phone.trim(),
                    address = address.trim(),
                    notes = notes.trim(),
                    createdAt = initial?.createdAt ?: now,
                    updatedAt = now
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun customerDate(timestamp: Long): String =
    java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(timestamp))

private fun customerMoney(value: Double): String =
    "₹" + String.format(java.util.Locale("en", "IN"), "%,.2f", value)
