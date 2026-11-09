package tn.binaa.app.domain

import java.time.DayOfWeek
import java.time.LocalDate

/** One attendance record, already priced (amount = wage snapshot x factor). */
data class DayRecord(
    val date: LocalDate,
    val status: AttendanceStatus,
    val amount: Long,
    val projectId: Long? = null,
)

data class WeekSummary(
    val weekStart: LocalDate,
    val fullDays: Int,
    val halfDays: Int,
    val absentDays: Int,
    val sundayWorked: Boolean,
    val gross: Long,
    val advances: Long,
) {
    /** Remaining salary = gross - advances. Can be negative if advances exceed earnings. */
    val net: Long get() = gross - advances
}

object PayrollCalculator {

    /**
     * Actual payroll for one worker and one payroll week.
     * Records outside the week are rejected (never mix payroll periods).
     */
    fun summarize(weekStart: LocalDate, records: List<DayRecord>, advanceAmounts: List<Long>): WeekSummary {
        require(weekStart.dayOfWeek == DayOfWeek.SUNDAY) { "weekStart must be a Sunday" }
        records.forEach {
            require(PayrollWeek.startOf(it.date) == weekStart) { "Record ${it.date} is not in week $weekStart" }
        }
        require(records.map { it.date }.toSet().size == records.size) { "Duplicate attendance date" }
        var full = 0; var half = 0; var absent = 0; var gross = 0L; var sunday = false
        for (r in records) {
            when (r.status) {
                AttendanceStatus.PRESENT -> full++
                AttendanceStatus.HALF -> half++
                AttendanceStatus.ABSENT -> absent++
            }
            if (r.date.dayOfWeek == DayOfWeek.SUNDAY && r.status != AttendanceStatus.ABSENT) sunday = true
            gross = Math.addExact(gross, r.amount)
        }
        val adv = advanceAmounts.fold(0L) { acc, a -> require(a > 0) { "Advance must be positive" }; Math.addExact(acc, a) }
        return WeekSummary(weekStart, full, half, absent, sunday, gross, adv)
    }

    /**
     * Forecast for the week's Saturday payroll.
     * - Recorded days use their real amount (Sunday included only when recorded).
     * - Mon..Sat days NOT yet recorded are assumed to be full working days at [currentWage],
     *   except days before the worker's [workerStart] date.
     * As attendance is entered the assumed days are replaced by real ones.
     */
    fun forecast(
        weekStart: LocalDate,
        currentWage: Long,
        workerStart: LocalDate?,
        records: List<DayRecord>,
        advanceAmounts: List<Long>,
    ): Long {
        val actual = summarize(weekStart, records, advanceAmounts)
        val recordedDates = records.map { it.date }.toSet()
        var assumed = 0L
        for (d in PayrollWeek.workDays(weekStart)) {
            if (d in recordedDates) continue
            if (workerStart != null && d.isBefore(workerStart)) continue
            assumed = Math.addExact(assumed, currentWage)
        }
        return actual.gross + assumed - actual.advances
    }

    /**
     * Running balance owed to a worker across ALL time:
     * everything earned - all (non-voided) advances - everything actually paid.
     * Because it is a ledger, an unpaid or partially paid week never "disappears"
     * when a new week begins.
     */
    fun balance(totalEarned: Long, totalAdvances: Long, totalPaid: Long): Long =
        totalEarned - totalAdvances - totalPaid
}
