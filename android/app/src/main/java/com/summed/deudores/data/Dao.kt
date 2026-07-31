package com.summed.deudores.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeudoresDao {

    @Query("SELECT * FROM deudores ORDER BY nombre COLLATE NOCASE")
    fun observarDeudores(): Flow<List<Deudor>>

    @Query("SELECT * FROM movimientos ORDER BY fecha")
    fun observarMovimientos(): Flow<List<Movimiento>>

    @Query("SELECT * FROM deudores")
    suspend fun deudores(): List<Deudor>

    @Query("SELECT * FROM movimientos WHERE deudorId = :deudorId ORDER BY fecha")
    suspend fun movimientosDe(deudorId: Long): List<Movimiento>

    @Insert
    suspend fun insertarDeudor(deudor: Deudor): Long

    @Update
    suspend fun actualizarDeudor(deudor: Deudor)

    @Delete
    suspend fun borrarDeudor(deudor: Deudor)

    @Insert
    suspend fun insertarMovimiento(movimiento: Movimiento)

    @Delete
    suspend fun borrarMovimiento(movimiento: Movimiento)

    @Query("DELETE FROM deudores")
    suspend fun borrarTodo()
}
