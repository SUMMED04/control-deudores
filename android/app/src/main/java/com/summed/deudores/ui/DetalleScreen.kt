package com.summed.deudores.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.summed.deudores.data.Estado
import com.summed.deudores.data.Movimiento
import com.summed.deudores.data.TipoMovimiento
import com.summed.deudores.data.serieDeSaldo
import com.summed.deudores.data.telefonoWhatsApp
import com.summed.deudores.data.ultimoPago
import com.summed.deudores.ui.theme.coloresEstado

@Composable
fun DetalleScreen(
    ficha: Ficha,
    padding: PaddingValues,
    onBorrarMovimiento: (Movimiento) -> Unit
) {
    val contexto = LocalContext.current
    val c = coloresEstado()
    val a = ficha.analisis
    val serie = ficha.movimientos.serieDeSaldo().reversed()

    val cargos = ficha.movimientos.filter { it.tipo == TipoMovimiento.CARGO }
    val pagos = ficha.movimientos.filter { it.tipo == TipoMovimiento.PAGO }
    val inicial = cargos.minByOrNull { it.fecha }
    val extras = cargos.size - if (inicial != null) 1 else 0
    val sumaExtras = cargos.filter { it.id != inicial?.id }.sumOf { it.monto }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp, end = 14.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "SALDO PENDIENTE",
                        fontSize = 10.sp, letterSpacing = 0.7.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        dinero(maxOf(0.0, a.saldo)),
                        fontSize = 32.sp, fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (a.estado) {
                            Estado.PAGADO -> "Deuda saldada por completo"
                            Estado.ATRASADO -> "Se pasó ${dias(a.diasMora)} de su fecha de pago"
                            Estado.POR_VENCER ->
                                if (a.diasRestantes == 0) "Vence hoy"
                                else "Vence en ${dias(a.diasRestantes)}"
                            Estado.AL_DIA -> "Próximo pago el ${fechaCortaDe(a.proximoVencimiento)}"
                        },
                        fontSize = 13.sp, color = colorDe(a.estado), fontWeight = FontWeight.Medium
                    )

                    if (ficha.deudor.telefono.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                val texto = "Hola ${ficha.deudor.nombre}, te recuerdo tu saldo pendiente de " +
                                    "${dinero(maxOf(0.0, a.saldo))}."
                                val url = "https://wa.me/${telefonoWhatsApp(ficha.deudor.telefono)}" +
                                    "?text=${Uri.encode(texto)}"
                                contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Escribirle por WhatsApp") }
                    }

                    if (ficha.deudor.notas.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            ficha.deudor.notas,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "DESGLOSE DE LA DEUDA",
                        fontSize = 10.sp, letterSpacing = 0.7.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    FilaDesglose("Préstamo inicial", inicial?.let { dinero(it.monto) } ?: "$0.00")
                    inicial?.let { FilaDesglose("Fecha del préstamo", fechaLargaDe(it.fecha)) }
                    FilaDesglose(
                        if (extras > 0) "Préstamos posteriores ($extras)" else "Préstamos posteriores",
                        if (extras > 0) "+ ${dinero(sumaExtras)}" else "ninguno",
                        if (extras > 0) c.porVencer else null
                    )
                    FilaDesglose("Total prestado", dinero(ficha.prestado), destacado = true)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    FilaDesglose(
                        if (pagos.isNotEmpty()) "Pagos recibidos (${pagos.size})" else "Pagos recibidos",
                        if (pagos.isNotEmpty()) "− ${dinero(ficha.pagado)}" else "ninguno",
                        if (pagos.isNotEmpty()) c.alDia else null
                    )
                    FilaDesglose(
                        "Pago promedio",
                        if (pagos.isNotEmpty()) dinero(ficha.pagado / pagos.size) else "$0.00"
                    )
                    FilaDesglose(
                        "Último pago",
                        ficha.movimientos.ultimoPago()?.let { fechaLargaDe(it) } ?: "todavía no paga"
                    )
                    FilaDesglose("Saldo pendiente", dinero(maxOf(0.0, a.saldo)), destacado = true)
                }
            }
        }

        item {
            Text(
                "MOVIMIENTOS (${ficha.movimientos.size})",
                fontSize = 10.sp, letterSpacing = 0.7.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }

        if (serie.isEmpty()) {
            item {
                Text(
                    "Sin movimientos registrados",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        } else {
            items(serie, key = { it.first.id }) { (m, saldoTras) ->
                FilaMovimiento(m, saldoTras) { onBorrarMovimiento(m) }
            }
        }
    }
}

@Composable
private fun FilaDesglose(
    etiqueta: String,
    valor: String,
    color: androidx.compose.ui.graphics.Color? = null,
    destacado: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            etiqueta, fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            valor, fontSize = 13.sp,
            fontWeight = if (destacado || color != null) FontWeight.Medium else FontWeight.Normal,
            color = color ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FilaMovimiento(m: Movimiento, saldoTras: Double, onBorrar: () -> Unit) {
    val c = coloresEstado()
    val esCargo = m.tipo == TipoMovimiento.CARGO
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (esCargo) c.porVencer else c.alDia)
            )
            Spacer(Modifier.height(0.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    if (esCargo) "Préstamo" else "Pago",
                    fontSize = 14.sp, fontWeight = FontWeight.Medium
                )
                Text(
                    "${fechaLargaDe(m.fecha)} · ${horaDe(m.fecha)}" +
                        if (m.nota.isNotBlank()) " · ${m.nota}" else "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (esCargo) "+ " else "− ") + dinero(m.monto),
                    fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = if (esCargo) c.porVencer else c.alDia
                )
                Text(
                    "saldo ${dinero(saldoTras)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onBorrar) {
                Icon(
                    Icons.Default.Close, contentDescription = "Eliminar movimiento",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}
