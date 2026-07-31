package com.summed.deudores.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

@Composable
fun TarjetaTotal(etiqueta: String, valor: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                etiqueta.uppercase(),
                fontSize = 10.sp,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(valor, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = color)
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

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onAbrir),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Franja lateral con el color del estado: permite barrer la lista
            // sin leer cada etiqueta.
            Box(Modifier.width(4.dp).fillMaxHeight().background(color))

            Column(Modifier.padding(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            ficha.deudor.nombre,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            textoEstado(ficha),
                            fontSize = 12.sp,
                            color = color
                        )
                    }
                    Text(
                        dinero(maxOf(0.0, ficha.analisis.saldo)),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { ficha.avance },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${dinero(ficha.pagado)} de ${dinero(ficha.prestado)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Button(
                        onClick = onCobrar,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        enabled = ficha.analisis.saldo > 0
                    ) { Text("Cobrar") }

                    IconButton(onClick = onAumentar) {
                        Icon(Icons.Default.Add, contentDescription = "Aumentar deuda", tint = coloresEstado().porVencer)
                    }
                    IconButton(onClick = onEditar) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = onBorrar) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = coloresEstado().atrasado)
                    }
                }
            }
        }
    }
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
