package tn.binaa.app.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tn.binaa.app.data.AppDatabase
import tn.binaa.app.domain.Formats
import java.io.File

/**
 * Backups are full copies of the SQLite file.
 *  - Automatic: at most every 6 hours, triggered by app start and by every financial change.
 *    The last 20 automatic copies are kept in app storage (also included in Android/Google backup).
 *  - Manual: exported to any location the user chooses (Drive, USB, WhatsApp...).
 *  - Restore: validates the file, makes a safety copy of the current data first, then restarts.
 */
class BackupManager(private val context: Context, private val db: AppDatabase) {

    private val mutex = Mutex()
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)
    val dir: File get() = File(context.filesDir, "backups").apply { mkdirs() }

    val lastBackupAt: Long get() = prefs.getLong("last_auto", 0L)

    private fun checkpoint() {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
    }

    suspend fun createBackup(kind: String): File = withContext(Dispatchers.IO) {
        mutex.withLock {
            checkpoint()
            val src = context.getDatabasePath(AppDatabase.NAME)
            val out = File(dir, "binaa_${kind}_${Formats.fileStamp()}.db")
            src.copyTo(out, overwrite = true)
            if (kind == "auto") {
                prefs.edit().putLong("last_auto", System.currentTimeMillis()).apply()
                dir.listFiles { f -> f.name.startsWith("binaa_auto_") }
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(20)
                    ?.forEach { it.delete() }
            }
            out
        }
    }

    suspend fun autoBackupIfDue() {
        if (System.currentTimeMillis() - lastBackupAt < 6 * 60 * 60 * 1000L) return
        try { createBackup("auto") } catch (_: Exception) { /* best effort, retried next time */ }
    }

    fun list(): List<File> = dir.listFiles { f -> f.name.endsWith(".db") }?.sortedByDescending { it.lastModified() } ?: emptyList()

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val file = createBackup("manual")
        context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: throw IllegalStateException("تعذر فتح الملف للكتابة")
    }

    /** Validates a candidate file: SQLite header + required tables. */
    private fun validate(file: File) {
        val header = ByteArray(16)
        file.inputStream().use { if (it.read(header) != 16) throw IllegalArgumentException("الملف ليس نسخة احتياطية صالحة") }
        if (String(header, Charsets.US_ASCII) != "SQLite format 3\u0000") throw IllegalArgumentException("الملف ليس نسخة احتياطية صالحة")
        val test = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val required = setOf("workers", "projects", "attendance", "advances", "payrolls", "project_payments", "audit_log")
            val found = mutableSetOf<String>()
            test.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c -> while (c.moveToNext()) found += c.getString(0) }
            if (!found.containsAll(required)) throw IllegalArgumentException("الملف لا يحتوي على بيانات هذا التطبيق")
        } finally { test.close() }
    }

    suspend fun restoreFromUri(uri: Uri) = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "restore_candidate.db")
        context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            ?: throw IllegalStateException("تعذر قراءة الملف")
        restore(tmp)
    }

    suspend fun restoreFromFile(file: File) = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "restore_candidate.db")
        file.copyTo(tmp, overwrite = true)
        restore(tmp)
    }

    private suspend fun restore(candidate: File) {
        validate(candidate)
        createBackup("pre_restore") // safety net: current data is never lost
        mutex.withLock {
            db.close()
            val target = context.getDatabasePath(AppDatabase.NAME)
            File(target.path + "-wal").delete()
            File(target.path + "-shm").delete()
            candidate.copyTo(target, overwrite = true)
            candidate.delete()
        }
        restartApp()
    }

    private fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
