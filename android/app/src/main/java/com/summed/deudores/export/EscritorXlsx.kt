package com.summed.deudores.export

import java.io.OutputStream
import java.util.Calendar
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Escritor minimo de .xlsx.
 *
 * Un xlsx es un zip con XML dentro, asi que se genera a mano en vez de arrastrar
 * Apache POI, que pesaria mas que el resto de la app junta. Cubre lo que hace
 * falta aqui: varias hojas, texto, numeros, fechas, moneda, porcentaje, ancho de
 * columna, cabecera de color y panel congelado.
 */
class EscritorXlsx {

    /** Estilos disponibles, en el mismo orden que los cellXfs de styles.xml. */
    enum class Estilo(val id: Int) {
        NORMAL(0), TITULO(1), CABECERA(2), MONEDA(3),
        FECHA(4), NEGRITA(5), MONEDA_NEGRITA(6), PORCENTAJE(7), TEXTO(8)
    }

    sealed class Celda {
        abstract val estilo: Estilo
        data class Texto(val valor: String, override val estilo: Estilo = Estilo.NORMAL) : Celda()
        data class Numero(val valor: Double, override val estilo: Estilo = Estilo.NORMAL) : Celda()
        data class Fecha(val ms: Long, override val estilo: Estilo = Estilo.FECHA) : Celda()
        data object Vacia : Celda() { override val estilo = Estilo.NORMAL }
    }

    // No puede ser private: la expone el constructor publico de Constructor.
    class Hoja internal constructor(
        internal val nombre: String,
        internal val anchos: List<Int>,
        internal val congelarPrimeraFila: Boolean
    ) {
        internal val filas = mutableListOf<List<Celda>>()
    }

    private val hojas = mutableListOf<Hoja>()

    fun hoja(nombre: String, anchos: List<Int>, congelarPrimeraFila: Boolean = false): Constructor {
        val h = Hoja(nombre, anchos, congelarPrimeraFila)
        hojas.add(h)
        return Constructor(h)
    }

    inner class Constructor(private val hoja: Hoja) {
        fun fila(vararg celdas: Celda) { hoja.filas.add(celdas.toList()) }
        fun fila(celdas: List<Celda>) { hoja.filas.add(celdas) }
        fun vacia() { hoja.filas.add(emptyList()) }
    }

    fun escribir(salida: OutputStream) {
        ZipOutputStream(salida).use { zip ->
            zip.entrada("[Content_Types].xml", contentTypes())
            zip.entrada("_rels/.rels", relsRaiz())
            zip.entrada("xl/workbook.xml", workbook())
            zip.entrada("xl/_rels/workbook.xml.rels", relsWorkbook())
            zip.entrada("xl/styles.xml", styles())
            hojas.forEachIndexed { i, h ->
                zip.entrada("xl/worksheets/sheet${i + 1}.xml", hojaXml(h))
            }
        }
    }

    private fun ZipOutputStream.entrada(nombre: String, contenido: String) {
        putNextEntry(ZipEntry(nombre))
        write(contenido.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    // ---------- piezas del archivo ----------

    private fun contentTypes(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        hojas.indices.forEach {
            append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun relsRaiz(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            "</Relationships>"

    private fun workbook(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """)
        append("""xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        hojas.forEachIndexed { i, h ->
            append("""<sheet name="${esc(h.nombre)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun relsWorkbook(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        hojas.indices.forEach {
            append("""<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""")
        }
        append("""<Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun styles(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<numFmts count="1"><numFmt numFmtId="164" formatCode="&quot;$&quot;#,##0.00"/></numFmts>""" +
            """<fonts count="4">""" +
            """<font><sz val="11"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="16"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="11"/><name val="Calibri"/></font>""" +
            """</fonts>""" +
            """<fills count="3">""" +
            """<fill><patternFill patternType="none"/></fill>""" +
            """<fill><patternFill patternType="gray125"/></fill>""" +
            """<fill><patternFill patternType="solid"><fgColor rgb="FF4F46E5"/><bgColor indexed="64"/></patternFill></fill>""" +
            """</fills>""" +
            """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="9">""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
            """<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment vertical="center"/></xf>""" +
            """<xf numFmtId="0" fontId="2" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>""" +
            """<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
            """<xf numFmtId="14" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
            """<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
            """<xf numFmtId="164" fontId="3" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>""" +
            """<xf numFmtId="10" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
            """<xf numFmtId="49" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
            """</cellXfs></styleSheet>"""

    private fun hojaXml(h: Hoja): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetViews><sheetView workbookViewId="0" showGridLines="0">""")
        if (h.congelarPrimeraFila) {
            append("""<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>""")
        }
        append("""</sheetView></sheetViews>""")

        if (h.anchos.isNotEmpty()) {
            append("<cols>")
            h.anchos.forEachIndexed { i, w ->
                append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""")
            }
            append("</cols>")
        }

        append("<sheetData>")
        h.filas.forEachIndexed { indiceFila, fila ->
            val nf = indiceFila + 1
            append("""<row r="$nf">""")
            fila.forEachIndexed { indiceCol, celda ->
                val ref = "${letraColumna(indiceCol)}$nf"
                val s = celda.estilo.id
                when (celda) {
                    is Celda.Texto ->
                        if (celda.valor.isNotEmpty())
                            append("""<c r="$ref" s="$s" t="inlineStr"><is><t xml:space="preserve">${esc(celda.valor)}</t></is></c>""")
                    is Celda.Numero ->
                        append("""<c r="$ref" s="$s"><v>${celda.valor}</v></c>""")
                    is Celda.Fecha ->
                        append("""<c r="$ref" s="$s"><v>${aSerialExcel(celda.ms)}</v></c>""")
                    Celda.Vacia -> Unit
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun letraColumna(indice: Int): String {
        var n = indice
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.toString()
    }

    /** Excel cuenta los dias desde el 30/12/1899. */
    private fun aSerialExcel(ms: Long): Double {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        val base = Calendar.getInstance().apply {
            clear(); set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
        }
        val dias = (c.timeInMillis - base.timeInMillis).toDouble() / 86_400_000.0
        // Se recorta a la fecha: la hora va en columna aparte.
        return kotlin.math.floor(dias)
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
}
