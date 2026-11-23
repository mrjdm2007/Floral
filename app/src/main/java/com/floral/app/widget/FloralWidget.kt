package com.floral.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.floral.app.MainActivity

class FloralWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }

    @Composable
    private fun WidgetContent() {
        val context = androidx.glance.LocalContext.current
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("quick_add", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        Row(
            modifier = GlanceModifier.fillMaxSize()
                .background(Color(0xFF173C2B))
                .clickable(actionStartActivity(intent))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Text("FLORAL", style = TextStyle(color = ColorProvider(Color(0xFFA8C9A7)), fontSize = 11.sp))
                Text("＋ Add new order", style = TextStyle(color = ColorProvider(Color(0xFFF3EAD2)), fontSize = 16.sp))
                Text("Quick order entry", style = TextStyle(color = ColorProvider(Color(0xFFD0DED2)), fontSize = 10.sp))
            }
        }
    }
}

class FloralWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FloralWidget()
}
