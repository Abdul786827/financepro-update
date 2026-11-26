package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

const val TAX_GROUP_INCOME = "INCOME"
const val TAX_GROUP_WITHHOLDING = "WITHHOLDING"
const val TAX_GROUP_DIRECT_TAX = "DIRECT_TAX"
const val TAX_GROUP_DEDUCTION = "DEDUCTION"
const val TAX_GROUP_EXPENSE = "EXPENSE"

const val WEALTH_PERIOD_OPENING = "OPENING"
const val WEALTH_PERIOD_CLOSING = "CLOSING"

const val WEALTH_CAT_BANK_BALANCE = "BANK_BALANCE"
const val WEALTH_CAT_CASH_IN_HAND = "CASH_IN_HAND"
const val WEALTH_CAT_PROPERTY = "PROPERTY"
const val WEALTH_CAT_VEHICLE = "VEHICLE"
const val WEALTH_CAT_GOLD_JEWELRY = "GOLD_JEWELRY"
const val WEALTH_CAT_INVESTMENTS = "INVESTMENTS"
const val WEALTH_CAT_LOAN_GIVEN = "LOAN_GIVEN"
const val WEALTH_CAT_LOAN_TAKEN = "LOAN_TAKEN"
const val WEALTH_CAT_OTHER_LIABILITY = "OTHER_LIABILITY"

const val TAX_DISCLAIMER_TEXT =
    "This is a record-keeping tool. Please check the figures with FBR rules or a tax consultant before filing."

object PakistanTaxConstants {
    val FILER_STATUSES = listOf(
        "Filer (ATL Active)",
        "Non-Filer",
        "Late Filer",
        "Exempt / Other"
    )

    val OCCUPATION_TYPES = listOf(
        "Salaried",
        "Business",
        "Freelancer",
        "Property Owner",
        "Mixed"
    )

    val DEFAULT_INCOME_CATEGORIES = listOf(
        "Salary",
        "Business/Freelance income",
        "Rental/Property income",
        "Capital gains",
        "Profit on debt / bank profit",
        "Dividends",
        "Agriculture income",
        "Foreign remittances",
        "Other income"
    )

    val DEFAULT_WITHHOLDING_CATEGORIES = listOf(
        "Salary",
        "Bank profit",
        "Vehicle",
        "Electricity bill",
        "Mobile/Telecom",
        "Cash withdrawal",
        "Property",
        "Other"
    )

    val DEFAULT_DIRECT_TAX_CATEGORIES = listOf(
        "Advance Tax",
        "Challan / CPR Payment",
        "Return Tax Paid",
        "Other Direct Tax"
    )

    val DEFAULT_DEDUCTION_CATEGORIES = listOf(
        "Zakat",
        "Charitable donations",
        "Education expenses",
        "Pension/provident fund contributions",
        "Health insurance"
    )

    val DEFAULT_TAX_EXPENSE_CATEGORIES = listOf(
        "Business expenses",
        "Rent",
        "Utility bills",
        "School fees",
        "Travel"
    )

    val DOCUMENT_CATEGORIES = listOf(
        "Salary Slip",
        "Bank Statement",
        "Tax Certificate",
        "Property Papers",
        "Receipt",
        "Challan / CPR",
        "Other"
    )

    val WEALTH_CATEGORIES = listOf(
        WEALTH_CAT_BANK_BALANCE to ("Bank Balances" to false),
        WEALTH_CAT_CASH_IN_HAND to ("Cash in Hand" to false),
        WEALTH_CAT_PROPERTY to ("Property" to false),
        WEALTH_CAT_VEHICLE to ("Vehicles" to false),
        WEALTH_CAT_GOLD_JEWELRY to ("Gold / Jewelry" to false),
        WEALTH_CAT_INVESTMENTS to ("Investments" to false),
        WEALTH_CAT_LOAN_GIVEN to ("Loans Given (Receivable)" to false),
        WEALTH_CAT_LOAN_TAKEN to ("Loans Taken (Liability)" to true),
        WEALTH_CAT_OTHER_LIABILITY to ("Other Liabilities" to true)
    )

    fun wealthCategoryLabel(cat: String): String =
        WEALTH_CATEGORIES.firstOrNull { it.first == cat }?.second?.first ?: cat

    fun isLiabilityCategory(cat: String): Boolean =
        WEALTH_CATEGORIES.firstOrNull { it.first == cat }?.second?.second ?: false

    fun defaultCategoriesForGroup(group: String): List<String> = when (group) {
        TAX_GROUP_INCOME -> DEFAULT_INCOME_CATEGORIES
        TAX_GROUP_WITHHOLDING -> DEFAULT_WITHHOLDING_CATEGORIES
        TAX_GROUP_DIRECT_TAX -> DEFAULT_DIRECT_TAX_CATEGORIES
        TAX_GROUP_DEDUCTION -> DEFAULT_DEDUCTION_CATEGORIES
        TAX_GROUP_EXPENSE -> DEFAULT_TAX_EXPENSE_CATEGORIES
        else -> emptyList()
    }
}

@Entity(tableName = "tax_profiles")
data class TaxProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Int = 1,
    val fullName: String = "",
    val cnicEncrypted: String = "",
    val ntnEncrypted: String = "",
    val filerStatus: String = "Filer (ATL Active)",
    val occupationType: String = "Salaried",
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tax_years")
data class TaxYearEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Int = 1,
    val taxYear: Int,
    val label: String,
    val startDate: String,
    val endDate: String,
    val filingDeadline: String = "",
    val reminderDaysBefore: Int = 7,
    val isLocked: Boolean = false,
    val lockedAt: Long = 0L,
    val customSlabJson: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tax_entries")
data class TaxEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Int = 1,
    val taxYear: Int,
    val entryGroup: String, // INCOME, WITHHOLDING, DIRECT_TAX, DEDUCTION, EXPENSE
    val category: String,
    val amount: Double,
    val date: String, // YYYY-MM-DD
    val sourceOrPayer: String = "",
    val referenceNumber: String = "", // CPR / Challan / Certificate No.
    val proofNote: String = "",
    val documentId: Long? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tax_wealth_items")
data class TaxWealthItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Int = 1,
    val taxYear: Int,
    val periodType: String, // OPENING or CLOSING
    val assetCategory: String,
    val title: String,
    val amount: Double,
    val isLiability: Boolean = false,
    val isAutoSynced: Boolean = false,
    val linkedSourceKey: String = "",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tax_documents")
data class TaxDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Int = 1,
    val taxYear: Int,
    val title: String,
    val category: String,
    val filePath: String,
    val mimeType: String = "application/octet-stream",
    val originalFileName: String = "",
    val fileSizeBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tax_custom_categories")
data class TaxCustomCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Int = 1,
    val entryGroup: String,
    val name: String
)

data class TaxSlabItem(
    val minIncome: Double,
    val maxIncome: Double?, // null means unlimited / above
    val fixedTax: Double,
    val ratePercent: Double
)

data class TaxDisplayItem(
    val id: Long,
    val taxYear: Int,
    val entryGroup: String,
    val category: String,
    val amount: Double,
    val date: String,
    val sourceOrPayer: String,
    val referenceNumber: String,
    val proofNote: String,
    val documentId: Long?,
    val notes: String,
    val isLinkedTransaction: Boolean = false,
    val linkedTransactionId: Long? = null
)

data class TaxChecklistItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val completed: Boolean
)

data class TaxYearSummary(
    val taxYear: Int,
    val incomeByCategory: Map<String, Double>,
    val totalIncome: Double,
    val totalWithholdingTax: Double,
    val totalDirectTaxPaid: Double,
    val totalDeductions: Double,
    val totalTaxExpenses: Double,
    val openingAssets: Double,
    val openingLiabilities: Double,
    val openingNetWealth: Double,
    val closingAssets: Double,
    val closingLiabilities: Double,
    val closingNetWealth: Double,
    val wealthDifference: Double,
    val netDeclaredInflow: Double,
    val unreconciledDifference: Double,
    val documentCount: Int,
    val checklist: List<TaxChecklistItem>,
    val checklistProgress: Float
)
