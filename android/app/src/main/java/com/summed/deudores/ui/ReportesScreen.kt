package com.summed.deudores.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.summed.deudores.data.Estado
import com.summed.deudores.ui.theme.coloresEstado

/**
 * Escala de tinta, no un arcoiris: la dona se lee por tono, y el rojo queda
 * reservado para lo vencido en vez de gastarse en un deudor cualquiera.
 */
private val PALETA = listOf(
    Color(0xFF14181F), Color(0xFF4A5261), Color(0xFF7C8494), Color(0xFFA9B0BC),
    Color(0xFFC6CBD4), Color(0xFFDDE0E6), Color(0xFF0F8A5F), Color(0xFFC98A2B)
)

@Composable
fun ReportesScreen(vm: DeudoresViewModel, padding: PaddingValues) {
    val fichas by vm.fichas.collectAsState()
    val totales by vm.totales.collectAsState()
    val c = coloresEstado()

    val ordenadas = fichas.sortedByDescending { it.analisis.saldo }
    val recuperado = if (totales.cobrado + totales.porCobrar > 0)
        (totales.cobrado / (totales.cobrado + totales.porCobrar)).toFloat() else 0f

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp, end = 14.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TarjetaTotal("Por cobrar", dinero(totales.porCobrar), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                TarjetaTotal("Cobrado", dinero(totales.cobrado), c.alDia, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TarjetaTotal("Atrasado", dinero(totales.atrasado), c.atrasado, Modifier.weight(1f))
                TarjetaTotal("Recuperado", "${(recuperado * 100).toInt()}%", c.pagado, Modifier.weight(1f))
            }
        }

        if (fichas.isEmpty()) {
            item {
                Text(
                    "Agrega deudores para ver los reportes.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 30.dp)
                )
            }
            return@LazyColumn
        }

        item {
            Tarjeta("Pendiente vs cobrado por deudor") {
                ordenadas.take(8).forEach { f ->
                    BarraDoble(
                        nombre = f.deudor.nombre,
                        pendiente = maxOf(0.0, f.analisis.saldo),
                        cobrado = f.pagado,
                        maximo = ordenadas.maxOf { maxOf(it.analisis.saldo, it.pagado) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Leyenda("Pendiente", MaterialTheme.colorScheme.primary)
                    Leyenda("Cobrado", c.alDia)
                }
            }
        }

        item {
            val conSaldo = ordenadas.filter { it.analisis.saldo > 0 }
            Tarjeta("Distribución del saldo pendiente") {
                if (conSaldo.isEmpty()) {
                    Text(
                        "Nadie tiene saldo pendiente.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dona(
                            valores = conSaldo.map { it.analisis.saldo.toFloat() },
                            modifier = Modifier.size(130.dp)
                        )
                        Spacer(Modifier.height(0.dp))
                        Column(Modifier.padding(start = 16.dp)) {
                            conSaldo.take(6).forEachIndexed { i, f ->
                                Leyenda(
                                    "${f.deudor.nombre}  ${dinero(f.analisis.saldo)}",
                                    PALETA[i % PALETA.size]
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }

        item {
            Tarjeta("Por estado") {
                Estado.entries.forEach { est ->
                    val cuantos = fichas.count { it.analisis.estado == est }
                    if (cuantos == 0) return@forEach
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Leyenda(
                            when (est) {
                                Estado.AL_DIA -> "Al día"
                                Estado.POR_VENCER -> "Por vencer"
                                Estado.ATRASADO -> "Atrasado"
                                Estado.PAGADO -> "Pagado"
                            },
                            colorDe(est)
                        )
                        Text("$cuantos", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/** Seccion, no tarjeta: un titulo pequeño y una linea que la separa de la anterior. */
@Composable
private fun Tarjeta(titulo: String, contenido: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(16.dp))
        Text(
            titulo.uppercase(),
            fontSize = 9.sp, letterSpacing = 1.4.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        contenido()
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun Leyenda(texto: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(
            texto, fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

/** Dos barras horizontales por deudor: se leen mejor que verticales en móvil. */
@Composable
private fun BarraDoble(nombre: String, pendiente: Double, cobrado: Double, maximo: Double) {
    val tope = if (maximo > 0) maximo else 1.0
    val c = coloresEstado()
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(nombre, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(dinero(pendiente), fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Barra((pendiente / tope).toFloat(), MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(3.dp))
        Barra((cobrado / tope).toFloat(), c.alDia)
    }
}

@Composable
private fun Barra(fraccion: Float, color: Color) {
    Box(
        Modifier.fillMaxWidth().height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier.fillMaxWidth(fraccion.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}

@Composable
private fun Dona(valores: List<Float>, modifier: Modifier = Modifier) {
    val total = valores.sum().takeIf { it > 0f } ?: 1f
    Canvas(modifier) {
        val grosor = size.minDimension * 0.22f
        var inicio = -90f
        valores.forEachIndexed { i, v ->
            val barrido = 360f * (v / total)
            drawArc(
                color = PALETA[i % PALETA.size],
                startAngle = inicio,
                sweepAngle = barrido,
                useCenter = false,
                topLeft = Offset(grosor / 2, grosor / 2),
                size = Size(size.width - grosor, size.height - grosor),
                style = Stroke(width = grosor)
            )
            inicio += barrido
        }
    }
}
