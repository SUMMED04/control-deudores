package com.summed.deudores.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.summed.deudores.data.Estado
import com.summed.deudores.data.etiqueta
import com.summed.deudores.ui.theme.coloresEstado

@Composable
fun DeudoresScreen(
    vm: DeudoresViewModel,
    padding: PaddingValues,
    onCobrar: (Ficha) -> Unit,
    onAumentar: (Ficha) -> Unit,
    onEditar: (Ficha) -> Unit,
    onBorrar: (Ficha) -> Unit,
    onAbrir: (Ficha) -> Unit
) {
    val fichas by vm.visibles.collectAsState()
    val todas by vm.fichas.collectAsState()
    val totales by vm.totales.collectAsState()
    val busqueda by vm.busqueda.collectAsState()
    val filtro by vm.filtro.collectAsState()
    val c = coloresEstado()

    val atrasados = todas.filter { it.analisis.estado == Estado.ATRASADO }
        .sortedByDescending { it.analisis.diasMora }
    val porVencer = todas.filter { it.analisis.estado == Estado.POR_VENCER }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp, end = 14.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 90.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (todas.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TarjetaTotal("Por cobrar", dinero(totales.porCobrar), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    TarjetaTotal("Atrasado", dinero(totales.atrasado), c.atrasado, Modifier.weight(1f))
                }
            }

            // El aviso solo existe si hay alguien atrasado o a punto de vencer.
            // Al registrar el cobro desaparece solo, porque el estado se
            // recalcula desde los movimientos.
            if (atrasados.isNotEmpty()) {
                item {
                    val f = atrasados.first()
                    val texto = if (atrasados.size == 1)
                        "${f.deudor.nombre} lleva ${dias(f.analisis.diasMora)} de atraso"
                    else "${atrasados.size} deudores se pasaron de su fecha de pago"
                    AvisoAtraso(texto, atrasado = true) { onCobrar(f) }
                }
            } else if (porVencer.isNotEmpty()) {
                item {
                    val f = porVencer.minByOrNull { it.analisis.diasRestantes }!!
                    val texto = if (f.analisis.diasRestantes == 0)
                        "${f.deudor.nombre} te paga hoy"
                    else "${f.deudor.nombre} vence en ${dias(f.analisis.diasRestantes)}"
                    AvisoAtraso(texto, atrasado = false) { onCobrar(f) }
                }
            }

            item {
                OutlinedTextField(
                    value = busqueda,
                    onValueChange = { vm.busqueda.value = it },
                    placeholder = { Text("Buscar por nombre o teléfono") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filtro == null,
                        onClick = { vm.filtro.value = null },
                        label = { Text("Todos") }
                    )
                    Estado.entries.forEach { est ->
                        FilterChip(
                            selected = filtro == est,
                            onClick = { vm.filtro.value = if (filtro == est) null else est },
                            label = { Text(est.etiqueta) }
                        )
                    }
                }
            }
        }

        if (todas.isEmpty()) {
            item { Vacio("Todavía no tienes deudores", "Toca el botón de abajo para agregar el primero.") }
        } else if (fichas.isEmpty()) {
            item { Vacio("Sin resultados", "Ningún deudor coincide con la búsqueda o el filtro.") }
        } else {
            items(fichas, key = { it.deudor.id }) { f ->
                TarjetaDeudor(
                    ficha = f,
                    onCobrar = { onCobrar(f) },
                    onAumentar = { onAumentar(f) },
                    onEditar = { onEditar(f) },
                    onBorrar = { onBorrar(f) },
                    onAbrir = { onAbrir(f) }
                )
            }
        }
    }
}

@Composable
private fun Vacio(titulo: String, detalle: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(titulo, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                detalle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
