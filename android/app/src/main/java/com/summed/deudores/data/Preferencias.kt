package com.summed.deudores.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class Tema { SISTEMA, CLARO, OSCURO }

/**
 * Ajustes de apariencia. Las imagenes no se guardan como Uri del selector:
 * esos permisos se pierden al reiniciar. Se copian a filesDir y se guarda solo
 * el nombre del archivo.
 */
class Preferencias private constructor(private val context: Context) {

    private val sp = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    private val _tema = MutableStateFlow(
        runCatching { Tema.valueOf(sp.getString("tema", null) ?: "SISTEMA") }.getOrDefault(Tema.SISTEMA)
    )
    val tema: StateFlow<Tema> = _tema.asStateFlow()

    private val _logo = MutableStateFlow(archivoSiExiste(sp.getString("logo", null)))
    val logo: StateFlow<File?> = _logo.asStateFlow()

    private val _fondo = MutableStateFlow(archivoSiExiste(sp.getString("fondo", null)))
    val fondo: StateFlow<File?> = _fondo.asStateFlow()

    private val _opacidadFondo = MutableStateFlow(sp.getFloat("opacidadFondo", 0.18f))
    val opacidadFondo: StateFlow<Float> = _opacidadFondo.asStateFlow()

    // ---------- aviso ----------

    private val _horaAviso = MutableStateFlow(sp.getInt("horaAviso", 9))
    val horaAviso: StateFlow<Int> = _horaAviso.asStateFlow()

    private val _minutoAviso = MutableStateFlow(sp.getInt("minutoAviso", 0))
    val minutoAviso: StateFlow<Int> = _minutoAviso.asStateFlow()

    /** Uri del tono elegido. Null significa el tono de alarma del sistema. */
    private val _tono = MutableStateFlow(sp.getString("tono", null))
    val tono: StateFlow<String?> = _tono.asStateFlow()

    /** Cuánto suena el aviso, en segundos. */
    private val _segundosSonido = MutableStateFlow(sp.getInt("segundosSonido", 10))
    val segundosSonido: StateFlow<Int> = _segundosSonido.asStateFlow()

    fun cambiarTema(t: Tema) {
        sp.edit().putString("tema", t.name).apply()
        _tema.value = t
    }

    fun cambiarOpacidadFondo(v: Float) {
        sp.edit().putFloat("opacidadFondo", v).apply()
        _opacidadFondo.value = v
    }

    fun cambiarHoraAviso(hora: Int, minuto: Int) {
        sp.edit().putInt("horaAviso", hora).putInt("minutoAviso", minuto).apply()
        _horaAviso.value = hora
        _minutoAviso.value = minuto
    }

    fun cambiarTono(uri: String?) {
        sp.edit().apply { if (uri == null) remove("tono") else putString("tono", uri) }.apply()
        _tono.value = uri
    }

    fun cambiarSegundosSonido(s: Int) {
        sp.edit().putInt("segundosSonido", s).apply()
        _segundosSonido.value = s
    }

    /** Copia la imagen elegida a filesDir y la deja fijada. */
    fun guardarImagen(origen: Uri, cual: Cual): Boolean = runCatching {
        val destino = File(context.filesDir, cual.archivo)
        context.contentResolver.openInputStream(origen)?.use { entrada ->
            destino.outputStream().use { salida -> entrada.copyTo(salida) }
        } ?: return false
        sp.edit().putString(cual.clave, cual.archivo).apply()
        // Se reasigna un File nuevo para que Compose detecte el cambio aunque
        // la ruta sea la misma que antes.
        val recien = File(context.filesDir, cual.archivo)
        when (cual) {
            Cual.LOGO -> _logo.value = recien
            Cual.FONDO -> _fondo.value = recien
        }
        true
    }.getOrDefault(false)

    fun quitarImagen(cual: Cual) {
        File(context.filesDir, cual.archivo).delete()
        sp.edit().remove(cual.clave).apply()
        when (cual) {
            Cual.LOGO -> _logo.value = null
            Cual.FONDO -> _fondo.value = null
        }
    }

    private fun archivoSiExiste(nombre: String?): File? =
        nombre?.let { File(context.filesDir, it).takeIf { f -> f.exists() } }

    enum class Cual(val clave: String, val archivo: String) {
        LOGO("logo", "logo.img"),
        FONDO("fondo", "fondo.img")
    }

    companion object {
        @Volatile private var instancia: Preferencias? = null
        fun obtener(context: Context): Preferencias =
            instancia ?: synchronized(this) {
                instancia ?: Preferencias(context.applicationContext).also { instancia = it }
            }
    }
}
