package jm.yardmoney

import android.app.Application
import androidx.room.Room
import jm.yardmoney.data.*
import jm.yardmoney.security.PrivateStorage
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class YardMoneyApplication : Application() {
    val storage by lazy { PrivateStorage(this) }
    private var openDatabase: YardDatabase? = null
    private var openRepository: FinanceRepository? = null

    // Built on first use. A failed build is retried on the next access instead of being cached.
    val database: YardDatabase
        @Synchronized get() = openDatabase ?: buildDatabase().also { openDatabase = it }

    val repository: FinanceRepository
        @Synchronized
        get() = openRepository ?: FinanceRepository(database).also { openRepository = it }

    /** Closes and forgets the database so the next access opens it again (used by recovery). */
    @Synchronized
    fun closeDatabase() {
        runCatching { openDatabase?.close() }
        openDatabase = null
        openRepository = null
    }

    private fun buildDatabase(): YardDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(this, YardDatabase::class.java, "yardmoney.db")
            .addMigrations(YardDatabase.MIGRATION_1_2, YardDatabase.MIGRATION_2_3, YardDatabase.MIGRATION_3_4, YardDatabase.MIGRATION_4_5)
            .openHelperFactory(SupportOpenHelperFactory(storage.databasePassphrase()))
            .build()
    }
}
