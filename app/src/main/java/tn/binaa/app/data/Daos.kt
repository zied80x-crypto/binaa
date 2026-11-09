package tn.binaa.app.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class IdSum(val id: Long, val total: Long)

data class LaborCost(val projectId: Long, val total: Long, val fullDays: Int, val halfDays: Int)

data class PaymentWithProject(
    @Embedded val payment: ProjectPayment,
    val projectName: String,
)

@Dao
interface WorkerDao {
    @Query("SELECT * FROM workers ORDER BY status ASC, fullName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Worker>>

    @Query("SELECT * FROM workers WHERE status = 'ACTIVE' ORDER BY fullName COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<Worker>>

    @Query("SELECT * FROM workers ORDER BY fullName COLLATE NOCASE ASC")
    suspend fun getAll(): List<Worker>

    @Query("SELECT * FROM workers WHERE id = :id")
    fun observe(id: Long): Flow<Worker?>

    @Query("SELECT * FROM workers WHERE id = :id")
    suspend fun get(id: Long): Worker?

    @Query("SELECT COUNT(*) FROM workers WHERE fullName = :name AND id != :excludeId")
    suspend fun countByName(name: String, excludeId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(w: Worker): Long

    @Update
    suspend fun update(w: Worker)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY status ASC, startDate DESC")
    fun observeAll(): Flow<List<Project>>

    @Query("SELECT * FROM projects ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<Project>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: Long): Flow<Project?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: Long): Project?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(p: Project): Long

    @Update
    suspend fun update(p: Project)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM project_payments WHERE projectId = :projectId ORDER BY date DESC, id DESC")
    fun observeByProject(projectId: Long): Flow<List<ProjectPayment>>

    @Query(
        """SELECT pp.*, p.name AS projectName FROM project_payments pp
           JOIN projects p ON p.id = pp.projectId
           ORDER BY pp.date DESC, pp.id DESC"""
    )
    fun observeAllWithProject(): Flow<List<PaymentWithProject>>

    @Query(
        """SELECT pp.*, p.name AS projectName FROM project_payments pp
           JOIN projects p ON p.id = pp.projectId
           ORDER BY pp.date ASC, pp.id ASC"""
    )
    suspend fun getAllWithProject(): List<PaymentWithProject>

    @Query("SELECT projectId AS id, COALESCE(SUM(amount),0) AS total FROM project_payments WHERE voided = 0 GROUP BY projectId")
    fun observeTotalsByProject(): Flow<List<IdSum>>

    @Query("SELECT * FROM project_payments WHERE id = :id")
    suspend fun get(id: Long): ProjectPayment?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(p: ProjectPayment): Long

    @Update
    suspend fun update(p: ProjectPayment)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE date = :date")
    fun observeByDate(date: Long): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance WHERE date = :date")
    suspend fun getByDate(date: Long): List<Attendance>

    @Query("SELECT * FROM attendance WHERE workerId = :workerId AND date = :date LIMIT 1")
    suspend fun getByWorkerDate(workerId: Long, date: Long): Attendance?

    @Query("SELECT * FROM attendance WHERE weekStart = :weekStart")
    fun observeByWeek(weekStart: Long): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance WHERE weekStart = :weekStart")
    suspend fun getByWeek(weekStart: Long): List<Attendance>

    @Query("SELECT * FROM attendance WHERE workerId = :workerId AND weekStart = :weekStart ORDER BY date ASC")
    suspend fun getByWorkerWeek(workerId: Long, weekStart: Long): List<Attendance>

    @Query("SELECT * FROM attendance WHERE workerId = :workerId ORDER BY date DESC")
    fun observeByWorker(workerId: Long): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance WHERE workerId = :workerId ORDER BY date ASC")
    suspend fun getByWorker(workerId: Long): List<Attendance>

    @Query("SELECT * FROM attendance WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getRange(from: Long, to: Long): List<Attendance>

    @Query("SELECT * FROM attendance WHERE projectId = :projectId ORDER BY date DESC")
    fun observeByProject(projectId: Long): Flow<List<Attendance>>

    @Query("SELECT workerId AS id, COALESCE(SUM(amount),0) AS total FROM attendance GROUP BY workerId")
    fun observeEarnedByWorker(): Flow<List<IdSum>>

    @Query("SELECT workerId AS id, COALESCE(SUM(amount),0) AS total FROM attendance GROUP BY workerId")
    suspend fun getEarnedByWorker(): List<IdSum>

    @Query(
        """SELECT projectId, COALESCE(SUM(amount),0) AS total,
           SUM(CASE WHEN status = 'PRESENT' THEN 1 ELSE 0 END) AS fullDays,
           SUM(CASE WHEN status = 'HALF' THEN 1 ELSE 0 END) AS halfDays
           FROM attendance WHERE projectId IS NOT NULL GROUP BY projectId"""
    )
    fun observeLaborCost(): Flow<List<LaborCost>>

    @Query(
        """SELECT projectId, COALESCE(SUM(amount),0) AS total,
           SUM(CASE WHEN status = 'PRESENT' THEN 1 ELSE 0 END) AS fullDays,
           SUM(CASE WHEN status = 'HALF' THEN 1 ELSE 0 END) AS halfDays
           FROM attendance WHERE projectId IS NOT NULL GROUP BY projectId"""
    )
    suspend fun getLaborCost(): List<LaborCost>

    @Query("SELECT DISTINCT weekStart FROM attendance WHERE workerId = :workerId")
    suspend fun weeksForWorker(workerId: Long): List<Long>

    @Query("SELECT projectId FROM attendance WHERE workerId = :workerId AND projectId IS NOT NULL ORDER BY date DESC LIMIT 1")
    suspend fun lastProjectOf(workerId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(a: Attendance): Long

    @Update
    suspend fun update(a: Attendance)

    @Query("DELETE FROM attendance WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AdvanceDao {
    @Query("SELECT * FROM advances WHERE workerId = :workerId ORDER BY timestamp DESC")
    fun observeByWorker(workerId: Long): Flow<List<Advance>>

    @Query("SELECT * FROM advances WHERE workerId = :workerId AND weekStart = :weekStart AND voided = 0 ORDER BY timestamp ASC")
    suspend fun getByWorkerWeek(workerId: Long, weekStart: Long): List<Advance>

    @Query("SELECT * FROM advances WHERE workerId = :workerId AND weekStart = :weekStart ORDER BY timestamp ASC")
    suspend fun getByWorkerWeekIncludingVoided(workerId: Long, weekStart: Long): List<Advance>

    @Query("SELECT * FROM advances WHERE weekStart = :weekStart AND voided = 0")
    fun observeByWeek(weekStart: Long): Flow<List<Advance>>

    @Query("SELECT * FROM advances WHERE weekStart = :weekStart AND voided = 0")
    suspend fun getByWeek(weekStart: Long): List<Advance>

    @Query("SELECT * FROM advances WHERE voided = 0 AND timestamp BETWEEN :from AND :to ORDER BY timestamp ASC")
    suspend fun getRange(from: Long, to: Long): List<Advance>

    @Query("SELECT * FROM advances WHERE voided = 0 ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Advance>>

    @Query("SELECT workerId AS id, COALESCE(SUM(amount),0) AS total FROM advances WHERE voided = 0 GROUP BY workerId")
    fun observeSumsByWorker(): Flow<List<IdSum>>

    @Query("SELECT workerId AS id, COALESCE(SUM(amount),0) AS total FROM advances WHERE voided = 0 GROUP BY workerId")
    suspend fun getSumsByWorker(): List<IdSum>

    @Query("SELECT DISTINCT weekStart FROM advances WHERE workerId = :workerId")
    suspend fun weeksForWorker(workerId: Long): List<Long>

    @Query("SELECT * FROM advances WHERE id = :id")
    suspend fun get(id: Long): Advance?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(a: Advance): Long

    @Update
    suspend fun update(a: Advance)
}

@Dao
interface PayrollDao {
    @Query("SELECT * FROM payrolls WHERE workerId = :workerId AND weekStart = :weekStart LIMIT 1")
    suspend fun get(workerId: Long, weekStart: Long): PayrollRecord?

    @Query("SELECT * FROM payrolls WHERE workerId = :workerId ORDER BY weekStart DESC")
    fun observeByWorker(workerId: Long): Flow<List<PayrollRecord>>

    @Query("SELECT * FROM payrolls WHERE weekStart = :weekStart")
    fun observeByWeek(weekStart: Long): Flow<List<PayrollRecord>>

    @Query("SELECT * FROM payrolls WHERE weekStart = :weekStart")
    suspend fun getByWeek(weekStart: Long): List<PayrollRecord>

    @Query("SELECT * FROM payrolls WHERE weekStart BETWEEN :fromWeek AND :toWeek ORDER BY weekStart ASC")
    suspend fun getRange(fromWeek: Long, toWeek: Long): List<PayrollRecord>

    @Query("SELECT * FROM payrolls ORDER BY paidAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PayrollRecord>>

    @Query("SELECT workerId AS id, COALESCE(SUM(paidAmount),0) AS total FROM payrolls GROUP BY workerId")
    fun observePaidByWorker(): Flow<List<IdSum>>

    @Query("SELECT workerId AS id, COALESCE(SUM(paidAmount),0) AS total FROM payrolls GROUP BY workerId")
    suspend fun getPaidByWorker(): List<IdSum>

    @Query("SELECT weekStart FROM payrolls WHERE workerId = :workerId")
    suspend fun weeksForWorker(workerId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(p: PayrollRecord): Long

    @Update
    suspend fun update(p: PayrollRecord)
}

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(e: AuditEntry): Long

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT 1000")
    fun observeRecent(): Flow<List<AuditEntry>>
}
