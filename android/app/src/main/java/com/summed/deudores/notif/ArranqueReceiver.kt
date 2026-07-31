package com.summed.deudores.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Al reiniciar el telefono se pierden todas las alarmas programadas, asi que
 * hay que volver a poner la del proximo corte. Lo mismo tras actualizar la app.
 */
class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Recordatorios.crearCanal(context)
                Recordatorios.programarProximo(context)
            } finally {
                pending.finish()
            }
        }
    }
}
