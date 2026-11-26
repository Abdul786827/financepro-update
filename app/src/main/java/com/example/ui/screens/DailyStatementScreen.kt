package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.data.model.AccountEntity
import com.example.data.model.PakistanTaxConstants
import com.example.data.model.TAX_GROUP_DEDUCTION
import com.example.data.model.TAX_GROUP_EXPENSE
import com.example.data.model.TAX_GROUP_INCOME
import com.example.data.model.TAX_GROUP_WITHHOLDING
import com.example.data.model.TransactionEntity
import com.example.ui.components.DailyStatementTableView
import com.example.ui.components.accountTypeIcon
import com.example.ui.components.accountTypeLabel
import com.example.ui.components.resolveAccountIconByName
import com.example.ui.theme.*
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.DateUtils
import com.example.util.PakistanTaxYearUtils
import com.example.util.StatementExportUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyStatementScreen(
    viewModel: FinanceViewModel,
    onAddTransaction: () -> Unit,
    onEditTransaction: (TransactionEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val filteredTransactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedAccount by viewModel.selectedAccountFilter.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedTypeFilter.collectAsStateWithLifecycle()

    var showDeleteConfirm by remember { mutableStateOf<TransactionEntity?>(null) }
    var viewMode by remember { mutableStateOf("LIST") } // "LIST" or "TABLE"
    var isBulkTaxMode by remember { mutableStateOf(false) }
    var selectedTxIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBulkTaxDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search & Export Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("Search transactions, transfers...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("statement_search_input"),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )

            // PDF Export button
            FilledTonalIconButton(
                onClick = {
                    StatementExportUtil.exportAndShareDailyPdf(
                        context = context,
                        title = "Daily Statement (${selectedAccount ?: "All Accounts"})",
                        transactions = filteredTransactions,
                        accounts = accounts
                    )
                },
                modifier = Modifier.testTag("export_pdf_button")
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = MaterialTheme.colorScheme.primary)
            }

            // CSV Export button
            FilledTonalIconButton(
                onClick = {
                    val csv = StatementExportUtil.generateDailyStatementCsv(filteredTransactions)
                    StatementExportUtil.exportAndShareCsv(
                        context = context,
                        filename = "Daily_Statement_${System.currentTimeMillis()}.csv",
                        csvContent = csv
                    )
                },
                modifier = Modifier.testTag("export_csv_button")
            ) {
                Icon(Icons.Default.TableChart, contentDescription = "Export CSV", tint = MaterialTheme.colorScheme.secondary)
            }
        }

        // Account Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = selectedAccount == null,
                    onClick = { viewModel.selectedAccountFilter.value = null },
                    label = { Text("All Accounts") }
                )
            }
            items(accounts) { acc ->
                FilterChip(
                    selected = selectedAccount.equals(acc.name, ignoreCase = true),
                    onClick = {
                        viewModel.selectedAccountFilter.value =
                            if (selectedAccount.equals(acc.name, ignoreCase = true)) null else acc.name
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = accountTypeIcon(acc),
                            contentDescription = accountTypeLabel(acc.accountType),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(acc.name) }
                )
            }
        }

        // Transaction Type Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val typeList = listOf(
                null to "All Types",
                "INCOME" to "Income",
                "EXPENSE" to "Expenses",
                "TRANSFER" to "Transfers",
                "PETROL" to "Petrol",
                "EXTRA_EXPENSE" to "Extra Expenses",
                "UDHAAR" to "Udhaar",
                "SAVINGS" to "Savings"
            )
            items(typeList) { (typeKey, typeLabel) ->
                FilterChip(
                    selected = selectedType == typeKey,
                    onClick = { viewModel.selectedTypeFilter.value = typeKey },
                    label = { Text(typeLabel) }
                )
            }
        }

        // Summary Bar with Income, Expense & Transfers
        val totalIn = filteredTransactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalOut = filteredTransactions.filter { it.type in listOf("EXPENSE", "PETROL", "EXTRA_EXPENSE") }.sumOf { it.amount }
        val totalTransfers = filteredTransactions.filter { it.type == "TRANSFER" }.sumOf { it.amount }
        val totalUdhaar = filteredTransactions.filter { it.type == "UDHAAR" }.sumOf { it.amount }
        val totalSavingsTx = filteredTransactions.filter { it.type == "SAVINGS" }.sumOf { it.amount }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredTransactions.size} Records Found",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // View Mode & Bulk Tax Switcher
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        if (userProfile?.isTaxpayerEnabled == true) {
                            FilterChip(
                                selected = isBulkTaxMode,
                                onClick = {
                                    isBulkTaxMode = !isBulkTaxMode
                                    if (!isBulkTaxMode) selectedTxIds = emptySet()
                                    else viewMode = "LIST"
                                },
                                label = { Text("Tax Mark", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.AssuredWorkload, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                modifier = Modifier.testTag("statement_bulk_tax_button")
                            )
                        }
                        FilterChip(
                            selected = viewMode == "LIST",
                            onClick = { viewMode = "LIST" },
                            label = { Text("List View", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.ViewList, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                        FilterChip(
                            selected = viewMode == "TABLE",
                            onClick = {
                                viewMode = "TABLE"
                                isBulkTaxMode = false
                            },
                            label = { Text("Excel Sheet", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "In: +${DateUtils.formatCurrency(totalIn)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = FinanceIncomeGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Out: -${DateUtils.formatCurrency(totalOut)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = FinanceExpenseRed,
                        fontWeight = FontWeight.Bold
                    )
                    if (totalTransfers > 0) {
                        Text(
                            text = "Transfer: ${DateUtils.formatCurrency(totalTransfers)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = FinanceTransferBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (totalUdhaar > 0) {
                        Text(
                            text = "Udhaar: ${DateUtils.formatCurrency(totalUdhaar)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF7C3AED),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (totalSavingsTx > 0) {
                        Text(
                            text = "Savings: ${DateUtils.formatCurrency(totalSavingsTx)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = FinanceSavingsTeal,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Bulk Tax Selection Action Bar
        if (isBulkTaxMode && viewMode == "LIST") {
            val taggableIds = filteredTransactions
                .filter { it.type in listOf("INCOME", "EXPENSE", "PETROL", "EXTRA_EXPENSE") }
                .map { it.id }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${selectedTxIds.size} selected",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = {
                                selectedTxIds = if (selectedTxIds.size == taggableIds.size) emptySet() else taggableIds.toSet()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(if (selectedTxIds.size == taggableIds.size && taggableIds.isNotEmpty()) "Clear" else "Select All", fontSize = 11.sp)
                        }
                        if (selectedTxIds.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.bulkTagTransactionsForTax(
                                        ids = selectedTxIds.toList(),
                                        isTaxRelated = false,
                                        taxSectionType = "",
                                        taxCategory = "",
                                        taxYearId = ""
                                    ) {
                                        Toast.makeText(context, "Removed Tax tag from ${selectedTxIds.size} item(s)", Toast.LENGTH_SHORT).show()
                                        selectedTxIds = emptySet()
                                        isBulkTaxMode = false
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("bulk_tax_untag_button")
                            ) {
                                Text("Untag", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { showBulkTaxDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("bulk_tax_apply_button")
                            ) {
                                Text("Tag Tax", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Content Area: List View or Excel Table View
        if (viewMode == "TABLE") {
            DailyStatementTableView(
                transactions = filteredTransactions,
                onEdit = onEditTransaction,
                onDelete = { showDeleteConfirm = it },
                modifier = Modifier.weight(1f)
            )
        } else {
            // "Records Found" Card List View
            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No records match your criteria",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTransactions, key = { it.id }) { tx ->
                        val canSelectForTax = tx.type in listOf("INCOME", "EXPENSE", "PETROL", "EXTRA_EXPENSE")
                        StatementRecordCard(
                            tx = tx,
                            accounts = accounts,
                            isBulkSelectMode = isBulkTaxMode && canSelectForTax,
                            isSelected = tx.id in selectedTxIds,
                            onToggleSelect = {
                                selectedTxIds = if (tx.id in selectedTxIds) {
                                    selectedTxIds - tx.id
                                } else {
                                    selectedTxIds + tx.id
                                }
                            },
                            onEdit = { onEditTransaction(tx) },
                            onDelete = { showDeleteConfirm = tx }
                        )
                    }
                }
            }
        }
    }

    // Bulk Tax Tagging Modal
    if (showBulkTaxDialog && selectedTxIds.isNotEmpty()) {
        val currentYear = PakistanTaxYearUtils.currentTaxYear()
        val yearOptions = listOf(currentYear - 1, currentYear, currentYear + 1)
        var bulkSection by remember { mutableStateOf(TAX_GROUP_EXPENSE) }
        var bulkCategory by remember { mutableStateOf(PakistanTaxConstants.DEFAULT_TAX_EXPENSE_CATEGORIES.first()) }
        var bulkYear by remember { mutableStateOf(currentYear) }

        AlertDialog(
            onDismissRequest = { showBulkTaxDialog = false },
            title = { Text("Tag ${selectedTxIds.size} Record(s) for Tax", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tax Section", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            TAX_GROUP_INCOME to "Income",
                            TAX_GROUP_EXPENSE to "Expense",
                            TAX_GROUP_DEDUCTION to "Deduction",
                            TAX_GROUP_WITHHOLDING to "Withholding"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = bulkSection == key,
                                onClick = {
                                    bulkSection = key
                                    bulkCategory = PakistanTaxConstants.defaultCategoriesForGroup(key).firstOrNull().orEmpty()
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = bulkCategory,
                        onValueChange = { bulkCategory = it },
                        label = { Text("Tax Category") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bulk_tax_category_input")
                    )

                    Text("Tax Year", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        yearOptions.forEach { yr ->
                            FilterChip(
                                selected = bulkYear == yr,
                                onClick = { bulkYear = yr },
                                label = { Text("TY $yr") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.bulkTagTransactionsForTax(
                            ids = selectedTxIds.toList(),
                            isTaxRelated = true,
                            taxSectionType = bulkSection,
                            taxCategory = bulkCategory.trim().ifBlank { "Other" },
                            taxYearId = bulkYear.toString()
                        ) {
                            Toast.makeText(context, "Tagged ${selectedTxIds.size} record(s) for TY $bulkYear", Toast.LENGTH_SHORT).show()
                            showBulkTaxDialog = false
                            selectedTxIds = emptySet()
                            isBulkTaxMode = false
                        }
                    },
                    modifier = Modifier.testTag("bulk_tax_confirm_button")
                ) {
                    Text("Apply Tag")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkTaxDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Delete confirmation dialog
    showDeleteConfirm?.let { tx ->
        val recordTitle = if (tx.type == "TRANSFER") "Transfer: ${tx.fromAccount} -> ${tx.toAccount}" else tx.description.ifBlank { tx.category }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Statement Record?") },
            text = {
                Text("Are you sure you want to delete Sr# ${tx.serialNumber} ($recordTitle - ${DateUtils.formatCurrency(tx.amount)})? Account balances will automatically recalculate.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransaction(tx) { err ->
                            android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
                        }
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatementRecordCard(
    tx: TransactionEntity,
    accounts: List<AccountEntity>,
    isBulkSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isTransfer = tx.type == "TRANSFER"
    val isUdhaar = tx.type == "UDHAAR"
    val isSavings = tx.type == "SAVINGS"
    val isUdhaarIncoming = isUdhaar && (tx.description.contains("Taken", ignoreCase = true) || tx.description.contains("Repayment Received", ignoreCase = true))
    val isSavingsIncoming = isSavings && tx.description.startsWith("Withdrawn from", ignoreCase = true)
    val isIncome = tx.type == "INCOME" || isUdhaarIncoming || isSavingsIncoming

    val icon = when (tx.type) {
        "TRANSFER" -> Icons.Default.SwapHoriz
        "INCOME" -> Icons.Default.ArrowDownward
        "PETROL" -> Icons.Default.LocalGasStation
        "EXTRA_EXPENSE" -> Icons.Default.ReceiptLong
        "UDHAAR" -> Icons.Default.Handshake
        "SAVINGS" -> Icons.Default.Savings
        else -> Icons.Default.ArrowUpward
    }

    val iconTint = when (tx.type) {
        "TRANSFER" -> FinanceTransferBlue
        "INCOME" -> FinanceIncomeGreen
        "PETROL" -> FinancePetrolOrange
        "EXTRA_EXPENSE" -> FinanceExtraPurple
        "UDHAAR" -> Color(0xFF7C3AED)
        "SAVINGS" -> FinanceSavingsTeal
        else -> FinanceExpenseRed
    }

    val iconBg = iconTint.copy(alpha = 0.15f)

    val cardBg = when (tx.type) {
        "TRANSFER" -> FinanceTransferBg.copy(alpha = 0.35f)
        "INCOME" -> FinanceIncomeBg.copy(alpha = 0.25f)
        "PETROL" -> FinancePetrolBg.copy(alpha = 0.25f)
        "EXTRA_EXPENSE" -> FinanceExtraBg.copy(alpha = 0.25f)
        "UDHAAR" -> Color(0xFF7C3AED).copy(alpha = 0.08f)
        "SAVINGS" -> FinanceSavingsTeal.copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isBulkSelectMode) onToggleSelect() else onEdit()
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isBulkSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.testTag("select_tx_tax_${tx.id}")
                )
            }

            // Icon Badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = tx.type,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Main Info
            Column(modifier = Modifier.weight(1f)) {
                val title = if (isTransfer) {
                    "Transfer: ${tx.fromAccount} -> ${tx.toAccount}"
                } else {
                    tx.description.ifBlank { tx.category }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (tx.isTaxRelated) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = EmeraldPrimaryLight.copy(alpha = 0.16f),
                            modifier = Modifier.testTag("tx_tax_badge_${tx.id}")
                        ) {
                            Text(
                                text = "TAX",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = EmeraldPrimaryLight,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                val dayText = tx.dayOfWeek.ifBlank { DateUtils.getDayOfWeek(tx.date) }
                val dateWithDay = DateUtils.formatReadableDateWithDay(tx.date, dayText)
                val time12 = DateUtils.format12HourTime(tx.time)
                val matchedAccountIcon = resolveAccountIconByName(tx.fromAccount, accounts)
                    ?: resolveAccountIconByName(tx.toAccount, accounts)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (matchedAccountIcon != null) {
                        Icon(
                            imageVector = matchedAccountIcon,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = "$dateWithDay • $time12 • ${tx.fromAccount} -> ${tx.toAccount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (tx.comments.isNotBlank() && tx.comments != title) {
                    Text(
                        text = tx.comments,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Amount, Balance and Action Buttons
            Column(horizontalAlignment = Alignment.End) {
                val amtColor = when {
                    isTransfer -> FinanceTransferBlue
                    isIncome -> FinanceIncomeGreen
                    else -> FinanceExpenseRed
                }
                val prefix = if (isTransfer) "" else if (isIncome) "+" else "-"

                Text(
                    text = "$prefix${DateUtils.formatCurrency(tx.amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = amtColor
                )

                Text(
                    text = "Bal: ${DateUtils.formatCurrency(tx.totalAccountBalance)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("edit_tx_${tx.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit record",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("delete_tx_${tx.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete record",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
