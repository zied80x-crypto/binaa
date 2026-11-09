package tn.binaa.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formats {
    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US)
    private val DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.US)
    private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", Locale.US)

    fun date(d: LocalDate): String = d.format(DATE)
    fun date(epochDay: Long): String = date(LocalDate.ofEpochDay(epochDay))
    fun dateTime(epochMillis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(DATE_TIME)
    fun fileStamp(dt: LocalDateTime = LocalDateTime.now()): String = dt.format(FILE_STAMP)

    fun parseDate(s: String): LocalDate? = try { LocalDate.parse(s.trim(), DATE) } catch (e: Exception) { null }

    fun dayName(d: LocalDate): String = when (d.dayOfWeek) {
        DayOfWeek.MONDAY -> "الاثنين"
        DayOfWeek.TUESDAY -> "الثلاثاء"
        DayOfWeek.WEDNESDAY -> "الأربعاء"
        DayOfWeek.THURSDAY -> "الخميس"
        DayOfWeek.FRIDAY -> "الجمعة"
        DayOfWeek.SATURDAY -> "السبت"
        DayOfWeek.SUNDAY -> "الأحد"
        null -> ""
    }

    private val MONTHS = listOf(
        "جانفي", "فيفري", "مارس", "أفريل", "ماي", "جوان",
        "جويلية", "أوت", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )
    fun monthName(month: Int): String = MONTHS[month - 1]
}
