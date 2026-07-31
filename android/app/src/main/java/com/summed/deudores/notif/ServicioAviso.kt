package com.summed.deudores.notif

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.summed.deudores.MainActivity
import com.summed.deudores.R
import com.summed.deudores.data.Preferencias

/**
 * Suena el aviso durante los segundos configurados y se apaga solo.
 *
 * Es un servicio en primer plano y no un simple sonido lanzado desde el
 * BroadcastReceiver: un receiver tiene unos segundos de vida y el sistema puede
 * matar el proceso a mitad, cortando el tono. El servicio garantiza que suene
 * los 10 o 20 segundos completos.
 */
class ServicioAviso : Service() {

    private var reproductor: MediaPlayer? = null
    private val manejador = Handler(Looper.getMainLooper())
    private val apagar = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val titulo = intent?.getStringExtra(EXTRA_TITULO) ?: "Recordatorio de pago"
        val detalle = intent?.getStringExtra(EXTRA_DETALLE).orEmpty()
        val prefs = Preferencias.obtener(this)
        val segundos = intent?.getIntExtra(EXTRA_SEGUNDOS, 0)?.takeIf { it > 0 }
            ?: prefs.segundosSonido.value

        Recordatorios.crearCanal(this)
        iniciarEnPrimerPlano(notificacion(titulo, detalle))
        sonar(prefs.tono.value)

        manejador.removeCallbacks(apagar)
        manejador.postDelayed(apagar, segundos * 1000L)
        return START_NOT_STICKY
    }

    private fun iniciarEnPrimerPlano(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Recordatorios.ID_NOTIFICACION, n,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(Recordatorios.ID_NOTIFICACION, n)
        }
    }

    private fun notificacion(titulo: String, detalle: String): Notification {
        val abrir = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Recordatorios.CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(detalle.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(detalle))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(abrir)
            .build()
    }

    /**
     * Si el tono elegido ya no existe (se borró el archivo, se quitó la tarjeta
     * SD, la app perdió el permiso), se cae al tono de alarma del sistema en vez
     * de quedarse en silencio o reventar.
     */
    private fun sonar(uriGuardada: String?) {
        val candidatos = listOfNotNull(
            uriGuardada?.let { runCatching { Uri.parse(it) }.getOrNull() },
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        )
        for (uri in candidatos) {
            val ok = runCatching {
                reproductor = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@ServicioAviso, uri)
                    isLooping = true
                    prepare()
                    start()
                }
                true
            }.getOrDefault(false)
            if (ok) return
            liberar()
        }
    }

    private fun liberar() {
        runCatching { reproductor?.stop() }
        runCatching { reproductor?.release() }
        reproductor = null
    }

    override fun onDestroy() {
        manejador.removeCallbacks(apagar)
        liberar()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TITULO = "titulo"
        const val EXTRA_DETALLE = "detalle"
        const val EXTRA_SEGUNDOS = "segundos"

        fun lanzar(context: Context, titulo: String, detalle: String, segundos: Int = 0) {
            val intent = Intent(context, ServicioAviso::class.java).apply {
                putExtra(EXTRA_TITULO, titulo)
                putExtra(EXTRA_DETALLE, detalle)
                putExtra(EXTRA_SEGUNDOS, segundos)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
