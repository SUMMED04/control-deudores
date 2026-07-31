package com.summed.deudores.notif

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.summed.deudores.MainActivity
import com.summed.deudores.R
import com.summed.deudores.data.BaseDatos
import com.summed.deudores.data.Estado
import com.summed.deudores.data.analizar
import com.summed.deudores.data.r2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Se dispara en la fecha de corte. Arma el aviso con quien debe y vuelve a
 * programar el siguiente, porque setAlarmClock es de un solo uso.
 */
class RecordatorioReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                avisar(context)
                Recordatorios.programarProximo(context)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun avisar(context: Context) {
        val dao = BaseDatos.obtener(context).dao()
        val ahora = System.currentTimeMillis()

        val atrasados = mutableListOf<Pair<String, Int>>()
        val vencenHoy = mutableListOf<String>()
        var totalDebido = 0.0

        for (d in dao.deudores()) {
            val a = analizar(d, dao.movimientosDe(d.id), ahora)
            when (a.estado) {
                Estado.ATRASADO -> { atrasados.add(d.nombre to a.diasMora); totalDebido = r2(totalDebido + a.saldo) }
                Estado.POR_VENCER -> if (a.diasRestantes == 0) {
                    vencenHoy.add(d.nombre); totalDebido = r2(totalDebido + a.saldo)
                }
                else -> Unit
            }
        }

        if (atrasados.isEmpty() && vencenHoy.isEmpty()) return

        val titulo = when {
            atrasados.isNotEmpty() && vencenHoy.isNotEmpty() ->
                "${atrasados.size} atrasados y ${vencenHoy.size} vencen hoy"
            atrasados.isNotEmpty() ->
                if (atrasados.size == 1) "${atrasados[0].first} lleva ${atrasados[0].second} días de atraso"
                else "${atrasados.size} deudores atrasados"
            else ->
                if (vencenHoy.size == 1) "${vencenHoy[0]} te paga hoy"
                else "${vencenHoy.size} pagos vencen hoy"
        }

        val detalle = buildString {
            atrasados.take(4).forEach { (nombre, dias) -> appendLine("$nombre: $dias días de atraso") }
            vencenHoy.take(4).forEach { nombre -> appendLine("$nombre: vence hoy") }
            val restantes = (atrasados.size + vencenHoy.size) - minOf(4, atrasados.size) - minOf(4, vencenHoy.size)
            if (restantes > 0) append("y $restantes más")
        }.trim()

        val abrir = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Recordatorios.crearCanal(context)
        val notif = NotificationCompat.Builder(context, Recordatorios.CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(detalle.lineSequence().firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detalle))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(abrir)
            .build()

        val permitido = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (permitido) {
            context.getSystemService(NotificationManager::class.java)
                .notify(Recordatorios.ID_NOTIFICACION, notif)
        }
    }
}
