package com.summed.deudores.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.summed.deudores.data.Estado
import com.summed.deudores.data.etiqueta
import com.summed.deudores.ui.theme.coloresEstado

@Composable
fun colorDe(estado: Estado): Color {
    val c = coloresEstado()
    return when (estado) {
        Estado.AL_DIA -> c.alDia
        Estado.POR_VENCER -> c.porVencer
        Estado.ATRASADO -> c.atrasado
        Estado.PAGADO -> c.pagado
    }
}

/**
 * Cifra suelta, sin tarjeta ni sombra: la separacion entre una y otra la hace
 * una linea de un pixel, no una caja.
 */
@Composable
fun TarjetaTotal(etiqueta: String, valor: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(end = 12.dp)) {
        Text(
            etiqueta.uppercase(),
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(3.dp))
        Text(valor, fontSize = 20.sp, fontWeight = FontWeight.Light, color = color)
    }
}

/**
 * Encabezado de la lista: el total en grande y una barra que reparte lo
 * cobrado, lo vencido y lo que falta. Reemplaza a las cuatro tarjetas.
 */
@Composable
fun ResumenCartera(porCobrar: Double, cobrado: Double, atrasado: Double) {
    val c = coloresEstado()
    val prestado = porCobrar + cobrado
    val fCobrado = if (prestado > 0) (cobrado / prestado).toFloat() else 0f
    val fAtrasado = if (prestado > 0) (atrasado / prestado).toFloat() else 0f

    Column(Modifier.fillMaxWidth()) {
        Text(
            "Te deben en total",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            dinero(porCobrar),
            fontSize = 40.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Row {
            Text(
                "de ${dinero(prestado)} prestados · ",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${(fCobrado * 100).toInt()}% recuperado",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = c.alDia
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (fCobrado > 0f) Box(Modifier.weight(fCobrado).fillMaxHeight().background(c.alDia))
            if (fAtrasado > 0f) Box(Modifier.weight(fAtrasado).fillMaxHeight().background(c.atrasado))
            val resto = (1f - fCobrado - fAtrasado).coerceAtLeast(0.0001f)
            Box(Modifier.weight(resto).fillMaxHeight())
        }
    }
}

/** Franja de aviso: solo aparece si hay alguien atrasado o a punto de vencer. */
@Composable
fun AvisoAtraso(texto: String, atrasado: Boolean, onClick: () -> Unit) {
    val c = coloresEstado()
    val color = if (atrasado) c.atrasado else c.porVencer
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
            Spacer(Modifier.width(10.dp))
            Text(texto, fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun TarjetaDeudor(
    ficha: Ficha,
    onCobrar: () -> Unit,
    onAumentar: () -> Unit,
    onEditar: () -> Unit,
    onBorrar: () -> Unit,
    onAbrir: () -> Unit
) {
    val estado = ficha.analisis.estado
    val color = colorDe(estado)

    // Sin tarjeta, sin sombra y sin franja de color: cada deudor es una fila
    // separada de la siguiente por una linea de un pixel.
    Column(Modifier.fillMaxWidth().clickable(onClick = onAbrir).padding(vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    ficha.deudor.nombre,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${dinero(ficha.pagado)} de ${dinero(ficha.prestado)}",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    dinero(maxOf(0.0, ficha.analisis.saldo)),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    textoEstado(ficha),
                    fontSize = 11.sp,
                    fontWeight = if (estado == Estado.ATRASADO) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (estado == Estado.ATRASADO) color
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { ficha.avance },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = if (estado == Estado.ATRASADO) color else MaterialTheme.colorScheme.onSurface,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onCobrar,
                enabled = ficha.analisis.saldo > 0,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) { Text("Cobrar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }

            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAumentar, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Add, contentDescription = "Aumentar deuda",
                    tint = coloresEstado().porVencer, modifier = Modifier.size(19.dp)
                )
            }
            IconButton(onClick = onEditar, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Edit, contentDescription = "Editar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onBorrar, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete, contentDescription = "Eliminar",
                    tint = coloresEstado().atrasado, modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun textoEstado(ficha: Ficha): String {
    val a = ficha.analisis
    return when (a.estado) {
        Estado.PAGADO -> "Pagado"
        Estado.ATRASADO -> "Atrasado ${dias(a.diasMora)}"
        Estado.POR_VENCER ->
            if (a.diasRestantes == 0) "Vence hoy"
            else "Vence en ${dias(a.diasRestantes)}"
        Estado.AL_DIA -> "${a.estado.etiqueta} · vence ${fechaCortaDe(a.proximoVencimiento)}"
    }
}
