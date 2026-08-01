package com.summed.deudores.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.summed.deudores.data.Deudor
import com.summed.deudores.ui.theme.coloresEstado
import java.util.Calendar
import java.util.TimeZone

private fun aMonto(texto: String): Double? =
    texto.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0 }

/**
 * Fecha del movimiento. Arranca siempre en el momento actual, que es lo que se
 * quiere casi siempre, y solo cambia si el usuario la toca a mano. Sirve para
 * anotar prestamos y pagos viejos: se presto en enero y se cobro en febrero
 * aunque hoy sea agosto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorFecha(fecha: Long, etiqueta: String, onFecha: (Long) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    val esHoy = mismoDia(fecha, System.currentTimeMillis())

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    etiqueta,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    fechaLargaDe(fecha) + if (esHoy) " · hoy" else "",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            TextButton(onClick = { abierto = true }) { Text("Cambiar") }
        }
    }

    if (abierto) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = aUtc(fecha),
            // Registrar un movimiento en el futuro no significa nada, asi que
            // el calendario solo deja elegir hasta hoy.
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis <= aUtc(System.currentTimeMillis())

                override fun isSelectableYear(year: Int) =
                    year <= Calendar.getInstance().get(Calendar.YEAR)
            }
        )
        DatePickerDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { onFecha(deUtc(it)) }
                    abierto = false
                }) { Text("Usar esta fecha") }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } }
        ) {
            DatePicker(state = estado, showModeToggle = false)
        }
    }
}

/** Medianoche UTC del dia local, que es lo que espera el calendario de Material. */
private fun aUtc(ms: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = ms }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/**
 * Camino inverso: el dia elegido se guarda con la hora actual, para que dos
 * movimientos del mismo dia queden en el orden en que se registraron.
 */
private fun deUtc(utcMs: Long): Long {
    val elegido = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMs }
    val ahora = Calendar.getInstance()
    return Calendar.getInstance().apply {
        set(
            elegido.get(Calendar.YEAR), elegido.get(Calendar.MONTH), elegido.get(Calendar.DAY_OF_MONTH),
            ahora.get(Calendar.HOUR_OF_DAY), ahora.get(Calendar.MINUTE), ahora.get(Calendar.SECOND)
        )
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun mismoDia(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

/** Cobro. La fecha arranca en hoy y se puede mover a un dia anterior. */
@Composable
fun DialogoCobro(
    ficha: Ficha,
    onCerrar: () -> Unit,
    onConfirmar: (Double, String, Long) -> Unit
) {
    val saldo = ficha.analisis.saldo
    val sugerido = if (ficha.deudor.pagoMensual > 0) minOf(ficha.deudor.pagoMensual, saldo) else saldo
    var monto by remember { mutableStateOf(String.format("%.2f", sugerido)) }
    var nota by remember { mutableStateOf("") }
    var fecha by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var error by remember { mutableStateOf<String?>(null) }

    val valor = aMonto(monto)
    val restante = if (valor != null) saldo - valor else saldo

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Cobrar a ${ficha.deudor.nombre}") },
        text = {
            // Con el selector de fecha el contenido puede pasarse de alto en
            // pantallas cortas, asi que el dialogo se desplaza.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Saldo actual ${dinero(saldo)}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = monto,
                    onValueChange = { monto = it; error = null },
                    label = { Text("Monto") },
                    prefix = { Text("$") },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (ficha.deudor.pagoMensual > 0) {
                        AssistChip(
                            onClick = { monto = String.format("%.2f", ficha.deudor.pagoMensual) },
                            label = { Text(dinero(ficha.deudor.pagoMensual)) }
                        )
                    }
                    AssistChip(
                        onClick = { monto = String.format("%.2f", saldo) },
                        label = { Text("Todo") }
                    )
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = nota,
                    onValueChange = { nota = it },
                    label = { Text("Nota (opcional)") },
                    placeholder = { Text("Efectivo, transferencia...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                SelectorFecha(fecha, "Fecha del pago") { fecha = it }

                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Saldo después del pago: ${dinero(maxOf(0.0, restante))}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val v = aMonto(monto)
                when {
                    v == null -> error = "Ingresa un monto válido"
                    v > saldo + 0.001 -> error = "El monto excede la deuda pendiente"
                    else -> { onConfirmar(v, nota, fecha); onCerrar() }
                }
            }) { Text("Registrar pago") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } }
    )
}

/** Aumento de deuda: un prestamo nuevo, que queda en el historial. */
@Composable
fun DialogoAumento(
    ficha: Ficha,
    onCerrar: () -> Unit,
    onConfirmar: (Double, String, Long) -> Unit
) {
    var monto by remember { mutableStateOf("") }
    var nota by remember { mutableStateOf("") }
    var fecha by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var error by remember { mutableStateOf<String?>(null) }
    val valor = aMonto(monto)

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Aumentar deuda de ${ficha.deudor.nombre}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = monto,
                    onValueChange = { monto = it; error = null },
                    label = { Text("Monto adicional prestado") },
                    prefix = { Text("$") },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = nota,
                    onValueChange = { nota = it },
                    label = { Text("Motivo (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                SelectorFecha(fecha, "Fecha del préstamo") { fecha = it }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Saldo actual ${dinero(ficha.analisis.saldo)}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    "Saldo nuevo ${dinero(ficha.analisis.saldo + (valor ?: 0.0))}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = coloresEstado().porVencer
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val v = aMonto(monto)
                if (v == null) error = "Ingresa un monto válido"
                else { onConfirmar(v, nota, fecha); onCerrar() }
            }) { Text("Confirmar aumento") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } }
    )
}

/**
 * Alta y edicion comparten formulario. Al editar no se toca el saldo: eso se
 * cambia cobrando o aumentando, para que quede rastro en el historial.
 */
@Composable
fun DialogoDeudor(
    existente: Deudor?,
    onCerrar: () -> Unit,
    onGuardarNuevo: (nombre: String, monto: Double, cuota: Double, dia: Int, tel: String, notas: String, fecha: Long) -> Unit,
    onGuardarEdicion: (Deudor) -> Unit
) {
    var nombre by remember { mutableStateOf(existente?.nombre ?: "") }
    var monto by remember { mutableStateOf("") }
    var fecha by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var cuota by remember { mutableStateOf(existente?.pagoMensual?.takeIf { it > 0 }?.let { String.format("%.2f", it) } ?: "") }
    var dia by remember { mutableStateOf((existente?.diaPago ?: 15).toString()) }
    var telefono by remember { mutableStateOf(existente?.telefono ?: "") }
    var notas by remember { mutableStateOf(existente?.notas ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(if (existente == null) "Nuevo deudor" else "Editar deudor") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = nombre, onValueChange = { nombre = it; error = null },
                    label = { Text("Nombre") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (existente == null) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = monto, onValueChange = { monto = it; error = null },
                        label = { Text("Monto que le prestaste") }, prefix = { Text("$") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    SelectorFecha(fecha, "Fecha del préstamo") { fecha = it }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = cuota, onValueChange = { cuota = it },
                    label = { Text("Cuota mensual sugerida") }, prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = dia, onValueChange = { dia = it.filter { c -> c.isDigit() }.take(2); error = null },
                    label = { Text("Día de pago de cada mes") },
                    supportingText = { Text("Si el mes no tiene ese día, se usa el último") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = telefono, onValueChange = { telefono = it },
                    label = { Text("Teléfono (opcional)") },
                    supportingText = { Text("Habilita el botón de WhatsApp") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = notas, onValueChange = { notas = it },
                    label = { Text("Notas (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val d = dia.toIntOrNull()
                val m = aMonto(monto)
                when {
                    nombre.isBlank() -> error = "Escribe el nombre"
                    d == null || d !in 1..31 -> error = "El día de pago debe estar entre 1 y 31"
                    existente == null && m == null -> error = "Ingresa el monto prestado"
                    else -> {
                        val c = aMonto(cuota) ?: 0.0
                        if (existente == null) onGuardarNuevo(nombre, m!!, c, d, telefono, notas, fecha)
                        else onGuardarEdicion(
                            existente.copy(
                                nombre = nombre.trim(), telefono = telefono.trim(),
                                notas = notas.trim(), diaPago = d, pagoMensual = c
                            )
                        )
                        onCerrar()
                    }
                }
            }) { Text(if (existente == null) "Crear deudor" else "Guardar cambios") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } }
    )
}

@Composable
fun DialogoConfirmar(
    titulo: String,
    mensaje: String,
    textoConfirmar: String,
    onCerrar: () -> Unit,
    onConfirmar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(titulo) },
        text = { Text(mensaje) },
        confirmButton = {
            Button(
                onClick = { onConfirmar(); onCerrar() },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) { Text(textoConfirmar) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } }
    )
}
