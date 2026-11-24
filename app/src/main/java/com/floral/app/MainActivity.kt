package com.floral.app

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.floral.app.data.*
import com.floral.app.notifications.DispatchReminder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val Forest=Color(0xFF244B36).copy(alpha=0.88f)
private val Deep=Color(0xFF0B1B13)
private val Panel=Color(0xFF244634).copy(alpha=0.74f)
private val Leaf=Color(0xFFA8C9A7)
private val Cream=Color(0xFFF3EAD2)
private val Muted=Color(0xFFB5C5B8)

class MainActivity:ComponentActivity(){
 private val notif=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
  val db=FloralDatabase.get(this);val dao=db.orders();val nurseryDao=db.nurseries();val orderPlantDao=db.orderPlants();val customerDao=db.customers()
  setContent {
   val orders by dao.observeAll().collectAsStateWithLifecycle(emptyList())
   val allOrderPlants by orderPlantDao.observeAll().collectAsStateWithLifecycle(emptyList())
   val customers by customerDao.observeAll().collectAsStateWithLifecycle(emptyList())
   LaunchedEffect(orders) {
    orders.forEach { order ->
     if (order.dispatchedAt != null) DispatchReminder.cancel(this@MainActivity, order.id)
     else DispatchReminder.schedule(this@MainActivity, order.id, order.expectedDispatchAt)
    }
   }
   val nurseries by nurseryDao.observeAll().collectAsStateWithLifecycle(emptyList())
   val nurseryPlants by nurseryDao.observeAllPlants().collectAsStateWithLifecycle(emptyList())
   var page by remember { mutableStateOf("Overview") }
   var editing by remember { mutableStateOf<Order?>(null) }
   var editingPlants by remember { mutableStateOf<List<OrderPlant>>(emptyList()) }
   var editor by remember { mutableStateOf(intent.getBooleanExtra("quick_add",false)) }
   var selectedNursery by remember { mutableStateOf<Nursery?>(null) }
   var nurseryEditor by remember { mutableStateOf(false) }
   var nurseryToEdit by remember { mutableStateOf<Nursery?>(null) }
   var plantToEdit by remember { mutableStateOf<NurseryPlant?>(null) }
   var plantEditor by remember { mutableStateOf(false) }
   var fromAddress by remember { mutableStateOf(getSharedPreferences("floral_settings", MODE_PRIVATE).getString("from_address", "") ?: "") }
   var printPdf by remember { mutableStateOf<File?>(null) }
   BackHandler(enabled = editor || nurseryEditor || plantEditor || selectedNursery != null || printPdf != null || page != "Overview") {
    when {
     printPdf != null -> printPdf = null
     plantEditor -> { plantEditor = false; plantToEdit = null }
     nurseryEditor -> { nurseryEditor = false; nurseryToEdit = null }
     editor -> { editor = false; editing = null; editingPlants = emptyList() }
     selectedNursery != null -> selectedNursery = null
     page != "Overview" -> page = "Overview"
    }
   }
   MaterialTheme(colorScheme=darkColorScheme(primary=Leaf,secondary=Cream,background=Deep,surface=Panel)){
    Surface(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1B13),Color(0xFF10271B),Color(0xFF0A1811)))),color=Color.Transparent){
     when {
      editor -> MultiPlantOrderEditor(editing,editingPlants,{editor=false;editing=null;editingPlants=emptyList()}){o,plants,onComplete->
       lifecycleScope.launch {
        try {
         val id=db.withTransaction {
          val savedId=if(o.id==0L)dao.insert(o) else {dao.update(o);o.id}
          orderPlantDao.deleteForOrder(savedId)
          orderPlantDao.insertAll(plants.map { p -> OrderPlant(orderId=savedId,name=p.name,plantCost=p.cost.toDoubleOrNull(),sellingPrice=p.selling.toDoubleOrNull(),finalPrice=p.finalPrice.toDoubleOrNull(),isPaid=p.paid) })
          if (!o.customerName.isNullOrBlank()) customerDao.insertFromOrder(o.customerName.trim(),o.phone.orEmpty().trim(),o.address.orEmpty().trim())
          savedId
         }
         runCatching {
          if(o.dispatchedAt!=null)DispatchReminder.cancel(this@MainActivity,id) else DispatchReminder.schedule(this@MainActivity,id,o.expectedDispatchAt)
         }.onFailure { reminderError ->
          android.widget.Toast.makeText(this@MainActivity,"Order saved, but reminder scheduling failed: " + (reminderError.message ?: "unknown error"),android.widget.Toast.LENGTH_LONG).show()
         }
         editor=false;editing=null;editingPlants=emptyList()
         onComplete(true)
        } catch(error: Exception) {
         android.widget.Toast.makeText(this@MainActivity,error.message ?: "Could not save order",android.widget.Toast.LENGTH_LONG).show()
         onComplete(false)
        }
       }
      }
      nurseryEditor -> NurseryEditor(nurseryToEdit,{nurseryEditor=false;nurseryToEdit=null}){n->
       lifecycleScope.launch { val id=if(n.id==0L)nurseryDao.insert(n) else {nurseryDao.update(n);n.id};selectedNursery=nurseryDao.get(id);nurseryEditor=false;nurseryToEdit=null }
      }
      plantEditor && selectedNursery!=null -> NurseryPlantEditor(plantToEdit,selectedNursery!!.id,{plantEditor=false;plantToEdit=null}){p->
       lifecycleScope.launch { if(p.id==0L)nurseryDao.insertPlant(p) else nurseryDao.updatePlant(p);plantEditor=false;plantToEdit=null }
      }
      selectedNursery!=null -> NurseryDetail(selectedNursery!!,nurseryDao,
       onBack={selectedNursery=null},
       onEdit={nurseryToEdit=selectedNursery;nurseryEditor=true},
       onAddPlant={plantToEdit=null;plantEditor=true},
       onEditPlant={plantToEdit=it;plantEditor=true},
       onDeletePlant={p->lifecycleScope.launch{nurseryDao.deletePlant(p.id)}}
      )
      else -> Scaffold(
       containerColor=Deep,
       floatingActionButton={
        FloatingActionButton(onClick={editing=null;editingPlants=emptyList();editor=true},containerColor=Leaf,contentColor=Deep){
         Icon(Icons.Default.Add,contentDescription="Add order")
        }
       }
      ) { innerPadding ->
       var swipeDistance by remember { mutableFloatStateOf(0f) }
       Column(Modifier.fillMaxSize().padding(innerPadding).padding(top=22.dp).padding(horizontal=18.dp).pointerInput(page) {
        detectHorizontalDragGestures(
         onHorizontalDrag={ _, amount -> swipeDistance += amount },
         onDragEnd={
          val tabs=listOf("Overview","Orders","Customers","Calendar","Search","Finances","Nurseries","Print","Settings")
          val index=tabs.indexOf(page)
          if(swipeDistance < -85f && index < tabs.lastIndex) page=tabs[index+1]
          else if(swipeDistance > 85f && index > 0) page=tabs[index-1]
          swipeDistance=0f
         },
         onDragCancel={swipeDistance=0f}
        )
       }){
       Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Column { Text("Floral",color=Cream,fontSize=31.sp,fontWeight=FontWeight.SemiBold);Text("A LITTLE MORE ORGANIZED, EVERY DAY",color=Leaf,fontSize=9.sp,letterSpacing=1.5.sp) }
       }
       Spacer(Modifier.height(16.dp))
       Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Overview","Orders","Customers","Calendar","Search","Finances","Nurseries","Print","Settings").forEach{FilterChip(selected=page==it,onClick={page=it},label={Text(it,fontSize=12.sp)})}}
       Spacer(Modifier.height(12.dp))
       Crossfade(targetState=page,label="tab-transition"){ activePage ->
       when(activePage){
        "Overview"->Overview(orders,allOrderPlants,{o->lifecycleScope.launch{editing=o;editingPlants=orderPlantDao.getForOrder(o.id);editor=true}},{o,v->lifecycleScope.launch{dao.setReceived(o.id,v)}},{p,v->lifecycleScope.launch{orderPlantDao.setPaid(p.id,v)}})
        "Customers"->CustomerManagementScreen(customers,orders,allOrderPlants,
         onSave={customer->lifecycleScope.launch{
          db.withTransaction {
           if(customer.id==0L) customerDao.insert(customer)
           else {
            val previous=customers.firstOrNull{it.id==customer.id}
            customerDao.update(customer)
            if(previous!=null) dao.updateCustomerDetails(previous.phone.trim(),previous.name,customer.name,customer.phone.trim(),customer.address.trim())
           }
          }
         }},
         onDelete={customer->lifecycleScope.launch{customerDao.delete(customer.id)}},
         openOrder={o->lifecycleScope.launch{editing=o;editingPlants=orderPlantDao.getForOrder(o.id);editor=true}}
        )
        "Calendar"->DispatchCalendar(orders,allOrderPlants){o->lifecycleScope.launch{editing=o;editingPlants=orderPlantDao.getForOrder(o.id);editor=true}}
        "Search"->GlobalSearch(orders,nurseries,allOrderPlants,nurseryPlants,{o->lifecycleScope.launch{editing=o;editingPlants=orderPlantDao.getForOrder(o.id);editor=true}},{n->selectedNursery=n})
        "Finances"->Finance(orders,allOrderPlants)
        "Nurseries"->NurseryList(nurseries,{selectedNursery=it},{nurseryToEdit=null;nurseryEditor=true})
        "Print"->Box(Modifier.fillMaxSize()) {
         PrintAddresses(orders,fromAddress,{page="Orders"}){selectedOrders->
          runCatching {
           printPdf=AddressLabelPdf.create(this@MainActivity,selectedOrders,fromAddress)
          }.onFailure { android.widget.Toast.makeText(this@MainActivity,it.message ?: "Could not create PDF",android.widget.Toast.LENGTH_LONG).show() }
         }
         if(printPdf!=null) PdfPreviewScreen(
          file=printPdf!!,
          onBack={printPdf=null},
          onPrint={file->
           runCatching {
            val manager=getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
            val attributes=android.print.PrintAttributes.Builder()
             .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
             .build()
            manager.print("Floral address labels",PdfFilePrintAdapter(file),attributes)
           }.onFailure { android.widget.Toast.makeText(this@MainActivity,it.message ?: "Could not open print service",android.widget.Toast.LENGTH_LONG).show() }
          },
          onShare={file->
           runCatching {
            val uri=FileProvider.getUriForFile(this@MainActivity,"${packageName}.fileprovider",file)
            val share=Intent(Intent.ACTION_SEND).apply { type="application/pdf";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            startActivity(Intent.createChooser(share,"Share address PDF"))
           }.onFailure { android.widget.Toast.makeText(this@MainActivity,it.message ?: "Could not share PDF",android.widget.Toast.LENGTH_LONG).show() }
          }
         )
        }
        "Settings"->BackupSettings(fromAddress,db){value->
         getSharedPreferences("floral_settings", MODE_PRIVATE).edit().putString("from_address",value).apply()
         fromAddress=value
         android.widget.Toast.makeText(this@MainActivity,"Sender address saved",android.widget.Toast.LENGTH_SHORT).show()
        }
        else->OrderList(orders,allOrderPlants,{o->lifecycleScope.launch{editing=o;editingPlants=orderPlantDao.getForOrder(o.id);editor=true}},{o,v->lifecycleScope.launch{dao.setReceived(o.id,v)}},{p,v->lifecycleScope.launch{orderPlantDao.setPaid(p.id,v)}},{selected->lifecycleScope.launch{selected.filter{it.dispatchedAt==null}.forEach{dao.setDispatched(it.id,System.currentTimeMillis())}}},{selected->lifecycleScope.launch{selected.filter{!it.isReceived}.forEach{dao.setReceived(it.id,true)}}},{selected->lifecycleScope.launch{selected.filter{!it.isPaid}.forEach{orderPlantDao.setPaid(it.id,true)}}})
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

@Composable private fun Overview(orders:List<Order>,plants:List<OrderPlant>,open:(Order)->Unit,toggle:(Order,Boolean)->Unit,togglePaid:(OrderPlant,Boolean)->Unit){
 val now=Calendar.getInstance()
 fun inPeriod(o:Order,p:Int):Boolean {val c=Calendar.getInstance().apply{timeInMillis=o.dispatchedAt?:o.createdAt};return when(p){Calendar.WEEK_OF_YEAR->c.getWeekYear()==now.getWeekYear()&&c.get(Calendar.WEEK_OF_YEAR)==now.get(Calendar.WEEK_OF_YEAR);Calendar.MONTH->c.get(Calendar.YEAR)==now.get(Calendar.YEAR)&&c.get(Calendar.MONTH)==now.get(Calendar.MONTH);else->c.get(Calendar.YEAR)==now.get(Calendar.YEAR)}}
 val complete=orders.filter{it.financialsComplete}
 Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
  Card(colors=CardDefaults.cardColors(containerColor=Forest),shape=RoundedCornerShape(23.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=0.10f))){Column(Modifier.fillMaxWidth().padding(20.dp)){Text("OVERALL PROFIT",color=Leaf,fontSize=11.sp,letterSpacing=1.3.sp);Text(money(complete.sumOf{it.profit?:0.0}),color=Cream,fontSize=34.sp,fontWeight=FontWeight.Bold);Text("${complete.size} orders with complete financial details",color=Muted,fontSize=12.sp)}}
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Week" to Calendar.WEEK_OF_YEAR,"Month" to Calendar.MONTH,"Year" to Calendar.YEAR).forEach{(label,p)->Card(Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(11.dp)){Text("This $label",color=Leaf,fontSize=10.sp);Text(money(complete.filter{inPeriod(it,p)}.sumOf{it.profit?:0.0}),color=Cream,fontWeight=FontWeight.SemiBold,fontSize=14.sp)}}}}
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Recent orders",color=Cream,fontWeight=FontWeight.SemiBold,fontSize=17.sp);Text("${orders.size} total",color=Muted,fontSize=11.sp)}
  if(orders.isEmpty()) Empty("Your first order starts here","Tap + or use the Floral widget to add one.")
  else LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=20.dp)){items(orders.take(6),key={it.id}){o->OrderCard(o,plants.filter{it.orderId==o.id},{open(o)},{toggle(o,!o.isReceived)},togglePaid)}}
 }
}
@Composable private fun OrderList(orders:List<Order>,plants:List<OrderPlant>,open:(Order)->Unit,toggle:(Order,Boolean)->Unit,togglePaid:(OrderPlant,Boolean)->Unit,bulkSent:(List<Order>)->Unit,bulkReceived:(List<Order>)->Unit,bulkPaid:(List<OrderPlant>)->Unit){
 var q by remember{mutableStateOf("")}
 var filter by remember{mutableStateOf("All")}
 var selectedIds by remember{mutableStateOf(setOf<Long>())}
 val visible=orders.filter{o->
  val orderPlants=plants.filter{it.orderId==o.id}
  (q.isBlank()||listOfNotNull(o.plantName,o.customerName,o.phone,o.address,o.notes).any{it.contains(q,true)}||orderPlants.any{it.name.contains(q,true)}) &&
  when(filter){"Pending"->o.dispatchedAt==null;"Sent"->o.dispatchedAt!=null;"Payment Pending"->orderPlants.any{!it.isPaid};else->true}
 }
 val selectedOrders=visible.filter{it.id in selectedIds}
 val selectedPlants=plants.filter{it.orderId in selectedIds}
 Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
  OutlinedTextField(q,{q=it;selectedIds=emptySet()},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search plant, customer or phone")},leadingIcon={Icon(Icons.Default.Search,null)})
  Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("Pending","Sent","All","Payment Pending").forEach{FilterChip(selected=filter==it,onClick={filter=it;selectedIds=emptySet()},label={Text(it)})}}
  if(visible.isNotEmpty()){
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
    Checkbox(checked=visible.isNotEmpty()&&visible.all{it.id in selectedIds},onCheckedChange={checked->selectedIds=if(checked)selectedIds+visible.map{it.id} else selectedIds-visible.map{it.id}.toSet()})
    Text(if(selectedIds.isEmpty())"${visible.size} orders" else "${selectedIds.size} selected",color=Muted,fontSize=12.sp,modifier=Modifier.weight(1f))
    if(selectedIds.isNotEmpty())TextButton(onClick={selectedIds=emptySet()}){Text("Clear")}
   }
  }
  if(selectedIds.isNotEmpty()){
   Card(colors=CardDefaults.cardColors(containerColor=Forest),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=0.09f))){
    Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
     Text("Bulk actions",color=Cream,fontWeight=FontWeight.SemiBold)
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
      Button(onClick={bulkSent(selectedOrders);selectedIds=emptySet()},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=Leaf,contentColor=Deep)){Text("Mark sent",fontSize=11.sp)}
      OutlinedButton(onClick={bulkReceived(selectedOrders);selectedIds=emptySet()},modifier=Modifier.weight(1f)){Text("Received",fontSize=11.sp)}
     }
     OutlinedButton(onClick={bulkPaid(selectedPlants);selectedIds=emptySet()},modifier=Modifier.fillMaxWidth()){Text("Mark all plants paid")}
    }
   }
  }
  if(visible.isEmpty())Empty("No matching orders","Try another filter or add a new order.")
  else LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=20.dp)){
   items(visible,key={it.id}){o->
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
     Checkbox(checked=o.id in selectedIds,onCheckedChange={checked->selectedIds=if(checked)selectedIds+o.id else selectedIds-o.id})
     Box(Modifier.weight(1f)){OrderCard(o,plants.filter{it.orderId==o.id},{open(o)},{toggle(o,!o.isReceived)},togglePaid)}
    }
   }
  }
 }
}
@Composable private fun OrderCard(o:Order,plants:List<OrderPlant>,open:()->Unit,toggleReceived:()->Unit,togglePaid:(OrderPlant,Boolean)->Unit){
 Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=0.09f)),elevation=CardDefaults.cardElevation(defaultElevation=3.dp)){
  Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
   Row(Modifier.fillMaxWidth().clickable(onClick=open),verticalAlignment=Alignment.CenterVertically){
    if(o.photoUri!=null) AsyncImage(Uri.parse(o.photoUri),null,Modifier.size(58.dp)) else Box(Modifier.size(58.dp).background(Forest,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Spa,null,tint=Leaf)}
    Spacer(Modifier.width(11.dp))
    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
     Text(o.customerName?: "Customer not entered",color=Cream,fontWeight=FontWeight.SemiBold)
     Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
      Surface(color=if(o.dispatchedAt!=null)Forest else Color(0xFF493B22),shape=RoundedCornerShape(50)){
       Text(if(o.dispatchedAt!=null)"SENT" else "PENDING",color=if(o.dispatchedAt!=null)Leaf else Color(0xFFFFD98A),fontSize=8.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=8.dp,vertical=4.dp))
      }
      Text(if(o.dispatchedAt!=null)"Dispatched · ${date(o.dispatchedAt)}" else o.expectedDispatchAt?.let{"Due · ${date(it)}"}?:"Dispatch date not set",color=Muted,fontSize=10.sp)
     }
     Text(if(o.missingFields.isEmpty())"Order details complete" else "${o.missingFields.size} details left",color=Muted,fontSize=10.sp)
     if(o.notes.isNotBlank())Text("Note: ${o.notes}",color=Cream,fontSize=10.sp,maxLines=1)
    }
    Column(horizontalAlignment=Alignment.CenterHorizontally){Checkbox(o.isReceived,{toggleReceived()},colors=CheckboxDefaults.colors(checkedColor=Leaf));Text("Received",color=if(o.isReceived)Leaf else Muted,fontSize=9.sp)}
   }
   if(plants.isNotEmpty()){
    Divider(color=Forest)
    plants.forEach { plant ->
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
      Text(plant.name.ifBlank{"Unnamed plant"},color=Cream,fontSize=12.sp,modifier=Modifier.weight(1f))
      Row(verticalAlignment=Alignment.CenterVertically){
       Checkbox(checked=plant.isPaid,onCheckedChange={togglePaid(plant,it)},colors=CheckboxDefaults.colors(checkedColor=Leaf))
       Text(if(plant.isPaid)"Paid" else "Unpaid",color=if(plant.isPaid)Leaf else Muted,fontSize=10.sp)
      }
     }
    }
   } else {
    Text(o.plantName?.ifBlank{"Unnamed plant"}?:"Plant details not entered",color=Cream,fontSize=12.sp)
   }
  }
 }
}
@Composable private fun Finance(orders:List<Order>,plants:List<OrderPlant>){
 val complete=orders.filter{it.financialsComplete}
 Column(verticalArrangement=Arrangement.spacedBy(11.dp)){
  Text("Financial overview",color=Cream,fontSize=23.sp,fontWeight=FontWeight.SemiBold)
  Metric("Total customer amount · agreed price + courier",orders.mapNotNull{it.totalValue}.sum())
  Metric("Recorded plant profit · agreed price − cost",complete.sumOf{it.profit?:0.0})
  val unpaid=plants.filter{!it.isPaid}.sumOf{it.finalPrice?:it.sellingPrice?:0.0}
  Metric("Plant payments still marked unpaid",unpaid)
  Text("The final agreed plant price is used when entered; otherwise the listed selling price is used. Courier is added to the customer total and excluded from plant profit as a pass-through charge. Unpaid total sums plant prices marked unpaid.",color=Muted,fontSize=11.sp)
  Text("Order breakdown",color=Cream,fontWeight=FontWeight.SemiBold)
  if(orders.isEmpty())Empty("No financial records yet","Order totals will appear here.")
  else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=18.dp)){items(orders,key={it.id}){o->Card(colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.fillMaxWidth().padding(13.dp)){Text(plants.filter{it.orderId==o.id}.joinToString(", "){it.name}.ifBlank{o.plantName?:"Unnamed plant"},color=Cream,fontWeight=FontWeight.SemiBold);Text("Order total: ${o.totalValue?.let(::money)?:"Awaiting agreed price + courier"}",color=Leaf,fontSize=12.sp);Text("Profit: ${if(o.financialsComplete)money(o.profit?:0.0) else "Pending details"}",color=Muted,fontSize=12.sp);Text("Paid plants: ${plants.count{it.orderId==o.id&&it.isPaid}} / ${plants.count{it.orderId==o.id}}",color=Muted,fontSize=11.sp)}}}}
 }
}
@Composable private fun Metric(label:String,amount:Double){Card(colors=CardDefaults.cardColors(containerColor=Forest),shape=RoundedCornerShape(18.dp)){Column(Modifier.fillMaxWidth().padding(16.dp)){Text(label,color=Leaf,fontSize=12.sp);Text(money(amount),color=Cream,fontSize=25.sp,fontWeight=FontWeight.Bold)}}}
@Composable private fun Empty(title:String,sub:String){Card(colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.fillMaxWidth().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.Spa,null,tint=Leaf,modifier=Modifier.size(30.dp));Text(title,color=Cream,fontWeight=FontWeight.SemiBold);Text(sub,color=Muted,fontSize=12.sp)}}}

@Composable private fun NurseryList(nurseries:List<Nursery>,open:(Nursery)->Unit,add:()->Unit){
 var query by remember{mutableStateOf("")}
 Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column{Text("Nursery directory",color=Cream,fontSize=22.sp,fontWeight=FontWeight.SemiBold);Text("${nurseries.size} saved nurseries",color=Muted,fontSize=11.sp)};Button(onClick=add,colors=ButtonDefaults.buttonColors(containerColor=Leaf,contentColor=Deep)){Icon(Icons.Default.Add,null);Text("Add")}}
  OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search nursery or location")},leadingIcon={Icon(Icons.Default.Search,null)})
  val shown=nurseries.filter{listOfNotNull(it.name,it.address,it.phone).any{v->v.contains(query,true)}}
  if(shown.isEmpty())Empty("No nurseries saved yet","Add a nursery once, then keep all its plants together.")
  else LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=20.dp)){items(shown,key={it.id}){n->
   Card(Modifier.fillMaxWidth().clickable{open(n)},colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(17.dp)){
    Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
     if(n.photoUri!=null)AsyncImage(Uri.parse(n.photoUri),null,Modifier.size(68.dp))else Box(Modifier.size(68.dp).background(Forest,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Park,null,tint=Leaf)}
     Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(n.name?.ifBlank{"Unnamed nursery"}?:"Unnamed nursery",color=Cream,fontWeight=FontWeight.SemiBold);Text(n.address?:if(n.latitude!=null)"GPS location saved" else "Location not added",color=Muted,fontSize=11.sp,maxLines=2);if(n.phone!=null)Text(n.phone,color=Leaf,fontSize=11.sp);if(n.notes.isNotBlank())Text(n.notes,color=Muted,fontSize=10.sp,maxLines=1)}
     Icon(Icons.Default.ChevronRight,null,tint=Muted)
    }
   }
  }}
 }
}

@Composable private fun NurseryDetail(nursery:Nursery,dao:NurseryDao,onBack:()->Unit,onEdit:()->Unit,onAddPlant:()->Unit,onEditPlant:(NurseryPlant)->Unit,onDeletePlant:(NurseryPlant)->Unit){
 val plants by dao.observePlants(nursery.id).collectAsStateWithLifecycle(emptyList())
 val context = androidx.compose.ui.platform.LocalContext.current
 Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(top=12.dp).padding(horizontal=16.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null);Text("Nurseries")};TextButton(onClick=onEdit){Icon(Icons.Default.Edit,null);Text("Edit profile")}}
  LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
   item {
    Card(colors=CardDefaults.cardColors(containerColor=Forest),shape=RoundedCornerShape(20.dp)){Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
     if(nursery.photoUri!=null)AsyncImage(Uri.parse(nursery.photoUri),null,Modifier.fillMaxWidth().heightIn(max=210.dp))
     Text(nursery.name?.ifBlank{"Unnamed nursery"}?:"Unnamed nursery",color=Cream,fontSize=23.sp,fontWeight=FontWeight.SemiBold)
     if(nursery.address!=null)Text(nursery.address,color=Leaf,fontSize=12.sp)
     if(nursery.latitude!=null&&nursery.longitude!=null)TextButton(onClick={val uri=Uri.parse("geo:${nursery.latitude},${nursery.longitude}?q=${nursery.latitude},${nursery.longitude}(${Uri.encode(nursery.name?:"Nursery")})");runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,uri))}}){Icon(Icons.Default.Map,null);Text("Open saved location in Maps")}
     if(nursery.phone!=null)TextButton(onClick={val intent=Intent(Intent.ACTION_DIAL,Uri.parse("tel:${nursery.phone}"));runCatching{context.startActivity(intent)}}){Icon(Icons.Default.Call,null);Text(nursery.phone)}
     if(nursery.notes.isNotBlank()){Divider(color=Leaf.copy(alpha=.35f));Text("Nursery notes",color=Leaf,fontWeight=FontWeight.SemiBold);Text(nursery.notes,color=Cream,fontSize=13.sp)}
    }}
   }
   item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column{Text("Plants from this nursery",color=Cream,fontSize=18.sp,fontWeight=FontWeight.SemiBold);Text("${plants.size} saved plants",color=Muted,fontSize=11.sp)};Button(onClick=onAddPlant,colors=ButtonDefaults.buttonColors(containerColor=Leaf,contentColor=Deep)){Icon(Icons.Default.Add,null);Text("Add plant")}}}
   if(plants.isEmpty())item{Empty("No plants recorded","Add the plants and prices your mother finds here.")}
   items(plants,key={it.id}){p->Card(Modifier.fillMaxWidth().clickable{onEditPlant(p)},colors=CardDefaults.cardColors(containerColor=Panel)){Row(Modifier.padding(11.dp),verticalAlignment=Alignment.CenterVertically){if(p.photoUri!=null)AsyncImage(Uri.parse(p.photoUri),null,Modifier.size(70.dp))else Box(Modifier.size(70.dp).background(Forest,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Spa,null,tint=Leaf)};Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(p.name?: "Unnamed plant",color=Cream,fontWeight=FontWeight.SemiBold);Text((p.price?.let{money(it)}?:"Price not entered")+" · Stock: ${p.stock}",color=Leaf,fontSize=12.sp);if(p.notes.isNotBlank())Text(p.notes,color=Muted,fontSize=10.sp,maxLines=2)};IconButton(onClick={onDeletePlant(p)}){Icon(Icons.Default.DeleteOutline,null,tint=Muted)}}}}
  }
 }
}

@Composable private fun NurseryEditor(existing:Nursery?,cancel:()->Unit,save:(Nursery)->Unit){
 val context=androidx.compose.ui.platform.LocalContext.current
 var name by remember(existing?.id){mutableStateOf(existing?.name.orEmpty())}
 var phone by remember(existing?.id){mutableStateOf(existing?.phone.orEmpty())}
 var address by remember(existing?.id){mutableStateOf(existing?.address.orEmpty())}
 var notes by remember(existing?.id){mutableStateOf(existing?.notes.orEmpty())}
 var photo by remember(existing?.id){mutableStateOf(existing?.photoUri.orEmpty())}
 var lat by remember(existing?.id){mutableStateOf(existing?.latitude)}
 var lon by remember(existing?.id){mutableStateOf(existing?.longitude)}
 var locationStatus by remember{mutableStateOf(if(lat!=null)"Saved location ready" else "Location not captured")}
 var pendingCameraUri by remember{mutableStateOf<Uri?>(null)}
 fun captureLocation(){
  val client=LocationServices.getFusedLocationProviderClient(context)
  try{client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,CancellationTokenSource().token).addOnSuccessListener{loc->
   if(loc==null){locationStatus="Could not get GPS fix. Try again outdoors."}
   else{lat=loc.latitude;lon=loc.longitude;locationStatus="GPS coordinates saved";try{val geocoder=Geocoder(context,Locale.getDefault());if(Build.VERSION.SDK_INT>=33){geocoder.getFromLocation(loc.latitude,loc.longitude,1){results->if(results.isNotEmpty()){val a=results[0].getAddressLine(0);if(!a.isNullOrBlank())address=a}}}else{@Suppress("DEPRECATION") val results=geocoder.getFromLocation(loc.latitude,loc.longitude,1);if(!results.isNullOrEmpty()){val a=results[0].getAddressLine(0);if(!a.isNullOrBlank())address=a}}}catch(_:Exception){}}
  }.addOnFailureListener{locationStatus="Location unavailable. You can enter the address manually."}}catch(_:SecurityException){locationStatus="Location permission not granted."}
 }
 val permission= androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){r->if(r[Manifest.permission.ACCESS_FINE_LOCATION]==true||r[Manifest.permission.ACCESS_COARSE_LOCATION]==true)captureLocation()else locationStatus="Permission declined. Location is optional."}
 val gallery=androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null){runCatching{context.contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)};photo=u.toString()}}
 val camera=androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->if(ok&&pendingCameraUri!=null){photo=pendingCameraUri.toString();if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)captureLocation()else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}}
 fun takePhoto(){try{val dir=File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),"nurseries").apply{mkdirs()};val file=File.createTempFile("nursery_${System.currentTimeMillis()}_",".jpg",dir);val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file);pendingCameraUri=uri;camera.launch(uri)}catch(e:Exception){locationStatus="Camera could not be opened. Choose a photo from the gallery instead."}}
 Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(top=12.dp).padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(if(existing==null)"New nursery" else "Edit nursery",color=Cream,fontSize=24.sp,fontWeight=FontWeight.SemiBold);TextButton(onClick=cancel){Text("Cancel")}}
  Text("All details are optional. Save the profile now and add plants whenever you visit.",color=Leaf,fontSize=12.sp)
  if(photo.isNotBlank())AsyncImage(Uri.parse(photo),null,Modifier.fillMaxWidth().heightIn(max=220.dp))
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={takePhoto()},modifier=Modifier.weight(1f)){Icon(Icons.Default.PhotoCamera,null);Text("Take photo")};OutlinedButton(onClick={gallery.launch(arrayOf("image/*"))},modifier=Modifier.weight(1f)){Icon(Icons.Default.Image,null);Text("Gallery")}}
  if(photo.isNotBlank())TextButton(onClick={photo=""}){Text("Remove photo")}
  Field("Nursery name",name){name=it};Field("Contact number",phone,true,KeyboardType.Phone){phone=it}
  Text("Location",color=Cream,fontWeight=FontWeight.SemiBold)
  Text(locationStatus,color=Muted,fontSize=11.sp)
  if(lat!=null&&lon!=null)Text("GPS: ${"%.6f".format(Locale.US,lat)}°, ${"%.6f".format(Locale.US,lon)}°",color=Leaf,fontSize=11.sp)
  OutlinedButton(onClick={if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)captureLocation()else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.MyLocation,null);Text(if(lat==null)"Capture current GPS location" else "Update GPS location")}
  Field("Address / location (editable)",address,false){address=it}
  Field("Notes about this nursery",notes,false){notes=it}
  Button(onClick={save(Nursery(id=existing?.id?:0,name=name.trim().ifBlank{null},phone=phone.trim().ifBlank{null},address=address.trim().ifBlank{null},latitude=lat,longitude=lon,photoUri=photo.ifBlank{null},notes=notes.trim(),createdAt=existing?.createdAt?:System.currentTimeMillis(),updatedAt=System.currentTimeMillis()))},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Leaf,contentColor=Deep)){Text("Save nursery profile",fontWeight=FontWeight.SemiBold)}
 }
}

@Composable private fun NurseryPlantEditor(existing:NurseryPlant?,nurseryId:Long,cancel:()->Unit,save:(NurseryPlant)->Unit){
 val context=androidx.compose.ui.platform.LocalContext.current
 var name by remember(existing?.id){mutableStateOf(existing?.name.orEmpty())};var price by remember(existing?.id){mutableStateOf(existing?.price?.toString().orEmpty())};var stock by remember(existing?.id){mutableStateOf(existing?.stock?.toString() ?: "0")};var notes by remember(existing?.id){mutableStateOf(existing?.notes.orEmpty())};var photo by remember(existing?.id){mutableStateOf(existing?.photoUri.orEmpty())}
 val picker=androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null){runCatching{context.contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)};photo=u.toString()}}
 val invalidPrice = price.isNotBlank() && (price.toDoubleOrNull()?.let { it < 0.0 } ?: true)
 val invalidStock = stock.toIntOrNull()?.let { it < 0 } ?: true
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(if(existing==null)"Add nursery plant" else "Edit plant",color=Cream,fontSize=24.sp,fontWeight=FontWeight.SemiBold);TextButton(onClick=cancel){Text("Cancel")}}
  Text("Plant details are optional. This entry will be saved under its nursery.",color=Leaf,fontSize=12.sp)
  if(photo.isNotBlank())AsyncImage(Uri.parse(photo),null,Modifier.fillMaxWidth().heightIn(max=220.dp))
  OutlinedButton(onClick={picker.launch(arrayOf("image/*"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Image,null);Text(if(photo.isBlank())"Choose plant photo" else "Change plant photo")}
  if(photo.isNotBlank())TextButton(onClick={photo=""}){Text("Remove photo")}
  Field("Plant name",name){name=it};Field("Nursery price (₹)",price,true,KeyboardType.Decimal){price=it};Field("Stock quantity",stock,true,KeyboardType.Number){stock=it};Field("Plant notes",notes,false){notes=it}
  if(invalidPrice)Text("Enter a valid non-negative nursery price.",color=MaterialTheme.colorScheme.error,fontSize=12.sp)
  if(invalidStock)Text("Enter a whole-number stock quantity of 0 or more.",color=MaterialTheme.colorScheme.error,fontSize=12.sp)
  Button(onClick={save(NurseryPlant(id=existing?.id?:0,nurseryId=nurseryId,name=name.trim().ifBlank{null},price=price.toDoubleOrNull(),stock=stock.toIntOrNull()?:0,photoUri=photo.ifBlank{null},notes=notes.trim(),createdAt=existing?.createdAt?:System.currentTimeMillis()))},enabled=!invalidPrice&&!invalidStock,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Leaf,contentColor=Deep)){Text("Save plant",fontWeight=FontWeight.SemiBold)}
 }
}

@Composable private fun OrderEditor(order:Order?,cancel:()->Unit,save:(Order)->Unit){
 var name by remember{mutableStateOf(order?.customerName.orEmpty())};var phone by remember{mutableStateOf(order?.phone.orEmpty())};var address by remember{mutableStateOf(order?.address.orEmpty())};var plant by remember{mutableStateOf(order?.plantName.orEmpty())};var photo by remember{mutableStateOf(order?.photoUri.orEmpty())}
 var cost by remember{mutableStateOf(order?.plantCost?.toString().orEmpty())};var selling by remember{mutableStateOf(order?.sellingPrice?.toString().orEmpty())};var finalPrice by remember{mutableStateOf(order?.finalPrice?.toString().orEmpty())};var courier by remember{mutableStateOf(order?.courierCharge?.toString().orEmpty())}
 var expected by remember{mutableStateOf(order?.expectedDispatchAt)};var dispatched by remember{mutableStateOf(order?.dispatchedAt)};var notes by remember{mutableStateOf(order?.notes.orEmpty())};var includeFrom by remember{mutableStateOf(order?.includeFromAddress?:false)}
 val context=androidx.compose.ui.platform.LocalContext.current
 val picker=androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){runCatching{context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)};photo=uri.toString()}}
 val missing=buildList{if(name.isBlank())add("Customer name");if(phone.isBlank())add("Phone number");if(address.isBlank())add("Delivery address");if(plant.isBlank())add("Plant name");if(photo.isBlank())add("Plant photograph");if(cost.toDoubleOrNull()==null)add("Actual plant cost");if(selling.toDoubleOrNull()==null)add("Selling price");if(finalPrice.toDoubleOrNull()==null)add("Final customer price");if(courier.toDoubleOrNull()==null)add("Courier charges");if(expected==null)add("Expected dispatch date");if(dispatched==null)add("Actual dispatch date")}
 Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(top=12.dp).padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(if(order==null)"New order" else "Edit order",color=Cream,fontSize=24.sp,fontWeight=FontWeight.SemiBold);TextButton(onClick=cancel){Text("Cancel")}}
  Text("Every field is optional. Save what you know and complete the rest later.",color=Leaf,fontSize=12.sp)
  Field("Customer name",name){name=it};Field("Phone number",phone,true,KeyboardType.Phone){phone=it};Field("Delivery address",address,false){address=it};Field("Plant name",plant){plant=it}
  Card(colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Plant photograph",color=Cream,fontWeight=FontWeight.SemiBold);if(photo.isNotBlank())AsyncImage(Uri.parse(photo),null,Modifier.fillMaxWidth().heightIn(max=190.dp));OutlinedButton(onClick={picker.launch(arrayOf("image/*"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Image,null);Spacer(Modifier.width(7.dp));Text(if(photo.isBlank())"Choose plant photo" else "Change plant photo")};if(photo.isNotBlank())TextButton(onClick={photo=""}){Text("Remove photo")}}}
  Text("Financial details",color=Cream,fontWeight=FontWeight.SemiBold)
  Field("Actual plant cost (₹)",cost,true,KeyboardType.Decimal){cost=it};Field("Selling price (₹, excluding courier)",selling,true,KeyboardType.Decimal){selling=it};Field("Final amount agreed with customer (₹)",finalPrice,true,KeyboardType.Decimal){finalPrice=it};Field("Courier charges (₹) · enter after dispatch",courier,true,KeyboardType.Decimal){courier=it}
  Text("Order total when both are known: ${money((selling.toDoubleOrNull()?:0.0)+(courier.toDoubleOrNull()?:0.0))}",color=Leaf,fontSize=12.sp)
  Text("Dispatch details",color=Cream,fontWeight=FontWeight.SemiBold)
  DateField("Expected dispatch date",expected){expected=it};DateField("Actual dispatch date",dispatched){dispatched=it}
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Checkbox(checked=includeFrom,onCheckedChange={includeFrom=it});Text("Include saved FROM address on parcel label",color=Cream,fontSize=13.sp)}
  Field("Notes / remarks for this order",notes,false){notes=it}
  Card(colors=CardDefaults.cardColors(containerColor=Forest),shape=RoundedCornerShape(17.dp)){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){Text("Still to be entered · ${missing.size}",color=Cream,fontWeight=FontWeight.SemiBold);if(missing.isEmpty())Text("Everything is filled in.",color=Leaf,fontSize=12.sp);missing.forEach{Text("• $it",color=Leaf,fontSize=12.sp)}}}
  Button(onClick={save(Order(id=order?.id?:0,customerName=name.trim().ifBlank{null},phone=phone.trim().ifBlank{null},address=address.trim().ifBlank{null},plantName=plant.trim().ifBlank{null},photoUri=photo.ifBlank{null},plantCost=cost.toDoubleOrNull(),sellingPrice=selling.toDoubleOrNull(),finalPrice=finalPrice.toDoubleOrNull(),courierCharge=courier.toDoubleOrNull(),expectedDispatchAt=expected,dispatchedAt=dispatched,isReceived=order?.isReceived?:false,notes=notes,includeFromAddress=includeFrom,createdAt=order?.createdAt?:System.currentTimeMillis()))},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Leaf,contentColor=Deep)){Text("Save order",fontWeight=FontWeight.SemiBold)}
 }
}
@Composable private fun Field(label:String,value:String,single:Boolean=true,type:KeyboardType=KeyboardType.Text,onChange:(String)->Unit){OutlinedTextField(value,onChange,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=single,keyboardOptions=KeyboardOptions(keyboardType=type),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Cream,unfocusedTextColor=Cream,focusedBorderColor=Leaf,unfocusedBorderColor=Muted.copy(alpha=.5f),focusedLabelColor=Leaf,unfocusedLabelColor=Muted,cursorColor=Leaf))}
@Composable private fun DateField(label:String,value:Long?,change:(Long?)->Unit){
 val context=androidx.compose.ui.platform.LocalContext.current
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){OutlinedButton(onClick={val c=Calendar.getInstance().apply{if(value!=null)timeInMillis=value};DatePickerDialog(context,{_,y,m,d->change(Calendar.getInstance().apply{set(y,m,d,12,0,0);set(Calendar.MILLISECOND,0)}.timeInMillis)},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show()},modifier=Modifier.weight(1f)){Icon(Icons.Default.CalendarMonth,null);Spacer(Modifier.width(7.dp));Text(if(value==null)label else "$label: ${date(value)}")};if(value!=null)TextButton(onClick={change(null)}){Text("Clear")}}
}
private fun date(t:Long)=SimpleDateFormat("dd MMM yyyy",Locale.getDefault()).format(Date(t))
private fun money(v:Double)="₹"+String.format(Locale("en","IN"),"%,.2f",v)
