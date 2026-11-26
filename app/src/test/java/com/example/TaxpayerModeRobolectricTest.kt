package com.example

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.config.AppVersionConfig
import com.example.data.local.FinanceDao
import com.example.data.local.FinanceDatabase
import com.example.data.model.*
import com.example.data.repository.FinanceRepository
import com.example.data.repository.TaxRepository
import com.example.ui.components.buildFinanceBottomNavItems
import com.example.ui.navigation.AppScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TaxScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaxpayerModeRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: FinanceDatabase
    private lateinit var dao: FinanceDao
    private lateinit var repository: FinanceRepository
    private lateinit var taxRepository: TaxRepository
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.financeDao()
        repository = FinanceRepository(dao, db)
        taxRepository = TaxRepository(dao, db.taxDao(), db)
        runBlocking {
            repository.initializeIfEmpty()
            repository.registerUser("Taxpayer User", "filer@example.com", "+923001112233", "SecretPass123")
            repository.verifyEmail("VERIFIED")
            repository.loginUser("filer@example.com", "SecretPass123")
            repository.seedLegacyDefaultAccountsForTest()
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testPakistanTaxYearMathAndCustomSlabEstimator() {
        assertEquals(2027, PakistanTaxYearUtils.taxYearForDate("2026-07-01"))
        assertEquals(2027, PakistanTaxYearUtils.taxYearForDate("2027-06-30"))
        assertEquals(2026, PakistanTaxYearUtils.taxYearForDate("2026-06-30"))
        assertEquals("Tax Year 2027 (1 Jul 2026 - 30 Jun 2027)", PakistanTaxYearUtils.formatTaxYearLabel(2027))
        assertEquals("2026-07-01", PakistanTaxYearUtils.startDateForTaxYear(2027))
        assertEquals("2027-06-30", PakistanTaxYearUtils.endDateForTaxYear(2027))

        val slabs = PakistanTaxYearUtils.editableStarterTemplate()
        val json = PakistanTaxYearUtils.serializeSlabsJson(slabs)
        val parsed = PakistanTaxYearUtils.parseSlabsJson(json)
        assertEquals(slabs.size, parsed.size)
        // On 1,500,000 -> falls into 1,200,000..2,200,000 slab: 30,000 + (300,000 * 15%) = 75,000
        assertEquals(75_000.0, PakistanTaxYearUtils.estimateTaxFromUserSlabs(1_500_000.0, parsed), 0.01)
    }

    @Test
    fun testEncryptedCnicNtnAndMaskingAndCopySafety(): Unit = runBlocking {
        val rawCnic = "35202-1234567-1"
        val rawNtn = "1234567-8"
        taxRepository.saveTaxProfile(
            context = context,
            fullName = "Ali Raza",
            cnicPlain = rawCnic,
            ntnPlain = rawNtn,
            filerStatus = "Filer (ATL Active)",
            occupationType = "Salaried",
            note = "RTO Lahore"
        )

        val stored = taxRepository.taxProfile.first()!!
        assertEquals("Ali Raza", stored.fullName)
        assertFalse(stored.cnicEncrypted.contains("35202"))
        assertFalse(stored.cnicEncrypted.contains("1234567"))
        assertFalse(stored.ntnEncrypted.contains("1234567"))

        val decCnic = TaxCryptoHelper.decryptSensitive(context, stored.cnicEncrypted)
        val decNtn = TaxCryptoHelper.decryptSensitive(context, stored.ntnEncrypted)
        assertEquals(rawCnic, decCnic)
        assertEquals(rawNtn, decNtn)

        assertEquals("35202-•••••••-1", TaxCryptoHelper.maskCnic(decCnic))
        assertEquals("••••••7-8", TaxCryptoHelper.maskNtn(decNtn))
    }

    @Test
    fun testAllTaxDataWorkflowsLockingWealthAutoFillExportsAndZeroDoubleCounting(): Unit = runBlocking {
        // 1. Add normal HBL income & expense and record starting totals
        val initialHbl = dao.getAllAccountsDirectForUser(taxRepository.resolveActiveUserId())
            .first { it.name == "HBL" }.currentBalance

        val salaryTxId = repository.addTransaction(
            date = "2026-08-05",
            time = "10:00 AM",
            type = "INCOME",
            amount = 200_000.0,
            fromAccount = "Acme Corp",
            toAccount = "HBL",
            category = "Salary",
            comments = "August Salary",
            isTaxRelated = true,
            taxSectionType = TAX_GROUP_INCOME,
            taxCategory = "Salary",
            taxYearId = "2027"
        )

        val normalExpTxId = repository.addTransaction(
            date = "2026-09-10",
            time = "02:00 PM",
            type = "EXPENSE",
            amount = 15_000.0,
            fromAccount = "HBL",
            toAccount = "School",
            category = "Education",
            comments = "Term Fees"
        )

        // Bulk tag from statement
        repository.bulkTagTransactionsForTax(
            ids = listOf(normalExpTxId),
            isTaxRelated = true,
            taxSectionType = TAX_GROUP_EXPENSE,
            taxCategory = "School fees",
            taxYearId = "2027"
        )

        val hblAfterNormalTx = dao.getAllAccountsDirectForUser(taxRepository.resolveActiveUserId())
            .first { it.name == "HBL" }.currentBalance
        assertEquals(initialHbl + 200_000.0 - 15_000.0, hblAfterNormalTx, 0.01)

        val txCountBeforeTaxEntries = repository.transactions.first().size

        // 2. Create Tax Year 2027 + add manual Tax entries across all 5 categories
        taxRepository.ensureTaxYearExists(2027)
        taxRepository.updateTaxYearDeadline(2027, "2027-09-30", 7)

        taxRepository.saveTaxEntry(
            taxYear = 2027,
            entryGroup = TAX_GROUP_INCOME,
            category = "Crypto Gains (Custom)",
            amount = 50_000.0,
            date = "2026-10-01",
            sourceOrPayer = "Exchange",
            notes = "Manual tax income"
        )
        taxRepository.saveTaxEntry(
            taxYear = 2027,
            entryGroup = TAX_GROUP_WITHHOLDING,
            category = "Salary",
            amount = 12_000.0,
            date = "2026-08-05",
            sourceOrPayer = "Acme Corp",
            referenceNumber = "CPR-WHT-001",
            proofNote = "Section 149 Slip"
        )
        taxRepository.saveTaxEntry(
            taxYear = 2027,
            entryGroup = TAX_GROUP_DIRECT_TAX,
            category = "Advance Tax",
            amount = 8_000.0,
            date = "2027-06-15",
            sourceOrPayer = "FBR e-Payment",
            referenceNumber = "CPR-998877",
            proofNote = "Paid via HBL"
        )
        taxRepository.saveTaxEntry(
            taxYear = 2027,
            entryGroup = TAX_GROUP_DEDUCTION,
            category = "Zakat",
            amount = 10_000.0,
            date = "2027-03-20",
            sourceOrPayer = "Foundation",
            referenceNumber = "REC-44",
            proofNote = "Zakat receipt"
        )

        // Verify manual tax entries did NOT touch normal transactions or bank balances!
        assertEquals(txCountBeforeTaxEntries, repository.transactions.first().size)
        val hblAfterTaxEntries = dao.getAllAccountsDirectForUser(taxRepository.resolveActiveUserId())
            .first { it.name == "HBL" }.currentBalance
        assertEquals(hblAfterNormalTx, hblAfterTaxEntries, 0.01)

        // 3. Add Udhaar and test Wealth Opening & Closing + Auto-fill
        val ublAcc = dao.getAllAccountsDirectForUser(taxRepository.resolveActiveUserId()).first { it.name == "UBL" }
        dao.updateAccount(ublAcc.copy(openingBalance = 50_000.0, currentBalance = 50_000.0))
        repository.addUdhaarRecord(
            type = "GIVEN",
            personName = "Bilal",
            category = "MONEY",
            amount = 25_000.0,
            accountName = "UBL",
            date = "2026-11-01",
            time = "11:00 AM"
        )

        taxRepository.saveWealthItem(
            taxYear = 2027,
            periodType = WEALTH_PERIOD_OPENING,
            assetCategory = WEALTH_CAT_PROPERTY,
            title = "Plot",
            amount = 1_000_000.0,
            isLiability = false
        )
        taxRepository.saveWealthItem(
            taxYear = 2027,
            periodType = WEALTH_PERIOD_CLOSING,
            assetCategory = WEALTH_CAT_PROPERTY,
            title = "Plot",
            amount = 1_000_000.0,
            isLiability = false
        )
        val syncedBanks = taxRepository.autoFillBankBalancesForWealth(2027, WEALTH_PERIOD_CLOSING)
        val syncedUdhaar = taxRepository.autoPullUdhaarForWealth(2027, WEALTH_PERIOD_CLOSING)
        assertTrue(syncedBanks >= 1)
        assertEquals(1, syncedUdhaar)

        // 4. Attach private document in app-private storage
        val doc = taxRepository.saveDocumentFromBytes(
            context = context,
            taxYear = 2027,
            title = "Salary Tax Cert",
            category = "Tax Certificate",
            mimeType = "application/pdf",
            originalFileName = "cert_2027.pdf",
            bytes = "%PDF-1.4 dummy".toByteArray()
        )
        assertTrue(File(doc.filePath).exists())
        assertTrue(doc.filePath.contains("tax_docs"))

        // 5. Build summary & check zero double-counting: 200,000 (linked tx) + 50,000 (manual) = 250,000
        taxRepository.saveTaxProfile(context, "Ali Raza", "35202-1234567-1", "1234567-8", "Filer (ATL Active)", "Salaried", "")
        val summary = taxRepository.buildYearSummary(
            taxYear = 2027,
            profile = taxRepository.taxProfile.first(),
            decryptedCnic = "35202-1234567-1",
            decryptedNtn = "1234567-8",
            manualEntries = taxRepository.taxEntries.first(),
            linkedTransactions = repository.transactions.first(),
            wealthItems = taxRepository.taxWealthItems.first(),
            documents = taxRepository.taxDocuments.first()
        )
        assertEquals(250_000.0, summary.totalIncome, 0.01)
        assertEquals(12_000.0, summary.totalWithholdingTax, 0.01)
        assertEquals(8_000.0, summary.totalDirectTaxPaid, 0.01)
        assertEquals(10_000.0, summary.totalDeductions, 0.01)
        assertEquals(15_000.0, summary.totalTaxExpenses, 0.01)
        assertTrue(summary.checklist.all { it.completed })
        assertEquals(1.0f, summary.checklistProgress, 0.001f)

        // 6. Exports: CSV, PDF, ZIP
        val tyEntity = taxRepository.taxYears.first().first { it.taxYear == 2027 }
        val merged = taxRepository.mergeEntriesAndLinkedTransactions(2027, taxRepository.taxEntries.first(), repository.transactions.first())
        val csv = TaxExportUtil.generateTaxCsvContent(
            tyEntity, taxRepository.taxProfile.first(), "35202-•••••••-1", "••••••7-8", summary,
            merged.filter { it.entryGroup == TAX_GROUP_INCOME },
            merged.filter { it.entryGroup == TAX_GROUP_WITHHOLDING },
            merged.filter { it.entryGroup == TAX_GROUP_DIRECT_TAX },
            merged.filter { it.entryGroup == TAX_GROUP_DEDUCTION },
            merged.filter { it.entryGroup == TAX_GROUP_EXPENSE },
            taxRepository.taxWealthItems.first()
        )
        assertTrue(csv.contains(TAX_DISCLAIMER_TEXT))
        assertTrue(csv.contains("35202-•••••••-1"))
        assertFalse(csv.contains("35202-1234567-1"))
        assertTrue(csv.contains("250000"))

        val pdfFile = TaxExportUtil.writeTaxPdfFile(
            context, tyEntity, taxRepository.taxProfile.first(), "35202-•••••••-1", "••••••7-8", summary,
            merged.filter { it.entryGroup == TAX_GROUP_INCOME },
            merged.filter { it.entryGroup == TAX_GROUP_WITHHOLDING },
            merged.filter { it.entryGroup == TAX_GROUP_DIRECT_TAX },
            merged.filter { it.entryGroup == TAX_GROUP_DEDUCTION },
            merged.filter { it.entryGroup == TAX_GROUP_EXPENSE },
            taxRepository.taxWealthItems.first()
        )
        assertTrue(pdfFile.exists() && pdfFile.length() > 100L)

        val zipFile = TaxExportUtil.writeDocumentsZipFile(context, 2027, taxRepository.taxDocuments.first())
        assertTrue(zipFile.exists() && zipFile.length() > 50L)

        // 7. Encrypted Backup & Restore + Wrong passphrase error + Reset protection
        val encryptedBackup = taxRepository.createEncryptedBackupBytes(context, "MyTaxBackup#2027", 2027)
        assertTrue(encryptedBackup.isNotEmpty())
        try {
            TaxBackupManager.parseEncryptedBackup(context, encryptedBackup, "WrongPass")
            fail("Expected wrong passphrase to fail decryption")
        } catch (_: Exception) {}

        // Lock year enforces read-only
        taxRepository.toggleTaxYearLock(2027, true)
        try {
            taxRepository.saveTaxEntry(taxYear = 2027, entryGroup = TAX_GROUP_INCOME, category = "Salary", amount = 1000.0, date = "2026-11-01")
            fail("Locked tax year must reject writes")
        } catch (_: IllegalStateException) {}

        // Unlock restores editability
        taxRepository.toggleTaxYearLock(2027, false)
        taxRepository.saveTaxEntry(taxYear = 2027, entryGroup = TAX_GROUP_INCOME, category = "Bonus", amount = 1000.0, date = "2026-11-01")

        // Reset ledger with deleteTaxData = false MUST preserve Tax data
        repository.resetAllDataToZero(deleteAccounts = true, deleteTaxData = false)
        assertTrue(taxRepository.taxYears.first().isNotEmpty())
        assertTrue(taxRepository.taxEntries.first().isNotEmpty())

        // Delete tax data explicitly, then restore from encrypted .fptax backup
        val uid = taxRepository.resolveActiveUserId()
        taxRepository.clearAllTaxDataForUser(context, uid)
        assertTrue(taxRepository.taxYears.first().isEmpty())

        val restoredYears = taxRepository.restoreEncryptedBackupBytes(context, encryptedBackup, "MyTaxBackup#2027")
        assertEquals(1, restoredYears)
        assertEquals(1, taxRepository.taxYears.first().size)
        assertTrue(taxRepository.taxEntries.first().isNotEmpty())
        assertEquals(1, taxRepository.taxDocuments.first().size)
    }

    @Test
    fun testMigration10To11PreservesExistingDataAndCreatesPreMigrationBackup(): Unit = runBlocking {
        val migDbName = "finance_tax_mig_test_${System.currentTimeMillis()}.db"
        context.deleteDatabase(migDbName)

        val v10Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(migDbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `user_profile` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fullName` TEXT NOT NULL, `email` TEXT NOT NULL, `mobile` TEXT NOT NULL, `passwordHash` TEXT NOT NULL, `isEmailVerified` INTEGER NOT NULL, `verificationToken` TEXT NOT NULL, `verificationExpiry` INTEGER NOT NULL, `accountStatus` TEXT NOT NULL, `pinCode` TEXT NOT NULL, `isBiometricEnabled` INTEGER NOT NULL, `currencySymbol` TEXT NOT NULL, `isLoggedIn` INTEGER NOT NULL, `profileImageBase64` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `accounts` (`id` TEXT NOT NULL, `userId` INTEGER NOT NULL, `name` TEXT NOT NULL, `openingBalance` REAL NOT NULL, `currentBalance` REAL NOT NULL, `totalReceived` REAL NOT NULL, `totalTransferredOut` REAL NOT NULL, `totalTransferredIn` REAL NOT NULL, `colorHex` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `accountType` TEXT NOT NULL, `holderTitle` TEXT NOT NULL, `accountNumberLast4` TEXT NOT NULL, `note` TEXT NOT NULL, `isBuiltIn` INTEGER NOT NULL, `isHidden` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` INTEGER NOT NULL, `serialNumber` INTEGER NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL, `dayOfWeek` TEXT NOT NULL, `type` TEXT NOT NULL, `amount` REAL NOT NULL, `fromAccount` TEXT NOT NULL, `toAccount` TEXT NOT NULL, `category` TEXT NOT NULL, `comments` TEXT NOT NULL, `description` TEXT NOT NULL, `hblBalance` REAL NOT NULL, `ublBalance` REAL NOT NULL, `sadapayBalance` REAL NOT NULL, `easypaisaBalance` REAL NOT NULL, `jazzCashBalance` REAL NOT NULL, `mashreqBalance` REAL NOT NULL, `walletBalance` REAL NOT NULL, `totalAccountBalance` REAL NOT NULL, `petrolLiters` REAL NOT NULL, `petrolRate` REAL NOT NULL, `extraItem` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `petrol_expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` INTEGER NOT NULL, `serialNumber` INTEGER NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL, `amount` REAL NOT NULL, `account` TEXT NOT NULL, `description` TEXT NOT NULL, `comments` TEXT NOT NULL, `liters` REAL NOT NULL, `ratePerLiter` REAL NOT NULL, `transactionId` INTEGER)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `extra_expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` INTEGER NOT NULL, `serialNumber` INTEGER NOT NULL, `item` TEXT NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL, `amount` REAL NOT NULL, `account` TEXT NOT NULL, `comments` TEXT NOT NULL, `transactionId` INTEGER)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `salary_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` INTEGER NOT NULL, `salaryDate` TEXT NOT NULL, `grossSalary` REAL NOT NULL, `preExistingAmount` REAL NOT NULL, `actualSalary` REAL NOT NULL, `receivedAmount` REAL NOT NULL, `remainingAmount` REAL NOT NULL, `depositedAccount` TEXT NOT NULL, `comments` TEXT NOT NULL)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `loan_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` INTEGER NOT NULL, `date` TEXT NOT NULL, `personOrSource` TEXT NOT NULL, `loanAmount` REAL NOT NULL, `paidAmount` REAL NOT NULL, `remainingLoan` REAL NOT NULL, `description` TEXT NOT NULL, `status` TEXT NOT NULL)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `monthly_statements` (`id` TEXT NOT NULL, `userId` INTEGER NOT NULL, `year` INTEGER NOT NULL, `month` INTEGER NOT NULL, `monthName` TEXT NOT NULL, `openingBalance` REAL NOT NULL, `totalReceived` REAL NOT NULL, `totalExpenses` REAL NOT NULL, `totalPetrolExpenses` REAL NOT NULL, `totalExtraExpenses` REAL NOT NULL, `totalSalary` REAL NOT NULL, `preExistingAmount` REAL NOT NULL, `actualSalary` REAL NOT NULL, `grossSalary` REAL NOT NULL, `totalLoan` REAL NOT NULL, `monthlyClosingBalance` REAL NOT NULL, `hblClosing` REAL NOT NULL, `ublClosing` REAL NOT NULL, `sadapayClosing` REAL NOT NULL, `easypaisaClosing` REAL NOT NULL, `jazzCashClosing` REAL NOT NULL, `mashreqClosing` REAL NOT NULL, `walletClosing` REAL NOT NULL, `isLocked` INTEGER NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`id`))""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `categories` (`name` TEXT NOT NULL, `icon` TEXT NOT NULL, `colorHex` TEXT NOT NULL, PRIMARY KEY(`name`))""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `udhaar_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` INTEGER NOT NULL, `type` TEXT NOT NULL, `personName` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, `category` TEXT NOT NULL, `originalAmount` REAL NOT NULL, `remainingAmount` REAL NOT NULL, `itemDescription` TEXT NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL, `dayOfWeek` TEXT NOT NULL, `dueDate` TEXT NOT NULL, `note` TEXT NOT NULL, `status` TEXT NOT NULL, `isItemReturned` INTEGER NOT NULL, `accountName` TEXT NOT NULL, `transactionId` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `udhaar_repayments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` INTEGER NOT NULL, `udhaarId` INTEGER NOT NULL, `amount` REAL NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL, `dayOfWeek` TEXT NOT NULL, `note` TEXT NOT NULL, `accountName` TEXT NOT NULL, `transactionId` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`udhaarId`) REFERENCES `udhaar_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_udhaar_repayments_udhaarId` ON `udhaar_repayments` (`udhaarId`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `savings_pots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` INTEGER NOT NULL, `name` TEXT NOT NULL, `targetAmount` REAL, `savedAmount` REAL NOT NULL, `targetDate` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `savings_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` INTEGER NOT NULL, `potId` INTEGER NOT NULL, `type` TEXT NOT NULL, `amount` REAL NOT NULL, `accountName` TEXT NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL, `dayOfWeek` TEXT NOT NULL, `note` TEXT NOT NULL, `transactionId` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`potId`) REFERENCES `savings_pots`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_savings_entries_potId` ON `savings_entries` (`potId`)""")

                        db.execSQL("""INSERT INTO `user_profile` (`id`,`fullName`,`email`,`mobile`,`passwordHash`,`isEmailVerified`,`verificationToken`,`verificationExpiry`,`accountStatus`,`pinCode`,`isBiometricEnabled`,`currencySymbol`,`isLoggedIn`,`profileImageBase64`,`createdAt`) VALUES (1,'Existing User','user10@test.com','+923001234567','hash',1,'OK',999999,'Active','',0,'Rs.',1,'',1000)""")
                        db.execSQL("""INSERT INTO `accounts` (`id`,`userId`,`name`,`openingBalance`,`currentBalance`,`totalReceived`,`totalTransferredOut`,`totalTransferredIn`,`colorHex`,`orderIndex`,`accountType`,`holderTitle`,`accountNumberLast4`,`note`,`isBuiltIn`,`isHidden`) VALUES ('u1_hbl',1,'HBL',50000.0,62000.0,12000.0,0.0,0.0,'#006C50',1,'BANK','','','',0,0)""")
                        db.execSQL("""INSERT INTO `savings_pots` (`id`,`userId`,`name`,`targetAmount`,`savedAmount`,`targetDate`,`createdAt`,`updatedAt`) VALUES (1,1,'Emergency',100000.0,25000.0,'',1000,1000)""")
                        db.execSQL("""INSERT INTO `udhaar_records` (`id`,`userId`,`type`,`personName`,`phoneNumber`,`category`,`originalAmount`,`remainingAmount`,`itemDescription`,`date`,`time`,`dayOfWeek`,`dueDate`,`note`,`status`,`isItemReturned`,`accountName`,`transactionId`,`createdAt`,`updatedAt`) VALUES (1,1,'GIVEN','Hamza','','MONEY',5000.0,5000.0,'','2026-10-01','10:00 AM','Thursday','','','PENDING',0,'HBL',0,1000,1000)""")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        v10Helper.writableDatabase.close()
        v10Helper.close()

        val dbFile = context.getDatabasePath(migDbName)
        assertEquals(10, DatabaseBackupManager.readSqliteUserVersion(dbFile))
        val backupResult = DatabaseBackupManager.backupBeforeMigrationIfNeeded(context, migDbName, AppVersionConfig.DB_SCHEMA_VERSION)
        assertTrue(backupResult.backupCreated)
        assertTrue(File(backupResult.backupFilePath!!).exists())

        val upgradedDb = Room.databaseBuilder(context, FinanceDatabase::class.java, migDbName)
            .addMigrations(*FinanceDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        val migDao = upgradedDb.financeDao()
        assertEquals(1, migDao.getAllAccountsDirectForUser(1).size)
        assertEquals(62000.0, migDao.getAllAccountsDirectForUser(1).first().currentBalance, 0.001)
        assertEquals(25000.0, migDao.getAllSavingsPotsDirectForUser(1).first().savedAmount, 0.001)
        assertEquals(5000.0, migDao.getAllUdhaarRecordsDirectForUser(1).first().remainingAmount, 0.001)
        assertFalse(migDao.getLoggedInUser()!!.isTaxpayerEnabled)

        // Verify new tax tables are live after migration
        val newTaxDao = upgradedDb.taxDao()
        newTaxDao.insertTaxYear(
            TaxYearEntity(
                userId = 1,
                taxYear = 2027,
                label = PakistanTaxYearUtils.formatTaxYearLabel(2027),
                startDate = "2026-07-01",
                endDate = "2027-06-30"
            )
        )
        assertEquals(1, newTaxDao.getTaxYearsDirectForUser(1).size)

        upgradedDb.close()
        context.deleteDatabase(migDbName)
    }

    private fun seedDiskDbUserForUiTests() {
        val diskDb = FinanceDatabase.getDatabase(context)
        val diskRepo = FinanceRepository(diskDb.financeDao(), diskDb)
        runBlocking {
            diskRepo.initializeIfEmpty()
            val existing = diskDb.financeDao().getUserByEmail("filer@example.com")
            if (existing == null) {
                diskRepo.registerUser("Taxpayer User", "filer@example.com", "+923001112233", "SecretPass123")
                diskRepo.verifyEmail("VERIFIED")
            }
            diskRepo.loginUser("filer@example.com", "SecretPass123")
        }
    }

    @Test
    fun testSettingsToggleBottomNavAndTaxLockScreenUi() {
        seedDiskDbUserForUiTests()
        val vm = FinanceViewModel(ApplicationProvider.getApplicationContext())

        composeTestRule.setContent {
            MyApplicationTheme {
                val profile by vm.userProfile.collectAsState()
                val navItems = buildFinanceBottomNavItems(profile?.isTaxpayerEnabled == true)
                assertEquals(profile?.isTaxpayerEnabled == true, navItems.any { it.screen == AppScreen.TAX })

                androidx.compose.foundation.layout.Column {
                    SettingsScreen(viewModel = vm)
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("taxpayer_mode_switch"))
        composeTestRule.onNodeWithTag("taxpayer_mode_switch").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(3000) {
            vm.userProfile.value?.isTaxpayerEnabled == true
        }

        // Verify bottom nav builder now includes Tax
        assertTrue(buildFinanceBottomNavItems(true).any { it.screen == AppScreen.TAX })
        assertFalse(buildFinanceBottomNavItems(false).any { it.screen == AppScreen.TAX })
    }

    @Test
    fun testTaxVaultPasswordLockGateUnlocksAndShowsSummary() {
        seedDiskDbUserForUiTests()
        val vm = FinanceViewModel(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            MyApplicationTheme {
                var isUnlocked by remember { mutableStateOf(false) }
                TaxScreen(
                    viewModel = vm,
                    isTaxUnlocked = isUnlocked,
                    onUnlockTax = { isUnlocked = true },
                    onEditLinkedTransaction = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tax_lock_screen").assertIsDisplayed()

        // Wrong password keeps vault locked
        composeTestRule.onNodeWithTag("tax_unlock_password_input").performTextInput("WrongPass")
        composeTestRule.onNodeWithTag("tax_unlock_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tax_lock_screen").assertIsDisplayed()

        // Correct password unlocks Tax screen and displays disclaimer & checklist
        composeTestRule.onNodeWithTag("tax_unlock_password_input").performTextReplacement("SecretPass123")
        composeTestRule.onNodeWithTag("tax_unlock_button").performClick()
        composeTestRule.waitUntil(3000) {
            composeTestRule.onAllNodesWithTag("tax_main_screen").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("tax_main_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tax_disclaimer_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tax_checklist_card").assertIsDisplayed()
    }
}
