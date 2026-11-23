package com.floral.app.widget
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.floral.app.MainActivity

class FloralWidget:GlanceAppWidget(){
 @Composable override fun Content(){
  val context=androidx.glance.LocalContext.current
  val intent=Intent(context,MainActivity::class.java).putExtra("quick_add",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
  Row(GlanceModifier.fillMaxSize().background(Color(0xFF173C2B)).cornerRadius(18.dp).clickable(actionStartActivity(intent)).padding(horizontal=14.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
   Column(GlanceModifier.defaultWeight()){
    Text("FLORAL",style=TextStyle(color=ColorProvider(Color(0xFFA8C9A7)),fontSize=11.sp))
    Text("＋ Add new order",style=TextStyle(color=ColorProvider(Color(0xFFF3EAD2)),fontSize=16.sp))
    Text("Quick order entry",style=TextStyle(color=ColorProvider(Color(0xFFD0DED2)),fontSize=10.sp))
   }
  }
 }
}
class FloralWidgetReceiver:GlanceAppWidgetReceiver(){override val glanceAppWidget:GlanceAppWidget=FloralWidget()}
