package com.summed.deudores.notif

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.summed.deudores.data.BaseDatos
import com.summed.deudores.data.Estado
import com.summed.deudores.data.Preferencias
import com.summed.deudores.data.analizar
import com.summed.deudores.data.proximoVencimiento
import java.util.Calendar

object Recordatorios {

    const val CANAL = "recordatorios_pago"
    const val ID_NOTIFICACION = 4101
    private const val CODIGO_ALARMA = 9021

    fun crearCanal(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CANAL) != null) return
        val canal = NotificationChannel(
            CANAL,
            "Recordatorios de pago",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisa el día de cobro y cuando alguien se atrasa"
            enableVibration(true)
            // El canal va en silencio a proposito: el tono lo pone ServicioAviso,
            // que es quien controla cuantos segundos suena. Si el canal tambien
            // sonara, se oirian dos tonos a la vez.
            setSound(null, null)
        }
        nm.createNotificationChannel(canal)
    }

    /**
     * Programa el proximo aviso.
     *
     * Se usa setAlarmClock y no setExactAndAllowWhileIdle porque es el unico
     * que atraviesa el modo Doze de forma fiable: con el telefono dormido, un
     * recordatorio del dia 15 se retrasaria horas o hasta el dia siguiente.
     * Es la misma leccion que salio en SummedAlarma.
     */
    suspend fun programarProximo(context: Context) {
        val dao = BaseDatos.obtener(context).dao()
        val prefs = Preferencias.obtener(context)
        val hora = prefs.horaAviso.value
        val minuto = prefs.minutoAviso.value
        val deudores = dao.deudores()
        val ahora = System.currentTimeMillis()

        val candidatos = mutableListOf<Long>()
        var hayAtrasados = false

        for (d in deudores) {
            val movs = dao.movimientosDe(d.id)
            val a = analizar(d, movs, ahora)
            if (a.estado == Estado.PAGADO) continue
            if (a.estado == Estado.ATRASADO) hayAtrasados = true

            val venc = proximoVencimiento(d.diaPago, ahora).apply {
                set(Calendar.HOUR_OF_DAY, hora)
                set(Calendar.MINUTE, minuto); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            if (venc.timeInMillis > ahora) candidatos.add(venc.timeInMillis)
        }

        // Si alguien ya esta atrasado, se vuelve a recordar mañana en vez de
        // esperar al siguiente corte, que puede ser dentro de un mes.
        if (hayAtrasados) {
            val mañana = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, hora)
                set(Calendar.MINUTE, minuto); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            candidatos.add(mañana.timeInMillis)
        }

        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context)
        val proximo = candidatos.filter { it > ahora }.minOrNull()

        if (proximo == null) {
            am.cancel(pi)
            return
        }

        val puedeExactas = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (puedeExactas) {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(proximo, pi), pi)
        } else {
            // Sin permiso de alarmas exactas el aviso puede llegar tarde, pero
            // llega. Peor seria no programar nada.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, proximo, pi)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            CODIGO_ALARMA,
            Intent(context, RecordatorioReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
