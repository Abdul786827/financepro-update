package com.example.data.local

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaxDao {

    // Tax Profile (per user)
    @Query("SELECT * FROM tax_profiles WHERE userId = :userId ORDER BY id DESC LIMIT 1")
    fun observeTaxProfileForUser(userId: Int): Flow<TaxProfileEntity?>

    @Query("SELECT * FROM tax_profiles WHERE userId = :userId ORDER BY id DESC LIMIT 1")
    suspend fun getTaxProfileDirectForUser(userId: Int): TaxProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaxProfile(profile: TaxProfileEntity): Long

    @Update
    suspend fun updateTaxProfile(profile: TaxProfileEntity)

    @Query("DELETE FROM tax_profiles WHERE userId = :userId")
    suspend fun clearTaxProfileForUser(userId: Int)

    // Tax Years (per user)
    @Query("SELECT * FROM tax_years WHERE userId = :userId ORDER BY taxYear DESC")
    fun observeTaxYearsForUser(userId: Int): Flow<List<TaxYearEntity>>

    @Query("SELECT * FROM tax_years WHERE userId = :userId ORDER BY taxYear DESC")
    suspend fun getTaxYearsDirectForUser(userId: Int): List<TaxYearEntity>

    @Query("SELECT * FROM tax_years WHERE userId = :userId AND taxYear = :taxYear LIMIT 1")
    suspend fun getTaxYearDirectForUser(userId: Int, taxYear: Int): TaxYearEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaxYear(year: TaxYearEntity): Long

    @Update
    suspend fun updateTaxYear(year: TaxYearEntity)

    @Query("DELETE FROM tax_years WHERE userId = :userId AND taxYear = :taxYear")
    suspend fun deleteTaxYearForUser(userId: Int, taxYear: Int)

    @Query("DELETE FROM tax_years WHERE userId = :userId")
    suspend fun clearTaxYearsForUser(userId: Int)

    // Tax Entries (Income, Withholding, Direct Tax, Deductions, Expenses)
    @Query("SELECT * FROM tax_entries WHERE userId = :userId ORDER BY date DESC, id DESC")
    fun observeAllTaxEntriesForUser(userId: Int): Flow<List<TaxEntryEntity>>

    @Query("SELECT * FROM tax_entries WHERE userId = :userId AND taxYear = :taxYear ORDER BY date DESC, id DESC")
    suspend fun getTaxEntriesForYearDirect(userId: Int, taxYear: Int): List<TaxEntryEntity>

    @Query("SELECT * FROM tax_entries WHERE userId = :userId ORDER BY date DESC, id DESC")
    suspend fun getAllTaxEntriesDirectForUser(userId: Int): List<TaxEntryEntity>

    @Query("SELECT * FROM tax_entries WHERE id = :id LIMIT 1")
    suspend fun getTaxEntryById(id: Long): TaxEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaxEntry(entry: TaxEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaxEntries(entries: List<TaxEntryEntity>)

    @Update
    suspend fun updateTaxEntry(entry: TaxEntryEntity)

    @Query("DELETE FROM tax_entries WHERE id = :id")
    suspend fun deleteTaxEntryById(id: Long)

    @Query("DELETE FROM tax_entries WHERE userId = :userId AND taxYear = :taxYear")
    suspend fun deleteTaxEntriesForYear(userId: Int, taxYear: Int)

    @Query("DELETE FROM tax_entries WHERE userId = :userId")
    suspend fun clearTaxEntriesForUser(userId: Int)

    // Tax Wealth Items (Opening / Closing Assets & Liabilities)
    @Query("SELECT * FROM tax_wealth_items WHERE userId = :userId ORDER BY periodType ASC, isLiability ASC, id ASC")
    fun observeAllWealthItemsForUser(userId: Int): Flow<List<TaxWealthItemEntity>>

    @Query("SELECT * FROM tax_wealth_items WHERE userId = :userId AND taxYear = :taxYear ORDER BY periodType ASC, isLiability ASC, id ASC")
    suspend fun getWealthItemsForYearDirect(userId: Int, taxYear: Int): List<TaxWealthItemEntity>

    @Query("SELECT * FROM tax_wealth_items WHERE userId = :userId ORDER BY taxYear DESC, id ASC")
    suspend fun getAllWealthItemsDirectForUser(userId: Int): List<TaxWealthItemEntity>

    @Query("SELECT * FROM tax_wealth_items WHERE id = :id LIMIT 1")
    suspend fun getWealthItemById(id: Long): TaxWealthItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWealthItem(item: TaxWealthItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWealthItems(items: List<TaxWealthItemEntity>)

    @Update
    suspend fun updateWealthItem(item: TaxWealthItemEntity)

    @Query("DELETE FROM tax_wealth_items WHERE id = :id")
    suspend fun deleteWealthItemById(id: Long)

    @Query("DELETE FROM tax_wealth_items WHERE userId = :userId AND taxYear = :taxYear AND periodType = :periodType AND isAutoSynced = 1")
    suspend fun deleteAutoSyncedWealthItems(userId: Int, taxYear: Int, periodType: String)

    @Query("DELETE FROM tax_wealth_items WHERE userId = :userId AND taxYear = :taxYear")
    suspend fun deleteWealthItemsForYear(userId: Int, taxYear: Int)

    @Query("DELETE FROM tax_wealth_items WHERE userId = :userId")
    suspend fun clearWealthItemsForUser(userId: Int)

    // Tax Documents Vault
    @Query("SELECT * FROM tax_documents WHERE userId = :userId ORDER BY createdAt DESC, id DESC")
    fun observeAllDocumentsForUser(userId: Int): Flow<List<TaxDocumentEntity>>

    @Query("SELECT * FROM tax_documents WHERE userId = :userId AND taxYear = :taxYear ORDER BY createdAt DESC, id DESC")
    suspend fun getDocumentsForYearDirect(userId: Int, taxYear: Int): List<TaxDocumentEntity>

    @Query("SELECT * FROM tax_documents WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getAllDocumentsDirectForUser(userId: Int): List<TaxDocumentEntity>

    @Query("SELECT * FROM tax_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): TaxDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: TaxDocumentEntity): Long

    @Update
    suspend fun updateDocument(doc: TaxDocumentEntity)

    @Query("DELETE FROM tax_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("UPDATE tax_entries SET documentId = NULL WHERE documentId = :docId")
    suspend fun unlinkDocumentFromEntries(docId: Long)

    @Query("DELETE FROM tax_documents WHERE userId = :userId")
    suspend fun clearDocumentsForUser(userId: Int)

    // Custom Tax Categories
    @Query("SELECT * FROM tax_custom_categories WHERE userId = :userId ORDER BY name ASC")
    fun observeCustomCategoriesForUser(userId: Int): Flow<List<TaxCustomCategoryEntity>>

    @Query("SELECT * FROM tax_custom_categories WHERE userId = :userId ORDER BY name ASC")
    suspend fun getCustomCategoriesDirectForUser(userId: Int): List<TaxCustomCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomCategory(cat: TaxCustomCategoryEntity): Long

    @Query("DELETE FROM tax_custom_categories WHERE userId = :userId")
    suspend fun clearCustomCategoriesForUser(userId: Int)

    // Stats helper
    @Query("SELECT COUNT(*) FROM tax_years WHERE userId = :userId")
    fun observeTaxYearCountForUser(userId: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM tax_years WHERE userId = :userId")
    suspend fun getTaxYearCountDirectForUser(userId: Int): Int

    @Query("SELECT COUNT(*) FROM tax_entries WHERE userId = :userId")
    suspend fun getTaxEntryCountDirectForUser(userId: Int): Int
}
