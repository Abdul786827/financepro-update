package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.PakistanBankDirectory
import com.example.data.model.*
import com.example.ui.theme.FinanceExpenseRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFormDialog(
    existingAccounts: List<AccountEntity>,
    accountToEdit: AccountEntity? = null,
    onDismiss: () -> Unit,
    onSave: (
        accountType: String,
        name: String,
        holderTitle: String,
        accountNumberLast4: String,
        note: String,
        openingBalance: Double,
        onError: (String) -> Unit
    ) -> Unit
) {
    val isEditing = accountToEdit != null
    var selectedType by remember {
        mutableStateOf(normalizeAccountType(accountToEdit?.accountType ?: ACCOUNT_TYPE_BANK))
    }

    val existingNamesSet = remember(existingAccounts) {
        existingAccounts.map { it.name.trim() }.toSet()
    }
    val directoryOptions = remember(selectedType, existingNamesSet, accountToEdit) {
        PakistanBankDirectory.getOptionsForType(
            accountType = selectedType,
            existingAccountNames = existingNamesSet,
            excludeOwnName = accountToEdit?.name
        )
    }

    var isManualEntry by remember {
        mutableStateOf(
            if (accountToEdit != null) {
                directoryOptions.none { it.name.equals(accountToEdit.name, ignoreCase = true) }
            } else {
                false
            }
        )
    }
    var selectedDirectoryName by remember {
        mutableStateOf(
            if (accountToEdit != null && !isManualEntry) accountToEdit.name else ""
        )
    }
    var customNameText by remember {
        mutableStateOf(
            if (accountToEdit != null && isManualEntry) accountToEdit.name else ""
        )
    }
    var holderTitle by remember { mutableStateOf(accountToEdit?.holderTitle ?: "") }
    var accountNumberLast4 by remember { mutableStateOf(accountToEdit?.accountNumberLast4 ?: "") }
    var noteText by remember { mutableStateOf(accountToEdit?.note ?: "") }
    var openingBalanceText by remember { mutableStateOf("0") }
    var showBankPickerDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val effectiveAccountName = if (isManualEntry) customNameText.trim() else selectedDirectoryName.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = accountTypeIcon(selectedType),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEditing) "Edit Account" else "Add New Account",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                // a) Account Type Selector: Bank / Wallet / Cash
                Text(
                    text = "Account Type *",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val types = listOf(
                        ACCOUNT_TYPE_BANK to "Bank",
                        ACCOUNT_TYPE_WALLET to "Wallet",
                        ACCOUNT_TYPE_CASH to "Cash"
                    )
                    types.forEach { (typeKey, label) ->
                        val isSelected = selectedType == typeKey
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedType = typeKey
                                errorMessage = null
                                // Clear incompatible directory pick unless editing/manual
                                if (!isManualEntry && !isEditing) {
                                    selectedDirectoryName = ""
                                }
                            },
                            label = {
                                Text(
                                    text = label,
                                    maxLines = 1,
                                    softWrap = false,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = accountTypeIcon(typeKey),
                                    contentDescription = label,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("account_type_chip_$typeKey")
                        )
                    }
                }

                // b) Account Name: Searchable Pakistani Banks/Wallets Dropdown + "Other / Add Manually"
                Text(
                    text = "Account Name *",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (isManualEntry) {
                            "Other / Add Manually"
                        } else {
                            selectedDirectoryName.ifBlank { "" }
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = {
                            Text(
                                when (selectedType) {
                                    ACCOUNT_TYPE_WALLET -> "Select Pakistani Wallet"
                                    ACCOUNT_TYPE_CASH -> "Select Cash Account"
                                    else -> "Select Pakistani Bank"
                                }
                            )
                        },
                        placeholder = { Text("Tap to search or choose manually") },
                        leadingIcon = {
                            Icon(
                                imageVector = accountTypeIcon(selectedType),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { showBankPickerDialog = true },
                                modifier = Modifier.testTag("account_name_picker_button")
                            ) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Open directory")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_name_dropdown_field"),
                        singleLine = true
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showBankPickerDialog = true }
                            .testTag("account_name_picker_trigger")
                    )
                }

                if (isManualEntry) {
                    OutlinedTextField(
                        value = customNameText,
                        onValueChange = {
                            customNameText = it
                            errorMessage = null
                        },
                        label = { Text("Custom ${accountTypeLabel(selectedType)} Name *") },
                        placeholder = {
                            Text(
                                when (selectedType) {
                                    ACCOUNT_TYPE_WALLET -> "e.g. Payoneer Wallet, Family Wallet"
                                    ACCOUNT_TYPE_CASH -> "e.g. Cash Drawer, Travel Cash"
                                    else -> "e.g. Meezan Savings, Salary Bank"
                                }
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = accountTypeIcon(selectedType),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_custom_name_input"),
                        singleLine = true
                    )
                }

                // c) Optional Account Title / Holder Name
                OutlinedTextField(
                    value = holderTitle,
                    onValueChange = { holderTitle = it },
                    label = { Text("Account Title / Holder Name (Optional)") },
                    placeholder = { Text("e.g. Muhammad Ali") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_holder_title_input"),
                    singleLine = true
                )

                // d) Optional Last 4 Digits or Account Number (stored as entered, displayed masked)
                OutlinedTextField(
                    value = accountNumberLast4,
                    onValueChange = { accountNumberLast4 = it },
                    label = { Text("Last 4 Digits / Account # (Optional)") },
                    placeholder = { Text("e.g. 1234") },
                    leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null) },
                    supportingText = if (accountNumberLast4.isNotBlank()) {
                        { Text("Displayed masked: ${maskAccountNumber(accountNumberLast4)}") }
                    } else {
                        { Text("Stored as entered; displayed masked") }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_number_last4_input"),
                    singleLine = true
                )

                // Note (Optional)
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note (Optional)") },
                    placeholder = { Text("e.g. Salary account or emergency fund") },
                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_note_input"),
                    maxLines = 2
                )

                // e) Opening Balance (default 0) when adding
                if (!isEditing) {
                    OutlinedTextField(
                        value = openingBalanceText,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.matches(Regex("""^\d*\.?\d*$"""))) {
                                openingBalanceText = input
                                errorMessage = null
                            }
                        },
                        label = { Text("Opening Balance (Rs., default 0)") },
                        leadingIcon = {
                            Text(
                                text = "Rs.",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_opening_balance_input"),
                        singleLine = true
                    )
                }

                }

                // Inline Validation Error pinned visibly below scrollable fields
                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_form_error")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = FinanceExpenseRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = effectiveAccountName
                    if (cleanName.isBlank()) {
                        errorMessage = "Account name cannot be empty."
                        return@Button
                    }
                    if (cleanName.equals(DELETED_ACCOUNT_LABEL, ignoreCase = true)) {
                        errorMessage = "'$DELETED_ACCOUNT_LABEL' is reserved."
                        return@Button
                    }
                    val duplicate = existingAccounts.find {
                        it.id != accountToEdit?.id && it.name.trim().equals(cleanName, ignoreCase = true)
                    }
                    if (duplicate != null) {
                        errorMessage = "An account named '${duplicate.name}' already exists."
                        return@Button
                    }
                    val openingBal = if (isEditing || openingBalanceText.isBlank()) {
                        0.0
                    } else {
                        openingBalanceText.toDoubleOrNull() ?: run {
                            errorMessage = "Please enter a valid opening balance."
                            return@Button
                        }
                    }
                    if (openingBal < 0.0) {
                        errorMessage = "Opening balance cannot be negative."
                        return@Button
                    }

                    onSave(
                        selectedType,
                        cleanName,
                        holderTitle.trim(),
                        accountNumberLast4.trim(),
                        noteText.trim(),
                        openingBal
                    ) { err -> errorMessage = err }
                },
                modifier = Modifier.testTag("save_account_button")
            ) {
                Text(if (isEditing) "Update Account" else "Save Account")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_account_button")
            ) {
                Text("Cancel")
            }
        }
    )

    if (showBankPickerDialog) {
        SearchableBankPickerDialog(
            accountType = selectedType,
            options = directoryOptions,
            currentSelected = if (isManualEntry) "" else selectedDirectoryName,
            onSelectDirectoryItem = { chosen ->
                selectedDirectoryName = chosen
                isManualEntry = false
                errorMessage = null
                showBankPickerDialog = false
            },
            onSelectManual = {
                isManualEntry = true
                errorMessage = null
                showBankPickerDialog = false
            },
            onDismiss = { showBankPickerDialog = false }
        )
    }
}

@Composable
private fun SearchableBankPickerDialog(
    accountType: String,
    options: List<com.example.data.local.BankDirectoryItem>,
    currentSelected: String,
    onSelectDirectoryItem: (String) -> Unit,
    onSelectManual: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredOptions = remember(searchQuery, options) {
        if (searchQuery.isBlank()) {
            options
        } else {
            val q = searchQuery.trim()
            options.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.categoryGroup.contains(q, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select ${accountTypeLabel(accountType)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search Pakistani banks & wallets...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_directory_search_input")
                )

                // Always-visible "Other / Add Manually" option
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectManual() }
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("option_add_manually")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Other / Add Manually",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Type any custom bank, wallet, or cash name",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (filteredOptions.isEmpty()) {
                        item {
                            Text(
                                text = "No matching entries. Tap 'Other / Add Manually' above to add it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    } else {
                        items(filteredOptions, key = { "${it.name}_${it.accountType}" }) { item ->
                            val isSelected = currentSelected.equals(item.name, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectDirectoryItem(item.name) }
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("bank_option_${item.name}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = accountTypeIcon(accountType),
                                            contentDescription = item.name,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = item.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            Text(
                                                text = item.categoryGroup,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
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

@Composable
fun DeleteAccountConfirmDialog(
    account: AccountEntity,
    usageCount: Int,
    onDismiss: () -> Unit,
    onConfirmDelete: (AccountDeleteMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = if (usageCount > 0) "Delete '${account.name}'?" else "Delete '${account.name}'?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (usageCount > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "'${account.name}' has $usageCount linked record(s) (transactions, transfers, Udhaar, or Savings). Choose how you want to handle its history:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { onConfirmDelete(AccountDeleteMode.DELETE_ACCOUNT_AND_TRANSACTIONS) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("delete_account_and_transactions_button")
                    ) {
                        Text(
                            text = "Delete account and all its transactions",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedButton(
                        onClick = { onConfirmDelete(AccountDeleteMode.KEEP_HISTORY_AS_DELETED_ACCOUNT) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("keep_history_deleted_account_button")
                    ) {
                        Text(
                            text = "Keep history as '$DELETED_ACCOUNT_LABEL'",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Text(
                    text = "Are you sure you want to delete '${account.name}'? It has no linked transactions.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            if (usageCount == 0) {
                Button(
                    onClick = { onConfirmDelete(AccountDeleteMode.SIMPLE) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    Text("Delete Account")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_account_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
