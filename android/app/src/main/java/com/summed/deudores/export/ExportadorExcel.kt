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

/** Mismas tres hojas que la version web: Panel, Deudores y Pagos. */
object ExportadorExcel {

    fun generar(context: Context, fichas: List<Ficha>): File {
        val orden = fichas.sortedByDescending { it.analisis.saldo }
        val libro = EscritorXlsx()

        // ---------- Panel ----------
        val panel = libro.hoja("Panel", listOf(24, 14, 14, 14, 12, 16, 11, 14))
        panel.fila(Celda.Texto("Control de deudores", Estilo.TITULO))
        panel.fila(Celda.Texto("Generado el ${fechaHoraLegible()}"))
        panel.vacia()

        val porCobrar = orden.sumOf { maxOf(0.0, it.analisis.saldo) }
        val cobrado = orden.sumOf { it.pagado }
        val prestado = orden.sumOf { it.prestado }
        val atrasado = orden.filter { it.analisis.estado == Estado.ATRASADO }.sumOf { it.analisis.saldo }
        val recuperado = if (prestado > 0) cobrado / prestado else 0.0

        panel.fila(
            Celda.Texto("POR COBRAR", Estilo.NEGRITA), Celda.Vacia,
            Celda.Texto("COBRADO", Estilo.NEGRITA), Celda.Vacia,
            Celda.Texto("ATRASADO", Estilo.NEGRITA), Celda.Vacia,
            Celda.Texto("RECUPERADO", Estilo.NEGRITA)
        )
        panel.fila(
            Celda.Numero(porCobrar, Estilo.MONEDA_NEGRITA), Celda.Vacia,
            Celda.Numero(cobrado, Estilo.MONEDA_NEGRITA), Celda.Vacia,
            Celda.Numero(atrasado, Estilo.MONEDA_NEGRITA), Celda.Vacia,
            Celda.Numero(recuperado, Estilo.PORCENTAJE)
        )
        panel.vacia()

        panel.fila(
            listOf("DEUDOR", "PRESTADO", "PAGADO", "SALDO", "AVANCE", "ESTADO", "DÍAS MORA", "VENCE")
                .map { Celda.Texto(it, Estilo.CABECERA) }
        )
        orden.forEach { f ->
            panel.fila(
                Celda.Texto(f.deudor.nombre),
                Celda.Numero(f.prestado, Estilo.MONEDA),
                Celda.Numero(f.pagado, Estilo.MONEDA),
                Celda.Numero(maxOf(0.0, f.analisis.saldo), Estilo.MONEDA_NEGRITA),
                Celda.Numero(f.avance.toDouble(), Estilo.PORCENTAJE),
                Celda.Texto(f.analisis.estado.etiqueta),
                if (f.analisis.estado == Estado.ATRASADO) Celda.Numero(f.analisis.diasMora.toDouble())
                else Celda.Vacia,
                if (f.analisis.estado == Estado.PAGADO) Celda.Vacia
                else Celda.Fecha(f.analisis.proximoVencimiento)
            )
        }
        panel.fila(
            Celda.Texto("Total", Estilo.NEGRITA),
            Celda.Numero(prestado, Estilo.MONEDA_NEGRITA),
            Celda.Numero(cobrado, Estilo.MONEDA_NEGRITA),
            Celda.Numero(porCobrar, Estilo.MONEDA_NEGRITA)
        )

        // ---------- Deudores ----------
        val hojaDeudores = libro.hoja(
            "Deudores",
            listOf(24, 15, 10, 14, 11, 14, 9, 14, 14, 14, 14, 11, 13, 30),
            congelarPrimeraFila = true
        )
        hojaDeudores.fila(
            listOf(
                "DEUDOR", "TELÉFONO", "DÍA PAGO", "TOTAL PRESTADO", "PRÉSTAMOS",
                "TOTAL PAGADO", "PAGOS", "PAGO PROMEDIO", "ÚLTIMO PAGO", "SALDO",
                "ESTADO", "DÍAS MORA", "VENCE", "NOTAS"
            ).map { Celda.Texto(it, Estilo.CABECERA) }
        )
        orden.forEach { f ->
            val cargos = f.movimientos.count { it.tipo == TipoMovimiento.CARGO }
            val pagos = f.movimientos.count { it.tipo == TipoMovimiento.PAGO }
            hojaDeudores.fila(
                Celda.Texto(f.deudor.nombre),
                // Como TEXTO: si va como numero, Excel se come el cero inicial
                // y 0991112233 se convierte en 991112233.
                Celda.Texto(f.deudor.telefono, Estilo.TEXTO),
                Celda.Numero(f.deudor.diaPago.toDouble()),
                Celda.Numero(f.prestado, Estilo.MONEDA),
                Celda.Numero(cargos.toDouble()),
                Celda.Numero(f.pagado, Estilo.MONEDA),
                Celda.Numero(pagos.toDouble()),
                Celda.Numero(if (pagos > 0) f.pagado / pagos else 0.0, Estilo.MONEDA),
                f.movimientos.ultimoPago()?.let { Celda.Fecha(it) } ?: Celda.Vacia,
                Celda.Numero(maxOf(0.0, f.analisis.saldo), Estilo.MONEDA_NEGRITA),
                Celda.Texto(f.analisis.estado.etiqueta),
                if (f.analisis.estado == Estado.ATRASADO) Celda.Numero(f.analisis.diasMora.toDouble())
                else Celda.Vacia,
                if (f.analisis.estado == Estado.PAGADO) Celda.Vacia
                else Celda.Fecha(f.analisis.proximoVencimiento),
                Celda.Texto(f.deudor.notas)
            )
        }

        // ---------- Pagos ----------
        val hojaPagos = libro.hoja(
            "Pagos", listOf(24, 13, 9, 12, 13, 13, 13, 30), congelarPrimeraFila = true
        )
        hojaPagos.fila(
            listOf("DEUDOR", "FECHA", "HORA", "CONCEPTO", "PRÉSTAMO", "PAGO", "SALDO", "NOTA")
                .map { Celda.Texto(it, Estilo.CABECERA) }
        )
        orden.forEach { f ->
            f.movimientos.serieDeSaldo().forEach { (m, saldo) ->
                val esCargo = m.tipo == TipoMovimiento.CARGO
                hojaPagos.fila(
                    Celda.Texto(f.deudor.nombre),
                    Celda.Fecha(m.fecha),
                    Celda.Texto(horaDe(m.fecha)),
                    Celda.Texto(if (esCargo) "Préstamo" else "Pago"),
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
