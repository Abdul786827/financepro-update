package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.theme.EmeraldPrimaryLight
import com.example.ui.theme.FinanceExpenseRed
import com.example.ui.theme.FinanceIncomeGreen
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.*
import java.io.File

private enum class TaxSubTab(val title: String, val tag: String) {
    SUMMARY("Summary", "tax_tab_summary"),
    PROFILE("Profile", "tax_tab_profile"),
    INCOME("Income", "tax_tab_income"),
    TAXES("Taxes Paid", "tax_tab_taxes"),
    DEDUCTIONS("Deduct & Exp", "tax_tab_deductions"),
    WEALTH("Wealth", "tax_tab_wealth"),
    DOCUMENTS("Docs", "tax_tab_docs")
}

@Composable
fun TaxScreen(
    viewModel: FinanceViewModel,
    isTaxUnlocked: Boolean,
    onUnlockTax: () -> Unit,
    onEditLinkedTransaction: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    if (!isTaxUnlocked) {
        TaxVaultLockGate(
            viewModel = viewModel,
            isBiometricEnabled = userProfile?.isBiometricEnabled == true,
            onUnlocked = onUnlockTax,
            modifier = modifier
        )
        return
    }

    LaunchedEffect(Unit) {
        viewModel.ensureCurrentTaxYearExists()
    }

    val taxProfile by viewModel.taxProfile.collectAsStateWithLifecycle()
    val taxYears by viewModel.taxYears.collectAsStateWithLifecycle()
    val selectedTaxYear by viewModel.selectedTaxYear.collectAsStateWithLifecycle()
    val manualEntries by viewModel.taxEntries.collectAsStateWithLifecycle()
    val allTransactions by viewModel.transactions.collectAsStateWithLifecycle()
    val wealthItems by viewModel.taxWealthItems.collectAsStateWithLifecycle()
    val documents by viewModel.taxDocuments.collectAsStateWithLifecycle()
    val customCategories by viewModel.taxCustomCategories.collectAsStateWithLifecycle()

    val currentYearEntity = remember(taxYears, selectedTaxYear) {
        taxYears.find { it.taxYear == selectedTaxYear } ?: TaxYearEntity(
            taxYear = selectedTaxYear,
            label = PakistanTaxYearUtils.formatTaxYearLabel(selectedTaxYear),
            startDate = PakistanTaxYearUtils.startDateForTaxYear(selectedTaxYear),
            endDate = PakistanTaxYearUtils.endDateForTaxYear(selectedTaxYear),
            filingDeadline = PakistanTaxYearUtils.defaultFilingDeadlineForTaxYear(selectedTaxYear)
        )
    }

    val summary = remember(selectedTaxYear, taxProfile, manualEntries, allTransactions, wealthItems, documents) {
        viewModel.buildTaxYearSummary(
            taxYear = selectedTaxYear,
            profile = taxProfile,
            manualEntries = manualEntries,
            allTransactions = allTransactions,
            wealthItems = wealthItems,
            documents = documents
        )
    }

    val mergedItems = remember(selectedTaxYear, manualEntries, allTransactions) {
        viewModel.getMergedTaxItemsForYear(selectedTaxYear, manualEntries, allTransactions)
    }

    val slabs = remember(currentYearEntity.customSlabJson) {
        PakistanTaxYearUtils.parseSlabsJson(currentYearEntity.customSlabJson)
    }

    val decryptedCnic = remember(taxProfile?.cnicEncrypted) {
        taxProfile?.cnicEncrypted?.let { viewModel.decryptTaxSensitive(it) }.orEmpty()
    }
    val decryptedNtn = remember(taxProfile?.ntnEncrypted) {
        taxProfile?.ntnEncrypted?.let { viewModel.decryptTaxSensitive(it) }.orEmpty()
    }
    val maskedCnic = remember(decryptedCnic) { TaxCryptoHelper.maskCnic(decryptedCnic) }
    val maskedNtn = remember(decryptedNtn) { TaxCryptoHelper.maskNtn(decryptedNtn) }

    var activeTab by rememberSaveable { mutableStateOf(TaxSubTab.SUMMARY) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showCreateYearDialog by remember { mutableStateOf(false) }
    var showDeadlineDialog by remember { mutableStateOf(false) }
    var showSlabDialog by remember { mutableStateOf(false) }
    var entryDialogGroup by remember { mutableStateOf<String?>(null) }
    var entryToEdit by remember { mutableStateOf<TaxDisplayItem?>(null) }
    var entryToDelete by remember { mutableStateOf<TaxDisplayItem?>(null) }
    var wealthDialogPeriod by remember { mutableStateOf<String?>(null) }
    var wealthToEdit by remember { mutableStateOf<TaxWealthItemEntity?>(null) }
    var wealthToDelete by remember { mutableStateOf<TaxWealthItemEntity?>(null) }
    var showBackupExportDialog by remember { mutableStateOf(false) }
    var pendingRestoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingExportBytes by remember { mutableStateOf<ByteArray?>(null) }

    val createBackupDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        viewModel.setExternalPickerActive(false)
        val bytes = pendingExportBytes
        pendingExportBytes = null
        if (uri != null && bytes != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                Toast.makeText(context, "Encrypted .fptax backup saved", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Failed to write backup file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val openRestoreDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        viewModel.setExternalPickerActive(false)
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    pendingRestoreBytes = bytes
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Could not read backup file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("tax_main_screen")
    ) {
        // 1. Tax Year Header Card
        TaxYearHeaderCard(
            taxYears = taxYears,
            currentYearEntity = currentYearEntity,
            onSelectYear = { viewModel.selectTaxYear(it) },
            onAddYear = { showCreateYearDialog = true },
            onEditDeadline = { showDeadlineDialog = true },
            onToggleLock = {
                val nextLock = !currentYearEntity.isLocked
                viewModel.toggleTaxYearLock(
                    taxYear = currentYearEntity.taxYear,
                    lock = nextLock,
                    onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                    onSuccess = {
                        Toast.makeText(
                            context,
                            if (nextLock) "Tax Year ${currentYearEntity.taxYear} locked & archived" else "Tax Year ${currentYearEntity.taxYear} unlocked",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        )

        if (currentYearEntity.isLocked) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("tax_year_locked_banner"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Archived / Read-Only — Unlock year above to add or edit records.",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 2. Scrollable Tax Sub-Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TaxSubTab.entries.forEach { tab ->
                FilterChip(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    label = { Text(tab.title, fontSize = 12.sp) },
                    modifier = Modifier.testTag(tab.tag)
                )
            }
        }

        // 3. Active Sub-Tab Body
        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                TaxSubTab.SUMMARY -> {
                    val incList = mergedItems.filter { it.entryGroup == TAX_GROUP_INCOME }
                    val whtList = mergedItems.filter { it.entryGroup == TAX_GROUP_WITHHOLDING }
                    val dirList = mergedItems.filter { it.entryGroup == TAX_GROUP_DIRECT_TAX }
                    val dedList = mergedItems.filter { it.entryGroup == TAX_GROUP_DEDUCTION }
                    val expList = mergedItems.filter { it.entryGroup == TAX_GROUP_EXPENSE }
                    val yearWealth = wealthItems.filter { it.taxYear == selectedTaxYear }
                    val yearDocs = documents.filter { it.taxYear == selectedTaxYear }

                    TaxSummaryTab(
                        taxYearEntity = currentYearEntity,
                        summary = summary,
                        slabs = slabs,
                        onEditSlabs = { showSlabDialog = true },
                        onExportPdf = {
                            val pdf = TaxExportUtil.writeTaxPdfFile(
                                context, currentYearEntity, taxProfile, maskedCnic, maskedNtn,
                                summary, incList, whtList, dirList, dedList, expList, yearWealth
                            )
                            TaxExportUtil.shareFile(context, pdf, "application/pdf", "Pakistan Tax Year ${currentYearEntity.taxYear} PDF", "Share Tax PDF")
                        },
                        onExportCsv = {
                            val csv = TaxExportUtil.generateTaxCsvContent(
                                currentYearEntity, taxProfile, maskedCnic, maskedNtn,
                                summary, incList, whtList, dirList, dedList, expList, yearWealth
                            )
                            val csvFile = TaxExportUtil.writeTaxCsvFile(context, currentYearEntity, csv)
                            TaxExportUtil.shareFile(context, csvFile, "text/csv", "Pakistan Tax Year ${currentYearEntity.taxYear} Excel CSV", "Share Tax CSV")
                        },
                        onExportDocsZip = {
                            val zip = TaxExportUtil.writeDocumentsZipFile(context, currentYearEntity.taxYear, yearDocs)
                            TaxExportUtil.shareFile(context, zip, "application/zip", "Pakistan Tax Year ${currentYearEntity.taxYear} Documents", "Share Documents ZIP")
                        },
                        onSharePackage = {
                            val pdf = TaxExportUtil.writeTaxPdfFile(
                                context, currentYearEntity, taxProfile, maskedCnic, maskedNtn,
                                summary, incList, whtList, dirList, dedList, expList, yearWealth
                            )
                            val csv = TaxExportUtil.generateTaxCsvContent(
                                currentYearEntity, taxProfile, maskedCnic, maskedNtn,
                                summary, incList, whtList, dirList, dedList, expList, yearWealth
                            )
                            val csvFile = TaxExportUtil.writeTaxCsvFile(context, currentYearEntity, csv)
                            val zip = TaxExportUtil.writeDocumentsZipFile(context, currentYearEntity.taxYear, yearDocs)
                            TaxExportUtil.shareTaxBundle(context, listOf(pdf, csvFile, zip), currentYearEntity.taxYear)
                        },
                        onCreateEncryptedBackup = { showBackupExportDialog = true },
                        onRestoreEncryptedBackup = {
                            viewModel.setExternalPickerActive(true)
                            openRestoreDocLauncher.launch(arrayOf("*/*"))
                        }
                    )
                }

                TaxSubTab.PROFILE -> TaxProfileTabContent(
                    profile = taxProfile,
                    maskedCnic = maskedCnic,
                    maskedNtn = maskedNtn,
                    decryptedCnic = decryptedCnic,
                    decryptedNtn = decryptedNtn,
                    onEditProfile = { showProfileDialog = true }
                )

                TaxSubTab.INCOME -> TaxEntriesListTab(
                    headerTitle = "Income (FBR Heads)",
                    totalAmount = summary.totalIncome,
                    items = mergedItems.filter { it.entryGroup == TAX_GROUP_INCOME },
                    isLocked = currentYearEntity.isLocked,
                    primaryButtonLabel = "+ Add Income",
                    primaryButtonTag = "tax_add_income_button",
                    onPrimaryAdd = { entryToEdit = null; entryDialogGroup = TAX_GROUP_INCOME },
                    onEditItem = { item ->
                        if (item.isLinkedTransaction) {
                            allTransactions.find { it.id == item.linkedTransactionId }?.let(onEditLinkedTransaction)
                        } else {
                            entryToEdit = item; entryDialogGroup = item.entryGroup
                        }
                    },
                    onDeleteItem = { item -> entryToDelete = item }
                )

                TaxSubTab.TAXES -> TaxEntriesListTab(
                    headerTitle = "Withholding (${DateUtils.formatCurrency(summary.totalWithholdingTax)}) & Direct Tax (${DateUtils.formatCurrency(summary.totalDirectTaxPaid)})",
                    totalAmount = summary.totalWithholdingTax + summary.totalDirectTaxPaid,
                    items = mergedItems.filter { it.entryGroup == TAX_GROUP_WITHHOLDING || it.entryGroup == TAX_GROUP_DIRECT_TAX },
                    isLocked = currentYearEntity.isLocked,
                    primaryButtonLabel = "+ Withholding",
                    primaryButtonTag = "tax_add_wht_button",
                    onPrimaryAdd = { entryToEdit = null; entryDialogGroup = TAX_GROUP_WITHHOLDING },
                    secondaryButtonLabel = "+ Direct / CPR",
                    secondaryButtonTag = "tax_add_direct_button",
                    onSecondaryAdd = { entryToEdit = null; entryDialogGroup = TAX_GROUP_DIRECT_TAX },
                    onEditItem = { item ->
                        if (item.isLinkedTransaction) {
                            allTransactions.find { it.id == item.linkedTransactionId }?.let(onEditLinkedTransaction)
                        } else {
                            entryToEdit = item; entryDialogGroup = item.entryGroup
                        }
                    },
                    onDeleteItem = { item -> entryToDelete = item }
                )

                TaxSubTab.DEDUCTIONS -> TaxEntriesListTab(
                    headerTitle = "Deductions (${DateUtils.formatCurrency(summary.totalDeductions)}) & Expenses (${DateUtils.formatCurrency(summary.totalTaxExpenses)})",
                    totalAmount = summary.totalDeductions + summary.totalTaxExpenses,
                    items = mergedItems.filter { it.entryGroup == TAX_GROUP_DEDUCTION || it.entryGroup == TAX_GROUP_EXPENSE },
                    isLocked = currentYearEntity.isLocked,
                    primaryButtonLabel = "+ Deduction",
                    primaryButtonTag = "tax_add_deduction_button",
                    onPrimaryAdd = { entryToEdit = null; entryDialogGroup = TAX_GROUP_DEDUCTION },
                    secondaryButtonLabel = "+ Tax Expense",
                    secondaryButtonTag = "tax_add_expense_button",
                    onSecondaryAdd = { entryToEdit = null; entryDialogGroup = TAX_GROUP_EXPENSE },
                    onEditItem = { item ->
                        if (item.isLinkedTransaction) {
                            allTransactions.find { it.id == item.linkedTransactionId }?.let(onEditLinkedTransaction)
                        } else {
                            entryToEdit = item; entryDialogGroup = item.entryGroup
                        }
                    },
                    onDeleteItem = { item -> entryToDelete = item }
                )

                TaxSubTab.WEALTH -> TaxWealthTab(
                    taxYear = selectedTaxYear,
                    isLocked = currentYearEntity.isLocked,
                    summary = summary,
                    wealthItems = wealthItems,
                    onAutoFillAccounts = { period ->
                        viewModel.autoFillBankBalancesForWealth(
                            taxYear = selectedTaxYear,
                            periodType = period,
                            onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                            onSuccess = { count ->
                                Toast.makeText(context, "Synced $count account balance(s) into $period", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    onAutoPullUdhaar = { period ->
                        viewModel.autoPullUdhaarForWealth(
                            taxYear = selectedTaxYear,
                            periodType = period,
                            onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                            onSuccess = { count ->
                                Toast.makeText(context, "Pulled $count active Udhaar record(s) into $period", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    onAddWealthItem = { period ->
                        wealthToEdit = null
                        wealthDialogPeriod = period
                    },
                    onEditWealthItem = { item ->
                        wealthToEdit = item
                        wealthDialogPeriod = item.periodType
                    },
                    onDeleteWealthItem = { item -> wealthToDelete = item }
                )

                TaxSubTab.DOCUMENTS -> TaxDocumentsTab(
                    viewModel = viewModel,
                    taxYear = selectedTaxYear,
                    isLocked = currentYearEntity.isLocked,
                    documents = documents
                )
            }
        }
    }

    // --- DIALOGS ---
    if (showProfileDialog) {
        TaxProfileDialog(
            existingProfile = taxProfile,
            initialDecryptedCnic = decryptedCnic,
            initialDecryptedNtn = decryptedNtn,
            onDismiss = { showProfileDialog = false },
            onSave = { name, cnic, ntn, status, occ, note ->
                viewModel.saveTaxProfile(
                    fullName = name,
                    cnicPlain = cnic,
                    ntnPlain = ntn,
                    filerStatus = status,
                    occupationType = occ,
                    note = note,
                    onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                    onSuccess = {
                        showProfileDialog = false
                        Toast.makeText(context, "Tax profile encrypted & saved", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    entryDialogGroup?.let { group ->
        TaxEntryDialog(
            entryGroup = group,
            taxYear = selectedTaxYear,
            customCategories = customCategories,
            documents = documents.filter { it.taxYear == selectedTaxYear },
            entryToEdit = entryToEdit,
            onDismiss = { entryDialogGroup = null; entryToEdit = null },
            onSave = { id, cat, amt, date, src, ref, proof, docId, notes ->
                viewModel.saveTaxEntry(
                    id = id,
                    taxYear = selectedTaxYear,
                    entryGroup = group,
                    category = cat,
                    amount = amt,
                    date = date,
                    sourceOrPayer = src,
                    referenceNumber = ref,
                    proofNote = proof,
                    documentId = docId,
                    notes = notes,
                    onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                    onSuccess = {
                        entryDialogGroup = null
                        entryToEdit = null
                    }
                )
            }
        )
    }

    entryToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Tax Entry?") },
            text = { Text("Delete ${item.category} (${DateUtils.formatCurrency(item.amount)})?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTaxEntry(
                            entryId = item.id,
                            onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                            onSuccess = { entryToDelete = null }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
            }
        )
    }

    wealthDialogPeriod?.let { period ->
        TaxWealthItemDialog(
            initialPeriod = period,
            itemToEdit = wealthToEdit,
            onDismiss = { wealthDialogPeriod = null; wealthToEdit = null },
            onSave = { id, per, cat, title, amt, isLiab, notes ->
                viewModel.saveWealthItem(
                    id = id,
                    taxYear = selectedTaxYear,
                    periodType = per,
                    assetCategory = cat,
                    title = title,
                    amount = amt,
                    isLiability = isLiab,
                    notes = notes,
                    onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                    onSuccess = { wealthDialogPeriod = null; wealthToEdit = null }
                )
            }
        )
    }

    wealthToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { wealthToDelete = null },
            title = { Text("Remove Wealth Item?") },
            text = { Text("Delete '${item.title}' (${DateUtils.formatCurrency(item.amount)})?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteWealthItem(
                            itemId = item.id,
                            onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                            onSuccess = { wealthToDelete = null }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { wealthToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showCreateYearDialog) {
        val nextSuggested = (taxYears.maxOfOrNull { it.taxYear } ?: PakistanTaxYearUtils.currentTaxYear()) + 1
        TaxYearCreateDialog(
            suggestedYear = nextSuggested,
            onDismiss = { showCreateYearDialog = false },
            onCreate = { yr, deadline, remDays ->
                viewModel.createTaxYear(
                    taxYear = yr,
                    filingDeadline = deadline,
                    reminderDaysBefore = remDays,
                    onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                    onSuccess = { showCreateYearDialog = false }
                )
            }
        )
    }

    if (showDeadlineDialog) {
        TaxDeadlineDialog(
            taxYear = currentYearEntity,
            onDismiss = { showDeadlineDialog = false },
            onSave = { deadline, remDays ->
                if (remDays > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                viewModel.updateTaxYearDeadline(
                    taxYear = currentYearEntity.taxYear,
                    filingDeadline = deadline,
                    reminderDaysBefore = remDays,
                    onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                    onSuccess = {
                        showDeadlineDialog = false
                        Toast.makeText(context, "Filing deadline & reminder updated", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    if (showSlabDialog) {
        TaxSlabEditorDialog(
            initialSlabs = slabs,
            onDismiss = { showSlabDialog = false },
            onSave = { updated ->
                viewModel.updateCustomTaxSlabs(
                    taxYear = currentYearEntity.taxYear,
                    slabs = updated,
                    onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                    onSuccess = { showSlabDialog = false }
                )
            }
        )
    }

    if (showBackupExportDialog) {
        TaxBackupPassphraseDialog(
            isRestore = false,
            selectedTaxYear = selectedTaxYear,
            onDismiss = { showBackupExportDialog = false },
            onConfirm = { pass, scopeYear ->
                viewModel.createTaxBackupBytes(
                    passphrase = pass,
                    scopeTaxYear = scopeYear,
                    onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                    onSuccess = { bytes ->
                        showBackupExportDialog = false
                        pendingExportBytes = bytes
                        val fname = if (scopeYear == null) {
                            "FinancePro_TaxBackup_AllYears.fptax"
                        } else {
                            "FinancePro_TaxBackup_TY$scopeYear.fptax"
                        }
                        viewModel.setExternalPickerActive(true)
                        createBackupDocLauncher.launch(fname)
                    }
                )
            }
        )
    }

    pendingRestoreBytes?.let { encBytes ->
        TaxBackupPassphraseDialog(
            isRestore = true,
            selectedTaxYear = selectedTaxYear,
            onDismiss = { pendingRestoreBytes = null },
            onConfirm = { pass, _ ->
                viewModel.restoreTaxBackupBytes(
                    encryptedBytes = encBytes,
                    passphrase = pass,
                    onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() },
                    onSuccess = { yearsCount ->
                        pendingRestoreBytes = null
                        Toast.makeText(context, "Restored $yearsCount tax year(s) from encrypted backup", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }
}

@Composable
private fun TaxVaultLockGate(
    viewModel: FinanceViewModel,
    isBiometricEnabled: Boolean,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val canBiometric = remember(context) { BiometricSecurityManager.canAuthenticate(context) }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(canBiometric, isBiometricEnabled) {
        if (canBiometric && isBiometricEnabled && activity != null) {
            BiometricSecurityManager.authenticate(
                activity = activity,
                title = "Unlock Pakistan Tax Vault",
                subtitle = "Verify identity to access CNIC, NTN, tax & wealth records",
                onSucceeded = onUnlocked,
                onError = {}
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("tax_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AssuredWorkload,
                    contentDescription = "Protected Tax Vault",
                    tint = EmeraldPrimaryLight,
                    modifier = Modifier.size(48.dp)
                )
                Text("Tax Vault Locked", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "Enter your account password or use fingerprint to view encrypted CNIC/NTN, tax filings, wealth statements & documents.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }

                if (canBiometric && activity != null) {
                    OutlinedButton(
                        onClick = {
                            BiometricSecurityManager.authenticate(
                                activity = activity,
                                title = "Unlock Tax Vault",
                                subtitle = "Touch fingerprint sensor",
                                onSucceeded = onUnlocked,
                                onError = { errorMsg = it }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("tax_biometric_unlock_button")
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock with Fingerprint")
                    }
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMsg = null },
                    label = { Text("Account Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tax_unlock_password_input")
                )

                Button(
                    onClick = {
                        viewModel.verifyCurrentUserPassword(password) { ok ->
                            if (ok) {
                                onUnlocked()
                            } else {
                                errorMsg = "Incorrect password. Please try again."
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("tax_unlock_button")
                ) {
                    Text("Unlock Tax Section")
                }
            }
        }
    }
}

@Composable
private fun TaxYearHeaderCard(
    taxYears: List<TaxYearEntity>,
    currentYearEntity: TaxYearEntity,
    onSelectYear: (Int) -> Unit,
    onAddYear: () -> Unit,
    onEditDeadline: () -> Unit,
    onToggleLock: () -> Unit
) {
    val daysRemaining = remember(currentYearEntity.taxYear) {
        PakistanTaxYearUtils.daysRemainingInTaxYear(currentYearEntity.taxYear)
    }
    val daysToDeadline = remember(currentYearEntity.filingDeadline) {
        PakistanTaxYearUtils.daysUntilDeadline(currentYearEntity.filingDeadline)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val distinctYears = (taxYears.map { it.taxYear } + currentYearEntity.taxYear).distinct().sortedDescending()
                distinctYears.forEach { yr ->
                    val yrLocked = taxYears.find { it.taxYear == yr }?.isLocked == true
                    FilterChip(
                        selected = currentYearEntity.taxYear == yr,
                        onClick = { onSelectYear(yr) },
                        label = { Text("TY $yr${if (yrLocked) " 🔒" else ""}", fontSize = 11.sp) }
                    )
                }
                OutlinedButton(
                    onClick = onAddYear,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("tax_add_year_button")
                ) {
                    Text("+ New Year", fontSize = 11.sp)
                }
            }

            Text(
                text = currentYearEntity.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("tax_year_label")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$daysRemaining day(s) remaining in tax year",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val deadlineText = currentYearEntity.filingDeadline.ifBlank { "Not set" }
                    val countdownSuffix = when {
                        daysToDeadline == null -> ""
                        daysToDeadline > 0 -> " (${daysToDeadline}d left)"
                        daysToDeadline == 0L -> " (Due today)"
                        else -> " (${kotlin.math.abs(daysToDeadline)}d past)"
                    }
                    Text(
                        text = "Filing Deadline: $deadlineText$countdownSuffix",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("tax_deadline_text")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!currentYearEntity.isLocked) {
                        IconButton(
                            onClick = onEditDeadline,
                            modifier = Modifier.testTag("tax_edit_deadline_button")
                        ) {
                            Icon(Icons.Default.EditCalendar, contentDescription = "Edit Deadline", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    OutlinedButton(
                        onClick = onToggleLock,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("tax_lock_year_button")
                    ) {
                        Icon(
                            imageVector = if (currentYearEntity.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (currentYearEntity.isLocked) "Unlock" else "Close / Lock", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaxProfileTabContent(
    profile: TaxProfileEntity?,
    maskedCnic: String,
    maskedNtn: String,
    decryptedCnic: String,
    decryptedNtn: String,
    onEditProfile: () -> Unit
) {
    val context = LocalContext.current
    fun copySensitive(label: String, plain: String) {
        if (plain.isBlank()) {
            Toast.makeText(context, "$label is empty", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, plain))
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Taxpayer Profile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = onEditProfile,
                            modifier = Modifier.testTag("tax_edit_profile_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (profile == null) "Set Up Profile" else "Edit", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp)

                    ProfileFieldRow("Full Legal Name", profile?.fullName?.ifBlank { "Not set" } ?: "Not set")
                    ProfileFieldRow("Filer Status", profile?.filerStatus ?: "Not set")
                    ProfileFieldRow("Occupation Type", profile?.occupationType ?: "Not set")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("CNIC (AES-256 Encrypted)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(maskedCnic, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("tax_masked_cnic_text"))
                        }
                        OutlinedButton(
                            onClick = { copySensitive("CNIC", decryptedCnic) },
                            modifier = Modifier.testTag("tax_copy_cnic_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy CNIC", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", fontSize = 11.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("NTN (AES-256 Encrypted)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(maskedNtn, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("tax_masked_ntn_text"))
                        }
                        OutlinedButton(
                            onClick = { copySensitive("NTN", decryptedNtn) },
                            modifier = Modifier.testTag("tax_copy_ntn_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy NTN", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", fontSize = 11.sp)
                        }
                    }

                    if (!profile?.note.isNullOrBlank()) {
                        ProfileFieldRow("Notes", profile!!.note)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileFieldRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TaxEntriesListTab(
    headerTitle: String,
    totalAmount: Double,
    items: List<TaxDisplayItem>,
    isLocked: Boolean,
    primaryButtonLabel: String,
    primaryButtonTag: String,
    onPrimaryAdd: () -> Unit,
    secondaryButtonLabel: String? = null,
    secondaryButtonTag: String? = null,
    onSecondaryAdd: (() -> Unit)? = null,
    onEditItem: (TaxDisplayItem) -> Unit,
    onDeleteItem: (TaxDisplayItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(headerTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = DateUtils.formatCurrency(totalAmount),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!isLocked) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onPrimaryAdd,
                                modifier = Modifier.testTag(primaryButtonTag)
                            ) {
                                Text(primaryButtonLabel, fontSize = 12.sp)
                            }
                            if (secondaryButtonLabel != null && onSecondaryAdd != null) {
                                FilledTonalButton(
                                    onClick = onSecondaryAdd,
                                    modifier = Modifier.let {
                                        if (secondaryButtonTag != null) it.testTag(secondaryButtonTag) else it
                                    }
                                ) {
                                    Text(secondaryButtonLabel, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (items.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "No entries yet. Tap above to add a record, or tag transactions from Daily Statement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(items, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(item.category, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                if (item.isLinkedTransaction) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = EmeraldPrimaryLight.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = "LINKED",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = EmeraldPrimaryLight,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = listOf(item.date, item.sourceOrPayer, item.referenceNumber)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (item.proofNote.isNotBlank() || item.notes.isNotBlank()) {
                                Text(
                                    text = listOf(item.proofNote, item.notes).filter { it.isNotBlank() }.joinToString(" — "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = DateUtils.formatCurrency(item.amount),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (item.entryGroup == TAX_GROUP_EXPENSE) FinanceExpenseRed else FinanceIncomeGreen
                            )
                            if (!isLocked) {
                                Row {
                                    IconButton(
                                        onClick = { onEditItem(item) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit tax entry", modifier = Modifier.size(16.dp))
                                    }
                                    if (!item.isLinkedTransaction) {
                                        IconButton(
                                            onClick = { onDeleteItem(item) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete tax entry", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
