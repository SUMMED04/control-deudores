package com.summed.deudores.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.summed.deudores.BUILD_TAG
import com.summed.deudores.data.Preferencias
import com.summed.deudores.data.Tema
import com.summed.deudores.export.Compartir
import com.summed.deudores.export.ExportadorExcel
import com.summed.deudores.export.ExportadorPdf
import com.summed.deudores.notif.Recordatorios
import com.summed.deudores.notif.ServicioAviso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(
    vm: DeudoresViewModel,
    padding: PaddingValues,
    onMensaje: (String) -> Unit
) {
    val contexto = LocalContext.current
    val prefs = remember { Preferencias.obtener(contexto) }
    val alcance = rememberCoroutineScope()

    val fichas by vm.fichas.collectAsState()
    val totales by vm.totales.collectAsState()
    val tema by prefs.tema.collectAsState()
    val hora by prefs.horaAviso.collectAsState()
    val minuto by prefs.minutoAviso.collectAsState()
    val segundos by prefs.segundosSonido.collectAsState()
    val tono by prefs.tono.collectAsState()
    val logo by prefs.logo.collectAsState()
    val fondo by prefs.fondo.collectAsState()
    val opacidad by prefs.opacidadFondo.collectAsState()

    var confirmarBorrado by remember { mutableStateOf(false) }
    var eligiendoHora by remember { mutableStateOf(false) }
    var exportando by remember { mutableStateOf(false) }

    val alarmasExactas = remember(hora) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            contexto.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        else true
    }

    val elegirLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val ok = prefs.guardarImagen(uri, Preferencias.Cual.LOGO)
            onMensaje(if (ok) "Logo actualizado" else "No se pudo leer esa imagen")
        }
    }
    val elegirFondo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val ok = prefs.guardarImagen(uri, Preferencias.Cual.FONDO)
            onMensaje(if (ok) "Fondo actualizado" else "No se pudo leer esa imagen")
        }
    }
    val elegirTono = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        val uri = resultado.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        prefs.cambiarTono(uri?.toString())
        onMensaje(if (uri == null) "Se usará el tono de alarma del sistema" else "Tono guardado")
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
            start = 14.dp, end = 14.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Bloque("Aviso del día de cobro") {
            Fila("Hora del aviso", "%02d:%02d".format(hora, minuto))
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { eligiendoHora = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Cambiar la hora")
            }

            Spacer(Modifier.height(14.dp))
            Text("Tono", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                if (tono == null) "El de alarma del sistema" else nombreTono(contexto, tono),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        elegirTono.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Tono del recordatorio")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    tono?.let { Uri.parse(it) }
                                )
                            }
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Elegir tono") }

                OutlinedButton(
                    onClick = {
                        ServicioAviso.lanzar(
                            contexto, "Prueba de sonido",
                            "Así sonará el recordatorio durante $segundos segundos.",
                            segundos
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Probar") }
            }

            Spacer(Modifier.height(14.dp))
            Text("Cuánto suena", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 20, 30).forEach { s ->
                    FilterChip(
                        selected = segundos == s,
                        onClick = { prefs.cambiarSegundosSonido(s) },
                        label = { Text("$s s") }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                if (alarmasExactas) "Alarmas exactas: permitidas"
                else "Alarmas exactas: bloqueadas",
                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = if (alarmasExactas) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
            )
            if (!alarmasExactas) {
                Text(
                    "Sin este permiso el aviso puede llegar horas tarde, porque Android " +
                        "deja la alarma para cuando el teléfono despierte.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            contexto.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Permitir alarmas exactas") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { abrirAjustesNotificaciones(contexto) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Ajustes de notificaciones") }
        }

        Bloque("Apariencia") {
            Text("Tema", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tema.entries.forEach { t ->
                    FilterChip(
                        selected = tema == t,
                        onClick = { prefs.cambiarTema(t) },
                        label = {
                            Text(
                                when (t) {
                                    Tema.SISTEMA -> "Automático"
                                    Tema.CLARO -> "Claro"
                                    Tema.OSCURO -> "Oscuro"
                                }
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Logo", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                "Aparece arriba de la lista y en la cabecera del PDF.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            logo?.let { archivo ->
                Spacer(Modifier.height(8.dp))
                Vista(archivo.absolutePath, 70.dp)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { elegirLogo.launch("image/*") }, modifier = Modifier.weight(1f)) {
                    Text(if (logo == null) "Elegir logo" else "Cambiar")
                }
                if (logo != null) {
                    OutlinedButton(
                        onClick = { prefs.quitarImagen(Preferencias.Cual.LOGO); onMensaje("Logo quitado") },
                        modifier = Modifier.weight(1f)
                    ) { Text("Quitar") }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Imagen de fondo", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                "Solo decora la pantalla. No sale en el PDF ni en el Excel, " +
                    "que se generan aparte.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            fondo?.let { archivo ->
                Spacer(Modifier.height(8.dp))
                Vista(archivo.absolutePath, 90.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Intensidad ${(opacidad * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Slider(
                    value = opacidad,
                    onValueChange = { prefs.cambiarOpacidadFondo(it) },
                    valueRange = 0.05f..0.6f
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { elegirFondo.launch("image/*") }, modifier = Modifier.weight(1f)) {
                    Text(if (fondo == null) "Elegir fondo" else "Cambiar")
                }
                if (fondo != null) {
                    OutlinedButton(
                        onClick = { prefs.quitarImagen(Preferencias.Cual.FONDO); onMensaje("Fondo quitado") },
                        modifier = Modifier.weight(1f)
                    ) { Text("Quitar") }
                }
            }
        }

        Bloque("Exportar") {
            Text(
                "El reporte se genera y se abre el selector para que elijas dónde " +
                    "guardarlo o a quién enviarlo.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        exportando = true
                        alcance.launch {
                            val r = runCatching {
                                withContext(Dispatchers.IO) {
                                    ExportadorPdf.generar(contexto, fichas, logo)
                                }
                            }
                            exportando = false
                            r.onSuccess { Compartir.pdf(contexto, it) }
                                .onFailure { onMensaje("No se pudo generar el PDF") }
                        }
                    },
                    enabled = fichas.isNotEmpty() && !exportando,
                    modifier = Modifier.weight(1f)
                ) { Text("PDF") }

                Button(
                    onClick = {
                        exportando = true
                        alcance.launch {
                            val r = runCatching {
                                withContext(Dispatchers.IO) {
                                    ExportadorExcel.generar(contexto, fichas)
                                }
                            }
                            exportando = false
                            r.onSuccess { Compartir.excel(contexto, it) }
                                .onFailure { onMensaje("No se pudo generar el Excel") }
                        }
                    },
                    enabled = fichas.isNotEmpty() && !exportando,
                    modifier = Modifier.weight(1f)
                ) { Text("Excel") }
            }
            if (exportando) {
                Spacer(Modifier.height(8.dp))
                Text("Generando...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }

        Bloque("Tus datos") {
            Fila("Deudores registrados", "${totales.cuantos}")
            Fila("Por cobrar", dinero(totales.porCobrar))
            Fila("Cobrado hasta hoy", dinero(totales.cobrado))
            Spacer(Modifier.height(10.dp))
            Text(
                "Todo se guarda solo en este teléfono. Si desinstalas la app o borras " +
                    "sus datos desde los ajustes de Android, el historial se pierde. " +
                    "Exporta el Excel de vez en cuando.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Bloque("Zona de riesgo") {
            Text(
                "Borra los ${totales.cuantos} deudores y todos sus movimientos. " +
                    "No se puede deshacer.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { confirmarBorrado = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
                enabled = totales.cuantos > 0
            ) { Text("Borrar todo") }
        }

        // Pie igual al de las otras apps, y solo aqui: en el resto de pantallas
        // la version no aporta nada y solo roba espacio.
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Versión $BUILD_TAG",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Proyecto por Jordy C.",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Construction,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "En construcción",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }

    if (eligiendoHora) {
        val estado = rememberTimePickerState(initialHour = hora, initialMinute = minuto, is24Hour = true)
        AlertDialog(
            onDismissRequest = { eligiendoHora = false },
            title = { Text("Hora del aviso") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = estado)
                }
            },
            confirmButton = {
                Button(onClick = {
                    prefs.cambiarHoraAviso(estado.hour, estado.minute)
                    eligiendoHora = false
                    // Cambiar la hora mueve el proximo aviso, hay que reprogramar.
                    alcance.launch(Dispatchers.IO) { Recordatorios.programarProximo(contexto) }
                    onMensaje("Aviso a las %02d:%02d".format(estado.hour, estado.minute))
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { eligiendoHora = false }) { Text("Cancelar") } }
        )
    }

    if (confirmarBorrado) {
        DialogoConfirmar(
            titulo = "Borrar todo",
            mensaje = "Se eliminarán los ${totales.cuantos} deudores y todo su historial de movimientos.",
            textoConfirmar = "Sí, borrar todo",
            onCerrar = { confirmarBorrado = false },
            onConfirmar = { vm.borrarTodo() }
        )
    }
}

@Composable
private fun Vista(ruta: String, alto: androidx.compose.ui.unit.Dp) {
    val bmp = remember(ruta, java.io.File(ruta).lastModified()) {
        runCatching { android.graphics.BitmapFactory.decodeFile(ruta) }.getOrNull()
    }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(alto).clip(RoundedCornerShape(10.dp))
        )
    }
}

private fun nombreTono(contexto: Context, uri: String?): String = runCatching {
    RingtoneManager.getRingtone(contexto, Uri.parse(uri)).getTitle(contexto)
}.getOrDefault("Tono personalizado")

private fun abrirAjustesNotificaciones(contexto: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName)
    }
    runCatching { contexto.startActivity(intent) }.onFailure {
        contexto.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", contexto.packageName, null)
            }
        )
    }
}

@Composable
private fun Bloque(titulo: String, contenido: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                titulo.uppercase(),
                fontSize = 10.sp, letterSpacing = 0.7.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(10.dp))
            contenido()
        }
    }
}

@Composable
private fun Fila(etiqueta: String, valor: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(etiqueta, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(valor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
