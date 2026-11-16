package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.example.config.AppVersionConfig
import com.example.data.model.*
import com.example.util.DatabaseBackupManager

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        PetrolExpenseEntity::class,
        ExtraExpenseEntity::class,
        SalaryRecordEntity::class,
        LoanRecordEntity::class,
        MonthlyStatementEntity::class,
        UserProfileEntity::class,
        CategoryEntity::class,
        UdhaarRecordEntity::class,
        UdhaarRepaymentEntity::class,
        SavingsPotEntity::class,
        SavingsEntryEntity::class
    ],
    version = AppVersionConfig.DB_SCHEMA_VERSION,
    exportSchema = false
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        const val DATABASE_NAME = "finance_statement_database.db"

        @Volatile
        private var INSTANCE: FinanceDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN petrolLiters REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN petrolRate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN extraItem TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_profile ADD COLUMN profileImageBase64 TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN accountType TEXT NOT NULL DEFAULT 'BANK'")
                db.execSQL("UPDATE accounts SET accountType = 'WALLET' WHERE LOWER(id) IN ('sadapay', 'easypaisa', 'jazzcash', 'wallet') OR LOWER(name) IN ('sadapay', 'easypaisa', 'jazzcash', 'wallet')")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS udhaar_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        personName TEXT NOT NULL,
                        phoneNumber TEXT NOT NULL DEFAULT '',
                        category TEXT NOT NULL DEFAULT 'MONEY',
                        originalAmount REAL NOT NULL DEFAULT 0.0,
                        remainingAmount REAL NOT NULL DEFAULT 0.0,
                        itemDescription TEXT NOT NULL DEFAULT '',
                        date TEXT NOT NULL,
                        time TEXT NOT NULL,
                        dayOfWeek TEXT NOT NULL DEFAULT '',
                        dueDate TEXT NOT NULL DEFAULT '',
                        note TEXT NOT NULL DEFAULT '',
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        isItemReturned INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS udhaar_repayments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        udhaarId INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        date TEXT NOT NULL,
                        time TEXT NOT NULL,
                        dayOfWeek TEXT NOT NULL DEFAULT '',
                        note TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (udhaarId) REFERENCES udhaar_records(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS index_udhaar_repayments_udhaarId ON udhaar_repayments(udhaarId)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE udhaar_records ADD COLUMN accountName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE udhaar_records ADD COLUMN transactionId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE udhaar_repayments ADD COLUMN accountName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE udhaar_repayments ADD COLUMN transactionId INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS savings_pots (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        targetAmount REAL,
                        savedAmount REAL NOT NULL DEFAULT 0.0,
                        targetDate TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS savings_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        potId INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        amount REAL NOT NULL,
                        accountName TEXT NOT NULL,
                        date TEXT NOT NULL,
                        time TEXT NOT NULL,
                        dayOfWeek TEXT NOT NULL DEFAULT '',
                        note TEXT NOT NULL DEFAULT '',
                        transactionId INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (potId) REFERENCES savings_pots(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_entries_potId ON savings_entries(potId)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN holderTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE accounts ADD COLUMN accountNumberLast4 TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE accounts ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE accounts ADD COLUMN isBuiltIn INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE accounts ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")

                db.execSQL("UPDATE accounts SET isBuiltIn = 1 WHERE LOWER(id) IN ('hbl', 'ubl', 'sadapay', 'easypaisa', 'jazzcash', 'mashreq', 'wallet') OR LOWER(name) IN ('hbl', 'ubl', 'sadapay', 'easypaisa', 'jazzcash', 'mashreq', 'wallet')")
                db.execSQL("UPDATE accounts SET accountType = 'BANK' WHERE LOWER(id) IN ('hbl', 'ubl', 'mashreq') OR LOWER(name) IN ('hbl', 'ubl', 'mashreq')")
                db.execSQL("UPDATE accounts SET accountType = 'WALLET' WHERE LOWER(id) IN ('sadapay', 'easypaisa', 'jazzcash', 'wallet') OR LOWER(name) IN ('sadapay', 'easypaisa', 'jazzcash', 'wallet')")
            }
        }

        /**
         * Non-destructive upgrade from schema v9 -> v10:
         * - Upgrades user_profile PK to AUTOINCREMENT so multiple users on one device each get unique IDs.
         * - Adds userId column to all 11 user-owned tables and binds existing rows to the existing user.
         * - Never removes, renames, or resets existing user accounts, balances, or transaction history.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Rebuild user_profile with AUTOINCREMENT primary key
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_profile_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        fullName TEXT NOT NULL,
                        email TEXT NOT NULL,
                        mobile TEXT NOT NULL,
                        passwordHash TEXT NOT NULL,
                        isEmailVerified INTEGER NOT NULL,
                        verificationToken TEXT NOT NULL,
                        verificationExpiry INTEGER NOT NULL,
                        accountStatus TEXT NOT NULL,
                        pinCode TEXT NOT NULL,
                        isBiometricEnabled INTEGER NOT NULL,
                        currencySymbol TEXT NOT NULL,
                        isLoggedIn INTEGER NOT NULL,
                        profileImageBase64 TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO user_profile_new (
                        id, fullName, email, mobile, passwordHash, isEmailVerified,
                        verificationToken, verificationExpiry, accountStatus, pinCode,
                        isBiometricEnabled, currencySymbol, isLoggedIn, profileImageBase64, createdAt
                    )
                    SELECT
                        id, fullName, email, mobile, passwordHash, isEmailVerified,
                        verificationToken, verificationExpiry, accountStatus, pinCode,
                        isBiometricEnabled, currencySymbol, isLoggedIn, profileImageBase64, createdAt
                    FROM user_profile
                """.trimIndent())

                db.execSQL("DROP TABLE user_profile")
                db.execSQL("ALTER TABLE user_profile_new RENAME TO user_profile")

                // 2. Add userId column to all user-owned tables
                val userTables = listOf(
                    "accounts",
                    "transactions",
                    "petrol_expenses",
                    "extra_expenses",
                    "salary_records",
                    "loan_records",
                    "monthly_statements",
                    "udhaar_records",
                    "udhaar_repayments",
                    "savings_pots",
                    "savings_entries"
                )
                for (table in userTables) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN userId INTEGER NOT NULL DEFAULT 1")
                }

                // Determine legacy owner userId (defaults to 1 if no profile exists yet)
                val ownerUserId = db.query(
                    "SELECT id FROM user_profile ORDER BY isLoggedIn DESC, id ASC LIMIT 1"
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 1
                }

                for (table in userTables) {
                    db.execSQL("UPDATE $table SET userId = ?", arrayOf(ownerUserId))
                }

                // 3. Scope existing monthly_statements primary keys by userId while preserving data
                db.execSQL("UPDATE monthly_statements SET id = 'u' || userId || '_' || id WHERE id NOT LIKE 'u%'")
            }
        }

        private fun safeOpenHelperFactory(backupPathProvider: () -> String?): SupportSQLiteOpenHelper.Factory {
            val delegate = FrameworkSQLiteOpenHelperFactory()
            return SupportSQLiteOpenHelper.Factory { config ->
                val wrappedCallback = object : SupportSQLiteOpenHelper.Callback(config.callback.version) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        config.callback.onConfigure(db)
                    }

                    override fun onCreate(db: SupportSQLiteDatabase) {
                        config.callback.onCreate(db)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        try {
                            config.callback.onUpgrade(db, oldVersion, newVersion)
                        } catch (t: Throwable) {
                            val backupInfo = backupPathProvider()?.let { "\nBackup preserved at: $it" } ?: ""
                            DatabaseBackupManager.reportMigrationError(
                                "Database upgrade from v$oldVersion to v$newVersion failed without wiping your existing data.${backupInfo}\nError: ${t.message ?: t::class.java.simpleName}"
                            )
                            throw t
                        }
                    }

                    override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        try {
                            config.callback.onDowngrade(db, oldVersion, newVersion)
                        } catch (t: Throwable) {
                            DatabaseBackupManager.reportMigrationError(
                                "Database downgrade from v$oldVersion to v$newVersion is unsupported; your existing file was left intact.\nError: ${t.message}"
                            )
                            throw t
                        }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        config.callback.onOpen(db)
                    }
                }
                val newConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context)
                    .name(config.name)
                    .callback(wrappedCallback)
                    .noBackupDirectory(config.useNoBackupDirectory)
                    .allowDataLossOnRecovery(false)
                    .build()
                delegate.create(newConfig)
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10
        )

        /**
         * Thread-safe singleton builder for the Room SQLite database.
         * Never uses destructive fallback migration; creates a pre-migration backup automatically.
         */
        fun getDatabase(context: Context): FinanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                val backupResult = try {
                    DatabaseBackupManager.backupBeforeMigrationIfNeeded(
                        context = appContext,
                        dbName = DATABASE_NAME,
                        targetVersion = AppVersionConfig.DB_SCHEMA_VERSION
                    )
                } catch (_: Exception) {
                    null
                }

                val instance = Room.databaseBuilder(
                    appContext,
                    FinanceDatabase::class.java,
                    DATABASE_NAME
                )
                    .openHelperFactory(safeOpenHelperFactory { backupResult?.backupFilePath })
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
