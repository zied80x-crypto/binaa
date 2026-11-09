package tn.binaa.app.domain

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.LocalDate

class PayrollLogicTest {

    private fun d(day: Int, month: Int = 11, year: Int = 2026) = LocalDate.of(year, month, day)
    private val wage = 50_000L // 50 DT

    // ---------- Sunday rule (example from the spec, Nov 2026) ----------

    @Test fun mondayToSaturdayIsWeekA() {
        val weekA = d(8) // Sunday 08/11/2026 opens week A
        for (day in 9..14) assertEquals(weekA, PayrollWeek.startOf(d(day)), "day $day")
        assertEquals(d(14), PayrollWeek.payDay(weekA))
    }

    @Test fun sundayGoesToNextWeek() {
        val weekB = d(15)
        assertEquals(weekB, PayrollWeek.startOf(d(15)), "Sunday 15 -> week B")
        for (day in 16..21) assertEquals(weekB, PayrollWeek.startOf(d(day)))
        assertEquals(d(21), PayrollWeek.payDay(weekB))
        // Sunday 15 must NOT be in week A
        assertTrue(!PayrollWeek.contains(d(8), d(15)))
    }

    @Test fun sundayIsNeverInTwoWeeks() {
        var day = d(1, 1, 2026)
        val end = d(31, 12, 2027)
        while (!day.isAfter(end)) {
            val owners = listOf(PayrollWeek.startOf(day).minusWeeks(1), PayrollWeek.startOf(day), PayrollWeek.startOf(day).plusWeeks(1))
                .count { PayrollWeek.allDays(it).contains(day) }
            assertEquals(1, owners, "date $day")
            day = day.plusDays(1)
        }
    }

    @Test fun yearBoundary() {
        // Sun 03/01/2027 opens a week; Thu 31/12/2026 belongs to the week of Sun 27/12/2026
        assertEquals(d(27, 12, 2026), PayrollWeek.startOf(d(31, 12, 2026)))
        assertEquals(d(27, 12, 2026), PayrollWeek.startOf(d(2, 1, 2027)))
        assertEquals(d(3, 1, 2027), PayrollWeek.startOf(d(3, 1, 2027)))
    }

    // ---------- Money ----------

    @Test fun parseMoney() {
        assertEquals(45_000L, Money.parse("45"))
        assertEquals(45_500L, Money.parse("45,5"))
        assertEquals(45_500L, Money.parse("45.500"))
        assertEquals(12_345L, Money.parse("12.345"))
        assertNull(Money.parse("12.3456"))
        assertNull(Money.parse("-3"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
    }

    @Test fun formatMoney() {
        assertEquals("1 250,500 د.ت", Money.format(1_250_500))
        assertEquals("50 د.ت", Money.format(50_000))
        assertEquals("-20 د.ت", Money.format(-20_000))
        assertEquals("0,005", Money.format(5, withCurrency = false))
    }

    @Test fun halfDayExact() {
        assertEquals(25_000L, Money.dayAmount(50_000, AttendanceStatus.HALF))
        assertEquals(22_750L, Money.dayAmount(45_500, AttendanceStatus.HALF))
        assertEquals(12_503L, Money.dayAmount(25_005, AttendanceStatus.HALF)) // 12502.5 -> HALF_UP
        assertEquals(0L, Money.dayAmount(50_000, AttendanceStatus.ABSENT))
        assertEquals(50_000L, Money.dayAmount(50_000, AttendanceStatus.PRESENT))
    }

    @Test fun noFloatingPointDrift() {
        // 0.1 + 0.2 style problem: 1000 half days of 33.333 DT
        val amount = Money.dayAmount(33_333, AttendanceStatus.HALF) // 16666.5 -> 16667
        var total = 0L
        repeat(1000) { total += amount }
        assertEquals(16_667_000L, total)
    }

    // ---------- Weekly payroll ----------

    private fun rec(day: Int, s: AttendanceStatus, w: Long = wage, project: Long? = 1) =
        DayRecord(d(day), s, Money.dayAmount(w, s), project)

    @Test fun weekWithSundayAndAdvances() {
        val weekB = d(15)
        val records = listOf(
            rec(15, AttendanceStatus.PRESENT, project = 2), // Sunday -> week B
            rec(16, AttendanceStatus.PRESENT, project = 1),
            rec(17, AttendanceStatus.HALF, project = 2),
            rec(18, AttendanceStatus.ABSENT, project = null),
            rec(19, AttendanceStatus.PRESENT, project = 3),
            rec(20, AttendanceStatus.PRESENT, project = 1),
            rec(21, AttendanceStatus.HALF, project = 1),
        )
        val s = PayrollCalculator.summarize(weekB, records, listOf(30_000, 20_500))
        assertEquals(4, s.fullDays)
        assertEquals(2, s.halfDays)
        assertEquals(1, s.absentDays)
        assertTrue(s.sundayWorked)
        assertEquals(250_000L, s.gross)     // 4 x 50 + 2 x 25
        assertEquals(50_500L, s.advances)
        assertEquals(199_500L, s.net)
    }

    @Test fun sundayRecordRejectedFromPreviousWeek() {
        val weekA = d(8)
        assertFailsWith<IllegalArgumentException> {
            PayrollCalculator.summarize(weekA, listOf(rec(15, AttendanceStatus.PRESENT)), emptyList())
        }
    }

    @Test fun duplicateDateRejected() {
        assertFailsWith<IllegalArgumentException> {
            PayrollCalculator.summarize(d(15), listOf(rec(16, AttendanceStatus.PRESENT), rec(16, AttendanceStatus.HALF)), emptyList())
        }
    }

    @Test fun advancesBiggerThanEarningsGiveNegativeNet() {
        val s = PayrollCalculator.summarize(d(15), listOf(rec(16, AttendanceStatus.HALF)), listOf(40_000))
        assertEquals(-15_000L, s.net)
    }

    // ---------- Forecast ----------

    @Test fun forecastBeforeAnyAttendance() {
        // 6 working days assumed, no Sunday
        assertEquals(300_000L, PayrollCalculator.forecast(d(15), wage, null, emptyList(), emptyList()))
    }

    @Test fun forecastUpdatesWithAttendanceAndAdvances() {
        val recs = listOf(rec(15, AttendanceStatus.PRESENT), rec(16, AttendanceStatus.ABSENT), rec(17, AttendanceStatus.HALF))
        // actual: 50 + 0 + 25 = 75 ; assumed Thu..Sat? no: Wed(18),Thu,Fri,Sat = 4 x 50 = 200 ; minus 10 advance
        assertEquals(265_000L, PayrollCalculator.forecast(d(15), wage, null, recs, listOf(10_000)))
    }

    @Test fun forecastRespectsWorkerStartDate() {
        // worker starts Thursday 19 -> Thu, Fri, Sat assumed
        assertEquals(150_000L, PayrollCalculator.forecast(d(15), wage, d(19), emptyList(), emptyList()))
    }

    // ---------- Ledger balance ----------

    @Test fun partialPaymentCarriesOver() {
        // week A: earned 300, advance 100, paid only 150 -> 50 still owed
        var bal = PayrollCalculator.balance(300_000, 100_000, 150_000)
        assertEquals(50_000L, bal)
        // week B starts: +200 earned, nothing paid yet -> 250 owed; old 50 did not disappear
        bal = PayrollCalculator.balance(500_000, 100_000, 150_000)
        assertEquals(250_000L, bal)
    }
}
