package com.summed.deudores.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Mismo modelo que la version web: un deudor NO guarda su saldo.
 * El saldo se calcula sumando sus movimientos, para que borrar uno del
 * historial no deje un saldo guardado diciendo otra cosa.
 */
@Entity(tableName = "deudores")
data class Deudor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val telefono: String = "",
    val notas: String = "",
    /** Dia de corte mensual, 1 a 31. */
    val diaPago: Int = 15,
    /** Cuota sugerida: solo prellena el formulario de cobro. */
    val pagoMensual: Double = 0.0,
    val creadoEn: Long = System.currentTimeMillis()
)

enum class TipoMovimiento { CARGO, PAGO }

@Entity(
    tableName = "movimientos",
    foreignKeys = [ForeignKey(
        entity = Deudor::class,
        parentColumns = ["id"],
        childColumns = ["deudorId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("deudorId")]
)
data class Movimiento(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deudorId: Long,
    val tipo: TipoMovimiento,
    val monto: Double,
    val nota: String = "",
    /** Momento exacto del registro, con hora. No se escribe a mano. */
    val fecha: Long = System.currentTimeMillis()
)

/** Un deudor con sus movimientos ya cargados, que es como lo consume la UI. */
data class DeudorConMovimientos(
    val deudor: Deudor,
    val movimientos: List<Movimiento>
)

enum class Estado { AL_DIA, POR_VENCER, ATRASADO, PAGADO }

/** Resultado de analizar a un deudor en una fecha dada. */
data class Analisis(
    val saldo: Double,
    val estado: Estado,
    /** Proxima fecha de corte, en milisegundos. */
    val proximoVencimiento: Long,
    /** Dias que faltan para el proximo corte. */
    val diasRestantes: Int,
    /** Dias transcurridos desde el corte que se salto. Cero si esta al dia. */
    val diasMora: Int
)

val Estado.etiqueta: String
    get() = when (this) {
        Estado.AL_DIA -> "Al día"
        Estado.POR_VENCER -> "Por vencer"
        Estado.ATRASADO -> "Atrasado"
        Estado.PAGADO -> "Pagado"
    }
