package com.example.nina

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Planche de communication imprimable (PDF, A4 paysage) : un secours sans tablette (batterie vide, bain, piscine…).
 * Les pictos sont aux mêmes places que sur l'écran (même ordre, même nombre par rangée), pour que Nina garde ses repères.
 * Page d'accueil : dossiers à gauche, grille au centre, phrases rapides à droite ; puis une page par dossier.
 */

/** Une case de la planche : quel picto (index dans la liste) et où, en points PDF. */
data class BoardSlot(val index: Int, val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * Découpe une grille en pages, avec la même règle que l'écran (computeGridLayout : une case garde au moins 65 %
 * de sa largeur en hauteur). Fonction pure, testée sans Android.
 */
fun boardPages(count: Int, columns: Int, left: Float, top: Float, width: Float, height: Float, gap: Float): List<List<BoardSlot>> {
    if (count <= 0) return emptyList()
    val layout = computeGridLayout(count, columns, width, height, gap, 0f)
    val cellW = (width - gap * (columns - 1)) / columns
    val cellH = (height - gap * (layout.rows - 1)) / layout.rows
    return (0 until layout.pageCount).map { page ->
        (0 until layout.perPage).mapNotNull { slot ->
            val index = page * layout.perPage + slot
            if (index >= count) return@mapNotNull null
            val row = slot / columns; val col = slot % columns
            BoardSlot(index, left + col * (cellW + gap), top + row * (cellH + gap), cellW, cellH)
        }
    }
}

object PrintBoard {
    // A4 paysage, en points (1/72 de pouce)
    private const val PAGE_W = 842; private const val PAGE_H = 595
    private const val MARGIN = 24f; private const val HEADER = 30f; private const val GAP = 8f
    private const val SIDE = 112f // Largeur des colonnes Dossiers et Phrases rapides

    /** Fabrique le PDF dans le cache de l'app (pour l'imprimer ou le partager). */
    suspend fun build(
        context: Context, home: List<AACCell>, left: List<AACCell>, quick: List<AACCell>,
        columns: Int, includeFolders: Boolean, includeQuick: Boolean
    ): File = withContext(Dispatchers.IO) {
        val images = mutableMapOf<String, Bitmap?>()
        suspend fun image(cell: AACCell): Bitmap? {
            val data: Any = cell.imageUrl?.takeIf { it.isNotBlank() } ?: cell.imageRes ?: return null
            val key = data.toString()
            if (key !in images) images[key] = runCatching {
                val request = ImageRequest.Builder(context).data(data).size(400).allowHardware(false).build()
                (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
            }.getOrNull()
            return images[key]
        }

        val doc = PdfDocument()
        var pageNumber = 0
        val date = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH).format(Date())
        fun newPage(title: String): PdfDocument.Page {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNumber).create())
            val c = page.canvas
            c.drawText(title, MARGIN, MARGIN + 14f, textPaint(18f, bold = true))
            val footer = "Planche de communication de Nina — $date"
            c.drawText(footer, PAGE_W - MARGIN - textPaint(9f).measureText(footer), PAGE_H - 10f, textPaint(9f, color = 0xFF888888.toInt()))
            return page
        }

        // Accueil : même disposition que l'écran
        val showQuick = includeQuick && quick.isNotEmpty()
        val gridLeft = MARGIN + if (left.isNotEmpty()) SIDE + GAP * 2 else 0f
        val gridRight = PAGE_W - MARGIN - if (showQuick) SIDE + GAP * 2 else 0f
        val top = MARGIN + HEADER; val bottom = PAGE_H - MARGIN
        val homePages = boardPages(home.size, columns, gridLeft, top, gridRight - gridLeft, bottom - top, GAP).ifEmpty { listOf(emptyList()) }
        homePages.forEachIndexed { i, slots ->
            val page = newPage(if (homePages.size > 1) "Accueil (${i + 1}/${homePages.size})" else "Accueil")
            val c = page.canvas
            if (left.isNotEmpty()) drawColumn(c, left, MARGIN, top, bottom) { image(it) }
            if (showQuick) drawColumn(c, quick, PAGE_W - MARGIN - SIDE, top, bottom) { image(it) }
            slots.forEach { s -> drawCell(c, home[s.index], RectF(s.left, s.top, s.left + s.width, s.top + s.height), image(home[s.index])) }
            doc.finishPage(page)
        }

        // Une page (ou plus) par dossier, sous-dossiers compris
        if (includeFolders) {
            val folders = mutableListOf<Pair<String, AACCell>>()
            fun collect(cells: List<AACCell>, path: String) {
                cells.filter { it.isFolder && it.content.isNotEmpty() && !it.isHidden }.forEach { f ->
                    val name = if (path.isEmpty()) f.label else "$path › ${f.label}"
                    folders += name to f
                    collect(f.content, name)
                }
            }
            collect(left + home, "")
            folders.distinctBy { it.second.id }.forEach { (name, folder) ->
                val pages = boardPages(folder.content.size, columns, MARGIN, top, PAGE_W - 2 * MARGIN, bottom - top, GAP)
                pages.forEachIndexed { i, slots ->
                    val page = newPage("Dossier : $name" + if (pages.size > 1) " (${i + 1}/${pages.size})" else "")
                    slots.forEach { s ->
                        val cell = folder.content[s.index]
                        drawCell(page.canvas, cell, RectF(s.left, s.top, s.left + s.width, s.top + s.height), image(cell))
                    }
                    doc.finishPage(page)
                }
            }
        }

        val file = File(context.cacheDir, "planche_nina.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        file
    }

    /** Colonne latérale (dossiers ou phrases rapides) : cases empilées, aussi hautes que possible (90 points au plus). */
    private suspend fun drawColumn(c: Canvas, cells: List<AACCell>, x: Float, top: Float, bottom: Float, image: suspend (AACCell) -> Bitmap?) {
        val visible = cells.filter { !it.isHidden }
        if (visible.isEmpty()) return
        val h = minOf(90f, (bottom - top - GAP * (visible.size - 1)) / visible.size)
        visible.forEachIndexed { i, cell ->
            val y = top + i * (h + GAP)
            drawCell(c, cell, RectF(x, y, x + SIDE, y + h), image(cell))
        }
    }

    /** Une case : fond de la couleur du picto, image dans un cadre blanc, mot en dessous. Case masquée : place vide. */
    private fun drawCell(c: Canvas, cell: AACCell, r: RectF, image: Bitmap?) {
        if (cell.isHidden) return
        val radius = minOf(r.width(), r.height()) * 0.08f
        c.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cell.colorLong.toInt() })
        if (cell.isFolder) c.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.5f; color = 0xFF8D6E63.toInt()
        })
        // Mot : le plus grand possible sur une ligne
        val labelSize = (r.height() * 0.15f).coerceIn(6f, 18f)
        val label = cell.label.uppercase(Locale.FRENCH)
        val paint = textPaint(labelSize, bold = true)
        while (paint.measureText(label) > r.width() - 6f && paint.textSize > 5f) paint.textSize -= 0.5f
        val labelTop = r.bottom - labelSize - 6f
        c.drawText(label, r.centerX() - paint.measureText(label) / 2, r.bottom - 6f, paint)
        // Image dans un cadre blanc au-dessus du mot
        val pad = minOf(r.width(), r.height()) * 0.06f
        val box = RectF(r.left + pad, r.top + pad, r.right - pad, labelTop - pad / 2)
        if (box.height() <= 4f || box.width() <= 4f) return
        c.drawRoundRect(box, radius * 0.7f, radius * 0.7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
        image?.let { bmp ->
            val scale = minOf((box.width() - 4f) / bmp.width, (box.height() - 4f) / bmp.height)
            val w = bmp.width * scale; val h = bmp.height * scale
            val dst = RectF(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2)
            c.drawBitmap(bmp, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

    private fun textPaint(size: Float, bold: Boolean = false, color: Int = android.graphics.Color.BLACK) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    /** Ouvre la fenêtre d'impression d'Android (imprimante, ou « Enregistrer au format PDF »). */
    fun print(context: Context, file: File) {
        val activity = context.findActivity() ?: return
        val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print("Planche Nina", object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback, extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
                callback.onLayoutFinished(
                    PrintDocumentInfo.Builder("planche_nina.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true
                )
            }

            override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?, callback: WriteResultCallback) {
                try {
                    file.inputStream().use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape()).build())
    }

    /** Envoie le PDF (mail, Drive, messagerie…), par exemple à l'école ou à l'orthophoniste. */
    fun share(context: Context, file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Planche de communication de Nina")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Envoyer la planche via :"))
    }
}

private fun Context.findActivity(): android.app.Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

/** Fenêtre « Planche imprimable » : réglages, puis impression ou partage du PDF. */
@Composable
fun PrintBoardDialog(home: List<AACCell>, left: List<AACCell>, quick: List<AACCell>, defaultColumns: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var columns by remember { mutableIntStateOf(defaultColumns.coerceIn(2, 8)) }
    var includeFolders by remember { mutableStateOf(true) }
    var includeQuick by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    fun make(then: (File) -> Unit) {
        busy = true
        scope.launch {
            val file = runCatching { PrintBoard.build(context, home, left, quick, columns, includeFolders, includeQuick) }.getOrNull()
            busy = false
            if (file != null) then(file)
            else android.widget.Toast.makeText(context, "Impossible de fabriquer la planche", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("🖨 Planche imprimable") },
        text = {
            HideSystemBarsInDialog()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Une version papier des grilles, pour communiquer sans la tablette (batterie vide, bain, sorties…). Les pictos sont aux mêmes places qu'à l'écran.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pictos par rangée", modifier = Modifier.weight(1f))
                    Slider(value = columns.toFloat(), onValueChange = { columns = it.toInt() }, valueRange = 2f..8f, steps = 5, modifier = Modifier.width(200.dp))
                    Text("$columns", fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                }
                Text("Même nombre qu'à l'écran conseillé : Nina retrouve ses repères.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeFolders, onCheckedChange = { includeFolders = it })
                    Text("Une page par dossier")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeQuick, onCheckedChange = { includeQuick = it })
                    Text("Phrases rapides sur la page d'accueil")
                }
                if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text("Préparation de la planche…")
                }
            }
        },
        confirmButton = {
            Button(onClick = { make { PrintBoard.print(context, it) } }, enabled = !busy) { Text("Imprimer") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { make { PrintBoard.share(context, it) } }, enabled = !busy) { Text("Partager le PDF") }
                TextButton(onClick = onDismiss, enabled = !busy) { Text("Fermer") }
            }
        }
    )
}
