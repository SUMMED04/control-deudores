package com.summed.deudores

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.summed.deudores.data.Deudor
import com.summed.deudores.data.Preferencias
import com.summed.deudores.data.Tema
import com.summed.deudores.notif.Recordatorios
import com.summed.deudores.ui.DeudoresScreen
import com.summed.deudores.ui.DeudoresViewModel
import com.summed.deudores.ui.DetalleScreen
import com.summed.deudores.ui.DialogoAumento
import com.summed.deudores.ui.DialogoCobro
import com.summed.deudores.ui.DialogoConfirmar
import com.summed.deudores.ui.DialogoDeudor
import com.summed.deudores.ui.Ficha
import com.summed.deudores.ui.ReportesScreen
import com.summed.deudores.ui.AjustesScreen
import com.summed.deudores.ui.dinero
import com.summed.deudores.ui.theme.TemaDeudores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Marca del build, igual que en las otras apps: sirve para saber de un vistazo
 * qué APK está instalado sin adivinar si un arreglo ya entró o no.
 */
const val BUILD_TAG = "2026.08.01-1"

private enum class Seccion(val titulo: String, val icono: ImageVector) {
    DEUDORES("Deudores", Icons.Default.Group),
    REPORTES("Reportes", Icons.Default.PieChart),
    AJUSTES("Ajustes", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val vm: DeudoresViewModel by viewModels()

    private val pedirNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Recordatorios.crearCanal(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val prefs = remember { Preferencias.obtener(this) }
            val tema by prefs.tema.collectAsState()
            val oscuro = when (tema) {
                Tema.CLARO -> false
                Tema.OSCURO -> true
                Tema.SISTEMA -> isSystemInDarkTheme()
            }
            TemaDeudores(oscuro = oscuro) { Pantalla(vm, prefs) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Pantalla(vm: DeudoresViewModel, prefs: Preferencias) {
    var seccion by remember { mutableStateOf(Seccion.DEUDORES) }
    var detalle by remember { mutableStateOf<Long?>(null) }

    var cobrando by remember { mutableStateOf<Ficha?>(null) }
    var aumentando by remember { mutableStateOf<Ficha?>(null) }
    var editando by remember { mutableStateOf<Deudor?>(null) }
    var creando by remember { mutableStateOf(false) }
    var borrando by remember { mutableStateOf<Ficha?>(null) }

    val fichas by vm.fichas.collectAsState()
    val abierta = detalle?.let { id -> fichas.firstOrNull { it.deudor.id == id } }
    val snackbar = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()

    val logo by prefs.logo.collectAsState()
    val fondo by prefs.fondo.collectAsState()
    val opacidad by prefs.opacidadFondo.collectAsState()

    // La imagen de fondo vive solo aqui, detras de la interfaz. Los reportes se
    // dibujan aparte, asi que no puede colarse en el PDF ni en el Excel.
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        fondo?.let { archivo ->
            val bmp = remember(archivo.path, archivo.lastModified()) {
                runCatching { BitmapFactory.decodeFile(archivo.absolutePath) }.getOrNull()
            }
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = opacidad,
                    modifier = Modifier.matchParentSize()
                )
            }
        }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(abierta?.deudor?.nombre ?: seccion.titulo) },
                navigationIcon = {
                    if (abierta != null) {
                        IconButton(onClick = { detalle = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
                // Sin franja de color: el titulo se apoya en el mismo blanco
                // que el resto de la pantalla, como en el resto del rediseño.
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            if (abierta == null) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp
                ) {
                    Seccion.entries.forEach { s ->
                        NavigationBarItem(
                            selected = seccion == s,
                            onClick = { seccion = s },
                            icon = { Icon(s.icono, contentDescription = null) },
                            label = { Text(s.titulo) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (abierta == null && seccion == Seccion.DEUDORES) {
                FloatingActionButton(onClick = { creando = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo deudor")
                }
            }
        }
    ) { padding ->
        when {
            abierta != null -> DetalleScreen(abierta, padding) { vm.borrarMovimiento(it) }
            seccion == Seccion.DEUDORES -> DeudoresScreen(
                vm = vm,
                padding = padding,
                logo = logo,
                onCobrar = { cobrando = it },
                onAumentar = { aumentando = it },
                onEditar = { editando = it.deudor },
                onBorrar = { borrando = it },
                onAbrir = { detalle = it.deudor.id }
            )
            seccion == Seccion.REPORTES -> ReportesScreen(vm, padding)
            else -> AjustesScreen(vm, padding) { texto ->
                alcance.launch { snackbar.showMessage(texto) }
            }
        }
    }
    }

    cobrando?.let { f ->
        DialogoCobro(
            ficha = f,
            onCerrar = { cobrando = null },
            onConfirmar = { monto, nota, fecha ->
                vm.registrarPago(f.deudor.id, monto, nota, fecha)
                val queda = f.analisis.saldo - monto
                alcance.launch(Dispatchers.Main) {
                    snackbar.showMessage(
                        if (queda <= 0.001) "${f.deudor.nombre} terminó de pagar"
                        else "Pago registrado. Quedan ${dinero(queda)}"
                    )
                }
            }
        )
    }

    aumentando?.let { f ->
        DialogoAumento(
            ficha = f,
            onCerrar = { aumentando = null },
            onConfirmar = { monto, nota, fecha -> vm.registrarCargo(f.deudor.id, monto, nota, fecha) }
        )
    }

    if (creando || editando != null) {
        DialogoDeudor(
            existente = editando,
            onCerrar = { creando = false; editando = null },
            onGuardarNuevo = { nombre, monto, cuota, dia, tel, notas, fecha ->
                vm.crearDeudor(nombre, monto, cuota, dia, tel, notas, fecha)
            },
            onGuardarEdicion = { vm.editarDeudor(it) }
        )
    }

    borrando?.let { f ->
        DialogoConfirmar(
            titulo = "Eliminar a ${f.deudor.nombre}",
            mensaje = "Se borrarán sus ${f.movimientos.size} movimientos y un saldo " +
                "pendiente de ${dinero(maxOf(0.0, f.analisis.saldo))}.",
            textoConfirmar = "Sí, eliminar",
            onCerrar = { borrando = null },
            onConfirmar = { vm.borrarDeudor(f.deudor) }
        )
    }
}

private suspend fun SnackbarHostState.showMessage(texto: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(texto)
}
