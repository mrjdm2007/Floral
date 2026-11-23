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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

private val Forest=Color(0xFF173C2B)
private val Deep=Color(0xFF0B1B13)
private val Panel=Color(0xFF142D20)
private val Leaf=Color(0xFFA8C9A7)
private val Cream=Color(0xFFF3EAD2)
private val Muted=Color(0xFFB5C5B8)

class MainActivity:ComponentActivity(){
 private val notif=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
  val db=FloralDatabase.get(this);val dao=db.orders();val nurseryDao=db.nurseries()
  setContent {
   val orders by dao.observeAll().collectAsStateWithLifecycle(emptyList())
   val nurseries by nurseryDao.observeAll().collectAsStateWithLifecycle(emptyList())
   var page by remember { mutableStateOf("Overview") }
   var editing by remember { mutableStateOf<Order?>(null) }
   var editor by remember { mutableStateOf(intent.getBooleanExtra("quick_add",false)) }
   var selectedNursery by remember { mutableStateOf<Nursery?>(null) }
   var nurseryEditor by remember { mutableStateOf(false) }
   var nurseryToEdit by remember { mutableStateOf<Nursery?>(null) }
   var plantToEdit by remember { mutableStateOf<NurseryPlant?>(null) }
   var plantEditor by remember { mutableStateOf(false) }
   var fromAddress by remember { mutableStateOf(getSharedPreferences("floral_settings", MODE_PRIVATE).getString("from_address", "") ?: "") }
   var printPdf by remember { mutableStateOf<File?>(null) }
   MaterialTheme(colorScheme=darkColorScheme(primary=Leaf,secondary=Cream,background=Deep,surface=Panel)){
    Surface(Modifier.fillMaxSize(),color=Deep){
     when {
      editor -> OrderEditor(editing,{editor=false;editing=null}){o->
       lifecycleScope.launch { val id=if(o.id==0L)dao.insert(o) else {dao.update(o);o.id};if(o.dispatchedAt!=null)DispatchReminder.cancel(this@MainActivity,id) else DispatchReminder.schedule(this@MainActivity,id,o.expectedDispatchAt);editor=false;editing=null }
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
      else -> Column(Modifier.fillMaxSize().padding(18.dp)){
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
        Column { Text("Floral",color=Cream,fontSize=31.sp,fontWeight=FontWeight.SemiBold);Text("A LITTLE MORE ORGANIZED, EVERY DAY",color=Leaf,fontSize=9.sp,letterSpacing=1.5.sp) }
        FilledIconButton(onClick={editing=null;editor=true},colors=IconButtonDefaults.filledIconButtonColors(containerColor=Forest,contentColor=Cream)){Icon(Icons.Default.Add,"Add order")}
       }
       Spacer(Modifier.height(16.dp))
       Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Overview","Orders","Finances","Nurseries","Print","Settings").forEach{FilterChip(selected=page==it,onClick={page=it},label={Text(it,fontSize=12.sp)})}}
       Spacer(Modifier.height(12.dp))
       when(page){
        "Overview"->Overview(orders,{editing=it;editor=true},{o,v->lifecycleScope.launch{dao.setReceived(o.id,v)}})
        "Finances"->Finance(orders)
        "Nurseries"->NurseryList(nurseries,{selectedNursery=it},{nurseryToEdit=null;nurseryEditor=true})
        "Print"->PrintAddresses(orders,fromAddress,{page="Orders"}){selectedOrders->
         runCatching {
          printPdf=AddressLabelPdf.create(this@MainActivity,selectedOrders,fromAddress)
         }.onFailure { android.widget.Toast.makeText(this@MainActivity,it.message ?: "Could not create PDF",android.widget.Toast.LENGTH_LONG).show() }
        }
        "Settings"->SenderAddressSettings(fromAddress){value->
         getSharedPreferences("floral_settings", MODE_PRIVATE).edit().putString("from_address",value).apply()
         fromAddress=value
         android.widget.Toast.makeText(this@MainActivity,"Sender address saved",android.widget.Toast.LENGTH_SHORT).show()
        }
        else->OrderList(orders,{editing=it;editor=true},{o,v->lifecycleScope.launch{dao.setReceived(o.id,v)}})
       }
      }
     }
    }
   }
  }
 }
}

@Composable private fun Overview(orders:List<Order>,open:(Order)->Unit,toggle:(Order,Boolean)->Unit){
 val now=Calendar.getInstance()
 fun inPeriod(o:Order,p:Int):Boolean {val c=Calendar.getInstance().apply{timeInMillis=o.dispatchedAt?:o.createdAt};return when(p){Calendar.WEEK_OF_YEAR->c.get(Calendar.YEAR)==now.get(Calendar.YEAR)&&c.get(Calendar.WEEK_OF_YEAR)==now.get(Calendar.WEEK_OF_YEAR);Calendar.MONTH->c.get(Calendar.YEAR)==now.get(Calendar.YEAR)&&c.get(Calendar.MONTH)==now.get(Calendar.MONTH);else->c.get(Calendar.YEAR)==now.get(Calendar.YEAR)}}
 val complete=orders.filter{it.financialsComplete}
 Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
  Card(colors=CardDefaults.cardColors(containerColor=Forest),shape=RoundedCornerShape(23.dp)){Column(Modifier.fillMaxWidth().padding(20.dp)){Text("OVERALL PROFIT",color=Leaf,fontSize=11.sp,letterSpacing=1.3.sp);Text(money(complete.sumOf{it.profit?:0.0}),color=Cream,fontSize=34.sp,fontWeight=FontWeight.Bold);Text("${complete.size} orders with complete financial details",color=Muted,fontSize=12.sp)}}
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Week" to Calendar.WEEK_OF_YEAR,"Month" to Calendar.MONTH,"Year" to Calendar.YEAR).forEach{(label,p)->Card(Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(11.dp)){Text("This $label",color=Leaf,fontSize=10.sp);Text(money(complete.filter{inPeriod(it,p)}.sumOf{it.profit?:0.0}),color=Cream,fontWeight=FontWeight.SemiBold,fontSize=14.sp)}}}}
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Recent orders",color=Cream,fontWeight=FontWeight.SemiBold,fontSize=17.sp);Text("${orders.size} total",color=Muted,fontSize=11.sp)}
  if(orders.isEmpty()) Empty("Your first order starts here","Tap + or use the Floral widget to add one.")
  else LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=20.dp)){items(orders.take(6),key={it.id}){o->OrderCard(o,{open(o)},{toggle(o,!o.isReceived)})}}
 }
}
@Composable private fun OrderList(orders:List<Order>,open:(Order)->Unit,toggle:(Order,Boolean)->Unit){
 var q by remember{mutableStateOf("")};var filter by remember{mutableStateOf("All")}
 val visible=orders.filter{listOfNotNull(it.plantName,it.customerName,it.phone).any{v->v.contains(q,true)}&&(filter=="All"||(filter=="Received"&&it.isReceived)||(filter=="Pending"&&!it.isReceived))}
 Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
  OutlinedTextField(q,{q=it},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search plant, customer or phone")},leadingIcon={Icon(Icons.Default.Search,null)})
  Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("All","Pending","Received").forEach{FilterChip(selected=filter==it,onClick={filter=it},label={Text(it)})}}
  if(visible.isEmpty()) Empty("No matching orders","Try another search or add a new order.")
  else LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=20.dp)){items(visible,key={it.id}){o->OrderCard(o,{open(o)},{toggle(o,!o.isReceived)})}}
 }
}
@Composable private fun OrderCard(o:Order,open:()->Unit,toggle:()->Unit){
 Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)){
  Row(Modifier.fillMaxWidth().clickable(onClick=open).padding(12.dp),verticalAlignment=Alignment.CenterVertically){
   if(o.photoUri!=null) AsyncImage(Uri.parse(o.photoUri),null,Modifier.size(58.dp)) else Box(Modifier.size(58.dp).background(Forest,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Spa,null,tint=Leaf)}
   Spacer(Modifier.width(11.dp))
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
    Text(o.plantName?.ifBlank{"Unnamed plant"}?:"Unnamed plant",color=Cream,fontWeight=FontWeight.SemiBold)
    Text(o.customerName?:"Customer not entered",color=Leaf,fontSize=12.sp)
    Text(if(o.dispatchedAt!=null)"Dispatched · ${date(o.dispatchedAt)}" else o.expectedDispatchAt?.let{"Dispatch due · ${date(it)}"}?:"Dispatch date not set",color=Muted,fontSize=10.sp)
    Text(if(o.missingFields.isEmpty())"All details complete" else "${o.missingFields.size} details left",color=Muted,fontSize=10.sp)
    if(o.notes.isNotBlank())Text("Note: ${o.notes}",color=Cream,fontSize=10.sp,maxLines=1)
   }
   Column(horizontalAlignment=Alignment.CenterHorizontally){Checkbox(o.isReceived,{toggle()},colors=CheckboxDefaults.colors(checkedColor=Leaf));Text("Received",color=if(o.isReceived)Leaf else Muted,fontSize=9.sp)}
  }
 }
}
@Composable private fun Finance(orders:List<Order>){
 val complete=orders.filter{it.financialsComplete}
 Column(verticalArrangement=Arrangement.spacedBy(11.dp)){
  Text("Financial overview",color=Cream,fontSize=23.sp,fontWeight=FontWeight.SemiBold)
  Metric("Total order value · plant price + courier",orders.mapNotNull{it.totalValue}.sum())
  Metric("Recorded profit",complete.sumOf{it.profit?:0.0})
  Text("Profit = selling price − actual plant cost. Courier is counted in the customer's total and treated as a pass-through charge. Only complete financial records count toward profit.",color=Muted,fontSize=11.sp)
  Text("Order breakdown",color=Cream,fontWeight=FontWeight.SemiBold)
  if(orders.isEmpty())Empty("No financial records yet","Order totals will appear here.")
  else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=18.dp)){items(orders,key={it.id}){o->Card(colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.fillMaxWidth().padding(13.dp)){Text(o.plantName?:"Unnamed plant",color=Cream,fontWeight=FontWeight.SemiBold);Text("Order total: ${o.totalValue?.let(::money)?:"Awaiting courier + selling price"}",color=Leaf,fontSize=12.sp);Text("Profit: ${if(o.financialsComplete)money(o.profit?:0.0) else "Pending details"}",color=Muted,fontSize=12.sp)}}}}
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
 Column(Modifier.fillMaxSize().padding(16.dp)){
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
   items(plants,key={it.id}){p->Card(Modifier.fillMaxWidth().clickable{onEditPlant(p)},colors=CardDefaults.cardColors(containerColor=Panel)){Row(Modifier.padding(11.dp),verticalAlignment=Alignment.CenterVertically){if(p.photoUri!=null)AsyncImage(Uri.parse(p.photoUri),null,Modifier.size(70.dp))else Box(Modifier.size(70.dp).background(Forest,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Spa,null,tint=Leaf)};Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(p.name?: "Unnamed plant",color=Cream,fontWeight=FontWeight.SemiBold);Text(p.price?.let{money(it)}?:"Price not entered",color=Leaf,fontSize=12.sp);if(p.notes.isNotBlank())Text(p.notes,color=Muted,fontSize=10.sp,maxLines=2)};IconButton(onClick={onDeletePlant(p)}){Icon(Icons.Default.DeleteOutline,null,tint=Muted)}}}}
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
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
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
 var name by remember(existing?.id){mutableStateOf(existing?.name.orEmpty())};var price by remember(existing?.id){mutableStateOf(existing?.price?.toString().orEmpty())};var notes by remember(existing?.id){mutableStateOf(existing?.notes.orEmpty())};var photo by remember(existing?.id){mutableStateOf(existing?.photoUri.orEmpty())}
 val picker=androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null){runCatching{context.contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)};photo=u.toString()}}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(if(existing==null)"Add nursery plant" else "Edit plant",color=Cream,fontSize=24.sp,fontWeight=FontWeight.SemiBold);TextButton(onClick=cancel){Text("Cancel")}}
  Text("Plant details are optional. This entry will be saved under its nursery.",color=Leaf,fontSize=12.sp)
  if(photo.isNotBlank())AsyncImage(Uri.parse(photo),null,Modifier.fillMaxWidth().heightIn(max=220.dp))
  OutlinedButton(onClick={picker.launch(arrayOf("image/*"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Image,null);Text(if(photo.isBlank())"Choose plant photo" else "Change plant photo")}
  if(photo.isNotBlank())TextButton(onClick={photo=""}){Text("Remove photo")}
  Field("Plant name",name){name=it};Field("Nursery price (₹)",price,true,KeyboardType.Decimal){price=it};Field("Plant notes",notes,false){notes=it}
  Button(onClick={save(NurseryPlant(id=existing?.id?:0,nurseryId=nurseryId,name=name.trim().ifBlank{null},price=price.toDoubleOrNull(),photoUri=photo.ifBlank{null},notes=notes.trim(),createdAt=existing?.createdAt?:System.currentTimeMillis()))},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Leaf,contentColor=Deep)){Text("Save plant",fontWeight=FontWeight.SemiBold)}
 }
}

@Composable private fun OrderEditor(order:Order?,cancel:()->Unit,save:(Order)->Unit){
 var name by remember{mutableStateOf(order?.customerName.orEmpty())};var phone by remember{mutableStateOf(order?.phone.orEmpty())};var address by remember{mutableStateOf(order?.address.orEmpty())};var plant by remember{mutableStateOf(order?.plantName.orEmpty())};var photo by remember{mutableStateOf(order?.photoUri.orEmpty())}
 var cost by remember{mutableStateOf(order?.plantCost?.toString().orEmpty())};var selling by remember{mutableStateOf(order?.sellingPrice?.toString().orEmpty())};var finalPrice by remember{mutableStateOf(order?.finalPrice?.toString().orEmpty())};var courier by remember{mutableStateOf(order?.courierCharge?.toString().orEmpty())}
 var expected by remember{mutableStateOf(order?.expectedDispatchAt)};var dispatched by remember{mutableStateOf(order?.dispatchedAt)};var notes by remember{mutableStateOf(order?.notes.orEmpty())};var includeFrom by remember{mutableStateOf(order?.includeFromAddress?:false)}
 val context=androidx.compose.ui.platform.LocalContext.current
 val picker=androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){runCatching{context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)};photo=uri.toString()}}
 val missing=buildList{if(name.isBlank())add("Customer name");if(phone.isBlank())add("Phone number");if(address.isBlank())add("Delivery address");if(plant.isBlank())add("Plant name");if(photo.isBlank())add("Plant photograph");if(cost.toDoubleOrNull()==null)add("Actual plant cost");if(selling.toDoubleOrNull()==null)add("Selling price");if(finalPrice.toDoubleOrNull()==null)add("Final customer price");if(courier.toDoubleOrNull()==null)add("Courier charges");if(expected==null)add("Expected dispatch date");if(dispatched==null)add("Actual dispatch date")}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
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
