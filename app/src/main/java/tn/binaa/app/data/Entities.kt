package tn.binaa.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import tn.binaa.app.domain.AttendanceStatus

/*
 * Conventions
 *  - Money: Long millimes (1 DT = 1000).
 *  - Dates: Long epochDay (LocalDate.toEpochDay()).
 *  - Timestamps: Long epoch millis.
 *  - weekStart: epochDay of the SUNDAY that opens the payroll week (see PayrollWeek).
 *  - Financial rows are never deleted: they are voided with a reason, and every
 *    change is written to audit_log. Every table has a stable Long id and
 *    createdAt/updatedAt so cloud sync can be added later.
 */

enum class ProjectStatus(val label: String) { ACTIVE("نشط"), COMPLETED("مكتمل"), SUSPENDED("متوقف") }
enum class WorkerStatus(val label: String) { ACTIVE("نشط"), INACTIVE("غير نشط") }

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val ownerPhone: String = "",
    val address: String = "",
    val startDate: Long,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "project_payments",
    foreignKeys = [ForeignKey(entity = Project::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("projectId"), Index("date")]
)
data class ProjectPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val amount: Long,
    val date: Long,
    val method: String = "",
    val notes: String = "",
    val voided: Boolean = false,
    val voidReason: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "workers")
data class Worker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val phone: String = "",
    val dailyWage: Long,
    val startDate: Long,
    val status: WorkerStatus = WorkerStatus.ACTIVE,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** One row per worker per day (unique index) -> no duplicate attendance. */
@Entity(
    tableName = "attendance",
    foreignKeys = [
        ForeignKey(entity = Worker::class, parentColumns = ["id"], childColumns = ["workerId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = Project::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["workerId", "date"], unique = true), Index("weekStart"), Index("projectId"), Index("date")]
)
data class Attendance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workerId: Long,
    val date: Long,
    val weekStart: Long,
    val status: AttendanceStatus,
    val projectId: Long?,
    /** Wage at the moment of recording, so a later wage change never rewrites history. */
    val wageSnapshot: Long,
    val amount: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "advances",
    foreignKeys = [ForeignKey(entity = Worker::class, parentColumns = ["id"], childColumns = ["workerId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("workerId"), Index("weekStart")]
)
data class Advance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workerId: Long,
    val amount: Long,
    val timestamp: Long,
    /** Exactly one payroll week -> an advance can only ever be deducted once. */
    val weekStart: Long,
    val notes: String = "",
    val voided: Boolean = false,
    val voidReason: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Snapshot written ONLY when the user presses "Confirm payment".
 * Unique (workerId, weekStart) -> a week can never be paid twice.
 */
@Entity(
    tableName = "payrolls",
    foreignKeys = [ForeignKey(entity = Worker::class, parentColumns = ["id"], childColumns = ["workerId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["workerId", "weekStart"], unique = true), Index("weekStart")]
)
data class PayrollRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workerId: Long,
    val weekStart: Long,
    val fullDays: Int,
    val halfDays: Int,
    val absentDays: Int,
    val gross: Long,
    val advances: Long,
    val net: Long,
    val paidAmount: Long,
    val paidAt: Long,
    val notes: String = "",
    /** Set when an authorized correction touched this week after payment. */
    val corrected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "audit_log", indices = [Index("timestamp")])
data class AuditEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val entityType: String,
    val entityId: Long,
    val action: String,
    val details: String,
)
