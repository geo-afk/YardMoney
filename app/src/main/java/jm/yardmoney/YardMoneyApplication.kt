package jm.yardmoney

import android.app.Application
import androidx.room.Room
import jm.yardmoney.data.*
import jm.yardmoney.security.PrivateStorage
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class YardMoneyApplication : Application() {
    val storage by lazy { PrivateStorage(this) }
    val database by lazy {
        System.loadLibrary("sqlcipher")
        Room.databaseBuilder(this, YardDatabase::class.java, "yardmoney.db")
            .openHelperFactory(SupportOpenHelperFactory(storage.databasePassphrase()))
            .build()
    }
    val repository by lazy { FinanceRepository(database) }
}
