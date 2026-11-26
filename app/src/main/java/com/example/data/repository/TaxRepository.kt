package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.example.data.local.FinanceDao
import com.example.data.local.FinanceDatabase
import com.example.data.local.TaxDao
import com.example.data.model.*
import com.example.util.DateUtils
import com.example.util.PakistanTaxYearUtils
import com.example.util.TaxBackupManager
import com.example.util.TaxCryptoHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class TaxRepository(
    private val financeDao: FinanceDao,
    private val taxDao: TaxDao,
    private val database: FinanceDatabase? = null
) {

    private suspend fun <T> runAtomic(block: suspend () -> T): T {
        return if (database != null) {
            database.withTransaction { block() }
        } else {
            block()
        }
    }

    private val activeUserIdFlow: Flow<Int?> = combine(
        financeDao.getUserProfile(),
        financeDao.observeUserCount()
    ) { profile, userCount ->
        when {
            profile?.isLoggedIn == true -> profile.id
            userCount == 0 -> 1
            else -> null
        }
    }.distinctUntilChanged()

    suspend fun resolveActiveUserId(): Int {
        val loggedIn = financeDao.getLoggedInUser()
        if (loggedIn != null) return loggedIn.id
        val userCount = financeDao.getUserCountDirect()
        if (userCount == 0) return 1
        return financeDao.getUserProfileDirect()?.id ?: 1
    }

    val taxProfile: Flow<TaxProfileEntity?> = activeUserIdFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(null) else taxDao.observeTaxProfileForUser(uid)
    }

    val taxYears: Flow<List<TaxYearEntity>> = activeUserIdFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else taxDao.observeTaxYearsForUser(uid)
    }

    val taxEntries: Flow<List<TaxEntryEntity>> = activeUserIdFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else taxDao.observeAllTaxEntriesForUser(uid)
    }

    val taxWealthItems: Flow<List<TaxWealthItemEntity>> = activeUserIdFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else taxDao.observeAllWealthItemsForUser(uid)
    }

    val taxDocuments: Flow<List<TaxDocumentEntity>> = activeUserIdFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else taxDao.observeAllDocumentsForUser(uid)
    }

    val taxCustomCategories: Flow<List<TaxCustomCategoryEntity>> = activeUserIdFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else taxDao.observeCustomCategoriesForUser(uid)
    }

    val taxYearCount: Flow<Int> = activeUserIdFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(0) else taxDao.observeTaxYearCountForUser(uid)
    }

    suspend fun saveTaxProfile(
        context: Context,
        fullName: String,
        cnicPlain: String,
        ntnPlain: String,
        filerStatus: String,
        occupationType: String,
        note: String
    ) = runAtomic {
        val uid = resolveActiveUserId()
        val existing = taxDao.getTaxProfileDirectForUser(uid)
        val cnicEnc = TaxCryptoHelper.encryptSensitive(context, cnicPlain)
        val ntnEnc = TaxCryptoHelper.encryptSensitive(context, ntnPlain)
        val entity = TaxProfileEntity(
            id = existing?.id ?: 0L,
            userId = uid,
            fullName = fullName.trim(),
            cnicEncrypted = cnicEnc,
            ntnEncrypted = ntnEnc,
            filerStatus = filerStatus.trim().ifBlank { "Filer (ATL Active)" },
            occupationType = occupationType.trim().ifBlank { "Salaried" },
            note = note.trim(),
            updatedAt = System.currentTimeMillis()
        )
        taxDao.insertTaxProfile(entity)
    }

    suspend fun ensureTaxYearExists(taxYear: Int): TaxYearEntity = runAtomic {
        val uid = resolveActiveUserId()
        val existing = taxDao.getTaxYearDirectForUser(uid, taxYear)
        if (existing != null) return@runAtomic existing

        val newYear = TaxYearEntity(
            userId = uid,
            taxYear = taxYear,
            label = PakistanTaxYearUtils.formatTaxYearLabel(taxYear),
            startDate = PakistanTaxYearUtils.startDateForTaxYear(taxYear),
            endDate = PakistanTaxYearUtils.endDateForTaxYear(taxYear),
            filingDeadline = PakistanTaxYearUtils.defaultFilingDeadlineForTaxYear(taxYear),
            reminderDaysBefore = 7,
            isLocked = false,
            lockedAt = 0L,
            customSlabJson = ""
        )
        val id = taxDao.insertTaxYear(newYear)
        newYear.copy(id = id)
    }

    suspend fun createTaxYear(
        taxYear: Int,
        filingDeadline: String = PakistanTaxYearUtils.defaultFilingDeadlineForTaxYear(taxYear),
        reminderDaysBefore: Int = 7
    ): TaxYearEntity = runAtomic {
        require(taxYear in 2000..2100) { "Please enter a valid tax year (2000–2100)." }
        val uid = resolveActiveUserId()
        val existing = taxDao.getTaxYearDirectForUser(uid, taxYear)
        if (existing != null) {
            throw IllegalArgumentException("Tax Year $taxYear already exists.")
        }
        val entity = TaxYearEntity(
            userId = uid,
            taxYear = taxYear,
            label = PakistanTaxYearUtils.formatTaxYearLabel(taxYear),
            startDate = PakistanTaxYearUtils.startDateForTaxYear(taxYear),
            endDate = PakistanTaxYearUtils.endDateForTaxYear(taxYear),
            filingDeadline = filingDeadline.trim(),
            reminderDaysBefore = reminderDaysBefore.coerceIn(0, 90),
            isLocked = false,
            lockedAt = 0L,
            customSlabJson = ""
        )
        val id = taxDao.insertTaxYear(entity)
        entity.copy(id = id)
    }

    suspend fun updateTaxYearDeadline(
        taxYear: Int,
        filingDeadline: String,
        reminderDaysBefore: Int
    ) = runAtomic {
        val uid = resolveActiveUserId()
        val yearObj = taxDao.getTaxYearDirectForUser(uid, taxYear)
            ?: ensureTaxYearExists(taxYear)
        require(!yearObj.isLocked) { "Tax Year $taxYear is locked/archived. Unlock it before editing." }
        taxDao.updateTaxYear(
            yearObj.copy(
                filingDeadline = filingDeadline.trim(),
                reminderDaysBefore = reminderDaysBefore.coerceIn(0, 90)
            )
        )
    }

    suspend fun toggleTaxYearLock(taxYear: Int, lock: Boolean) = runAtomic {
        val uid = resolveActiveUserId()
        val yearObj = taxDao.getTaxYearDirectForUser(uid, taxYear)
            ?: ensureTaxYearExists(taxYear)
        taxDao.updateTaxYear(
            yearObj.copy(
                isLocked = lock,
                lockedAt = if (lock) System.currentTimeMillis() else 0L
            )
        )
    }

    suspend fun updateCustomSlabs(taxYear: Int, slabs: List<TaxSlabItem>) = runAtomic {
        val uid = resolveActiveUserId()
        val yearObj = taxDao.getTaxYearDirectForUser(uid, taxYear)
            ?: ensureTaxYearExists(taxYear)
        taxDao.updateTaxYear(
            yearObj.copy(customSlabJson = PakistanTaxYearUtils.serializeSlabsJson(slabs))
        )
    }

    private suspend fun assertTaxYearUnlocked(uid: Int, taxYear: Int) {
        val existing = taxDao.getTaxYearDirectForUser(uid, taxYear)
        if (existing == null) {
            ensureTaxYearExists(taxYear)
        } else if (existing.isLocked) {
            throw IllegalStateException("Tax Year $taxYear is locked/archived. Unlock it first to modify records.")
        }
    }

    suspend fun addCustomCategory(entryGroup: String, name: String) = runAtomic {
        val uid = resolveActiveUserId()
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Category name cannot be empty." }
        val existing = taxDao.getCustomCategoriesDirectForUser(uid)
        val alreadyExists = existing.any {
            it.entryGroup == entryGroup && it.name.equals(clean, ignoreCase = true)
        } || PakistanTaxConstants.defaultCategoriesForGroup(entryGroup).any {
            it.equals(clean, ignoreCase = true)
        }
        if (!alreadyExists) {
            taxDao.insertCustomCategory(
                TaxCustomCategoryEntity(userId = uid, entryGroup = entryGroup, name = clean)
            )
        }
    }

    suspend fun saveTaxEntry(
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
        notes: String = ""
    ): Long = runAtomic {
        val uid = resolveActiveUserId()
        assertTaxYearUnlocked(uid, taxYear)
        val cleanCat = category.trim()
        require(cleanCat.isNotEmpty()) { "Please select or enter a category." }
        require(amount > 0.0) { "Amount must be greater than 0." }

        // Save custom category automatically if not in built-ins
        if (PakistanTaxConstants.defaultCategoriesForGroup(entryGroup).none { it.equals(cleanCat, ignoreCase = true) }) {
            val customs = taxDao.getCustomCategoriesDirectForUser(uid)
            if (customs.none { it.entryGroup == entryGroup && it.name.equals(cleanCat, ignoreCase = true) }) {
                taxDao.insertCustomCategory(
                    TaxCustomCategoryEntity(userId = uid, entryGroup = entryGroup, name = cleanCat)
                )
            }
        }

        val now = System.currentTimeMillis()
        val existing = if (id > 0L) taxDao.getTaxEntryById(id) else null
        val entity = TaxEntryEntity(
            id = existing?.id ?: 0L,
            userId = uid,
            taxYear = taxYear,
            entryGroup = entryGroup,
            category = cleanCat,
            amount = amount,
            date = date.trim().ifBlank { DateUtils.getCurrentDate() },
            sourceOrPayer = sourceOrPayer.trim(),
            referenceNumber = referenceNumber.trim(),
            proofNote = proofNote.trim(),
            documentId = documentId,
            notes = notes.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        taxDao.insertTaxEntry(entity)
    }

    suspend fun deleteTaxEntry(entryId: Long) = runAtomic {
        val existing = taxDao.getTaxEntryById(entryId) ?: return@runAtomic
        assertTaxYearUnlocked(existing.userId, existing.taxYear)
        taxDao.deleteTaxEntryById(entryId)
    }

    suspend fun saveWealthItem(
        id: Long = 0L,
        taxYear: Int,
        periodType: String,
        assetCategory: String,
        title: String,
        amount: Double,
        isLiability: Boolean = PakistanTaxConstants.isLiabilityCategory(assetCategory),
        notes: String = ""
    ): Long = runAtomic {
        val uid = resolveActiveUserId()
        assertTaxYearUnlocked(uid, taxYear)
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "Title cannot be empty." }
        require(amount >= 0.0) { "Amount cannot be negative." }

        val existing = if (id > 0L) taxDao.getWealthItemById(id) else null
        val item = TaxWealthItemEntity(
            id = existing?.id ?: 0L,
            userId = uid,
            taxYear = taxYear,
            periodType = periodType,
            assetCategory = assetCategory,
            title = cleanTitle,
            amount = amount,
            isLiability = isLiability,
            isAutoSynced = false, // Edited or manual items stay independent
            linkedSourceKey = existing?.linkedSourceKey ?: "",
            notes = notes.trim(),
            updatedAt = System.currentTimeMillis()
        )
        taxDao.insertWealthItem(item)
    }

    suspend fun deleteWealthItem(itemId: Long) = runAtomic {
        val existing = taxDao.getWealthItemById(itemId) ?: return@runAtomic
        assertTaxYearUnlocked(existing.userId, existing.taxYear)
        taxDao.deleteWealthItemById(itemId)
    }

    /**
     * Auto-fills bank & wallet balances from existing app accounts into the specified period (usually CLOSING).
     * Items remain fully editable afterwards.
     */
    suspend fun autoFillBankBalancesForWealth(taxYear: Int, periodType: String = WEALTH_PERIOD_CLOSING): Int = runAtomic {
        val uid = resolveActiveUserId()
        assertTaxYearUnlocked(uid, taxYear)
        val accounts = financeDao.getAllAccountsDirectForUser(uid)
        val existingItems = taxDao.getWealthItemsForYearDirect(uid, taxYear)
            .filter { it.periodType == periodType }

        var count = 0
        for (acc in accounts) {
            val key = "account:${acc.id}"
            val matched = existingItems.firstOrNull { it.linkedSourceKey == key }
            val updated = TaxWealthItemEntity(
                id = matched?.id ?: 0L,
                userId = uid,
                taxYear = taxYear,
                periodType = periodType,
                assetCategory = if (isCashAccount(acc)) WEALTH_CAT_CASH_IN_HAND else WEALTH_CAT_BANK_BALANCE,
                title = "${acc.name} (${accountTypeShortLabel(acc.accountType)})",
                amount = acc.currentBalance.coerceAtLeast(0.0),
                isLiability = false,
                isAutoSynced = true,
                linkedSourceKey = key,
                notes = "Auto-filled from ${acc.name} on ${DateUtils.getCurrentDate()}",
                updatedAt = System.currentTimeMillis()
            )
            taxDao.insertWealthItem(updated)
            count++
        }
        count
    }

    /**
     * Auto-pulls pending Money Udhaar balances (Given -> Receivable Asset, Taken -> Payable Liability).
     */
    suspend fun autoPullUdhaarForWealth(taxYear: Int, periodType: String = WEALTH_PERIOD_CLOSING): Int = runAtomic {
        val uid = resolveActiveUserId()
        assertTaxYearUnlocked(uid, taxYear)
        val udhaarList = financeDao.getAllUdhaarRecordsDirectForUser(uid)
            .filter { it.category == "MONEY" && it.status != "SETTLED" && it.remainingAmount > 0.0 }

        val existingItems = taxDao.getWealthItemsForYearDirect(uid, taxYear)
            .filter { it.periodType == periodType }

        var count = 0
        for (rec in udhaarList) {
            val isLiability = rec.type == "TAKEN"
            val key = "udhaar:${rec.id}"
            val matched = existingItems.firstOrNull { it.linkedSourceKey == key }
            val item = TaxWealthItemEntity(
                id = matched?.id ?: 0L,
                userId = uid,
                taxYear = taxYear,
                periodType = periodType,
                assetCategory = if (isLiability) WEALTH_CAT_LOAN_TAKEN else WEALTH_CAT_LOAN_GIVEN,
                title = if (isLiability) "Udhaar Taken: ${rec.personName}" else "Udhaar Given: ${rec.personName}",
                amount = rec.remainingAmount,
                isLiability = isLiability,
                isAutoSynced = true,
                linkedSourceKey = key,
                notes = "Auto-pulled from Udhaar (${rec.date})",
                updatedAt = System.currentTimeMillis()
            )
            taxDao.insertWealthItem(item)
            count++
        }
        count
    }

    private fun accountTypeShortLabel(type: String): String = when (normalizeAccountType(type)) {
        ACCOUNT_TYPE_WALLET -> "Wallet"
        ACCOUNT_TYPE_CASH -> "Cash"
        else -> "Bank"
    }

    suspend fun saveDocumentFromUri(
        context: Context,
        taxYear: Int,
        title: String,
        category: String,
        uri: Uri
    ): TaxDocumentEntity {
        val uid = resolveActiveUserId()
        assertTaxYearUnlocked(uid, taxYear)

        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        var originalName = "document"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) originalName = cursor.getString(idx) ?: originalName
            }
        }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Unable to read selected file.")

        return saveDocumentFromBytes(
            context = context,
            taxYear = taxYear,
            title = title,
            category = category,
            mimeType = mimeType,
            originalFileName = originalName,
            bytes = bytes
        )
    }

    suspend fun saveDocumentFromBytes(
        context: Context,
        taxYear: Int,
        title: String,
        category: String,
        mimeType: String,
        originalFileName: String,
        bytes: ByteArray
    ): TaxDocumentEntity = runAtomic {
        val uid = resolveActiveUserId()
        assertTaxYearUnlocked(uid, taxYear)
        val cleanTitle = title.trim().ifBlank { originalFileName.ifBlank { "Tax Document" } }

        val docsDir = File(context.filesDir, "tax_docs/u$uid/ty$taxYear").apply {
            if (!exists()) mkdirs()
        }
        val safeName = originalFileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "doc" }
        val targetFile = File(docsDir, "${System.currentTimeMillis()}_$safeName")
        FileOutputStream(targetFile).use { it.write(bytes) }

        val entity = TaxDocumentEntity(
            userId = uid,
            taxYear = taxYear,
            title = cleanTitle,
            category = category.ifBlank { "Other" },
            filePath = targetFile.absolutePath,
            mimeType = mimeType,
            originalFileName = originalFileName,
            fileSizeBytes = bytes.size.toLong()
        )
        val id = taxDao.insertDocument(entity)
        entity.copy(id = id)
    }

    suspend fun deleteDocument(docId: Long) = runAtomic {
        val doc = taxDao.getDocumentById(docId) ?: return@runAtomic
        assertTaxYearUnlocked(doc.userId, doc.taxYear)
        taxDao.unlinkDocumentFromEntries(docId)
        try {
            File(doc.filePath).delete()
        } catch (_: Exception) {}
        taxDao.deleteDocumentById(docId)
    }

    suspend fun createEncryptedBackupBytes(
        context: Context,
        passphrase: String,
        scopeTaxYear: Int?
    ): ByteArray {
        val uid = resolveActiveUserId()
        val profile = taxDao.getTaxProfileDirectForUser(uid)
        val years = taxDao.getTaxYearsDirectForUser(uid)
        val entries = taxDao.getAllTaxEntriesDirectForUser(uid)
        val wealth = taxDao.getAllWealthItemsDirectForUser(uid)
        val customCats = taxDao.getCustomCategoriesDirectForUser(uid)
        val docs = taxDao.getAllDocumentsDirectForUser(uid)

        return TaxBackupManager.createEncryptedBackup(
            context = context,
            passphrase = passphrase,
            scopeTaxYear = scopeTaxYear,
            profile = profile,
            years = years,
            entries = entries,
            wealthItems = wealth,
            customCategories = customCats,
            documents = docs
        )
    }

    suspend fun restoreEncryptedBackupBytes(
        context: Context,
        encryptedBytes: ByteArray,
        passphrase: String
    ): Int = runAtomic {
        val uid = resolveActiveUserId()
        val bundle = TaxBackupManager.parseEncryptedBackup(context, encryptedBytes, passphrase)
        val targetYear = bundle.scopeTaxYear

        // Restore profile if included
        bundle.profile?.let { prof ->
            val existing = taxDao.getTaxProfileDirectForUser(uid)
            taxDao.insertTaxProfile(prof.copy(id = existing?.id ?: 0L, userId = uid))
        }

        // Restore custom categories
        val existingCats = taxDao.getCustomCategoriesDirectForUser(uid)
        for (cat in bundle.customCategories) {
            if (existingCats.none { it.entryGroup == cat.entryGroup && it.name.equals(cat.name, ignoreCase = true) }) {
                taxDao.insertCustomCategory(cat.copy(id = 0L, userId = uid))
            }
        }

        if (targetYear == null) {
            // Full backup restore: replace tax years, entries, wealth, docs for this user
            taxDao.clearTaxEntriesForUser(uid)
            taxDao.clearWealthItemsForUser(uid)
            taxDao.clearDocumentsForUser(uid)
            taxDao.clearTaxYearsForUser(uid)
        } else {
            taxDao.deleteTaxEntriesForYear(uid, targetYear)
            taxDao.deleteWealthItemsForYear(uid, targetYear)
            taxDao.deleteTaxYearForUser(uid, targetYear)
            val oldDocs = taxDao.getDocumentsForYearDirect(uid, targetYear)
            for (d in oldDocs) {
                try { File(d.filePath).delete() } catch (_: Exception) {}
                taxDao.deleteDocumentById(d.id)
            }
        }

        for (y in bundle.years) {
            taxDao.insertTaxYear(y.copy(id = 0L, userId = uid))
        }
        if (bundle.entries.isNotEmpty()) {
            taxDao.insertTaxEntries(bundle.entries.map { it.copy(id = 0L, userId = uid) })
        }
        if (bundle.wealthItems.isNotEmpty()) {
            taxDao.insertWealthItems(bundle.wealthItems.map { it.copy(id = 0L, userId = uid) })
        }

        var restoredDocsCount = 0
        for (d in bundle.documents) {
            val bytes = d.contentBytes
            if (bytes != null) {
                val docsDir = File(context.filesDir, "tax_docs/u$uid/ty${d.taxYear}").apply {
                    if (!exists()) mkdirs()
                }
                val safeName = d.originalFileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "doc" }
                val outFile = File(docsDir, "${System.currentTimeMillis()}_$safeName")
                FileOutputStream(outFile).use { it.write(bytes) }
                taxDao.insertDocument(
                    TaxDocumentEntity(
                        userId = uid,
                        taxYear = d.taxYear,
                        title = d.title,
                        category = d.category,
                        filePath = outFile.absolutePath,
                        mimeType = d.mimeType,
                        originalFileName = d.originalFileName,
                        fileSizeBytes = bytes.size.toLong()
                    )
                )
                restoredDocsCount++
            }
        }

        bundle.years.size
    }

    suspend fun clearAllTaxDataForUser(context: Context, userId: Int) = runAtomic {
        val docs = taxDao.getAllDocumentsDirectForUser(userId)
        for (doc in docs) {
            try { File(doc.filePath).delete() } catch (_: Exception) {}
        }
        taxDao.clearTaxEntriesForUser(userId)
        taxDao.clearWealthItemsForUser(userId)
        taxDao.clearDocumentsForUser(userId)
        taxDao.clearCustomCategoriesForUser(userId)
        taxDao.clearTaxYearsForUser(userId)
        taxDao.clearTaxProfileForUser(userId)
        financeDao.clearAllTaxTagsOnTransactionsForUser(userId)
    }

    fun buildYearSummary(
        taxYear: Int,
        profile: TaxProfileEntity?,
        decryptedCnic: String,
        decryptedNtn: String,
        manualEntries: List<TaxEntryEntity>,
        linkedTransactions: List<TransactionEntity>,
        wealthItems: List<TaxWealthItemEntity>,
        documents: List<TaxDocumentEntity>
    ): TaxYearSummary {
        val combinedItems = mergeEntriesAndLinkedTransactions(taxYear, manualEntries, linkedTransactions)

        val incomeItems = combinedItems.filter { it.entryGroup == TAX_GROUP_INCOME }
        val whtItems = combinedItems.filter { it.entryGroup == TAX_GROUP_WITHHOLDING }
        val directTaxItems = combinedItems.filter { it.entryGroup == TAX_GROUP_DIRECT_TAX }
        val deductionItems = combinedItems.filter { it.entryGroup == TAX_GROUP_DEDUCTION }
        val expenseItems = combinedItems.filter { it.entryGroup == TAX_GROUP_EXPENSE }

        val incomeByCat = linkedMapOf<String, Double>()
        for (item in incomeItems) {
            incomeByCat[item.category] = (incomeByCat[item.category] ?: 0.0) + item.amount
        }

        val totalIncome = incomeItems.sumOf { it.amount }
        val totalWht = whtItems.sumOf { it.amount }
        val totalDirect = directTaxItems.sumOf { it.amount }
        val totalDeductions = deductionItems.sumOf { it.amount }
        val totalExpenses = expenseItems.sumOf { it.amount }

        val yearWealth = wealthItems.filter { it.taxYear == taxYear }
        val openingAssets = yearWealth.filter { it.periodType == WEALTH_PERIOD_OPENING && !it.isLiability }.sumOf { it.amount }
        val openingLiabilities = yearWealth.filter { it.periodType == WEALTH_PERIOD_OPENING && it.isLiability }.sumOf { it.amount }
        val openingNet = openingAssets - openingLiabilities

        val closingAssets = yearWealth.filter { it.periodType == WEALTH_PERIOD_CLOSING && !it.isLiability }.sumOf { it.amount }
        val closingLiabilities = yearWealth.filter { it.periodType == WEALTH_PERIOD_CLOSING && it.isLiability }.sumOf { it.amount }
        val closingNet = closingAssets - closingLiabilities

        val wealthDiff = closingNet - openingNet
        val netDeclaredInflow = totalIncome - (totalExpenses + totalWht + totalDirect + totalDeductions)
        val unreconciledDiff = wealthDiff - netDeclaredInflow

        val yearDocs = documents.filter { it.taxYear == taxYear }

        val profileDone = profile != null &&
            profile.fullName.isNotBlank() &&
            decryptedCnic.isNotBlank() &&
            decryptedNtn.isNotBlank() &&
            profile.occupationType.isNotBlank()

        val incomeEntered = incomeItems.isNotEmpty()

        val whtCertsAttached = whtItems.any {
            it.proofNote.isNotBlank() || it.referenceNumber.isNotBlank() || it.documentId != null
        } || yearDocs.any {
            it.category.equals("Tax Certificate", ignoreCase = true) ||
                it.category.equals("Challan / CPR", ignoreCase = true) ||
                it.category.equals("Salary Slip", ignoreCase = true)
        }

        val wealthDone = yearWealth.any { it.periodType == WEALTH_PERIOD_OPENING } &&
            yearWealth.any { it.periodType == WEALTH_PERIOD_CLOSING }

        val deductionsProofAttached = if (deductionItems.isEmpty()) {
            yearDocs.any { it.category.equals("Receipt", ignoreCase = true) }
        } else {
            deductionItems.all {
                it.proofNote.isNotBlank() || it.referenceNumber.isNotBlank() || it.documentId != null
            } || yearDocs.any { it.category.equals("Receipt", ignoreCase = true) }
        }

        val checklist = listOf(
            TaxChecklistItem(
                id = "profile",
                title = "Taxpayer Profile Complete",
                subtitle = if (profileDone) "Name, CNIC, NTN & occupation saved" else "Add full name, CNIC, NTN & occupation",
                completed = profileDone
            ),
            TaxChecklistItem(
                id = "income",
                title = "Income Entered",
                subtitle = if (incomeEntered) "${incomeItems.size} income source(s) recorded" else "Add salary, business, freelance or other income",
                completed = incomeEntered
            ),
            TaxChecklistItem(
                id = "wht_certs",
                title = "Withholding Certificates Attached",
                subtitle = if (whtCertsAttached) "Withholding tax proof or certificate attached" else "Attach tax certificate or payment proof",
                completed = whtCertsAttached
            ),
            TaxChecklistItem(
                id = "wealth",
                title = "Wealth Statement Completed",
                subtitle = if (wealthDone) "Both opening & closing assets/liabilities recorded" else "Add opening and closing assets & liabilities",
                completed = wealthDone
            ),
            TaxChecklistItem(
                id = "deductions_proof",
                title = "Deductions Proof Attached",
                subtitle = if (deductionsProofAttached) "Deduction / credit proofs ready" else "Attach Zakat, donation, education or insurance proof",
                completed = deductionsProofAttached
            )
        )
        val completedCount = checklist.count { it.completed }
        val progress = completedCount.toFloat() / checklist.size.toFloat()

        return TaxYearSummary(
            taxYear = taxYear,
            incomeByCategory = incomeByCat,
            totalIncome = totalIncome,
            totalWithholdingTax = totalWht,
            totalDirectTaxPaid = totalDirect,
            totalDeductions = totalDeductions,
            totalTaxExpenses = totalExpenses,
            openingAssets = openingAssets,
            openingLiabilities = openingLiabilities,
            openingNetWealth = openingNet,
            closingAssets = closingAssets,
            closingLiabilities = closingLiabilities,
            closingNetWealth = closingNet,
            wealthDifference = wealthDiff,
            netDeclaredInflow = netDeclaredInflow,
            unreconciledDifference = unreconciledDiff,
            documentCount = yearDocs.size,
            checklist = checklist,
            checklistProgress = progress
        )
    }

    fun mergeEntriesAndLinkedTransactions(
        taxYear: Int,
        manualEntries: List<TaxEntryEntity>,
        transactions: List<TransactionEntity>
    ): List<TaxDisplayItem> {
        val manual = manualEntries
            .filter { it.taxYear == taxYear }
            .map { e ->
                TaxDisplayItem(
                    id = e.id,
                    taxYear = e.taxYear,
                    entryGroup = e.entryGroup,
                    category = e.category,
                    amount = e.amount,
                    date = e.date,
                    sourceOrPayer = e.sourceOrPayer,
                    referenceNumber = e.referenceNumber,
                    proofNote = e.proofNote,
                    documentId = e.documentId,
                    notes = e.notes,
                    isLinkedTransaction = false,
                    linkedTransactionId = null
                )
            }

        val linked = transactions
            .filter { tx ->
                tx.isTaxRelated && tx.taxSectionType.isNotBlank() &&
                    (tx.taxYearId.toIntOrNull() ?: PakistanTaxYearUtils.taxYearForDate(tx.date)) == taxYear
            }
            .map { tx ->
                TaxDisplayItem(
                    id = -tx.id, // Negative ID prevents LazyColumn key collision with tax_entries
                    taxYear = taxYear,
                    entryGroup = tx.taxSectionType,
                    category = tx.taxCategory.ifBlank { tx.category.ifBlank { "General" } },
                    amount = tx.amount,
                    date = tx.date,
                    sourceOrPayer = if (tx.taxSectionType == TAX_GROUP_INCOME) tx.fromAccount else tx.toAccount,
                    referenceNumber = "TX#${tx.serialNumber}",
                    proofNote = "Linked from Statement",
                    documentId = null,
                    notes = tx.comments.ifBlank { tx.description },
                    isLinkedTransaction = true,
                    linkedTransactionId = tx.id
                )
            }

        return (manual + linked).sortedWith(compareByDescending<TaxDisplayItem> { it.date }.thenByDescending { it.id })
    }
}
