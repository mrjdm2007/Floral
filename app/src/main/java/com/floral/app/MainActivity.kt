package com.floral.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.floral.app.data.*
import com.floral.app.notifications.DispatchReminder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val Forest = Color(0xFF173C2B)
private val Deep = Color(0xFF0D2118)
private val Leaf = Color(0xFF9BC7A6)
private val Cream = Color(0xFFF3EAD2)

class MainActivity : ComponentActivity() {
 private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
  val dao = FloralDatabase.get(this).orders()
  setContent {
   val orders by dao.observeAll().collectAsStateWithLifecycle(emptyList())
   var page by remember { mutableStateOf("Overview") }
   var editing by remember { mutableStateOf<Order?>(null) }
   var editorOpen by remember { mutableStateOf(false) }
   MaterialTheme(colorScheme = darkColorScheme(primary = Leaf, secondary = Cream, background = Deep, surface = Forest)) {
    Surface(Modifier.fillMaxSize(), color = Deep) {
     if (editorOpen) {
      OrderEditor(editing, onCancel = { editorOpen = false; editing = null }) { order ->
       lifecycleScope.launch {
        val id = if (order.id == 0L) dao.insert(order) else { dao.update(order); order.id }
        if (order.dispatchedAt != null) DispatchReminder.cancel(this@MainActivity, id)
        else DispatchReminder.schedule(this@MainActivity, id, order.expectedDispatchAt)
        editorOpen = false; editing = null
       }
      }
     } else {
      Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
       Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
         Text("Floral", color = Cream, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
         Text("PLANT BUSINESS MANAGER", color = Leaf, fontSize = 10.sp, letterSpacing = 2.sp)
        }
        FilledIconButton(onClick = { editing = null; editorOpen = true }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Forest, contentColor = Cream)) { Icon(Icons.Default.Add, "Add order") }
       }
       Spacer(Modifier.height(18.dp))
       Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf("Overview", "Orders", "Finances").forEach { tab ->
         FilterChip(selected = page == tab, onClick = { page = tab }, label = { Text(tab) })
        }
       }
       Spacer(Modifier.height(12.dp))
       when (page) {
        "Overview" -> Overview(orders)
        "Finances" -> Finance(orders)
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
         items(orders, key = { it.id }) { order ->
          OrderCard(order, onReceived = { checked -> lifecycleScope.launch { dao.setReceived(order.id, checked) } },
           onOpen = { editing = order; editorOpen = true })
         }
        }
       }
      }
     }
    }
   }
  }
 }
}

@Composable private fun Overview(orders: List<Order>) {
 val now = Calendar.getInstance()
 fun matches(order: Order, period: Int): Boolean {
  val c = Calendar.getInstance().apply { timeInMillis = order.dispatchedAt ?: order.createdAt }
  return when (period) {
   Calendar.WEEK_OF_YEAR -> c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.WEEK_OF_YEAR) == now.get(Calendar.WEEK_OF_YEAR)
   Calendar.MONTH -> c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.MONTH) == now.get(Calendar.MONTH)
   else -> c.get(Calendar.YEAR) == now.get(Calendar.YEAR)
  }
 }
 val profitOrders = orders.filter { it.profit != null }
 Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
  Card(colors = CardDefaults.cardColors(containerColor = Forest), shape = RoundedCornerShape(22.dp)) {
   Column(Modifier.fillMaxWidth().padding(20.dp)) {
    Text("OVERALL RECORDED PROFIT", color = Leaf, fontSize = 11.sp, letterSpacing = 1.sp)
    Text("₹" + "%.2f".format(profitOrders.sumOf { it.profit ?: 0.0 }), color = Cream, fontSize = 32.sp, fontWeight = FontWeight.Bold)
    Text(profitOrders.size.toString() + " financially complete orders", color = Leaf, fontSize = 12.sp)
   }
  }
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   listOf("Week" to Calendar.WEEK_OF_YEAR, "Month" to Calendar.MONTH, "Year" to Calendar.YEAR).forEach { (label, period) ->
    val value = profitOrders.filter { matches(it, period) }.sumOf { it.profit ?: 0.0 }
    Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Forest)) {
     Column(Modifier.padding(11.dp)) { Text(label, color = Leaf, fontSize = 11.sp); Text("₹" + "%.0f".format(value), color = Cream, fontWeight = FontWeight.SemiBold) }
    }
   }
  }
  Text("Recent orders", color = Cream, fontWeight = FontWeight.SemiBold)
  orders.take(5).forEach { OrderCard(it, {}, {}) }
 }
}
@Composable private fun Finance(orders: List<Order>) {
 val value = orders.mapNotNull { it.totalValue }.sum()
 val profit = orders.mapNotNull { it.profit }.sum()
 Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
  Text("Financial overview", color = Cream, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
  Metric("Total order value (selling price + courier)", value)
  Metric("Recorded profit", profit)
  Text("Profit is shown only when plant cost, selling price and courier charges are entered. Courier charges are treated as a business expense.", color = Leaf, fontSize = 12.sp)
  orders.forEach { o -> Text((o.plantName ?: "Unnamed order") + " · " + (o.profit?.let { "₹" + "%.2f".format(it) } ?: "Profit pending"), color = Cream) }
 }
}
@Composable private fun Metric(label: String, amount: Double) {
 Card(colors = CardDefaults.cardColors(containerColor = Forest)) {
  Column(Modifier.fillMaxWidth().padding(16.dp)) { Text(label, color = Leaf, fontSize = 12.sp); Text("₹" + "%.2f".format(amount), color = Cream, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
 }
}
@Composable private fun OrderCard(order: Order, onReceived: (Boolean) -> Unit, onOpen: () -> Unit) {
 Card(colors = CardDefaults.cardColors(containerColor = Forest), shape = RoundedCornerShape(18.dp)) {
  Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
   Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Text(order.plantName?.takeIf { it.isNotBlank() } ?: "Unnamed plant", color = Cream, fontWeight = FontWeight.SemiBold)
    Text(order.customerName ?: "Customer not entered", color = Leaf, fontSize = 12.sp)
    Text(if (order.missingFields.isEmpty()) "All details entered" else order.missingFields.size.toString() + " details remaining", color = Leaf, fontSize = 11.sp)
    if (order.notes.isNotBlank()) Text("Note: " + order.notes, color = Cream, fontSize = 11.sp, maxLines = 1)
   }
   Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Checkbox(checked = order.isReceived, onCheckedChange = onReceived, colors = CheckboxDefaults.colors(checkedColor = Leaf))
    Text("Received", color = Leaf, fontSize = 10.sp)
   }
  }
 }
}
@Composable private fun OrderEditor(order: Order?, onCancel: () -> Unit, onSave: (Order) -> Unit) {
 var name by remember { mutableStateOf(order?.customerName.orEmpty()) }
 var phone by remember { mutableStateOf(order?.phone.orEmpty()) }
 var address by remember { mutableStateOf(order?.address.orEmpty()) }
 var plant by remember { mutableStateOf(order?.plantName.orEmpty()) }
 var photo by remember { mutableStateOf(order?.photoUri.orEmpty()) }
 var cost by remember { mutableStateOf(order?.plantCost?.toString().orEmpty()) }
 var selling by remember { mutableStateOf(order?.sellingPrice?.toString().orEmpty()) }
 var finalPrice by remember { mutableStateOf(order?.finalPrice?.toString().orEmpty()) }
 var courier by remember { mutableStateOf(order?.courierCharge?.toString().orEmpty()) }
 var expected by remember { mutableStateOf(order?.expectedDispatchAt?.let { fmt(it) }.orEmpty()) }
 var dispatched by remember { mutableStateOf(order?.dispatchedAt?.let { fmt(it) }.orEmpty()) }
 var notes by remember { mutableStateOf(order?.notes.orEmpty()) }
 val missing = buildList {
  if (name.isBlank()) add("Customer name"); if (phone.isBlank()) add("Phone number"); if (address.isBlank()) add("Address")
  if (plant.isBlank()) add("Plant name"); if (photo.isBlank()) add("Plant photo")
  if (cost.toDoubleOrNull() == null) add("Plant cost"); if (selling.toDoubleOrNull() == null) add("Selling price")
  if (finalPrice.toDoubleOrNull() == null) add("Final price"); if (courier.toDoubleOrNull() == null) add("Courier charges")
  if (expected.isBlank() || parseDate(expected) == null) add("Expected dispatch date")
  if (dispatched.isBlank() || parseDate(dispatched) == null) add("Actual dispatch date")
 }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
   Text(if (order == null) "New order" else "Edit order", color = Cream, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
   TextButton(onClick = onCancel) { Text("Cancel") }
  }
  Text("Every field is optional. Save what you know and fill in the rest later.", color = Leaf, fontSize = 12.sp)
  Input("Customer name", name) { name = it }
  Input("Phone number", phone, KeyboardType.Phone) { phone = it }
  Input("Delivery address", address) { address = it }
  Input("Plant name", plant) { plant = it }
  Input("Plant photo URI (photo attachment can be added in a later iteration)", photo) { photo = it }
  Text("Financial details", color = Cream, fontWeight = FontWeight.SemiBold)
  Input("Actual plant cost (₹)", cost, KeyboardType.Decimal) { cost = it }
  Input("Selling price (₹)", selling, KeyboardType.Decimal) { selling = it }
  Input("Final customer price (₹)", finalPrice, KeyboardType.Decimal) { finalPrice = it }
  Input("Courier charges (₹) — enter after dispatch", courier, KeyboardType.Decimal) { courier = it }
  Text("Dates · use YYYY-MM-DD", color = Cream, fontWeight = FontWeight.SemiBold)
  Input("Expected dispatch date", expected) { expected = it }
  Input("Actual dispatch date", dispatched) { dispatched = it }
  Input("Notes / remarks for this order", notes) { notes = it }
  Card(colors = CardDefaults.cardColors(containerColor = Forest)) {
   Column(Modifier.padding(14.dp)) {
    Text("Still to be entered · " + missing.size, color = Cream, fontWeight = FontWeight.SemiBold)
    if (missing.isEmpty()) Text("Everything is filled in.", color = Leaf)
    missing.forEach { Text("• " + it, color = Leaf, fontSize = 12.sp) }
   }
  }
  Button(onClick = {
   onSave(Order(id = order?.id ?: 0, customerName = name.ifBlank { null }, phone = phone.ifBlank { null },
    address = address.ifBlank { null }, plantName = plant.ifBlank { null }, photoUri = photo.ifBlank { null },
    plantCost = cost.toDoubleOrNull(), sellingPrice = selling.toDoubleOrNull(), finalPrice = finalPrice.toDoubleOrNull(),
    courierCharge = courier.toDoubleOrNull(), expectedDispatchAt = parseDate(expected), dispatchedAt = parseDate(dispatched),
    isReceived = order?.isReceived ?: false, notes = notes, createdAt = order?.createdAt ?: System.currentTimeMillis()))
  }, modifier = Modifier.fillMaxWidth()) { Text("Save order") }
 }
}
@Composable private fun Input(label: String, value: String, type: KeyboardType = KeyboardType.Text, change: (String) -> Unit) {
 OutlinedTextField(value, change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
  keyboardOptions = KeyboardOptions(keyboardType = type), colors = OutlinedTextFieldDefaults.colors(
   focusedTextColor = Cream, unfocusedTextColor = Cream, focusedBorderColor = Leaf, unfocusedBorderColor = Leaf.copy(alpha = .5f),
   focusedLabelColor = Leaf, unfocusedLabelColor = Leaf.copy(alpha = .75f)))
}
private fun fmt(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(time))
private fun parseDate(text: String): Long? = runCatching {
 SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { isLenient = false }.parse(text)?.time
}.getOrNull()
