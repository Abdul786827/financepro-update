package com.example.data.local

import androidx.room.*
import com.example.data.model.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object (DAO) for Transactions table, strictly scoped per userId.
 */
@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY serialNumber DESC, id DESC")
    fun getAllTransactionsForUser(userId: Int): Flow<List<TransactionEntity>>

    fun getAllTransactions(): Flow<List<TransactionEntity>> = getAllTransactionsForUser(1)

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY serialNumber ASC, id ASC")
    suspend fun getAllTransactionsAscForUser(userId: Int): List<TransactionEntity>

    suspend fun getAllTransactionsAsc(): List<TransactionEntity> = getAllTransactionsAscForUser(1)

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE userId = :userId AND category = :category ORDER BY date DESC, time DESC")
    fun getTransactionsByCategoryForUser(userId: Int, category: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND type = :type ORDER BY date DESC, time DESC")
    fun getTransactionsByTypeForUser(userId: Int, type: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND date LIKE :yearMonth || '%' ORDER BY serialNumber ASC, id ASC")
    fun getTransactionsByMonthForUser(userId: Int, yearMonth: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND date LIKE :yearMonth || '%' ORDER BY serialNumber ASC, id ASC")
    suspend fun getTransactionsByMonthDirectForUser(userId: Int, yearMonth: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC, time DESC")
    fun getTransactionsByDateRangeForUser(userId: Int, startDate: String, endDate: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND (fromAccount = :accountName OR toAccount = :accountName) ORDER BY date DESC, time DESC")
    fun getTransactionsByAccountForUser(userId: Int, accountName: String): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("SELECT MAX(serialNumber) FROM transactions WHERE userId = :userId")
    suspend fun getMaxSerialNumberForUser(userId: Int): Int?

    @Query("SELECT COUNT(*) FROM transactions WHERE userId = :userId")
    suspend fun getTransactionCountForUser(userId: Int): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE userId = :userId AND (LOWER(fromAccount) = LOWER(:accountName) OR LOWER(toAccount) = LOWER(:accountName))")
    suspend fun countTransactionsForAccountForUser(userId: Int, accountName: String): Int

    @Query("SELECT * FROM transactions WHERE userId = :userId AND (LOWER(fromAccount) = LOWER(:accountName) OR LOWER(toAccount) = LOWER(:accountName)) ORDER BY serialNumber ASC, id ASC")
    suspend fun getTransactionsForAccountDirectForUser(userId: Int, accountName: String): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE userId = :userId AND (LOWER(fromAccount) = LOWER(:accountName) OR LOWER(toAccount) = LOWER(:accountName))")
    suspend fun deleteTransactionsByAccountForUser(userId: Int, accountName: String)

    @Query("UPDATE transactions SET fromAccount = :newName WHERE userId = :userId AND LOWER(fromAccount) = LOWER(:oldName)")
    suspend fun renameFromAccountInTransactionsForUser(userId: Int, oldName: String, newName: String)

    @Query("UPDATE transactions SET toAccount = :newName WHERE userId = :userId AND LOWER(toAccount) = LOWER(:oldName)")
    suspend fun renameToAccountInTransactionsForUser(userId: Int, oldName: String, newName: String)

    @Query("UPDATE transactions SET isTaxRelated = :isTaxRelated, taxSectionType = :taxSectionType, taxCategory = :taxCategory, taxYearId = :taxYearId, updatedAt = :updatedAt WHERE userId = :userId AND id IN (:ids)")
    suspend fun bulkUpdateTransactionTaxTag(
        userId: Int,
        ids: List<Long>,
        isTaxRelated: Boolean,
        taxSectionType: String,
        taxCategory: String,
        taxYearId: String,
        updatedAt: Long
    )

    @Query("UPDATE transactions SET isTaxRelated = 0, taxSectionType = '', taxCategory = '', taxYearId = '' WHERE userId = :userId AND isTaxRelated = 1")
    suspend fun clearAllTaxTagsOnTransactionsForUser(userId: Int)

    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun deleteAllTransactionsForUser(userId: Int)
}
