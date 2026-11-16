package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FinanceDatabase
import com.example.data.model.*
import com.example.data.repository.FinanceRepository
import com.example.util.AppUpdateChecker
import com.example.util.DateUtils
import com.example.util.SecurityUtils
import com.example.util.UpdateCheckResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository
    val isExternalPickerActive = MutableStateFlow(false)

    init {
        val database = FinanceDatabase.getDatabase(application)
        repository = FinanceRepository(database.financeDao(), database)
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
                petrolRate = petrolRate
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
                newPetrolRate = petrolRate
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

    fun resetAllDataToZero(deleteAccounts: Boolean = false, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.resetAllDataToZero(deleteAccounts = deleteAccounts)
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
}
