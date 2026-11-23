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
import java.util.Calendar

private val BizCream = Color(0xFFF3EAD2)
private val BizLeaf = Color(0xFFA8C9A7)
private val BizMuted = Color(0xFFB5C5B8)
private val BizPanel = Color(0xFF142D20)
private val BizForest = Color(0xFF173C2B)

@Composable
fun CustomerHistory(orders: List<Order>, openOrder: (Order) -> Unit) {
    var query by remember { mutableStateOf("") }
    val customers = orders.filter { !it.customerName.isNullOrBlank() }
        .groupBy { it.customerName!!.trim().lowercase() }
        .map { (_, list) -> list.first().customerName!!.trim() to list }
        .filter { it.first.contains(query, true) }
        .sortedBy { it.first.lowercase() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Customer history", color = BizCream, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Text("Each customer name keeps all their orders together.", color = BizMuted, fontSize = 12.sp)
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Search customer name") })
        if (customers.isEmpty()) {
            Text("No customer records yet. Add a customer name in an order to create their history.", color = BizMuted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
                items(customers, key = { it.first.lowercase() }) { (name, customerOrders) ->
                    var expanded by remember(name) { mutableStateOf(false) }
                    Card(colors = CardDefaults.cardColors(containerColor = BizPanel), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(13.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null, tint = BizLeaf)
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, color = BizCream, fontWeight = FontWeight.SemiBold)
                                    Text("${customerOrders.size} order(s)", color = BizMuted, fontSize = 11.sp)
                                }
                                Text(if (expanded) "−" else "+", color = BizLeaf, fontSize = 22.sp)
                            }
                            if (expanded) {
                                Spacer(Modifier.height(8.dp))
                                customerOrders.sortedByDescending { it.createdAt }.forEach { order ->
                                    TextButton(onClick = { openOrder(order) }, contentPadding = PaddingValues(vertical = 2.dp)) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(order.plantName?.takeIf { it.isNotBlank() } ?: "Plant order", color = BizCream)
                                            Text("Order · ${date(order.createdAt)} · ${if (order.isReceived) "Received" else "Pending"}", color = BizMuted, fontSize = 10.sp)
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
fun DispatchCalendar(orders: List<Order>, openOrder: (Order) -> Unit) {
    var monthOffset by remember { mutableIntStateOf(0) }
    val month = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, monthOffset)
    }
    val monthTitle = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(month.time)
    val monthOrders = orders.filter { order ->
        val due = order.expectedDispatchAt ?: order.dispatchedAt ?: return@filter false
        val c = Calendar.getInstance().apply { timeInMillis = due }
        c.get(Calendar.YEAR) == month.get(Calendar.YEAR) && c.get(Calendar.MONTH) == month.get(Calendar.MONTH)
    }.sortedBy { it.expectedDispatchAt ?: it.dispatchedAt ?: Long.MAX_VALUE }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Dispatch calendar", color = BizCream, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Card(colors = CardDefaults.cardColors(containerColor = BizForest), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { monthOffset-- }) { Text("‹", color = BizLeaf, fontSize = 22.sp) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CalendarMonth, null, tint = BizLeaf)
                    Text(monthTitle, color = BizCream, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { monthOffset++ }) { Text("›", color = BizLeaf, fontSize = 22.sp) }
            }
        }
        Text("${monthOrders.size} dispatch record(s)", color = BizMuted, fontSize = 12.sp)
        if (monthOrders.isEmpty()) Text("No dispatches scheduled for this month.", color = BizMuted)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
            items(monthOrders, key = { it.id }) { order ->
                val timestamp = order.expectedDispatchAt ?: order.dispatchedAt!!
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
                            Text(order.plantName ?: "Plant order", color = BizMuted, fontSize = 12.sp)
                            Text(if (order.dispatchedAt != null) "Dispatched" else "Expected dispatch",
                                color = BizLeaf, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlobalSearch(orders: List<Order>, nurseries: List<Nursery>, onOpenOrder: (Order) -> Unit) {
    var query by remember { mutableStateOf("") }
    val matches = if (query.isBlank()) emptyList() else orders.filter { o ->
        listOfNotNull(o.customerName, o.phone, o.address, o.plantName, o.notes)
            .any { it.contains(query, true) }
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
                        Text(order.plantName ?: "Plant order", color = BizCream, fontWeight = FontWeight.SemiBold)
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
