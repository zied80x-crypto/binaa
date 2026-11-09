package tn.binaa.app.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Payroll week rule (the "Sunday rule"):
 *
 *   A payroll week is identified by its START = the SUNDAY that opens it.
 *   It covers Sunday .. Saturday, and is paid on that Saturday.
 *
 *   Sun 15/11  -> week starting 15/11  (paid Sat 21/11)
 *   Mon 16/11 .. Sat 21/11 -> week starting 15/11
 *   Mon 09/11 .. Sat 14/11 -> week starting 08/11 (paid Sat 14/11)
 *
 * So a Sunday ALWAYS belongs to the week that is paid six days later (the NEXT
 * Saturday), never to the Saturday that just passed. Every date maps to exactly
 * one week, so a Sunday can never be counted twice.
 */
object PayrollWeek {

    /** Start (Sunday) of the payroll week that contains [date]. */
    fun startOf(date: LocalDate): LocalDate {
        // DayOfWeek.value: MONDAY=1 .. SATURDAY=6, SUNDAY=7  ->  % 7 gives SUNDAY=0
        val offset = date.dayOfWeek.value % 7
        return date.minusDays(offset.toLong())
    }

    fun keyOf(date: LocalDate): Long = startOf(date).toEpochDay()

    fun payDay(weekStart: LocalDate): LocalDate {
        require(weekStart.dayOfWeek == DayOfWeek.SUNDAY) { "weekStart must be a Sunday" }
        return weekStart.plusDays(6)
    }

    /** The 7 days of the week in display order: Mon..Sat first, the Sunday shown separately. */
    fun workDays(weekStart: LocalDate): List<LocalDate> = (1..6L).map { weekStart.plusDays(it) }

    fun allDays(weekStart: LocalDate): List<LocalDate> = (0..6L).map { weekStart.plusDays(it) }

    fun contains(weekStart: LocalDate, date: LocalDate): Boolean = startOf(date) == weekStart

    fun previous(weekStart: LocalDate): LocalDate = weekStart.minusWeeks(1)
    fun next(weekStart: LocalDate): LocalDate = weekStart.plusWeeks(1)

    /** Human label: "16/11/2026 ← 21/11/2026 (+ الأحد 15/11)". */
    fun label(weekStart: LocalDate): String =
        "${Formats.date(weekStart.plusDays(1))} - ${Formats.date(payDay(weekStart))}"
}
