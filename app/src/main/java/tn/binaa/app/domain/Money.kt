package tn.binaa.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * All money is stored as a Long number of MILLIMES (1 DT = 1000 millimes).
 * Integer storage makes SQL SUM() exact; any multiplication (e.g. half-day)
 * goes through BigDecimal with an explicit rounding rule. No Float/Double anywhere.
 */
object Money {
    const val MILLIMES_PER_DINAR = 1000L
    private val ROUNDING = RoundingMode.HALF_UP

    /** Parses user input such as "45", "45.5", "45,500" into millimes. Returns null if invalid or negative. */
    fun parse(input: String): Long? {
        val cleaned = input.trim().replace(',', '.').replace(" ", "")
        if (cleaned.isEmpty()) return null
        val bd = try { BigDecimal(cleaned) } catch (e: NumberFormatException) { return null }
        if (bd.signum() < 0) return null
        if (bd.stripTrailingZeros().scale() > 3) return null
        return try {
            bd.movePointRight(3).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        } catch (e: ArithmeticException) { null }
    }

    fun toDecimal(millimes: Long): BigDecimal = BigDecimal.valueOf(millimes, 3)

    /** "1 250,500 د.ت" style, with Latin digits as used in Tunisia. */
    fun format(millimes: Long, withCurrency: Boolean = true): String {
        val negative = millimes < 0
        val abs = kotlin.math.abs(millimes)
        val dinars = abs / MILLIMES_PER_DINAR
        val mill = abs % MILLIMES_PER_DINAR
        val dinarStr = dinars.toString().reversed().chunked(3).joinToString(" ").reversed()
        val body = if (mill == 0L) dinarStr else dinarStr + "," + mill.toString().padStart(3, '0')
        val sign = if (negative) "-" else ""
        return if (withCurrency) "$sign$body د.ت" else "$sign$body"
    }

    /** Plain decimal string for editing fields / exports: "45.500" -> "45.5". */
    fun toInput(millimes: Long): String = toDecimal(millimes).stripTrailingZeros().toPlainString()

    /** Amount earned for one day = wage x factor, rounded HALF_UP to the millime. */
    fun dayAmount(dailyWage: Long, status: AttendanceStatus): Long =
        BigDecimal.valueOf(dailyWage)
            .multiply(status.factor)
            .setScale(0, ROUNDING)
            .longValueExact()
}

enum class AttendanceStatus(val factor: BigDecimal, val label: String) {
    PRESENT(BigDecimal.ONE, "حاضر"),
    HALF(BigDecimal("0.5"), "نصف يوم"),
    ABSENT(BigDecimal.ZERO, "غائب");
}
