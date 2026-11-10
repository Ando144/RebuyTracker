package dev.rebuytracker.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ES: Locale = Locale.forLanguageTag("es-ES")

fun formatEuros(cents: Long): String =
    NumberFormat.getCurrencyInstance(ES).format(BigDecimal.valueOf(cents, 2))

/** Valor de una ficha con los decimales que hagan falta (0,01 €, 0,0025 €, 0,50 €…). */
fun formatChipValue(centsPerChip: Double): String = NumberFormat.getCurrencyInstance(ES).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 4
}.format(centsPerChip / 100.0)

/** Fichas que se dan por 1 €, solo si es un número redondo. */
fun chipsPerEuro(centsPerChip: Double): Long? {
    val v = 100.0 / centsPerChip
    return if (v >= 1 && kotlin.math.abs(v - Math.round(v)) < 1e-9) Math.round(v) else null
}

fun formatSignedEuros(cents: Long): String =
    if (cents > 0) "+" + formatEuros(cents) else formatEuros(cents)

fun formatChips(chips: Long): String = NumberFormat.getIntegerInstance(ES).apply {
    isGroupingUsed = true
}.format(chips)

fun formatDate(millis: Long): String = SimpleDateFormat("EEE d MMM yyyy · HH:mm", ES).format(Date(millis))

fun formatDay(millis: Long): String = SimpleDateFormat("d/MM/yyyy", ES).format(Date(millis))

fun formatTime(millis: Long): String = SimpleDateFormat("HH:mm", ES).format(Date(millis))

/** Acepta "10", "7,5", "7.50", "12 €"… y devuelve céntimos. */
fun parseCents(input: String): Long? {
    val clean = input.trim().replace("€", "").replace(" ", "").replace(",", ".")
    if (clean.isEmpty()) return null
    return clean.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong()
}

fun parseChips(input: String): Long? = input.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toLongOrNull()

fun centsToInput(cents: Long): String =
    BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString().replace('.', ',')

fun sanitizeMoney(input: String): String = input.filter { it.isDigit() || it == ',' || it == '.' }.take(8)

fun sanitizeChips(input: String): String = input.filter { it.isDigit() }.take(9)
