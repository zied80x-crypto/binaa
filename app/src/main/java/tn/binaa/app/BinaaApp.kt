package tn.binaa.app

import android.app.Application
import tn.binaa.app.backup.BackupManager
import tn.binaa.app.data.AppDatabase
import tn.binaa.app.data.Repository
import tn.binaa.app.security.AuthStore

class AppContainer(app: Application) {
    val db: AppDatabase = AppDatabase.build(app)
    val backup = BackupManager(app, db)
    val repo = Repository(db) { backup.autoBackupIfDue() }
    val auth = AuthStore(app)
}

class BinaaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
