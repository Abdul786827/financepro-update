package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.example.data.local.FinanceDatabase
import com.example.data.model.*
import com.example.data.repository.FinanceRepository
import com.example.data.repository.TaxRepository
import com.example.util.AppUpdateChecker
import com.example.util.DateUtils
import com.example.util.PakistanTaxYearUtils
import com.example.util.SecurityUtils
import com.example.util.TaxCryptoHelper
import com.example.util.TaxReminderHelper
import com.example.util.UpdateCheckResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository
    private val taxRepository: TaxRepository
    val isExternalPickerActive = MutableStateFlow(false)

    init {
        val database = FinanceDatabase.getDatabase(application)
        repository = FinanceRepository(database.financeDao(), database)
        taxRepository = TaxRepository(database.financeDao(), database.taxDao(), database)
        viewModelScope.launch {
            repository.initializeIfEmpty()
        }
    }

    fun setExternalPickerActive(active: Boolean) {
        isExternalPickerActive.value = active
    }

    val accounts = repository.accounts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val visibleAccounts = accounts.map { list ->
        list.filter { !it.isHidden }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val transactions = repository.transactions.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val petrolExpenses = repository.petrolExpenses.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val extraExpenses = repository.extraExpenses.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val salaryRecords = repository.salaryRecords.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val loanRecords = repository.loanRecords.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val monthlyStatements = repository.monthlyStatements.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val userProfile = repository.userProfile.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val categories = repository.categories.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Separate Udhaar Ledger states
    val udhaarRecords = repository.udhaarRecords.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val udhaarRepayments = repository.udhaarRepayments.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Udhaar Summary Metrics (Completely separate from bank/wallet accounts)
    val udhaarTotalToReceive = udhaarRecords.map { list ->
        list.filter { it.type == "GIVEN" && it.category == "MONEY" && it.status != "SETTLED" }
            .sumOf { it.remainingAmount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val udhaarTotalToPay = udhaarRecords.map { list ->
        list.filter { it.type == "TAKEN" && it.category == "MONEY" && it.status != "SETTLED" }
            .sumOf { it.remainingAmount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val udhaarNetBalance = combine(udhaarTotalToReceive, udhaarTotalToPay) { recv, pay ->
        recv - pay
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val udhaarPendingItemsCount = udhaarRecords.map { list ->
        list.count { it.category == "ITEM" && !it.isItemReturned }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Separate Savings Ledger states (never included in totalAvailableBalance or income/expenses)
    val savingsPots = repository.savingsPots.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val savingsEntries = repository.savingsEntries.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val totalSavings = savingsPots.map { list ->
        list.sumOf { it.savedAmount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // --- PAKISTAN TAXPAYER MODE STATEFLOWS ---
    val taxProfile = taxRepository.taxProfile.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val taxYears = taxRepository.taxYears.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val taxEntries = taxRepository.taxEntries.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val taxWealthItems = taxRepository.taxWealthItems.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val taxDocuments = taxRepository.taxDocuments.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val taxCustomCategories = taxRepository.taxCustomCategories.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val taxYearCount = taxRepository.taxYearCount.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0
    )

    val selectedTaxYear = MutableStateFlow(PakistanTaxYearUtils.currentTaxYear())

    // Filter states
    val searchQuery = MutableStateFlow("")
    val selectedAccountFilter = MutableStateFlow<String?>(null)
    val selectedTypeFilter = MutableStateFlow<String?>(null)
    val selectedMonth = MutableStateFlow("2026-11")
    val selectedYear = MutableStateFlow(2026)

    // Computed Dashboard Metrics
    val totalAvailableBalance = accounts.map { list ->
        list.sumOf { it.currentBalance }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalIncome = transactions.map { list ->
        list.filter { it.type == "INCOME" }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalExpenses = transactions.map { list ->
        list.filter { it.type in listOf("EXPENSE", "PETROL", "EXTRA_EXPENSE") }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalTransfers = transactions.map { list ->
        list.filter { it.type == "TRANSFER" }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalPetrol = petrolExpenses.map { list ->
        list.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalExtra = extraExpenses.map { list ->
        list.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Filtered transactions for Daily Statement
    val filteredTransactions = combine(
        transactions,
        searchQuery,
        selectedAccountFilter,
        selectedTypeFilter
    ) { txList, query, accFilter, typeFilter ->
        txList.filter { tx ->
            val matchesQuery = query.isBlank() ||
                    tx.comments.contains(query, ignoreCase = true) ||
                    tx.description.contains(query, ignoreCase = true) ||
                    tx.fromAccount.contains(query, ignoreCase = true) ||
                    tx.toAccount.contains(query, ignoreCase = true) ||
                    tx.category.contains(query, ignoreCase = true) ||
                    tx.type.contains(query, ignoreCase = true) ||
                    (tx.type == "TRANSFER" && ("transfer".contains(query.trim(), ignoreCase = true) || "${tx.fromAccount} -> ${tx.toAccount}".contains(query.trim(), ignoreCase = true))) ||
                    tx.amount.toString().contains(query)

            val matchesAccount = accFilter == null ||
                    tx.fromAccount.equals(accFilter, ignoreCase = true) ||
                    tx.toAccount.equals(accFilter, ignoreCase = true)

            val matchesType = typeFilter == null || tx.type.equals(typeFilter, ignoreCase = true)

            matchesQuery && matchesAccount && matchesType
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTransaction(
        date: String = DateUtils.getCurrentDate(),
        time: String = DateUtils.getCurrentTime(),
        type: String,
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
        taxYearId: String = "",
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.addTransaction(
                date = date,
                time = time,
                type = type,
                amount = amount,
                fromAccount = fromAccount,
                toAccount = toAccount,
                category = category,
                comments = comments,
                description = description,
                extraItem = extraItem,
                petrolLiters = petrolLiters,
                petrolRate = petrolRate,
                isTaxRelated = isTaxRelated,
                taxSectionType = taxSectionType,
                taxCategory = taxCategory,
                taxYearId = taxYearId
            )
            onComplete()
        }
    }

    fun deleteTransaction(tx: TransactionEntity, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.deleteTransaction(tx)
            } catch (e: Exception) {
                onError(e.message ?: "Unable to delete transaction")
            }
        }
    }

    fun updateTransaction(
        oldTx: TransactionEntity,
        date: String,
        time: String,
        type: String,
        amount: Double,
        fromAccount: String,
        toAccount: String,
        category: String,
        comments: String = "",
        extraItem: String = "",
        petrolLiters: Double = 0.0,
        petrolRate: Double = 0.0,
        isTaxRelated: Boolean = oldTx.isTaxRelated,
        taxSectionType: String = oldTx.taxSectionType,
        taxCategory: String = oldTx.taxCategory,
        taxYearId: String = oldTx.taxYearId,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.updateTransaction(
                oldTx = oldTx,
                newDate = date,
                newTime = time,
                newType = type,
                newAmount = amount,
                newFromAccount = fromAccount,
                newToAccount = toAccount,
                newCategory = category,
                newComments = comments,
                newExtraItem = extraItem,
                newPetrolLiters = petrolLiters,
                newPetrolRate = petrolRate,
                isTaxRelated = isTaxRelated,
                taxSectionType = taxSectionType,
                taxCategory = taxCategory,
                taxYearId = taxYearId
            )
            onComplete()
        }
    }

    fun bulkTagTransactionsForTax(
        ids: List<Long>,
        isTaxRelated: Boolean,
        taxSectionType: String,
        taxCategory: String,
        taxYearId: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.bulkTagTransactionsForTax(
                ids = ids,
                isTaxRelated = isTaxRelated,
                taxSectionType = taxSectionType,
                taxCategory = taxCategory,
                taxYearId = taxYearId
            )
            onComplete()
        }
    }

    fun addSalary(
        month: String,
        gross: Double,
        preExisting: Double,
        received: Double,
        account: String,
        comments: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.addSalaryRecord(
                salaryDate = month,
                gross = gross,
                preExisting = preExisting,
                received = received,
                account = account,
                comments = comments
            )
            onComplete()
        }
    }

    fun addLoan(
        date: String,
        personOrSource: String,
        amount: Double,
        description: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.addLoan(date, personOrSource, amount, description)
            onComplete()
        }
    }

    fun payLoan(
        loan: LoanRecordEntity,
        amount: Double,
        account: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.payLoan(loan, amount, account)
            onComplete()
        }
    }

    val isLoggedIn = userProfile.map { it?.isLoggedIn == true }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    fun register(
        fullName: String,
        email: String,
        mobile: String,
        rawPassword: String,
        onResult: (token: String) -> Unit
    ) {
        viewModelScope.launch {
            val token = repository.registerUser(fullName, email, mobile, rawPassword)
            onResult(token)
        }
    }

    fun verifyEmail(token: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.verifyEmail(token)
            onResult(success)
        }
    }

    fun resendVerification(email: String? = null, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val token = repository.resendVerificationToken(email)
            onResult(token)
        }
    }

    fun login(email: String, password: String, onResult: (AuthResult) -> Unit) {
        viewModelScope.launch {
            val result = repository.loginUser(email, password)
            onResult(result)
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logoutUser()
        }
    }

    fun unlockWithBiometric(onResult: () -> Unit = {}) {
        viewModelScope.launch {
            repository.setSessionLoggedIn()
            onResult()
        }
    }

    fun resetPassword(email: String, newPass: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.resetPassword(email, newPass)
            onResult(success)
        }
    }

    fun changePassword(oldPass: String, newPass: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val (success, message) = repository.changePassword(oldPass, newPass)
            onResult(success, message)
        }
    }

    fun toggleStatementLock(id: String) {
        viewModelScope.launch {
            repository.toggleStatementLock(id)
        }
    }

    fun updateUserProfile(profile: UserProfileEntity) {
        viewModelScope.launch {
            repository.updateUserProfile(profile)
        }
    }

    fun updateProfile(fullName: String, email: String, mobile: String, profileImageBase64: String) {
        viewModelScope.launch {
            repository.updateProfileDetails(fullName, email, mobile, profileImageBase64)
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBiometricEnabled(enabled)
        }
    }

    fun setTaxpayerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setTaxpayerEnabled(enabled)
            if (enabled) {
                val curYear = selectedTaxYear.value
                val yearObj = taxRepository.ensureTaxYearExists(curYear)
                TaxReminderHelper.checkAndNotifyFilingDeadlines(getApplication(), listOf(yearObj))
            }
        }
    }

    fun verifyCurrentUserPassword(rawPassword: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repository.verifyCurrentUserPassword(rawPassword)
            onResult(ok)
        }
    }

    fun resetAllDataToZero(
        deleteAccounts: Boolean = false,
        deleteTaxData: Boolean = false,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.resetAllDataToZero(
                deleteAccounts = deleteAccounts,
                deleteTaxData = deleteTaxData,
                onDeleteTaxData = { uid ->
                    taxRepository.clearAllTaxDataForUser(getApplication(), uid)
                }
            )
            onComplete()
        }
    }

    fun resetToDemoData() {
        viewModelScope.launch {
            repository.resetAllDataToZero(deleteAccounts = false)
        }
    }

    fun checkForAppUpdate(onResult: (UpdateCheckResult) -> Unit) {
        viewModelScope.launch {
            val result = try {
                AppUpdateChecker.checkForUpdate()
            } catch (e: Exception) {
                UpdateCheckResult.Error(
                    "Unable to check for updates right now. Please check your connection and try again."
                )
            }
            onResult(result)
        }
    }

    // --- UDHAAR VIEWMODEL ACTIONS ---
    fun addUdhaar(
        type: String,
        personName: String,
        phoneNumber: String = "",
        category: String = "MONEY",
        amount: Double = 0.0,
        accountName: String = "",
        itemDescription: String = "",
        date: String = DateUtils.getCurrentDate(),
        time: String = DateUtils.getCurrentTime(),
        dueDate: String = "",
        note: String = "",
        onError: (String) -> Unit = {},
        onSuccess: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val id = repository.addUdhaarRecord(
                    type = type,
                    personName = personName,
                    phoneNumber = phoneNumber,
                    category = category,
                    amount = amount,
                    accountName = accountName,
                    itemDescription = itemDescription,
                    date = date,
                    time = time,
                    dueDate = dueDate,
                    note = note
                )
                onSuccess(id)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to save Udhaar record")
            }
        }
    }

    fun updateUdhaar(
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
        note: String = "",
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.updateUdhaarRecord(
                    id = id,
                    type = type,
                    personName = personName,
                    phoneNumber = phoneNumber,
                    category = category,
                    amount = amount,
                    accountName = accountName,
                    itemDescription = itemDescription,
                    date = date,
                    time = time,
                    dueDate = dueDate,
                    note = note
                )
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update Udhaar record")
            }
        }
    }

    fun deleteUdhaar(id: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteUdhaarRecord(id)
            onComplete()
        }
    }

    fun markUdhaarItemReturned(id: Long, isReturned: Boolean = true, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.markItemReturned(id, isReturned)
            onComplete()
        }
    }

    fun addUdhaarRepayment(
        udhaarId: Long,
        amount: Double,
        accountName: String = "",
        date: String = DateUtils.getCurrentDate(),
        time: String = DateUtils.getCurrentTime(),
        note: String = "",
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.addUdhaarRepayment(
                    udhaarId = udhaarId,
                    amount = amount,
                    accountName = accountName,
                    date = date,
                    time = time,
                    note = note
                )
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to save repayment")
            }
        }
    }

    fun deleteUdhaarRepayment(repaymentId: Long, udhaarId: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteUdhaarRepayment(repaymentId, udhaarId)
            onComplete()
        }
    }

    fun updateUdhaarRepayment(
        repaymentId: Long,
        udhaarId: Long,
        amount: Double,
        accountName: String = "",
        date: String,
        time: String,
        note: String,
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.updateUdhaarRepayment(
                    repaymentId = repaymentId,
                    udhaarId = udhaarId,
                    newAmount = amount,
                    newAccountName = accountName,
                    date = date,
                    time = time,
                    note = note
                )
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update repayment")
            }
        }
    }

    // --- SAVINGS VIEWMODEL ACTIONS ---
    fun createSavingsPot(
        name: String,
        targetAmount: Double? = null,
        targetDate: String = "",
        onError: (String) -> Unit = {},
        onSuccess: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val id = repository.createSavingsPot(name, targetAmount, targetDate)
                onSuccess(id)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to create savings pot")
            }
        }
    }

    fun updateSavingsPot(
        id: Long,
        name: String,
        targetAmount: Double? = null,
        targetDate: String = "",
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.updateSavingsPot(id, name, targetAmount, targetDate)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update savings pot")
            }
        }
    }

    fun deleteSavingsPot(
        id: Long,
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.deleteSavingsPot(id)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete savings pot")
            }
        }
    }

    fun addSavingsEntry(
        potId: Long,
        type: String,
        amount: Double,
        accountName: String,
        date: String = DateUtils.getCurrentDate(),
        time: String = DateUtils.getCurrentTime(),
        note: String = "",
        onError: (String) -> Unit = {},
        onSuccess: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val id = repository.addSavingsEntry(
                    potId = potId,
                    type = type,
                    amount = amount,
                    accountName = accountName,
                    date = date,
                    time = time,
                    note = note
                )
                onSuccess(id)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to save savings entry")
            }
        }
    }

    fun updateSavingsEntry(
        entryId: Long,
        potId: Long,
        type: String,
        amount: Double,
        accountName: String,
        date: String,
        time: String,
        note: String,
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.updateSavingsEntry(
                    entryId = entryId,
                    newPotId = potId,
                    newType = type,
                    newAmount = amount,
                    newAccountName = accountName,
                    newDate = date,
                    newTime = time,
                    newNote = note
                )
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update savings entry")
            }
        }
    }

    fun deleteSavingsEntry(
        entryId: Long,
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.deleteSavingsEntry(entryId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete savings entry")
            }
        }
    }

    // --- ACCOUNT MANAGEMENT ACTIONS ---

    fun addAccount(
        accountType: String,
        name: String,
        holderTitle: String = "",
        accountNumberLast4: String = "",
        note: String = "",
        openingBalance: Double = 0.0,
        onError: (String) -> Unit = {},
        onSuccess: (AccountEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val created = repository.addAccount(
                    accountType = accountType,
                    name = name,
                    holderTitle = holderTitle,
                    accountNumberLast4 = accountNumberLast4,
                    note = note,
                    openingBalance = openingBalance
                )
                onSuccess(created)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to add account")
            }
        }
    }

    fun editAccount(
        id: String,
        newType: String,
        newName: String,
        holderTitle: String = "",
        accountNumberLast4: String = "",
        note: String = "",
        onError: (String) -> Unit = {},
        onSuccess: (AccountEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val updated = repository.editAccount(
                    id = id,
                    newType = newType,
                    newName = newName,
                    holderTitle = holderTitle,
                    accountNumberLast4 = accountNumberLast4,
                    note = note
                )
                onSuccess(updated)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update account")
            }
        }
    }

    fun checkAccountUsageCount(accountName: String, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.getAccountUsageCount(accountName)
            onResult(count)
        }
    }

    fun deleteAccount(
        id: String,
        mode: AccountDeleteMode = AccountDeleteMode.SIMPLE,
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.deleteAccount(
                    id = id,
                    mode = mode
                )
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete account")
            }
        }
    }

    fun toggleAccountHidden(id: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.toggleAccountHidden(id)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update account visibility")
            }
        }
    }

    fun moveAccountOrder(id: String, moveUp: Boolean) {
        viewModelScope.launch {
            repository.moveAccountOrder(id, moveUp)
        }
    }

    // --- PAKISTAN TAXPAYER MODE ACTIONS ---

    fun ensureCurrentTaxYearExists() {
        viewModelScope.launch {
            val yearObj = taxRepository.ensureTaxYearExists(selectedTaxYear.value)
            TaxReminderHelper.checkAndNotifyFilingDeadlines(getApplication(), listOf(yearObj))
        }
    }

    fun selectTaxYear(taxYear: Int) {
        selectedTaxYear.value = taxYear
    }

    fun decryptTaxSensitive(encrypted: String): String {
        return TaxCryptoHelper.decryptSensitive(getApplication(), encrypted)
    }

    fun saveTaxProfile(
        fullName: String,
        cnicPlain: String,
        ntnPlain: String,
        filerStatus: String,
        occupationType: String,
        note: String,
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                taxRepository.saveTaxProfile(
                    context = getApplication(),
                    fullName = fullName,
                    cnicPlain = cnicPlain,
                    ntnPlain = ntnPlain,
                    filerStatus = filerStatus,
                    occupationType = occupationType,
                    note = note
                )
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to save tax profile")
            }
        }
    }

    fun createTaxYear(
        taxYear: Int,
        filingDeadline: String,
        reminderDaysBefore: Int,
        onError: (String) -> Unit = {},
        onSuccess: (TaxYearEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val created = taxRepository.createTaxYear(taxYear, filingDeadline, reminderDaysBefore)
                selectedTaxYear.value = created.taxYear
                TaxReminderHelper.checkAndNotifyFilingDeadlines(getApplication(), listOf(created))
                onSuccess(created)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to create tax year")
            }
        }
    }

    fun updateTaxYearDeadline(
        taxYear: Int,
        filingDeadline: String,
        reminderDaysBefore: Int,
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                taxRepository.updateTaxYearDeadline(taxYear, filingDeadline, reminderDaysBefore)
                val updatedYear = taxYears.value.find { it.taxYear == taxYear }?.copy(
                    filingDeadline = filingDeadline,
                    reminderDaysBefore = reminderDaysBefore
                )
                if (updatedYear != null) {
                    TaxReminderHelper.checkAndNotifyFilingDeadlines(getApplication(), listOf(updatedYear))
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update deadline")
            }
        }
    }

    fun toggleTaxYearLock(
        taxYear: Int,
        lock: Boolean,
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                taxRepository.toggleTaxYearLock(taxYear, lock)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update lock status")
            }
        }
    }

    fun updateCustomTaxSlabs(
        taxYear: Int,
        slabs: List<TaxSlabItem>,
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                taxRepository.updateCustomSlabs(taxYear, slabs)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to save tax slabs")
            }
        }
    }

    fun saveTaxEntry(
        id: Long = 0L,
        taxYear: Int,
        entryGroup: String,
        category: String,
        amount: Double,
        date: String,
        sourceOrPayer: String = "",
        referenceNumber: String = "",
        proofNote: String = "",
        documentId: Long? = null,
        notes: String = "",
        onError: (String) -> Unit = {},
        onSuccess: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val savedId = taxRepository.saveTaxEntry(
                    id = id,
                    taxYear = taxYear,
                    entryGroup = entryGroup,
                    category = category,
                    amount = amount,
                    date = date,
                    sourceOrPayer = sourceOrPayer,
                    referenceNumber = referenceNumber,
                    proofNote = proofNote,
                    documentId = documentId,
                    notes = notes
                )
                onSuccess(savedId)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to save tax record")
            }
        }
    }

    fun deleteTaxEntry(
        entryId: Long,
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                taxRepository.deleteTaxEntry(entryId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete tax record")
            }
        }
    }

    fun saveWealthItem(
        id: Long = 0L,
        taxYear: Int,
        periodType: String,
        assetCategory: String,
        title: String,
        amount: Double,
        isLiability: Boolean,
        notes: String = "",
        onError: (String) -> Unit = {},
        onSuccess: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val savedId = taxRepository.saveWealthItem(
                    id = id,
                    taxYear = taxYear,
                    periodType = periodType,
                    assetCategory = assetCategory,
                    title = title,
                    amount = amount,
                    isLiability = isLiability,
                    notes = notes
                )
                onSuccess(savedId)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to save wealth item")
            }
        }
    }

    fun deleteWealthItem(
        itemId: Long,
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                taxRepository.deleteWealthItem(itemId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete wealth item")
            }
        }
    }

    fun autoFillBankBalancesForWealth(
        taxYear: Int,
        periodType: String,
        onError: (String) -> Unit = {},
        onSuccess: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val count = taxRepository.autoFillBankBalancesForWealth(taxYear, periodType)
                onSuccess(count)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to auto-fill bank balances")
            }
        }
    }

    fun autoPullUdhaarForWealth(
        taxYear: Int,
        periodType: String,
        onError: (String) -> Unit = {},
        onSuccess: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val count = taxRepository.autoPullUdhaarForWealth(taxYear, periodType)
                onSuccess(count)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to pull Udhaar balances")
            }
        }
    }

    fun saveTaxDocumentFromUri(
        taxYear: Int,
        title: String,
        category: String,
        uri: Uri,
        onError: (String) -> Unit = {},
        onSuccess: (TaxDocumentEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val doc = taxRepository.saveDocumentFromUri(
                    context = getApplication(),
                    taxYear = taxYear,
                    title = title,
                    category = category,
                    uri = uri
                )
                onSuccess(doc)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to save document")
            }
        }
    }

    fun deleteTaxDocument(
        docId: Long,
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                taxRepository.deleteDocument(docId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete document")
            }
        }
    }

    fun createTaxBackupBytes(
        passphrase: String,
        scopeTaxYear: Int?,
        onError: (String) -> Unit = {},
        onSuccess: (ByteArray) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val bytes = taxRepository.createEncryptedBackupBytes(getApplication(), passphrase, scopeTaxYear)
                onSuccess(bytes)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to create encrypted backup")
            }
        }
    }

    fun restoreTaxBackupBytes(
        encryptedBytes: ByteArray,
        passphrase: String,
        onError: (String) -> Unit = {},
        onSuccess: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val restoredYears = taxRepository.restoreEncryptedBackupBytes(getApplication(), encryptedBytes, passphrase)
                onSuccess(restoredYears)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to restore backup. Check passphrase.")
            }
        }
    }

    fun buildTaxYearSummary(
        taxYear: Int,
        profile: TaxProfileEntity?,
        manualEntries: List<TaxEntryEntity>,
        allTransactions: List<TransactionEntity>,
        wealthItems: List<TaxWealthItemEntity>,
        documents: List<TaxDocumentEntity>
    ): TaxYearSummary {
        val decCnic = profile?.cnicEncrypted?.let { decryptTaxSensitive(it) }.orEmpty()
        val decNtn = profile?.ntnEncrypted?.let { decryptTaxSensitive(it) }.orEmpty()
        return taxRepository.buildYearSummary(
            taxYear = taxYear,
            profile = profile,
            decryptedCnic = decCnic,
            decryptedNtn = decNtn,
            manualEntries = manualEntries,
            linkedTransactions = allTransactions,
            wealthItems = wealthItems,
            documents = documents
        )
    }

    fun getMergedTaxItemsForYear(
        taxYear: Int,
        manualEntries: List<TaxEntryEntity>,
        allTransactions: List<TransactionEntity>
    ): List<TaxDisplayItem> {
        return taxRepository.mergeEntriesAndLinkedTransactions(taxYear, manualEntries, allTransactions)
    }
}
