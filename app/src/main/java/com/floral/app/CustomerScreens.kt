package com.floral.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
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
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CustomerLeaf) },
            placeholder = { Text("Search customers") }
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
                        shape = RoundedCornerShape(21.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
            existingCustomers = customers,
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
private fun CustomerEditorDialog(
    initial: Customer?,
    existingCustomers: List<Customer>,
    onDismiss: () -> Unit,
    onSave: (Customer) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var phone by remember(initial?.id) { mutableStateOf(initial?.phone.orEmpty()) }
    var address by remember(initial?.id) { mutableStateOf(initial?.address.orEmpty()) }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    var confirmAnyway by remember(initial?.id) { mutableStateOf(false) }
    val valid = name.isNotBlank() && (phone.isBlank() || phone.count { it.isDigit() } >= 7)
    val matches = remember(name, phone, existingCustomers, initial?.id) {
        val normalizedPhone = customerPhoneKey(phone)
        existingCustomers.filter { candidate ->
            candidate.id != initial?.id && (
                (normalizedPhone.isNotBlank() && normalizedPhone == customerPhoneKey(candidate.phone)) ||
                customerNamesSimilar(name, candidate.name)
            )
        }.sortedByDescending { candidate ->
            if (normalizedPhone.isNotBlank() && normalizedPhone == customerPhoneKey(candidate.phone)) 2 else 1
        }.take(4)
    }
    val hasMatches = matches.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (initial == null) "New customer" else "Edit customer", fontWeight = FontWeight.SemiBold)
                Text("Customer details", color = CustomerMuted, style = MaterialTheme.typography.labelMedium)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(name, { name = it; confirmAnyway = false },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Customer name *") },
                    singleLine = true, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(phone, { phone = it; confirmAnyway = false },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Phone number") },
                    singleLine = true, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(address, { address = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Default delivery address") },
                    minLines = 2, maxLines = 4, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(notes, { notes = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Notes") },
                    minLines = 2, maxLines = 4, shape = RoundedCornerShape(14.dp))
                if (!valid) Text("Enter a customer name and a valid phone number (if provided).",
                    color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                if (hasMatches) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3020)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD98A).copy(alpha = 0.38f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFD98A))
                                Text("Possible duplicate found", color = Color(0xFFFFD98A), fontWeight = FontWeight.SemiBold)
                            }
                            Text("A customer with a similar name or matching phone number already exists. Check before saving.",
                                color = CustomerCream, fontSize = 12.sp)
                            matches.forEach { match ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(match.name, color = CustomerCream, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text(listOf(match.phone, match.address).filter { it.isNotBlank() }.joinToString(" · ")
                                        .ifBlank { "No phone or address saved" }, color = CustomerMuted, fontSize = 11.sp)
                                }
                                if (match != matches.last()) HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                            }
                            Text("If this is a different person, such as a family member sharing a number, you can still save it.",
                                color = CustomerMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(
                    enabled = valid && (!hasMatches || confirmAnyway),
                    onClick = {
                        val now = System.currentTimeMillis()
                        onSave(Customer(
                            id = initial?.id ?: 0, name = name.trim(), phone = phone.trim(),
                            address = address.trim(), notes = notes.trim(),
                            createdAt = initial?.createdAt ?: now, updatedAt = now
                        ))
                    }
                ) { Text(if (hasMatches) "Save anyway" else "Save customer") }
                if (hasMatches && !confirmAnyway && valid) {
                    TextButton(onClick = { confirmAnyway = true }) { Text("This is a different customer") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun customerPhoneKey(value: String): String {
    val digits = value.filter(Char::isDigit)
    return if (digits.length > 10) digits.takeLast(10) else digits
}

private fun customerNameKey(value: String): String =
    value.lowercase(java.util.Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

private fun customerNamesSimilar(first: String, second: String): Boolean {
    val a = customerNameKey(first)
    val b = customerNameKey(second)
    if (a.length < 4 || b.length < 4) return false
    if (a == b) return true
    if (a.contains(b) || b.contains(a)) return minOf(a.length, b.length) >= 5
    val at = a.split(" ").filter { it.length >= 3 }.toSet()
    val bt = b.split(" ").filter { it.length >= 3 }.toSet()
    if (at.intersect(bt).isNotEmpty()) return true
    if (kotlin.math.abs(a.length - b.length) > 2) return false
    return customerEditDistance(a, b) <= if (maxOf(a.length, b.length) >= 8) 2 else 1
}

private fun customerEditDistance(a: String, b: String): Int {
    var previous = IntArray(b.length + 1) { it }
    for (i in a.indices) {
        val current = IntArray(b.length + 1)
        current[0] = i + 1
        for (j in b.indices) {
            current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1,
                previous[j] + if (a[i] == b[j]) 0 else 1)
        }
        previous = current
    }
    return previous[b.length]
}

private fun customerDate(timestamp: Long): String =
    java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(timestamp))

private fun customerMoney(value: Double): String =
    "₹" + String.format(java.util.Locale("en", "IN"), "%,.2f", value)
