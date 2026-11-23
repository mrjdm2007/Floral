package com.floral.app

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val PreviewDeep = Color(0xFF0B1B13)
private val PreviewForest = Color(0xFF173C2B)
private val PreviewPanel = Color(0xFF142D20)
private val PreviewLeaf = Color(0xFFA8C9A7)
private val PreviewCream = Color(0xFFF3EAD2)
private val PreviewMuted = Color(0xFFB5C5B8)

@Composable
fun PdfPreviewScreen(file: File, onBack: () -> Unit, onPrint: (File) -> Unit) {
    val pageCount by produceState<Int?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(descriptor).use { it.pageCount }
            }.getOrNull()
        }
    }

    Column(Modifier.fillMaxSize().background(PreviewDeep)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PreviewCream)
            }
            Column(Modifier.weight(1f)) {
                Text("PDF preview", color = PreviewCream, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (pageCount == null) "Preparing pages…" else "$pageCount A4 ${if (pageCount == 1) "page" else "pages"}",
                    color = PreviewLeaf,
                    fontSize = 12.sp
                )
            }
            Icon(Icons.Default.Description, contentDescription = null, tint = PreviewLeaf)
        }

        when {
            pageCount == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PreviewLeaf)
            }
            pageCount == 0 -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("The PDF could not be opened. Go back and try again.", color = PreviewMuted)
            }
            else -> LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items((0 until (pageCount ?: 0)).toList(), key = { it }) { index ->
                    PdfPreviewPage(file, index)
                }
            }
        }

        Surface(color = PreviewForest, shadowElevation = 8.dp) {
            Button(
                onClick = { onPrint(file) },
                enabled = (pageCount ?: 0) > 0,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PreviewLeaf,
                    contentColor = PreviewDeep,
                    disabledContainerColor = PreviewPanel,
                    disabledContentColor = PreviewMuted
                )
            ) {
                Icon(Icons.Default.Print, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Print / Share PDF", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PdfPreviewPage(file: File, pageIndex: Int) {
    val bitmap by produceState<Bitmap?>(initialValue = null, file, pageIndex) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(descriptor).use { renderer ->
                    renderer.openPage(pageIndex).use { page ->
                        val targetWidth = 1000
                        val targetHeight = (targetWidth.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also {
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }.getOrNull()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "PDF page ${pageIndex + 1}",
                modifier = Modifier.fillMaxWidth().padding(5.dp).clip(RoundedCornerShape(3.dp))
            )
        } else {
            Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PreviewForest)
            }
        }
    }
    Text("PAGE ${pageIndex + 1}", color = PreviewMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
}
