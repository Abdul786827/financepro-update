package com.example.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.example.data.model.AccountEntity
import com.example.data.model.SavingsEntryEntity
import com.example.data.model.SavingsPotEntity
import com.example.ui.theme.FinanceExpenseRed
import com.example.ui.theme.FinanceIncomeGreen
import com.example.ui.theme.FinanceSavingsTeal
import com.example.util.DateUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsPotDialog(
    potToEdit: SavingsPotEntity? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, targetAmount: Double?, targetDate: String, onError: (String) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(potToEdit?.name ?: "") }
    var targetAmountText by remember {
        mutableStateOf(
            if (potToEdit?.targetAmount != null && potToEdit.targetAmount > 0.0) {
                potToEdit.targetAmount.toBigDecimal().stripTrailingZeros().toPlainString()
            } else {
                ""
            }
        )
    }
    var targetDate by remember { mutableStateOf(potToEdit?.targetDate ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (potToEdit != null) "Edit Savings Pot" else "Create Savings Pot",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMsg = null
                    },
                    label = { Text("Pot Name * (e.g. Emergency, Hajj, Car)") },
                    leadingIcon = { Icon(Icons.Default.Savings, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("savings_pot_name_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = targetAmountText,
                    onValueChange = {
                        targetAmountText = it
                        errorMsg = null
                    },
                    label = { Text("Target Amount (Optional, Rs.)") },
                    leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("savings_pot_target_input"),
                    singleLine = true
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (targetDate.isNotBlank()) DateUtils.formatReadableDate(targetDate) else "No target date",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Date (Optional)") },
                        leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) },
                        trailingIcon = {
                            Row {
                                if (targetDate.isNotBlank()) {
                                    IconButton(onClick = { targetDate = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear target date")
                                    }
                                }
                                IconButton(onClick = { showDatePicker = true }) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = "Pick target date")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showDatePicker = true }
                    )
                }

                errorMsg?.let { err ->
                    Text(
                        text = err,
                        color = FinanceExpenseRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim()
                    if (cleanName.isBlank()) {
                        errorMsg = "Please enter a pot name."
                        return@Button
                    }
                    val targetVal = if (targetAmountText.isBlank()) {
                        null
                    } else {
                        val parsed = targetAmountText.toDoubleOrNull()
                        if (parsed == null || parsed <= 0.0) {
                            errorMsg = "Target amount must be greater than 0."
                            return@Button
                        }
                        parsed
                    }
                    onSave(cleanName, targetVal, targetDate, { err -> errorMsg = err })
                },
                modifier = Modifier.testTag("savings_pot_save_button")
            ) {
                Text(if (potToEdit != null) "Update" else "Create Pot")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = try {
                if (targetDate.isNotBlank()) {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.parse(targetDate)?.time ?: System.currentTimeMillis()
                } else {
                    System.currentTimeMillis()
                }
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            targetDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }.format(Date(millis))
                        }
                        showDatePicker = false
                    }
                ) { Text("OK", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsEntryDialog(
    pots: List<SavingsPotEntity>,
    accounts: List<AccountEntity>,
    initialType: String = "DEPOSIT",
    initialPotId: Long? = null,
    entryToEdit: SavingsEntryEntity? = null,
    onDismiss: () -> Unit,
    onSave: (
        potId: Long,
        type: String,
        amount: Double,
        accountName: String,
        date: String,
        time: String,
        note: String,
        onError: (String) -> Unit
    ) -> Unit
) {
    var type by remember { mutableStateOf(entryToEdit?.type ?: initialType) }
    var selectedPotId by remember {
        mutableLongStateOf(
            entryToEdit?.potId ?: initialPotId ?: pots.firstOrNull()?.id ?: 0L
        )
    }
    var selectedAccountName by remember {
        mutableStateOf(
            entryToEdit?.accountName ?: accounts.firstOrNull()?.name ?: ""
        )
    }
    var amountText by remember {
        mutableStateOf(
            if (entryToEdit != null) {
                entryToEdit.amount.toBigDecimal().stripTrailingZeros().toPlainString()
            } else {
                ""
            }
        )
    }
    var dateText by remember { mutableStateOf(entryToEdit?.date ?: DateUtils.getCurrentDate()) }
    val dayText by remember(dateText) {
        derivedStateOf { DateUtils.getDayOfWeek(dateText) }
    }
    var timeText by remember { mutableStateOf(entryToEdit?.time ?: DateUtils.getCurrentTime()) }
    var noteText by remember { mutableStateOf(entryToEdit?.note ?: "") }

    var potDropdownExpanded by remember { mutableStateOf(false) }
    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val visibleAccounts = remember(accounts) {
        accounts.filter { !it.isHidden }.ifEmpty { accounts }
    }
    val selectedPot = pots.find { it.id == selectedPotId } ?: pots.firstOrNull()
    val selectedAccount = accounts.find { it.name.equals(selectedAccountName, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when {
                    entryToEdit != null -> "Edit Savings Entry"
                    type == "DEPOSIT" -> "Add to Savings"
                    else -> "Withdraw from Savings"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Mode Toggle: Deposit vs Withdraw
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = type == "DEPOSIT",
                        onClick = {
                            type = "DEPOSIT"
                            errorMsg = null
                        },
                        label = { Text("Add to Savings", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = FinanceSavingsTeal
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = type == "WITHDRAW",
                        onClick = {
                            type = "WITHDRAW"
                            errorMsg = null
                        },
                        label = { Text("Withdraw", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = FinanceExpenseRed
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Pot Selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedPot?.let { "${it.name} (${DateUtils.formatCurrency(it.savedAmount)})" } ?: "Select Pot",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Savings Pot *") },
                        leadingIcon = { Icon(Icons.Default.Savings, contentDescription = null, tint = FinanceSavingsTeal) },
                        trailingIcon = {
                            IconButton(onClick = { potDropdownExpanded = !potDropdownExpanded }) {
                                Icon(
                                    if (potDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = "Select pot"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("savings_entry_pot_selector"),
                        singleLine = true
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { potDropdownExpanded = true }
                    )

                    DropdownMenu(
                        expanded = potDropdownExpanded,
                        onDismissRequest = { potDropdownExpanded = false }
                    ) {
                        pots.forEach { pot ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(pot.name, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            DateUtils.formatCurrency(pot.savedAmount),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = FinanceSavingsTeal
                                        )
                                    }
                                },
                                onClick = {
                                    selectedPotId = pot.id
                                    potDropdownExpanded = false
                                    errorMsg = null
                                }
                            )
                        }
                    }
                }

                // Account Selector
                if (visibleAccounts.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No bank or wallet accounts yet. Please add an account first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    val selectedAccIcon = accountTypeIcon(selectedAccount?.accountType ?: com.example.data.model.ACCOUNT_TYPE_BANK)
                    OutlinedTextField(
                        value = selectedAccount?.let { "${it.name} (${DateUtils.formatCurrency(it.currentBalance)})" }
                            ?: selectedAccountName.ifBlank { "No account available" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (type == "DEPOSIT") "From Account *" else "To Account *") },
                        leadingIcon = { Icon(selectedAccIcon, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { accountDropdownExpanded = !accountDropdownExpanded }) {
                                Icon(
                                    if (accountDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = "Select account"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("savings_entry_account_selector"),
                        singleLine = true
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { accountDropdownExpanded = true }
                    )

                    DropdownMenu(
                        expanded = accountDropdownExpanded,
                        onDismissRequest = { accountDropdownExpanded = false }
                    ) {
                        visibleAccounts.forEach { acc ->
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = accountTypeIcon(acc),
                                        contentDescription = accountTypeLabel(acc.accountType),
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(acc.name, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            DateUtils.formatCurrency(acc.currentBalance),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (acc.currentBalance > 0) FinanceIncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedAccountName = acc.name
                                    accountDropdownExpanded = false
                                    errorMsg = null
                                }
                            )
                        }
                    }
                }

                // Balance impact hint
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = FinanceSavingsTeal.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = FinanceSavingsTeal, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (type == "DEPOSIT") {
                                "Moves money out of ${selectedAccountName.ifBlank { "account" }} into ${selectedPot?.name ?: "pot"} (excluded from account balance)."
                            } else {
                                "Moves money from ${selectedPot?.name ?: "pot"} back into ${selectedAccountName.ifBlank { "account" }}."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMsg = null
                    },
                    label = { Text("Amount (Rs.) *") },
                    leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("savings_entry_amount_input"),
                    singleLine = true
                )

                // Date & Auto-Calculated Day Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1.3f)) {
                        OutlinedTextField(
                            value = dateText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Date") },
                            trailingIcon = {
                                IconButton(onClick = { showDatePicker = true }) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = "Pick date")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showDatePicker = true }
                        )
                    }

                    OutlinedTextField(
                        value = dayText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Day") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // 12-Hour Time (AM/PM)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = DateUtils.format12HourTime(timeText),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Time (12-Hour AM/PM)") },
                        trailingIcon = {
                            IconButton(onClick = { showTimePicker = true }) {
                                Icon(Icons.Default.Schedule, contentDescription = "Pick time")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showTimePicker = true }
                    )
                }

                // Note
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note (Optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("savings_entry_note_input"),
                    maxLines = 2
                )

                errorMsg?.let { err ->
                    Text(
                        text = err,
                        color = FinanceExpenseRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (selectedPot == null || selectedPotId <= 0L) {
                        errorMsg = "Please create or select a savings pot first."
                        return@Button
                    }
                    if (visibleAccounts.isEmpty() || selectedAccountName.isBlank()) {
                        errorMsg = "Please add and select an account first."
                        return@Button
                    }
                    if (amt <= 0.0) {
                        errorMsg = "Please enter an amount greater than 0."
                        return@Button
                    }
                    onSave(
                        selectedPotId,
                        type,
                        amt,
                        selectedAccountName,
                        dateText,
                        timeText,
                        noteText.trim(),
                        { err -> errorMsg = err }
                    )
                },
                modifier = Modifier.testTag("savings_entry_save_button")
            ) {
                Text(if (entryToEdit != null) "Update" else if (type == "DEPOSIT") "Save" else "Withdraw")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = try {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(dateText)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            dateText = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }.format(Date(millis))
                        }
                        showDatePicker = false
                    }
                ) { Text("OK", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val (initHour, initMinute) = remember(timeText) {
            try {
                val canonical24 = DateUtils.to24HourTime(timeText)
                val parts = canonical24.split(":")
                Pair(parts.getOrNull(0)?.toIntOrNull() ?: 12, parts.getOrNull(1)?.toIntOrNull() ?: 0)
            } catch (_: Exception) {
                Pair(12, 0)
            }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = initHour,
            initialMinute = initMinute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        timeText = String.format(Locale.US, "%02d:%02d:00", timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) { Text("OK", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }
}
