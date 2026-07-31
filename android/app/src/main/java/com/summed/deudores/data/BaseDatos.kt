package com.summed.deudores.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Convertidores {
    @TypeConverter
    fun aTipo(valor: String): TipoMovimiento = TipoMovimiento.valueOf(valor)

    @TypeConverter
    fun deTipo(tipo: TipoMovimiento): String = tipo.name
}

@Database(entities = [Deudor::class, Movimiento::class], version = 1, exportSchema = false)
@TypeConverters(Convertidores::class)
abstract class BaseDatos : RoomDatabase() {
    abstract fun dao(): DeudoresDao

    companion object {
        @Volatile private var instancia: BaseDatos? = null

        fun obtener(context: Context): BaseDatos =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    BaseDatos::class.java,
                    "deudores.db"
                ).build().also { instancia = it }
            }
    }
}
