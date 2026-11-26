package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.FinanceDao
import com.example.data.local.FinanceDatabase
import com.example.data.local.SeedData
import com.example.data.model.*
import com.example.util.DateUtils
import com.example.util.SecurityUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceRepository(
    private val dao: FinanceDao,
    private val database: FinanceDatabase? = null
) {

    private suspend fun <T> runAtomic(block: suspend () -> T): T {
        return if (database != null) {
            database.withTransaction { block() }
        } else {
            block()
        }
    }

    val userProfile: Flow<UserProfileEntity?> = dao.getUserProfile()
    val categories: Flow<List<CategoryEntity>> = dao.getAllCategories()

    /**
     * Resolves the currently active user scope:
     * - When a user is logged in -> their unique userId.
     * - When no users exist on the device yet (fresh/test DB) -> default userId 1.
     * - When registered users exist and nobody is logged in -> null (emit empty lists until sign-in).
     */
    private val activeUserIdFlow: Flow<Int?> = combine(
        dao.getUserProfile(),
        dao.observeUserCount()
    ) { profile, userCount ->
        when {
            profile?.isLoggedIn == true -> profile.id
            userCount == 0 -> 1
            else -> null
        }
    }.distinctUntilChanged()

    suspend fun resolveActiveUserId(): Int {
        val loggedIn = dao.getLoggedInUser()
        if (loggedIn != null) return loggedIn.id
        val userCount = dao.getUserCountDirect()
        if (userCount == 0) return 1
        return dao.getUserProfileDirect()?.id ?: 1
    }

    val accounts: Flow<List<AccountEntity>> = activeUserIdFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else dao.getAllAccountsForUser(uid)
    }

    val transactions: Flow<List<TransactionEntity>> = activeUserIdFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else dao.getAllTransactionsForUser(uid)
    }

    val petrolExpenses: Flow<List<PetrolExpenseEntity>> = activeUserIdFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else dao.getAllPetrolExpensesForUser(uid)
    }

    val extraExpenses: Flow<List<ExtraExpenseEntity>> = activeUserIdFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else dao.getAllExtraExpensesForUser(uid)
    }

    val salaryRecords: Flow<List<SalaryRecordEntity>> = activeUserIdFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else dao.getAllSalaryRecordsForUser(uid)
    }

    val loanRecords: Flow<List<LoanRecordEntity>> = activeUserIdFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else dao.getAllLoanRecordsForUser(uid)
    }

    val monthlyStatements: Flow<List<MonthlyStatementEntity>> = activeUserIdFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else dao.getAllMonthlyStatementsForUser(uid)
    }

    val udhaarRecords: Flow<List<UdhaarRecordEntity>> = activeUserIdFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else dao.getAllUdhaarRecordsForUser(uid)
    }

    val udhaarRepayments: Flow<List<UdhaarRepaymentEntity>> = activeUserIdFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else dao.getAllUdhaarRepaymentsForUser(uid)
    }

    val savingsPots: Flow<List<SavingsPotEntity>> = activeUserIdFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else dao.getAllSavingsPotsForUser(uid)
    }

    val savingsEntries: Flow<List<SavingsEntryEntity>> = activeUserIdFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else dao.getAllSavingsEntriesForUser(uid)
    }

    /**
     * Initializes static categories and cleans duplicate user emails.
     * New users start with ZERO accounts — never auto-seeding HBL/UBL/wallets.
     */
    suspend fun initializeIfEmpty() {
        dao.insertCategories(SeedData.defaultCategories)

        // Normalize existing user emails and deduplicate rows if needed
        val allUsers = dao.getAllUsers()
        val seenEmails = mutableSetOf<String>()
        for (u in allUsers) {
            val normalized = SecurityUtils.normalizeEmail(u.email)
            if (normalized != u.email) {
                dao.updateProfileDetails(u.id, u.fullName, normalized, u.mobile, u.profileImageBase64)
            }
            if (seenEmails.contains(normalized)) {
                dao.deleteDuplicateUsers(normalized, u.id)
            } else {
                seenEmails.add(normalized)
            }
        }
    }

    /**
     * Explicit test helper for populating legacy default accounts in unit tests that target
     * pre-populated HBL/UBL/wallet scenarios. Never called in production initialization.
     */
    suspend fun seedLegacyDefaultAccountsForTest(userId: Int? = null) {
        val uid = userId ?: resolveActiveUserId()
        dao.insertCategories(SeedData.defaultCategories)
        val existing = dao.getAllAccountsDirectForUser(uid)
        val existingNames = existing.map { it.name.trim().lowercase() }.toSet()
        val toInsert = SeedData.defaultAccounts
            .filter { it.name.trim().lowercase() !in existingNames }
            .map { acc ->
                val scopedId = if (uid == 1) acc.id else "u${uid}_${acc.id}"
                acc.copy(id = scopedId, userId = uid)
            }
        if (toInsert.isNotEmpty()) {
            dao.insertAccounts(toInsert)
        }
    }

    private fun buildAccountSnapshotBalances(
        accountMap: Map<String, AccountEntity>
    ): AccountSnapshotBalances {
        val byLower = accountMap.values.associateBy { it.name.trim().lowercase() }
        return AccountSnapshotBalances(
            hbl = byLower["hbl"]?.currentBalance ?: 0.0,
            ubl = byLower["ubl"]?.currentBalance ?: 0.0,
            sadapay = byLower["sadapay"]?.currentBalance ?: 0.0,
            easypaisa = byLower["easypaisa"]?.currentBalance ?: 0.0,
            jazzCash = byLower["jazzcash"]?.currentBalance ?: 0.0,
            mashreq = byLower["mashreq"]?.currentBalance ?: 0.0,
            wallet = byLower["wallet"]?.currentBalance ?: 0.0,
            total = accountMap.values.sumOf { it.currentBalance }
        )
    }

    private data class AccountSnapshotBalances(
        val hbl: Double,
        val ubl: Double,
        val sadapay: Double,
        val easypaisa: Double,
        val jazzCash: Double,
        val mashreq: Double,
        val wallet: Double,
        val total: Double
    )

    suspend fun addTransaction(
        date: String,
        time: String,
        type: String, // INCOME, EXPENSE, TRANSFER, PETROL, EXTRA_EXPENSE
        amount: Double,
        fromAccount: String,
        toAccount: String,
        category: String,
        comments: String = "",
        description: String = "",
        extraItem: String = "",
        petrolLiters: Double = 0.0,
        petrolRate: Double = 0.0,
        isTaxRelated: Boolean = false,
        taxSectionType: String = "",
        taxCategory: String = "",
        taxYearId: String = ""
    ): Long = runAtomic {
        val uid = resolveActiveUserId()
        val accountsList = dao.getAllAccountsDirectForUser(uid).toMutableList()
        val accountMap = accountsList.associateBy { it.name }.toMutableMap()

        when (type) {
            "INCOME" -> {
                val dest = accountMap.values.find { it.name.equals(toAccount, ignoreCase = true) }
                if (dest != null) {
                    val updated = dest.copy(
                        currentBalance = dest.currentBalance + amount,
                        totalReceived = dest.totalReceived + amount
                    )
                    accountMap[dest.name] = updated
                    dao.updateAccount(updated)
                }
            }
            "EXPENSE", "PETROL", "EXTRA_EXPENSE" -> {
                val src = accountMap.values.find { it.name.equals(fromAccount, ignoreCase = true) }
                if (src != null) {
                    val updated = src.copy(
                        currentBalance = src.currentBalance - amount
                    )
                    accountMap[src.name] = updated
                    dao.updateAccount(updated)
                }
            }
            "TRANSFER" -> {
                val src = accountMap.values.find { it.name.equals(fromAccount, ignoreCase = true) }
                val dest = accountMap.values.find { it.name.equals(toAccount, ignoreCase = true) }
                if (src != null) {
                    val updatedSrc = src.copy(
                        currentBalance = src.currentBalance - amount,
                        totalTransferredOut = src.totalTransferredOut + amount
                    )
                    accountMap[src.name] = updatedSrc
                    dao.updateAccount(updatedSrc)
                }
                if (dest != null) {
                    val updatedDest = dest.copy(
                        currentBalance = dest.currentBalance + amount,
                        totalTransferredIn = dest.totalTransferredIn + amount
                    )
                    accountMap[dest.name] = updatedDest
                    dao.updateAccount(updatedDest)
                }
            }
        }

        val dayOfWeek = getDayOfWeek(date)
        val nextSerial = (dao.getMaxSerialNumberForUser(uid) ?: 0) + 1
        val snap = buildAccountSnapshotBalances(accountMap)

        val transaction = TransactionEntity(
            userId = uid,
            serialNumber = nextSerial,
            date = date,
            time = DateUtils.to24HourTime(time),
            dayOfWeek = dayOfWeek,
            type = type,
            amount = amount,
            fromAccount = fromAccount,
            toAccount = toAccount,
            category = category,
            comments = comments,
            description = if (description.isNotBlank()) description else when (type) {
                "PETROL" -> "Petrol: $comments"
                "EXTRA_EXPENSE" -> "Extra: $extraItem"
                "TRANSFER" -> "Transfer $fromAccount -> $toAccount"
                else -> category
            },
            hblBalance = snap.hbl,
            ublBalance = snap.ubl,
            sadapayBalance = snap.sadapay,
            easypaisaBalance = snap.easypaisa,
            jazzCashBalance = snap.jazzCash,
            mashreqBalance = snap.mashreq,
            walletBalance = snap.wallet,
            totalAccountBalance = snap.total,
            petrolLiters = petrolLiters,
            petrolRate = petrolRate,
            extraItem = extraItem,
            isTaxRelated = isTaxRelated,
            taxSectionType = if (isTaxRelated) taxSectionType.trim() else "",
            taxCategory = if (isTaxRelated) taxCategory.trim() else "",
            taxYearId = if (isTaxRelated) taxYearId.trim().ifBlank { com.example.util.PakistanTaxYearUtils.taxYearForDate(date).toString() } else ""
        )

        val txId = dao.insertTransaction(transaction)

        if (type == "PETROL") {
            val nextPetrolSerial = (dao.getMaxPetrolSerialNumberForUser(uid) ?: 0) + 1
            dao.insertPetrolExpense(
                PetrolExpenseEntity(
                    userId = uid,
                    serialNumber = nextPetrolSerial,
                    date = date,
                    time = time,
                    amount = amount,
                    account = fromAccount,
                    description = comments.ifBlank { "Petrol fill" },
                    comments = if (petrolLiters > 0) "$petrolLiters Liters @ $petrolRate" else comments,
                    liters = petrolLiters,
                    ratePerLiter = petrolRate,
                    transactionId = txId
                )
            )
        }

        if (type == "EXTRA_EXPENSE") {
            val nextExtraSerial = (dao.getMaxExtraSerialNumberForUser(uid) ?: 0) + 1
            dao.insertExtraExpense(
                ExtraExpenseEntity(
                    userId = uid,
                    serialNumber = nextExtraSerial,
                    item = extraItem.ifBlank { "Extra Expense" },
                    date = date,
                    time = time,
                    amount = amount,
                    account = fromAccount,
                    comments = comments,
                    transactionId = txId
                )
            )
        }

        val yearMonth = if (date.length >= 7) date.substring(0, 7) else "2026-11"
        recalculateMonthlyStatementForUser(uid, yearMonth)

        txId
    }

    suspend fun deleteTransaction(tx: TransactionEntity) {
        runAtomic {
            val uid = tx.userId
            if (tx.type == "SAVINGS") {
                val linkedEntry = dao.getSavingsEntryByTransactionId(tx.id)
                if (linkedEntry != null) {
                    deleteSavingsEntryInternal(linkedEntry.id)
                    return@runAtomic
                }
            }

            val accountsList = dao.getAllAccountsDirectForUser(uid)
            val accountMap = accountsList.associateBy { it.name }.toMutableMap()

            when (tx.type) {
                "INCOME" -> {
                    val dest = accountMap.values.find { it.name.equals(tx.toAccount, ignoreCase = true) }
                    if (dest != null) {
                        val updated = dest.copy(
                            currentBalance = dest.currentBalance - tx.amount,
                            totalReceived = (dest.totalReceived - tx.amount).coerceAtLeast(0.0)
                        )
                        dao.updateAccount(updated)
                    }
                }
                "EXPENSE", "PETROL", "EXTRA_EXPENSE" -> {
                    val src = accountMap.values.find { it.name.equals(tx.fromAccount, ignoreCase = true) }
                    if (src != null) {
                        val updated = src.copy(
                            currentBalance = src.currentBalance + tx.amount
                        )
                        dao.updateAccount(updated)
                    }
                }
                "TRANSFER" -> {
                    val src = accountMap.values.find { it.name.equals(tx.fromAccount, ignoreCase = true) }
                    val dest = accountMap.values.find { it.name.equals(tx.toAccount, ignoreCase = true) }
                    if (src != null) {
                        val updatedSrc = src.copy(
                            currentBalance = src.currentBalance + tx.amount,
                            totalTransferredOut = (src.totalTransferredOut - tx.amount).coerceAtLeast(0.0)
                        )
                        dao.updateAccount(updatedSrc)
                    }
                    if (dest != null) {
                        val updatedDest = dest.copy(
                            currentBalance = dest.currentBalance - tx.amount,
                            totalTransferredIn = (dest.totalTransferredIn - tx.amount).coerceAtLeast(0.0)
                        )
                        dao.updateAccount(updatedDest)
                    }
                }
            }

            dao.deleteTransaction(tx)
            val yearMonth = if (tx.date.length >= 7) tx.date.substring(0, 7) else "2026-11"
            recalculateMonthlyStatementForUser(uid, yearMonth)
        }
    }

    suspend fun updateTransaction(
        oldTx: TransactionEntity,
        newDate: String,
        newTime: String,
        newType: String,
        newAmount: Double,
        newFromAccount: String,
        newToAccount: String,
        newCategory: String,
        newComments: String = "",
        newExtraItem: String = "",
        newPetrolLiters: Double = 0.0,
        newPetrolRate: Double = 0.0,
        isTaxRelated: Boolean = oldTx.isTaxRelated,
        taxSectionType: String = oldTx.taxSectionType,
        taxCategory: String = oldTx.taxCategory,
        taxYearId: String = oldTx.taxYearId
    ) = runAtomic {
        val uid = oldTx.userId
        val accountsList = dao.getAllAccountsDirectForUser(uid).toMutableList()
        val accountMap = accountsList.associateBy { it.name }.toMutableMap()

        // 1. Revert old transaction balances
        when (oldTx.type) {
            "INCOME" -> {
                val dest = accountMap.values.find { it.name.equals(oldTx.toAccount, ignoreCase = true) }
                if (dest != null) {
                    val updated = dest.copy(
                        currentBalance = dest.currentBalance - oldTx.amount,
                        totalReceived = (dest.totalReceived - oldTx.amount).coerceAtLeast(0.0)
                    )
                    accountMap[dest.name] = updated
                    dao.updateAccount(updated)
                }
            }
            "EXPENSE", "PETROL", "EXTRA_EXPENSE" -> {
                val src = accountMap.values.find { it.name.equals(oldTx.fromAccount, ignoreCase = true) }
                if (src != null) {
                    val updated = src.copy(
                        currentBalance = src.currentBalance + oldTx.amount
                    )
                    accountMap[src.name] = updated
                    dao.updateAccount(updated)
                }
            }
            "TRANSFER" -> {
                val src = accountMap.values.find { it.name.equals(oldTx.fromAccount, ignoreCase = true) }
                val dest = accountMap.values.find { it.name.equals(oldTx.toAccount, ignoreCase = true) }
                if (src != null) {
                    val updatedSrc = src.copy(
                        currentBalance = src.currentBalance + oldTx.amount,
                        totalTransferredOut = (src.totalTransferredOut - oldTx.amount).coerceAtLeast(0.0)
                    )
                    accountMap[src.name] = updatedSrc
                    dao.updateAccount(updatedSrc)
                }
                if (dest != null) {
                    val updatedDest = dest.copy(
                        currentBalance = dest.currentBalance - oldTx.amount,
                        totalTransferredIn = (dest.totalTransferredIn - oldTx.amount).coerceAtLeast(0.0)
                    )
                    accountMap[dest.name] = updatedDest
                    dao.updateAccount(updatedDest)
                }
            }
        }

        // 2. Apply new transaction balances
        when (newType) {
            "INCOME" -> {
                val dest = accountMap.values.find { it.name.equals(newToAccount, ignoreCase = true) }
                if (dest != null) {
                    val updated = dest.copy(
                        currentBalance = dest.currentBalance + newAmount,
                        totalReceived = dest.totalReceived + newAmount
                    )
                    accountMap[dest.name] = updated
                    dao.updateAccount(updated)
                }
            }
            "EXPENSE", "PETROL", "EXTRA_EXPENSE" -> {
                val src = accountMap.values.find { it.name.equals(newFromAccount, ignoreCase = true) }
                if (src != null) {
                    val updated = src.copy(
                        currentBalance = src.currentBalance - newAmount
                    )
                    accountMap[src.name] = updated
                    dao.updateAccount(updated)
                }
            }
            "TRANSFER" -> {
                val src = accountMap.values.find { it.name.equals(newFromAccount, ignoreCase = true) }
                val dest = accountMap.values.find { it.name.equals(newToAccount, ignoreCase = true) }
                if (src != null) {
                    val updatedSrc = src.copy(
                        currentBalance = src.currentBalance - newAmount,
                        totalTransferredOut = src.totalTransferredOut + newAmount
                    )
                    accountMap[src.name] = updatedSrc
                    dao.updateAccount(updatedSrc)
                }
                if (dest != null) {
                    val updatedDest = dest.copy(
                        currentBalance = dest.currentBalance + newAmount,
                        totalTransferredIn = dest.totalTransferredIn + newAmount
                    )
                    accountMap[dest.name] = updatedDest
                    dao.updateAccount(updatedDest)
                }
            }
        }

        val dayOfWeek = getDayOfWeek(newDate)
        val snap = buildAccountSnapshotBalances(accountMap)

        val updatedTx = oldTx.copy(
            date = newDate,
            time = DateUtils.to24HourTime(newTime),
            dayOfWeek = dayOfWeek,
            type = newType,
            amount = newAmount,
            fromAccount = newFromAccount,
            toAccount = newToAccount,
            category = newCategory,
            comments = newComments,
            description = when (newType) {
                "PETROL" -> "Petrol: $newComments"
                "EXTRA_EXPENSE" -> "Extra: $newExtraItem"
                "TRANSFER" -> "Transfer $newFromAccount -> $newToAccount"
                else -> newCategory
            },
            hblBalance = snap.hbl,
            ublBalance = snap.ubl,
            sadapayBalance = snap.sadapay,
            easypaisaBalance = snap.easypaisa,
            jazzCashBalance = snap.jazzCash,
            mashreqBalance = snap.mashreq,
            walletBalance = snap.wallet,
            totalAccountBalance = snap.total,
            isTaxRelated = isTaxRelated,
            taxSectionType = if (isTaxRelated) taxSectionType.trim() else "",
            taxCategory = if (isTaxRelated) taxCategory.trim() else "",
            taxYearId = if (isTaxRelated) taxYearId.trim().ifBlank { com.example.util.PakistanTaxYearUtils.taxYearForDate(newDate).toString() } else "",
            updatedAt = System.currentTimeMillis()
        )

        dao.updateTransaction(updatedTx)

        val oldMonth = if (oldTx.date.length >= 7) oldTx.date.substring(0, 7) else "2026-11"
        val newMonth = if (newDate.length >= 7) newDate.substring(0, 7) else "2026-11"
        recalculateMonthlyStatementForUser(uid, oldMonth)
        if (oldMonth != newMonth) {
            recalculateMonthlyStatementForUser(uid, newMonth)
        }
    }

    suspend fun recalculateMonthlyStatement(yearMonth: String) {
        recalculateMonthlyStatementForUser(resolveActiveUserId(), yearMonth)
    }

    private suspend fun recalculateMonthlyStatementForUser(uid: Int, yearMonth: String) {
        val parts = yearMonth.split("-")
        if (parts.size < 2) return
        val year = parts[0].toIntOrNull() ?: 2026
        val month = parts[1].toIntOrNull() ?: 11

        val monthTxList = dao.getTransactionsByMonthDirectForUser(uid, yearMonth)
        val monthNames = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        val monthTitle = "${monthNames.getOrElse(month - 1) { "Month" }} $year"

        var totalRecv = 0.0
        var totalExp = 0.0
        var totalPetrol = 0.0
        var totalExtra = 0.0

        for (tx in monthTxList) {
            when (tx.type) {
                "INCOME" -> totalRecv += tx.amount
                "EXPENSE" -> totalExp += tx.amount
                "PETROL" -> {
                    totalExp += tx.amount
                    totalPetrol += tx.amount
                }
                "EXTRA_EXPENSE" -> {
                    totalExp += tx.amount
                    totalExtra += tx.amount
                }
            }
        }

        val salaryRecord = dao.getSalaryByDateForUser(uid, yearMonth)
        val salaryGross = salaryRecord?.grossSalary ?: 0.0
        val salaryPre = salaryRecord?.preExistingAmount ?: 0.0
        val salaryActual = salaryRecord?.actualSalary ?: 0.0
        val salaryTotal = salaryRecord?.receivedAmount ?: (if (totalRecv > 0) totalRecv else 0.0)

        val accountsMap = dao.getAllAccountsDirectForUser(uid).associateBy { it.name }
        val snap = buildAccountSnapshotBalances(accountsMap)

        val scopedId = "u${uid}_$yearMonth"
        val existing = dao.getMonthlyStatementForMonth(uid, year, month)
            ?: dao.getMonthlyStatementByRawId(scopedId)
            ?: dao.getMonthlyStatementByRawId(yearMonth)?.takeIf { it.userId == uid }

        val openingBal = existing?.openingBalance ?: (snap.total - totalRecv + totalExp)

        val updatedStatement = MonthlyStatementEntity(
            id = existing?.id ?: scopedId,
            userId = uid,
            year = year,
            month = month,
            monthName = monthTitle,
            openingBalance = openingBal,
            totalReceived = totalRecv,
            totalExpenses = totalExp,
            totalPetrolExpenses = totalPetrol,
            totalExtraExpenses = totalExtra,
            totalSalary = salaryTotal,
            preExistingAmount = salaryPre,
            actualSalary = salaryActual,
            grossSalary = salaryGross,
            totalLoan = 0.0,
            monthlyClosingBalance = snap.total,
            hblClosing = snap.hbl,
            ublClosing = snap.ubl,
            sadapayClosing = snap.sadapay,
            easypaisaClosing = snap.easypaisa,
            jazzCashClosing = snap.jazzCash,
            mashreqClosing = snap.mashreq,
            walletClosing = snap.wallet,
            isLocked = existing?.isLocked ?: false,
            notes = existing?.notes ?: ""
        )
        dao.insertMonthlyStatement(updatedStatement)
    }

    suspend fun addSalaryRecord(
        salaryDate: String,
        gross: Double,
        preExisting: Double,
        received: Double,
        account: String,
        comments: String
    ) {
        val uid = resolveActiveUserId()
        val actual = gross - preExisting
        val remaining = (actual - received).coerceAtLeast(0.0)
        val entity = SalaryRecordEntity(
            userId = uid,
            salaryDate = salaryDate,
            grossSalary = gross,
            preExistingAmount = preExisting,
            actualSalary = actual,
            receivedAmount = received,
            remainingAmount = remaining,
            depositedAccount = account,
            comments = comments
        )
        dao.insertSalaryRecord(entity)

        if (received > 0 && account.isNotBlank()) {
            val nowTime = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Karachi")
            }.format(Date())
            val dateStr = if (salaryDate.length == 7) "$salaryDate-01" else salaryDate
            addTransaction(
                date = dateStr,
                time = nowTime,
                type = "INCOME",
                amount = received,
                fromAccount = "Employer / Salary",
                toAccount = account,
                category = "Salary",
                comments = "Salary $salaryDate (Actual: Rs. $actual, Recv: Rs. $received)"
            )
        }
    }

    suspend fun addLoan(
        date: String,
        personOrSource: String,
        loanAmount: Double,
        description: String
    ) {
        val uid = resolveActiveUserId()
        val entity = LoanRecordEntity(
            userId = uid,
            date = date,
            personOrSource = personOrSource,
            loanAmount = loanAmount,
            paidAmount = 0.0,
            remainingLoan = loanAmount,
            description = description,
            status = "Active"
        )
        dao.insertLoanRecord(entity)
    }

    suspend fun payLoan(loan: LoanRecordEntity, amountPaid: Double, paymentAccount: String) {
        val newPaid = loan.paidAmount + amountPaid
        val newRemaining = (loan.loanAmount - newPaid).coerceAtLeast(0.0)
        val newStatus = if (newRemaining <= 0.0) "Repaid" else "Partially Paid"
        dao.updateLoanRecord(
            loan.copy(
                paidAmount = newPaid,
                remainingLoan = newRemaining,
                status = newStatus
            )
        )

        val nowTime = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Karachi")
        }.format(Date())
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Karachi")
        }.format(Date())

        addTransaction(
            date = today,
            time = nowTime,
            type = "EXPENSE",
            amount = amountPaid,
            fromAccount = paymentAccount,
            toAccount = loan.personOrSource,
            category = "Loan Repayment",
            comments = "Loan repayment for: ${loan.description}"
        )
    }

    suspend fun toggleStatementLock(id: String) {
        val uid = resolveActiveUserId()
        val st = dao.getMonthlyStatementByRawId(id) ?: dao.getMonthlyStatementById(id, uid)
        if (st != null) {
            dao.insertMonthlyStatement(st.copy(isLocked = !st.isLocked))
        }
    }

    suspend fun updateProfileDetails(fullName: String, email: String, mobile: String, profileImageBase64: String): Boolean {
        val profile = dao.getLoggedInUser() ?: dao.getUserProfileDirect() ?: dao.getAnyUserProfile() ?: return false
        val cleanEmail = SecurityUtils.normalizeEmail(email)
        dao.updateProfileDetails(
            id = profile.id,
            fullName = fullName.trim(),
            email = cleanEmail,
            mobile = mobile.trim(),
            profileImageBase64 = profileImageBase64
        )
        return true
    }

    suspend fun updateUserProfile(profile: UserProfileEntity) {
        val cleanEmail = SecurityUtils.normalizeEmail(profile.email)
        dao.updateProfileDetails(
            id = profile.id,
            fullName = profile.fullName.trim(),
            email = cleanEmail,
            mobile = profile.mobile.trim(),
            profileImageBase64 = profile.profileImageBase64
        )
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        val profile = dao.getLoggedInUser() ?: dao.getUserProfileDirect() ?: dao.getAnyUserProfile() ?: return
        dao.updateBiometricEnabled(profile.id, enabled)
    }

    suspend fun setTaxpayerEnabled(enabled: Boolean) {
        val profile = dao.getLoggedInUser() ?: dao.getUserProfileDirect() ?: dao.getAnyUserProfile() ?: return
        dao.updateTaxpayerEnabled(profile.id, enabled)
    }

    suspend fun bulkTagTransactionsForTax(
        ids: List<Long>,
        isTaxRelated: Boolean,
        taxSectionType: String,
        taxCategory: String,
        taxYearId: String
    ) = runAtomic {
        if (ids.isEmpty()) return@runAtomic
        val uid = resolveActiveUserId()
        val cleanSection = if (isTaxRelated) taxSectionType.trim() else ""
        val cleanCat = if (isTaxRelated) taxCategory.trim() else ""
        val cleanYear = if (isTaxRelated) taxYearId.trim() else ""
        dao.bulkUpdateTransactionTaxTag(
            userId = uid,
            ids = ids,
            isTaxRelated = isTaxRelated,
            taxSectionType = cleanSection,
            taxCategory = cleanCat,
            taxYearId = cleanYear,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun verifyCurrentUserPassword(rawPassword: String): Boolean {
        val clean = rawPassword.trim()
        if (clean.isEmpty()) return false
        val profile = dao.getLoggedInUser() ?: dao.getUserProfileDirect() ?: dao.getAnyUserProfile()
            ?: return false
        return SecurityUtils.verifyPassword(clean, profile.passwordHash)
    }

    /**
     * Resets the current user's financial history to 0.
     * - deleteAccounts == false: preserves the user's accounts with balances reset to 0.0.
     * - deleteAccounts == true: also removes all user accounts so the user starts completely clean (0 accounts).
     * - deleteTaxData == false (default): preserves Pakistan Taxpayer records unless explicitly confirmed.
     * Never creates default HBL/UBL/wallet accounts.
     */
    suspend fun resetAllDataToZero(
        deleteAccounts: Boolean = false,
        deleteTaxData: Boolean = false,
        onDeleteTaxData: (suspend (Int) -> Unit)? = null
    ) {
        runAtomic {
            val uid = resolveActiveUserId()
            dao.clearTransactionsForUser(uid)
            dao.clearPetrolForUser(uid)
            dao.clearExtraForUser(uid)
            dao.clearSalaryForUser(uid)
            dao.clearLoansForUser(uid)
            dao.clearMonthlyStatementsForUser(uid)
            dao.clearUdhaarRepaymentsForUser(uid)
            dao.clearUdhaarRecordsForUser(uid)
            dao.clearSavingsEntriesForUser(uid)
            dao.clearSavingsPotsForUser(uid)

            if (deleteAccounts) {
                dao.clearAccountsForUser(uid)
            } else {
                val existingAccounts = dao.getAllAccountsDirectForUser(uid)
                val resetAccounts = existingAccounts.map { acc ->
                    acc.copy(
                        openingBalance = 0.0,
                        currentBalance = 0.0,
                        totalReceived = 0.0,
                        totalTransferredOut = 0.0,
                        totalTransferredIn = 0.0
                    )
                }
                if (resetAccounts.isNotEmpty()) {
                    dao.insertAccounts(resetAccounts)
                }
            }

            if (deleteTaxData) {
                onDeleteTaxData?.invoke(uid)
            }
        }
    }

    // --- ACCOUNT MANAGEMENT (ALL ACCOUNTS EDITABLE, HIDEABLE, REORDERABLE, AND REMOVABLE) ---

    suspend fun getAccountUsageCount(accountName: String): Int {
        val uid = resolveActiveUserId()
        return getAccountUsageCountForUser(uid, accountName)
    }

    private suspend fun getAccountUsageCountForUser(uid: Int, accountName: String): Int {
        val clean = accountName.trim()
        if (clean.isBlank()) return 0
        return dao.countTransactionsForAccountForUser(uid, clean) +
            dao.countPetrolForAccountForUser(uid, clean) +
            dao.countExtraForAccountForUser(uid, clean) +
            dao.countSalaryForAccountForUser(uid, clean) +
            dao.countUdhaarRecordsForAccountForUser(uid, clean) +
            dao.countUdhaarRepaymentsForAccountForUser(uid, clean) +
            dao.countSavingsEntriesForAccountForUser(uid, clean)
    }

    suspend fun addAccount(
        accountType: String,
        name: String,
        holderTitle: String = "",
        accountNumberLast4: String = "",
        note: String = "",
        openingBalance: Double = 0.0
    ): AccountEntity = runAtomic {
        val uid = resolveActiveUserId()
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Account name cannot be empty." }
        require(!cleanName.equals(DELETED_ACCOUNT_LABEL, ignoreCase = true)) {
            "Account name '$DELETED_ACCOUNT_LABEL' is reserved."
        }
        require(openingBalance >= 0.0) { "Opening balance cannot be negative." }

        val existing = dao.getAllAccountsDirectForUser(uid)
        val duplicate = existing.find { it.name.trim().equals(cleanName, ignoreCase = true) }
            ?: dao.getAccountByNameIgnoreCaseForUser(uid, cleanName)
        if (duplicate != null) {
            throw IllegalArgumentException("An account named '${duplicate.name}' already exists.")
        }

        val normalizedType = normalizeAccountType(accountType)
        val slug = cleanName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "acct" }
        val baseId = "u${uid}_$slug"
        var candidateId = baseId
        var suffix = 2
        while (dao.getAccountById(candidateId) != null) {
            candidateId = "${baseId}_$suffix"
            suffix++
        }

        val palette = listOf(
            "#006C50", "#0284C7", "#0D9488", "#2563EB",
            "#7C3AED", "#EA580C", "#D97706", "#059669"
        )
        val colorHex = palette[existing.size % palette.size]
        val nextOrder = (dao.getMaxAccountOrderIndexForUser(uid) ?: existing.maxOfOrNull { it.orderIndex } ?: 0) + 1

        val newAccount = AccountEntity(
            id = candidateId,
            userId = uid,
            name = cleanName,
            openingBalance = openingBalance,
            currentBalance = openingBalance,
            totalReceived = 0.0,
            totalTransferredOut = 0.0,
            totalTransferredIn = 0.0,
            colorHex = colorHex,
            orderIndex = nextOrder,
            accountType = normalizedType,
            holderTitle = holderTitle.trim(),
            accountNumberLast4 = accountNumberLast4.trim(),
            note = note.trim(),
            isBuiltIn = false,
            isHidden = false
        )
        dao.insertAccount(newAccount)

        if (openingBalance > 0.0) {
            val today = DateUtils.getCurrentDate()
            val ym = if (today.length >= 7) today.substring(0, 7) else "2026-11"
            if (dao.getMonthlyStatementById(ym, uid) != null) {
                recalculateMonthlyStatementForUser(uid, ym)
            }
        }

        newAccount
    }

    suspend fun editAccount(
        id: String,
        newType: String,
        newName: String,
        holderTitle: String,
        accountNumberLast4: String,
        note: String
    ): AccountEntity = runAtomic {
        val existing = dao.getAccountById(id)
            ?: throw IllegalArgumentException("Account not found.")
        val uid = existing.userId
        val cleanName = newName.trim()
        require(cleanName.isNotBlank()) { "Account name cannot be empty." }
        require(!cleanName.equals(DELETED_ACCOUNT_LABEL, ignoreCase = true)) {
            "Account name '$DELETED_ACCOUNT_LABEL' is reserved."
        }

        val allAccounts = dao.getAllAccountsDirectForUser(uid)
        val duplicate = allAccounts.find {
            it.id != existing.id && it.name.trim().equals(cleanName, ignoreCase = true)
        }
        if (duplicate != null) {
            throw IllegalArgumentException("An account named '${duplicate.name}' already exists.")
        }

        val updated = existing.copy(
            name = cleanName,
            accountType = normalizeAccountType(newType),
            holderTitle = holderTitle.trim(),
            accountNumberLast4 = accountNumberLast4.trim(),
            note = note.trim()
        )
        dao.updateAccount(updated)

        if (existing.name != cleanName) {
            dao.renameFromAccountInTransactionsForUser(uid, existing.name, cleanName)
            dao.renameToAccountInTransactionsForUser(uid, existing.name, cleanName)
            dao.renameAccountInPetrolForUser(uid, existing.name, cleanName)
            dao.renameAccountInExtraForUser(uid, existing.name, cleanName)
            dao.renameAccountInSalaryForUser(uid, existing.name, cleanName)
            dao.renameAccountInUdhaarRecordsForUser(uid, existing.name, cleanName)
            dao.renameAccountInUdhaarRepaymentsForUser(uid, existing.name, cleanName)
            dao.renameAccountInSavingsEntriesForUser(uid, existing.name, cleanName)
        }

        updated
    }

    /**
     * Deletes ANY account (including formerly default HBL/UBL/Mashreq/Wallets) cleanly in one atomic DB transaction:
     * - SIMPLE: allowed only when the account has 0 linked records.
     * - KEEP_HISTORY_AS_DELETED_ACCOUNT: renames references in history to "Deleted Account", then deletes the account.
     * - DELETE_ACCOUNT_AND_TRANSACTIONS: deletes all transactions/entries linked to the account, reverses any counterpart
     *   transfer balances, updates affected Udhaar/Savings pot totals and MonthlyStatements, and deletes the account.
     */
    suspend fun deleteAccount(
        id: String,
        mode: AccountDeleteMode = AccountDeleteMode.SIMPLE
    ) = runAtomic {
        val existing = dao.getAccountById(id)
            ?: throw IllegalArgumentException("Account not found.")
        val uid = existing.userId
        val accountName = existing.name
        val usageCount = getAccountUsageCountForUser(uid, accountName)

        if (usageCount == 0) {
            dao.deleteAccountById(existing.id)
            return@runAtomic
        }

        when (mode) {
            AccountDeleteMode.SIMPLE -> {
                throw IllegalStateException(
                    "$accountName has $usageCount linked record(s). Choose whether to delete all its transactions or keep history as '$DELETED_ACCOUNT_LABEL'."
                )
            }

            AccountDeleteMode.KEEP_HISTORY_AS_DELETED_ACCOUNT -> {
                dao.renameFromAccountInTransactionsForUser(uid, accountName, DELETED_ACCOUNT_LABEL)
                dao.renameToAccountInTransactionsForUser(uid, accountName, DELETED_ACCOUNT_LABEL)
                dao.renameAccountInPetrolForUser(uid, accountName, DELETED_ACCOUNT_LABEL)
                dao.renameAccountInExtraForUser(uid, accountName, DELETED_ACCOUNT_LABEL)
                dao.renameAccountInSalaryForUser(uid, accountName, DELETED_ACCOUNT_LABEL)
                dao.renameAccountInUdhaarRecordsForUser(uid, accountName, DELETED_ACCOUNT_LABEL)
                dao.renameAccountInUdhaarRepaymentsForUser(uid, accountName, DELETED_ACCOUNT_LABEL)
                dao.renameAccountInSavingsEntriesForUser(uid, accountName, DELETED_ACCOUNT_LABEL)
                dao.deleteAccountById(existing.id)
            }

            AccountDeleteMode.DELETE_ACCOUNT_AND_TRANSACTIONS -> {
                val allAccountsByLowerName = dao.getAllAccountsDirectForUser(uid)
                    .associateBy { it.name.trim().lowercase() }
                    .toMutableMap()
                val affectedMonths = mutableSetOf<String>()

                // 1. Handle Savings entries linked to this account & recalculate affected pots
                val linkedSavingsEntries = dao.getSavingsEntriesForAccountDirectForUser(uid, accountName)
                val modifiedPots = mutableMapOf<Long, SavingsPotEntity>()
                for (entry in linkedSavingsEntries) {
                    val pot = modifiedPots[entry.potId] ?: dao.getSavingsPotById(entry.potId)
                    if (pot != null) {
                        val updatedSaved = if (entry.type == "DEPOSIT") {
                            (pot.savedAmount - entry.amount).coerceAtLeast(0.0)
                        } else {
                            pot.savedAmount + entry.amount
                        }
                        modifiedPots[pot.id] = pot.copy(
                            savedAmount = updatedSaved,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    dao.deleteSavingsEntryById(entry.id)
                    if (entry.date.length >= 7) affectedMonths.add(entry.date.substring(0, 7))
                }
                for ((_, pot) in modifiedPots) {
                    dao.updateSavingsPot(pot)
                }

                // 2. Handle Udhaar records & repayments linked to this account
                val linkedUdhaarRecords = dao.getUdhaarRecordsForAccountDirectForUser(uid, accountName)
                val deletedUdhaarRecordIds = linkedUdhaarRecords.map { it.id }.toSet()
                val linkedRepayments = dao.getUdhaarRepaymentsForAccountDirectForUser(uid, accountName)
                val affectedParentUdhaarIds = mutableSetOf<Long>()

                // Delete repayments directly made using this account
                for (rep in linkedRepayments) {
                    dao.deleteUdhaarRepaymentById(rep.id)
                    if (rep.udhaarId !in deletedUdhaarRecordIds) {
                        affectedParentUdhaarIds.add(rep.udhaarId)
                    }
                    if (rep.date.length >= 7) affectedMonths.add(rep.date.substring(0, 7))
                }

                // Delete parent Udhaar records linked to this account (and reverse any repayments on OTHER accounts)
                for (rec in linkedUdhaarRecords) {
                    val otherRepayments = dao.getRepaymentsForUdhaarDirect(rec.id)
                    for (otherRep in otherRepayments) {
                        if (otherRep.accountName.isNotBlank() && !otherRep.accountName.equals(accountName, ignoreCase = true)) {
                            val otherAcc = allAccountsByLowerName[otherRep.accountName.trim().lowercase()]
                            if (otherAcc != null) {
                                val reverted = if (rec.type == "GIVEN") {
                                    otherAcc.copy(
                                        currentBalance = otherAcc.currentBalance - otherRep.amount,
                                        totalReceived = (otherAcc.totalReceived - otherRep.amount).coerceAtLeast(0.0)
                                    )
                                } else {
                                    otherAcc.copy(
                                        currentBalance = otherAcc.currentBalance + otherRep.amount,
                                        totalTransferredOut = (otherAcc.totalTransferredOut - otherRep.amount).coerceAtLeast(0.0)
                                    )
                                }
                                allAccountsByLowerName[otherAcc.name.trim().lowercase()] = reverted
                            }
                        }
                        if (otherRep.transactionId > 0) {
                            dao.deleteTransactionById(otherRep.transactionId)
                        }
                        dao.deleteUdhaarRepaymentById(otherRep.id)
                    }
                    if (rec.transactionId > 0) {
                        dao.deleteTransactionById(rec.transactionId)
                    }
                    dao.deleteUdhaarRecordById(rec.id)
                    if (rec.date.length >= 7) affectedMonths.add(rec.date.substring(0, 7))
                }

                // Recalculate remainingAmount/status for surviving Udhaar records whose repayments were removed
                for (parentId in affectedParentUdhaarIds) {
                    val parent = dao.getUdhaarRecordById(parentId) ?: continue
                    val remainingReps = dao.getRepaymentsForUdhaarDirect(parentId)
                    val totalRepaid = remainingReps.sumOf { it.amount }
                    val newRemaining = (parent.originalAmount - totalRepaid).coerceAtLeast(0.0)
                    val newStatus = when {
                        parent.category == "ITEM" -> if (parent.isItemReturned) "SETTLED" else "PENDING"
                        newRemaining <= 0.0001 -> "SETTLED"
                        totalRepaid > 0.0 -> "PARTIAL"
                        else -> "PENDING"
                    }
                    dao.updateUdhaarRecord(
                        parent.copy(
                            remainingAmount = newRemaining,
                            status = newStatus,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }

                // 3. Reverse counterpart impacts on surviving accounts for TRANSFERs involving this account
                val linkedTxs = dao.getTransactionsForAccountDirectForUser(uid, accountName)
                for (tx in linkedTxs) {
                    if (tx.date.length >= 7) {
                        affectedMonths.add(tx.date.substring(0, 7))
                    }
                    if (tx.type == "TRANSFER") {
                        val isFromDeleted = tx.fromAccount.equals(accountName, ignoreCase = true)
                        val isToDeleted = tx.toAccount.equals(accountName, ignoreCase = true)
                        if (isFromDeleted && !isToDeleted) {
                            val destKey = tx.toAccount.trim().lowercase()
                            allAccountsByLowerName[destKey]?.let { dest ->
                                allAccountsByLowerName[destKey] = dest.copy(
                                    currentBalance = dest.currentBalance - tx.amount,
                                    totalTransferredIn = (dest.totalTransferredIn - tx.amount).coerceAtLeast(0.0)
                                )
                            }
                        } else if (isToDeleted && !isFromDeleted) {
                            val srcKey = tx.fromAccount.trim().lowercase()
                            allAccountsByLowerName[srcKey]?.let { src ->
                                allAccountsByLowerName[srcKey] = src.copy(
                                    currentBalance = src.currentBalance + tx.amount,
                                    totalTransferredOut = (src.totalTransferredOut - tx.amount).coerceAtLeast(0.0)
                                )
                            }
                        }
                    }
                }

                // Persist updated surviving counterpart accounts
                val deletedLower = accountName.trim().lowercase()
                for ((key, acc) in allAccountsByLowerName) {
                    if (key != deletedLower && acc.id != existing.id) {
                        dao.updateAccount(acc)
                    }
                }

                // 4. Delete linked petrol, extra, salary, transactions, and the account itself
                dao.deletePetrolByAccountForUser(uid, accountName)
                dao.deleteExtraByAccountForUser(uid, accountName)
                dao.deleteSalaryByAccountForUser(uid, accountName)
                dao.deleteTransactionsByAccountForUser(uid, accountName)
                dao.deleteAccountById(existing.id)

                // 5. Recalculate affected monthly statements
                for (ym in affectedMonths) {
                    recalculateMonthlyStatementForUser(uid, ym)
                }
            }
        }
    }

    suspend fun toggleAccountHidden(id: String): AccountEntity = runAtomic {
        val existing = dao.getAccountById(id)
            ?: throw IllegalArgumentException("Account not found.")
        val updated = existing.copy(isHidden = !existing.isHidden)
        dao.updateAccount(updated)
        updated
    }

    suspend fun moveAccountOrder(id: String, moveUp: Boolean) = runAtomic {
        val uid = resolveActiveUserId()
        val ordered = dao.getAllAccountsDirectForUser(uid).sortedBy { it.orderIndex }.toMutableList()
        val idx = ordered.indexOfFirst { it.id == id }
        if (idx < 0) return@runAtomic
        val targetIdx = if (moveUp) idx - 1 else idx + 1
        if (targetIdx !in ordered.indices) return@runAtomic

        val moved = ordered.removeAt(idx)
        ordered.add(targetIdx, moved)

        ordered.forEachIndexed { index, acc ->
            val desiredOrder = index + 1
            if (acc.orderIndex != desiredOrder) {
                dao.updateAccount(acc.copy(orderIndex = desiredOrder))
            }
        }
    }

    // --- UDHAAR (LEND & BORROW) CONNECTED LEDGER METHODS ---

    fun getRepaymentsForUdhaar(udhaarId: Long): Flow<List<UdhaarRepaymentEntity>> {
        return dao.getRepaymentsForUdhaar(udhaarId)
    }

    suspend fun addUdhaarRecord(
        type: String, // "GIVEN" or "TAKEN"
        personName: String,
        phoneNumber: String = "",
        category: String = "MONEY", // "MONEY" or "ITEM"
        amount: Double = 0.0,
        accountName: String = "", // Required for MONEY
        itemDescription: String = "",
        date: String = DateUtils.getCurrentDate(),
        time: String = DateUtils.getCurrentTime(),
        dueDate: String = "",
        note: String = ""
    ): Long = runAtomic {
        val uid = resolveActiveUserId()
        val cleanName = personName.trim()
        val dayOfWeek = DateUtils.getDayOfWeek(date)
        val initialStatus = if (category == "ITEM") "PENDING" else if (amount <= 0.0) "SETTLED" else "PENDING"

        var txId = 0L
        var linkedAccount = ""

        if (category == "MONEY") {
            require(accountName.isNotBlank()) { "Please select an account for money records." }
            require(amount > 0) { "Please enter a valid amount greater than 0." }

            val accountsList = dao.getAllAccountsDirectForUser(uid)
            val account = accountsList.find { it.name.equals(accountName, ignoreCase = true) }
                ?: throw IllegalArgumentException("Account '$accountName' not found")

            if (type == "GIVEN") {
                if (account.currentBalance - amount < -0.0001) {
                    throw IllegalStateException("Insufficient balance in ${account.name}. Available: Rs. ${DateUtils.formatCurrency(account.currentBalance)}, Required: Rs. ${DateUtils.formatCurrency(amount)}")
                }
                val updatedAcc = account.copy(
                    currentBalance = account.currentBalance - amount,
                    totalTransferredOut = account.totalTransferredOut + amount
                )
                dao.updateAccount(updatedAcc)
            } else {
                val updatedAcc = account.copy(
                    currentBalance = account.currentBalance + amount,
                    totalReceived = account.totalReceived + amount
                )
                dao.updateAccount(updatedAcc)
            }

            linkedAccount = account.name

            val freshAccounts = dao.getAllAccountsDirectForUser(uid).associateBy { it.name }
            val snap = buildAccountSnapshotBalances(freshAccounts)
            val nextSerial = (dao.getMaxSerialNumberForUser(uid) ?: 0) + 1
            val transaction = TransactionEntity(
                userId = uid,
                serialNumber = nextSerial,
                date = date,
                time = DateUtils.to24HourTime(time),
                dayOfWeek = dayOfWeek,
                type = "UDHAAR",
                amount = amount,
                fromAccount = if (type == "GIVEN") account.name else cleanName,
                toAccount = if (type == "GIVEN") cleanName else account.name,
                category = "Udhaar",
                comments = note.trim().ifBlank { if (type == "GIVEN") "Udhaar Given to $cleanName" else "Udhaar Taken from $cleanName" },
                description = if (type == "GIVEN") "Udhaar Given: $cleanName" else "Udhaar Taken: $cleanName",
                hblBalance = snap.hbl,
                ublBalance = snap.ubl,
                sadapayBalance = snap.sadapay,
                easypaisaBalance = snap.easypaisa,
                jazzCashBalance = snap.jazzCash,
                mashreqBalance = snap.mashreq,
                walletBalance = snap.wallet,
                totalAccountBalance = snap.total
            )
            txId = dao.insertTransaction(transaction)
        }

        val record = UdhaarRecordEntity(
            userId = uid,
            type = type,
            personName = cleanName,
            phoneNumber = phoneNumber.trim(),
            category = category,
            originalAmount = if (category == "MONEY") amount else 0.0,
            remainingAmount = if (category == "MONEY") amount else 0.0,
            itemDescription = if (category == "ITEM") itemDescription.trim() else "",
            date = date,
            time = DateUtils.format12HourTime(time),
            dayOfWeek = dayOfWeek,
            dueDate = dueDate.trim(),
            note = note.trim(),
            status = initialStatus,
            isItemReturned = false,
            accountName = linkedAccount,
            transactionId = txId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        dao.insertUdhaarRecord(record)
    }

    suspend fun updateUdhaarRecord(
        id: Long,
        type: String,
        personName: String,
        phoneNumber: String = "",
        category: String = "MONEY",
        amount: Double = 0.0,
        accountName: String = "",
        itemDescription: String = "",
        date: String,
        time: String,
        dueDate: String = "",
        note: String = ""
    ) = runAtomic {
        val existing = dao.getUdhaarRecordById(id) ?: return@runAtomic
        val uid = existing.userId
        val dayOfWeek = DateUtils.getDayOfWeek(date)
        val cleanName = personName.trim()

        var currentTxId = existing.transactionId
        var finalAccountName = if (category == "MONEY") accountName.trim() else ""

        if (category == "MONEY") {
            require(finalAccountName.isNotBlank()) { "Please select an account for money records." }
            require(amount > 0) { "Please enter a valid amount greater than 0." }

            val accounts = dao.getAllAccountsDirectForUser(uid).associateBy { it.name }.toMutableMap()
            var simulatedOldBalance = accounts.values.find { it.name.equals(existing.accountName, ignoreCase = true) }?.currentBalance ?: 0.0

            if (existing.category == "MONEY" && existing.accountName.isNotBlank()) {
                simulatedOldBalance = if (existing.type == "GIVEN") {
                    simulatedOldBalance + existing.originalAmount
                } else {
                    simulatedOldBalance - existing.originalAmount
                }
            }

            if (type == "GIVEN") {
                val availableInNew = if (finalAccountName.equals(existing.accountName, ignoreCase = true)) {
                    simulatedOldBalance
                } else {
                    accounts.values.find { it.name.equals(finalAccountName, ignoreCase = true) }?.currentBalance ?: 0.0
                }
                if (availableInNew - amount < -0.0001) {
                    throw IllegalStateException("Insufficient balance in $finalAccountName. Available: Rs. ${DateUtils.formatCurrency(availableInNew)}, Required: Rs. ${DateUtils.formatCurrency(amount)}")
                }
            }

            if (existing.category == "MONEY" && existing.accountName.isNotBlank()) {
                val oldAcc = dao.getAllAccountsDirectForUser(uid).find { it.name.equals(existing.accountName, ignoreCase = true) }
                if (oldAcc != null) {
                    val reverted = if (existing.type == "GIVEN") {
                        oldAcc.copy(
                            currentBalance = oldAcc.currentBalance + existing.originalAmount,
                            totalTransferredOut = (oldAcc.totalTransferredOut - existing.originalAmount).coerceAtLeast(0.0)
                        )
                    } else {
                        oldAcc.copy(
                            currentBalance = oldAcc.currentBalance - existing.originalAmount,
                            totalReceived = (oldAcc.totalReceived - existing.originalAmount).coerceAtLeast(0.0)
                        )
                    }
                    dao.updateAccount(reverted)
                }
            }

            val newAcc = dao.getAllAccountsDirectForUser(uid).find { it.name.equals(finalAccountName, ignoreCase = true) }
                ?: throw IllegalArgumentException("Account '$finalAccountName' not found")
            val updatedNew = if (type == "GIVEN") {
                newAcc.copy(
                    currentBalance = newAcc.currentBalance - amount,
                    totalTransferredOut = newAcc.totalTransferredOut + amount
                )
            } else {
                newAcc.copy(
                    currentBalance = newAcc.currentBalance + amount,
                    totalReceived = newAcc.totalReceived + amount
                )
            }
            dao.updateAccount(updatedNew)
            finalAccountName = newAcc.name

            val freshAccounts = dao.getAllAccountsDirectForUser(uid).associateBy { it.name }
            val snap = buildAccountSnapshotBalances(freshAccounts)

            if (currentTxId > 0) {
                val existingTx = dao.getTransactionById(currentTxId)
                if (existingTx != null) {
                    val updatedTx = existingTx.copy(
                        date = date,
                        time = DateUtils.to24HourTime(time),
                        dayOfWeek = dayOfWeek,
                        type = "UDHAAR",
                        amount = amount,
                        fromAccount = if (type == "GIVEN") finalAccountName else cleanName,
                        toAccount = if (type == "GIVEN") cleanName else finalAccountName,
                        description = if (type == "GIVEN") "Udhaar Given: $cleanName" else "Udhaar Taken: $cleanName",
                        comments = note.trim().ifBlank { if (type == "GIVEN") "Udhaar Given to $cleanName" else "Udhaar Taken from $cleanName" },
                        hblBalance = snap.hbl,
                        ublBalance = snap.ubl,
                        sadapayBalance = snap.sadapay,
                        easypaisaBalance = snap.easypaisa,
                        jazzCashBalance = snap.jazzCash,
                        mashreqBalance = snap.mashreq,
                        walletBalance = snap.wallet,
                        totalAccountBalance = snap.total,
                        updatedAt = System.currentTimeMillis()
                    )
                    dao.updateTransaction(updatedTx)
                }
            } else {
                val nextSerial = (dao.getMaxSerialNumberForUser(uid) ?: 0) + 1
                val newTx = TransactionEntity(
                    userId = uid,
                    serialNumber = nextSerial,
                    date = date,
                    time = DateUtils.to24HourTime(time),
                    dayOfWeek = dayOfWeek,
                    type = "UDHAAR",
                    amount = amount,
                    fromAccount = if (type == "GIVEN") finalAccountName else cleanName,
                    toAccount = if (type == "GIVEN") cleanName else finalAccountName,
                    category = "Udhaar",
                    description = if (type == "GIVEN") "Udhaar Given: $cleanName" else "Udhaar Taken: $cleanName",
                    comments = note.trim().ifBlank { if (type == "GIVEN") "Udhaar Given to $cleanName" else "Udhaar Taken from $cleanName" },
                    hblBalance = snap.hbl,
                    ublBalance = snap.ubl,
                    sadapayBalance = snap.sadapay,
                    easypaisaBalance = snap.easypaisa,
                    jazzCashBalance = snap.jazzCash,
                    mashreqBalance = snap.mashreq,
                    walletBalance = snap.wallet,
                    totalAccountBalance = snap.total
                )
                currentTxId = dao.insertTransaction(newTx)
            }
        } else {
            if (existing.category == "MONEY" && existing.accountName.isNotBlank()) {
                val oldAcc = dao.getAllAccountsDirectForUser(uid).find { it.name.equals(existing.accountName, ignoreCase = true) }
                if (oldAcc != null) {
                    val reverted = if (existing.type == "GIVEN") {
                        oldAcc.copy(
                            currentBalance = oldAcc.currentBalance + existing.originalAmount,
                            totalTransferredOut = (oldAcc.totalTransferredOut - existing.originalAmount).coerceAtLeast(0.0)
                        )
                    } else {
                        oldAcc.copy(
                            currentBalance = oldAcc.currentBalance - existing.originalAmount,
                            totalReceived = (oldAcc.totalReceived - existing.originalAmount).coerceAtLeast(0.0)
                        )
                    }
                    dao.updateAccount(reverted)
                }
            }
            if (currentTxId > 0) {
                dao.deleteTransactionById(currentTxId)
                currentTxId = 0L
            }
            finalAccountName = ""
        }

        val repayments = dao.getRepaymentsForUdhaarDirect(id)
        val totalRepaid = repayments.sumOf { it.amount }
        val newRemaining = if (category == "MONEY") {
            (amount - totalRepaid).coerceAtLeast(0.0)
        } else {
            0.0
        }
        val newStatus = if (category == "ITEM") {
            if (existing.isItemReturned) "SETTLED" else "PENDING"
        } else {
            when {
                newRemaining <= 0.0001 -> "SETTLED"
                totalRepaid > 0.0 -> "PARTIAL"
                else -> "PENDING"
            }
        }

        val updated = existing.copy(
            type = type,
            personName = cleanName,
            phoneNumber = phoneNumber.trim(),
            category = category,
            originalAmount = if (category == "MONEY") amount else 0.0,
            remainingAmount = newRemaining,
            itemDescription = if (category == "ITEM") itemDescription.trim() else "",
            date = date,
            time = DateUtils.format12HourTime(time),
            dayOfWeek = dayOfWeek,
            dueDate = dueDate.trim(),
            note = note.trim(),
            status = newStatus,
            accountName = finalAccountName,
            transactionId = currentTxId,
            updatedAt = System.currentTimeMillis()
        )
        dao.updateUdhaarRecord(updated)
    }

    suspend fun deleteUdhaarRecord(id: Long) = runAtomic {
        val record = dao.getUdhaarRecordById(id) ?: return@runAtomic
        val uid = record.userId

        val repayments = dao.getRepaymentsForUdhaarDirect(id)
        for (rep in repayments) {
            if (rep.accountName.isNotBlank()) {
                val acc = dao.getAllAccountsDirectForUser(uid).find { it.name.equals(rep.accountName, ignoreCase = true) }
                if (acc != null) {
                    val reverted = if (record.type == "GIVEN") {
                        acc.copy(
                            currentBalance = acc.currentBalance - rep.amount,
                            totalReceived = (acc.totalReceived - rep.amount).coerceAtLeast(0.0)
                        )
                    } else {
                        acc.copy(
                            currentBalance = acc.currentBalance + rep.amount,
                            totalTransferredOut = (acc.totalTransferredOut - rep.amount).coerceAtLeast(0.0)
                        )
                    }
                    dao.updateAccount(reverted)
                }
            }
            if (rep.transactionId > 0) {
                dao.deleteTransactionById(rep.transactionId)
            }
            dao.deleteUdhaarRepaymentById(rep.id)
        }

        if (record.category == "MONEY" && record.accountName.isNotBlank()) {
            val acc = dao.getAllAccountsDirectForUser(uid).find { it.name.equals(record.accountName, ignoreCase = true) }
            if (acc != null) {
                val reverted = if (record.type == "GIVEN") {
                    acc.copy(
                        currentBalance = acc.currentBalance + record.originalAmount,
                        totalTransferredOut = (acc.totalTransferredOut - record.originalAmount).coerceAtLeast(0.0)
                    )
                } else {
                    acc.copy(
                        currentBalance = acc.currentBalance - record.originalAmount,
                        totalReceived = (acc.totalReceived - record.originalAmount).coerceAtLeast(0.0)
                    )
                }
                dao.updateAccount(reverted)
            }
        }
        if (record.transactionId > 0) {
            dao.deleteTransactionById(record.transactionId)
        }

        dao.deleteUdhaarRecordById(id)
    }

    suspend fun markItemReturned(id: Long, isReturned: Boolean = true) {
        val existing = dao.getUdhaarRecordById(id) ?: return
        val updated = existing.copy(
            isItemReturned = isReturned,
            status = if (isReturned) "SETTLED" else "PENDING",
            updatedAt = System.currentTimeMillis()
        )
        dao.updateUdhaarRecord(updated)
    }

    suspend fun addUdhaarRepayment(
        udhaarId: Long,
        amount: Double,
        accountName: String = "",
        date: String = DateUtils.getCurrentDate(),
        time: String = DateUtils.getCurrentTime(),
        note: String = ""
    ): Long = runAtomic {
        val record = dao.getUdhaarRecordById(udhaarId) ?: throw IllegalArgumentException("Udhaar record not found")
        val uid = record.userId
        require(amount > 0) { "Please enter a valid repayment amount greater than 0." }

        val targetAccountName = accountName.trim().ifBlank { record.accountName }
        require(targetAccountName.isNotBlank()) { "Please select an account for repayment." }

        val account = dao.getAllAccountsDirectForUser(uid).find { it.name.equals(targetAccountName, ignoreCase = true) }
            ?: throw IllegalArgumentException("Account '$targetAccountName' not found")

        if (record.type == "GIVEN") {
            val updated = account.copy(
                currentBalance = account.currentBalance + amount,
                totalReceived = account.totalReceived + amount
            )
            dao.updateAccount(updated)
        } else {
            if (account.currentBalance - amount < -0.0001) {
                throw IllegalStateException("Insufficient balance in ${account.name}. Available: Rs. ${DateUtils.formatCurrency(account.currentBalance)}, Required: Rs. ${DateUtils.formatCurrency(amount)}")
            }
            val updated = account.copy(
                currentBalance = account.currentBalance - amount,
                totalTransferredOut = account.totalTransferredOut + amount
            )
            dao.updateAccount(updated)
        }

        val freshAccounts = dao.getAllAccountsDirectForUser(uid).associateBy { it.name }
        val snap = buildAccountSnapshotBalances(freshAccounts)

        val dayOfWeek = DateUtils.getDayOfWeek(date)
        val nextSerial = (dao.getMaxSerialNumberForUser(uid) ?: 0) + 1
        val transaction = TransactionEntity(
            userId = uid,
            serialNumber = nextSerial,
            date = date,
            time = DateUtils.to24HourTime(time),
            dayOfWeek = dayOfWeek,
            type = "UDHAAR",
            amount = amount,
            fromAccount = if (record.type == "GIVEN") record.personName else account.name,
            toAccount = if (record.type == "GIVEN") account.name else record.personName,
            category = "Udhaar",
            description = if (record.type == "GIVEN") "Repayment Received: ${record.personName}" else "Repayment Paid: ${record.personName}",
            comments = note.trim().ifBlank { if (record.type == "GIVEN") "Repayment from ${record.personName}" else "Repayment to ${record.personName}" },
            hblBalance = snap.hbl,
            ublBalance = snap.ubl,
            sadapayBalance = snap.sadapay,
            easypaisaBalance = snap.easypaisa,
            jazzCashBalance = snap.jazzCash,
            mashreqBalance = snap.mashreq,
            walletBalance = snap.wallet,
            totalAccountBalance = snap.total
        )
        val txId = dao.insertTransaction(transaction)

        val repayment = UdhaarRepaymentEntity(
            userId = uid,
            udhaarId = udhaarId,
            amount = amount,
            date = date,
            time = DateUtils.format12HourTime(time),
            dayOfWeek = dayOfWeek,
            note = note.trim(),
            accountName = account.name,
            transactionId = txId,
            createdAt = System.currentTimeMillis()
        )
        val repId = dao.insertUdhaarRepayment(repayment)

        val allRepayments = dao.getRepaymentsForUdhaarDirect(udhaarId)
        val totalRepaid = allRepayments.sumOf { it.amount }
        val remaining = (record.originalAmount - totalRepaid).coerceAtLeast(0.0)
        val newStatus = when {
            remaining <= 0.0001 -> "SETTLED"
            totalRepaid > 0.0 -> "PARTIAL"
            else -> "PENDING"
        }

        dao.updateUdhaarRecord(
            record.copy(
                remainingAmount = remaining,
                status = newStatus,
                updatedAt = System.currentTimeMillis()
            )
        )
        repId
    }

    suspend fun deleteUdhaarRepayment(repaymentId: Long, udhaarId: Long) = runAtomic {
        val rep = dao.getUdhaarRepaymentById(repaymentId) ?: return@runAtomic
        val record = dao.getUdhaarRecordById(udhaarId) ?: return@runAtomic
        val uid = record.userId

        if (rep.accountName.isNotBlank()) {
            val acc = dao.getAllAccountsDirectForUser(uid).find { it.name.equals(rep.accountName, ignoreCase = true) }
            if (acc != null) {
                val reverted = if (record.type == "GIVEN") {
                    acc.copy(
                        currentBalance = acc.currentBalance - rep.amount,
                        totalReceived = (acc.totalReceived - rep.amount).coerceAtLeast(0.0)
                    )
                } else {
                    acc.copy(
                        currentBalance = acc.currentBalance + rep.amount,
                        totalTransferredOut = (acc.totalTransferredOut - rep.amount).coerceAtLeast(0.0)
                    )
                }
                dao.updateAccount(reverted)
            }
        }
        if (rep.transactionId > 0) {
            dao.deleteTransactionById(rep.transactionId)
        }

        dao.deleteUdhaarRepaymentById(repaymentId)

        val remainingRepayments = dao.getRepaymentsForUdhaarDirect(udhaarId)
        val totalRepaid = remainingRepayments.sumOf { it.amount }
        val remaining = (record.originalAmount - totalRepaid).coerceAtLeast(0.0)
        val newStatus = when {
            remaining <= 0.0001 -> "SETTLED"
            totalRepaid > 0.0 -> "PARTIAL"
            else -> "PENDING"
        }
        dao.updateUdhaarRecord(
            record.copy(
                remainingAmount = remaining,
                status = newStatus,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateUdhaarRepayment(
        repaymentId: Long,
        udhaarId: Long,
        newAmount: Double,
        newAccountName: String,
        date: String,
        time: String,
        note: String
    ) = runAtomic {
        val rep = dao.getUdhaarRepaymentById(repaymentId) ?: return@runAtomic
        val record = dao.getUdhaarRecordById(udhaarId) ?: return@runAtomic
        val uid = record.userId

        val targetAccountName = newAccountName.trim().ifBlank { rep.accountName }.ifBlank { record.accountName }
        require(targetAccountName.isNotBlank()) { "Please select an account for repayment." }
        require(newAmount > 0) { "Please enter a valid repayment amount greater than 0." }

        val accounts = dao.getAllAccountsDirectForUser(uid).associateBy { it.name }.toMutableMap()
        var simulatedOldBalance = accounts.values.find { it.name.equals(rep.accountName, ignoreCase = true) }?.currentBalance ?: 0.0
        if (rep.accountName.isNotBlank()) {
            simulatedOldBalance = if (record.type == "GIVEN") {
                simulatedOldBalance - rep.amount
            } else {
                simulatedOldBalance + rep.amount
            }
        }

        if (record.type == "TAKEN") {
            val availableInNew = if (targetAccountName.equals(rep.accountName, ignoreCase = true)) {
                simulatedOldBalance
            } else {
                accounts.values.find { it.name.equals(targetAccountName, ignoreCase = true) }?.currentBalance ?: 0.0
            }
            if (availableInNew - newAmount < -0.0001) {
                throw IllegalStateException("Insufficient balance in $targetAccountName. Available: Rs. ${DateUtils.formatCurrency(availableInNew)}, Required: Rs. ${DateUtils.formatCurrency(newAmount)}")
            }
        }

        if (rep.accountName.isNotBlank()) {
            val oldAcc = dao.getAllAccountsDirectForUser(uid).find { it.name.equals(rep.accountName, ignoreCase = true) }
            if (oldAcc != null) {
                val reverted = if (record.type == "GIVEN") {
                    oldAcc.copy(
                        currentBalance = oldAcc.currentBalance - rep.amount,
                        totalReceived = (oldAcc.totalReceived - rep.amount).coerceAtLeast(0.0)
                    )
                } else {
                    oldAcc.copy(
                        currentBalance = oldAcc.currentBalance + rep.amount,
                        totalTransferredOut = (oldAcc.totalTransferredOut - rep.amount).coerceAtLeast(0.0)
                    )
                }
                dao.updateAccount(reverted)
            }
        }

        val newAcc = dao.getAllAccountsDirectForUser(uid).find { it.name.equals(targetAccountName, ignoreCase = true) }
            ?: throw IllegalArgumentException("Account '$targetAccountName' not found")
        val applied = if (record.type == "GIVEN") {
            newAcc.copy(
                currentBalance = newAcc.currentBalance + newAmount,
                totalReceived = newAcc.totalReceived + newAmount
            )
        } else {
            newAcc.copy(
                currentBalance = newAcc.currentBalance - newAmount,
                totalTransferredOut = newAcc.totalTransferredOut + newAmount
            )
        }
        dao.updateAccount(applied)

        val freshAccounts = dao.getAllAccountsDirectForUser(uid).associateBy { it.name }
        val snap = buildAccountSnapshotBalances(freshAccounts)

        val dayOfWeek = DateUtils.getDayOfWeek(date)
        var currentTxId = rep.transactionId

        if (currentTxId > 0) {
            val existingTx = dao.getTransactionById(currentTxId)
            if (existingTx != null) {
                val updatedTx = existingTx.copy(
                    date = date,
                    time = DateUtils.to24HourTime(time),
                    dayOfWeek = dayOfWeek,
                    type = "UDHAAR",
                    amount = newAmount,
                    fromAccount = if (record.type == "GIVEN") record.personName else newAcc.name,
                    toAccount = if (record.type == "GIVEN") newAcc.name else record.personName,
                    description = if (record.type == "GIVEN") "Repayment Received: ${record.personName}" else "Repayment Paid: ${record.personName}",
                    comments = note.trim().ifBlank { if (record.type == "GIVEN") "Repayment from ${record.personName}" else "Repayment to ${record.personName}" },
                    hblBalance = snap.hbl,
                    ublBalance = snap.ubl,
                    sadapayBalance = snap.sadapay,
                    easypaisaBalance = snap.easypaisa,
                    jazzCashBalance = snap.jazzCash,
                    mashreqBalance = snap.mashreq,
                    walletBalance = snap.wallet,
                    totalAccountBalance = snap.total,
                    updatedAt = System.currentTimeMillis()
                )
                dao.updateTransaction(updatedTx)
            }
        } else {
            val nextSerial = (dao.getMaxSerialNumberForUser(uid) ?: 0) + 1
            val newTx = TransactionEntity(
                userId = uid,
                serialNumber = nextSerial,
                date = date,
                time = DateUtils.to24HourTime(time),
                dayOfWeek = dayOfWeek,
                type = "UDHAAR",
                amount = newAmount,
                fromAccount = if (record.type == "GIVEN") record.personName else newAcc.name,
                toAccount = if (record.type == "GIVEN") newAcc.name else record.personName,
                category = "Udhaar",
                description = if (record.type == "GIVEN") "Repayment Received: ${record.personName}" else "Repayment Paid: ${record.personName}",
                comments = note.trim().ifBlank { if (record.type == "GIVEN") "Repayment from ${record.personName}" else "Repayment to ${record.personName}" },
                hblBalance = snap.hbl,
                ublBalance = snap.ubl,
                sadapayBalance = snap.sadapay,
                easypaisaBalance = snap.easypaisa,
                jazzCashBalance = snap.jazzCash,
                mashreqBalance = snap.mashreq,
                walletBalance = snap.wallet,
                totalAccountBalance = snap.total
            )
            currentTxId = dao.insertTransaction(newTx)
        }

        val updatedRepayment = rep.copy(
            amount = newAmount,
            accountName = newAcc.name,
            transactionId = currentTxId,
            date = date,
            time = DateUtils.format12HourTime(time),
            dayOfWeek = dayOfWeek,
            note = note.trim()
        )
        dao.updateUdhaarRepayment(updatedRepayment)

        val allRepayments = dao.getRepaymentsForUdhaarDirect(udhaarId)
        val totalRepaid = allRepayments.sumOf { it.amount }
        val remaining = (record.originalAmount - totalRepaid).coerceAtLeast(0.0)
        val newStatus = when {
            remaining <= 0.0001 -> "SETTLED"
            totalRepaid > 0.0 -> "PARTIAL"
            else -> "PENDING"
        }
        dao.updateUdhaarRecord(
            record.copy(
                remainingAmount = remaining,
                status = newStatus,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    // --- MULTI-USER AUTHENTICATION (ISOLATED PER USER) ---

    suspend fun registerUser(
        fullName: String,
        email: String,
        mobile: String,
        rawPassword: String
    ): String {
        initializeIfEmpty()
        val cleanEmail = SecurityUtils.normalizeEmail(email)
        val cleanPassword = rawPassword.trim()
        val hashedPassword = SecurityUtils.hashPassword(cleanPassword)
        val verificationToken = UUID.randomUUID().toString().substring(0, 8).uppercase()
        val expiryTime = System.currentTimeMillis() + 30 * 60 * 1000 // 30 minutes

        // Reuse existing ID ONLY if this exact email already exists; otherwise autoGenerate (id = 0)
        val existingUsers = dao.getAllUsersByEmail(cleanEmail)
        val existingId = existingUsers.firstOrNull()?.id ?: 0

        val newProfile = UserProfileEntity(
            id = existingId,
            fullName = fullName.trim(),
            email = cleanEmail,
            mobile = mobile.trim(),
            passwordHash = hashedPassword,
            isEmailVerified = false,
            verificationToken = verificationToken,
            verificationExpiry = expiryTime,
            accountStatus = "Pending",
            isLoggedIn = false
        )
        val insertedId = dao.insertUserProfile(newProfile).toInt()
        val keptId = if (existingId > 0) existingId else insertedId
        dao.deleteDuplicateUsers(cleanEmail, keptId)

        return verificationToken
    }

    suspend fun verifyEmail(token: String): Boolean {
        val cleanToken = token.trim()
        val profile = if (cleanToken == "VERIFIED") {
            dao.getUserProfileDirect() ?: dao.getAnyUserProfile()
        } else {
            dao.getUserByVerificationToken(cleanToken)
                ?: dao.getUserProfileDirect()?.takeIf { cleanToken.equals(it.verificationToken.trim(), ignoreCase = true) }
        } ?: return false

        val isMatch = cleanToken.equals(profile.verificationToken.trim(), ignoreCase = true) || cleanToken == "VERIFIED"
        if (isMatch) {
            dao.updateEmailVerified(profile.id, verified = true, status = "Active")
            return true
        }
        return false
    }

    suspend fun resendVerificationToken(email: String? = null): String {
        val cleanEmail = email?.let { SecurityUtils.normalizeEmail(it) }.orEmpty()
        val profile = if (cleanEmail.isNotBlank()) {
            dao.getUserByEmail(cleanEmail)
        } else {
            dao.getUserProfileDirect() ?: dao.getAnyUserProfile()
        } ?: return ""

        val newToken = UUID.randomUUID().toString().substring(0, 8).uppercase()
        val expiryTime = System.currentTimeMillis() + 30 * 60 * 1000
        dao.updateVerificationToken(profile.id, newToken, expiryTime)
        return newToken
    }

    suspend fun loginUser(email: String, rawPassword: String): AuthResult {
        initializeIfEmpty()

        val cleanEmail = SecurityUtils.normalizeEmail(email)
        val cleanPassword = rawPassword.trim()

        if (cleanEmail.isEmpty()) {
            return AuthResult.Error("Please enter your email address.")
        }
        if (cleanPassword.isEmpty()) {
            return AuthResult.Error("Please enter your password.")
        }

        val matchingUsers = dao.getAllUsersByEmail(cleanEmail)
        val profile = matchingUsers.firstOrNull()

        if (profile == null) {
            return AuthResult.Error("Email not found. Please check your email or create an account.")
        }

        if (matchingUsers.size > 1) {
            dao.deleteDuplicateUsers(cleanEmail, profile.id)
        }

        val passwordMatches = SecurityUtils.verifyPassword(cleanPassword, profile.passwordHash)
        if (!passwordMatches) {
            return AuthResult.Error("Incorrect password. Please try again or use Forgot Password.")
        }

        if (!profile.isEmailVerified) {
            return AuthResult.EmailNotVerified(profile.email, profile.verificationToken)
        }

        val isSingleSha256 = profile.passwordHash.length == 64 &&
            profile.passwordHash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        val standardHash = SecurityUtils.hashPassword(cleanPassword)
        if (!isSingleSha256 || !profile.passwordHash.equals(standardHash, ignoreCase = true)) {
            dao.updatePasswordHash(profile.id, standardHash)
        }

        dao.clearAllLoginSessions()
        dao.updateLoginSession(profile.id, true)

        val updated = profile.copy(
            email = cleanEmail,
            passwordHash = standardHash,
            isLoggedIn = true
        )
        return AuthResult.Success(updated)
    }

    suspend fun changePassword(oldPassword: String, newPassword: String): Pair<Boolean, String> {
        initializeIfEmpty()
        val cleanOld = oldPassword.trim()
        val cleanNew = newPassword.trim()
        val profile = dao.getLoggedInUser() ?: dao.getUserProfileDirect() ?: dao.getAnyUserProfile()
            ?: return Pair(false, "User profile not found")

        if (!SecurityUtils.verifyPassword(cleanOld, profile.passwordHash)) {
            return Pair(false, "Current password is incorrect")
        }

        if (cleanNew.length < 6) {
            return Pair(false, "New password must be at least 6 characters")
        }

        val newHashedPassword = SecurityUtils.hashPassword(cleanNew)
        dao.updatePasswordHash(profile.id, newHashedPassword)
        return Pair(true, "Password changed successfully")
    }

    suspend fun setSessionLoggedIn(): UserProfileEntity {
        initializeIfEmpty()
        val profile = dao.getLoggedInUser() ?: dao.getUserProfileDirect() ?: dao.getAnyUserProfile()
        if (profile != null) {
            dao.clearAllLoginSessions()
            dao.updateLoginSession(profile.id, true)
            return profile.copy(isLoggedIn = true)
        }
        val fallback = UserProfileEntity(
            id = 0,
            isLoggedIn = true
        )
        val insertedId = dao.insertUserProfile(fallback).toInt()
        return fallback.copy(id = insertedId)
    }

    suspend fun logoutUser() {
        dao.clearAllLoginSessions()
    }

    suspend fun resetPassword(email: String, newPassword: String): Boolean {
        initializeIfEmpty()
        val cleanEmail = SecurityUtils.normalizeEmail(email)
        val cleanPassword = newPassword.trim()
        if (cleanEmail.isEmpty() || cleanPassword.isEmpty() || cleanPassword.length < 6) return false

        val matchingUsers = dao.getAllUsersByEmail(cleanEmail)
        val profile = matchingUsers.firstOrNull() ?: return false

        val newHashedPassword = SecurityUtils.hashPassword(cleanPassword)
        dao.updatePasswordHash(profile.id, newHashedPassword)
        dao.updateEmailVerified(profile.id, verified = true, status = "Active")
        dao.deleteDuplicateUsers(cleanEmail, profile.id)
        return true
    }

    suspend fun resetToDefaultDemo() {
        resetAllDataToZero(deleteAccounts = false)
    }

    // --- SAVINGS POTS & ENTRIES (SEPARATE FROM ACCOUNT BALANCES & INCOME/EXPENSE SUMMARIES) ---

    suspend fun createSavingsPot(
        name: String,
        targetAmount: Double? = null,
        targetDate: String = ""
    ): Long = runAtomic {
        val uid = resolveActiveUserId()
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Please enter a savings pot name." }
        if (targetAmount != null) {
            require(targetAmount > 0) { "Target amount must be greater than 0." }
        }
        val pot = SavingsPotEntity(
            userId = uid,
            name = cleanName,
            targetAmount = targetAmount,
            savedAmount = 0.0,
            targetDate = targetDate.trim(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        dao.insertSavingsPot(pot)
    }

    suspend fun updateSavingsPot(
        id: Long,
        name: String,
        targetAmount: Double? = null,
        targetDate: String = ""
    ) = runAtomic {
        val existing = dao.getSavingsPotById(id) ?: return@runAtomic
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Please enter a savings pot name." }
        if (targetAmount != null) {
            require(targetAmount > 0) { "Target amount must be greater than 0." }
        }

        val updated = existing.copy(
            name = cleanName,
            targetAmount = targetAmount,
            targetDate = targetDate.trim(),
            updatedAt = System.currentTimeMillis()
        )
        dao.updateSavingsPot(updated)

        if (existing.name != cleanName) {
            val entries = dao.getSavingsEntriesForPotDirect(id)
            for (entry in entries) {
                if (entry.transactionId > 0) {
                    val tx = dao.getTransactionById(entry.transactionId)
                    if (tx != null) {
                        val isDeposit = entry.type == "DEPOSIT"
                        val newDesc = if (isDeposit) "Saved to $cleanName" else "Withdrawn from $cleanName"
                        val updatedTx = tx.copy(
                            fromAccount = if (isDeposit) entry.accountName else cleanName,
                            toAccount = if (isDeposit) cleanName else entry.accountName,
                            description = newDesc,
                            comments = entry.note.ifBlank { newDesc },
                            updatedAt = System.currentTimeMillis()
                        )
                        dao.updateTransaction(updatedTx)
                    }
                }
            }
        }
    }

    suspend fun deleteSavingsPot(id: Long) = runAtomic {
        val pot = dao.getSavingsPotById(id) ?: return@runAtomic
        val uid = pot.userId
        val entries = dao.getSavingsEntriesForPotDirect(id)
        val accountMap = dao.getAllAccountsDirectForUser(uid).associateBy { it.name }.toMutableMap()

        for (entry in entries) {
            val acc = accountMap.values.find { it.name.equals(entry.accountName, ignoreCase = true) }
            if (acc != null) {
                val reverted = if (entry.type == "DEPOSIT") {
                    acc.copy(
                        currentBalance = acc.currentBalance + entry.amount,
                        totalTransferredOut = (acc.totalTransferredOut - entry.amount).coerceAtLeast(0.0)
                    )
                } else {
                    acc.copy(
                        currentBalance = acc.currentBalance - entry.amount,
                        totalReceived = (acc.totalReceived - entry.amount).coerceAtLeast(0.0)
                    )
                }
                accountMap[acc.name] = reverted
            }
        }

        for ((_, acc) in accountMap) {
            if (acc.currentBalance < -0.0001) {
                throw IllegalStateException(
                    "Cannot delete pot because withdrawn funds in ${acc.name} have already been spent."
                )
            }
        }

        for ((_, acc) in accountMap) {
            dao.updateAccount(acc)
        }
        for (entry in entries) {
            if (entry.transactionId > 0) {
                dao.deleteTransactionById(entry.transactionId)
            }
        }
        dao.deleteSavingsPotById(id)
    }

    suspend fun addSavingsEntry(
        potId: Long,
        type: String, // "DEPOSIT" or "WITHDRAW"
        amount: Double,
        accountName: String,
        date: String = DateUtils.getCurrentDate(),
        time: String = DateUtils.getCurrentTime(),
        note: String = ""
    ): Long = runAtomic {
        require(type == "DEPOSIT" || type == "WITHDRAW") { "Invalid savings operation type." }
        require(amount > 0) { "Please enter a valid amount greater than 0." }
        require(accountName.isNotBlank()) { "Please select an account." }

        val pot = dao.getSavingsPotById(potId)
            ?: throw IllegalArgumentException("Savings pot not found.")
        val uid = pot.userId
        val accountsList = dao.getAllAccountsDirectForUser(uid)
        val account = accountsList.find { it.name.equals(accountName.trim(), ignoreCase = true) }
            ?: throw IllegalArgumentException("Account '$accountName' not found.")

        val updatedAccount: AccountEntity
        val updatedPot: SavingsPotEntity

        if (type == "DEPOSIT") {
            if (account.currentBalance - amount < -0.0001) {
                throw IllegalStateException(
                    "Insufficient balance in ${account.name}. Available: ${DateUtils.formatCurrency(account.currentBalance)}, Required: ${DateUtils.formatCurrency(amount)}"
                )
            }
            updatedAccount = account.copy(
                currentBalance = account.currentBalance - amount,
                totalTransferredOut = account.totalTransferredOut + amount
            )
            updatedPot = pot.copy(
                savedAmount = pot.savedAmount + amount,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            if (pot.savedAmount - amount < -0.0001) {
                throw IllegalStateException(
                    "Insufficient savings in ${pot.name}. Available: ${DateUtils.formatCurrency(pot.savedAmount)}, Required: ${DateUtils.formatCurrency(amount)}"
                )
            }
            updatedPot = pot.copy(
                savedAmount = (pot.savedAmount - amount).coerceAtLeast(0.0),
                updatedAt = System.currentTimeMillis()
            )
            updatedAccount = account.copy(
                currentBalance = account.currentBalance + amount,
                totalReceived = account.totalReceived + amount
            )
        }

        dao.updateAccount(updatedAccount)
        dao.updateSavingsPot(updatedPot)

        val freshAccounts = dao.getAllAccountsDirectForUser(uid).associateBy { it.name }
        val snap = buildAccountSnapshotBalances(freshAccounts)

        val dayOfWeek = DateUtils.getDayOfWeek(date)
        val canonicalTime = DateUtils.to24HourTime(time)
        val cleanNote = note.trim()
        val desc = if (type == "DEPOSIT") "Saved to ${pot.name}" else "Withdrawn from ${pot.name}"
        val nextSerial = (dao.getMaxSerialNumberForUser(uid) ?: 0) + 1

        val transaction = TransactionEntity(
            userId = uid,
            serialNumber = nextSerial,
            date = date,
            time = canonicalTime,
            dayOfWeek = dayOfWeek,
            type = "SAVINGS",
            amount = amount,
            fromAccount = if (type == "DEPOSIT") account.name else pot.name,
            toAccount = if (type == "DEPOSIT") pot.name else account.name,
            category = "Savings",
            comments = cleanNote.ifBlank { desc },
            description = desc,
            hblBalance = snap.hbl,
            ublBalance = snap.ubl,
            sadapayBalance = snap.sadapay,
            easypaisaBalance = snap.easypaisa,
            jazzCashBalance = snap.jazzCash,
            mashreqBalance = snap.mashreq,
            walletBalance = snap.wallet,
            totalAccountBalance = snap.total
        )
        val txId = dao.insertTransaction(transaction)

        val entry = SavingsEntryEntity(
            userId = uid,
            potId = pot.id,
            type = type,
            amount = amount,
            accountName = account.name,
            date = date,
            time = canonicalTime,
            dayOfWeek = dayOfWeek,
            note = cleanNote,
            transactionId = txId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        dao.insertSavingsEntry(entry)
    }

    suspend fun updateSavingsEntry(
        entryId: Long,
        newPotId: Long,
        newType: String,
        newAmount: Double,
        newAccountName: String,
        newDate: String,
        newTime: String,
        newNote: String
    ) = runAtomic {
        val oldEntry = dao.getSavingsEntryById(entryId) ?: return@runAtomic
        val uid = oldEntry.userId
        require(newType == "DEPOSIT" || newType == "WITHDRAW") { "Invalid savings operation type." }
        require(newAmount > 0) { "Please enter a valid amount greater than 0." }
        require(newAccountName.isNotBlank()) { "Please select an account." }

        val potsMap = dao.getAllSavingsPotsDirectForUser(uid).associateBy { it.id }.toMutableMap()
        val accountsMap = dao.getAllAccountsDirectForUser(uid).associateBy { it.name }.toMutableMap()

        val oldPot = potsMap[oldEntry.potId]
            ?: throw IllegalStateException("Original savings pot not found.")
        val targetPot = potsMap[newPotId]
            ?: throw IllegalStateException("Selected savings pot not found.")
        val oldAccount = accountsMap.values.find { it.name.equals(oldEntry.accountName, ignoreCase = true) }
        val newAccountKey = accountsMap.keys.find { it.equals(newAccountName.trim(), ignoreCase = true) }
            ?: throw IllegalArgumentException("Account '$newAccountName' not found.")

        val revertedOldPot = if (oldEntry.type == "DEPOSIT") {
            oldPot.copy(savedAmount = oldPot.savedAmount - oldEntry.amount, updatedAt = System.currentTimeMillis())
        } else {
            oldPot.copy(savedAmount = oldPot.savedAmount + oldEntry.amount, updatedAt = System.currentTimeMillis())
        }
        potsMap[oldPot.id] = revertedOldPot

        if (oldAccount != null) {
            val revertedOldAcc = if (oldEntry.type == "DEPOSIT") {
                oldAccount.copy(
                    currentBalance = oldAccount.currentBalance + oldEntry.amount,
                    totalTransferredOut = (oldAccount.totalTransferredOut - oldEntry.amount).coerceAtLeast(0.0)
                )
            } else {
                oldAccount.copy(
                    currentBalance = oldAccount.currentBalance - oldEntry.amount,
                    totalReceived = (oldAccount.totalReceived - oldEntry.amount).coerceAtLeast(0.0)
                )
            }
            accountsMap[oldAccount.name] = revertedOldAcc
        }

        if (oldPot.id != targetPot.id && revertedOldPot.savedAmount < -0.0001) {
            throw IllegalStateException(
                "Cannot move this deposit from ${oldPot.name} because those savings were already withdrawn."
            )
        }
        if (oldAccount != null && !oldAccount.name.equals(newAccountKey, ignoreCase = true)) {
            if ((accountsMap[oldAccount.name]?.currentBalance ?: 0.0) < -0.0001) {
                throw IllegalStateException(
                    "Cannot change account from ${oldAccount.name} because withdrawn funds have already been spent."
                )
            }
        }

        val currentTargetPot = potsMap[newPotId]!!
        val currentNewAcc = accountsMap[newAccountKey]!!

        val appliedPot: SavingsPotEntity
        val appliedAcc: AccountEntity

        if (newType == "DEPOSIT") {
            if (currentNewAcc.currentBalance - newAmount < -0.0001) {
                throw IllegalStateException(
                    "Insufficient balance in ${currentNewAcc.name}. Available: ${DateUtils.formatCurrency(currentNewAcc.currentBalance)}, Required: ${DateUtils.formatCurrency(newAmount)}"
                )
            }
            val resultingPotBal = currentTargetPot.savedAmount + newAmount
            if (resultingPotBal < -0.0001) {
                throw IllegalStateException(
                    "Cannot reduce deposit in ${currentTargetPot.name} because funds were already withdrawn."
                )
            }
            appliedAcc = currentNewAcc.copy(
                currentBalance = currentNewAcc.currentBalance - newAmount,
                totalTransferredOut = currentNewAcc.totalTransferredOut + newAmount
            )
            appliedPot = currentTargetPot.copy(
                savedAmount = resultingPotBal.coerceAtLeast(0.0),
                updatedAt = System.currentTimeMillis()
            )
        } else {
            if (currentTargetPot.savedAmount - newAmount < -0.0001) {
                throw IllegalStateException(
                    "Insufficient savings in ${currentTargetPot.name}. Available: ${DateUtils.formatCurrency(currentTargetPot.savedAmount.coerceAtLeast(0.0))}, Required: ${DateUtils.formatCurrency(newAmount)}"
                )
            }
            appliedPot = currentTargetPot.copy(
                savedAmount = (currentTargetPot.savedAmount - newAmount).coerceAtLeast(0.0),
                updatedAt = System.currentTimeMillis()
            )
            appliedAcc = currentNewAcc.copy(
                currentBalance = currentNewAcc.currentBalance + newAmount,
                totalReceived = currentNewAcc.totalReceived + newAmount
            )
        }

        if (appliedAcc.currentBalance < -0.0001) {
            throw IllegalStateException(
                "Insufficient balance in ${appliedAcc.name} after reversal."
            )
        }

        potsMap[newPotId] = appliedPot
        accountsMap[newAccountKey] = appliedAcc

        dao.updateSavingsPot(potsMap[oldPot.id]!!)
        if (newPotId != oldPot.id) {
            dao.updateSavingsPot(potsMap[newPotId]!!)
        }
        if (oldAccount != null && !oldAccount.name.equals(newAccountKey, ignoreCase = true)) {
            dao.updateAccount(accountsMap[oldAccount.name]!!)
        }
        dao.updateAccount(accountsMap[newAccountKey]!!)

        val snap = buildAccountSnapshotBalances(accountsMap)

        val dayOfWeek = DateUtils.getDayOfWeek(newDate)
        val canonicalTime = DateUtils.to24HourTime(newTime)
        val cleanNote = newNote.trim()
        val desc = if (newType == "DEPOSIT") "Saved to ${appliedPot.name}" else "Withdrawn from ${appliedPot.name}"

        var txId = oldEntry.transactionId
        val existingTx = if (txId > 0) dao.getTransactionById(txId) else null
        if (existingTx != null) {
            val updatedTx = existingTx.copy(
                date = newDate,
                time = canonicalTime,
                dayOfWeek = dayOfWeek,
                type = "SAVINGS",
                amount = newAmount,
                fromAccount = if (newType == "DEPOSIT") appliedAcc.name else appliedPot.name,
                toAccount = if (newType == "DEPOSIT") appliedPot.name else appliedAcc.name,
                category = "Savings",
                comments = cleanNote.ifBlank { desc },
                description = desc,
                hblBalance = snap.hbl,
                ublBalance = snap.ubl,
                sadapayBalance = snap.sadapay,
                easypaisaBalance = snap.easypaisa,
                jazzCashBalance = snap.jazzCash,
                mashreqBalance = snap.mashreq,
                walletBalance = snap.wallet,
                totalAccountBalance = snap.total,
                updatedAt = System.currentTimeMillis()
            )
            dao.updateTransaction(updatedTx)
        } else {
            val nextSerial = (dao.getMaxSerialNumberForUser(uid) ?: 0) + 1
            txId = dao.insertTransaction(
                TransactionEntity(
                    userId = uid,
                    serialNumber = nextSerial,
                    date = newDate,
                    time = canonicalTime,
                    dayOfWeek = dayOfWeek,
                    type = "SAVINGS",
                    amount = newAmount,
                    fromAccount = if (newType == "DEPOSIT") appliedAcc.name else appliedPot.name,
                    toAccount = if (newType == "DEPOSIT") appliedPot.name else appliedAcc.name,
                    category = "Savings",
                    comments = cleanNote.ifBlank { desc },
                    description = desc,
                    hblBalance = snap.hbl,
                    ublBalance = snap.ubl,
                    sadapayBalance = snap.sadapay,
                    easypaisaBalance = snap.easypaisa,
                    jazzCashBalance = snap.jazzCash,
                    mashreqBalance = snap.mashreq,
                    walletBalance = snap.wallet,
                    totalAccountBalance = snap.total
                )
            )
        }

        dao.updateSavingsEntry(
            oldEntry.copy(
                potId = newPotId,
                type = newType,
                amount = newAmount,
                accountName = appliedAcc.name,
                date = newDate,
                time = canonicalTime,
                dayOfWeek = dayOfWeek,
                note = cleanNote,
                transactionId = txId,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteSavingsEntry(entryId: Long) = runAtomic {
        deleteSavingsEntryInternal(entryId)
    }

    private suspend fun deleteSavingsEntryInternal(entryId: Long) {
        val entry = dao.getSavingsEntryById(entryId) ?: return
        val uid = entry.userId
        val pot = dao.getSavingsPotById(entry.potId)
        val acc = dao.getAllAccountsDirectForUser(uid).find { it.name.equals(entry.accountName, ignoreCase = true) }

        if (entry.type == "DEPOSIT") {
            if (pot != null) {
                if (pot.savedAmount - entry.amount < -0.0001) {
                    throw IllegalStateException(
                        "Cannot delete deposit: funds have already been withdrawn from ${pot.name}."
                    )
                }
                dao.updateSavingsPot(
                    pot.copy(
                        savedAmount = (pot.savedAmount - entry.amount).coerceAtLeast(0.0),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            if (acc != null) {
                dao.updateAccount(
                    acc.copy(
                        currentBalance = acc.currentBalance + entry.amount,
                        totalTransferredOut = (acc.totalTransferredOut - entry.amount).coerceAtLeast(0.0)
                    )
                )
            }
        } else {
            if (acc != null) {
                if (acc.currentBalance - entry.amount < -0.0001) {
                    throw IllegalStateException(
                        "Cannot delete withdrawal: withdrawn funds in ${acc.name} have already been spent."
                    )
                }
                dao.updateAccount(
                    acc.copy(
                        currentBalance = acc.currentBalance - entry.amount,
                        totalReceived = (acc.totalReceived - entry.amount).coerceAtLeast(0.0)
                    )
                )
            }
            if (pot != null) {
                dao.updateSavingsPot(
                    pot.copy(
                        savedAmount = pot.savedAmount + entry.amount,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        if (entry.transactionId > 0) {
            dao.deleteTransactionById(entry.transactionId)
        }
        dao.deleteSavingsEntryById(entryId)
    }

    private fun getDayOfWeek(dateString: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Karachi")
            }
            val parsed = sdf.parse(dateString) ?: Date()
            val dayFormat = SimpleDateFormat("EEEE", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Karachi")
            }
            dayFormat.format(parsed)
        } catch (_: Exception) {
            "Monday"
        }
    }
}
