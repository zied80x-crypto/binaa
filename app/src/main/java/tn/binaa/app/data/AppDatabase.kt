package tn.binaa.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Project::class, ProjectPayment::class, Worker::class, Attendance::class, Advance::class, PayrollRecord::class, AuditEntry::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workers(): WorkerDao
    abstract fun projects(): ProjectDao
    abstract fun payments(): PaymentDao
    abstract fun attendance(): AttendanceDao
    abstract fun advances(): AdvanceDao
    abstract fun payrolls(): PayrollDao
    abstract fun audit(): AuditDao

    companion object {
        const val NAME = "binaa.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                // Never use fallbackToDestructiveMigration: financial data must never be wiped.
                .build()
    }
}
