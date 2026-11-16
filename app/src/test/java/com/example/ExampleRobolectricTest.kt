package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Payments
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.config.AppVersionConfig
import com.example.data.local.AppChangelog
import com.example.data.local.FinanceDao
import com.example.data.local.FinanceDatabase
import com.example.data.local.PakistanBankDirectory
import com.example.data.model.ACCOUNT_TYPE_BANK
import com.example.data.model.ACCOUNT_TYPE_CASH
import com.example.data.model.ACCOUNT_TYPE_WALLET
import com.example.data.model.AccountDeleteMode
import com.example.data.model.AuthResult
import com.example.data.model.DELETED_ACCOUNT_LABEL
import com.example.data.model.UserProfileEntity
import com.example.data.model.maskAccountNumber
import com.example.data.repository.FinanceRepository
import com.example.ui.components.AccountFormDialog
import com.example.ui.components.ScrollableFinanceBottomBar
import com.example.ui.components.accountTypeBadgeTag
import com.example.ui.components.accountTypeIcon
import com.example.ui.components.financeBottomNavItems
import com.example.ui.navigation.AppScreen
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.AppUpdateChecker
import com.example.util.DatabaseBackupManager
import com.example.util.DateUtils
import com.example.util.SecurityUtils
import com.example.util.UpdateCheckResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: FinanceDatabase
    private lateinit var dao: FinanceDao
    private lateinit var repository: FinanceRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.financeDao()
        repository = FinanceRepository(dao, database)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testCurrencyZeroFormatting() {
        val formatted = DateUtils.formatCurrency(0.0)
        assertEquals("Rs. 0", formatted)
    }

    @Test
    fun testSalaryCalculationFormula() {
        val grossSalary = 200000.0
        val preExisting = 15000.0
        val actualSalary = grossSalary - preExisting
        assertEquals(185000.0, actualSalary, 0.001)
    }

    @Test
    fun testFullAuthFlowRegisterLoginUpdateProfileChangeAndReset() = runBlocking {
        val rawPassword = "UserPass123"
        val email = "AbdulQuddus7745@Gmail.COM "
        val cleanEmail = SecurityUtils.normalizeEmail(email)

        // 1. Create Account / Register
        val token = repository.registerUser(
            fullName = "Abdul Quddus",
            email = email,
            mobile = "+92 300 1234567",
            rawPassword = rawPassword
        )
        assertNotNull(token)
        assertEquals(8, token.length)

        // Verify hash stored in SQLite: exactly 64-character SHA-256
        val storedUser = dao.getUserByEmail(cleanEmail)
        assertNotNull(storedUser)
        assertEquals(64, storedUser!!.passwordHash.length)
        assertEquals(SecurityUtils.hashPassword(rawPassword), storedUser.passwordHash)

        // 2. Verify Email
        val verified = repository.verifyEmail(token)
        assertTrue(verified)

        // 3. Login with Correct Credentials
        val loginResult = repository.loginUser(email, rawPassword)
        assertTrue("Login must succeed with correct credentials", loginResult is AuthResult.Success)
        val profile = (loginResult as AuthResult.Success).profile
        assertEquals(cleanEmail, profile.email)
        assertTrue(profile.isLoggedIn)

        // 4. Update Profile (name, email, mobile, photo) - MUST NEVER TOUCH PASSWORD HASH
        val oldHash = profile.passwordHash
        repository.updateProfileDetails(
            fullName = "Abdul Quddus Updated",
            email = cleanEmail,
            mobile = "+92 333 7654321",
            profileImageBase64 = "sampleBase64ImageData"
        )
        val userAfterProfileUpdate = dao.getUserByEmail(cleanEmail)
        assertNotNull(userAfterProfileUpdate)
        assertEquals("Abdul Quddus Updated", userAfterProfileUpdate!!.fullName)
        assertEquals("+92 333 7654321", userAfterProfileUpdate.mobile)
        assertEquals("sampleBase64ImageData", userAfterProfileUpdate.profileImageBase64)
        assertEquals("Password hash MUST remain unchanged after profile update", oldHash, userAfterProfileUpdate.passwordHash)

        // 5. Change Password (verifies old, hashes new once)
        val changeResult = repository.changePassword("UserPass123", "NewSecret456")
        assertTrue(changeResult.first)
        val userAfterPassChange = dao.getUserByEmail(cleanEmail)
        assertNotNull(userAfterPassChange)
        assertEquals(SecurityUtils.hashPassword("NewSecret456"), userAfterPassChange!!.passwordHash)

        // Login with new password succeeds
        val newLoginResult = repository.loginUser(cleanEmail, "NewSecret456")
        assertTrue(newLoginResult is AuthResult.Success)

        // Login with old password fails with specific message
        val oldLoginResult = repository.loginUser(cleanEmail, "UserPass123")
        assertTrue(oldLoginResult is AuthResult.Error)
        assertEquals("Incorrect password. Please try again or use Forgot Password.", (oldLoginResult as AuthResult.Error).message)

        // 6. Reset Password (Forgot Password)
        val resetSuccess = repository.resetPassword(cleanEmail, "ResetFinal789")
        assertTrue(resetSuccess)

        // Login with reset password succeeds
        val resetLoginResult = repository.loginUser(cleanEmail, "ResetFinal789")
        assertTrue(resetLoginResult is AuthResult.Success)
    }

    @Test
    fun testLegacyAccountAutoMigrationOnSuccessfulLogin() = runBlocking {
        val email = "legacyuser@example.com"
        val plainPassword = "plain123456"

        // Insert legacy user with raw plain-text password
        dao.insertUserProfile(
            UserProfileEntity(
                id = 99,
                fullName = "Legacy User",
                email = email,
                mobile = "03001122334",
                passwordHash = plainPassword, // Stored as plain text (length 11)
                isEmailVerified = true,
                accountStatus = "Active"
            )
        )

        // User logs in with plain text password
        val result = repository.loginUser(email, plainPassword)
        assertTrue(result is AuthResult.Success)

        // Verify that database was safely migrated to 64-char SHA-256 hash
        val migratedUser = dao.getUserByEmail(email)
        assertNotNull(migratedUser)
        assertEquals(64, migratedUser!!.passwordHash.length)
        assertEquals(SecurityUtils.hashPassword(plainPassword), migratedUser.passwordHash)
    }

    @Test
    fun testTransactionDayOfWeekAnd12HourTimeFlow() = runBlocking {
        repository.seedLegacyDefaultAccountsForTest()
        // 1. Add transaction on 2026-11-07 with 12-hour time "04:45 PM"
        val txId = repository.addTransaction(
            date = "2026-11-07",
            time = "04:45 PM",
            type = "EXPENSE",
            amount = 1500.0,
            fromAccount = "HBL",
            toAccount = "Supermarket",
            category = "Groceries",
            comments = "Weekly Groceries"
        )
        val insertedTx = dao.getTransactionById(txId)
        assertNotNull(insertedTx)
        assertEquals("Saturday", insertedTx!!.dayOfWeek)
        // Stored in 24-hour format
        assertEquals("16:45:00", insertedTx.time)
        // Displayed in 12-hour format
        assertEquals("04:45 PM", DateUtils.format12HourTime(insertedTx.time))
        assertEquals("Sat, 07 Nov 2026", DateUtils.formatReadableDateWithDay(insertedTx.date, insertedTx.dayOfWeek))

        // 2. Update transaction to a different date (Monday 2026-11-02) and midnight (12:00 AM)
        repository.updateTransaction(
            oldTx = insertedTx,
            newDate = "2026-11-02",
            newTime = "12:00 AM",
            newType = "EXPENSE",
            newAmount = 2000.0,
            newFromAccount = "HBL",
            newToAccount = "Supermarket",
            newCategory = "Groceries"
        )
        val updatedTx = dao.getTransactionById(txId)
        assertNotNull(updatedTx)
        assertEquals("Monday", updatedTx!!.dayOfWeek)
        assertEquals("00:00:00", updatedTx.time)
        assertEquals("12:00 AM", DateUtils.format12HourTime(updatedTx.time))
        assertEquals("Mon, 02 Nov 2026", DateUtils.formatReadableDateWithDay(updatedTx.date, updatedTx.dayOfWeek))
    }

    @Test
    fun testTransferAppearsInTransactionListAndUpdatesBalances() = runBlocking {
        repository.seedLegacyDefaultAccountsForTest()

        val accountsBefore = dao.getAllAccountsDirect().associateBy { it.name }
        val hblBefore = accountsBefore["HBL"]?.currentBalance ?: 0.0
        val jazzBefore = accountsBefore["JazzCash"]?.currentBalance ?: 0.0

        val transferAmount = 7500.0
        val txId = repository.addTransaction(
            date = "2026-11-08",
            time = "02:15 PM",
            type = "TRANSFER",
            amount = transferAmount,
            fromAccount = "HBL",
            toAccount = "JazzCash",
            category = "Transfer",
            comments = "Bank to wallet transfer",
            description = "Transfer: HBL -> JazzCash"
        )

        val tx = dao.getTransactionById(txId)
        assertNotNull(tx)
        assertEquals("TRANSFER", tx!!.type)
        assertEquals("HBL", tx.fromAccount)
        assertEquals("JazzCash", tx.toAccount)
        assertEquals("Transfer: HBL -> JazzCash", tx.description)
        assertEquals(transferAmount, tx.amount, 0.001)

        val accountsAfter = dao.getAllAccountsDirect().associateBy { it.name }
        val hblAfter = accountsAfter["HBL"]?.currentBalance ?: 0.0
        val jazzAfter = accountsAfter["JazzCash"]?.currentBalance ?: 0.0
        assertEquals(hblBefore - transferAmount, hblAfter, 0.001)
        assertEquals(jazzBefore + transferAmount, jazzAfter, 0.001)
    }

    @Test
    fun testAccountTypeExplicitClassification() = runBlocking {
        repository.seedLegacyDefaultAccountsForTest()

        val accounts = dao.getAllAccountsDirect().associateBy { it.name }
        val mashreq = accounts["Mashreq"]
        val hbl = accounts["HBL"]
        val ubl = accounts["UBL"]
        val sadapay = accounts["Sadapay"]
        val easypaisa = accounts["Easypaisa"]
        val jazzcash = accounts["JazzCash"]

        assertNotNull(mashreq)
        assertNotNull(hbl)
        assertNotNull(ubl)
        assertNotNull(sadapay)
        assertNotNull(easypaisa)
        assertNotNull(jazzcash)

        // Mashreq, HBL, UBL must be BANK (not wallet)
        assertEquals("BANK", mashreq!!.accountType)
        assertFalse(com.example.data.model.isWalletAccount(mashreq))

        assertEquals("BANK", hbl!!.accountType)
        assertFalse(com.example.data.model.isWalletAccount(hbl))

        assertEquals("BANK", ubl!!.accountType)
        assertFalse(com.example.data.model.isWalletAccount(ubl))

        // Wallets must be WALLET
        assertEquals("WALLET", sadapay!!.accountType)
        assertTrue(com.example.data.model.isWalletAccount(sadapay))

        assertEquals("WALLET", easypaisa!!.accountType)
        assertTrue(com.example.data.model.isWalletAccount(easypaisa))

        assertEquals("WALLET", jazzcash!!.accountType)
        assertTrue(com.example.data.model.isWalletAccount(jazzcash))
    }

    @Test
    fun testUdhaarGivenFromHBLDecreasesBalance() = runBlocking {
        repository.seedLegacyDefaultAccountsForTest()

        val hblAcc = dao.getAllAccountsDirect().find { it.name == "HBL" }!!
        dao.updateAccount(hblAcc.copy(currentBalance = 50000.0))
        val hblBefore = 50000.0

        val loanAmount = 5000.0
        val recordId = repository.addUdhaarRecord(
            type = "GIVEN",
            personName = "Ali Khan",
            phoneNumber = "03001234567",
            category = "MONEY",
            amount = loanAmount,
            accountName = "HBL",
            note = "Emergency loan"
        )

        val record = dao.getUdhaarRecordById(recordId)
        assertNotNull(record)
        assertEquals("GIVEN", record!!.type)
        assertEquals("HBL", record.accountName)
        assertEquals(loanAmount, record.originalAmount, 0.001)
        assertEquals(loanAmount, record.remainingAmount, 0.001)
        assertEquals("PENDING", record.status)
        assertTrue(record.transactionId > 0)

        // HBL balance must decrease by loanAmount
        val accountsAfter = dao.getAllAccountsDirect().associateBy { it.name }
        val hblAfter = accountsAfter["HBL"]?.currentBalance ?: 0.0
        assertEquals(hblBefore - loanAmount, hblAfter, 0.001)

        // Linked transaction check
        val tx = dao.getTransactionById(record.transactionId)
        assertNotNull(tx)
        assertEquals("UDHAAR", tx!!.type)
        assertEquals("HBL", tx.fromAccount)
        assertEquals("Ali Khan", tx.toAccount)
        assertEquals(loanAmount, tx.amount, 0.001)
        assertEquals("Udhaar Given: Ali Khan", tx.description)
    }

    @Test
    fun testUdhaarTakenIntoJazzCashIncreasesBalance() = runBlocking {
        repository.seedLegacyDefaultAccountsForTest()

        val accountsBefore = dao.getAllAccountsDirect().associateBy { it.name }
        val jazzBefore = accountsBefore["JazzCash"]?.currentBalance ?: 0.0

        val borrowedAmount = 8000.0
        val recordId = repository.addUdhaarRecord(
            type = "TAKEN",
            personName = "Ahmed Raza",
            category = "MONEY",
            amount = borrowedAmount,
            accountName = "JazzCash"
        )

        val record = dao.getUdhaarRecordById(recordId)
        assertNotNull(record)
        assertEquals("TAKEN", record!!.type)
        assertEquals("JazzCash", record.accountName)
        assertEquals(borrowedAmount, record.originalAmount, 0.001)

        // JazzCash balance must increase by borrowedAmount
        val accountsAfter = dao.getAllAccountsDirect().associateBy { it.name }
        val jazzAfter = accountsAfter["JazzCash"]?.currentBalance ?: 0.0
        assertEquals(jazzBefore + borrowedAmount, jazzAfter, 0.001)

        // Linked transaction check
        val tx = dao.getTransactionById(record.transactionId)
        assertNotNull(tx)
        assertEquals("UDHAAR", tx!!.type)
        assertEquals("Ahmed Raza", tx.fromAccount)
        assertEquals("JazzCash", tx.toAccount)
        assertEquals(borrowedAmount, tx.amount, 0.001)
        assertEquals("Udhaar Taken: Ahmed Raza", tx.description)
    }

    @Test
    fun testUdhaarRepaymentsBothWaysAndSettlement() = runBlocking {
        repository.seedLegacyDefaultAccountsForTest()

        val hblAcc = dao.getAllAccountsDirect().find { it.name == "HBL" }!!
        dao.updateAccount(hblAcc.copy(currentBalance = 50000.0))
        val hblStart = 50000.0

        val givenId = repository.addUdhaarRecord(
            type = "GIVEN",
            personName = "Usman",
            category = "MONEY",
            amount = 10000.0,
            accountName = "HBL"
        )
        val hblAfterGiven = dao.getAllAccountsDirect().find { it.name == "HBL" }!!.currentBalance
        assertEquals(hblStart - 10000.0, hblAfterGiven, 0.001)

        // Partial repayment of 4,000 received into HBL
        val rep1Id = repository.addUdhaarRepayment(
            udhaarId = givenId,
            amount = 4000.0,
            accountName = "HBL"
        )
        assertTrue(rep1Id > 0)
        val hblAfterRep1 = dao.getAllAccountsDirect().find { it.name == "HBL" }!!.currentBalance
        assertEquals(hblAfterGiven + 4000.0, hblAfterRep1, 0.001)

        val givenRecordPartial = dao.getUdhaarRecordById(givenId)!!
        assertEquals(6000.0, givenRecordPartial.remainingAmount, 0.001)
        assertEquals("PARTIAL", givenRecordPartial.status)

        // Second repayment of 6,000 received into HBL -> Full settlement
        repository.addUdhaarRepayment(
            udhaarId = givenId,
            amount = 6000.0,
            accountName = "HBL"
        )
        val hblFinal = dao.getAllAccountsDirect().find { it.name == "HBL" }!!.currentBalance
        assertEquals(hblStart, hblFinal, 0.001)

        val givenRecordSettled = dao.getUdhaarRecordById(givenId)!!
        assertEquals(0.0, givenRecordSettled.remainingAmount, 0.001)
        assertEquals("SETTLED", givenRecordSettled.status)

        // 2. Taken 5,000 into Sadapay
        val sadaStart = dao.getAllAccountsDirect().find { it.name == "Sadapay" }!!.currentBalance
        val takenId = repository.addUdhaarRecord(
            type = "TAKEN",
            personName = "Tariq",
            category = "MONEY",
            amount = 5000.0,
            accountName = "Sadapay"
        )
        val sadaAfterTaken = dao.getAllAccountsDirect().find { it.name == "Sadapay" }!!.currentBalance
        assertEquals(sadaStart + 5000.0, sadaAfterTaken, 0.001)

        // Repayment paid back 5,000 from Sadapay
        repository.addUdhaarRepayment(
            udhaarId = takenId,
            amount = 5000.0,
            accountName = "Sadapay"
        )
        val sadaFinal = dao.getAllAccountsDirect().find { it.name == "Sadapay" }!!.currentBalance
        assertEquals(sadaStart, sadaFinal, 0.001)

        val takenRecordSettled = dao.getUdhaarRecordById(takenId)!!
        assertEquals(0.0, takenRecordSettled.remainingAmount, 0.001)
        assertEquals("SETTLED", takenRecordSettled.status)
    }

    @Test
    fun testUdhaarInsufficientBalanceBlocksTransaction() = runBlocking {
        repository.seedLegacyDefaultAccountsForTest()

        val hbl = dao.getAllAccountsDirect().find { it.name == "HBL" }!!
        val hugeAmount = hbl.currentBalance + 500000.0

        try {
            repository.addUdhaarRecord(
                type = "GIVEN",
                personName = "Borrower",
                category = "MONEY",
                amount = hugeAmount,
                accountName = "HBL"
            )
            fail("Must throw IllegalStateException due to insufficient balance")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Insufficient balance in HBL", ignoreCase = true) == true)
        }
    }

    @Test
    fun testUdhaarEditReversesOldAndAppliesNewBalance() = runBlocking {
        repository.seedLegacyDefaultAccountsForTest()

        val hblAcc = dao.getAllAccountsDirect().find { it.name == "HBL" }!!
        dao.updateAccount(hblAcc.copy(currentBalance = 50000.0))
        val ublAcc = dao.getAllAccountsDirect().find { it.name == "UBL" }!!
        dao.updateAccount(ublAcc.copy(currentBalance = 50000.0))

        val hblBefore = 50000.0
        val ublBefore = 50000.0

        // 1. Create Given Rs 5,000 on HBL
        val recordId = repository.addUdhaarRecord(
            type = "GIVEN",
            personName = "Zaid",
            category = "MONEY",
            amount = 5000.0,
            accountName = "HBL"
        )
        val hblMid = dao.getAllAccountsDirect().find { it.name == "HBL" }!!.currentBalance
        assertEquals(hblBefore - 5000.0, hblMid, 0.001)

        // 2. Edit to UBL Rs 3,000
        repository.updateUdhaarRecord(
            id = recordId,
            type = "GIVEN",
            personName = "Zaid",
            category = "MONEY",
            amount = 3000.0,
            accountName = "UBL",
            date = "2026-11-09",
            time = "10:00:00"
        )

        // HBL balance must be restored (+5,000)
        val hblAfter = dao.getAllAccountsDirect().find { it.name == "HBL" }!!.currentBalance
        assertEquals(hblBefore, hblAfter, 0.001)

        // UBL balance must be debited by 3,000
        val ublAfter = dao.getAllAccountsDirect().find { it.name == "UBL" }!!.currentBalance
        assertEquals(ublBefore - 3000.0, ublAfter, 0.001)

        val updatedRecord = dao.getUdhaarRecordById(recordId)!!
        assertEquals("UBL", updatedRecord.accountName)
        assertEquals(3000.0, updatedRecord.originalAmount, 0.001)
    }

    @Test
    fun testUdhaarDeleteRestoresAccountBalances() = runBlocking {
        repository.seedLegacyDefaultAccountsForTest()

        val hblAcc = dao.getAllAccountsDirect().find { it.name == "HBL" }!!
        dao.updateAccount(hblAcc.copy(currentBalance = 50000.0))
        val hblBefore = 50000.0

        val recordId = repository.addUdhaarRecord(
            type = "GIVEN",
            personName = "Bilal",
            category = "MONEY",
            amount = 6000.0,
            accountName = "HBL"
        )

        // Repayment of 2,000 received
        val repId = repository.addUdhaarRepayment(
            udhaarId = recordId,
            amount = 2000.0,
            accountName = "HBL"
        )

        // Net change so far: -4,000
        val hblMid = dao.getAllAccountsDirect().find { it.name == "HBL" }!!.currentBalance
        assertEquals(hblBefore - 4000.0, hblMid, 0.001)

        // Delete repayment only
        repository.deleteUdhaarRepayment(repaymentId = repId, udhaarId = recordId)
        val hblAfterRepDel = dao.getAllAccountsDirect().find { it.name == "HBL" }!!.currentBalance
        assertEquals(hblBefore - 6000.0, hblAfterRepDel, 0.001)

        // Delete entire record
        repository.deleteUdhaarRecord(recordId)
        val hblFinal = dao.getAllAccountsDirect().find { it.name == "HBL" }!!.currentBalance
        assertEquals(hblBefore, hblFinal, 0.001)

        assertNull(dao.getUdhaarRecordById(recordId))
    }

    @Test
    fun testUdhaarItemRecordNeverTouchesBalances() = runBlocking {
        repository.seedLegacyDefaultAccountsForTest()

        val totalBalBefore = dao.getAllAccountsDirect().sumOf { it.currentBalance }

        val itemId = repository.addUdhaarRecord(
            type = "GIVEN",
            personName = "Hamza",
            category = "ITEM",
            itemDescription = "MacBook Pro",
            accountName = ""
        )

        val totalBalAfter = dao.getAllAccountsDirect().sumOf { it.currentBalance }
        assertEquals(totalBalBefore, totalBalAfter, 0.001)

        val itemRecord = dao.getUdhaarRecordById(itemId)!!
        assertEquals("ITEM", itemRecord.category)
        assertEquals("MacBook Pro", itemRecord.itemDescription)
        assertEquals("", itemRecord.accountName)
        assertEquals(0L, itemRecord.transactionId)

        // Mark returned
        repository.markItemReturned(itemId, true)
        val returnedRecord = dao.getUdhaarRecordById(itemId)!!
        assertTrue(returnedRecord.isItemReturned)
        assertEquals("SETTLED", returnedRecord.status)
        assertEquals(totalBalBefore, dao.getAllAccountsDirect().sumOf { it.currentBalance }, 0.001)
    }

    @Test
    fun testUdhaarUnlinkedMigration() = runBlocking {
        repository.seedLegacyDefaultAccountsForTest()

        val hblAcc = dao.getAllAccountsDirect().find { it.name == "HBL" }!!
        dao.updateAccount(hblAcc.copy(currentBalance = 50000.0))
        val hblBefore = 50000.0

        // Insert legacy unlinked record with accountName = "" and transactionId = 0
        val unlinked = com.example.data.model.UdhaarRecordEntity(
            type = "GIVEN",
            personName = "Old Record",
            category = "MONEY",
            originalAmount = 3500.0,
            remainingAmount = 3500.0,
            accountName = "",
            transactionId = 0L,
            date = "2026-11-01",
            time = "12:00:00"
        )
        val legacyId = dao.insertUdhaarRecord(unlinked)

        // Balance should NOT have changed yet
        assertEquals(hblBefore, dao.getAllAccountsDirect().find { it.name == "HBL" }!!.currentBalance, 0.001)

        // When user edits and chooses HBL, balance is applied once
        repository.updateUdhaarRecord(
            id = legacyId,
            type = "GIVEN",
            personName = "Old Record",
            category = "MONEY",
            amount = 3500.0,
            accountName = "HBL",
            date = "2026-11-01",
            time = "12:00:00"
        )

        val hblAfter = dao.getAllAccountsDirect().find { it.name == "HBL" }!!.currentBalance
        assertEquals(hblBefore - 3500.0, hblAfter, 0.001)

        val updated = dao.getUdhaarRecordById(legacyId)!!
        assertEquals("HBL", updated.accountName)
        assertTrue(updated.transactionId > 0)
    }

    private suspend fun assertAllAccountBalancesMatchHistory() {
        val accounts = dao.getAllAccountsDirect()
        val txs = dao.getAllTransactions().first()
        for (acc in accounts) {
            var expected = acc.openingBalance
            for (tx in txs) {
                when (tx.type) {
                    "INCOME" -> if (tx.toAccount.equals(acc.name, ignoreCase = true)) expected += tx.amount
                    "EXPENSE", "PETROL", "EXTRA_EXPENSE" -> if (tx.fromAccount.equals(acc.name, ignoreCase = true)) expected -= tx.amount
                    "TRANSFER", "UDHAAR", "SAVINGS" -> {
                        if (tx.fromAccount.equals(acc.name, ignoreCase = true)) expected -= tx.amount
                        if (tx.toAccount.equals(acc.name, ignoreCase = true)) expected += tx.amount
                    }
                }
            }
            assertEquals("Account ${acc.name} balance mismatch vs tx history", expected, acc.currentBalance, 0.001)
        }
    }

    @Test
    fun testSavingsPotCreateRenameDeleteAndProgress() = runBlocking {
        repository.seedLegacyDefaultAccountsForTest()
        val hblAcc = dao.getAllAccountsDirect().find { it.name == "HBL" }!!
        dao.updateAccount(hblAcc.copy(openingBalance = 50000.0, currentBalance = 50000.0))

        // 1. Create "Emergency" pot with target 20,000 and targetDate
        val potId = repository.createSavingsPot(
            name = "Emergency",
            targetAmount = 20000.0,
            targetDate = "2027-06-30"
        )
        var pot = dao.getSavingsPotById(potId)
        assertNotNull(pot)
        assertEquals("Emergency", pot!!.name)
        assertEquals(20000.0, pot.targetAmount!!, 0.001)
        assertEquals(0.0, pot.savedAmount, 0.001)
        assertEquals("2027-06-30", pot.targetDate)

        // Deposit 5,000 from HBL -> 25% progress, 15,000 remaining
        repository.addSavingsEntry(
            potId = potId,
            type = "DEPOSIT",
            amount = 5000.0,
            accountName = "HBL",
            date = "2026-11-10",
            time = "03:30 PM",
            note = "First savings deposit"
        )
        pot = dao.getSavingsPotById(potId)!!
        assertEquals(5000.0, pot.savedAmount, 0.001)
        val progress = (pot.savedAmount / pot.targetAmount!!).toFloat()
        val percent = ((pot.savedAmount / pot.targetAmount!!) * 100.0).toInt()
        val remaining = (pot.targetAmount!! - pot.savedAmount).coerceAtLeast(0.0)
        assertEquals(0.25f, progress, 0.001f)
        assertEquals(25, percent)
        assertEquals(15000.0, remaining, 0.001)

        // 2. Rename pot to "Emergency Fund" -> linked tx updates cleanly
        repository.updateSavingsPot(
            id = potId,
            name = "Emergency Fund",
            targetAmount = 25000.0,
            targetDate = "2027-12-31"
        )
        val renamedPot = dao.getSavingsPotById(potId)!!
        assertEquals("Emergency Fund", renamedPot.name)
        assertEquals(25000.0, renamedPot.targetAmount!!, 0.001)

        val entries = dao.getSavingsEntriesForPotDirect(potId)
        assertEquals(1, entries.size)
        val linkedTx = dao.getTransactionById(entries[0].transactionId)!!
        assertEquals("SAVINGS", linkedTx.type)
        assertEquals("Emergency Fund", linkedTx.toAccount)
        assertEquals("Saved to Emergency Fund", linkedTx.description)

        // 3. Delete pot -> restores HBL back to 50,000 and removes tx/entries
        repository.deleteSavingsPot(potId)
        assertNull(dao.getSavingsPotById(potId))
        assertTrue(dao.getSavingsEntriesForPotDirect(potId).isEmpty())
        assertNull(dao.getTransactionById(entries[0].transactionId))
        val hblRestored = dao.getAllAccountsDirect().find { it.name == "HBL" }!!
        assertEquals(50000.0, hblRestored.currentBalance, 0.001)
        assertAllAccountBalancesMatchHistory()
    }

    @Test
    fun testSavingsDepositAndWithdrawUpdatesAccountBalancesAtomicallyAndExcludesFromTotals() = runBlocking {
        repository.seedLegacyDefaultAccountsForTest()
        val hblAcc = dao.getAllAccountsDirect().find { it.name == "HBL" }!!
        dao.updateAccount(hblAcc.copy(openingBalance = 100000.0, currentBalance = 100000.0))

        // Add income of 20,000 into HBL so HBL = 120,000 and totalIncome = 20,000
        repository.addTransaction(
            date = "2026-11-05",
            time = "10:00 AM",
            type = "INCOME",
            amount = 20000.0,
            fromAccount = "Client",
            toAccount = "HBL",
            category = "Salary"
        )

        val totalSpendableBeforeSavings = dao.getAllAccountsDirect().sumOf { it.currentBalance }
        assertEquals(120000.0, totalSpendableBeforeSavings, 0.001)

        // Create Hajj pot & deposit 30,000 from HBL
        val hajjId = repository.createSavingsPot(name = "Hajj", targetAmount = 300000.0)
        repository.addSavingsEntry(
            potId = hajjId,
            type = "DEPOSIT",
            amount = 30000.0,
            accountName = "HBL",
            date = "2026-11-10",
            time = "04:45 PM",
            note = "Monthly Hajj savings"
        )

        val hblAfterDeposit = dao.getAllAccountsDirect().find { it.name == "HBL" }!!.currentBalance
        assertEquals(90000.0, hblAfterDeposit, 0.001)
        val totalSpendableAfterDeposit = dao.getAllAccountsDirect().sumOf { it.currentBalance }
        // Spendable account total MUST exclude savings (down from 120,000 to 90,000)
        assertEquals(90000.0, totalSpendableAfterDeposit, 0.001)
        val totalSavingsAfterDeposit = dao.getAllSavingsPotsDirect().sumOf { it.savedAmount }
        assertEquals(30000.0, totalSavingsAfterDeposit, 0.001)

        // Check Income/Expense/Monthly statements NEVER include SAVINGS
        repository.recalculateMonthlyStatement("2026-11")
        val statement = dao.getMonthlyStatementById("2026-11")!!
        assertEquals(20000.0, statement.totalReceived, 0.001)
        assertEquals(0.0, statement.totalExpenses, 0.001)

        // Withdraw 10,000 back into HBL
        repository.addSavingsEntry(
            potId = hajjId,
            type = "WITHDRAW",
            amount = 10000.0,
            accountName = "HBL",
            date = "2026-11-11",
            time = "11:00 AM",
            note = "Emergency partial withdrawal"
        )

        val hblAfterWithdraw = dao.getAllAccountsDirect().find { it.name == "HBL" }!!.currentBalance
        assertEquals(100000.0, hblAfterWithdraw, 0.001)
        assertEquals(20000.0, dao.getSavingsPotById(hajjId)!!.savedAmount, 0.001)
        assertAllAccountBalancesMatchHistory()
    }

    @Test
    fun testSavingsInsufficientAccountAndPotBalanceBlocked() = runBlocking {
        repository.seedLegacyDefaultAccountsForTest()
        val hblAcc = dao.getAllAccountsDirect().find { it.name == "HBL" }!!
        dao.updateAccount(hblAcc.copy(openingBalance = 5000.0, currentBalance = 5000.0))

        val carPotId = repository.createSavingsPot(name = "Car", targetAmount = 100000.0)

        // 1. Deposit exceeding HBL balance must fail & leave DB untouched
        try {
            repository.addSavingsEntry(
                potId = carPotId,
                type = "DEPOSIT",
                amount = 6000.0,
                accountName = "HBL"
            )
            fail("Expected IllegalStateException when depositing more than HBL balance")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Insufficient balance in HBL", ignoreCase = true) == true)
        }
        assertEquals(5000.0, dao.getAllAccountsDirect().find { it.name == "HBL" }!!.currentBalance, 0.001)
        assertEquals(0.0, dao.getSavingsPotById(carPotId)!!.savedAmount, 0.001)
        assertTrue(dao.getAllTransactions().first().none { it.type == "SAVINGS" })

        // 2. Deposit valid 3,000 then try withdrawing 4,000 (> 3,000 in pot) -> must fail cleanly
        repository.addSavingsEntry(
            potId = carPotId,
            type = "DEPOSIT",
            amount = 3000.0,
            accountName = "HBL"
        )
        try {
            repository.addSavingsEntry(
                potId = carPotId,
                type = "WITHDRAW",
                amount = 4000.0,
                accountName = "HBL"
            )
            fail("Expected IllegalStateException when withdrawing more than pot savedAmount")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Insufficient savings in Car", ignoreCase = true) == true)
        }
        assertEquals(2000.0, dao.getAllAccountsDirect().find { it.name == "HBL" }!!.currentBalance, 0.001)
        assertEquals(3000.0, dao.getSavingsPotById(carPotId)!!.savedAmount, 0.001)
        assertAllAccountBalancesMatchHistory()
    }

    @Test
    fun testSavingsEditAndDeleteEntryReversesAndRecalculates() = runBlocking {
        repository.seedLegacyDefaultAccountsForTest()
        val hblAcc = dao.getAllAccountsDirect().find { it.name == "HBL" }!!
        dao.updateAccount(hblAcc.copy(openingBalance = 40000.0, currentBalance = 40000.0))
        val ublAcc = dao.getAllAccountsDirect().find { it.name == "UBL" }!!
        dao.updateAccount(ublAcc.copy(openingBalance = 25000.0, currentBalance = 25000.0))

        val potId = repository.createSavingsPot(name = "Emergency", targetAmount = 50000.0)
        val entryId = repository.addSavingsEntry(
            potId = potId,
            type = "DEPOSIT",
            amount = 10000.0,
            accountName = "HBL",
            date = "2026-11-12",
            time = "09:15 AM",
            note = "Initial deposit"
        )
        assertEquals(30000.0, dao.getAllAccountsDirect().find { it.name == "HBL" }!!.currentBalance, 0.001)
        assertEquals(10000.0, dao.getSavingsPotById(potId)!!.savedAmount, 0.001)

        // Edit: change account from HBL -> UBL and amount from 10,000 -> 15,000
        repository.updateSavingsEntry(
            entryId = entryId,
            newPotId = potId,
            newType = "DEPOSIT",
            newAmount = 15000.0,
            newAccountName = "UBL",
            newDate = "2026-11-13",
            newTime = "05:30 PM",
            newNote = "Switched to UBL"
        )
        assertEquals(40000.0, dao.getAllAccountsDirect().find { it.name == "HBL" }!!.currentBalance, 0.001)
        assertEquals(10000.0, dao.getAllAccountsDirect().find { it.name == "UBL" }!!.currentBalance, 0.001)
        assertEquals(15000.0, dao.getSavingsPotById(potId)!!.savedAmount, 0.001)
        assertAllAccountBalancesMatchHistory()

        // Delete entry -> UBL restored to 25,000, pot back to 0, linked tx deleted
        val entry = dao.getSavingsEntryById(entryId)!!
        repository.deleteSavingsEntry(entryId)
        assertNull(dao.getSavingsEntryById(entryId))
        assertNull(dao.getTransactionById(entry.transactionId))
        assertEquals(25000.0, dao.getAllAccountsDirect().find { it.name == "UBL" }!!.currentBalance, 0.001)
        assertEquals(0.0, dao.getSavingsPotById(potId)!!.savedAmount, 0.001)
        assertAllAccountBalancesMatchHistory()
    }

    @Test
    fun testSavingsPersistenceAndResetAllDataClearsSavings() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test_savings_persist.db"
            context.deleteDatabase(dbName)

            // Session 1: Create pot and deposit
            var diskDb = Room.databaseBuilder(context, FinanceDatabase::class.java, dbName)
                .addMigrations(FinanceDatabase.MIGRATION_7_8, FinanceDatabase.MIGRATION_8_9, FinanceDatabase.MIGRATION_9_10)
                .allowMainThreadQueries()
                .build()
            var diskRepo = FinanceRepository(diskDb.financeDao(), diskDb)
            diskRepo.seedLegacyDefaultAccountsForTest()
            val hbl = diskDb.financeDao().getAllAccountsDirect().find { it.name == "HBL" }!!
            diskDb.financeDao().updateAccount(hbl.copy(openingBalance = 20000.0, currentBalance = 20000.0))

            val potId = diskRepo.createSavingsPot(name = "Car", targetAmount = 100000.0, targetDate = "2028-01-01")
            diskRepo.addSavingsEntry(potId = potId, type = "DEPOSIT", amount = 8000.0, accountName = "HBL")
            diskDb.close()

            // Session 2 (Reopen app): verify pots, entries, and account balances survived
            diskDb = Room.databaseBuilder(context, FinanceDatabase::class.java, dbName)
                .addMigrations(FinanceDatabase.MIGRATION_7_8, FinanceDatabase.MIGRATION_8_9, FinanceDatabase.MIGRATION_9_10)
                .allowMainThreadQueries()
                .build()
            diskRepo = FinanceRepository(diskDb.financeDao(), diskDb)
            val loadedPot = diskDb.financeDao().getSavingsPotById(potId)
            assertNotNull(loadedPot)
            assertEquals("Car", loadedPot!!.name)
            assertEquals(8000.0, loadedPot.savedAmount, 0.001)
            assertEquals(1, diskDb.financeDao().getSavingsEntriesForPotDirect(potId).size)
            assertEquals(12000.0, diskDb.financeDao().getAllAccountsDirect().find { it.name == "HBL" }!!.currentBalance, 0.001)

            // Reset All Data must clear Savings pots & entries too
            diskRepo.resetAllDataToZero()
            assertTrue(diskDb.financeDao().getAllSavingsPotsDirect().isEmpty())
            assertTrue(diskDb.financeDao().getAllSavingsEntries().first().isEmpty())
            assertEquals(0.0, diskDb.financeDao().getAllAccountsDirect().sumOf { it.currentBalance }, 0.001)

            diskDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun testScrollableBottomNavBar360dpAndLargeFontScale() {
        var selectedScreen by mutableStateOf(AppScreen.DASHBOARD)
        var listStateRef: androidx.compose.foundation.lazy.LazyListState? = null

        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1.5f)
            ) {
                MyApplicationTheme {
                    Box(modifier = Modifier.width(360.dp)) {
                        val listState = rememberLazyListState()
                        listStateRef = listState
                        ScrollableFinanceBottomBar(
                            currentScreen = selectedScreen,
                            onSelectScreen = { selectedScreen = it },
                            listState = listState
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // All 7 items present in data model, including Savings
        assertEquals(7, financeBottomNavItems.size)
        assertTrue(financeBottomNavItems.any { it.screen == AppScreen.SAVINGS && it.label == "Savings" })

        // On 360dp width with 80dp items, first 4 items fit completely (and 5th peeks ~40dp -> 4.5 items)
        composeTestRule.onNodeWithTag("nav_dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_statement").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_accounts").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_petrol_extra").assertIsDisplayed()

        // Single-line check via Compose TextLayoutResult: every visible label must have lineCount == 1 even at 1.5f fontScale
        listOf(
            "nav_dashboard_label",
            "nav_statement_label",
            "nav_accounts_label",
            "nav_petrol_extra_label"
        ).forEach { labelTag ->
            val layoutResults = mutableListOf<TextLayoutResult>()
            composeTestRule.onNodeWithTag(labelTag, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layoutResults) }
            assertEquals("$labelTag must lay out on 1 line", 1, layoutResults.first().lineCount)
        }

        // Switching to Savings must automatically scroll it into the 360dp viewport and keep it completely visible
        composeTestRule.runOnUiThread {
            selectedScreen = AppScreen.SAVINGS
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_savings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_savings_label", useUnmergedTree = true).assertIsDisplayed()

        val savingsLayoutResults = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithTag("nav_savings_label", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(savingsLayoutResults) }
        assertEquals("Savings label must stay single-line", 1, savingsLayoutResults.first().lineCount)

        val savingsBounds = composeTestRule.onNodeWithTag("nav_savings").getUnclippedBoundsInRoot()
        assertTrue("Savings nav item left must be inside 360dp viewport", savingsBounds.left >= 0.dp)
        assertTrue("Savings nav item right must fit completely inside 360dp viewport", savingsBounds.right <= 360.5.dp)

        val visibleIndices = listStateRef?.layoutInfo?.visibleItemsInfo?.map { it.index } ?: emptyList()
        assertTrue("Auto-scroll must make Savings index (6) visible", 6 in visibleIndices)
    }

    @Test
    fun testDatabaseMigration7To8CreatesSavingsTablesSafely() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_migration_7_8.db"
        context.deleteDatabase(dbName)

        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS dummy_v7 (id INTEGER PRIMARY KEY)")
                        db.execSQL("INSERT INTO dummy_v7 (id) VALUES (42)")
                    }

                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val sqliteDb = helper.writableDatabase
        FinanceDatabase.MIGRATION_7_8.migrate(sqliteDb)

        // Verify v7 data intact
        sqliteDb.query("SELECT id FROM dummy_v7").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(42, cursor.getInt(0))
        }
        // Verify new savings_pots & savings_entries tables exist and accept inserts
        sqliteDb.execSQL(
            "INSERT INTO savings_pots (name, targetAmount, savedAmount, targetDate, createdAt, updatedAt) VALUES ('Emergency', 50000.0, 0.0, '', 1000, 1000)"
        )
        sqliteDb.query("SELECT name, targetAmount FROM savings_pots").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Emergency", cursor.getString(0))
            assertEquals(50000.0, cursor.getDouble(1), 0.001)
        }
        sqliteDb.execSQL(
            "INSERT INTO savings_entries (potId, type, amount, accountName, date, time, dayOfWeek, note, transactionId, createdAt, updatedAt) VALUES (1, 'DEPOSIT', 1000.0, 'HBL', '2026-11-15', '12:00:00', 'Sunday', 'Test', 0, 1000, 1000)"
        )
        sqliteDb.query("SELECT COUNT(*) FROM savings_entries").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        sqliteDb.close()
        helper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun testAddBankFromDirectoryAndDuplicateValidation(): Unit {
        runBlocking {
            repository.seedLegacyDefaultAccountsForTest()
            val initialAccounts = dao.getAllAccountsDirect()
            assertEquals(7, initialAccounts.size)

            // Built-in directory includes all required Pakistani bank & wallet entries and filters out already-added names
            val bankOptions = PakistanBankDirectory.getOptionsForType(
                accountType = ACCOUNT_TYPE_BANK,
                existingAccountNames = initialAccounts.map { it.name }.toSet()
            )
            assertTrue(bankOptions.any { it.name == "Meezan Bank" })
            assertTrue(bankOptions.any { it.name == "Bank Alfalah" })
            assertTrue(bankOptions.any { it.name == "Mobilink Microfinance Bank (Jazz)" })
            assertFalse("Existing HBL should be filtered out", bankOptions.any { it.name.equals("HBL", ignoreCase = true) })

            val walletOptions = PakistanBankDirectory.getOptionsForType(
                accountType = ACCOUNT_TYPE_WALLET,
                existingAccountNames = initialAccounts.map { it.name }.toSet()
            )
            assertTrue(walletOptions.any { it.name == "NayaPay" })
            assertTrue(walletOptions.any { it.name == "Konnect by HBL" })
            assertFalse("Existing SadaPay should be filtered out", walletOptions.any { it.name.equals("Sadapay", ignoreCase = true) })

            // Add Meezan Bank from list with optional details
            val created = repository.addAccount(
                accountType = ACCOUNT_TYPE_BANK,
                name = "Meezan Bank",
                holderTitle = "Abdul Quddus",
                accountNumberLast4 = "123456789876",
                note = "Primary Islamic account",
                openingBalance = 25000.0
            )
            assertEquals("Meezan Bank", created.name)
            assertEquals(ACCOUNT_TYPE_BANK, created.accountType)
            assertEquals(25000.0, created.openingBalance, 0.001)
            assertEquals(25000.0, created.currentBalance, 0.001)
            assertEquals("•••• 9876", maskAccountNumber(created.accountNumberLast4))
            assertFalse(created.isBuiltIn)

            // Duplicate check (case-insensitive against built-in and newly added)
            for (dupName in listOf("meezan bank", "MEEZAN BANK", "hbl", "JazzCash")) {
                try {
                    repository.addAccount(accountType = ACCOUNT_TYPE_BANK, name = dupName)
                    fail("Expected duplicate account rejection for $dupName")
                } catch (e: IllegalArgumentException) {
                    assertTrue(e.message?.contains("already exists", ignoreCase = true) == true)
                }
            }

            // Empty name rejected
            try {
                repository.addAccount(accountType = ACCOUNT_TYPE_BANK, name = "   ")
                fail("Expected empty account name rejection")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("empty", ignoreCase = true) == true)
            }
        }
    }

    @Test
    fun testManualAccountsAndStrictIconsByAccountType(): Unit {
        runBlocking {
            repository.seedLegacyDefaultAccountsForTest()

            val manualBank = repository.addAccount(
                accountType = ACCOUNT_TYPE_BANK,
                name = "NayaPay SadaPay Bank", // Even if name resembles a wallet word, BANK type MUST resolve Bank icon
                openingBalance = 1000.0
            )
            val walletAcc = repository.addAccount(
                accountType = ACCOUNT_TYPE_WALLET,
                name = "NayaPay",
                openingBalance = 500.0
            )
            val cashAcc = repository.addAccount(
                accountType = ACCOUNT_TYPE_CASH,
                name = "Cash in Hand",
                openingBalance = 2500.0
            )

            assertEquals(Icons.Default.AccountBalance, accountTypeIcon(manualBank))
            assertEquals(Icons.Default.AccountBalanceWallet, accountTypeIcon(walletAcc))
            assertEquals(Icons.Default.Payments, accountTypeIcon(cashAcc))

            assertEquals("[Bank]", accountTypeBadgeTag(manualBank.accountType))
            assertEquals("[Wallet]", accountTypeBadgeTag(walletAcc.accountType))
            assertEquals("[Cash]", accountTypeBadgeTag(cashAcc.accountType))

            // Verify all built-in accounts keep exact icons
            val all = dao.getAllAccountsDirect()
            val hbl = all.find { it.name == "HBL" }!!
            val ubl = all.find { it.name == "UBL" }!!
            val mashreq = all.find { it.name == "Mashreq" }!!
            val sadapay = all.find { it.name == "Sadapay" }!!
            assertEquals(Icons.Default.AccountBalance, accountTypeIcon(hbl))
            assertEquals(Icons.Default.AccountBalance, accountTypeIcon(ubl))
            assertEquals(Icons.Default.AccountBalance, accountTypeIcon(mashreq))
            assertEquals(Icons.Default.AccountBalanceWallet, accountTypeIcon(sadapay))
        }
    }

    @Test
    fun testEditAccountNameUpdatesAllReferencesPreservingHistory(): Unit {
        runBlocking {
            repository.seedLegacyDefaultAccountsForTest()
            val meezan = repository.addAccount(
                accountType = ACCOUNT_TYPE_BANK,
                name = "Meezan Bank",
                openingBalance = 40000.0
            )

            // Create transactions, Udhaar, and Savings linked to "Meezan Bank"
            repository.addTransaction(
                date = "2026-11-10",
                time = "10:00 AM",
                type = "EXPENSE",
                amount = 5000.0,
                fromAccount = "Meezan Bank",
                toAccount = "Supermarket",
                category = "Groceries"
            )
            repository.addTransaction(
                date = "2026-11-11",
                time = "11:00 AM",
                type = "TRANSFER",
                amount = 3000.0,
                fromAccount = "HBL",
                toAccount = "Meezan Bank",
                category = "Account Transfer"
            )
            val udhaarId = repository.addUdhaarRecord(
                type = "GIVEN",
                personName = "Ali",
                category = "MONEY",
                amount = 2000.0,
                accountName = "Meezan Bank",
                date = "2026-11-12",
                time = "02:00 PM"
            )
            val potId = repository.createSavingsPot(name = "Emergency")
            repository.addSavingsEntry(
                potId = potId,
                type = "DEPOSIT",
                amount = 4000.0,
                accountName = "Meezan Bank",
                date = "2026-11-12",
                time = "03:00 PM"
            )

            // Edit name: "Meezan Bank" -> "Meezan Savings"
            val updated = repository.editAccount(
                id = meezan.id,
                newType = ACCOUNT_TYPE_BANK,
                newName = "Meezan Savings",
                holderTitle = "Zainab",
                accountNumberLast4 = "4321",
                note = "Renamed account"
            )
            assertEquals("Meezan Savings", updated.name)
            assertEquals(32000.0, updated.currentBalance, 0.001)

            // Every reference across tables now uses "Meezan Savings"
            assertEquals(0, repository.getAccountUsageCount("Meezan Bank"))
            assertEquals(6, repository.getAccountUsageCount("Meezan Savings"))
            val txList = dao.getAllTransactions().first()
            assertTrue(txList.none { it.fromAccount == "Meezan Bank" || it.toAccount == "Meezan Bank" })
            assertTrue(txList.any { it.fromAccount == "Meezan Savings" && it.type == "EXPENSE" })
            assertTrue(txList.any { it.toAccount == "Meezan Savings" && it.type == "TRANSFER" })
            assertEquals("Meezan Savings", dao.getUdhaarRecordById(udhaarId)!!.accountName)
            assertEquals("Meezan Savings", dao.getSavingsEntriesForPotDirect(potId).first().accountName)
        }
    }

    @Test
    fun testAllAccountsRemovableAndAtomicDeleteModesPreserveConsistency(): Unit {
        runBlocking {
            repository.seedLegacyDefaultAccountsForTest()

            // 1. Built-in accounts WITHOUT transactions (e.g., UBL, Mashreq) can be deleted cleanly via SIMPLE mode
            assertEquals(0, repository.getAccountUsageCount("UBL"))
            repository.deleteAccount("UBL", AccountDeleteMode.SIMPLE)
            assertNull(dao.getAccountById("UBL"))

            // 2. Built-in account WITH transactions (e.g., HBL) blocks SIMPLE and supports KEEP_HISTORY_AS_DELETED_ACCOUNT
            val hblAcc = dao.getAccountById("HBL")!!
            dao.updateAccount(hblAcc.copy(openingBalance = 20000.0, currentBalance = 20000.0))
            val hblTxId = repository.addTransaction(
                date = "2026-11-12",
                time = "04:00 PM",
                type = "EXPENSE",
                amount = 1500.0,
                fromAccount = "HBL",
                toAccount = "Utility Store",
                category = "Groceries"
            )
            try {
                repository.deleteAccount("HBL", AccountDeleteMode.SIMPLE)
                fail("Should require choosing a delete mode when transactions exist")
            } catch (e: IllegalStateException) {
                assertTrue(e.message?.contains(DELETED_ACCOUNT_LABEL) == true)
            }
            assertNotNull(dao.getAccountById("HBL"))

            repository.deleteAccount("HBL", AccountDeleteMode.KEEP_HISTORY_AS_DELETED_ACCOUNT)
            assertNull(dao.getAccountById("HBL"))
            val preservedTx = dao.getTransactionById(hblTxId)!!
            assertEquals(DELETED_ACCOUNT_LABEL, preservedTx.fromAccount)
            assertEquals(1500.0, preservedTx.amount, 0.001)

            // 3. DELETE_ACCOUNT_AND_TRANSACTIONS mode removes linked tx, reverses counterpart transfer balance, and recalculates Savings/Udhaar atomically
            val meezan = repository.addAccount(ACCOUNT_TYPE_BANK, "Meezan Bank", openingBalance = 50000.0)
            val sadaBefore = dao.getAccountById("Sadapay")!!.currentBalance

            val expId = repository.addTransaction(
                date = "2026-11-13",
                time = "11:00 AM",
                type = "EXPENSE",
                amount = 4000.0,
                fromAccount = "Meezan Bank",
                toAccount = "Fuel",
                category = "Transport"
            )
            val trId = repository.addTransaction(
                date = "2026-11-13",
                time = "12:00 PM",
                type = "TRANSFER",
                amount = 6000.0,
                fromAccount = "Meezan Bank",
                toAccount = "Sadapay",
                category = "Account Transfer"
            )
            assertEquals(sadaBefore + 6000.0, dao.getAccountById("Sadapay")!!.currentBalance, 0.001)

            val potId = repository.createSavingsPot("Hajj")
            val savEntryId = repository.addSavingsEntry(
                potId = potId,
                type = "DEPOSIT",
                amount = 10000.0,
                accountName = "Meezan Bank",
                date = "2026-11-13",
                time = "01:00 PM"
            )
            assertEquals(10000.0, dao.getSavingsPotById(potId)!!.savedAmount, 0.001)

            repository.deleteAccount(meezan.id, AccountDeleteMode.DELETE_ACCOUNT_AND_TRANSACTIONS)

            // Account and all its linked records are gone, Sadapay transfer is reversed, Hajj pot recalculated to 0.0
            assertNull(dao.getAccountById(meezan.id))
            assertNull(dao.getTransactionById(expId))
            assertNull(dao.getTransactionById(trId))
            assertNull(dao.getSavingsEntryById(savEntryId))
            assertEquals(sadaBefore, dao.getAccountById("Sadapay")!!.currentBalance, 0.001)
            assertEquals(0.0, dao.getSavingsPotById(potId)!!.savedAmount, 0.001)
        }
    }

    @Test
    fun testNewAccountIncludedInDashboardTotalSnapshotsAndResetPreservesList(): Unit {
        runBlocking {
            repository.seedLegacyDefaultAccountsForTest()
            repository.addAccount(
                accountType = ACCOUNT_TYPE_BANK,
                name = "Meezan Bank",
                openingBalance = 15000.0
            )
            repository.addAccount(
                accountType = ACCOUNT_TYPE_WALLET,
                name = "NayaPay",
                openingBalance = 5000.0
            )

            // Income + Transfer involving custom accounts
            val incId = repository.addTransaction(
                date = "2026-11-10",
                time = "09:30 AM",
                type = "INCOME",
                amount = 3000.0,
                fromAccount = "Freelance Client",
                toAccount = "Meezan Bank",
                category = "Freelance"
            )
            val trId = repository.addTransaction(
                date = "2026-11-10",
                time = "10:15 AM",
                type = "TRANSFER",
                amount = 2000.0,
                fromAccount = "Meezan Bank",
                toAccount = "NayaPay",
                category = "Account Transfer"
            )

            val allAccounts = dao.getAllAccountsDirect()
            val totalSum = allAccounts.sumOf { it.currentBalance }
            assertEquals(23000.0, totalSum, 0.001)
            assertEquals(16000.0, allAccounts.find { it.name == "Meezan Bank" }!!.currentBalance, 0.001)
            assertEquals(7000.0, allAccounts.find { it.name == "NayaPay" }!!.currentBalance, 0.001)

            // Transaction snapshots and monthly closing balance MUST include custom accounts
            assertEquals(23000.0, dao.getTransactionById(incId)!!.totalAccountBalance, 0.001)
            assertEquals(23000.0, dao.getTransactionById(trId)!!.totalAccountBalance, 0.001)
            val monthly = dao.getMonthlyStatementById("2026-11")!!
            assertEquals(23000.0, monthly.monthlyClosingBalance, 0.001)

            // Reset All Data must KEEP custom accounts with balances reset to 0.0
            repository.resetAllDataToZero()
            val afterReset = dao.getAllAccountsDirect()
            assertEquals(9, afterReset.size)
            assertTrue(afterReset.any { it.name == "Meezan Bank" && it.currentBalance == 0.0 && it.openingBalance == 0.0 })
            assertTrue(afterReset.any { it.name == "NayaPay" && it.currentBalance == 0.0 && it.openingBalance == 0.0 })
        }
    }

    @Test
    fun testAccountPersistenceAcrossReopenAndMigration8To9PreservesExistingAccounts(): Unit {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()

            // 1. Test non-destructive MIGRATION_8_9 on existing v8 database with non-zero balances
            val migDbName = "test_migration_8_9.db"
            context.deleteDatabase(migDbName)
            val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(migDbName)
                    .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(8) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            db.execSQL("""
                                CREATE TABLE IF NOT EXISTS accounts (
                                    id TEXT NOT NULL PRIMARY KEY,
                                    name TEXT NOT NULL,
                                    openingBalance REAL NOT NULL,
                                    currentBalance REAL NOT NULL,
                                    totalReceived REAL NOT NULL,
                                    totalTransferredOut REAL NOT NULL,
                                    totalTransferredIn REAL NOT NULL,
                                    colorHex TEXT NOT NULL,
                                    orderIndex INTEGER NOT NULL,
                                    accountType TEXT NOT NULL
                                )
                            """.trimIndent())
                            db.execSQL("INSERT INTO accounts VALUES ('HBL','HBL',10000,45000,35000,0,0,'#006C50',1,'BANK')")
                            db.execSQL("INSERT INTO accounts VALUES ('Sadapay','Sadapay',2000,12000,10000,0,0,'#FF5A5F',3,'WALLET')")
                        }
                        override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                    })
                    .build()
            )
            val migSqlite = helper.writableDatabase
            FinanceDatabase.MIGRATION_8_9.migrate(migSqlite)
            migSqlite.query("SELECT id, currentBalance, accountType, isBuiltIn, isHidden FROM accounts ORDER BY orderIndex ASC").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("HBL", c.getString(0))
                assertEquals(45000.0, c.getDouble(1), 0.001)
                assertEquals("BANK", c.getString(2))
                assertEquals(1, c.getInt(3))
                assertEquals(0, c.getInt(4))

                assertTrue(c.moveToNext())
                assertEquals("Sadapay", c.getString(0))
                assertEquals(12000.0, c.getDouble(1), 0.001)
                assertEquals("WALLET", c.getString(2))
                assertEquals(1, c.getInt(3))
                assertEquals(0, c.getInt(4))
            }
            migSqlite.close()
            helper.close()
            context.deleteDatabase(migDbName)

            // 2. Test app reopen persistence for custom accounts
            val persistDbName = "test_account_persist.db"
            context.deleteDatabase(persistDbName)
            var diskDb = Room.databaseBuilder(context, FinanceDatabase::class.java, persistDbName)
                .addMigrations(FinanceDatabase.MIGRATION_7_8, FinanceDatabase.MIGRATION_8_9, FinanceDatabase.MIGRATION_9_10)
                .allowMainThreadQueries()
                .build()
            var diskRepo = FinanceRepository(diskDb.financeDao(), diskDb)
            diskRepo.seedLegacyDefaultAccountsForTest()
            diskRepo.addAccount(
                accountType = ACCOUNT_TYPE_BANK,
                name = "Faysal Bank",
                holderTitle = "Tariq",
                accountNumberLast4 = "9988",
                note = "Business",
                openingBalance = 18000.0
            )
            diskDb.close()

            diskDb = Room.databaseBuilder(context, FinanceDatabase::class.java, persistDbName)
                .addMigrations(FinanceDatabase.MIGRATION_7_8, FinanceDatabase.MIGRATION_8_9, FinanceDatabase.MIGRATION_9_10)
                .allowMainThreadQueries()
                .build()
            val reloaded = diskDb.financeDao().getAccountByNameIgnoreCase("Faysal Bank")
            assertNotNull(reloaded)
            assertEquals(ACCOUNT_TYPE_BANK, reloaded!!.accountType)
            assertEquals("Tariq", reloaded.holderTitle)
            assertEquals("9988", reloaded.accountNumberLast4)
            assertEquals("Business", reloaded.note)
            assertEquals(18000.0, reloaded.currentBalance, 0.001)
            diskDb.close()
            context.deleteDatabase(persistDbName)
        }
    }

    @Test
    fun testAddAccountDialogComposeUiValidationAndSubmit(): Unit {
        val existing = com.example.data.local.SeedData.defaultAccounts
        var savedName: String? = null
        var savedType: String? = null
        var savedOpening: Double? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                AccountFormDialog(
                    existingAccounts = existing,
                    onDismiss = {},
                    onSave = { type, name, _, _, _, opening, _ ->
                        savedType = type
                        savedName = name
                        savedOpening = opening
                    }
                )
            }
        }

        composeTestRule.waitForIdle()

        // 1. Submitting empty form displays validation error
        composeTestRule.onNodeWithTag("save_account_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("account_form_error").assertIsDisplayed()

        // 2. Open directory, pick "Other / Add Manually", enter duplicate "HBL" -> error shown
        composeTestRule.onNodeWithTag("account_name_picker_trigger").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("option_add_manually").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("account_custom_name_input").performTextInput("hbl")
        composeTestRule.onNodeWithTag("save_account_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("account_form_error").assertIsDisplayed()

        // 3. Replace with unique name, enter last4 and opening balance -> masked preview & valid save
        composeTestRule.onNodeWithTag("account_custom_name_input").performScrollTo().performTextReplacement("My Custom Bank")
        composeTestRule.onNodeWithTag("account_number_last4_input").performScrollTo().performTextInput("987654321012")
        composeTestRule.onNodeWithTag("account_opening_balance_input").performScrollTo().performTextReplacement("5000")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Displayed masked: •••• 1012").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("save_account_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(ACCOUNT_TYPE_BANK, savedType)
        assertEquals("My Custom Bank", savedName)
        assertEquals(5000.0, savedOpening!!, 0.001)
    }

    @Test
    fun testNewUserStartsWithZeroAccountsAndEmptyStates(): Unit {
        runBlocking {
            repository.initializeIfEmpty()
            val token = repository.registerUser(
                fullName = "Fresh User",
                email = "fresh@example.com",
                mobile = "03001234567",
                rawPassword = "password123"
            )
            assertTrue(repository.verifyEmail(token))
            val loginRes = repository.loginUser("fresh@example.com", "password123")
            assertTrue(loginRes is AuthResult.Success)

            // No pre-created banks, wallets, or cash accounts
            val accounts = repository.accounts.first()
            assertTrue("New user must start with 0 accounts", accounts.isEmpty())

            // Reset modes never recreate default accounts
            val customAcc = repository.addAccount(ACCOUNT_TYPE_BANK, "Meezan Bank", openingBalance = 12000.0)
            assertEquals(1, repository.accounts.first().size)

            repository.resetAllDataToZero(deleteAccounts = false)
            val keptAfterReset = repository.accounts.first()
            assertEquals(1, keptAfterReset.size)
            assertEquals(0.0, keptAfterReset.first().currentBalance, 0.001)

            repository.resetAllDataToZero(deleteAccounts = true)
            assertTrue("Delete everything resets to 0 accounts without re-seeding defaults", repository.accounts.first().isEmpty())
            assertNull(dao.getAccountById(customAcc.id))
        }
    }

    @Test
    fun testPerUserDataAndAuthIsolation(): Unit {
        runBlocking {
            repository.initializeIfEmpty()

            // Register and login User 1
            val token1 = repository.registerUser("User One", "user1@test.com", "03001111111", "pass1111")
            assertTrue(repository.verifyEmail(token1))
            assertTrue(repository.loginUser("user1@test.com", "pass1111") is AuthResult.Success)
            val user1Id = repository.resolveActiveUserId()

            repository.addAccount(ACCOUNT_TYPE_BANK, "Meezan Bank", openingBalance = 25000.0)
            repository.addTransaction(
                date = "2026-11-10",
                time = "10:00 AM",
                type = "EXPENSE",
                amount = 5000.0,
                fromAccount = "Meezan Bank",
                toAccount = "Store",
                category = "Groceries"
            )
            val user1Pot = repository.createSavingsPot("User1 Hajj")
            repository.addUdhaarRecord(type = "GIVEN", personName = "Friend 1", category = "ITEM", itemDescription = "Camera")

            assertEquals(1, repository.accounts.first().size)
            assertEquals(1, repository.transactions.first().size)
            assertEquals(1, repository.savingsPots.first().size)
            assertEquals(1, repository.udhaarRecords.first().size)

            // Register and login User 2 -> User 1's credentials & data remain untouched and isolated
            val token2 = repository.registerUser("User Two", "user2@test.com", "03002222222", "pass2222")
            assertTrue(repository.verifyEmail(token2))
            assertTrue(repository.loginUser("user2@test.com", "pass2222") is AuthResult.Success)
            val user2Id = repository.resolveActiveUserId()
            assertNotEquals(user1Id, user2Id)

            // User 2 starts completely empty and does not see any of User 1's data
            assertTrue(repository.accounts.first().isEmpty())
            assertTrue(repository.transactions.first().isEmpty())
            assertTrue(repository.savingsPots.first().isEmpty())
            assertTrue(repository.udhaarRecords.first().isEmpty())

            repository.addAccount(ACCOUNT_TYPE_WALLET, "NayaPay", openingBalance = 8000.0)
            assertEquals(1, repository.accounts.first().size)
            assertEquals("NayaPay", repository.accounts.first().first().name)

            // Log out -> active streams emit empty lists
            repository.logoutUser()
            assertTrue(repository.accounts.first().isEmpty())

            // Re-login User 1 -> still has full original data, none of User 2's data
            assertTrue(repository.loginUser("user1@test.com", "pass1111") is AuthResult.Success)
            val user1AccountsAgain = repository.accounts.first()
            assertEquals(1, user1AccountsAgain.size)
            assertEquals("Meezan Bank", user1AccountsAgain.first().name)
            assertEquals(20000.0, user1AccountsAgain.first().currentBalance, 0.001)
            assertEquals(1, repository.transactions.first().size)
            assertEquals("User1 Hajj", repository.savingsPots.first().first().name)
            assertNull(dao.getSavingsPotById(user1Pot)?.let { null })
        }
    }

    @Test
    fun testNonDestructiveMigration9To10PreservesExistingDataAndCreatesBackup(): Unit {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val migDbName = "test_migration_9_10.db"
            context.deleteDatabase(migDbName)
            File(context.filesDir, "db_backups").deleteRecursively()

            // Create a genuine schema v9 SQLite DB before upgrade
            val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(migDbName)
                    .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(9) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            db.execSQL("""
                                CREATE TABLE IF NOT EXISTS accounts (
                                    id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, openingBalance REAL NOT NULL,
                                    currentBalance REAL NOT NULL, totalReceived REAL NOT NULL, totalTransferredOut REAL NOT NULL,
                                    totalTransferredIn REAL NOT NULL, colorHex TEXT NOT NULL, orderIndex INTEGER NOT NULL,
                                    accountType TEXT NOT NULL, holderTitle TEXT NOT NULL, accountNumberLast4 TEXT NOT NULL,
                                    note TEXT NOT NULL, isBuiltIn INTEGER NOT NULL, isHidden INTEGER NOT NULL
                                )
                            """.trimIndent())
                            db.execSQL("""
                                CREATE TABLE IF NOT EXISTS categories (
                                    name TEXT NOT NULL PRIMARY KEY, icon TEXT NOT NULL, colorHex TEXT NOT NULL
                                )
                            """.trimIndent())
                            db.execSQL("""
                                CREATE TABLE IF NOT EXISTS transactions (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, serialNumber INTEGER NOT NULL, date TEXT NOT NULL,
                                    time TEXT NOT NULL, dayOfWeek TEXT NOT NULL, type TEXT NOT NULL, amount REAL NOT NULL,
                                    fromAccount TEXT NOT NULL, toAccount TEXT NOT NULL, category TEXT NOT NULL,
                                    comments TEXT NOT NULL, description TEXT NOT NULL, hblBalance REAL NOT NULL,
                                    ublBalance REAL NOT NULL, sadapayBalance REAL NOT NULL, easypaisaBalance REAL NOT NULL,
                                    jazzCashBalance REAL NOT NULL, mashreqBalance REAL NOT NULL, walletBalance REAL NOT NULL,
                                    totalAccountBalance REAL NOT NULL, petrolLiters REAL NOT NULL, petrolRate REAL NOT NULL,
                                    extraItem TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
                                )
                            """.trimIndent())
                            db.execSQL("""
                                CREATE TABLE IF NOT EXISTS petrol_expenses (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, serialNumber INTEGER NOT NULL, date TEXT NOT NULL,
                                    time TEXT NOT NULL, amount REAL NOT NULL, account TEXT NOT NULL, description TEXT NOT NULL,
                                    comments TEXT NOT NULL, liters REAL NOT NULL, ratePerLiter REAL NOT NULL, transactionId INTEGER
                                )
                            """.trimIndent())
                            db.execSQL("""
                                CREATE TABLE IF NOT EXISTS extra_expenses (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, serialNumber INTEGER NOT NULL, item TEXT NOT NULL,
                                    date TEXT NOT NULL, time TEXT NOT NULL, amount REAL NOT NULL, account TEXT NOT NULL,
                                    comments TEXT NOT NULL, transactionId INTEGER
                                )
                            """.trimIndent())
                            db.execSQL("""
                                CREATE TABLE IF NOT EXISTS salary_records (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, salaryDate TEXT NOT NULL, grossSalary REAL NOT NULL,
                                    preExistingAmount REAL NOT NULL, actualSalary REAL NOT NULL, receivedAmount REAL NOT NULL,
                                    remainingAmount REAL NOT NULL, depositedAccount TEXT NOT NULL, comments TEXT NOT NULL
                                )
                            """.trimIndent())
                            db.execSQL("""
                                CREATE TABLE IF NOT EXISTS loan_records (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, personOrSource TEXT NOT NULL,
                                    loanAmount REAL NOT NULL, paidAmount REAL NOT NULL, remainingLoan REAL NOT NULL,
                                    description TEXT NOT NULL, status TEXT NOT NULL
                                )
                            """.trimIndent())
                            db.execSQL("""
                                CREATE TABLE IF NOT EXISTS monthly_statements (
                                    id TEXT NOT NULL PRIMARY KEY, year INTEGER NOT NULL, month INTEGER NOT NULL,
                                    monthName TEXT NOT NULL, openingBalance REAL NOT NULL, totalReceived REAL NOT NULL,
                                    totalExpenses REAL NOT NULL, totalPetrolExpenses REAL NOT NULL, totalExtraExpenses REAL NOT NULL,
                                    totalSalary REAL NOT NULL, preExistingAmount REAL NOT NULL, actualSalary REAL NOT NULL,
                                    grossSalary REAL NOT NULL, totalLoan REAL NOT NULL, monthlyClosingBalance REAL NOT NULL,
                                    hblClosing REAL NOT NULL, ublClosing REAL NOT NULL, sadapayClosing REAL NOT NULL,
                                    easypaisaClosing REAL NOT NULL, jazzCashClosing REAL NOT NULL, mashreqClosing REAL NOT NULL,
                                    walletClosing REAL NOT NULL, isLocked INTEGER NOT NULL, notes TEXT NOT NULL
                                )
                            """.trimIndent())
                            db.execSQL("""
                                CREATE TABLE IF NOT EXISTS user_profile (
                                    id INTEGER NOT NULL PRIMARY KEY, fullName TEXT NOT NULL, email TEXT NOT NULL,
                                    mobile TEXT NOT NULL, passwordHash TEXT NOT NULL, isEmailVerified INTEGER NOT NULL,
                                    verificationToken TEXT NOT NULL, verificationExpiry INTEGER NOT NULL,
                                    accountStatus TEXT NOT NULL, pinCode TEXT NOT NULL, isBiometricEnabled INTEGER NOT NULL,
                                    currencySymbol TEXT NOT NULL, isLoggedIn INTEGER NOT NULL,
                                    profileImageBase64 TEXT NOT NULL, createdAt INTEGER NOT NULL
                                )
                            """.trimIndent())
                            db.execSQL("""
                                CREATE TABLE IF NOT EXISTS udhaar_records (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, personName TEXT NOT NULL,
                                    phoneNumber TEXT NOT NULL, category TEXT NOT NULL, originalAmount REAL NOT NULL,
                                    remainingAmount REAL NOT NULL, itemDescription TEXT NOT NULL, date TEXT NOT NULL,
                                    time TEXT NOT NULL, dayOfWeek TEXT NOT NULL, dueDate TEXT NOT NULL, note TEXT NOT NULL,
                                    status TEXT NOT NULL, isItemReturned INTEGER NOT NULL, accountName TEXT NOT NULL,
                                    transactionId INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
                                )
                            """.trimIndent())
                            db.execSQL("""
                                CREATE TABLE IF NOT EXISTS udhaar_repayments (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, udhaarId INTEGER NOT NULL, amount REAL NOT NULL,
                                    date TEXT NOT NULL, time TEXT NOT NULL, dayOfWeek TEXT NOT NULL, note TEXT NOT NULL,
                                    accountName TEXT NOT NULL, transactionId INTEGER NOT NULL, createdAt INTEGER NOT NULL,
                                    FOREIGN KEY(udhaarId) REFERENCES udhaar_records(id) ON UPDATE NO ACTION ON DELETE CASCADE
                                )
                            """.trimIndent())
                            db.execSQL("CREATE INDEX IF NOT EXISTS index_udhaar_repayments_udhaarId ON udhaar_repayments(udhaarId)")
                            db.execSQL("""
                                CREATE TABLE IF NOT EXISTS savings_pots (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, targetAmount REAL,
                                    savedAmount REAL NOT NULL, targetDate TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
                                )
                            """.trimIndent())
                            db.execSQL("""
                                CREATE TABLE IF NOT EXISTS savings_entries (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, potId INTEGER NOT NULL, type TEXT NOT NULL,
                                    amount REAL NOT NULL, accountName TEXT NOT NULL, date TEXT NOT NULL, time TEXT NOT NULL,
                                    dayOfWeek TEXT NOT NULL, note TEXT NOT NULL, transactionId INTEGER NOT NULL,
                                    createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                                    FOREIGN KEY(potId) REFERENCES savings_pots(id) ON UPDATE NO ACTION ON DELETE CASCADE
                                )
                            """.trimIndent())
                            db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_entries_potId ON savings_entries(potId)")

                            db.execSQL("INSERT INTO user_profile VALUES (1, 'Abdul Quddus', 'owner@test.com', '03001234567', 'hash', 1, '', 0, 'Active', '', 0, 'Rs.', 1, '', 1000)")
                            db.execSQL("INSERT INTO accounts VALUES ('HBL', 'HBL', 50000, 45000, 0, 0, 0, '#006C50', 1, 'BANK', 'Abdul', '1234', '', 1, 0)")
                            db.execSQL("INSERT INTO accounts VALUES ('Sadapay', 'Sadapay', 12000, 12000, 0, 0, 0, '#FF5A5F', 3, 'WALLET', '', '', '', 1, 0)")
                            db.execSQL("INSERT INTO transactions (id, serialNumber, date, time, dayOfWeek, type, amount, fromAccount, toAccount, category, comments, description, hblBalance, ublBalance, sadapayBalance, easypaisaBalance, jazzCashBalance, mashreqBalance, walletBalance, totalAccountBalance, petrolLiters, petrolRate, extraItem, createdAt, updatedAt) VALUES (1, 1, '2026-11-10', '12:00:00', 'Tuesday', 'EXPENSE', 5000, 'HBL', 'Store', 'Groceries', '', 'Groceries', 45000, 0, 12000, 0, 0, 0, 0, 57000, 0, 0, '', 1000, 1000)")
                            db.execSQL("INSERT INTO monthly_statements VALUES ('2026-11', 2026, 11, 'November 2026', 50000, 0, 5000, 0, 0, 0, 0, 0, 0, 0, 57000, 45000, 0, 12000, 0, 0, 0, 0, 0, '')")
                        }
                        override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                    })
                    .build()
            )
            helper.writableDatabase.close()
            helper.close()

            // Verify raw header user_version == 9 and pre-migration backup creates snapshot
            val dbFile = context.getDatabasePath(migDbName)
            assertEquals(9, DatabaseBackupManager.readSqliteUserVersion(dbFile))
            val backupResult = DatabaseBackupManager.backupBeforeMigrationIfNeeded(context, migDbName, AppVersionConfig.DB_SCHEMA_VERSION)
            assertTrue(backupResult.backupCreated)
            assertTrue(File(backupResult.backupFilePath!!).exists())

            // Upgrade DB via real Room builder (non-destructive)
            val upgradedDb = Room.databaseBuilder(context, FinanceDatabase::class.java, migDbName)
                .addMigrations(FinanceDatabase.MIGRATION_7_8, FinanceDatabase.MIGRATION_8_9, FinanceDatabase.MIGRATION_9_10)
                .allowMainThreadQueries()
                .build()
            val migDao = upgradedDb.financeDao()

            val existingAccounts = migDao.getAllAccountsDirectForUser(1)
            assertEquals(2, existingAccounts.size)
            assertEquals(45000.0, existingAccounts.find { it.name == "HBL" }!!.currentBalance, 0.001)
            assertEquals(12000.0, existingAccounts.find { it.name == "Sadapay" }!!.currentBalance, 0.001)
            assertEquals(1, migDao.getAllTransactionsForUser(1).first().size)
            assertEquals("owner@test.com", migDao.getLoggedInUser()?.email)
            assertNotNull(migDao.getMonthlyStatementByRawId("u1_2026-11"))

            upgradedDb.close()
            context.deleteDatabase(migDbName)
        }
    }

    @Test
    fun testAppVersionConfigChangelogUpdateCheckerAndAuthVersionUi(): Unit {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Version metadata & one-time What's New tracking
        assertEquals(2, AppVersionConfig.versionCode)
        assertEquals("1.1.0", AppVersionConfig.versionName)
        assertEquals("Version 1.1.0 (build 2)", AppVersionConfig.formattedVersion)

        context.getSharedPreferences("finance_pro_version_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        assertTrue(AppVersionConfig.shouldShowWhatsNew(context))
        AppVersionConfig.markCurrentVersionSeen(context)
        assertFalse(AppVersionConfig.shouldShowWhatsNew(context))

        // 2. Changelog history
        assertTrue(AppChangelog.releases.any { it.versionName == "1.1.0" && it.versionCode == 2 && it.changes.isNotEmpty() })
        assertTrue(AppChangelog.releases.any { it.versionName == "1.0.0" && it.versionCode == 1 && it.changes.isNotEmpty() })

        // 3. Remote update checker config, HTTP/offline/JSON edge cases, and manifest permission
        assertEquals(
            "https://raw.githubusercontent.com/Abdul786827/financepro-update/refs/heads/main/version.json",
            AppVersionConfig.UPDATE_CHECK_URL
        )

        val newerPayload = """{"latestVersionCode": 3, "latestVersionName": "1.2.0", "downloadUrl": "https://example.com/FinancePro.apk", "changes": ["Cloud sync", "Speed improvements"]}"""
        val updateAvailable = AppUpdateChecker.evaluateJsonPayload(newerPayload, currentVersionCode = 2, currentVersionName = "1.1.0")
        assertTrue(updateAvailable is UpdateCheckResult.UpdateAvailable)
        val availInfo = (updateAvailable as UpdateCheckResult.UpdateAvailable).info
        assertEquals(3, availInfo.latestVersionCode)
        assertEquals("1.2.0", availInfo.latestVersionName)
        assertEquals("https://example.com/FinancePro.apk", availInfo.downloadUrl)
        assertEquals(2, availInfo.changes.size)

        val currentPayload = """{"latestVersionCode": 2, "latestVersionName": "1.1.0", "downloadUrl": "", "changes": []}"""
        assertTrue(AppUpdateChecker.evaluateJsonPayload(currentPayload, 2, "1.1.0") is UpdateCheckResult.UpToDate)
        assertTrue(AppUpdateChecker.evaluateJsonPayload("{bad json", 2, "1.1.0") is UpdateCheckResult.Error)
        assertTrue(AppUpdateChecker.evaluateJsonPayload("<html>404</html>", 2, "1.1.0") is UpdateCheckResult.Error)
        assertTrue(AppUpdateChecker.evaluateJsonPayload("", 2, "1.1.0") is UpdateCheckResult.Error)

        fun clientWithResponse(code: Int, body: String): OkHttpClient =
            OkHttpClient.Builder().addInterceptor { chain ->
                assertEquals(AppVersionConfig.UPDATE_CHECK_URL, chain.request().url.toString())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }.build()

        val offlineClient = OkHttpClient.Builder().addInterceptor {
            throw UnknownHostException("Unable to resolve host raw.githubusercontent.com")
        }.build()

        runBlocking {
            assertTrue(AppUpdateChecker.checkForUpdate(feedUrl = "") is UpdateCheckResult.NotConfigured)

            val fetchedUpdate = AppUpdateChecker.checkForUpdate(client = clientWithResponse(200, newerPayload))
            assertTrue(fetchedUpdate is UpdateCheckResult.UpdateAvailable)
            assertEquals("https://example.com/FinancePro.apk", (fetchedUpdate as UpdateCheckResult.UpdateAvailable).info.downloadUrl)

            val fetchedUpToDate = AppUpdateChecker.checkForUpdate(client = clientWithResponse(200, currentPayload))
            assertTrue(fetchedUpToDate is UpdateCheckResult.UpToDate)

            val fetchedBadJson = AppUpdateChecker.checkForUpdate(client = clientWithResponse(200, "not-valid-json"))
            assertTrue(fetchedBadJson is UpdateCheckResult.Error)

            val fetchedHttpError = AppUpdateChecker.checkForUpdate(client = clientWithResponse(404, "Not Found"))
            assertTrue(fetchedHttpError is UpdateCheckResult.Error)

            val fetchedOffline = AppUpdateChecker.checkForUpdate(client = offlineClient)
            assertTrue(fetchedOffline is UpdateCheckResult.Error)
        }

        // 4. Empty states on AccountsScreen & DashboardScreen + Version string on AuthScreen
        composeTestRule.setContent {
            MyApplicationTheme {
                androidx.compose.foundation.layout.Column {
                    val emptyVm = FinanceViewModel(ApplicationProvider.getApplicationContext())
                    androidx.compose.material3.Text(AppVersionConfig.formattedVersion)
                    AccountsScreen(viewModel = emptyVm, onOpenTransfer = {})
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Version 1.1.0 (build 2)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("accounts_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("empty_add_account_button").assertIsDisplayed()
    }
}


