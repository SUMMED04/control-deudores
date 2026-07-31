package com.summed.deudores.export

import android.content.Context
import com.summed.deudores.data.Estado
import com.summed.deudores.data.TipoMovimiento
import com.summed.deudores.data.etiqueta
import com.summed.deudores.data.serieDeSaldo
import com.summed.deudores.data.ultimoPago
import com.summed.deudores.export.EscritorXlsx.Celda
import com.summed.deudores.export.EscritorXlsx.Estilo
import com.summed.deudores.ui.Ficha
import com.summed.deudores.ui.horaDe
import java.io.File
import java.util.Calendar

/**
 * Mismas tres hojas que la version web (Panel, Deudores y Pagos) y la misma
 * presentacion: franja de titulo, cuatro tarjetas de indicadores, tablas con
 * cabecera oscura y barras de avance.
 *
 * El grafico del Panel es un grafico NATIVO de Excel, no una imagen: se puede
 * hacer clic en el, cambiar el tipo y se recalcula solo si se edita una cifra.
 * La version web no puede hacerlo porque ExcelJS no genera graficos.
 */
object ExportadorExcel {

    private const val COLS_PANEL = 8

    fun generar(context: Context, fichas: List<Ficha>): File {
        val orden = fichas.sortedByDescending { it.analisis.saldo }
        val libro = EscritorXlsx()

        val porCobrar = orden.sumOf { maxOf(0.0, it.analisis.saldo) }
        val cobrado = orden.sumOf { it.pagado }
        val prestado = orden.sumOf { it.prestado }
        val atrasado = orden.filter { it.analisis.estado == Estado.ATRASADO }.sumOf { it.analisis.saldo }
        val recuperado = if (prestado > 0) cobrado / prestado else 0.0

        // ================= Panel =================
        val panel = libro.hoja("Panel", listOf(24, 14, 14, 14, 12, 16, 11, 14))

        franja(panel, "CONTROL DE DEUDORES", COLS_PANEL)
        panel.fila(Celda.Texto("Generado el ${fechaHoraLegible()}", Estilo.SUBTITULO))
        panel.combinar("A3:${col(COLS_PANEL - 1)}3")
        panel.vacia()

        // Cuatro tarjetas de dos columnas cada una, en las filas 5 y 6.
        val tarjetas = listOf(
            Triple("POR COBRAR", porCobrar, Estilo.KPI_ETIQUETA_1),
            Triple("COBRADO", cobrado, Estilo.KPI_ETIQUETA_2),
            Triple("ATRASADO", atrasado, Estilo.KPI_ETIQUETA_3),
            Triple("RECUPERADO", recuperado, Estilo.KPI_ETIQUETA_4)
        )
        val valores = listOf(
            Estilo.KPI_VALOR_1, Estilo.KPI_VALOR_2, Estilo.KPI_VALOR_3, Estilo.KPI_VALOR_4
        )
        panel.fila(tarjetas.flatMap { (texto, _, estilo) ->
            listOf(Celda.Texto(texto, estilo), Celda.Texto("", estilo))
        })
        panel.alto(16.0)
        panel.fila(tarjetas.mapIndexed { i, (_, valor, _) ->
            listOf(Celda.Numero(valor, valores[i]), Celda.Texto("", valores[i]))
        }.flatten())
        panel.alto(30.0)
        (0 until 4).forEach { i ->
            panel.combinar("${col(i * 2)}5:${col(i * 2 + 1)}5")
            panel.combinar("${col(i * 2)}6:${col(i * 2 + 1)}6")
        }
        panel.vacia()

        panel.fila((0 until COLS_PANEL).map {
            Celda.Texto(if (it == 0) "RESUMEN POR DEUDOR" else "", Estilo.SECCION)
        })
        panel.combinar("A8:${col(COLS_PANEL - 1)}8")
        panel.alto(20.0)

        panel.fila(
            listOf("DEUDOR", "PRESTADO", "PAGADO", "SALDO", "AVANCE", "ESTADO", "DÍAS MORA", "VENCE")
                .map { Celda.Texto(it, Estilo.CABECERA) }
        )
        panel.alto(22.0)

        val primeraFilaDatos = panel.ultimaFila + 1
        orden.forEach { f ->
            panel.fila(
                Celda.Texto(f.deudor.nombre),
                Celda.Numero(f.prestado, Estilo.MONEDA),
                Celda.Numero(f.pagado, Estilo.MONEDA),
                Celda.Numero(maxOf(0.0, f.analisis.saldo), Estilo.MONEDA_NEGRITA),
                Celda.Numero(f.avance.toDouble(), Estilo.PORCENTAJE),
                Celda.Texto(f.analisis.estado.etiqueta, Estilo.CENTRADO),
                if (f.analisis.estado == Estado.ATRASADO)
                    Celda.Numero(f.analisis.diasMora.toDouble(), Estilo.CENTRADO)
                else Celda.Vacia,
                if (f.analisis.estado == Estado.PAGADO) Celda.Vacia
                else Celda.Fecha(f.analisis.proximoVencimiento)
            )
        }
        val ultimaFilaDatos = panel.ultimaFila

        panel.fila(
            Celda.Texto("TOTAL", Estilo.TOTAL_TEXTO),
            Celda.Numero(prestado, Estilo.TOTAL_MONEDA),
            Celda.Numero(cobrado, Estilo.TOTAL_MONEDA),
            Celda.Numero(porCobrar, Estilo.TOTAL_MONEDA),
            Celda.Texto("", Estilo.TOTAL_TEXTO),
            Celda.Texto("", Estilo.TOTAL_TEXTO),
            Celda.Texto("", Estilo.TOTAL_TEXTO),
            Celda.Texto("", Estilo.TOTAL_TEXTO)
        )

        // Barra dentro de la celda de Avance. Es formato condicional nativo:
        // si se edita el porcentaje a mano, la barra se mueve sola.
        if (orden.isNotEmpty()) {
            panel.barrasDeAvance("E$primeraFilaDatos:E$ultimaFilaDatos")

            panel.grafico(
                EscritorXlsx.Grafico(
                    titulo = "Prestado, cobrado y pendiente",
                    categorias = orden.map { it.deudor.nombre },
                    rangoCategorias = "\$A\$$primeraFilaDatos:\$A\$$ultimaFilaDatos",
                    series = listOf(
                        EscritorXlsx.Serie(
                            "Prestado", "4F46E5", orden.map { it.prestado },
                            "\$B\$$primeraFilaDatos:\$B\$$ultimaFilaDatos"
                        ),
                        EscritorXlsx.Serie(
                            "Pagado", "10B981", orden.map { it.pagado },
                            "\$C\$$primeraFilaDatos:\$C\$$ultimaFilaDatos"
                        ),
                        EscritorXlsx.Serie(
                            "Saldo", "F59E0B", orden.map { maxOf(0.0, it.analisis.saldo) },
                            "\$D\$$primeraFilaDatos:\$D\$$ultimaFilaDatos"
                        )
                    ),
                    // El ancla va desde cero, por eso la fila de abajo coincide
                    // con el numero de la ultima fila escrita.
                    filaDesde = panel.ultimaFila + 1,
                    filaHasta = panel.ultimaFila + 18,
                    colDesde = 0,
                    colHasta = COLS_PANEL
                )
            )
            // Filas reservadas para que el grafico no tape el pie.
            repeat(19) { panel.vacia() }
        }

        panel.fila(
            (0 until COLS_PANEL).map {
                Celda.Texto(
                    if (it == 0)
                        "La hoja Deudores tiene la ficha completa de cada uno y la hoja Pagos todos los movimientos con su fecha y hora."
                    else "",
                    Estilo.PIE
                )
            }
        )
        panel.combinar("A${panel.ultimaFila}:${col(COLS_PANEL - 1)}${panel.ultimaFila}")
        panel.alto(24.0)

        // ================= Deudores =================
        // Anchos medidos contra la cabecera: con 14 se cortaban TOTAL PRESTADO
        // y PAGO PROMEDIO.
        val anchosDeudores = listOf(24, 15, 10, 17, 12, 15, 9, 17, 14, 14, 14, 12, 13, 30)
        val hojaDeudores = libro.hoja("Deudores", anchosDeudores, filasCongeladas = 4)
        franja(hojaDeudores, "FICHA DE CADA DEUDOR", anchosDeudores.size)
        hojaDeudores.fila(Celda.Texto("Un renglón por deudor, con lo prestado, lo pagado y lo que falta.", Estilo.SUBTITULO))
        hojaDeudores.combinar("A3:${col(anchosDeudores.size - 1)}3")

        hojaDeudores.fila(
            listOf(
                "DEUDOR", "TELÉFONO", "DÍA PAGO", "TOTAL PRESTADO", "PRÉSTAMOS",
                "TOTAL PAGADO", "PAGOS", "PAGO PROMEDIO", "ÚLTIMO PAGO", "SALDO",
                "ESTADO", "DÍAS MORA", "VENCE", "NOTAS"
            ).map { Celda.Texto(it, Estilo.CABECERA) }
        )
        hojaDeudores.alto(22.0)

        val primeraDeudor = hojaDeudores.ultimaFila + 1
        orden.forEach { f ->
            val cargos = f.movimientos.count { it.tipo == TipoMovimiento.CARGO }
            val pagos = f.movimientos.count { it.tipo == TipoMovimiento.PAGO }
            hojaDeudores.fila(
                Celda.Texto(f.deudor.nombre),
                // Como TEXTO: si va como numero, Excel se come el cero inicial
                // y 0991112233 se convierte en 991112233.
                Celda.Texto(f.deudor.telefono, Estilo.TEXTO),
                Celda.Numero(f.deudor.diaPago.toDouble(), Estilo.CENTRADO),
                Celda.Numero(f.prestado, Estilo.MONEDA),
                Celda.Numero(cargos.toDouble(), Estilo.CENTRADO),
                Celda.Numero(f.pagado, Estilo.MONEDA),
                Celda.Numero(pagos.toDouble(), Estilo.CENTRADO),
                Celda.Numero(if (pagos > 0) f.pagado / pagos else 0.0, Estilo.MONEDA),
                f.movimientos.ultimoPago()?.let { Celda.Fecha(it) } ?: Celda.Vacia,
                Celda.Numero(maxOf(0.0, f.analisis.saldo), Estilo.MONEDA_NEGRITA),
                Celda.Texto(f.analisis.estado.etiqueta, Estilo.CENTRADO),
                if (f.analisis.estado == Estado.ATRASADO)
                    Celda.Numero(f.analisis.diasMora.toDouble(), Estilo.CENTRADO)
                else Celda.Vacia,
                if (f.analisis.estado == Estado.PAGADO) Celda.Vacia
                else Celda.Fecha(f.analisis.proximoVencimiento),
                Celda.Texto(f.deudor.notas)
            )
        }
        if (orden.isNotEmpty()) {
            hojaDeudores.sinAvisoDeTexto("B$primeraDeudor:B${hojaDeudores.ultimaFila}")
        }

        // ================= Pagos =================
        val anchosPagos = listOf(24, 13, 9, 12, 13, 13, 13, 30)
        val hojaPagos = libro.hoja("Pagos", anchosPagos, filasCongeladas = 4)
        franja(hojaPagos, "REGISTRO DE MOVIMIENTOS", anchosPagos.size)
        hojaPagos.fila(Celda.Texto("Cada préstamo y cada abono con su fecha y su hora exacta.", Estilo.SUBTITULO))
        hojaPagos.combinar("A3:${col(anchosPagos.size - 1)}3")

        hojaPagos.fila(
            listOf("DEUDOR", "FECHA", "HORA", "CONCEPTO", "PRÉSTAMO", "PAGO", "SALDO", "NOTA")
                .map { Celda.Texto(it, Estilo.CABECERA) }
        )
        hojaPagos.alto(22.0)
        orden.forEach { f ->
            f.movimientos.serieDeSaldo().forEach { (m, saldo) ->
                val esCargo = m.tipo == TipoMovimiento.CARGO
                hojaPagos.fila(
                    Celda.Texto(f.deudor.nombre),
                    Celda.Fecha(m.fecha),
                    Celda.Texto(horaDe(m.fecha), Estilo.CENTRADO),
                    Celda.Texto(if (esCargo) "Préstamo" else "Pago", Estilo.CENTRADO),
                    if (esCargo) Celda.Numero(m.monto, Estilo.MONEDA) else Celda.Vacia,
                    if (esCargo) Celda.Vacia else Celda.Numero(m.monto, Estilo.MONEDA),
                    Celda.Numero(saldo, Estilo.MONEDA_NEGRITA),
                    Celda.Texto(m.nota)
                )
            }
        }

        val archivo = File(context.cacheDir, "reportes").apply { mkdirs() }
            .resolve("Deudores_${marcaTiempo()}.xlsx")
        archivo.outputStream().use { libro.escribir(it) }
        return archivo
    }

    /** Franja morada de dos filas con el nombre de la hoja. */
    private fun franja(hoja: EscritorXlsx.Constructor, texto: String, columnas: Int) {
        hoja.fila((0 until columnas).map { Celda.Texto(if (it == 0) texto else "", Estilo.TITULO) })
        hoja.alto(26.0)
        hoja.fila((0 until columnas).map { Celda.Texto("", Estilo.TITULO) })
        hoja.alto(12.0)
        hoja.combinar("A1:${col(columnas - 1)}2")
    }

    /** Letra de columna a partir de un indice que empieza en cero. */
    private fun col(indice: Int): String {
        var n = indice
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.toString()
    }

    private fun marcaTiempo(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun fechaHoraLegible(): String {
        val c = Calendar.getInstance()
        return "%02d/%02d/%04d %02d:%02d".format(
            c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)
        )
    }
}
