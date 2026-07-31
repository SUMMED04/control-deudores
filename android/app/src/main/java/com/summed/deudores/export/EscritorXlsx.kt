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
 * falta para que el reporte del movil salga igual que el de la web: varias hojas,
 * celdas combinadas, tarjetas de color, bordes, barras de avance de formato
 * condicional y un grafico de barras NATIVO de Excel.
 *
 * El grafico nativo es la ventaja de escribir el XML a mano: la version web usa
 * ExcelJS, que solo puede pegar el grafico como imagen. Aqui las barras se
 * mueven solas si el usuario edita un numero.
 */
class EscritorXlsx {

    /** Estilos disponibles, en el mismo orden que los cellXfs de styles.xml. */
    enum class Estilo(val id: Int) {
        NORMAL(0), TITULO(1), CABECERA(2), MONEDA(3),
        FECHA(4), NEGRITA(5), MONEDA_NEGRITA(6), PORCENTAJE(7), TEXTO(8),
        SUBTITULO(9), SECCION(10),
        KPI_ETIQUETA_1(11), KPI_ETIQUETA_2(12), KPI_ETIQUETA_3(13), KPI_ETIQUETA_4(14),
        KPI_VALOR_1(15), KPI_VALOR_2(16), KPI_VALOR_3(17), KPI_VALOR_4(18),
        PIE(19), TOTAL_TEXTO(20), TOTAL_MONEDA(21), CENTRADO(22)
    }

    sealed class Celda {
        abstract val estilo: Estilo
        data class Texto(val valor: String, override val estilo: Estilo = Estilo.NORMAL) : Celda()
        data class Numero(val valor: Double, override val estilo: Estilo = Estilo.NORMAL) : Celda()
        data class Fecha(val ms: Long, override val estilo: Estilo = Estilo.FECHA) : Celda()
        data object Vacia : Celda() { override val estilo = Estilo.NORMAL }
    }

    /** Una serie del grafico: su nombre, su color y de que celdas sale. */
    data class Serie(
        val nombre: String,
        val colorRgb: String,
        val valores: List<Double>,
        /** Rango absoluto sin la hoja, por ejemplo "$B$9:$B$12". */
        val rango: String
    )

    /**
     * Grafico de barras agrupadas anclado a un rectangulo de celdas.
     * Las filas y columnas van desde cero.
     */
    data class Grafico(
        val titulo: String,
        val categorias: List<String>,
        val rangoCategorias: String,
        val series: List<Serie>,
        val filaDesde: Int,
        val filaHasta: Int,
        val colDesde: Int = 0,
        val colHasta: Int = 8
    )

    // No puede ser private: la expone el constructor publico de Constructor.
    class Hoja internal constructor(
        internal val nombre: String,
        internal val anchos: List<Int>,
        internal val filasCongeladas: Int
    ) {
        internal val filas = mutableListOf<List<Celda>>()
        internal val altos = mutableMapOf<Int, Double>()
        internal val combinadas = mutableListOf<String>()
        internal val barras = mutableListOf<String>()
        internal val textoNumerico = mutableListOf<String>()
        internal var grafico: Grafico? = null
    }

    private val hojas = mutableListOf<Hoja>()

    fun hoja(nombre: String, anchos: List<Int>, filasCongeladas: Int = 0): Constructor {
        val h = Hoja(nombre, anchos, filasCongeladas)
        hojas.add(h)
        return Constructor(h)
    }

    inner class Constructor(private val hoja: Hoja) {
        fun fila(vararg celdas: Celda) { hoja.filas.add(celdas.toList()) }
        fun fila(celdas: List<Celda>) { hoja.filas.add(celdas) }
        fun vacia() { hoja.filas.add(emptyList()) }

        /** Numero de la ultima fila escrita, contando desde uno como Excel. */
        val ultimaFila: Int get() = hoja.filas.size

        /** Alto en puntos de la ultima fila escrita. */
        fun alto(puntos: Double) { hoja.altos[hoja.filas.size] = puntos }

        fun combinar(rango: String) { hoja.combinadas.add(rango) }

        /** Barra de avance nativa: se mueve sola si se edita el porcentaje. */
        fun barrasDeAvance(rango: String) { hoja.barras.add(rango) }

        /** Quita el triangulito verde de "numero guardado como texto". */
        fun sinAvisoDeTexto(rango: String) { hoja.textoNumerico.add(rango) }

        fun grafico(g: Grafico) { hoja.grafico = g }
    }

    fun escribir(salida: OutputStream) {
        val conGrafico = hojas.indexOfFirst { it.grafico != null }
        ZipOutputStream(salida).use { zip ->
            zip.entrada("[Content_Types].xml", contentTypes(conGrafico))
            zip.entrada("_rels/.rels", relsRaiz())
            zip.entrada("xl/workbook.xml", workbook())
            zip.entrada("xl/_rels/workbook.xml.rels", relsWorkbook())
            zip.entrada("xl/styles.xml", styles())
            hojas.forEachIndexed { i, h ->
                zip.entrada("xl/worksheets/sheet${i + 1}.xml", hojaXml(h))
            }
            if (conGrafico >= 0) {
                val h = hojas[conGrafico]
                zip.entrada(
                    "xl/worksheets/_rels/sheet${conGrafico + 1}.xml.rels",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                        """<Relationship Id="rIdDib" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/>""" +
                        "</Relationships>"
                )
                zip.entrada("xl/drawings/drawing1.xml", drawingXml(h.grafico!!))
                zip.entrada(
                    "xl/drawings/_rels/drawing1.xml.rels",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                        """<Relationship Id="rIdGraf" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart" Target="../charts/chart1.xml"/>""" +
                        "</Relationships>"
                )
                zip.entrada("xl/charts/chart1.xml", chartXml(h.nombre, h.grafico!!))
            }
        }
    }

    private fun ZipOutputStream.entrada(nombre: String, contenido: String) {
        putNextEntry(ZipEntry(nombre))
        write(contenido.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    // ---------- piezas del archivo ----------

    private fun contentTypes(conGrafico: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        hojas.indices.forEach {
            append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        if (conGrafico >= 0) {
            append("""<Override PartName="/xl/drawings/drawing1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>""")
            append("""<Override PartName="/xl/charts/chart1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.chart+xml"/>""")
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

    /**
     * Paleta unica del reporte, la misma que la version web.
     * Los indices de fuente, relleno y borde se referencian a mano en cellXfs,
     * asi que si se agrega uno hay que respetar el orden.
     */
    private fun styles(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        // La fecha va con formato propio: el 14 de Excel depende del idioma del
        // equipo y saca 15/8/2026 en vez de 15/08/2026.
        append("""<numFmts count="2">""")
        append("""<numFmt numFmtId="164" formatCode="&quot;$&quot;#,##0.00"/>""")
        append("""<numFmt numFmtId="165" formatCode="dd/mm/yyyy"/>""")
        append("""</numFmts>""")

        // 0 base, 1 titulo, 2 cabecera, 3 negrita, 4 subtitulo,
        // 5-8 etiquetas de tarjeta, 9-12 valores de tarjeta, 13 seccion, 14 pie
        append("""<fonts count="15">""")
        append(fuente(11, color = "FF1F2937"))
        append(fuente(16, negrita = true, color = "FFFFFFFF"))
        append(fuente(11, negrita = true, color = "FFFFFFFF"))
        append(fuente(11, negrita = true, color = "FF1F2937"))
        append(fuente(9, cursiva = true, color = "FF6B7280"))
        append(fuente(8, negrita = true, color = "FF4F46E5"))
        append(fuente(8, negrita = true, color = "FF059669"))
        append(fuente(8, negrita = true, color = "FFDC2626"))
        append(fuente(8, negrita = true, color = "FFB45309"))
        append(fuente(16, negrita = true, color = "FF4F46E5"))
        append(fuente(16, negrita = true, color = "FF059669"))
        append(fuente(16, negrita = true, color = "FFDC2626"))
        append(fuente(16, negrita = true, color = "FFB45309"))
        append(fuente(10, negrita = true, color = "FF4F46E5"))
        append(fuente(9, cursiva = true, color = "FFFFFFFF"))
        append("""</fonts>""")

        // 0 ninguno, 1 gray125 (Excel los exige en ese orden), 2 indigo,
        // 3 gris oscuro, 4-7 fondos de tarjeta, 8 pie, 9 fila de total
        append("""<fills count="10">""")
        append("""<fill><patternFill patternType="none"/></fill>""")
        append("""<fill><patternFill patternType="gray125"/></fill>""")
        append(relleno("FF4F46E5"))
        append(relleno("FF1F2937"))
        append(relleno("FFEEF2FF"))
        append(relleno("FFECFDF5"))
        append(relleno("FFFEF2F2"))
        append(relleno("FFFFFBEB"))
        append(relleno("FF111827"))
        append(relleno("FFF3F4F6"))
        append("""</fills>""")

        // 0 sin borde, 1 solo linea inferior clara para separar las filas
        append("""<borders count="2">""")
        append("""<border><left/><right/><top/><bottom/><diagonal/></border>""")
        append("""<border><left/><right/><top/><bottom style="thin"><color rgb="FFE5E7EB"/></bottom><diagonal/></border>""")
        append("""</borders>""")

        append("""<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""")
        append("""<cellXfs count="23">""")
        append(xf(0, 0, 0, 1))                                              // NORMAL
        append(xf(0, 1, 2, 0, vert = "center", sangria = 1))                // TITULO
        append(xf(0, 2, 3, 0, hor = "center", vert = "center"))             // CABECERA
        append(xf(164, 0, 0, 1))                                            // MONEDA
        append(xf(165, 0, 0, 1, hor = "center"))                            // FECHA
        append(xf(0, 3, 0, 1))                                              // NEGRITA
        append(xf(164, 3, 0, 1))                                            // MONEDA_NEGRITA
        append(xf(9, 0, 0, 1, hor = "center"))                              // PORCENTAJE
        append(xf(49, 0, 0, 1))                                             // TEXTO
        append(xf(0, 4, 0, 0, sangria = 1))                                 // SUBTITULO
        append(xf(0, 13, 4, 0, vert = "center", sangria = 1))               // SECCION
        append(xf(0, 5, 4, 0, hor = "center"))                              // KPI_ETIQUETA_1
        append(xf(0, 6, 5, 0, hor = "center"))                              // KPI_ETIQUETA_2
        append(xf(0, 7, 6, 0, hor = "center"))                              // KPI_ETIQUETA_3
        append(xf(0, 8, 7, 0, hor = "center"))                              // KPI_ETIQUETA_4
        append(xf(164, 9, 4, 0, hor = "center", vert = "center"))           // KPI_VALOR_1
        append(xf(164, 10, 5, 0, hor = "center", vert = "center"))          // KPI_VALOR_2
        append(xf(164, 11, 6, 0, hor = "center", vert = "center"))          // KPI_VALOR_3
        append(xf(9, 12, 7, 0, hor = "center", vert = "center"))            // KPI_VALOR_4
        append(xf(0, 14, 8, 0, hor = "center", vert = "center"))            // PIE
        append(xf(0, 3, 9, 0))                                              // TOTAL_TEXTO
        append(xf(164, 3, 9, 0))                                            // TOTAL_MONEDA
        append(xf(0, 0, 0, 1, hor = "center"))                              // CENTRADO
        append("""</cellXfs></styleSheet>""")
    }

    private fun fuente(
        tam: Int, negrita: Boolean = false, cursiva: Boolean = false, color: String
    ): String = buildString {
        append("<font>")
        if (negrita) append("<b/>")
        if (cursiva) append("<i/>")
        append("""<sz val="$tam"/><color rgb="$color"/><name val="Calibri"/>""")
        append("</font>")
    }

    private fun relleno(rgb: String): String =
        """<fill><patternFill patternType="solid"><fgColor rgb="$rgb"/><bgColor indexed="64"/></patternFill></fill>"""

    private fun xf(
        numFmt: Int, fuente: Int, relleno: Int, borde: Int,
        hor: String? = null, vert: String? = null, sangria: Int = 0
    ): String = buildString {
        append("""<xf numFmtId="$numFmt" fontId="$fuente" fillId="$relleno" borderId="$borde" xfId="0"""")
        append(""" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1"""")
        val alinear = hor != null || vert != null || sangria > 0
        if (alinear) {
            append(""" applyAlignment="1"><alignment""")
            if (hor != null) append(""" horizontal="$hor"""")
            if (vert != null) append(""" vertical="$vert"""")
            if (sangria > 0) append(""" indent="$sangria"""")
            append("/></xf>")
        } else {
            append("/>")
        }
    }

    private fun hojaXml(h: Hoja): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """)
        append("""xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("""<sheetViews><sheetView workbookViewId="0" showGridLines="0">""")
        if (h.filasCongeladas > 0) {
            val n = h.filasCongeladas
            append("""<pane ySplit="$n" topLeftCell="A${n + 1}" activePane="bottomLeft" state="frozen"/>""")
        }
        append("""</sheetView></sheetViews>""")
        append("""<sheetFormatPr defaultRowHeight="15"/>""")

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
            val alto = h.altos[nf]
            if (alto != null) append("""<row r="$nf" ht="$alto" customHeight="1">""")
            else append("""<row r="$nf">""")
            fila.forEachIndexed { indiceCol, celda ->
                val ref = "${letraColumna(indiceCol)}$nf"
                val s = celda.estilo.id
                when (celda) {
                    is Celda.Texto ->
                        if (celda.valor.isNotEmpty())
                            append("""<c r="$ref" s="$s" t="inlineStr"><is><t xml:space="preserve">${esc(celda.valor)}</t></is></c>""")
                        else
                            append("""<c r="$ref" s="$s"/>""")
                    is Celda.Numero ->
                        append("""<c r="$ref" s="$s"><v>${celda.valor}</v></c>""")
                    is Celda.Fecha ->
                        append("""<c r="$ref" s="$s"><v>${aSerialExcel(celda.ms)}</v></c>""")
                    // Se escribe igual para que herede el color de la tarjeta.
                    Celda.Vacia -> append("""<c r="$ref" s="$s"/>""")
                }
            }
            append("</row>")
        }
        append("</sheetData>")

        // El orden de lo que sigue lo fija el esquema: combinadas, formato
        // condicional, avisos ignorados y por ultimo el dibujo.
        if (h.combinadas.isNotEmpty()) {
            append("""<mergeCells count="${h.combinadas.size}">""")
            h.combinadas.forEach { append("""<mergeCell ref="$it"/>""") }
            append("</mergeCells>")
        }
        h.barras.forEachIndexed { i, rango ->
            append("""<conditionalFormatting sqref="$rango">""")
            append("""<cfRule type="dataBar" priority="${i + 1}"><dataBar>""")
            append("""<cfvo type="num" val="0"/><cfvo type="num" val="1"/>""")
            append("""<color rgb="FF85B7EB"/>""")
            append("""</dataBar></cfRule></conditionalFormatting>""")
        }
        if (h.textoNumerico.isNotEmpty()) {
            append("<ignoredErrors>")
            h.textoNumerico.forEach {
                append("""<ignoredError sqref="$it" numberStoredAsText="1"/>""")
            }
            append("</ignoredErrors>")
        }
        if (h.grafico != null) append("""<drawing r:id="rIdDib"/>""")

        append("</worksheet>")
    }

    // ---------- grafico nativo ----------

    /** Coloca el grafico sobre un rectangulo de celdas. */
    private fun drawingXml(g: Grafico): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" """ +
            """xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">""" +
            """<xdr:twoCellAnchor>""" +
            """<xdr:from><xdr:col>${g.colDesde}</xdr:col><xdr:colOff>0</xdr:colOff>""" +
            """<xdr:row>${g.filaDesde}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>""" +
            """<xdr:to><xdr:col>${g.colHasta}</xdr:col><xdr:colOff>0</xdr:colOff>""" +
            """<xdr:row>${g.filaHasta}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to>""" +
            """<xdr:graphicFrame macro="">""" +
            """<xdr:nvGraphicFramePr><xdr:cNvPr id="2" name="Grafico 1"/><xdr:cNvGraphicFramePr/></xdr:nvGraphicFramePr>""" +
            """<xdr:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/></xdr:xfrm>""" +
            """<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/chart">""" +
            """<c:chart xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" """ +
            """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" r:id="rIdGraf"/>""" +
            """</a:graphicData></a:graphic></xdr:graphicFrame>""" +
            """<xdr:clientData/></xdr:twoCellAnchor></xdr:wsDr>"""

    private fun chartXml(hoja: String, g: Grafico): String = buildString {
        val h = "'${hoja.replace("'", "''")}'"
        val ejeCat = 111111111L
        val ejeVal = 222222222L

        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<c:chartSpace xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" """)
        append("""xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" """)
        append("""xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("""<c:chart>""")
        append("""<c:title><c:tx><c:rich><a:bodyPr/><a:lstStyle/><a:p><a:pPr><a:defRPr sz="1200" b="1">""")
        append("""<a:solidFill><a:srgbClr val="1F2937"/></a:solidFill></a:defRPr></a:pPr>""")
        append("""<a:r><a:rPr lang="es-EC" sz="1200" b="1"/><a:t>${esc(g.titulo)}</a:t></a:r></a:p>""")
        append("""</c:rich></c:tx><c:overlay val="0"/></c:title>""")
        append("""<c:autoTitleDeleted val="0"/>""")
        append("""<c:plotArea><c:layout/>""")
        append("""<c:barChart><c:barDir val="col"/><c:grouping val="clustered"/><c:varyColors val="0"/>""")

        g.series.forEachIndexed { i, s ->
            append("""<c:ser><c:idx val="$i"/><c:order val="$i"/>""")
            append("""<c:tx><c:v>${esc(s.nombre)}</c:v></c:tx>""")
            append("""<c:spPr><a:solidFill><a:srgbClr val="${s.colorRgb}"/></a:solidFill>""")
            append("""<a:ln><a:noFill/></a:ln></c:spPr>""")
            append("""<c:invertIfNegative val="0"/>""")
            append("""<c:cat><c:strRef><c:f>$h!${g.rangoCategorias}</c:f><c:strCache>""")
            append("""<c:ptCount val="${g.categorias.size}"/>""")
            g.categorias.forEachIndexed { j, c ->
                append("""<c:pt idx="$j"><c:v>${esc(c)}</c:v></c:pt>""")
            }
            append("""</c:strCache></c:strRef></c:cat>""")
            append("""<c:val><c:numRef><c:f>$h!${s.rango}</c:f><c:numCache>""")
            append("""<c:formatCode>General</c:formatCode><c:ptCount val="${s.valores.size}"/>""")
            s.valores.forEachIndexed { j, v ->
                append("""<c:pt idx="$j"><c:v>$v</c:v></c:pt>""")
            }
            append("""</c:numCache></c:numRef></c:val></c:ser>""")
        }

        // Hueco ancho entre grupos: Excel no tiene grosor maximo de barra, asi
        // que con dos o tres deudores es lo unico que evita que salgan gordas.
        append("""<c:gapWidth val="300"/><c:overlap val="0"/>""")
        append("""<c:axId val="$ejeCat"/><c:axId val="$ejeVal"/></c:barChart>""")

        append("""<c:catAx><c:axId val="$ejeCat"/>""")
        append("""<c:scaling><c:orientation val="minMax"/></c:scaling><c:delete val="0"/><c:axPos val="b"/>""")
        append("""<c:majorTickMark val="none"/><c:minorTickMark val="none"/><c:tickLblPos val="nextTo"/>""")
        append("""<c:crossAx val="$ejeVal"/></c:catAx>""")

        append("""<c:valAx><c:axId val="$ejeVal"/>""")
        append("""<c:scaling><c:orientation val="minMax"/></c:scaling><c:delete val="0"/><c:axPos val="l"/>""")
        append("""<c:majorGridlines><c:spPr><a:ln w="9525"><a:solidFill>""")
        append("""<a:srgbClr val="E5E7EB"/></a:solidFill></a:ln></c:spPr></c:majorGridlines>""")
        append("""<c:numFmt formatCode="&quot;$&quot;#,##0" sourceLinked="0"/>""")
        append("""<c:majorTickMark val="none"/><c:minorTickMark val="none"/><c:tickLblPos val="nextTo"/>""")
        append("""<c:crossAx val="$ejeCat"/></c:valAx>""")

        append("""</c:plotArea>""")
        append("""<c:legend><c:legendPos val="b"/><c:overlay val="0"/></c:legend>""")
        append("""<c:plotVisOnly val="1"/><c:dispBlanksAs val="gap"/>""")
        append("""</c:chart></c:chartSpace>""")
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
