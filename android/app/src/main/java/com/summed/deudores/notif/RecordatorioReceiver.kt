package com.summed.deudores.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.summed.deudores.data.BaseDatos
import com.summed.deudores.data.Estado
import com.summed.deudores.data.analizar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Se dispara en la fecha de corte. Arma el texto del aviso, se lo pasa al
 * servicio (que es quien lo hace sonar) y vuelve a programar el siguiente,
 * porque setAlarmClock es de un solo uso.
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

        for (d in dao.deudores()) {
            val a = analizar(d, dao.movimientosDe(d.id), ahora)
            when {
                a.estado == Estado.ATRASADO -> atrasados.add(d.nombre to a.diasMora)
                a.estado == Estado.POR_VENCER && a.diasRestantes == 0 -> vencenHoy.add(d.nombre)
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
            val mostrados = minOf(4, atrasados.size) + minOf(4, vencenHoy.size)
            val restantes = atrasados.size + vencenHoy.size - mostrados
            if (restantes > 0) append("y $restantes más")
        }.trim()

        ServicioAviso.lanzar(context, titulo, detalle)
    }
}
