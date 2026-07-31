package com.summed.deudores.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Los archivos se generan en cacheDir y se ofrecen con el selector del sistema:
 * asi el usuario elige si guardarlos, mandarlos por WhatsApp o subirlos, sin
 * pedir permisos de almacenamiento.
 */
object Compartir {

    private fun enviar(context: Context, archivo: File, tipoMime: String, titulo: String) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.archivos", archivo
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = tipoMime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, archivo.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, titulo).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun pdf(context: Context, archivo: File) =
        enviar(context, archivo, "application/pdf", "Compartir el reporte PDF")

    fun excel(context: Context, archivo: File) = enviar(
        context, archivo,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "Compartir el Excel"
    )
}
