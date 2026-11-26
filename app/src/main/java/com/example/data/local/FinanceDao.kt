package com.example.data.local

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao : TransactionDao {

    // Accounts (per user)
    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY orderIndex ASC")
    fun getAllAccountsForUser(userId: Int): Flow<List<AccountEntity>>

    fun getAllAccounts(): Flow<List<AccountEntity>> = getAllAccountsForUser(1)

    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY orderIndex ASC")
    suspend fun getAllAccountsDirectForUser(userId: Int): List<AccountEntity>

    suspend fun getAllAccountsDirect(): List<AccountEntity> = getAllAccountsDirectForUser(1)

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE userId = :userId AND LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getAccountByNameIgnoreCaseForUser(userId: Int, name: String): AccountEntity?

    suspend fun getAccountByNameIgnoreCase(name: String): AccountEntity? = getAccountByNameIgnoreCaseForUser(1, name)

    @Query("SELECT MAX(orderIndex) FROM accounts WHERE userId = :userId")
    suspend fun getMaxAccountOrderIndexForUser(userId: Int): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccountById(id: String)

    @Query("DELETE FROM accounts WHERE userId = :userId")
    suspend fun clearAccountsForUser(userId: Int)

    // Cross-table account usage counts, renames, and cascading per-account queries (scoped by userId)
    @Query("SELECT COUNT(*) FROM petrol_expenses WHERE userId = :userId AND LOWER(account) = LOWER(:accountName)")
    suspend fun countPetrolForAccountForUser(userId: Int, accountName: String): Int

    @Query("SELECT COUNT(*) FROM extra_expenses WHERE userId = :userId AND LOWER(account) = LOWER(:accountName)")
    suspend fun countExtraForAccountForUser(userId: Int, accountName: String): Int

    @Query("SELECT COUNT(*) FROM salary_records WHERE userId = :userId AND LOWER(depositedAccount) = LOWER(:accountName)")
    suspend fun countSalaryForAccountForUser(userId: Int, accountName: String): Int

    @Query("SELECT COUNT(*) FROM udhaar_records WHERE userId = :userId AND LOWER(accountName) = LOWER(:accountName)")
    suspend fun countUdhaarRecordsForAccountForUser(userId: Int, accountName: String): Int

    @Query("SELECT COUNT(*) FROM udhaar_repayments WHERE userId = :userId AND LOWER(accountName) = LOWER(:accountName)")
    suspend fun countUdhaarRepaymentsForAccountForUser(userId: Int, accountName: String): Int

    @Query("SELECT COUNT(*) FROM savings_entries WHERE userId = :userId AND LOWER(accountName) = LOWER(:accountName)")
    suspend fun countSavingsEntriesForAccountForUser(userId: Int, accountName: String): Int

    @Query("UPDATE petrol_expenses SET account = :newName WHERE userId = :userId AND LOWER(account) = LOWER(:oldName)")
    suspend fun renameAccountInPetrolForUser(userId: Int, oldName: String, newName: String)

    @Query("UPDATE extra_expenses SET account = :newName WHERE userId = :userId AND LOWER(account) = LOWER(:oldName)")
    suspend fun renameAccountInExtraForUser(userId: Int, oldName: String, newName: String)

    @Query("UPDATE salary_records SET depositedAccount = :newName WHERE userId = :userId AND LOWER(depositedAccount) = LOWER(:oldName)")
    suspend fun renameAccountInSalaryForUser(userId: Int, oldName: String, newName: String)

    @Query("UPDATE udhaar_records SET accountName = :newName WHERE userId = :userId AND LOWER(accountName) = LOWER(:oldName)")
    suspend fun renameAccountInUdhaarRecordsForUser(userId: Int, oldName: String, newName: String)

    @Query("UPDATE udhaar_repayments SET accountName = :newName WHERE userId = :userId AND LOWER(accountName) = LOWER(:oldName)")
    suspend fun renameAccountInUdhaarRepaymentsForUser(userId: Int, oldName: String, newName: String)

    @Query("UPDATE savings_entries SET accountName = :newName WHERE userId = :userId AND LOWER(accountName) = LOWER(:oldName)")
    suspend fun renameAccountInSavingsEntriesForUser(userId: Int, oldName: String, newName: String)

    @Query("DELETE FROM petrol_expenses WHERE userId = :userId AND LOWER(account) = LOWER(:accountName)")
    suspend fun deletePetrolByAccountForUser(userId: Int, accountName: String)

    @Query("DELETE FROM extra_expenses WHERE userId = :userId AND LOWER(account) = LOWER(:accountName)")
    suspend fun deleteExtraByAccountForUser(userId: Int, accountName: String)

    @Query("DELETE FROM salary_records WHERE userId = :userId AND LOWER(depositedAccount) = LOWER(:accountName)")
    suspend fun deleteSalaryByAccountForUser(userId: Int, accountName: String)

    @Query("SELECT * FROM udhaar_records WHERE userId = :userId AND LOWER(accountName) = LOWER(:accountName)")
    suspend fun getUdhaarRecordsForAccountDirectForUser(userId: Int, accountName: String): List<UdhaarRecordEntity>

    @Query("SELECT * FROM udhaar_repayments WHERE userId = :userId AND LOWER(accountName) = LOWER(:accountName)")
    suspend fun getUdhaarRepaymentsForAccountDirectForUser(userId: Int, accountName: String): List<UdhaarRepaymentEntity>

    @Query("SELECT * FROM savings_entries WHERE userId = :userId AND LOWER(accountName) = LOWER(:accountName)")
    suspend fun getSavingsEntriesForAccountDirectForUser(userId: Int, accountName: String): List<SavingsEntryEntity>

    // Petrol Expenses
    @Query("SELECT * FROM petrol_expenses WHERE userId = :userId ORDER BY serialNumber DESC, id DESC")
    fun getAllPetrolExpensesForUser(userId: Int): Flow<List<PetrolExpenseEntity>>

    @Query("SELECT * FROM petrol_expenses WHERE userId = :userId ORDER BY serialNumber DESC, id DESC")
    suspend fun getAllPetrolExpensesDirectForUser(userId: Int): List<PetrolExpenseEntity>

    @Query("SELECT * FROM petrol_expenses WHERE userId = :userId AND date LIKE :yearMonth || '%' ORDER BY serialNumber ASC")
    fun getPetrolByMonthForUser(userId: Int, yearMonth: String): Flow<List<PetrolExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPetrolExpense(expense: PetrolExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPetrolExpenses(expenses: List<PetrolExpenseEntity>)

    @Delete
    suspend fun deletePetrolExpense(expense: PetrolExpenseEntity)

    @Query("DELETE FROM petrol_expenses WHERE id = :id")
    suspend fun deletePetrolById(id: Long)

    @Query("SELECT MAX(serialNumber) FROM petrol_expenses WHERE userId = :userId")
    suspend fun getMaxPetrolSerialNumberForUser(userId: Int): Int?

    // Extra Expenses
    @Query("SELECT * FROM extra_expenses WHERE userId = :userId ORDER BY serialNumber DESC, id DESC")
    fun getAllExtraExpensesForUser(userId: Int): Flow<List<ExtraExpenseEntity>>

    @Query("SELECT * FROM extra_expenses WHERE userId = :userId ORDER BY serialNumber DESC, id DESC")
    suspend fun getAllExtraExpensesDirectForUser(userId: Int): List<ExtraExpenseEntity>

    @Query("SELECT * FROM extra_expenses WHERE userId = :userId AND date LIKE :yearMonth || '%' ORDER BY serialNumber ASC")
    fun getExtraByMonthForUser(userId: Int, yearMonth: String): Flow<List<ExtraExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtraExpense(expense: ExtraExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtraExpenses(expenses: List<ExtraExpenseEntity>)

    @Delete
    suspend fun deleteExtraExpense(expense: ExtraExpenseEntity)

    @Query("DELETE FROM extra_expenses WHERE id = :id")
    suspend fun deleteExtraById(id: Long)

    @Query("SELECT MAX(serialNumber) FROM extra_expenses WHERE userId = :userId")
    suspend fun getMaxExtraSerialNumberForUser(userId: Int): Int?

    // Salary Records
    @Query("SELECT * FROM salary_records WHERE userId = :userId ORDER BY salaryDate DESC")
    fun getAllSalaryRecordsForUser(userId: Int): Flow<List<SalaryRecordEntity>>

    @Query("SELECT * FROM salary_records WHERE userId = :userId AND salaryDate = :salaryDate LIMIT 1")
    suspend fun getSalaryByDateForUser(userId: Int, salaryDate: String): SalaryRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalaryRecord(salary: SalaryRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalaryRecords(salaries: List<SalaryRecordEntity>)

    @Delete
    suspend fun deleteSalaryRecord(salary: SalaryRecordEntity)

    // Loan Records
    @Query("SELECT * FROM loan_records WHERE userId = :userId ORDER BY date DESC, id DESC")
    fun getAllLoanRecordsForUser(userId: Int): Flow<List<LoanRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoanRecord(loan: LoanRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoanRecords(loans: List<LoanRecordEntity>)

    @Update
    suspend fun updateLoanRecord(loan: LoanRecordEntity)

    @Delete
    suspend fun deleteLoanRecord(loan: LoanRecordEntity)

    // Monthly Statements
    @Query("SELECT * FROM monthly_statements WHERE userId = :userId ORDER BY year DESC, month DESC")
    fun getAllMonthlyStatementsForUser(userId: Int): Flow<List<MonthlyStatementEntity>>

    @Query("SELECT * FROM monthly_statements WHERE userId = :userId AND year = :year ORDER BY month ASC")
    fun getMonthlyStatementsForYearForUser(userId: Int, year: Int): Flow<List<MonthlyStatementEntity>>

    @Query("SELECT * FROM monthly_statements WHERE id = :id LIMIT 1")
    suspend fun getMonthlyStatementByRawId(id: String): MonthlyStatementEntity?

    @Query("SELECT * FROM monthly_statements WHERE userId = :userId AND year = :year AND month = :month LIMIT 1")
    suspend fun getMonthlyStatementForMonth(userId: Int, year: Int, month: Int): MonthlyStatementEntity?

    suspend fun getMonthlyStatementById(id: String, userId: Int = 1): MonthlyStatementEntity? {
        return getMonthlyStatementByRawId("u${userId}_$id") ?: getMonthlyStatementByRawId(id)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMonthlyStatement(statement: MonthlyStatementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMonthlyStatements(statements: List<MonthlyStatementEntity>)

    // User Profile & Multi-user Authentication
    @Query("SELECT * FROM user_profile ORDER BY isLoggedIn DESC, id DESC LIMIT 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile ORDER BY isLoggedIn DESC, id DESC LIMIT 1")
    suspend fun getUserProfileDirect(): UserProfileEntity?

    @Query("SELECT * FROM user_profile WHERE isLoggedIn = 1 ORDER BY id DESC LIMIT 1")
    suspend fun getLoggedInUser(): UserProfileEntity?

    @Query("SELECT COUNT(*) FROM user_profile")
    fun observeUserCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM user_profile")
    suspend fun getUserCountDirect(): Int

    @Query("SELECT * FROM user_profile WHERE LOWER(TRIM(email)) = LOWER(TRIM(:email)) ORDER BY id DESC LIMIT 1")
    suspend fun getUserByEmail(email: String): UserProfileEntity?

    @Query("SELECT * FROM user_profile WHERE verificationToken = :token ORDER BY id DESC LIMIT 1")
    suspend fun getUserByVerificationToken(token: String): UserProfileEntity?

    @Query("SELECT * FROM user_profile WHERE LOWER(TRIM(email)) = LOWER(TRIM(:email)) ORDER BY id DESC")
    suspend fun getAllUsersByEmail(email: String): List<UserProfileEntity>

    @Query("SELECT * FROM user_profile ORDER BY id ASC")
    suspend fun getAllUsers(): List<UserProfileEntity>

    @Query("SELECT * FROM user_profile ORDER BY id ASC LIMIT 1")
    suspend fun getAnyUserProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(userProfile: UserProfileEntity): Long

    @Query("UPDATE user_profile SET isLoggedIn = 0")
    suspend fun clearAllLoginSessions()

    // Partial updates - guaranteed to never overwrite passwordHash
    @Query("UPDATE user_profile SET fullName = :fullName, email = :email, mobile = :mobile, profileImageBase64 = :profileImageBase64 WHERE id = :id")
    suspend fun updateProfileDetails(id: Int, fullName: String, email: String, mobile: String, profileImageBase64: String)

    @Query("UPDATE user_profile SET passwordHash = :passwordHash WHERE id = :id")
    suspend fun updatePasswordHash(id: Int, passwordHash: String)

    @Query("UPDATE user_profile SET pinCode = :pinCode WHERE id = :id")
    suspend fun updatePinCode(id: Int, pinCode: String)

    @Query("UPDATE user_profile SET isBiometricEnabled = :enabled WHERE id = :id")
    suspend fun updateBiometricEnabled(id: Int, enabled: Boolean)

    @Query("UPDATE user_profile SET isTaxpayerEnabled = :enabled WHERE id = :id")
    suspend fun updateTaxpayerEnabled(id: Int, enabled: Boolean)

    @Query("UPDATE user_profile SET isLoggedIn = :isLoggedIn WHERE id = :id")
    suspend fun updateLoginSession(id: Int, isLoggedIn: Boolean)

    @Query("UPDATE user_profile SET isEmailVerified = :verified, accountStatus = :status WHERE id = :id")
    suspend fun updateEmailVerified(id: Int, verified: Boolean, status: String)

    @Query("UPDATE user_profile SET verificationToken = :token, verificationExpiry = :expiry WHERE id = :id")
    suspend fun updateVerificationToken(id: Int, token: String, expiry: Long)

    @Query("DELETE FROM user_profile WHERE LOWER(TRIM(email)) = LOWER(TRIM(:email)) AND id != :keepId")
    suspend fun deleteDuplicateUsers(email: String, keepId: Int)

    @Query("DELETE FROM user_profile WHERE email = 'abdulquddus7745@gmail.com' AND passwordHash = '123456'")
    suspend fun deleteLegacyDemoUser()

    // Categories (global)
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    // Wipe / Clear for reset or restore (per user)
    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun clearTransactionsForUser(userId: Int)

    @Query("DELETE FROM petrol_expenses WHERE userId = :userId")
    suspend fun clearPetrolForUser(userId: Int)

    @Query("DELETE FROM extra_expenses WHERE userId = :userId")
    suspend fun clearExtraForUser(userId: Int)

    @Query("DELETE FROM salary_records WHERE userId = :userId")
    suspend fun clearSalaryForUser(userId: Int)

    @Query("DELETE FROM loan_records WHERE userId = :userId")
    suspend fun clearLoansForUser(userId: Int)

    @Query("DELETE FROM monthly_statements WHERE userId = :userId")
    suspend fun clearMonthlyStatementsForUser(userId: Int)

    // Udhaar Records (per user)
    @Query("SELECT * FROM udhaar_records WHERE userId = :userId ORDER BY date DESC, id DESC")
    fun getAllUdhaarRecordsForUser(userId: Int): Flow<List<UdhaarRecordEntity>>

    @Query("SELECT * FROM udhaar_records WHERE userId = :userId ORDER BY date DESC, id DESC")
    suspend fun getAllUdhaarRecordsDirectForUser(userId: Int): List<UdhaarRecordEntity>

    @Query("SELECT * FROM udhaar_records WHERE id = :id LIMIT 1")
    suspend fun getUdhaarRecordById(id: Long): UdhaarRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUdhaarRecord(record: UdhaarRecordEntity): Long

    @Update
    suspend fun updateUdhaarRecord(record: UdhaarRecordEntity)

    @Delete
    suspend fun deleteUdhaarRecord(record: UdhaarRecordEntity)

    @Query("DELETE FROM udhaar_records WHERE id = :id")
    suspend fun deleteUdhaarRecordById(id: Long)

    // Udhaar Repayments (per user)
    @Query("SELECT * FROM udhaar_repayments WHERE userId = :userId ORDER BY date ASC, id ASC")
    fun getAllUdhaarRepaymentsForUser(userId: Int): Flow<List<UdhaarRepaymentEntity>>

    @Query("SELECT * FROM udhaar_repayments WHERE udhaarId = :udhaarId ORDER BY date ASC, id ASC")
    fun getRepaymentsForUdhaar(udhaarId: Long): Flow<List<UdhaarRepaymentEntity>>

    @Query("SELECT * FROM udhaar_repayments WHERE udhaarId = :udhaarId ORDER BY date ASC, id ASC")
    suspend fun getRepaymentsForUdhaarDirect(udhaarId: Long): List<UdhaarRepaymentEntity>

    @Query("SELECT * FROM udhaar_repayments WHERE id = :id LIMIT 1")
    suspend fun getUdhaarRepaymentById(id: Long): UdhaarRepaymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUdhaarRepayment(repayment: UdhaarRepaymentEntity): Long

    @Update
    suspend fun updateUdhaarRepayment(repayment: UdhaarRepaymentEntity)

    @Delete
    suspend fun deleteUdhaarRepayment(repayment: UdhaarRepaymentEntity)

    @Query("DELETE FROM udhaar_repayments WHERE id = :id")
    suspend fun deleteUdhaarRepaymentById(id: Long)

    @Query("DELETE FROM udhaar_records WHERE userId = :userId")
    suspend fun clearUdhaarRecordsForUser(userId: Int)

    @Query("DELETE FROM udhaar_repayments WHERE userId = :userId")
    suspend fun clearUdhaarRepaymentsForUser(userId: Int)

    // Savings Pots (per user)
    @Query("SELECT * FROM savings_pots WHERE userId = :userId ORDER BY createdAt ASC, id ASC")
    fun getAllSavingsPotsForUser(userId: Int): Flow<List<SavingsPotEntity>>

    fun getAllSavingsPots(): Flow<List<SavingsPotEntity>> = getAllSavingsPotsForUser(1)

    @Query("SELECT * FROM savings_pots WHERE userId = :userId ORDER BY createdAt ASC, id ASC")
    suspend fun getAllSavingsPotsDirectForUser(userId: Int): List<SavingsPotEntity>

    suspend fun getAllSavingsPotsDirect(): List<SavingsPotEntity> = getAllSavingsPotsDirectForUser(1)

    @Query("SELECT * FROM savings_pots WHERE id = :id LIMIT 1")
    suspend fun getSavingsPotById(id: Long): SavingsPotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavingsPot(pot: SavingsPotEntity): Long

    @Update
    suspend fun updateSavingsPot(pot: SavingsPotEntity)

    @Query("DELETE FROM savings_pots WHERE id = :id")
    suspend fun deleteSavingsPotById(id: Long)

    // Savings Entries (per user)
    @Query("SELECT * FROM savings_entries WHERE userId = :userId ORDER BY date DESC, time DESC, id DESC")
    fun getAllSavingsEntriesForUser(userId: Int): Flow<List<SavingsEntryEntity>>

    fun getAllSavingsEntries(): Flow<List<SavingsEntryEntity>> = getAllSavingsEntriesForUser(1)

    @Query("SELECT * FROM savings_entries WHERE potId = :potId ORDER BY date DESC, time DESC, id DESC")
    fun getSavingsEntriesForPot(potId: Long): Flow<List<SavingsEntryEntity>>

    @Query("SELECT * FROM savings_entries WHERE potId = :potId ORDER BY date ASC, id ASC")
    suspend fun getSavingsEntriesForPotDirect(potId: Long): List<SavingsEntryEntity>

    @Query("SELECT * FROM savings_entries WHERE id = :id LIMIT 1")
    suspend fun getSavingsEntryById(id: Long): SavingsEntryEntity?

    @Query("SELECT * FROM savings_entries WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getSavingsEntryByTransactionId(transactionId: Long): SavingsEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavingsEntry(entry: SavingsEntryEntity): Long

    @Update
    suspend fun updateSavingsEntry(entry: SavingsEntryEntity)

    @Query("DELETE FROM savings_entries WHERE id = :id")
    suspend fun deleteSavingsEntryById(id: Long)

    @Query("DELETE FROM savings_entries WHERE userId = :userId")
    suspend fun clearSavingsEntriesForUser(userId: Int)

    @Query("DELETE FROM savings_pots WHERE userId = :userId")
    suspend fun clearSavingsPotsForUser(userId: Int)
}
