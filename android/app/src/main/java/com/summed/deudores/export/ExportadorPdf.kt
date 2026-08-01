package com.summed.deudores.export

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.summed.deudores.data.Estado
import com.summed.deudores.data.TipoMovimiento
import com.summed.deudores.data.etiqueta
import com.summed.deudores.data.serieDeSaldo
import com.summed.deudores.data.ultimoPago
import com.summed.deudores.ui.Ficha
import com.summed.deudores.ui.dinero
import com.summed.deudores.ui.fechaCortaDe
import com.summed.deudores.ui.fechaLargaDe
import com.summed.deudores.ui.horaDe
import java.io.File
import java.util.Calendar

/**
 * Los tres reportes en PDF, dibujados a mano con PdfDocument y en el mismo
 * estilo "Papel" que la app: blanco, tinta y lineas de un pixel.
 *
 *  - [general]    todo junto, con la linea de tiempo de cada deuda.
 *  - [individual] el estado de cuenta de una sola persona.
 *  - [todos]      una ficha por deudor, dos por hoja.
 *
 * Solo se dibuja lo que se pide, asi que la imagen de fondo de la pantalla
 * nunca acaba dentro del reporte.
 */
object ExportadorPdf {

    private const val ANCHO = 595   // A4 a 72 dpi
    private const val ALTO = 842
    private const val M = 42f       // margen
    private const val DER = ANCHO - M

    private const val TINTA = 0xFF14181F.toInt()
    private const val GRIS = 0xFF939AA6.toInt()
    private const val GRIS_SUAVE = 0xFFB6BCC7.toInt()
    private const val LINEA = 0xFFE7E9EE.toInt()
    private const val LINEA_TENUE = 0xFFF3F4F7.toInt()
    private const val VERDE = 0xFF0F8A5F.toInt()
    private const val AMBAR = 0xFFC98A2B.toInt()
    private const val ROJO = 0xFFC0392B.toInt()
    private const val ROJO_FONDO = 0xFFFDF6F5.toInt()
    private const val BLANCO = 0xFFFFFFFF.toInt()

    private val LIGERA: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)

    // ---------------------------------------------------------------- API

    fun general(context: Context, fichas: List<Ficha>, logo: File?): File {
        val doc = PdfDocument()
        val h = Hoja(doc)
        val orden = fichas.sortedByDescending { it.analisis.saldo }

        encabezado(h, "Reporte de cartera", "Corte al ${fechaLargaDe(ahora())}", logo,
            "POR COBRAR", dinero(orden.sumOf { maxOf(0.0, it.analisis.saldo) }))
        cifrasClave(h, orden)
        avisoMora(h, orden)
        lineaDeTiempo(h, orden)
        loQueViene(h, orden)
        tablaCartera(h, orden)

        h.cerrar()
        return guardar(context, doc, "Cartera")
    }

    fun individual(context: Context, ficha: Ficha, logo: File?): File {
        val doc = PdfDocument()
        val h = Hoja(doc)

        encabezado(h, "Estado de cuenta", "Sistema de Control de Deudores", logo,
            "EMITIDO", fechaLargaDe(ahora()))
        fichaDelDeudor(h, ficha)
        resumenDeCinco(h, ficha)
        graficoEvolucion(h, ficha)
        tablaMovimientos(h, ficha)
        cierreYFirma(h, ficha)

        h.cerrar()
        return guardar(context, doc, "Cuenta_${ficha.deudor.nombre.filter { it.isLetterOrDigit() }}")
    }

    fun todos(context: Context, fichas: List<Ficha>, logo: File?): File {
        val doc = PdfDocument()
        val h = Hoja(doc)
        val orden = fichas.sortedByDescending { it.analisis.saldo }

        encabezado(h, "Todos los deudores", "${orden.size} fichas · corte al ${fechaLargaDe(ahora())}",
            logo, "POR COBRAR", dinero(orden.sumOf { maxOf(0.0, it.analisis.saldo) }))
        orden.forEach { bloqueDeudor(h, it) }

        h.cerrar()
        return guardar(context, doc, "Deudores")
    }

    // ------------------------------------------------- control de paginas

    /**
     * Lleva la pagina abierta y la altura ya usada. Cuando algo no cabe se
     * abre una hoja nueva, asi ninguna tabla se corta a la mitad.
     */
    private class Hoja(val doc: PdfDocument) {
        var pagina: PdfDocument.Page = doc.startPage(info(1))
        var c: Canvas = pagina.canvas
        var y: Float = M

        fun info(n: Int) = PdfDocument.PageInfo.Builder(ANCHO, ALTO, n).create()

        /**
         * Abre hoja nueva si lo que viene no cabe en lo que queda. Devuelve
         * true cuando hubo salto, para poder repetir la cabecera de una tabla.
         */
        fun sitio(alto: Float): Boolean {
            if (y + alto <= ALTO - 46f) return false
            doc.finishPage(pagina)
            pagina = doc.startPage(info(doc.pages.size + 1))
            c = pagina.canvas
            y = M
            return true
        }

        fun cerrar() = doc.finishPage(pagina)
    }

    // ------------------------------------------------------------ piezas

    private fun encabezado(
        h: Hoja, titulo: String, subtitulo: String, logo: File?,
        etiquetaDer: String, valorDer: String
    ) {
        var xTexto = M
        if (logo != null && logo.exists()) {
            runCatching {
                BitmapFactory.decodeFile(logo.absolutePath)?.let { bmp ->
                    val alto = 30f
                    val ancho = alto * bmp.width / bmp.height
                    h.c.drawBitmap(
                        bmp, Rect(0, 0, bmp.width, bmp.height),
                        RectF(M, h.y - 4f, M + ancho, h.y - 4f + alto), null
                    )
                    xTexto = M + ancho + 12f
                }
            }
        }

        h.c.drawText(titulo, xTexto, h.y + 14f, p(19f, TINTA, ligera = true))
        h.c.drawText(subtitulo, xTexto, h.y + 27f, p(8f, GRIS))
        derecha(h.c, etiquetaDer, DER, h.y + 6f, p(7f, GRIS, negrita = true))
        derecha(h.c, valorDer, DER, h.y + 22f, p(14f, TINTA, ligera = true))

        h.y += 36f
        h.c.drawLine(M, h.y, DER, h.y, trazo(TINTA, 1.6f))
        h.y += 20f
    }

    private fun cifrasClave(h: Hoja, fichas: List<Ficha>) {
        val porCobrar = fichas.sumOf { maxOf(0.0, it.analisis.saldo) }
        val cobrado = fichas.sumOf { it.pagado }
        val prestado = fichas.sumOf { it.prestado }
        val atrasado = fichas.filter { it.analisis.estado == Estado.ATRASADO }.sumOf { it.analisis.saldo }
        val pagos = fichas.sumOf { f -> f.movimientos.count { it.tipo == TipoMovimiento.PAGO } }

        val datos = listOf(
            Triple("POR COBRAR", dinero(porCobrar), TINTA),
            Triple("COBRADO", dinero(cobrado), VERDE),
            Triple("ATRASADO", dinero(atrasado), ROJO),
            Triple("PRESTADO", dinero(prestado), TINTA),
            Triple("PAGO PROMEDIO", if (pagos > 0) dinero(cobrado / pagos) else "$0,00", TINTA)
        )
        val ancho = (DER - M) / datos.size
        datos.forEachIndexed { i, (et, valor, color) ->
            val x = M + i * ancho + if (i == 0) 0f else 10f
            h.c.drawText(et, x, h.y + 8f, p(6.5f, GRIS, negrita = true))
            h.c.drawText(valor, x, h.y + 27f, p(17f, color, ligera = true))
            if (i > 0) h.c.drawLine(M + i * ancho, h.y - 2f, M + i * ancho, h.y + 31f, trazo(LINEA, 1f))
        }
        h.y += 44f
    }

    private fun avisoMora(h: Hoja, fichas: List<Ficha>) {
        val morosos = fichas.filter { it.analisis.estado == Estado.ATRASADO }
        if (morosos.isEmpty()) return

        val texto = if (morosos.size == 1) {
            val f = morosos.first()
            "${f.deudor.nombre} se pasó ${f.analisis.diasMora} días de su fecha de pago " +
                "con ${dinero(f.analisis.saldo)} pendientes."
        } else {
            "${morosos.size} deudores se pasaron de su fecha de pago, " +
                "${dinero(morosos.sumOf { it.analisis.saldo })} vencidos en total."
        }
        h.c.drawRect(RectF(M, h.y, DER, h.y + 24f), relleno(ROJO_FONDO))
        h.c.drawRect(RectF(M, h.y, M + 2.5f, h.y + 24f), relleno(ROJO))
        h.c.drawText(texto.take(110), M + 10f, h.y + 15f, p(8f, ROJO))
        h.y += 34f
    }

    private fun titulo(h: Hoja, texto: String, detalle: String? = null) {
        h.c.drawText(texto, M, h.y, p(7.5f, GRIS_SUAVE, negrita = true, espaciado = 1.6f))
        h.y += if (detalle != null) 11f else 6f
        if (detalle != null) {
            h.c.drawText(detalle, M, h.y, p(8f, GRIS))
            h.y += 8f
        }
    }

    // ------------------------------------------- reporte general: timeline

    private fun lineaDeTiempo(h: Hoja, fichas: List<Ficha>) {
        val conMovs = fichas.filter { it.movimientos.isNotEmpty() }.take(9)
        if (conMovs.isEmpty()) return

        val altoFila = 26f
        h.sitio(60f + conMovs.size * altoFila)
        titulo(h, "LÍNEA DE TIEMPO DE CADA DEUDA",
            "La barra empieza el día del préstamo y la marca es la fecha de pago acordada")
        h.y += 6f

        val inicio = mesInicio(conMovs.minOf { f -> f.movimientos.minOf { it.fecha } })
        val fin = mesFin(maxOf(ahora(), conMovs.maxOf { it.analisis.proximoVencimiento }))
        val total = (fin - inicio).toFloat().coerceAtLeast(1f)
        val xNombre = M + 74f
        val ancho = DER - xNombre

        fun px(ms: Long) = xNombre + ((ms - inicio).toFloat() / total) * ancho

        // Rejilla de meses
        val cal = Calendar.getInstance().apply { timeInMillis = inicio }
        while (cal.timeInMillis < fin) {
            val x = px(cal.timeInMillis)
            h.c.drawLine(x, h.y, x, h.y + conMovs.size * altoFila + 4f, trazo(LINEA_TENUE, 1f))
            h.c.drawText(mesCorto(cal), x + 4f, h.y - 4f, p(6.5f, GRIS_SUAVE, negrita = true, espaciado = 1.2f))
            cal.add(Calendar.MONTH, 1)
        }
        h.c.drawLine(xNombre, h.y, DER, h.y, trazo(LINEA, 1f))

        conMovs.forEach { f ->
            val cy = h.y + altoFila / 2f + 2f
            h.c.drawText(f.deudor.nombre.take(13), M, cy - 1f, p(8.5f, TINTA, negrita = true))
            h.c.drawText("debe ${dinero(maxOf(0.0, f.analisis.saldo))}", M, cy + 8f, p(6.5f, GRIS_SUAVE))

            val desde = px(f.movimientos.minOf { it.fecha })
            val vencido = f.analisis.estado == Estado.ATRASADO
            // Barra: lo cubierto en tinta (o rojo si esta vencida) y el resto claro.
            h.c.drawRoundRect(RectF(desde, cy - 3.5f, DER, cy + 3.5f), 3.5f, 3.5f, relleno(LINEA))
            val cubierto = desde + (DER - desde) * f.avance
            if (cubierto > desde) {
                h.c.drawRoundRect(
                    RectF(desde, cy - 3.5f, cubierto, cy + 3.5f), 3.5f, 3.5f,
                    relleno(if (vencido) ROJO else TINTA)
                )
            }

            f.movimientos.sortedBy { it.fecha }.forEach { m ->
                val x = px(m.fecha)
                val esCargo = m.tipo == TipoMovimiento.CARGO
                h.c.drawCircle(x, cy, 4.6f, relleno(BLANCO))
                h.c.drawCircle(x, cy, 3.4f, relleno(if (esCargo) AMBAR else VERDE))
            }

            // Marca de la fecha de pago acordada.
            val venc = if (vencido) f.analisis.proximoVencimiento - 30L * 86_400_000L
                       else f.analisis.proximoVencimiento
            if (venc in inicio..fin) {
                val x = px(venc)
                val color = if (vencido) ROJO else VERDE
                h.c.drawLine(x, cy - 9f, x, cy + 9f, trazo(color, 1.4f))
                h.c.drawText(
                    if (vencido) "venció ${fechaCortaDe(venc)}" else "paga ${fechaCortaDe(venc)}",
                    x + 3f, cy - 10f, p(6f, color, negrita = true)
                )
            }
            h.y += altoFila
        }

        // Marca de hoy, por encima de todas las barras.
        val xHoy = px(ahora())
        h.c.drawLine(xHoy, h.y - conMovs.size * altoFila - 2f, xHoy, h.y, trazo(TINTA, 1f))

        h.y += 10f
        leyenda(h, listOf("Préstamo" to AMBAR, "Pago recibido" to VERDE,
            "Parte cubierta" to TINTA, "Vencido" to ROJO), xNombre)
        h.y += 22f
    }

    private fun leyenda(h: Hoja, items: List<Pair<String, Int>>, x0: Float) {
        var x = x0
        items.forEach { (texto, color) ->
            h.c.drawCircle(x + 3f, h.y - 2.5f, 3f, relleno(color))
            h.c.drawText(texto, x + 10f, h.y, p(7f, GRIS))
            x += 10f + p(7f, GRIS).measureText(texto) + 14f
        }
    }

    private fun loQueViene(h: Hoja, fichas: List<Ficha>) {
        val proximos = fichas.filter { it.analisis.saldo > 0 }
            .sortedWith(compareByDescending<Ficha> { it.analisis.diasMora }
                .thenBy { it.analisis.diasRestantes })
            .take(4)
        if (proximos.isEmpty()) return

        h.sitio(90f)
        titulo(h, "LO QUE VIENE", "Próximas fechas de pago acordadas")
        h.y += 4f

        val ancho = (DER - M - 3 * 8f) / 4f
        proximos.forEachIndexed { i, f ->
            val x = M + i * (ancho + 8f)
            val r = RectF(x, h.y, x + ancho, h.y + 62f)
            val vencido = f.analisis.estado == Estado.ATRASADO
            if (vencido) h.c.drawRoundRect(r, 7f, 7f, relleno(ROJO_FONDO))
            h.c.drawRoundRect(r, 7f, 7f, borde(if (vencido) ROJO else LINEA, 1f))

            val dia = Calendar.getInstance().apply {
                timeInMillis = if (vencido) f.analisis.proximoVencimiento - 30L * 86_400_000L
                               else f.analisis.proximoVencimiento
            }
            h.c.drawText("%02d".format(dia.get(Calendar.DAY_OF_MONTH)), x + 10f, h.y + 22f, p(16f, TINTA, ligera = true))
            h.c.drawText(
                mesLargo(dia).uppercase() + if (vencido) ", VENCIDO" else "",
                x + 10f, h.y + 32f, p(6f, GRIS_SUAVE, negrita = true, espaciado = 1.1f)
            )
            h.c.drawText(f.deudor.nombre.take(14), x + 10f, h.y + 45f, p(8.5f, TINTA, negrita = true))
            h.c.drawText(dinero(maxOf(0.0, f.analisis.saldo)), x + 10f, h.y + 56f,
                p(9.5f, if (vencido) ROJO else TINTA, negrita = true))
        }
        h.y += 76f
    }

    private fun tablaCartera(h: Hoja, fichas: List<Ficha>) {
        h.sitio(60f)
        titulo(h, "RESUMEN")
        h.y += 8f

        val cols = floatArrayOf(M, 168f, 250f, 322f, 394f, 470f)
        h.c.drawText("DEUDOR", cols[0], h.y, p(6.5f, GRIS, negrita = true, espaciado = 1.1f))
        h.c.drawText("DESDE", cols[1], h.y, p(6.5f, GRIS, negrita = true, espaciado = 1.1f))
        derecha(h.c, "PRESTADO", cols[3], h.y, p(6.5f, GRIS, negrita = true, espaciado = 1.1f))
        derecha(h.c, "PAGADO", cols[4], h.y, p(6.5f, GRIS, negrita = true, espaciado = 1.1f))
        derecha(h.c, "SALDO", cols[5], h.y, p(6.5f, GRIS, negrita = true, espaciado = 1.1f))
        h.c.drawText("ESTADO", cols[5] + 14f, h.y, p(6.5f, GRIS, negrita = true, espaciado = 1.1f))
        h.y += 5f
        h.c.drawLine(M, h.y, DER, h.y, trazo(TINTA, 1f))
        h.y += 14f

        fichas.forEach { f ->
            h.sitio(20f)
            val e = f.analisis
            val vencido = e.estado == Estado.ATRASADO
            if (vencido) h.c.drawRect(RectF(M - 4f, h.y - 10f, DER + 4f, h.y + 8f), relleno(ROJO_FONDO))

            h.c.drawText(f.deudor.nombre.take(20), cols[0], h.y, p(9.5f, TINTA, negrita = true))
            h.c.drawText(
                f.movimientos.minOfOrNull { it.fecha }?.let { fechaLargaDe(it) } ?: "sin registro",
                cols[1], h.y, p(8f, GRIS)
            )
            derecha(h.c, dinero(f.prestado), cols[3], h.y, p(8.5f))
            derecha(h.c, dinero(f.pagado), cols[4], h.y, p(8.5f, VERDE))
            derecha(h.c, dinero(maxOf(0.0, e.saldo)), cols[5], h.y, p(9f, TINTA, negrita = true))
            h.c.drawText(
                if (vencido) "Atrasado ${e.diasMora} d" else e.estado.etiqueta,
                cols[5] + 14f, h.y, p(7.5f, if (vencido) ROJO else GRIS, negrita = vencido)
            )
            h.y += 8f
            h.c.drawLine(M, h.y, DER, h.y, trazo(LINEA_TENUE, 1f))
            h.y += 14f
        }

        h.y += 2f
        h.c.drawLine(M, h.y - 10f, DER, h.y - 10f, trazo(TINTA, 1.2f))
        h.c.drawText("Total", cols[0], h.y + 2f, p(9f, TINTA, negrita = true))
        derecha(h.c, dinero(fichas.sumOf { it.prestado }), cols[3], h.y + 2f, p(9f, TINTA, negrita = true))
        derecha(h.c, dinero(fichas.sumOf { it.pagado }), cols[4], h.y + 2f, p(9f, TINTA, negrita = true))
        derecha(h.c, dinero(fichas.sumOf { maxOf(0.0, it.analisis.saldo) }), cols[5], h.y + 2f,
            p(9.5f, TINTA, negrita = true))
        h.y += 20f
    }

    // --------------------------------------------- estado de cuenta

    private fun fichaDelDeudor(h: Hoja, f: Ficha) {
        val alto = 76f
        val anchoSaldo = 218f
        val rDatos = RectF(M, h.y, DER - anchoSaldo - 10f, h.y + alto)
        val rSaldo = RectF(DER - anchoSaldo, h.y, DER, h.y + alto)

        h.c.drawRoundRect(rDatos, 8f, 8f, borde(LINEA, 1f))
        h.c.drawText("DEUDOR", rDatos.left + 12f, rDatos.top + 15f, p(6.5f, GRIS, negrita = true, espaciado = 1.2f))
        h.c.drawText(f.deudor.nombre.take(22), rDatos.left + 12f, rDatos.top + 34f, p(17f, TINTA, ligera = true))

        val a = f.analisis
        val situacion = when (a.estado) {
            Estado.PAGADO -> "Deuda saldada por completo"
            Estado.ATRASADO -> "Atrasado ${a.diasMora} días"
            Estado.POR_VENCER -> if (a.diasRestantes == 0) "Vence hoy" else "Vence en ${a.diasRestantes} días"
            Estado.AL_DIA -> "Al día"
        }
        h.c.drawText(
            "Fecha de pago acordada: día ${f.deudor.diaPago} de cada mes",
            rDatos.left + 12f, rDatos.top + 49f, p(8f, 0xFF4B5563.toInt())
        )
        h.c.drawText(
            "Primer préstamo: ${f.movimientos.minOfOrNull { it.fecha }?.let { fechaLargaDe(it) } ?: "sin registro"}" +
                " · Último pago: ${f.movimientos.ultimoPago()?.let { fechaLargaDe(it) } ?: "todavía no paga"}",
            rDatos.left + 12f, rDatos.top + 61f, p(8f, 0xFF4B5563.toInt())
        )
        h.c.drawText("Situación: ", rDatos.left + 12f, rDatos.top + 73f, p(8f, 0xFF4B5563.toInt()))
        h.c.drawText(
            situacion, rDatos.left + 12f + p(8f).measureText("Situación: "), rDatos.top + 73f,
            p(8f, if (a.estado == Estado.ATRASADO) ROJO else VERDE, negrita = true)
        )

        h.c.drawRoundRect(rSaldo, 8f, 8f, relleno(TINTA))
        h.c.drawText("SALDO PENDIENTE A LA FECHA", rSaldo.left + 14f, rSaldo.top + 16f,
            p(6.5f, 0xFF9AA2B1.toInt(), negrita = true, espaciado = 1.2f))
        h.c.drawText(dinero(maxOf(0.0, a.saldo)), rSaldo.left + 14f, rSaldo.top + 46f,
            p(30f, BLANCO, ligera = true))
        h.c.drawText(
            "Ha cubierto el ${(f.avance * 100).toInt()}% de lo prestado",
            rSaldo.left + 14f, rSaldo.top + 60f, p(7.5f, 0xFF9AA2B1.toInt())
        )
        val pista = RectF(rSaldo.left + 14f, rSaldo.top + 65f, rSaldo.right - 14f, rSaldo.top + 69f)
        h.c.drawRoundRect(pista, 2f, 2f, relleno(0xFF303845.toInt()))
        if (f.avance > 0f) {
            h.c.drawRoundRect(
                RectF(pista.left, pista.top, pista.left + pista.width() * f.avance, pista.bottom),
                2f, 2f, relleno(0xFF34D399.toInt())
            )
        }
        h.y += alto + 22f
    }

    private fun resumenDeCinco(h: Hoja, f: Ficha) {
        val cargos = f.movimientos.count { it.tipo == TipoMovimiento.CARGO }
        val pagos = f.movimientos.count { it.tipo == TipoMovimiento.PAGO }
        val datos = listOf(
            "TOTAL PRESTADO" to dinero(f.prestado),
            "PRÉSTAMOS" to "$cargos",
            "TOTAL PAGADO" to dinero(f.pagado),
            "PAGOS" to "$pagos",
            "PAGO PROMEDIO" to if (pagos > 0) dinero(f.pagado / pagos) else "$0,00"
        )
        titulo(h, "RESUMEN")
        h.y += 6f
        val r = RectF(M, h.y, DER, h.y + 40f)
        h.c.drawRoundRect(r, 8f, 8f, borde(LINEA, 1f))
        val ancho = (DER - M) / datos.size
        datos.forEachIndexed { i, (et, valor) ->
            val x = M + i * ancho + 12f
            h.c.drawText(et, x, h.y + 15f, p(6.5f, GRIS, negrita = true, espaciado = 1.1f))
            h.c.drawText(valor, x, h.y + 31f, p(12f, if (et == "TOTAL PAGADO") VERDE else TINTA, negrita = true))
            if (i > 0) h.c.drawLine(M + i * ancho, h.y, M + i * ancho, h.y + 40f, trazo(LINEA, 1f))
        }
        h.y += 58f
    }

    private fun graficoEvolucion(h: Hoja, f: Ficha) {
        val serie = f.movimientos.serieDeSaldo()
        if (serie.size < 2) return

        h.sitio(120f)
        titulo(h, "CÓMO HA IDO LA DEUDA")
        h.y += 6f

        val r = RectF(M, h.y, DER, h.y + 86f)
        h.c.drawLine(r.left, r.bottom, r.right, r.bottom, trazo(LINEA, 1f))
        val maximo = serie.maxOf { it.second }.takeIf { it > 0 } ?: 1.0
        val paso = (r.width() - 24f) / (serie.size - 1)

        var xAnt = 0f; var yAnt = 0f
        serie.forEachIndexed { i, (_, saldo) ->
            val x = r.left + 12f + i * paso
            val yy = r.bottom - 8f - (saldo / maximo).toFloat() * (r.height() - 26f)
            if (i > 0) h.c.drawLine(xAnt, yAnt, x, yy, trazo(TINTA, 1.6f))
            xAnt = x; yAnt = yy
        }
        serie.forEachIndexed { i, (m, saldo) ->
            val x = r.left + 12f + i * paso
            val yy = r.bottom - 8f - (saldo / maximo).toFloat() * (r.height() - 26f)
            val esCargo = m.tipo == TipoMovimiento.CARGO
            h.c.drawCircle(x, yy, 3.4f, relleno(if (esCargo) AMBAR else VERDE))
            if (i == 0 || i == serie.size - 1) {
                val etiqueta = dinero(saldo)
                val ancho = p(7.5f).measureText(etiqueta)
                h.c.drawText(
                    etiqueta,
                    (x - ancho / 2).coerceIn(r.left, r.right - ancho),
                    yy - 8f, p(7.5f, TINTA, negrita = true)
                )
            }
        }
        h.y += 100f
    }

    private fun tablaMovimientos(h: Hoja, f: Ficha) {
        h.sitio(60f)
        titulo(h, "MOVIMIENTOS DE LA CUENTA")
        h.y += 8f
        cabeceraMovimientos(h)

        val serie = f.movimientos.serieDeSaldo().reversed()
        if (serie.isEmpty()) {
            h.c.drawText("Sin movimientos registrados", M, h.y, p(9f, GRIS))
            h.y += 20f
            return
        }
        serie.forEach { (m, saldo) ->
            if (h.sitio(30f)) cabeceraMovimientos(h)
            filaMovimiento(h, m.fecha, m.tipo == TipoMovimiento.CARGO, m.monto, m.nota, saldo)
        }

        h.y += 2f
        h.c.drawLine(M, h.y - 10f, DER, h.y - 10f, trazo(TINTA, 1.2f))
        h.c.drawText("Totales", M, h.y + 2f, p(8.5f, TINTA, negrita = true))
        derecha(h.c, dinero(f.prestado), 420f, h.y + 2f, p(8.5f, AMBAR, negrita = true))
        derecha(h.c, dinero(f.pagado), 490f, h.y + 2f, p(8.5f, VERDE, negrita = true))
        derecha(h.c, dinero(maxOf(0.0, f.analisis.saldo)), DER, h.y + 2f, p(9f, TINTA, negrita = true))
        h.y += 22f
    }

    private fun cabeceraMovimientos(h: Hoja) {
        h.c.drawText("FECHA", M, h.y, p(6.5f, GRIS, negrita = true, espaciado = 1.1f))
        h.c.drawText("HORA", 128f, h.y, p(6.5f, GRIS, negrita = true, espaciado = 1.1f))
        h.c.drawText("CONCEPTO", 170f, h.y, p(6.5f, GRIS, negrita = true, espaciado = 1.1f))
        derecha(h.c, "CARGO", 420f, h.y, p(6.5f, GRIS, negrita = true, espaciado = 1.1f))
        derecha(h.c, "ABONO", 490f, h.y, p(6.5f, GRIS, negrita = true, espaciado = 1.1f))
        derecha(h.c, "SALDO", DER, h.y, p(6.5f, GRIS, negrita = true, espaciado = 1.1f))
        h.y += 5f
        h.c.drawLine(M, h.y, DER, h.y, trazo(TINTA, 1f))
        h.y += 14f
    }

    private fun filaMovimiento(
        h: Hoja, fecha: Long, esCargo: Boolean, monto: Double, nota: String, saldo: Double
    ) {
        val concepto = when {
            nota.isNotBlank() -> nota
            esCargo -> "Préstamo"
            else -> "Pago recibido"
        }
        h.c.drawText(fechaLargaDe(fecha), M, h.y, p(8.5f))
        h.c.drawText(horaDe(fecha), 128f, h.y, p(8f, GRIS))
        h.c.drawText(concepto.take(40), 170f, h.y, p(8.5f))
        if (esCargo) derecha(h.c, dinero(monto), 420f, h.y, p(8.5f, AMBAR, negrita = true))
        else derecha(h.c, dinero(monto), 490f, h.y, p(8.5f, VERDE, negrita = true))
        derecha(h.c, dinero(saldo), DER, h.y, p(8.5f, TINTA, negrita = true))
        h.y += 7f
        h.c.drawLine(M, h.y, DER, h.y, trazo(LINEA_TENUE, 1f))
        h.y += 14f
    }

    private fun cierreYFirma(h: Hoja, f: Ficha) {
        h.sitio(80f)
        val a = f.analisis
        val texto = when (a.estado) {
            Estado.PAGADO -> "${f.deudor.nombre} no tiene saldo pendiente a la fecha de este documento."
            Estado.ATRASADO -> "La fecha de pago acordada era el día ${f.deudor.diaPago}. " +
                "Lleva ${a.diasMora} días de atraso con ${dinero(a.saldo)} pendientes."
            else -> "El saldo pendiente es de ${dinero(maxOf(0.0, a.saldo))} y el próximo pago " +
                "corresponde al día ${f.deudor.diaPago} del mes."
        }
        val anchoTexto = DER - M - 190f
        h.c.drawRect(RectF(M, h.y, M + 2.5f, h.y + 46f), relleno(TINTA))
        var yy = h.y + 14f
        partir(texto, p(8.5f, 0xFF4B5563.toInt()), anchoTexto).take(3).forEach { linea ->
            h.c.drawText(linea, M + 12f, yy, p(8.5f, 0xFF4B5563.toInt()))
            yy += 12f
        }

        val xFirma = DER - 170f
        h.c.drawLine(xFirma, h.y + 34f, DER, h.y + 34f, trazo(GRIS, 1f))
        h.c.drawText("RECIBÍ CONFORME · FIRMA DEL DEUDOR", xFirma, h.y + 44f,
            p(6.5f, GRIS, espaciado = 1.1f))
        h.y += 60f
    }

    // ------------------------------------------------- todos, dos por hoja

    private fun bloqueDeudor(h: Hoja, f: Ficha) {
        val movs = f.movimientos.serieDeSaldo().reversed().take(4)
        val alto = 118f + movs.size * 15f
        h.sitio(alto + 12f)

        val vencido = f.analisis.estado == Estado.ATRASADO
        val r = RectF(M, h.y, DER, h.y + alto)
        if (vencido) h.c.drawRoundRect(r, 9f, 9f, relleno(ROJO_FONDO))
        h.c.drawRoundRect(r, 9f, 9f, borde(if (vencido) 0xFFEDC9C4.toInt() else LINEA, 1f))

        var y = h.y + 22f
        h.c.drawText(f.deudor.nombre.take(24), M + 14f, y, p(15f, TINTA, ligera = true))
        derecha(h.c, dinero(maxOf(0.0, f.analisis.saldo)), DER - 14f, y, p(18f, if (vencido) ROJO else TINTA, ligera = true))
        y += 11f
        h.c.drawText(
            "paga el ${f.deudor.diaPago} de cada mes · último pago " +
                (f.movimientos.ultimoPago()?.let { fechaLargaDe(it) } ?: "todavía no paga"),
            M + 14f, y, p(7.5f, GRIS)
        )
        derecha(h.c, if (vencido) "SALDO VENCIDO" else "SALDO PENDIENTE", DER - 14f, y,
            p(6.5f, GRIS, negrita = true, espaciado = 1.1f))
        y += 8f
        h.c.drawLine(M + 14f, y, DER - 14f, y, trazo(if (vencido) 0xFFF2DDD9.toInt() else LINEA_TENUE, 1f))
        y += 16f

        val datos = listOf(
            "PRESTADO" to dinero(f.prestado),
            "PAGADO" to dinero(f.pagado),
            "AVANCE" to "${(f.avance * 100).toInt()}%",
            "SITUACIÓN" to if (vencido) "Atrasado ${f.analisis.diasMora} d" else f.analisis.estado.etiqueta
        )
        val ancho = (DER - M - 28f) / datos.size
        datos.forEachIndexed { i, (et, valor) ->
            val x = M + 14f + i * ancho
            h.c.drawText(et, x, y, p(6f, GRIS, negrita = true, espaciado = 1.1f))
            h.c.drawText(
                valor, x, y + 13f,
                p(10.5f, when {
                    et == "PAGADO" -> VERDE
                    et == "SITUACIÓN" && vencido -> ROJO
                    et == "SITUACIÓN" -> VERDE
                    else -> TINTA
                }, negrita = true)
            )
            if (i > 0) h.c.drawLine(x - 10f, y - 8f, x - 10f, y + 17f, trazo(LINEA, 1f))
        }
        y += 24f

        val pista = RectF(M + 14f, y, DER - 14f, y + 4f)
        h.c.drawRoundRect(pista, 2f, 2f, relleno(if (vencido) 0xFFF2DDD9.toInt() else LINEA))
        if (f.avance > 0f) {
            h.c.drawRoundRect(
                RectF(pista.left, pista.top, pista.left + pista.width() * f.avance, pista.bottom),
                2f, 2f, relleno(if (vencido) ROJO else VERDE)
            )
        }
        y += 18f

        movs.forEach { (m, saldo) ->
            val esCargo = m.tipo == TipoMovimiento.CARGO
            h.c.drawText(fechaLargaDe(m.fecha), M + 14f, y, p(8f, GRIS))
            h.c.drawText(
                (m.nota.ifBlank { if (esCargo) "Préstamo" else "Pago recibido" }).take(34),
                M + 96f, y, p(8f)
            )
            derecha(h.c, (if (esCargo) "+ " else "− ") + dinero(m.monto), DER - 96f, y,
                p(8f, if (esCargo) AMBAR else VERDE, negrita = true))
            derecha(h.c, dinero(saldo), DER - 14f, y, p(8f, TINTA, negrita = true))
            y += 15f
        }

        val restantes = f.movimientos.size - movs.size
        if (restantes > 0) {
            h.c.drawText(
                "y $restantes movimiento${if (restantes == 1) "" else "s"} más en su estado de cuenta",
                M + 14f, y - 2f, p(7f, GRIS_SUAVE)
            )
        }

        h.y += alto + 12f
    }

    // ------------------------------------------------------------ utiles

    private fun p(
        tam: Float, color: Int = TINTA, negrita: Boolean = false,
        ligera: Boolean = false, espaciado: Float = 0f
    ) = Paint().apply {
        isAntiAlias = true
        textSize = tam
        this.color = color
        letterSpacing = espaciado / 10f
        typeface = when {
            negrita -> Typeface.DEFAULT_BOLD
            ligera -> LIGERA
            else -> Typeface.DEFAULT
        }
    }

    private fun trazo(color: Int, grosor: Float) = Paint().apply {
        isAntiAlias = true
        this.color = color
        strokeWidth = grosor
        style = Paint.Style.STROKE
    }

    private fun relleno(color: Int) = Paint().apply {
        isAntiAlias = true
        this.color = color
        style = Paint.Style.FILL
    }

    private fun borde(color: Int, grosor: Float) = trazo(color, grosor)

    private fun derecha(c: Canvas, texto: String, x: Float, y: Float, pintura: Paint) {
        c.drawText(texto, x - pintura.measureText(texto), y, pintura)
    }

    /** Parte un texto largo en lineas que quepan en [ancho]. */
    private fun partir(texto: String, pintura: Paint, ancho: Float): List<String> {
        val lineas = mutableListOf<String>()
        var actual = StringBuilder()
        texto.split(" ").forEach { palabra ->
            val prueba = if (actual.isEmpty()) palabra else "$actual $palabra"
            if (pintura.measureText(prueba) > ancho && actual.isNotEmpty()) {
                lineas += actual.toString()
                actual = StringBuilder(palabra)
            } else {
                actual = StringBuilder(prueba)
            }
        }
        if (actual.isNotEmpty()) lineas += actual.toString()
        return lineas
    }

    private fun ahora() = System.currentTimeMillis()

    private fun mesInicio(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun mesFin(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = mesInicio(ms)
        add(Calendar.MONTH, 1)
    }.timeInMillis

    private fun mesCorto(c: Calendar): String {
        val meses = arrayOf("ENE", "FEB", "MAR", "ABR", "MAY", "JUN",
            "JUL", "AGO", "SEP", "OCT", "NOV", "DIC")
        return meses[c.get(Calendar.MONTH)] + " " + (c.get(Calendar.YEAR) % 100)
    }

    private fun mesLargo(c: Calendar): String {
        val meses = arrayOf("enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
        return meses[c.get(Calendar.MONTH)]
    }

    private fun guardar(context: Context, doc: PdfDocument, nombre: String): File {
        val archivo = File(context.cacheDir, "reportes").apply { mkdirs() }
            .resolve("${nombre}_${marcaTiempo()}.pdf")
        archivo.outputStream().use { doc.writeTo(it) }
        doc.close()
        return archivo
    }

    private fun marcaTiempo(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }
}
