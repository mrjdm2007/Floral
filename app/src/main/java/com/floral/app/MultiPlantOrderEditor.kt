package com.floral.app

import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.floral.app.data.Order
import com.floral.app.data.OrderPlant
import android.content.Context

private val Forest = Color(0xFF173C2B)
private val Deep = Color(0xFF0B1B13)
private val Panel = Color(0xFF142D20)
private val Leaf = Color(0xFFA8C9A7)
private val Cream = Color(0xFFF3EAD2)
private val Muted = Color(0xFFB5C5B8)

data class PlantEntryDraft(
    val name: String = "",
    val cost: String = "",
    val selling: String = "",
    val finalPrice: String = "",
    val paid: Boolean = false
)

@Composable
fun MultiPlantOrderEditor(
    order: Order?,
    existingPlants: List<OrderPlant>,
    cancel: () -> Unit,
    save: (Order, List<PlantEntryDraft>) -> Unit
) {
    var customer by remember(order?.id) { mutableStateOf(order?.customerName.orEmpty()) }
    var phone by remember(order?.id) { mutableStateOf(order?.phone.orEmpty()) }
    var address by remember(order?.id) { mutableStateOf(order?.address.orEmpty()) }
    var photo by remember(order?.id) { mutableStateOf(order?.photoUri.orEmpty()) }
    val plants = remember(order?.id) {
        androidx.compose.runtime.mutableStateListOf<PlantEntryDraft>().apply {
            if (existingPlants.isNotEmpty()) addAll(existingPlants.map {
                PlantEntryDraft(it.name, it.plantCost?.toString().orEmpty(), it.sellingPrice?.toString().orEmpty(),
                    it.finalPrice?.toString().orEmpty(), it.isPaid)
            }) else add(PlantEntryDraft(order?.plantName.orEmpty(), order?.plantCost?.toString().orEmpty(),
                order?.sellingPrice?.toString().orEmpty(), order?.finalPrice?.toString().orEmpty()))
        }
    }
    var courier by remember(order?.id) { mutableStateOf(order?.courierCharge?.toString().orEmpty()) }
    var expected by remember(order?.id) { mutableStateOf(order?.expectedDispatchAt) }
    var dispatched by remember(order?.id) { mutableStateOf(order?.dispatchedAt) }
    var notes by remember(order?.id) { mutableStateOf(order?.notes.orEmpty()) }
    var includeFrom by remember(order?.id) { mutableStateOf(order?.includeFromAddress ?: false) }
    val context = LocalContext.current
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            photo = uri.toString()
        }
    }
    val first = plants.firstOrNull() ?: PlantEntryDraft()
    val missing = buildList {
        if (customer.isBlank()) add("Customer name")
        if (phone.isBlank()) add("Phone number")
        if (address.isBlank()) add("Delivery address")
        if (plants.none { it.name.isNotBlank() }) add("At least one plant name")
        if (plants.any { it.name.isNotBlank() && it.cost.toDoubleOrNull() == null }) add("Plant costs")
        if (plants.any { it.name.isNotBlank() && it.selling.toDoubleOrNull() == null }) add("Plant selling prices")
        if (courier.toDoubleOrNull() == null) add("Courier charges")
        if (expected == null) add("Expected dispatch date")
    }

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())
            .padding(top = 14.dp).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (order == null) "New order" else "Edit order", color = Cream, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = cancel) { Text("Cancel") }
        }
        Text("Save one customer order with one or more plants.", color = Leaf, fontSize = 12.sp)
        OrderField("Customer name", customer) { customer = it }
        OrderField("Phone number", phone, true, KeyboardType.Phone) { phone = it }
        OrderField("Delivery address", address, false) { address = it }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Plants in this order", color = Cream, fontWeight = FontWeight.SemiBold)
            FilledTonalButton(onClick = { plants.add(PlantEntryDraft()) }) { Icon(Icons.Default.Add, null); Text("Add plant") }
        }
        plants.forEachIndexed { index, plant ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(15.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Plant ${index + 1}", color = Leaf, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (plants.size > 1) IconButton(onClick = { plants.removeAt(index) }) {
                            Icon(Icons.Default.DeleteOutline, null, tint = Muted)
                        }
                    }
                    OrderField("Plant name", plant.name) { plants[index] = plants[index].copy(name = it) }
                    OrderField("Plant cost (₹)", plant.cost, true, KeyboardType.Decimal) { plants[index] = plants[index].copy(cost = it) }
                    OrderField("Selling price (₹)", plant.selling, true, KeyboardType.Decimal) { plants[index] = plants[index].copy(selling = it) }
                    OrderField("Final agreed price (₹)", plant.finalPrice, true, KeyboardType.Decimal) { plants[index] = plants[index].copy(finalPrice = it) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(plant.paid, { plants[index] = plants[index].copy(paid = it) },
                            colors = CheckboxDefaults.colors(checkedColor = Leaf))
                        Text("Paid", color = if (plant.paid) Leaf else Cream, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Text("Order photograph (optional)", color = Cream, fontWeight = FontWeight.SemiBold)
        if (photo.isNotBlank()) AsyncImage(Uri.parse(photo), null, Modifier.fillMaxWidth().heightIn(max = 170.dp))
        OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Image, null); Text(if (photo.isBlank()) "Choose order photo" else "Change order photo")
        }
        if (photo.isNotBlank()) TextButton(onClick = { photo = "" }) { Text("Remove photo") }
        OrderField("Courier charges (₹)", courier, true, KeyboardType.Decimal) { courier = it }
        Text("Dispatch details", color = Cream, fontWeight = FontWeight.SemiBold)
        OrderDateField("Expected dispatch date", expected) { expected = it }
        OrderDateField("Actual dispatch date", dispatched) { dispatched = it }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(includeFrom, { includeFrom = it })
            Text("Include saved FROM address on parcel label", color = Cream, fontSize = 13.sp)
        }
        OrderField("Notes / remarks for this order", notes, false) { notes = it }
        Card(colors = CardDefaults.cardColors(containerColor = Forest), shape = RoundedCornerShape(17.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Still to be entered · ${missing.size}", color = Cream, fontWeight = FontWeight.SemiBold)
                if (missing.isEmpty()) Text("Everything is filled in.", color = Leaf, fontSize = 12.sp)
                missing.forEach { Text("• $it", color = Leaf, fontSize = 12.sp) }
            }
        }
        Button(onClick = {
            val primary = plants.firstOrNull() ?: PlantEntryDraft()
            val namedPlants = plants.filter { it.name.isNotBlank() }
            val totalCost = if (namedPlants.isNotEmpty() && namedPlants.all { it.cost.toDoubleOrNull() != null }) namedPlants.sumOf { it.cost.toDouble() } else null
            val totalSelling = if (namedPlants.isNotEmpty() && namedPlants.all { it.selling.toDoubleOrNull() != null }) namedPlants.sumOf { it.selling.toDouble() } else null
            val totalFinal = if (namedPlants.isNotEmpty() && namedPlants.all { it.finalPrice.toDoubleOrNull() != null }) namedPlants.sumOf { it.finalPrice.toDouble() } else null
            save(Order(
                id = order?.id ?: 0, customerName = customer.trim().ifBlank { null },
                phone = phone.trim().ifBlank { null }, address = address.trim().ifBlank { null },
                plantName = primary.name.trim().ifBlank { null }, photoUri = photo.ifBlank { null },
                plantCost = totalCost, sellingPrice = totalSelling,
                finalPrice = totalFinal, courierCharge = courier.toDoubleOrNull(),
                expectedDispatchAt = expected, dispatchedAt = dispatched, isReceived = order?.isReceived ?: false,
                notes = notes, includeFromAddress = includeFrom, createdAt = order?.createdAt ?: System.currentTimeMillis()
            ), plants.toList())
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Leaf, contentColor = Deep)) {
            Text("Save order", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OrderField(label: String, value: String, single: Boolean = true,
    type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
        singleLine = single, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = type),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Cream, unfocusedTextColor = Cream,
            focusedBorderColor = Leaf, unfocusedBorderColor = Muted.copy(alpha = .5f),
            focusedLabelColor = Leaf, unfocusedLabelColor = Muted, cursorColor = Leaf))
}

@Composable
private fun OrderDateField(label: String, value: Long?, change: (Long?) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = {
            val c = java.util.Calendar.getInstance().apply { if (value != null) timeInMillis = value }
            android.app.DatePickerDialog(context, { _, year, month, day ->
                change(java.util.Calendar.getInstance().apply {
                    set(year, month, day, 12, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis)
            }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.CalendarMonth, null)
            Spacer(Modifier.width(7.dp))
            Text(if (value == null) label else "$label: " + java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(value)))
        }
        if (value != null) TextButton(onClick = { change(null) }) { Text("Clear") }
    }
}
