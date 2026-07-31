package com.summed.deudores.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AjustesScreen(vm: DeudoresViewModel, padding: PaddingValues) {
    val contexto = LocalContext.current
    val totales by vm.totales.collectAsState()
    var confirmarBorrado by remember { mutableStateOf(false) }

    val alarmasExactas = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            contexto.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        else true
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
            start = 14.dp, end = 14.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Bloque("Recordatorios") {
            Text(
                "La app te avisa a las 9 de la mañana del día de cobro de cada deudor, " +
                    "y vuelve a insistir cada día mientras siga atrasado.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (alarmasExactas) "Alarmas exactas: permitidas"
                else "Alarmas exactas: bloqueadas",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (alarmasExactas) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
            )
            if (!alarmasExactas) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sin este permiso el aviso puede llegar horas tarde, porque Android " +
                        "deja la alarma para cuando el teléfono despierte.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            contexto.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Permitir alarmas exactas") }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { abrirAjustesNotificaciones(contexto) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Ajustes de notificaciones") }
        }

        Bloque("Tus datos") {
            Fila("Deudores registrados", "${totales.cuantos}")
            Fila("Por cobrar", dinero(totales.porCobrar))
            Fila("Cobrado hasta hoy", dinero(totales.cobrado))
            Spacer(Modifier.height(10.dp))
            Text(
                "Todo se guarda solo en este teléfono. Si desinstalas la app o borras " +
                    "sus datos desde los ajustes de Android, el historial se pierde.",
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

private fun abrirAjustesNotificaciones(contexto: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName)
    }
    if (intent.resolveActivity(contexto.packageManager) != null) {
        contexto.startActivity(intent)
    } else {
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
        shape = MaterialTheme.shapes.large,
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
