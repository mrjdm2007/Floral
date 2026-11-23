package com.floral.app.widget
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
class FloralWidget : GlanceAppWidget() {
 @Composable override fun Content() {
  Row(GlanceModifier.fillMaxSize().background(Color(0xFF173C2B)).cornerRadius(18.dp).clickable(actionStartActivity<MainActivity>()).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
   Column(GlanceModifier.defaultWeight()) {
    Text("FLORAL", style = TextStyle(color = ColorProvider(Color(0xFFB8D9BE)), fontSize = 11.sp))
    Text("＋ Add new order", style = TextStyle(color = ColorProvider(Color.White), fontSize = 16.sp))
   }
  }
 }
}
class FloralWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = FloralWidget() }