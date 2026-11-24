package com.floral.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.floral.app.data.Order
import com.floral.app.data.Nursery
import com.floral.app.data.OrderPlant
import java.util.Calendar

private val BizCream = Color(0xFFF3EAD2)
private val BizLeaf = Color(0xFFA8C9A7)
private val BizMuted = Color(0xFFB5C5B8)
private val BizPanel = Color(0xFF142D20)
private val BizForest = Color(0xFF173C2B)

@Composable
fun CustomerHistory(orders: List<Order>, plants: List<OrderPlant>, openOrder: (Order) -> Unit) {
    var query by remember { mutableStateOf("") }
    val customers = orders.filter { !it.customerName.isNullOrBlank() }
        .groupBy { order ->
            val phoneKey = order.phone.orEmpty().filter { it.isDigit() }
            if (phoneKey.isNotBlank()) "phone:" + phoneKey else "name:" + order.customerName!!.trim().lowercase()
        }
        .map { (key, list) -> Triple(key, list.first().customerName!!.trim(), list) }
        .filter { it.second.contains(query, true) || it.third.any { order -> order.phone.orEmpty().contains(query, true) } }
        .sortedBy { it.second.lowercase() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Customer history", color = BizCream, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Text("Each customer name keeps all their orders together.", color = BizMuted, fontSize = 12.sp)
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Search customer name") })
        if (customers.isEmpty()) {
            Text("No customer records yet. Add a customer name in an order to create their history.", color = BizMuted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
                items(customers, key = { it.first }) { (customerKey, name, customerOrders) ->
                    var expanded by remember(customerKey) { mutableStateOf(false) }
                    Card(colors = CardDefaults.cardColors(containerColor = BizPanel), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(13.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null, tint = BizLeaf)
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, color = BizCream, fontWeight = FontWeight.SemiBold)
                                    Text(customerOrders.size.toString() + " order(s) · " + customerOrders.firstOrNull()?.phone.orEmpty(), color = BizMuted, fontSize = 11.sp)
                                }
                                Text(if (expanded) "−" else "+", color = BizLeaf, fontSize = 22.sp)
                            }
                            if (expanded) {
                                Spacer(Modifier.height(8.dp))
                                customerOrders.sortedByDescending { it.createdAt }.forEach { order ->
                                    TextButton(onClick = { openOrder(order) }, contentPadding = PaddingValues(vertical = 2.dp)) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(plants.filter { it.orderId == order.id }.joinToString(", ") { it.name }.ifBlank { order.plantName?.takeIf { it.isNotBlank() } ?: "Plant order" }, color = BizCream)
                                            Text("Order · ${businessDate(order.createdAt)} · ${if (order.isReceived) "Received" else "Pending"}", color = BizMuted, fontSize = 10.sp)
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
}

@Composable
fun DispatchCalendar(orders: List<Order>, plants: List<OrderPlant>, openOrder: (Order) -> Unit) {
    var monthOffset by remember { mutableIntStateOf(0) }
    val month = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, monthOffset)
    }
    var selectedDay by remember(monthOffset) {
        mutableIntStateOf(if (monthOffset == 0) Calendar.getInstance().get(Calendar.DAY_OF_MONTH) else 1)
    }
    val year = month.get(Calendar.YEAR)
    val monthIndex = month.get(Calendar.MONTH)
    val monthTitle = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(month.time)
    val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstWeekdayOffset = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday-first
    fun eventDate(order: Order): Calendar? {
        val timestamp = order.dispatchedAt ?: order.expectedDispatchAt ?: return null
        return Calendar.getInstance().apply { timeInMillis = timestamp }
    }
    val monthOrders = orders.filter { order ->
        val c = eventDate(order) ?: return@filter false
        c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == monthIndex
    }
    val selectedOrders = monthOrders.filter { order ->
        eventDate(order)?.get(Calendar.DAY_OF_MONTH) == selectedDay
    }.sortedBy { it.customerName.orEmpty().lowercase() }
    val eventDays = monthOrders.mapNotNull { eventDate(it)?.get(Calendar.DAY_OF_MONTH) }.toSet()
    val weekdayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Dispatch calendar", color = BizCream, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Card(colors = CardDefaults.cardColors(containerColor = BizForest), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { monthOffset--; selectedDay = 1 }) { Text("‹", color = BizLeaf, fontSize = 22.sp) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CalendarMonth, null, tint = BizLeaf)
                        Text(monthTitle, color = BizCream, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = { monthOffset++; selectedDay = 1 }) { Text("›", color = BizLeaf, fontSize = 22.sp) }
                }
                Row(Modifier.fillMaxWidth()) {
                    weekdayLabels.forEach { label ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(label, color = BizMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                val cellCount = firstWeekdayOffset + daysInMonth
                val weekCount = (cellCount + 6) / 7
                repeat(weekCount) { week ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { column ->
                            val day = week * 7 + column - firstWeekdayOffset + 1
                            if (day in 1..daysInMonth) {
                                val selected = day == selectedDay
                                val hasEvents = day in eventDays
                                Surface(
                                    modifier = Modifier.weight(1f).padding(2.dp).height(39.dp)
                                        .clickable { selectedDay = day },
                                    color = if (selected) BizLeaf else Color.Transparent,
                                    shape = RoundedCornerShape(9.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center) {
                                        Text(day.toString(), color = if (selected) BizForest else BizCream,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp)
                                        Text(if (hasEvents) "•" else " ", color = if (selected) BizForest else BizLeaf,
                                            fontSize = 11.sp, lineHeight = 9.sp)
                                    }
                                }
                            } else {
                                Spacer(Modifier.weight(1f).height(39.dp))
                            }
                        }
                    }
                }
            }
        }
        Text("${monthOrders.size} dispatch record(s) · ${selectedOrders.size} on ${selectedDay} ${java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()).format(month.time)}",
            color = BizMuted, fontSize = 12.sp)
        if (selectedOrders.isEmpty()) {
            Text("No dispatches on this day. Select a marked date to view its orders.", color = BizMuted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
                items(selectedOrders, key = { it.id }) { order ->
                    val timestamp = eventDate(order)?.timeInMillis ?: 0L
                    Card(Modifier.fillMaxWidth().clickable { openOrder(order) },
                        colors = CardDefaults.cardColors(containerColor = BizPanel), shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(java.text.SimpleDateFormat("dd", java.util.Locale.getDefault()).format(java.util.Date(timestamp)),
                                    color = BizLeaf, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text(java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(java.util.Date(timestamp)),
                                    color = BizMuted, fontSize = 10.sp)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(order.customerName ?: "Customer not entered", color = BizCream, fontWeight = FontWeight.SemiBold)
                                Text(plants.filter { it.orderId == order.id }.joinToString(", ") { it.name }
                                    .ifBlank { order.plantName ?: "Plant order" }, color = BizMuted, fontSize = 12.sp)
                                Text(if (order.dispatchedAt != null) "Actually dispatched" else "Expected dispatch",
                                    color = BizLeaf, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlobalSearch(orders: List<Order>, nurseries: List<Nursery>, plants: List<OrderPlant>, onOpenOrder: (Order) -> Unit) {
    var query by remember { mutableStateOf("") }
    val matches = if (query.isBlank()) emptyList() else orders.filter { o ->
        listOfNotNull(o.customerName, o.phone, o.address, o.plantName, o.notes)
            .any { it.contains(query, true) } || plants.any { it.orderId == o.id && it.name.contains(query, true) }
    }
    val nurseryMatches = if (query.isBlank()) emptyList() else nurseries.filter { n ->
        listOfNotNull(n.name, n.address, n.phone).any { it.contains(query, true) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Search Floral", color = BizCream, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Customer, phone, plant or order") })
        if (query.isNotBlank() && matches.isEmpty() && nurseryMatches.isEmpty()) Text("No matching records.", color = BizMuted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
            items(matches, key = { "order-${it.id}" }) { order ->
                Card(Modifier.fillMaxWidth().clickable { onOpenOrder(order) },
                    colors = CardDefaults.cardColors(containerColor = BizPanel), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(plants.filter { it.orderId == order.id }.joinToString(", ") { it.name }.ifBlank { order.plantName ?: "Plant order" }, color = BizCream, fontWeight = FontWeight.SemiBold)
                        Text(order.customerName ?: "Customer not entered", color = BizLeaf, fontSize = 12.sp)
                        Text(order.phone ?: "No phone number", color = BizMuted, fontSize = 11.sp)
                    }
                }
            }
            items(nurseryMatches, key = { "nursery-${it.id}" }) { nursery ->
                Card(colors = CardDefaults.cardColors(containerColor = BizPanel), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(nursery.name ?: "Unnamed nursery", color = BizCream, fontWeight = FontWeight.SemiBold)
                        Text(nursery.address ?: "Nursery record", color = BizMuted, fontSize = 11.sp)
                        Text("Nursery", color = BizLeaf, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

private fun businessDate(timestamp: Long): String = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
