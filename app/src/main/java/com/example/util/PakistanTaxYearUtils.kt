package com.example.util

import com.example.data.model.TaxSlabItem
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Pakistan Income Tax Year utilities (1 July to 30 June) and editable slab calculations.
 */
object PakistanTaxYearUtils {

    private val TIMEZONE_KARACHI = TimeZone.getTimeZone("Asia/Karachi")

    /**
     * Pakistan Tax Year runs from 1 July (year - 1) to 30 June (year).
     * Examples:
     * - 2026-07-01 -> Tax Year 2027 (1 Jul 2026 - 30 Jun 2027)
     * - 2027-06-30 -> Tax Year 2027
     * - 2026-06-30 -> Tax Year 2026
     */
    fun taxYearForDate(dateStr: String): Int {
        return try {
            val parts = dateStr.trim().split("-")
            val year = parts.getOrNull(0)?.toIntOrNull() ?: return currentTaxYear()
            val month = parts.getOrNull(1)?.toIntOrNull() ?: return currentTaxYear()
            if (month >= 7) year + 1 else year
        } catch (_: Exception) {
            currentTaxYear()
        }
    }

    fun currentTaxYear(todayStr: String = DateUtils.getCurrentDate()): Int {
        return taxYearForDate(todayStr)
    }

    fun startDateForTaxYear(taxYear: Int): String = "${taxYear - 1}-07-01"

    fun endDateForTaxYear(taxYear: Int): String = "$taxYear-06-30"

    /**
     * Initial editable suggestion for return filing deadline (user can edit anytime).
     */
    fun defaultFilingDeadlineForTaxYear(taxYear: Int): String = "$taxYear-09-30"

    fun formatTaxYearLabel(taxYear: Int): String {
        return "Tax Year $taxYear (1 Jul ${taxYear - 1} - 30 Jun $taxYear)"
    }

    fun formatShortTaxYearLabel(taxYear: Int): String = "TY $taxYear"

    fun daysRemainingBetween(fromDateStr: String, toDateStr: String): Long {
        if (fromDateStr.isBlank() || toDateStr.isBlank()) return 0L
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TIMEZONE_KARACHI
                isLenient = false
            }
            val from = sdf.parse(fromDateStr) ?: return 0L
            val to = sdf.parse(toDateStr) ?: return 0L
            TimeUnit.MILLISECONDS.toDays(to.time - from.time)
        } catch (_: Exception) {
            0L
        }
    }

    fun daysRemainingInTaxYear(taxYear: Int, todayStr: String = DateUtils.getCurrentDate()): Long {
        return daysRemainingBetween(todayStr, endDateForTaxYear(taxYear)).coerceAtLeast(0L)
    }

    fun daysUntilDeadline(deadlineStr: String, todayStr: String = DateUtils.getCurrentDate()): Long? {
        if (deadlineStr.isBlank()) return null
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TIMEZONE_KARACHI
                isLenient = false
            }
            val from = sdf.parse(todayStr) ?: return null
            val to = sdf.parse(deadlineStr) ?: return null
            TimeUnit.MILLISECONDS.toDays(to.time - from.time)
        } catch (_: Exception) {
            null
        }
    }

    fun isDateInTaxYear(dateStr: String, taxYear: Int): Boolean {
        val clean = dateStr.trim()
        if (clean.length < 10) return false
        return clean >= startDateForTaxYear(taxYear) && clean <= endDateForTaxYear(taxYear)
    }

    fun parseSlabsJson(json: String): List<TaxSlabItem> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return emptyList()
        return try {
            val arr = JSONArray(trimmed)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val min = obj.optDouble("minIncome", 0.0)
                    val max = if (obj.isNull("maxIncome")) null else obj.optDouble("maxIncome", 0.0)
                    val fixed = obj.optDouble("fixedTax", 0.0)
                    val rate = obj.optDouble("ratePercent", 0.0)
                    add(TaxSlabItem(minIncome = min, maxIncome = max, fixedTax = fixed, ratePercent = rate))
                }
            }.sortedBy { it.minIncome }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun serializeSlabsJson(slabs: List<TaxSlabItem>): String {
        val arr = JSONArray()
        for (slab in slabs.sortedBy { it.minIncome }) {
            val obj = JSONObject().apply {
                put("minIncome", slab.minIncome)
                if (slab.maxIncome == null) put("maxIncome", JSONObject.NULL) else put("maxIncome", slab.maxIncome)
                put("fixedTax", slab.fixedTax)
                put("ratePercent", slab.ratePercent)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    /**
     * Computes an estimate strictly from the user's own entered slab table.
     */
    fun estimateTaxFromUserSlabs(taxableIncome: Double, slabs: List<TaxSlabItem>): Double {
        if (taxableIncome <= 0.0 || slabs.isEmpty()) return 0.0
        val sorted = slabs.sortedBy { it.minIncome }
        val matched = sorted.lastOrNull { slab ->
            taxableIncome >= slab.minIncome && (slab.maxIncome == null || taxableIncome <= slab.maxIncome)
        } ?: sorted.lastOrNull { taxableIncome >= it.minIncome }
        if (matched == null) return 0.0
        val excess = (taxableIncome - matched.minIncome).coerceAtLeast(0.0)
        return (matched.fixedTax + excess * (matched.ratePercent / 100.0)).coerceAtLeast(0.0)
    }

    /**
     * Blank/editable starter template the user can load and customize themselves.
     */
    fun editableStarterTemplate(): List<TaxSlabItem> = listOf(
        TaxSlabItem(minIncome = 0.0, maxIncome = 600_000.0, fixedTax = 0.0, ratePercent = 0.0),
        TaxSlabItem(minIncome = 600_000.0, maxIncome = 1_200_000.0, fixedTax = 0.0, ratePercent = 5.0),
        TaxSlabItem(minIncome = 1_200_000.0, maxIncome = 2_200_000.0, fixedTax = 30_000.0, ratePercent = 15.0),
        TaxSlabItem(minIncome = 2_200_000.0, maxIncome = null, fixedTax = 180_000.0, ratePercent = 25.0)
    )
}
