package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.util.DateUtils
import com.example.util.PakistanTaxYearUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxCalendarDatePickerDialog(
    initialDate: String,
    onDismiss: () -> Unit,
    onDateSelected: (String) -> Unit
) {
    val initialMillis = remember(initialDate) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.parse(initialDate)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    onDateSelected(sdf.format(Date(millis)))
                } ?: onDismiss()
            }) { Text("OK", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
fun TaxProfileDialog(
    existingProfile: TaxProfileEntity?,
    initialDecryptedCnic: String,
    initialDecryptedNtn: String,
    onDismiss: () -> Unit,
    onSave: (fullName: String, cnic: String, ntn: String, filerStatus: String, occupationType: String, note: String) -> Unit
) {
    var fullName by remember { mutableStateOf(existingProfile?.fullName ?: "") }
    var cnic by remember { mutableStateOf(initialDecryptedCnic) }
    var ntn by remember { mutableStateOf(initialDecryptedNtn) }
    var filerStatus by remember { mutableStateOf(existingProfile?.filerStatus ?: PakistanTaxConstants.FILER_STATUSES.first()) }
    var occupationType by remember { mutableStateOf(existingProfile?.occupationType ?: PakistanTaxConstants.OCCUPATION_TYPES.first()) }
    var note by remember { mutableStateOf(existingProfile?.note ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Taxpayer Profile (Encrypted)", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; errorMsg = null },
                    label = { Text("Full Legal Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tax_profile_name_input")
                )
                OutlinedTextField(
                    value = cnic,
                    onValueChange = { cnic = it },
                    label = { Text("CNIC (13 digits, AES-256 encrypted)") },
                    placeholder = { Text("e.g. 35202-1234567-1") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tax_profile_cnic_input")
                )
                OutlinedTextField(
                    value = ntn,
                    onValueChange = { ntn = it },
                    label = { Text("NTN (AES-256 encrypted)") },
                    placeholder = { Text("e.g. 1234567-8") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tax_profile_ntn_input")
                )

                Text("Filer Status", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PakistanTaxConstants.FILER_STATUSES.forEach { status ->
                        FilterChip(
                            selected = filerStatus == status,
                            onClick = { filerStatus = status },
                            label = { Text(status, fontSize = 11.sp) }
                        )
                    }
                }

                Text("Occupation Type", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PakistanTaxConstants.OCCUPATION_TYPES.forEach { occ ->
                        FilterChip(
                            selected = occupationType == occ,
                            onClick = { occupationType = occ },
                            label = { Text(occ, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (Tax Office / IRIS Ref)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fullName.isBlank()) {
                        errorMsg = "Please enter your full legal name."
                        return@Button
                    }
                    onSave(fullName.trim(), cnic.trim(), ntn.trim(), filerStatus, occupationType, note.trim())
                },
                modifier = Modifier.testTag("tax_profile_save_button")
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TaxEntryDialog(
    entryGroup: String,
    taxYear: Int,
    customCategories: List<TaxCustomCategoryEntity>,
    documents: List<TaxDocumentEntity>,
    entryToEdit: TaxDisplayItem? = null,
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        category: String,
        amount: Double,
        date: String,
        sourceOrPayer: String,
        referenceNumber: String,
        proofNote: String,
        documentId: Long?,
        notes: String
    ) -> Unit
) {
    val defaultCats = PakistanTaxConstants.defaultCategoriesForGroup(entryGroup)
    val customCatNames = customCategories.filter { it.entryGroup == entryGroup }.map { it.name }
    val allCats = (defaultCats + customCatNames).distinct()

    var category by remember { mutableStateOf(entryToEdit?.category ?: allCats.firstOrNull().orEmpty()) }
    var amountText by remember { mutableStateOf(if (entryToEdit != null) entryToEdit.amount.toString() else "") }
    var date by remember {
        mutableStateOf(
            entryToEdit?.date ?: DateUtils.getCurrentDate().takeIf {
                PakistanTaxYearUtils.isDateInTaxYear(it, taxYear)
            } ?: PakistanTaxYearUtils.endDateForTaxYear(taxYear)
        )
    }
    var sourceOrPayer by remember { mutableStateOf(entryToEdit?.sourceOrPayer ?: "") }
    var referenceNumber by remember { mutableStateOf(entryToEdit?.referenceNumber ?: "") }
    var proofNote by remember { mutableStateOf(entryToEdit?.proofNote ?: "") }
    var selectedDocId by remember { mutableStateOf<Long?>(entryToEdit?.documentId) }
    var notes by remember { mutableStateOf(entryToEdit?.notes ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val dialogTitle = when (entryGroup) {
        TAX_GROUP_INCOME -> if (entryToEdit != null) "Edit Income" else "Add Tax Income"
        TAX_GROUP_WITHHOLDING -> if (entryToEdit != null) "Edit Withholding Tax" else "Add Withholding Tax"
        TAX_GROUP_DIRECT_TAX -> if (entryToEdit != null) "Edit Direct Tax Paid" else "Add Direct Tax / Challan"
        TAX_GROUP_DEDUCTION -> if (entryToEdit != null) "Edit Deduction / Credit" else "Add Deduction / Credit"
        else -> if (entryToEdit != null) "Edit Tax Expense" else "Add Tax Expense"
    }

    val sourceLabel = when (entryGroup) {
        TAX_GROUP_INCOME -> "Source / Payer Name"
        TAX_GROUP_WITHHOLDING -> "Deducted By (Bank / Employer / Utility)"
        TAX_GROUP_DIRECT_TAX -> "Bank / FBR Payment Channel"
        TAX_GROUP_DEDUCTION -> "Donee / Institution / Insurer"
        else -> "Payee / Vendor"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }

                Text("Category (pick or type custom) *", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it; errorMsg = null },
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tax_entry_category_input")
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allCats.forEach { cat ->
                        FilterChip(
                            selected = category.equals(cat, ignoreCase = true),
                            onClick = { category = cat; errorMsg = null },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("""^\d*\.?\d*$"""))) {
                            amountText = input
                            errorMsg = null
                        }
                    },
                    label = { Text("Amount (PKR) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tax_entry_amount_input")
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Date (YYYY-MM-DD)") },
                        trailingIcon = {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date", tint = MaterialTheme.colorScheme.primary)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { showDatePicker = true })
                }

                OutlinedTextField(
                    value = sourceOrPayer,
                    onValueChange = { sourceOrPayer = it },
                    label = { Text(sourceLabel) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tax_entry_source_input")
                )

                if (entryGroup == TAX_GROUP_WITHHOLDING || entryGroup == TAX_GROUP_DIRECT_TAX || entryGroup == TAX_GROUP_DEDUCTION) {
                    OutlinedTextField(
                        value = referenceNumber,
                        onValueChange = { referenceNumber = it },
                        label = {
                            Text(
                                if (entryGroup == TAX_GROUP_DIRECT_TAX) "CPR / Challan Number"
                                else "Certificate / Receipt / Reference No."
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tax_entry_ref_input")
                    )
                    OutlinedTextField(
                        value = proofNote,
                        onValueChange = { proofNote = it },
                        label = { Text("Payment Proof / Verification Note") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tax_entry_proof_input")
                    )
                }

                if (documents.isNotEmpty()) {
                    Text("Link Vault Document (Optional)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedDocId == null,
                            onClick = { selectedDocId = null },
                            label = { Text("None", fontSize = 11.sp) }
                        )
                        documents.forEach { doc ->
                            FilterChip(
                                selected = selectedDocId == doc.id,
                                onClick = { selectedDocId = doc.id },
                                label = { Text(doc.title, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tax_entry_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (category.isBlank()) {
                        errorMsg = "Please enter or choose a category."
                        return@Button
                    }
                    if (amt <= 0.0) {
                        errorMsg = "Please enter an amount greater than 0."
                        return@Button
                    }
                    onSave(
                        entryToEdit?.id ?: 0L,
                        category.trim(),
                        amt,
                        date,
                        sourceOrPayer.trim(),
                        referenceNumber.trim(),
                        proofNote.trim(),
                        selectedDocId,
                        notes.trim()
                    )
                },
                modifier = Modifier.testTag("tax_entry_save_button")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        TaxCalendarDatePickerDialog(
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onDateSelected = {
                date = it
                showDatePicker = false
            }
        )
    }
}

@Composable
fun TaxWealthItemDialog(
    initialPeriod: String,
    itemToEdit: TaxWealthItemEntity? = null,
    onDismiss: () -> Unit,
    onSave: (id: Long, periodType: String, category: String, title: String, amount: Double, isLiability: Boolean, notes: String) -> Unit
) {
    var periodType by remember { mutableStateOf(itemToEdit?.periodType ?: initialPeriod) }
    var assetCategory by remember { mutableStateOf(itemToEdit?.assetCategory ?: WEALTH_CAT_PROPERTY) }
    var title by remember { mutableStateOf(itemToEdit?.title ?: "") }
    var amountText by remember { mutableStateOf(if (itemToEdit != null) itemToEdit.amount.toString() else "") }
    var notes by remember { mutableStateOf(itemToEdit?.notes ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (itemToEdit != null) "Edit Wealth Item" else "Add Wealth Asset / Liability", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }

                Text("Snapshot Period", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = periodType == WEALTH_PERIOD_OPENING,
                        onClick = { periodType = WEALTH_PERIOD_OPENING },
                        label = { Text("Opening (1 Jul)") },
                        modifier = Modifier.testTag("wealth_period_opening_chip")
                    )
                    FilterChip(
                        selected = periodType == WEALTH_PERIOD_CLOSING,
                        onClick = { periodType = WEALTH_PERIOD_CLOSING },
                        label = { Text("Closing (30 Jun)") },
                        modifier = Modifier.testTag("wealth_period_closing_chip")
                    )
                }

                Text("Category", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PakistanTaxConstants.WEALTH_CATEGORIES.forEach { (key, pair) ->
                        val (label, _) = pair
                        FilterChip(
                            selected = assetCategory == key,
                            onClick = { assetCategory = key },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; errorMsg = null },
                    label = { Text("Asset / Liability Name *") },
                    placeholder = { Text("e.g. Plot 12B, Honda Civic, HBL Balance") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wealth_title_input")
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("""^\d*\.?\d*$"""))) {
                            amountText = input
                            errorMsg = null
                        }
                    },
                    label = { Text("Value / Amount (PKR) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wealth_amount_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Acquisition Detail") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    if (title.isBlank()) {
                        errorMsg = "Please enter an item title."
                        return@Button
                    }
                    if (amt == null || amt < 0.0) {
                        errorMsg = "Please enter a valid non-negative amount."
                        return@Button
                    }
                    onSave(
                        itemToEdit?.id ?: 0L,
                        periodType,
                        assetCategory,
                        title.trim(),
                        amt,
                        PakistanTaxConstants.isLiabilityCategory(assetCategory),
                        notes.trim()
                    )
                },
                modifier = Modifier.testTag("wealth_save_button")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TaxYearCreateDialog(
    suggestedYear: Int,
    onDismiss: () -> Unit,
    onCreate: (taxYear: Int, deadline: String, reminderDays: Int) -> Unit
) {
    var yearText by remember { mutableStateOf(suggestedYear.toString()) }
    val parsedYear = yearText.toIntOrNull() ?: suggestedYear
    var deadline by remember { mutableStateOf(PakistanTaxYearUtils.defaultFilingDeadlineForTaxYear(suggestedYear)) }
    var reminderDays by remember { mutableIntStateOf(7) }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Pakistan Tax Year", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                OutlinedTextField(
                    value = yearText,
                    onValueChange = { input ->
                        if (input.length <= 4 && input.all { it.isDigit() }) {
                            yearText = input
                            input.toIntOrNull()?.let { yr ->
                                if (yr in 2000..2100) {
                                    deadline = PakistanTaxYearUtils.defaultFilingDeadlineForTaxYear(yr)
                                }
                            }
                            errorMsg = null
                        }
                    },
                    label = { Text("Tax Year (e.g. 2027)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_tax_year_input")
                )
                if (parsedYear in 2000..2100) {
                    Text(
                        text = PakistanTaxYearUtils.formatTaxYearLabel(parsedYear),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = deadline,
                        onValueChange = { deadline = it },
                        label = { Text("Editable Filing Deadline (YYYY-MM-DD)") },
                        trailingIcon = {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Deadline")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { showDatePicker = true })
                }
                Text("Reminder Notification Before Deadline", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Off", 3 to "3d", 7 to "7d", 14 to "14d", 30 to "30d").forEach { (days, lbl) ->
                        FilterChip(
                            selected = reminderDays == days,
                            onClick = { reminderDays = days },
                            label = { Text(lbl, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val yr = yearText.toIntOrNull()
                    if (yr == null || yr !in 2000..2100) {
                        errorMsg = "Enter a 4-digit tax year between 2000 and 2100."
                        return@Button
                    }
                    onCreate(yr, deadline.trim(), reminderDays)
                },
                modifier = Modifier.testTag("create_tax_year_confirm_button")
            ) { Text("Create Year") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        TaxCalendarDatePickerDialog(
            initialDate = deadline,
            onDismiss = { showDatePicker = false },
            onDateSelected = {
                deadline = it
                showDatePicker = false
            }
        )
    }
}

@Composable
fun TaxDeadlineDialog(
    taxYear: TaxYearEntity,
    onDismiss: () -> Unit,
    onSave: (deadline: String, reminderDays: Int) -> Unit
) {
    var deadline by remember { mutableStateOf(taxYear.filingDeadline.ifBlank { PakistanTaxYearUtils.defaultFilingDeadlineForTaxYear(taxYear.taxYear) }) }
    var reminderDays by remember { mutableIntStateOf(taxYear.reminderDaysBefore) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filing Deadline & Reminder", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = taxYear.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = deadline,
                        onValueChange = { deadline = it },
                        label = { Text("Filing Deadline (YYYY-MM-DD)") },
                        trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Deadline") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("deadline_date_input")
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { showDatePicker = true })
                }
                Text("Local Reminder Notification", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0 to "Off", 3 to "3 days", 7 to "7 days", 14 to "14 days", 30 to "30 days").forEach { (days, lbl) ->
                        FilterChip(
                            selected = reminderDays == days,
                            onClick = { reminderDays = days },
                            label = { Text(lbl, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(deadline.trim(), reminderDays) },
                modifier = Modifier.testTag("deadline_save_button")
            ) { Text("Save Deadline") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        TaxCalendarDatePickerDialog(
            initialDate = deadline,
            onDismiss = { showDatePicker = false },
            onDateSelected = {
                deadline = it
                showDatePicker = false
            }
        )
    }
}

@Composable
fun TaxSlabEditorDialog(
    initialSlabs: List<TaxSlabItem>,
    onDismiss: () -> Unit,
    onSave: (List<TaxSlabItem>) -> Unit
) {
    var slabs by remember { mutableStateOf(initialSlabs) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editable Tax Slabs", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Customize your own slab thresholds and rates for planning estimates. Official FBR rates change yearly.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { slabs = PakistanTaxYearUtils.editableStarterTemplate() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) { Text("Load Template", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = {
                            val nextMin = slabs.lastOrNull()?.maxIncome ?: 0.0
                            slabs = slabs + TaxSlabItem(nextMin, null, 0.0, 0.0)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) { Text("+ Add Slab", fontSize = 11.sp) }
                    TextButton(
                        onClick = { slabs = emptyList() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) { Text("Clear", fontSize = 11.sp) }
                }

                slabs.forEachIndexed { idx, slab ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Slab #${idx + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                IconButton(
                                    onClick = { slabs = slabs.filterIndexed { i, _ -> i != idx } },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove slab", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = slab.minIncome.toLong().toString(),
                                    onValueChange = { v ->
                                        val parsed = v.toDoubleOrNull() ?: 0.0
                                        slabs = slabs.toMutableList().also { it[idx] = slab.copy(minIncome = parsed) }
                                    },
                                    label = { Text("Min PKR", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = slab.maxIncome?.toLong()?.toString() ?: "",
                                    onValueChange = { v ->
                                        val parsed = if (v.isBlank()) null else v.toDoubleOrNull()
                                        slabs = slabs.toMutableList().also { it[idx] = slab.copy(maxIncome = parsed) }
                                    },
                                    label = { Text("Max (blank=∞)", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = slab.fixedTax.toLong().toString(),
                                    onValueChange = { v ->
                                        val parsed = v.toDoubleOrNull() ?: 0.0
                                        slabs = slabs.toMutableList().also { it[idx] = slab.copy(fixedTax = parsed) }
                                    },
                                    label = { Text("Fixed Tax", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = slab.ratePercent.toString(),
                                    onValueChange = { v ->
                                        val parsed = v.toDoubleOrNull() ?: 0.0
                                        slabs = slabs.toMutableList().also { it[idx] = slab.copy(ratePercent = parsed) }
                                    },
                                    label = { Text("Rate %", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(slabs) }) { Text("Save Slabs") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TaxBackupPassphraseDialog(
    isRestore: Boolean,
    selectedTaxYear: Int,
    onDismiss: () -> Unit,
    onConfirm: (passphrase: String, scopeTaxYear: Int?) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var scopeSingleYear by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isRestore) "Restore Encrypted Tax Backup" else "Create Encrypted Tax Backup",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isRestore) {
                        "Enter the passphrase used when creating this .fptax backup file."
                    } else {
                        "Your tax backup is encrypted with AES-256-GCM. Save the generated .fptax file to Google Drive or device storage."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }

                if (!isRestore) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !scopeSingleYear,
                            onClick = { scopeSingleYear = false },
                            label = { Text("All Tax Years", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = scopeSingleYear,
                            onClick = { scopeSingleYear = true },
                            label = { Text("Only TY $selectedTaxYear", fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it; errorMsg = null },
                    label = { Text("Backup Passphrase (min 6 chars)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tax_backup_passphrase_input")
                )

                if (!isRestore) {
                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = { confirmPass = it; errorMsg = null },
                        label = { Text("Confirm Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = passphrase.trim()
                    if (clean.length < 6) {
                        errorMsg = "Passphrase must be at least 6 characters."
                        return@Button
                    }
                    if (!isRestore && clean != confirmPass.trim()) {
                        errorMsg = "Passphrases do not match."
                        return@Button
                    }
                    onConfirm(clean, if (scopeSingleYear) selectedTaxYear else null)
                },
                modifier = Modifier.testTag("tax_backup_confirm_button")
            ) {
                Text(if (isRestore) "Decrypt & Restore" else "Encrypt & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
