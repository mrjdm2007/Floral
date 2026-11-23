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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.floral.app.data.Order
import com.floral.app.data.OrderPlant
import android.content.Context

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
        if (first.cost.toDoubleOrNull() == null) add("First plant cost")
        if (first.selling.toDoubleOrNull() == null) add("First plant selling price")
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
        Field("Customer name", customer) { customer = it }
        Field("Phone number", phone, true, KeyboardType.Phone) { phone = it }
        Field("Delivery address", address, false) { address = it }
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
                    Field("Plant name", plant.name) { plants[index] = plants[index].copy(name = it) }
                    Field("Plant cost (₹)", plant.cost, true, KeyboardType.Decimal) { plants[index] = plants[index].copy(cost = it) }
                    Field("Selling price (₹)", plant.selling, true, KeyboardType.Decimal) { plants[index] = plants[index].copy(selling = it) }
                    Field("Final agreed price (₹)", plant.finalPrice, true, KeyboardType.Decimal) { plants[index] = plants[index].copy(finalPrice = it) }
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
        Field("Courier charges (₹)", courier, true, KeyboardType.Decimal) { courier = it }
        Text("Dispatch details", color = Cream, fontWeight = FontWeight.SemiBold)
        DateField("Expected dispatch date", expected) { expected = it }
        DateField("Actual dispatch date", dispatched) { dispatched = it }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(includeFrom, { includeFrom = it })
            Text("Include saved FROM address on parcel label", color = Cream, fontSize = 13.sp)
        }
        Field("Notes / remarks for this order", notes, false) { notes = it }
        Card(colors = CardDefaults.cardColors(containerColor = Forest), shape = RoundedCornerShape(17.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Still to be entered · ${missing.size}", color = Cream, fontWeight = FontWeight.SemiBold)
                if (missing.isEmpty()) Text("Everything is filled in.", color = Leaf, fontSize = 12.sp)
                missing.forEach { Text("• $it", color = Leaf, fontSize = 12.sp) }
            }
        }
        Button(onClick = {
            val primary = plants.firstOrNull() ?: PlantEntryDraft()
            save(Order(
                id = order?.id ?: 0, customerName = customer.trim().ifBlank { null },
                phone = phone.trim().ifBlank { null }, address = address.trim().ifBlank { null },
                plantName = primary.name.trim().ifBlank { null }, photoUri = photo.ifBlank { null },
                plantCost = primary.cost.toDoubleOrNull(), sellingPrice = primary.selling.toDoubleOrNull(),
                finalPrice = primary.finalPrice.toDoubleOrNull(), courierCharge = courier.toDoubleOrNull(),
                expectedDispatchAt = expected, dispatchedAt = dispatched, isReceived = order?.isReceived ?: false,
                notes = notes, includeFromAddress = includeFrom, createdAt = order?.createdAt ?: System.currentTimeMillis()
            ), plants.toList())
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Leaf, contentColor = Deep)) {
            Text("Save order", fontWeight = FontWeight.SemiBold)
        }
    }
}
