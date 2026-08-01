package com.summed.deudores.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.summed.deudores.data.Analisis
import com.summed.deudores.data.BaseDatos
import com.summed.deudores.data.Deudor
import com.summed.deudores.data.Estado
import com.summed.deudores.data.Movimiento
import com.summed.deudores.data.TipoMovimiento
import com.summed.deudores.data.analizar
import com.summed.deudores.data.r2
import com.summed.deudores.data.totalPagado
import com.summed.deudores.data.totalPrestado
import com.summed.deudores.notif.Recordatorios
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Un deudor listo para pintar: sus datos, sus movimientos y su analisis. */
data class Ficha(
    val deudor: Deudor,
    val movimientos: List<Movimiento>,
    val analisis: Analisis
) {
    val prestado: Double get() = movimientos.totalPrestado()
    val pagado: Double get() = movimientos.totalPagado()
    val avance: Float get() = if (prestado > 0) (pagado / prestado).toFloat().coerceIn(0f, 1f) else 0f
}

data class Totales(
    val porCobrar: Double = 0.0,
    val cobrado: Double = 0.0,
    val atrasado: Double = 0.0,
    val cuantos: Int = 0
)

enum class Orden { SALDO, NOMBRE, VENCE, RECIENTE }

class DeudoresViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = BaseDatos.obtener(app).dao()

    val busqueda = MutableStateFlow("")
    val filtro = MutableStateFlow<Estado?>(null)
    val orden = MutableStateFlow(Orden.SALDO)

    /** Todas las fichas, sin filtrar. Los totales se calculan sobre esto. */
    val fichas: StateFlow<List<Ficha>> =
        combine(dao.observarDeudores(), dao.observarMovimientos()) { deudores, movimientos ->
            val porDeudor = movimientos.groupBy { it.deudorId }
            deudores.map { d ->
                val movs = porDeudor[d.id].orEmpty()
                Ficha(d, movs, analizar(d, movs))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totales: StateFlow<Totales> = fichas
        .map { lista ->
            Totales(
                porCobrar = r2(lista.sumOf { maxOf(0.0, it.analisis.saldo) }),
                cobrado = r2(lista.sumOf { it.pagado }),
                atrasado = r2(lista.filter { it.analisis.estado == Estado.ATRASADO }
                    .sumOf { it.analisis.saldo }),
                cuantos = lista.size
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Totales())

    /** Lo que se pinta en la lista, ya filtrado y ordenado. */
    val visibles: StateFlow<List<Ficha>> =
        combine(fichas, busqueda, filtro, orden) { lista, q, est, ord ->
            lista.filter { f ->
                (est == null || f.analisis.estado == est) &&
                    (q.isBlank() ||
                        f.deudor.nombre.contains(q, ignoreCase = true) ||
                        f.deudor.telefono.contains(q))
            }.sortedWith(comparador(ord))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun comparador(orden: Orden): Comparator<Ficha> = when (orden) {
        Orden.SALDO -> compareByDescending { it.analisis.saldo }
        Orden.NOMBRE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.deudor.nombre }
        Orden.VENCE -> compareBy { it.analisis.proximoVencimiento }
        Orden.RECIENTE -> compareByDescending { it.deudor.creadoEn }
    }

    // ---------- operaciones ----------

    /**
     * La fecha viene de la pantalla, no del reloj: por defecto es hoy, pero
     * permite anotar un prestamo viejo sin que quede con la fecha de captura.
     */
    fun crearDeudor(
        nombre: String, monto: Double, cuota: Double,
        diaPago: Int, telefono: String, notas: String,
        fecha: Long = System.currentTimeMillis()
    ) = enIO {
        val id = dao.insertarDeudor(
            Deudor(
                nombre = nombre.trim(), telefono = telefono.trim(), notas = notas.trim(),
                diaPago = diaPago, pagoMensual = cuota, creadoEn = fecha
            )
        )
        dao.insertarMovimiento(
            Movimiento(
                deudorId = id, tipo = TipoMovimiento.CARGO,
                monto = r2(monto), nota = "Préstamo inicial", fecha = fecha
            )
        )
    }

    fun editarDeudor(deudor: Deudor) = enIO { dao.actualizarDeudor(deudor) }

    fun borrarDeudor(deudor: Deudor) = enIO { dao.borrarDeudor(deudor) }

    fun borrarTodo() = enIO { dao.borrarTodo() }

    /** Registra un cobro en la fecha que se eligio, que por defecto es hoy. */
    fun registrarPago(
        deudorId: Long, monto: Double, nota: String,
        fecha: Long = System.currentTimeMillis()
    ) = enIO {
        dao.insertarMovimiento(
            Movimiento(
                deudorId = deudorId, tipo = TipoMovimiento.PAGO,
                monto = r2(monto), nota = nota.trim(), fecha = fecha
            )
        )
    }

    /** Suma deuda: un prestamo nuevo, que queda en el historial. */
    fun registrarCargo(
        deudorId: Long, monto: Double, nota: String,
        fecha: Long = System.currentTimeMillis()
    ) = enIO {
        dao.insertarMovimiento(
            Movimiento(
                deudorId = deudorId, tipo = TipoMovimiento.CARGO,
                monto = r2(monto), nota = nota.trim(), fecha = fecha
            )
        )
    }

    fun borrarMovimiento(movimiento: Movimiento) = enIO { dao.borrarMovimiento(movimiento) }

    /**
     * Cualquier cambio puede adelantar o retrasar el proximo aviso, asi que se
     * reprograma la alarma despues de cada operacion.
     */
    private fun enIO(bloque: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            bloque()
            Recordatorios.programarProximo(getApplication<Application>())
        }
    }
}
