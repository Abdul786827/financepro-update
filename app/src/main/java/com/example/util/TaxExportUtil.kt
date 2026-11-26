package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.*
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates FBR-ordered Tax Return Reports (PDF, Excel-compatible CSV, Documents ZIP)
 * and Android share intents (WhatsApp / Email / Drive).
 * Always masks CNIC/NTN and includes the record-keeping disclaimer.
 */
object TaxExportUtil {

    private fun csvEscape(value: String): String {
        val clean = value.replace("\r", " ").replace("\n", " ")
        return if (clean.contains(",") || clean.contains("\"")) {
            "\"${clean.replace("\"", "\"\"")}\""
        } else {
            clean
        }
    }

    fun generateTaxCsvContent(
        taxYear: TaxYearEntity,
        profile: TaxProfileEntity?,
        maskedCnic: String,
        maskedNtn: String,
        summary: TaxYearSummary,
        incomeItems: List<TaxDisplayItem>,
        withholdingItems: List<TaxDisplayItem>,
        directTaxItems: List<TaxDisplayItem>,
        deductionItems: List<TaxDisplayItem>,
        expenseItems: List<TaxDisplayItem>,
        wealthItems: List<TaxWealthItemEntity>
    ): String {
        val sb = StringBuilder()
        // UTF-8 BOM for clean Excel opening
        sb.append('\uFEFF')
        sb.append("FINANCE PRO - PAKISTAN TAXPAYER RECORD REPORT\n")
        sb.append("Disclaimer,${csvEscape(TAX_DISCLAIMER_TEXT)}\n")
        sb.append("Tax Year,${csvEscape(taxYear.label)}\n")
        sb.append("Status,${if (taxYear.isLocked) "Locked / Archived" else "Active"}\n")
        sb.append("Filing Deadline,${csvEscape(taxYear.filingDeadline.ifBlank { "Not set" })}\n\n")

        sb.append("SECTION 0: TAXPAYER PROFILE\n")
        sb.append("Full Name,CNIC (Masked),NTN (Masked),Filer Status,Occupation Type,Note\n")
        sb.append(
            listOf(
                csvEscape(profile?.fullName.orEmpty().ifBlank { "Not set" }),
                csvEscape(maskedCnic),
                csvEscape(maskedNtn),
                csvEscape(profile?.filerStatus ?: "Not set"),
                csvEscape(profile?.occupationType ?: "Not set"),
                csvEscape(profile?.note.orEmpty())
            ).joinToString(",")
        ).append("\n\n")

        sb.append("SECTION 1: INCOME (FBR INCOME HEADS)\n")
        sb.append("Date,Category,Source / Payer,Amount (PKR),Origin,Notes\n")
        for (item in incomeItems) {
            sb.append(
                listOf(
                    csvEscape(item.date),
                    csvEscape(item.category),
                    csvEscape(item.sourceOrPayer),
                    item.amount.toString(),
                    if (item.isLinkedTransaction) "Linked Transaction" else "Tax Record",
                    csvEscape(item.notes)
                ).joinToString(",")
            ).append("\n")
        }
        sb.append("Total Income,,,${summary.totalIncome},,\n\n")

        sb.append("SECTION 2A: TAX DEDUCTED AT SOURCE (WITHHOLDING TAX)\n")
        sb.append("Date,Type / Section,Deducted By,Amount (PKR),Payment Proof / Ref,Origin,Notes\n")
        for (item in withholdingItems) {
            sb.append(
                listOf(
                    csvEscape(item.date),
                    csvEscape(item.category),
                    csvEscape(item.sourceOrPayer),
                    item.amount.toString(),
                    csvEscape(item.proofNote.ifBlank { item.referenceNumber }),
                    if (item.isLinkedTransaction) "Linked Transaction" else "Tax Record",
                    csvEscape(item.notes)
                ).joinToString(",")
            ).append("\n")
        }
        sb.append("Total Withholding Tax,,,${summary.totalWithholdingTax},,,\n\n")

        sb.append("SECTION 2B: DIRECT TAX PAID (ADVANCE TAX / CHALLAN / CPR)\n")
        sb.append("Date,Category,CPR / Challan No.,Amount (PKR),Proof,Notes\n")
        for (item in directTaxItems) {
            sb.append(
                listOf(
                    csvEscape(item.date),
                    csvEscape(item.category),
                    csvEscape(item.referenceNumber),
                    item.amount.toString(),
                    csvEscape(item.proofNote),
                    csvEscape(item.notes)
                ).joinToString(",")
            ).append("\n")
        }
        sb.append("Total Direct Tax Paid,,,${summary.totalDirectTaxPaid},,\n\n")

        sb.append("SECTION 3A: DEDUCTIONS & TAX CREDITS\n")
        sb.append("Date,Category,Institution / Donee,Amount (PKR),Proof,Origin,Notes\n")
        for (item in deductionItems) {
            sb.append(
                listOf(
                    csvEscape(item.date),
                    csvEscape(item.category),
                    csvEscape(item.sourceOrPayer),
                    item.amount.toString(),
                    csvEscape(item.proofNote.ifBlank { item.referenceNumber }),
                    if (item.isLinkedTransaction) "Linked Transaction" else "Tax Record",
                    csvEscape(item.notes)
                ).joinToString(",")
            ).append("\n")
        }
        sb.append("Total Deductions & Credits,,,${summary.totalDeductions},,,\n\n")

        sb.append("SECTION 3B: TAX-RELEVANT EXPENSES\n")
        sb.append("Date,Category,Payee,Amount (PKR),Receipt / Proof,Origin,Notes\n")
        for (item in expenseItems) {
            sb.append(
                listOf(
                    csvEscape(item.date),
                    csvEscape(item.category),
                    csvEscape(item.sourceOrPayer),
                    item.amount.toString(),
                    csvEscape(item.proofNote.ifBlank { item.referenceNumber }),
                    if (item.isLinkedTransaction) "Linked Transaction" else "Tax Record",
                    csvEscape(item.notes)
                ).joinToString(",")
            ).append("\n")
        }
        sb.append("Total Tax-Relevant Expenses,,,${summary.totalTaxExpenses},,,\n\n")

        sb.append("SECTION 4: WEALTH STATEMENT (ASSETS & LIABILITIES)\n")
        sb.append("Period,Type,Category,Title,Amount (PKR),Auto-Synced,Notes\n")
        for (w in wealthItems) {
            sb.append(
                listOf(
                    csvEscape(w.periodType),
                    if (w.isLiability) "Liability" else "Asset",
                    csvEscape(PakistanTaxConstants.wealthCategoryLabel(w.assetCategory)),
                    csvEscape(w.title),
                    w.amount.toString(),
                    if (w.isAutoSynced) "Yes" else "No",
                    csvEscape(w.notes)
                ).joinToString(",")
            ).append("\n")
        }
        sb.append("\nWEALTH RECONCILIATION SUMMARY\n")
        sb.append("Opening Assets,${summary.openingAssets}\n")
        sb.append("Opening Liabilities,${summary.openingLiabilities}\n")
        sb.append("Opening Net Wealth,${summary.openingNetWealth}\n")
        sb.append("Closing Assets,${summary.closingAssets}\n")
        sb.append("Closing Liabilities,${summary.closingLiabilities}\n")
        sb.append("Closing Net Wealth,${summary.closingNetWealth}\n")
        sb.append("Wealth Change (Closing - Opening),${summary.wealthDifference}\n")
        sb.append("Net Declared Inflow (Income - Outflows),${summary.netDeclaredInflow}\n")
        sb.append("Unreconciled Difference,${summary.unreconciledDifference}\n")

        return sb.toString()
    }

    fun writeTaxCsvFile(
        context: Context,
        taxYear: TaxYearEntity,
        csvContent: String
    ): File {
        val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
        val file = File(reportsDir, "Pakistan_Tax_TY${taxYear.taxYear}_Return_Report.csv")
        FileOutputStream(file).use { it.write(csvContent.toByteArray(Charsets.UTF_8)) }
        return file
    }

    fun writeTaxPdfFile(
        context: Context,
        taxYear: TaxYearEntity,
        profile: TaxProfileEntity?,
        maskedCnic: String,
        maskedNtn: String,
        summary: TaxYearSummary,
        incomeItems: List<TaxDisplayItem>,
        withholdingItems: List<TaxDisplayItem>,
        directTaxItems: List<TaxDisplayItem>,
        deductionItems: List<TaxDisplayItem>,
        expenseItems: List<TaxDisplayItem>,
        wealthItems: List<TaxWealthItemEntity>
    ): File {
        val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
        val pdfFile = File(reportsDir, "Pakistan_Tax_TY${taxYear.taxYear}_Return_Report.pdf")
        val fallbackLines = mutableListOf<String>()
        fallbackLines += "FINANCE PRO - PAKISTAN TAX RETURN RECORD"
        fallbackLines += "${taxYear.label} | Status: ${if (taxYear.isLocked) "Archived / Locked" else "Active"}"
        fallbackLines += TAX_DISCLAIMER_TEXT
        fallbackLines += "Name: ${profile?.fullName.orEmpty().ifBlank { "Not set" }} | Filer: ${profile?.filerStatus ?: "Not set"} | Occupation: ${profile?.occupationType ?: "Not set"}"
        fallbackLines += "CNIC (Masked): $maskedCnic | NTN (Masked): $maskedNtn | Deadline: ${taxYear.filingDeadline.ifBlank { "Not set" }}"
        fallbackLines += "1. INCOME: ${DateUtils.formatCurrency(summary.totalIncome)}"
        incomeItems.forEach { fallbackLines += "  ${it.date} | ${it.category} | ${it.sourceOrPayer} | ${DateUtils.formatCurrency(it.amount)}" }
        fallbackLines += "2A. WITHHOLDING TAX: ${DateUtils.formatCurrency(summary.totalWithholdingTax)}"
        withholdingItems.forEach { fallbackLines += "  ${it.date} | ${it.category} | ${it.sourceOrPayer} | ${DateUtils.formatCurrency(it.amount)}" }
        fallbackLines += "2B. DIRECT TAX PAID: ${DateUtils.formatCurrency(summary.totalDirectTaxPaid)}"
        directTaxItems.forEach { fallbackLines += "  ${it.date} | ${it.category} | CPR:${it.referenceNumber} | ${DateUtils.formatCurrency(it.amount)}" }
        fallbackLines += "3. DEDUCTIONS: ${DateUtils.formatCurrency(summary.totalDeductions)} | TAX EXPENSES: ${DateUtils.formatCurrency(summary.totalTaxExpenses)}"
        deductionItems.forEach { fallbackLines += "  [Deduction] ${it.date} | ${it.category} | ${DateUtils.formatCurrency(it.amount)}" }
        expenseItems.forEach { fallbackLines += "  [Expense] ${it.date} | ${it.category} | ${DateUtils.formatCurrency(it.amount)}" }
        fallbackLines += "4. WEALTH STATEMENT: Opening Net=${DateUtils.formatCurrency(summary.openingNetWealth)} | Closing Net=${DateUtils.formatCurrency(summary.closingNetWealth)} | Diff=${DateUtils.formatCurrency(summary.unreconciledDifference)}"

        val pdfDocument = PdfDocument()
        try {
            val pageWidth = 842
            val pageHeight = 1191
            var pageNum = 1
            var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            var canvas = page.canvas
            var y = 48f

            val titlePaint = Paint().apply {
                color = Color.rgb(0, 108, 80)
                textSize = 20f
                isFakeBoldText = true
            }
            val sectionPaint = Paint().apply {
                color = Color.rgb(0, 81, 59)
                textSize = 14f
                isFakeBoldText = true
            }
            val bodyPaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 11f
            }
            val boldPaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 11f
                isFakeBoldText = true
            }
            val warnPaint = Paint().apply {
                color = Color.rgb(180, 83, 9)
                textSize = 10.5f
                isFakeBoldText = true
            }
            val linePaint = Paint().apply {
                color = Color.rgb(203, 213, 225)
                strokeWidth = 1f
            }

            fun ensureSpace(neededHeight: Float) {
                if (y + neededHeight > pageHeight - 50f) {
                    pdfDocument.finishPage(page)
                    pageNum++
                    page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                    canvas = page.canvas
                    y = 48f
                }
            }

            fun drawHeading(text: String) {
                ensureSpace(34f)
                y += 10f
                canvas.drawText(text, 40f, y, sectionPaint)
                y += 6f
                canvas.drawLine(40f, y, pageWidth - 40f, y, linePaint)
                y += 16f
            }

            fun drawRow(left: String, mid: String, right: String, paint: Paint = bodyPaint) {
                ensureSpace(20f)
                canvas.drawText(left.take(40), 42f, y, paint)
                canvas.drawText(mid.take(34), 340f, y, paint)
                canvas.drawText(right.take(28), 620f, y, paint)
                y += 17f
            }

            canvas.drawText("FINANCE PRO — PAKISTAN TAX RETURN RECORD", 40f, y, titlePaint)
            y += 20f
            canvas.drawText("${taxYear.label} | Status: ${if (taxYear.isLocked) "Archived / Locked" else "Active"}", 40f, y, boldPaint)
            y += 16f
            canvas.drawText(TAX_DISCLAIMER_TEXT, 40f, y, warnPaint)
            y += 20f

            drawHeading("TAXPAYER PROFILE")
            drawRow("Name: ${profile?.fullName.orEmpty().ifBlank { "Not set" }}", "Filer: ${profile?.filerStatus ?: "Not set"}", "Occupation: ${profile?.occupationType ?: "Not set"}")
            drawRow("CNIC (Masked): $maskedCnic", "NTN (Masked): $maskedNtn", "Deadline: ${taxYear.filingDeadline.ifBlank { "Not set" }}")

            drawHeading("1. INCOME (${DateUtils.formatCurrency(summary.totalIncome)})")
            if (incomeItems.isEmpty()) {
                drawRow("No income entries recorded", "", "-")
            } else {
                incomeItems.forEach {
                    drawRow("${it.date} • ${it.category}", it.sourceOrPayer, DateUtils.formatCurrency(it.amount))
                }
            }

            drawHeading("2A. WITHHOLDING TAX AT SOURCE (${DateUtils.formatCurrency(summary.totalWithholdingTax)})")
            if (withholdingItems.isEmpty()) {
                drawRow("No withholding tax entries recorded", "", "-")
            } else {
                withholdingItems.forEach {
                    drawRow("${it.date} • ${it.category}", "${it.sourceOrPayer} ${it.proofNote}".trim(), DateUtils.formatCurrency(it.amount))
                }
            }

            drawHeading("2B. DIRECT TAX PAID / CHALLANS (${DateUtils.formatCurrency(summary.totalDirectTaxPaid)})")
            if (directTaxItems.isEmpty()) {
                drawRow("No direct tax payments recorded", "", "-")
            } else {
                directTaxItems.forEach {
                    drawRow("${it.date} • ${it.category}", "CPR/Challan: ${it.referenceNumber.ifBlank { "-" }}", DateUtils.formatCurrency(it.amount))
                }
            }

            drawHeading("3. DEDUCTIONS & CREDITS (${DateUtils.formatCurrency(summary.totalDeductions)}) + TAX EXPENSES (${DateUtils.formatCurrency(summary.totalTaxExpenses)})")
            if (deductionItems.isEmpty() && expenseItems.isEmpty()) {
                drawRow("No deductions or tax expenses recorded", "", "-")
            } else {
                deductionItems.forEach {
                    drawRow("[Deduction] ${it.date} • ${it.category}", it.sourceOrPayer, DateUtils.formatCurrency(it.amount))
                }
                expenseItems.forEach {
                    drawRow("[Expense] ${it.date} • ${it.category}", it.sourceOrPayer, DateUtils.formatCurrency(it.amount))
                }
            }

            drawHeading("4. WEALTH STATEMENT & RECONCILIATION")
            drawRow("Opening Assets: ${DateUtils.formatCurrency(summary.openingAssets)}", "Opening Liabilities: ${DateUtils.formatCurrency(summary.openingLiabilities)}", "Opening Net: ${DateUtils.formatCurrency(summary.openingNetWealth)}", boldPaint)
            drawRow("Closing Assets: ${DateUtils.formatCurrency(summary.closingAssets)}", "Closing Liabilities: ${DateUtils.formatCurrency(summary.closingLiabilities)}", "Closing Net: ${DateUtils.formatCurrency(summary.closingNetWealth)}", boldPaint)
            drawRow("Wealth Change: ${DateUtils.formatCurrency(summary.wealthDifference)}", "Net Inflow: ${DateUtils.formatCurrency(summary.netDeclaredInflow)}", "Diff: ${DateUtils.formatCurrency(summary.unreconciledDifference)}", boldPaint)
            wealthItems.forEach { w ->
                val kind = if (w.isLiability) "Liability" else "Asset"
                drawRow("${w.periodType} • $kind • ${PakistanTaxConstants.wealthCategoryLabel(w.assetCategory)}", w.title, DateUtils.formatCurrency(w.amount))
            }

            pdfDocument.finishPage(page)
            FileOutputStream(pdfFile).use { pdfDocument.writeTo(it) }
        } catch (_: IllegalStateException) {
            writeMinimalTextPdf(pdfFile, fallbackLines)
        } finally {
            runCatching { pdfDocument.close() }
        }
        return pdfFile
    }

    private fun writeMinimalTextPdf(pdfFile: File, lines: List<String>) {
        fun esc(s: String): String = s
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .filter { it.code in 32..126 }

        val contentStream = buildString {
            append("BT\n/F1 10 Tf\n40 800 Td\n14 TL\n")
            lines.take(52).forEachIndexed { idx, line ->
                if (idx == 0) {
                    append("(${esc(line)}) Tj\n")
                } else {
                    append("T* (${esc(line)}) Tj\n")
                }
            }
            append("ET\n")
        }
        val objects = listOf(
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n",
            "4 0 obj\n<< /Length ${contentStream.toByteArray(Charsets.US_ASCII).size} >>\nstream\n$contentStream\nendstream\nendobj\n",
            "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n"
        )
        val sb = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf(0)
        for (obj in objects) {
            offsets += sb.length
            sb.append(obj)
        }
        val xrefOffset = sb.length
        sb.append("xref\n0 ${objects.size + 1}\n")
        sb.append("0000000000 65535 f \n")
        for (i in 1..objects.size) {
            sb.append(String.format("%010d 00000 n \n", offsets[i]))
        }
        sb.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        FileOutputStream(pdfFile).use { it.write(sb.toString().toByteArray(Charsets.US_ASCII)) }
    }

    fun writeDocumentsZipFile(
        context: Context,
        taxYear: Int,
        documents: List<TaxDocumentEntity>
    ): File {
        val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
        val zipFile = File(reportsDir, "Pakistan_Tax_TY${taxYear}_Documents.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val usedNames = mutableSetOf<String>()
            var addedCount = 0
            for ((index, doc) in documents.withIndex()) {
                val srcFile = File(doc.filePath)
                if (!srcFile.exists() || !srcFile.isFile) continue
                val baseName = doc.originalFileName.ifBlank {
                    val sanitized = doc.title.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    if (doc.mimeType.contains("pdf", ignoreCase = true)) "$sanitized.pdf" else "$sanitized.dat"
                }
                val safeCategory = doc.category.replace(Regex("[^A-Za-z0-9_-]"), "_")
                var entryName = "${safeCategory}/$baseName"
                if (!usedNames.add(entryName)) {
                    entryName = "${safeCategory}/${index + 1}_$baseName"
                    usedNames.add(entryName)
                }
                zos.putNextEntry(ZipEntry(entryName))
                BufferedInputStream(FileInputStream(srcFile)).use { bis ->
                    bis.copyTo(zos)
                }
                zos.closeEntry()
                addedCount++
            }
            if (addedCount == 0) {
                zos.putNextEntry(ZipEntry("README.txt"))
                zos.write("No attached documents found for Tax Year $taxYear.\n$TAX_DISCLAIMER_TEXT\n".toByteArray())
                zos.closeEntry()
            }
        }
        return zipFile
    }

    fun shareFile(context: Context, file: File, mimeType: String, subject: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun shareTaxBundle(context: Context, files: List<File>, taxYear: Int) {
        val uris = ArrayList(files.map { file ->
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "Pakistan Tax Year $taxYear Package (PDF, CSV & Documents)")
            putExtra(Intent.EXTRA_TEXT, "Attached Finance Pro Tax Year $taxYear return records.\n$TAX_DISCLAIMER_TEXT")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Tax Package via WhatsApp / Email / Drive").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
