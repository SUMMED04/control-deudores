package com.summed.deudores.data

import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Todo lo derivado del historial de movimientos. Son funciones puras: la misma
 * entrada da siempre la misma salida, sin tocar base de datos ni UI.
 */

/** Redondeo a centavos. Sin esto, 250.55 - 0.1 - 0.2 acumula error binario. */
fun r2(n: Double): Double = (n * 100).roundToLong() / 100.0

fun List<Movimiento>.saldo(): Double = r2(
    sumOf { if (it.tipo == TipoMovimiento.CARGO) it.monto else -it.monto }
)

fun List<Movimiento>.totalPrestado(): Double = r2(
    filter { it.tipo == TipoMovimiento.CARGO }.sumOf { it.monto }
)

fun List<Movimiento>.totalPagado(): Double = r2(
    filter { it.tipo == TipoMovimiento.PAGO }.sumOf { it.monto }
)

fun List<Movimiento>.ultimoPago(): Long? =
    filter { it.tipo == TipoMovimiento.PAGO }.maxOfOrNull { it.fecha }

/** Medianoche local del dia que contiene [ms]. */
private fun aMedianoche(ms: Long): Calendar = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}

/**
 * Fecha de corte de un mes concreto. Si el mes no llega al dia pedido
 * (31 en febrero), cae en el ultimo dia de ese mes.
 */
fun fechaCorte(anio: Int, mesCero: Int, diaPago: Int): Calendar {
    val c = Calendar.getInstance().apply {
        clear()
        set(Calendar.YEAR, anio)
        set(Calendar.MONTH, mesCero)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val ultimo = c.getActualMaximum(Calendar.DAY_OF_MONTH)
    c.set(Calendar.DAY_OF_MONTH, minOf(diaPago, ultimo))
    return c
}

/** Proxima fecha de corte igual o posterior a [hoyMs]. */
fun proximoVencimiento(diaPago: Int, hoyMs: Long = System.currentTimeMillis()): Calendar {
    val hoy = aMedianoche(hoyMs)
    var v = fechaCorte(hoy.get(Calendar.YEAR), hoy.get(Calendar.MONTH), diaPago)
    if (v.before(hoy)) {
        val sig = (hoy.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        v = fechaCorte(sig.get(Calendar.YEAR), sig.get(Calendar.MONTH), diaPago)
    }
    return v
}

private fun diasEntre(desde: Calendar, hasta: Calendar): Int =
    ((hasta.timeInMillis - desde.timeInMillis) / 86_400_000L).toInt()

/**
 * Estado de un deudor.
 *
 * La regla de mora es la misma que en la version web: esta atrasado si su
 * ultimo pago es anterior a la fecha de corte anterior, es decir, si paso un
 * dia 15 sin que abonara nada. Si nunca ha pagado, se compara contra la fecha
 * en que se registro la deuda.
 */
fun analizar(
    deudor: Deudor,
    movimientos: List<Movimiento>,
    hoyMs: Long = System.currentTimeMillis()
): Analisis {
    val saldo = movimientos.saldo()
    val hoy = aMedianoche(hoyMs)
    val prox = proximoVencimiento(deudor.diaPago, hoyMs)
    val diasRestantes = diasEntre(hoy, prox)

    if (saldo <= 0.0) {
        return Analisis(saldo, Estado.PAGADO, prox.timeInMillis, diasRestantes, 0)
    }

    val anterior = (prox.clone() as Calendar).let {
        it.add(Calendar.MONTH, -1)
        fechaCorte(it.get(Calendar.YEAR), it.get(Calendar.MONTH), deudor.diaPago)
    }
    val referencia = aMedianoche(movimientos.ultimoPago() ?: deudor.creadoEn)

    val mora = if (referencia.before(anterior) && !anterior.after(hoy))
        abs(diasEntre(anterior, hoy)) else 0

    val estado = when {
        mora > 0 -> Estado.ATRASADO
        diasRestantes <= 5 -> Estado.POR_VENCER
        else -> Estado.AL_DIA
    }
    return Analisis(saldo, estado, prox.timeInMillis, diasRestantes, mora)
}

/** Saldo acumulado tras cada movimiento, en orden cronologico. */
fun List<Movimiento>.serieDeSaldo(): List<Pair<Movimiento, Double>> {
    var acumulado = 0.0
    return sortedBy { it.fecha }.map { m ->
        acumulado = r2(if (m.tipo == TipoMovimiento.CARGO) acumulado + m.monto else acumulado - m.monto)
        m to acumulado
    }
}

/**
 * Normaliza un telefono a formato internacional para el enlace de WhatsApp:
 * 0991112233, como se escribe en Ecuador, tiene que salir como 593991112233.
 */
fun telefonoWhatsApp(t: String, codigoPais: String = "593"): String {
    val n = t.filter { it.isDigit() }
    return when {
        n.startsWith("00") -> n.drop(2)
        n.startsWith(codigoPais) -> n
        n.startsWith("0") -> codigoPais + n.drop(1)
        n.length <= 9 -> codigoPais + n
        else -> n
    }
}
