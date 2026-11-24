package com.floral.app

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.floral.app.data.Order
import java.io.File
import java.io.FileOutputStream

object AddressLabelPdf {
    fun create(context: Context, orders: List<Order>, savedFromAddress: String): File {
        require(orders.isNotEmpty()) { "Select at least one order." }
        val pageWidth = 595
        val pageHeight = 842
        val margin = 28f
        val gap = 14f
        val colWidth = (pageWidth - margin * 2 - gap) / 2f
        val pdf = PdfDocument()
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 9.5f }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 10f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textSize = 8f }
        fun wrap(text: String, paint: Paint, width: Float): List<String> {
            if (text.isBlank()) return listOf("")
            val result = mutableListOf<String>()
            var line = ""
            text.split(Regex("\\s+")).forEach { originalWord ->
                var word = originalWord
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) <= width) {
                    line = candidate
                    return@forEach
                }
                if (line.isNotEmpty()) {
                    result.add(line)
                    line = ""
                }
                // Split unusually long unbroken strings (for example, long email-like
                // identifiers or copied address tokens) instead of drawing beyond the page.
                while (paint.measureText(word) > width) {
                    val count = paint.breakText(word, true, width, null).coerceAtLeast(1)
                    result.add(word.substring(0, count))
                    word = word.substring(count)
                }
                line = word
            }
            if (line.isNotEmpty()) result.add(line)
            return result
        }
        data class Label(val fromLines: List<String>, val toLines: List<String>, val height: Float)
        val labels = orders.map { order ->
            val from = if (order.includeFromAddress && savedFromAddress.isNotBlank()) savedFromAddress.split(Regex("\\r?\\n")).flatMap { wrap(it, body, colWidth - 22f) } else emptyList()
            val toText = listOfNotNull(
                order.customerName?.takeIf { it.isNotBlank() },
                order.address?.takeIf { it.isNotBlank() },
                order.phone?.takeIf { it.isNotBlank() }?.let { "Phone: $it" }
            ).joinToString("\\n")
            val to = toText.split("\\n").flatMap { wrap(it, body, colWidth - 22f) }
            Label(from, to, (58f + (from.size + to.size) * 12f).coerceAtLeast(112f))
        }

        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin
        fun finishPage() {
            pdf.finishPage(page)
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin
        }

        labels.chunked(2).forEach { row ->
            val rowHeight = row.maxOf { it.height }
            if (y + rowHeight > pageHeight - margin) finishPage()
            row.forEachIndexed { index, label ->
                val left = margin + index * (colWidth + gap)
                val top = y
                val bottom = top + rowHeight
                val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.LTGRAY
                    style = Paint.Style.STROKE
                    strokeWidth = 0.8f
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 3f), 0f)
                }
                canvas.drawRect(left, top, left + colWidth, bottom, cut)
                var textY = top + 16f
                if (label.fromLines.isNotEmpty()) {
                    canvas.drawText("FROM", left + 10f, textY, heading)
                    textY += 13f
                    label.fromLines.forEach { canvas.drawText(it, left + 10f, textY, body); textY += 11f }
                    textY += 5f
                    canvas.drawLine(left + 10f, textY, left + colWidth - 10f, textY, small)
                    textY += 13f
                }
                canvas.drawText("SHIP TO", left + 10f, textY, heading)
                textY += 14f
                label.toLines.forEach { canvas.drawText(it, left + 10f, textY, body); textY += 12f }
            }
            y += rowHeight + gap
        }
        pdf.finishPage(page)
        val out = File(context.cacheDir, "floral-address-labels.pdf")
        FileOutputStream(out).use { pdf.writeTo(it) }
        pdf.close()
        return out
    }
}
