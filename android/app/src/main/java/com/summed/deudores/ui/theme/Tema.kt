package com.summed.deudores.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val Indigo = Color(0xFF4F46E5)
val IndigoClaro = Color(0xFFA5B4FC)
val Verde = Color(0xFF059669)
val VerdeClaro = Color(0xFF34D399)
val Ambar = Color(0xFFD97706)
val AmbarClaro = Color(0xFFFBBF24)
val Rojo = Color(0xFFDC2626)
val RojoClaro = Color(0xFFF87171)

private val Claro = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF312E81),
    secondary = Verde,
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF4F4F8),
    background = Color(0xFFF6F6FA),
    error = Rojo
)

private val Oscuro = darkColorScheme(
    primary = IndigoClaro,
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = VerdeClaro,
    surface = Color(0xFF2A2A3E),
    surfaceVariant = Color(0xFF33334C),
    background = Color(0xFF1E1E2F),
    error = RojoClaro
)

/** Colores de estado, que cambian de tono segun el modo. */
data class ColoresEstado(
    val alDia: Color,
    val porVencer: Color,
    val atrasado: Color,
    val pagado: Color
)

@Composable
fun coloresEstado(oscuro: Boolean = isSystemInDarkTheme()): ColoresEstado =
    if (oscuro) ColoresEstado(VerdeClaro, AmbarClaro, RojoClaro, IndigoClaro)
    else ColoresEstado(Verde, Ambar, Rojo, Indigo)

@Composable
fun TemaDeudores(
    oscuro: Boolean = isSystemInDarkTheme(),
    contenido: @Composable () -> Unit
) {
    val esquema = if (oscuro) Oscuro else Claro
    val vista = LocalView.current
    if (!vista.isInEditMode) {
        SideEffect {
            val ventana = (vista.context as Activity).window
            ventana.statusBarColor = esquema.primary.toArgb()
            WindowCompat.getInsetsController(ventana, vista).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = esquema, content = contenido)
}
