package com.summed.deudores.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.summed.deudores.data.Estado
import com.summed.deudores.data.TipoMovimiento
import com.summed.deudores.data.etiqueta
import com.summed.deudores.data.serieDeSaldo
import com.summed.deudores.data.ultimoPago
import com.summed.deudores.ui.Ficha
import com.summed.deudores.ui.dinero
import com.summed.deudores.ui.fechaLargaDe
import com.summed.deudores.ui.horaDe
import java.io.File
import java.util.Calendar

/**
 * Reporte en PDF, dibujado a mano con PdfDocument.
 *
 * Solo se dibuja lo que se pide explicitamente, asi que la imagen de fondo de
 * la pantalla nunca acaba dentro del reporte: el PDF no hereda nada de la UI.
 * El logo si aparece, porque es del negocio y no decoracion.
 */
object ExportadorPdf {

    private const val ANCHO = 595   // A4 a 72 dpi
    private const val ALTO = 842
    private const val MARGEN = 40f

    private const val INDIGO = 0xFF4F46E5.toInt()
    private const val TINTA = 0xFF1F2937.toInt()
    private const val GRIS = 0xFF6B7280.toInt()
    private const val LINEA = 0xFFE5E7EB.toInt()
    private const val VERDE = 0xFF059669.toInt()
    private const val AMBAR = 0xFFD97706.toInt()
    private const val ROJO = 0xFFDC2626.toInt()

    private fun pintura(tam: Float, color: Int = TINTA, negrita: Boolean = false) = Paint().apply {
        isAntiAlias = true
        textSize = tam
        this.color = color
        typeface = if (negrita) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
    }

    fun generar(context: Context, fichas: List<Ficha>, logo: File?): File {
        val doc = PdfDocument()
        val orden = fichas.sortedByDescending { it.analisis.saldo }

        paginaResumen(doc, orden, logo)
        orden.forEach { paginaDeudor(doc, it) }

        val archivo = File(context.cacheDir, "reportes").apply { mkdirs() }
            .resolve("Deudores_${marcaTiempo()}.pdf")
        archivo.outputStream().use { doc.writeTo(it) }
        doc.close()
        return archivo
    }

    // ---------- pagina 1: resumen de cartera ----------

    private fun paginaResumen(doc: PdfDocument, fichas: List<Ficha>, logo: File?) {
        val pagina = doc.startPage(PdfDocument.PageInfo.Builder(ANCHO, ALTO, 1).create())
        val c = pagina.canvas
        var y = MARGEN

        y = encabezado(c, y, "Estado de cuentas por cobrar", logo)

        val porCobrar = fichas.sumOf { maxOf(0.0, it.analisis.saldo) }
        val cobrado = fichas.sumOf { it.pagado }
        val prestado = fichas.sumOf { it.prestado }
        val atrasado = fichas.filter { it.analisis.estado == Estado.ATRASADO }.sumOf { it.analisis.saldo }

        y += 18f
        y = bloqueTotales(
            c, y,
            listOf(
                Triple("POR COBRAR", dinero(porCobrar), INDIGO),
                Triple("COBRADO", dinero(cobrado), VERDE),
                Triple("ATRASADO", dinero(atrasado), ROJO),
                Triple("DEUDORES", "${fichas.size}", TINTA)
            )
        )

        val morosos = fichas.filter { it.analisis.estado == Estado.ATRASADO }
        if (morosos.isNotEmpty()) {
            y += 20f
            val texto = morosos.joinToString(", ") { "${it.deudor.nombre} (${it.analisis.diasMora} d)" }
            c.drawText(
                "Se pasaron de su fecha de pago: $texto".take(95),
                MARGEN, y, pintura(9f, ROJO)
            )
        }

        y += 30f
        c.drawText("RESUMEN DE CARTERA", MARGEN, y, pintura(8f, GRIS, true))
        y += 12f

        val cols = floatArrayOf(MARGEN, 200f, 280f, 360f, 430f, 520f)
        c.drawText("DEUDOR", cols[0], y, pintura(7.5f, GRIS, true))
        derecha(c, "PRESTADO", cols[2], y, pintura(7.5f, GRIS, true))
        derecha(c, "PAGADO", cols[3], y, pintura(7.5f, GRIS, true))
        derecha(c, "SALDO", cols[4], y, pintura(7.5f, GRIS, true))
        c.drawText("ESTADO", cols[4] + 12f, y, pintura(7.5f, GRIS, true))
        y += 4f
        c.drawLine(MARGEN, y, ANCHO - MARGEN, y, Paint().apply { color = GRIS; strokeWidth = 1f })
        y += 14f

        fichas.forEach { f ->
            c.drawText(f.deudor.nombre.take(24), cols[0], y, pintura(9f))
            derecha(c, dinero(f.prestado), cols[2], y, pintura(9f))
            derecha(c, dinero(f.pagado), cols[3], y, pintura(9f))
            derecha(c, dinero(maxOf(0.0, f.analisis.saldo)), cols[4], y, pintura(9f, TINTA, true))
            val e = f.analisis
            val txt = if (e.estado == Estado.ATRASADO) "${e.estado.etiqueta} (${e.diasMora} d)"
                      else e.estado.etiqueta
            c.drawText(txt, cols[4] + 12f, y, pintura(8.5f, colorEstado(e.estado)))
            y += 6f
            c.drawLine(MARGEN, y, ANCHO - MARGEN, y, Paint().apply { color = LINEA; strokeWidth = 0.7f })
            y += 14f
        }

        y += 2f
        c.drawText("Total", cols[0], y, pintura(9f, TINTA, true))
        derecha(c, dinero(prestado), cols[2], y, pintura(9f, TINTA, true))
        derecha(c, dinero(cobrado), cols[3], y, pintura(9f, TINTA, true))
        derecha(c, dinero(porCobrar), cols[4], y, pintura(9f, TINTA, true))

        pie(c, 1)
        doc.finishPage(pagina)
    }

    // ---------- una pagina por deudor ----------

    private fun paginaDeudor(doc: PdfDocument, f: Ficha) {
        val numero = doc.pages.size + 1
        var pagina = doc.startPage(PdfDocument.PageInfo.Builder(ANCHO, ALTO, numero).create())
        var c = pagina.canvas
        var y = MARGEN

        // Cabecera del deudor
        c.drawText(f.deudor.nombre, MARGEN, y, pintura(15f, TINTA, true))
        derecha(c, dinero(maxOf(0.0, f.analisis.saldo)), ANCHO - MARGEN, y, pintura(17f, TINTA, true))
        y += 13f
        c.drawText(textoEstado(f), MARGEN, y, pintura(9f, colorEstado(f.analisis.estado)))
        derecha(c, "SALDO PENDIENTE", ANCHO - MARGEN, y, pintura(7f, GRIS))
        y += 8f
        c.drawLine(MARGEN, y, ANCHO - MARGEN, y, Paint().apply { color = LINEA; strokeWidth = 1f })
        y += 20f

        // Desglose en dos columnas
        c.drawText("DESGLOSE DE LA DEUDA", MARGEN, y, pintura(8f, GRIS, true))
        y += 14f

        val cargos = f.movimientos.filter { it.tipo == TipoMovimiento.CARGO }
        val pagos = f.movimientos.filter { it.tipo == TipoMovimiento.PAGO }
        val inicial = cargos.minByOrNull { it.fecha }
        val extras = cargos.filter { it.id != inicial?.id }
        val medio = ANCHO / 2f

        var yIzq = y
        yIzq = filaDesglose(c, MARGEN, medio - 20f, yIzq, "Préstamo inicial", inicial?.let { dinero(it.monto) } ?: "$0.00")
        yIzq = filaDesglose(c, MARGEN, medio - 20f, yIzq, "Fecha del préstamo", inicial?.let { fechaLargaDe(it.fecha) } ?: "sin registro")
        yIzq = filaDesglose(
            c, MARGEN, medio - 20f, yIzq,
            if (extras.isEmpty()) "Préstamos posteriores" else "Préstamos posteriores (${extras.size})",
            if (extras.isEmpty()) "ninguno" else "+ ${dinero(extras.sumOf { it.monto })}",
            if (extras.isEmpty()) TINTA else AMBAR
        )
        yIzq = filaDesglose(c, MARGEN, medio - 20f, yIzq, "Total prestado", dinero(f.prestado), TINTA, true)

        var yDer = y
        yDer = filaDesglose(
            c, medio, ANCHO - MARGEN, yDer,
            if (pagos.isEmpty()) "Pagos recibidos" else "Pagos recibidos (${pagos.size})",
            if (pagos.isEmpty()) "ninguno" else "− ${dinero(f.pagado)}",
            if (pagos.isEmpty()) TINTA else VERDE
        )
        yDer = filaDesglose(c, medio, ANCHO - MARGEN, yDer, "Pago promedio",
            if (pagos.isEmpty()) "$0.00" else dinero(f.pagado / pagos.size))
        yDer = filaDesglose(c, medio, ANCHO - MARGEN, yDer, "Último pago",
            f.movimientos.ultimoPago()?.let { fechaLargaDe(it) } ?: "todavía no paga")
        yDer = filaDesglose(c, medio, ANCHO - MARGEN, yDer, "Saldo pendiente",
            dinero(maxOf(0.0, f.analisis.saldo)), TINTA, true)

        y = maxOf(yIzq, yDer) + 22f

        // Grafico de evolucion del saldo
        c.drawText("EVOLUCIÓN DEL SALDO", MARGEN, y, pintura(8f, GRIS, true))
        y += 8f
        graficoLinea(c, RectF(MARGEN, y, ANCHO - MARGEN, y + 120f), f)
        y += 138f

        // Grafico de deuda y pagos por mes
        c.drawText("DEUDA Y PAGOS POR MES", MARGEN, y, pintura(8f, GRIS, true))
        y += 8f
        graficoBarras(c, RectF(MARGEN, y, ANCHO - MARGEN, y + 110f), f)
        y += 128f

        // Tabla de movimientos, con salto de pagina si no cabe
        c.drawText("MOVIMIENTOS", MARGEN, y, pintura(8f, GRIS, true))
        y += 12f
        c.drawText("FECHA", MARGEN, y, pintura(7.5f, GRIS, true))
        c.drawText("HORA", 170f, y, pintura(7.5f, GRIS, true))
        c.drawText("CONCEPTO", 220f, y, pintura(7.5f, GRIS, true))
        derecha(c, "MONTO", 400f, y, pintura(7.5f, GRIS, true))
        derecha(c, "SALDO", 480f, y, pintura(7.5f, GRIS, true))
        c.drawText("NOTA", 492f, y, pintura(7.5f, GRIS, true))
        y += 4f
        c.drawLine(MARGEN, y, ANCHO - MARGEN, y, Paint().apply { color = GRIS; strokeWidth = 1f })
        y += 13f

        val serie = f.movimientos.serieDeSaldo()
        if (serie.isEmpty()) {
            c.drawText("Sin movimientos registrados", MARGEN, y, pintura(9f, GRIS))
        }
        serie.forEach { (m, saldo) ->
            if (y > ALTO - 60f) {
                pie(c, doc.pages.size + 1)
                doc.finishPage(pagina)
                pagina = doc.startPage(PdfDocument.PageInfo.Builder(ANCHO, ALTO, doc.pages.size + 1).create())
                c = pagina.canvas
                y = MARGEN
                c.drawText("${f.deudor.nombre} (continuación)", MARGEN, y, pintura(10f, GRIS, true))
                y += 20f
            }
            val esCargo = m.tipo == TipoMovimiento.CARGO
            c.drawText(fechaLargaDe(m.fecha), MARGEN, y, pintura(8.5f))
            c.drawText(horaDe(m.fecha), 170f, y, pintura(8.5f, GRIS))
            c.drawText(if (esCargo) "Préstamo" else "Pago", 220f, y, pintura(8.5f))
            derecha(c, (if (esCargo) "+ " else "− ") + dinero(m.monto), 400f, y,
                pintura(8.5f, if (esCargo) AMBAR else VERDE, true))
            derecha(c, dinero(saldo), 480f, y, pintura(8.5f, TINTA, true))
            c.drawText(m.nota.take(18), 492f, y, pintura(7.5f, GRIS))
            y += 5f
            c.drawLine(MARGEN, y, ANCHO - MARGEN, y, Paint().apply { color = LINEA; strokeWidth = 0.6f })
            y += 13f
        }

        pie(c, doc.pages.size + 1)
        doc.finishPage(pagina)
    }

    // ---------- piezas reutilizables ----------

    private fun encabezado(c: Canvas, yInicial: Float, titulo: String, logo: File?): Float {
        var y = yInicial
        var xTexto = MARGEN

        if (logo != null && logo.exists()) {
            runCatching {
                val bmp = BitmapFactory.decodeFile(logo.absolutePath)
                if (bmp != null) {
                    val alto = 42f
                    val ancho = alto * bmp.width / bmp.height
                    c.drawBitmap(
                        bmp,
                        Rect(0, 0, bmp.width, bmp.height),
                        RectF(MARGEN, y - 12f, MARGEN + ancho, y - 12f + alto),
                        null
                    )
                    xTexto = MARGEN + ancho + 14f
                }
            }
        }

        c.drawText(titulo, xTexto, y + 14f, pintura(16f, TINTA, true))
        val ahora = Calendar.getInstance()
        derecha(c, "Generado el ${fechaLargaDe(ahora.timeInMillis)}", ANCHO - MARGEN, y + 6f, pintura(8f, GRIS))
        derecha(c, horaDe(ahora.timeInMillis), ANCHO - MARGEN, y + 17f, pintura(8f, GRIS))
        y += 32f
        c.drawLine(MARGEN, y, ANCHO - MARGEN, y, Paint().apply { color = INDIGO; strokeWidth = 1.6f })
        return y
    }

    private fun bloqueTotales(c: Canvas, yInicial: Float, datos: List<Triple<String, String, Int>>): Float {
        val y = yInicial
        val ancho = (ANCHO - 2 * MARGEN) / datos.size
        c.drawRect(
            RectF(MARGEN, y, ANCHO - MARGEN, y + 46f),
            Paint().apply { color = 0xFFF8F8FB.toInt() }
        )
        datos.forEachIndexed { i, (etiqueta, valor, color) ->
            val x = MARGEN + i * ancho + 12f
            c.drawText(etiqueta, x, y + 17f, pintura(7f, GRIS, true))
            c.drawText(valor, x, y + 36f, pintura(15f, color, true))
            if (i > 0) {
                val xl = MARGEN + i * ancho
                c.drawLine(xl, y + 8f, xl, y + 38f, Paint().apply { color = LINEA; strokeWidth = 0.8f })
            }
        }
        return y + 46f
    }

    private fun filaDesglose(
        c: Canvas, x0: Float, x1: Float, y: Float,
        etiqueta: String, valor: String, color: Int = TINTA, negrita: Boolean = false
    ): Float {
        c.drawText(etiqueta, x0, y, pintura(8.5f, GRIS))
        derecha(c, valor, x1, y, pintura(8.5f, color, negrita))
        c.drawLine(x0, y + 4f, x1, y + 4f, Paint().apply { this.color = LINEA; strokeWidth = 0.6f })
        return y + 16f
    }

    private fun graficoLinea(c: Canvas, r: RectF, f: Ficha) {
        val serie = f.movimientos.serieDeSaldo()
        marco(c, r)
        if (serie.size < 2) {
            c.drawText("Un solo movimiento, no hay evolución que mostrar", r.left + 8f, r.centerY(), pintura(8f, GRIS))
            return
        }
        val maximo = serie.maxOf { it.second }.takeIf { it > 0 } ?: 1.0
        val paso = r.width() / (serie.size - 1)
        val trazo = Paint().apply { isAntiAlias = true; color = INDIGO; strokeWidth = 2f; style = Paint.Style.STROKE }
        val punto = Paint().apply { isAntiAlias = true; color = INDIGO }

        var xAnterior = 0f; var yAnterior = 0f
        serie.forEachIndexed { i, (_, saldo) ->
            val x = r.left + i * paso
            val yy = r.bottom - (saldo / maximo).toFloat() * (r.height() - 14f)
            if (i > 0) c.drawLine(xAnterior, yAnterior, x, yy, trazo)
            c.drawCircle(x, yy, 2.6f, punto)
            xAnterior = x; yAnterior = yy
        }
        c.drawText(dinero(maximo), r.left + 3f, r.top + 10f, pintura(7f, GRIS))
        c.drawText(dinero(serie.last().second), r.right - 46f, r.bottom - 4f, pintura(7f, GRIS))
    }

    private fun graficoBarras(c: Canvas, r: RectF, f: Ficha) {
        marco(c, r)
        val porMes = LinkedHashMap<String, Pair<Double, Double>>()
        f.movimientos.sortedBy { it.fecha }.forEach { m ->
            val cal = Calendar.getInstance().apply { timeInMillis = m.fecha }
            val clave = "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.YEAR) % 100}"
            val (cargo, pago) = porMes[clave] ?: (0.0 to 0.0)
            porMes[clave] = if (m.tipo == TipoMovimiento.CARGO) (cargo + m.monto) to pago
                            else cargo to (pago + m.monto)
        }
        if (porMes.isEmpty()) return

        val maximo = porMes.values.maxOf { maxOf(it.first, it.second) }.takeIf { it > 0 } ?: 1.0
        val anchoGrupo = r.width() / porMes.size
        // Se limita el ancho de barra: con uno o dos meses, repartir todo el
        // espacio da barras enormes, que es justo lo que se corrigio en la web.
        val anchoBarra = minOf(anchoGrupo / 3f, 22f)

        porMes.entries.forEachIndexed { i, (mes, valores) ->
            val centro = r.left + i * anchoGrupo + anchoGrupo / 2f
            val (cargo, pago) = valores
            barra(c, centro - anchoBarra - 2f, r, (cargo / maximo).toFloat(), anchoBarra, AMBAR)
            barra(c, centro + 2f, r, (pago / maximo).toFloat(), anchoBarra, VERDE)
            c.drawText(mes, centro - 10f, r.bottom + 10f, pintura(7f, GRIS))
        }
        c.drawText("Deuda", r.left + 4f, r.top + 10f, pintura(7f, AMBAR, true))
        c.drawText("Pagos", r.left + 38f, r.top + 10f, pintura(7f, VERDE, true))
    }

    private fun barra(c: Canvas, x: Float, r: RectF, fraccion: Float, ancho: Float, color: Int) {
        if (fraccion <= 0f) return
        val alto = fraccion.coerceIn(0f, 1f) * (r.height() - 18f)
        c.drawRect(
            RectF(x, r.bottom - alto, x + ancho, r.bottom),
            Paint().apply { this.color = color; isAntiAlias = true }
        )
    }

    private fun marco(c: Canvas, r: RectF) {
        c.drawLine(r.left, r.bottom, r.right, r.bottom, Paint().apply { color = LINEA; strokeWidth = 0.8f })
    }

    private fun derecha(c: Canvas, texto: String, x: Float, y: Float, p: Paint) {
        c.drawText(texto, x - p.measureText(texto), y, p)
    }

    private fun pie(c: Canvas, numero: Int) {
        c.drawLine(MARGEN, ALTO - 34f, ANCHO - MARGEN, ALTO - 34f,
            Paint().apply { color = LINEA; strokeWidth = 0.6f })
        c.drawText("Sistema de Control de Deudores", MARGEN, ALTO - 22f, pintura(7.5f, GRIS))
        derecha(c, "Página $numero", ANCHO - MARGEN, ALTO - 22f, pintura(7.5f, GRIS))
    }

    private fun textoEstado(f: Ficha): String {
        val a = f.analisis
        return when (a.estado) {
            Estado.PAGADO -> "Deuda saldada por completo"
            Estado.ATRASADO -> "Se pasó ${a.diasMora} días de su fecha de pago"
            Estado.POR_VENCER -> if (a.diasRestantes == 0) "Vence hoy" else "Vence en ${a.diasRestantes} días"
            Estado.AL_DIA -> "Paga el ${f.deudor.diaPago} de cada mes"
        }
    }

    private fun colorEstado(e: Estado): Int = when (e) {
        Estado.AL_DIA -> VERDE
        Estado.POR_VENCER -> AMBAR
        Estado.ATRASADO -> ROJO
        Estado.PAGADO -> INDIGO
    }

    private fun marcaTiempo(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
}
