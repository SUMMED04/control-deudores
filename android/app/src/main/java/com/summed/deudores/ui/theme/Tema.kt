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

/**
 * Paleta "Papel": blanco, tinta y lineas de un pixel. El color no decora, solo
 * informa: verde lo cobrado, rojo lo vencido, ambar lo que se presta. Por eso
 * el acento principal es tinta y no un color de marca.
 */
val Tinta = Color(0xFF14181F)
val Grafito = Color(0xFF939AA6)
val Linea = Color(0xFFF0F1F4)
val Papel = Color(0xFFFFFFFF)
val PapelGris = Color(0xFFF4F5F7)

val Verde = Color(0xFF0F8A5F)
val VerdeClaro = Color(0xFF34D399)
val Ambar = Color(0xFFC98A2B)
val AmbarClaro = Color(0xFFFBBF24)
val Rojo = Color(0xFFC0392B)
val RojoClaro = Color(0xFFF87171)

private val Claro = lightColorScheme(
    primary = Tinta,
    onPrimary = Papel,
    primaryContainer = PapelGris,
    onPrimaryContainer = Tinta,
    secondary = Verde,
    onSecondary = Papel,
    surface = Papel,
    onSurface = Tinta,
    surfaceVariant = PapelGris,
    onSurfaceVariant = Grafito,
    background = Papel,
    onBackground = Tinta,
    outline = Grafito,
    outlineVariant = Linea,
    error = Rojo
)

// En oscuro se invierte la misma idea: fondo casi negro, tinta clara y las
// mismas lineas finas, sin degradados ni superficies flotantes.
private val Oscuro = darkColorScheme(
    primary = Color(0xFFE9ECF2),
    onPrimary = Color(0xFF14181F),
    primaryContainer = Color(0xFF1E232B),
    onPrimaryContainer = Color(0xFFE9ECF2),
    secondary = VerdeClaro,
    onSecondary = Color(0xFF06281C),
    surface = Color(0xFF0F1218),
    onSurface = Color(0xFFE9ECF2),
    surfaceVariant = Color(0xFF1B212A),
    onSurfaceVariant = Color(0xFF8B93A2),
    background = Color(0xFF0B0D10),
    onBackground = Color(0xFFE9ECF2),
    outline = Color(0xFF8B93A2),
    outlineVariant = Color(0xFF1E232B),
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
    if (oscuro) ColoresEstado(VerdeClaro, AmbarClaro, RojoClaro, Color(0xFFE9ECF2))
    else ColoresEstado(Verde, Ambar, Rojo, Tinta)

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
            // La barra de estado se funde con la pantalla: ya no hay franja de
            // color arriba, asi que sus iconos van oscuros sobre el blanco.
            ventana.statusBarColor = esquema.background.toArgb()
            WindowCompat.getInsetsController(ventana, vista).isAppearanceLightStatusBars = !oscuro
        }
    }
    MaterialTheme(colorScheme = esquema, content = contenido)
}
