package com.example.util

import android.content.Context
import android.util.Base64
import com.example.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class RestoredTaxBundle(
    val scopeTaxYear: Int?,
    val profile: TaxProfileEntity?,
    val years: List<TaxYearEntity>,
    val entries: List<TaxEntryEntity>,
    val wealthItems: List<TaxWealthItemEntity>,
    val customCategories: List<TaxCustomCategoryEntity>,
    val documents: List<RestoredTaxDocument>
)

data class RestoredTaxDocument(
    val taxYear: Int,
    val title: String,
    val category: String,
    val mimeType: String,
    val originalFileName: String,
    val contentBytes: ByteArray?
)

/**
 * Serializes and encrypts/decrypts Pakistan Taxpayer Mode data (per year or all years)
 * into a portable passphrase-protected `.fptax` backup file suitable for Google Drive.
 */
object TaxBackupManager {

    private const val MAX_EMBED_DOC_BYTES = 2 * 1024 * 1024 // 2 MB per file for portable backup

    fun createEncryptedBackup(
        context: Context,
        passphrase: String,
        scopeTaxYear: Int?, // null = all tax years
        profile: TaxProfileEntity?,
        years: List<TaxYearEntity>,
        entries: List<TaxEntryEntity>,
        wealthItems: List<TaxWealthItemEntity>,
        customCategories: List<TaxCustomCategoryEntity>,
        documents: List<TaxDocumentEntity>
    ): ByteArray {
        val filteredYears = if (scopeTaxYear == null) years else years.filter { it.taxYear == scopeTaxYear }
        val filteredEntries = if (scopeTaxYear == null) entries else entries.filter { it.taxYear == scopeTaxYear }
        val filteredWealth = if (scopeTaxYear == null) wealthItems else wealthItems.filter { it.taxYear == scopeTaxYear }
        val filteredDocs = if (scopeTaxYear == null) documents else documents.filter { it.taxYear == scopeTaxYear }

        val root = JSONObject().apply {
            put("schemaVersion", 1)
            put("exportedAt", System.currentTimeMillis())
            if (scopeTaxYear != null) put("scopeTaxYear", scopeTaxYear) else put("scopeTaxYear", JSONObject.NULL)

            profile?.let { prof ->
                put("profile", JSONObject().apply {
                    put("fullName", prof.fullName)
                    put("cnicPlain", TaxCryptoHelper.decryptSensitive(context, prof.cnicEncrypted))
                    put("ntnPlain", TaxCryptoHelper.decryptSensitive(context, prof.ntnEncrypted))
                    put("filerStatus", prof.filerStatus)
                    put("occupationType", prof.occupationType)
                    put("note", prof.note)
                })
            }

            put("years", JSONArray().apply {
                filteredYears.forEach { y ->
                    put(JSONObject().apply {
                        put("taxYear", y.taxYear)
                        put("label", y.label)
                        put("startDate", y.startDate)
                        put("endDate", y.endDate)
                        put("filingDeadline", y.filingDeadline)
                        put("reminderDaysBefore", y.reminderDaysBefore)
                        put("isLocked", y.isLocked)
                        put("lockedAt", y.lockedAt)
                        put("customSlabJson", y.customSlabJson)
                    })
                }
            })

            put("entries", JSONArray().apply {
                filteredEntries.forEach { e ->
                    put(JSONObject().apply {
                        put("taxYear", e.taxYear)
                        put("entryGroup", e.entryGroup)
                        put("category", e.category)
                        put("amount", e.amount)
                        put("date", e.date)
                        put("sourceOrPayer", e.sourceOrPayer)
                        put("referenceNumber", e.referenceNumber)
                        put("proofNote", e.proofNote)
                        put("notes", e.notes)
                    })
                }
            })

            put("wealthItems", JSONArray().apply {
                filteredWealth.forEach { w ->
                    put(JSONObject().apply {
                        put("taxYear", w.taxYear)
                        put("periodType", w.periodType)
                        put("assetCategory", w.assetCategory)
                        put("title", w.title)
                        put("amount", w.amount)
                        put("isLiability", w.isLiability)
                        put("isAutoSynced", w.isAutoSynced)
                        put("linkedSourceKey", w.linkedSourceKey)
                        put("notes", w.notes)
                    })
                }
            })

            put("customCategories", JSONArray().apply {
                customCategories.forEach { c ->
                    put(JSONObject().apply {
                        put("entryGroup", c.entryGroup)
                        put("name", c.name)
                    })
                }
            })

            put("documents", JSONArray().apply {
                filteredDocs.forEach { d ->
                    put(JSONObject().apply {
                        put("taxYear", d.taxYear)
                        put("title", d.title)
                        put("category", d.category)
                        put("mimeType", d.mimeType)
                        put("originalFileName", d.originalFileName)
                        val file = File(d.filePath)
                        if (file.exists() && file.isFile && file.length() <= MAX_EMBED_DOC_BYTES) {
                            put("contentBase64", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
                        }
                    })
                }
            })
        }

        val plainBytes = root.toString().toByteArray(Charsets.UTF_8)
        return TaxCryptoHelper.encryptBackupWithPassphrase(plainBytes, passphrase)
    }

    fun parseEncryptedBackup(
        context: Context,
        encryptedBytes: ByteArray,
        passphrase: String
    ): RestoredTaxBundle {
        val plainBytes = TaxCryptoHelper.decryptBackupWithPassphrase(encryptedBytes, passphrase)
        val root = JSONObject(String(plainBytes, Charsets.UTF_8))
        val scopeTaxYear = if (root.isNull("scopeTaxYear")) null else root.optInt("scopeTaxYear")

        val profile = root.optJSONObject("profile")?.let { p ->
            TaxProfileEntity(
                fullName = p.optString("fullName", ""),
                cnicEncrypted = TaxCryptoHelper.encryptSensitive(context, p.optString("cnicPlain", "")),
                ntnEncrypted = TaxCryptoHelper.encryptSensitive(context, p.optString("ntnPlain", "")),
                filerStatus = p.optString("filerStatus", "Filer (ATL Active)"),
                occupationType = p.optString("occupationType", "Salaried"),
                note = p.optString("note", "")
            )
        }

        val years = buildList {
            val arr = root.optJSONArray("years") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val y = arr.optJSONObject(i) ?: continue
                val yr = y.optInt("taxYear", 0)
                if (yr <= 0) continue
                add(
                    TaxYearEntity(
                        taxYear = yr,
                        label = y.optString("label", PakistanTaxYearUtils.formatTaxYearLabel(yr)),
                        startDate = y.optString("startDate", PakistanTaxYearUtils.startDateForTaxYear(yr)),
                        endDate = y.optString("endDate", PakistanTaxYearUtils.endDateForTaxYear(yr)),
                        filingDeadline = y.optString("filingDeadline", ""),
                        reminderDaysBefore = y.optInt("reminderDaysBefore", 7),
                        isLocked = y.optBoolean("isLocked", false),
                        lockedAt = y.optLong("lockedAt", 0L),
                        customSlabJson = y.optString("customSlabJson", "")
                    )
                )
            }
        }

        val entries = buildList {
            val arr = root.optJSONArray("entries") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                add(
                    TaxEntryEntity(
                        taxYear = e.optInt("taxYear", PakistanTaxYearUtils.currentTaxYear()),
                        entryGroup = e.optString("entryGroup", TAX_GROUP_INCOME),
                        category = e.optString("category", "Other"),
                        amount = e.optDouble("amount", 0.0),
                        date = e.optString("date", DateUtils.getCurrentDate()),
                        sourceOrPayer = e.optString("sourceOrPayer", ""),
                        referenceNumber = e.optString("referenceNumber", ""),
                        proofNote = e.optString("proofNote", ""),
                        notes = e.optString("notes", "")
                    )
                )
            }
        }

        val wealthItems = buildList {
            val arr = root.optJSONArray("wealthItems") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val w = arr.optJSONObject(i) ?: continue
                add(
                    TaxWealthItemEntity(
                        taxYear = w.optInt("taxYear", PakistanTaxYearUtils.currentTaxYear()),
                        periodType = w.optString("periodType", WEALTH_PERIOD_CLOSING),
                        assetCategory = w.optString("assetCategory", WEALTH_CAT_CASH_IN_HAND),
                        title = w.optString("title", "Item"),
                        amount = w.optDouble("amount", 0.0),
                        isLiability = w.optBoolean("isLiability", false),
                        isAutoSynced = w.optBoolean("isAutoSynced", false),
                        linkedSourceKey = w.optString("linkedSourceKey", ""),
                        notes = w.optString("notes", "")
                    )
                )
            }
        }

        val customCategories = buildList {
            val arr = root.optJSONArray("customCategories") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val name = c.optString("name", "").trim()
                val group = c.optString("entryGroup", "").trim()
                if (name.isNotEmpty() && group.isNotEmpty()) {
                    add(TaxCustomCategoryEntity(entryGroup = group, name = name))
                }
            }
        }

        val documents = buildList {
            val arr = root.optJSONArray("documents") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val d = arr.optJSONObject(i) ?: continue
                val b64 = d.optString("contentBase64", "")
                val bytes = if (b64.isNotBlank()) Base64.decode(b64, Base64.NO_WRAP) else null
                add(
                    RestoredTaxDocument(
                        taxYear = d.optInt("taxYear", PakistanTaxYearUtils.currentTaxYear()),
                        title = d.optString("title", "Document"),
                        category = d.optString("category", "Other"),
                        mimeType = d.optString("mimeType", "application/octet-stream"),
                        originalFileName = d.optString("originalFileName", "document"),
                        contentBytes = bytes
                    )
                )
            }
        }

        return RestoredTaxBundle(
            scopeTaxYear = scopeTaxYear,
            profile = profile,
            years = years,
            entries = entries,
            wealthItems = wealthItems,
            customCategories = customCategories,
            documents = documents
        )
    }
}
