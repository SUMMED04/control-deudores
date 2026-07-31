package com.summed.deudores.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ES = Locale("es", "EC")

private val fechaHora = SimpleDateFormat("dd MMM yyyy · HH:mm", ES)
private val fechaLarga = SimpleDateFormat("dd MMM yyyy", ES)
private val fechaCorta = SimpleDateFormat("d MMM", ES)
private val soloHora = SimpleDateFormat("HH:mm", ES)

fun dinero(n: Double): String = "$" + String.format(ES, "%,.2f", n)

fun fechaHoraDe(ms: Long): String = fechaHora.format(Date(ms))
fun fechaLargaDe(ms: Long): String = fechaLarga.format(Date(ms))
fun fechaCortaDe(ms: Long): String = fechaCorta.format(Date(ms))
fun horaDe(ms: Long): String = soloHora.format(Date(ms))

/** "1 día" o "5 días", para no escribir el plural a mano en cada pantalla. */
fun dias(n: Int): String = if (n == 1) "1 día" else "$n días"
