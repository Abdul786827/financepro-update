package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.DateUtils

@Composable
fun AccountsScreen(
    viewModel: FinanceViewModel,
    onOpenTransfer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allAccounts by viewModel.accounts.collectAsStateWithLifecycle()
    val visibleAccounts = remember(allAccounts) { allAccounts.filter { !it.isHidden } }
    val hiddenAccounts = remember(allAccounts) { allAccounts.filter { it.isHidden } }
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    var selectedAccountName by remember { mutableStateOf<String?>(null) }
    var showHiddenSection by remember { mutableStateOf(false) }

    // Dialog states
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<AccountEntity?>(null) }
    var pendingDeleteInfo by remember { mutableStateOf<Pair<AccountEntity, Int>?>(null) }

    // Ensure selectedAccountName remains valid if accounts change
    LaunchedEffect(allAccounts) {
        if (selectedAccountName != null && allAccounts.none { it.name.equals(selectedAccountName, ignoreCase = true) }) {
            selectedAccountName = visibleAccounts.firstOrNull()?.name ?: allAccounts.firstOrNull()?.name
        } else if (selectedAccountName == null && visibleAccounts.isNotEmpty()) {
            selectedAccountName = visibleAccounts.first().name
        } else if (allAccounts.isEmpty()) {
            selectedAccountName = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header with Add Account & Transfer Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "Bank & Wallet Accounts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${visibleAccounts.size} Managed Accounts • Real-time Balances",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showAddAccountDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("add_account_header_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Account", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Add", maxLines = 1, softWrap = false, fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        if (allAccounts.size < 2) {
                            Toast.makeText(context, "Add at least 2 accounts to make a transfer", Toast.LENGTH_SHORT).show()
                            if (allAccounts.isEmpty()) showAddAccountDialog = true
                        } else {
                            onOpenTransfer()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("transfer_button")
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Transfer", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Transfer", maxLines = 1, softWrap = false, fontSize = 12.sp)
                }
            }
        }

        if (allAccounts.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("accounts_empty_state"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "No accounts yet. Add your first bank or wallet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Add any Pakistani bank, mobile wallet, or cash account to start tracking your balances and transactions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { showAddAccountDialog = true },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("empty_add_account_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Account", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            // Account Cards Grid + "+" Add Account Card at the end
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 155.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 260.dp)
            ) {
                items(visibleAccounts, key = { it.id }) { acc ->
                    val isSelected = selectedAccountName.equals(acc.name, ignoreCase = true)
                    AccountCardItem(
                        account = acc,
                        isSelected = isSelected,
                        onClick = { selectedAccountName = acc.name }
                    )
                }
                item(key = "add_account_grid_card") {
                    AddAccountGridCard(
                        onClick = { showAddAccountDialog = true }
                    )
                }
            }
        }

        // Collapsible Hidden Accounts section (if any)
        if (hiddenAccounts.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showHiddenSection = !showHiddenSection }
                    .testTag("hidden_accounts_toggle")
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Hidden Accounts (${hiddenAccounts.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            imageVector = if (showHiddenSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showHiddenSection) "Collapse" else "Expand"
                        )
                    }

                    if (showHiddenSection) {
                        Spacer(modifier = Modifier.height(6.dp))
                        hiddenAccounts.forEach { hiddenAcc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedAccountName = hiddenAcc.name }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = accountTypeIcon(hiddenAcc),
                                        contentDescription = accountTypeLabel(hiddenAcc.accountType),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${hiddenAcc.name} (${DateUtils.formatCurrency(hiddenAcc.currentBalance)})",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.toggleAccountHidden(hiddenAcc.id) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("unhide_account_${hiddenAcc.id}")
                                ) {
                                    Text("Unhide", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // Account Details & History Section
        val activeAccount = allAccounts.find { it.name.equals(selectedAccountName, ignoreCase = true) }
        val accountTxList = transactions.filter {
            it.fromAccount.equals(selectedAccountName, ignoreCase = true) ||
                it.toAccount.equals(selectedAccountName, ignoreCase = true)
        }

        activeAccount?.let { acc ->
            val orderIdx = allAccounts.indexOfFirst { it.id == acc.id }
            val canMoveUp = orderIdx > 0
            val canMoveDown = orderIdx in 0 until (allAccounts.size - 1)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = accountTypeIcon(acc),
                                    contentDescription = accountTypeLabel(acc.accountType),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${acc.name} Ledger Details",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${accountTypeLabel(acc.accountType)} • Opening: ${DateUtils.formatCurrency(acc.openingBalance)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (acc.holderTitle.isNotBlank() || acc.accountNumberLast4.isNotBlank()) {
                                val metaParts = buildList {
                                    if (acc.holderTitle.isNotBlank()) add(acc.holderTitle)
                                    if (acc.accountNumberLast4.isNotBlank()) add(maskAccountNumber(acc.accountNumberLast4))
                                }
                                Text(
                                    text = metaParts.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (acc.note.isNotBlank()) {
                                Text(
                                    text = acc.note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Current Balance",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = DateUtils.formatCurrency(acc.currentBalance),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Manage actions row: Reorder, Hide/Unhide, Edit, Delete (custom only)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(
                                onClick = { viewModel.moveAccountOrder(acc.id, moveUp = true) },
                                enabled = canMoveUp,
                                modifier = Modifier.size(36.dp).testTag("move_account_up_${acc.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Move Earlier",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.moveAccountOrder(acc.id, moveUp = false) },
                                enabled = canMoveDown,
                                modifier = Modifier.size(36.dp).testTag("move_account_down_${acc.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Move Later",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.toggleAccountHidden(acc.id) },
                                modifier = Modifier.size(36.dp).testTag("toggle_hide_account_${acc.id}")
                            ) {
                                Icon(
                                    imageVector = if (acc.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (acc.isHidden) "Unhide Account" else "Hide Account",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { accountToEdit = acc },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("edit_account_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Account", modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit", fontSize = 12.sp)
                            }

                            TextButton(
                                onClick = {
                                    viewModel.checkAccountUsageCount(acc.name) { usageCount ->
                                        pendingDeleteInfo = acc to usageCount
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("delete_account_button")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Account", modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = "Transaction History for ${selectedAccountName ?: "All"} (${accountTxList.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (accountTxList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No transactions recorded for this account yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(accountTxList, key = { it.id }) { tx ->
                    AccountTxRow(
                        tx = tx,
                        currentAccount = selectedAccountName ?: "",
                        accounts = allAccounts
                    )
                }
            }
        }
    }

    // Add Account Dialog
    if (showAddAccountDialog) {
        AccountFormDialog(
            existingAccounts = allAccounts,
            onDismiss = { showAddAccountDialog = false },
            onSave = { accountType, name, holderTitle, last4, note, openingBalance, onError ->
                viewModel.addAccount(
                    accountType = accountType,
                    name = name,
                    holderTitle = holderTitle,
                    accountNumberLast4 = last4,
                    note = note,
                    openingBalance = openingBalance,
                    onError = onError,
                    onSuccess = { created ->
                        selectedAccountName = created.name
                        showAddAccountDialog = false
                        Toast.makeText(context, "${created.name} added to accounts", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    // Edit Account Dialog
    accountToEdit?.let { editing ->
        AccountFormDialog(
            existingAccounts = allAccounts,
            accountToEdit = editing,
            onDismiss = { accountToEdit = null },
            onSave = { accountType, name, holderTitle, last4, note, _, onError ->
                viewModel.editAccount(
                    id = editing.id,
                    newType = accountType,
                    newName = name,
                    holderTitle = holderTitle,
                    accountNumberLast4 = last4,
                    note = note,
                    onError = onError,
                    onSuccess = { updated ->
                        selectedAccountName = updated.name
                        accountToEdit = null
                        Toast.makeText(context, "${updated.name} updated", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    // Delete Account Confirmation Dialog
    pendingDeleteInfo?.let { (acc, usageCount) ->
        DeleteAccountConfirmDialog(
            account = acc,
            usageCount = usageCount,
            onDismiss = { pendingDeleteInfo = null },
            onConfirmDelete = { mode ->
                viewModel.deleteAccount(
                    id = acc.id,
                    mode = mode,
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        pendingDeleteInfo = null
                    },
                    onSuccess = {
                        pendingDeleteInfo = null
                        selectedAccountName = visibleAccounts.firstOrNull { it.id != acc.id }?.name
                        Toast.makeText(context, "${acc.name} deleted", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }
}

@Composable
private fun AccountCardItem(
    account: AccountEntity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon = accountTypeIcon(account)
    val typeLabel = accountTypeLabel(account.accountType)
    val masked = maskAccountNumber(account.accountNumberLast4)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("account_card_${account.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = typeLabel,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (masked.isNotBlank() || account.holderTitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildList {
                        if (masked.isNotBlank()) add(masked)
                        if (account.holderTitle.isNotBlank()) add(account.holderTitle)
                    }.joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = DateUtils.formatCurrency(account.currentBalance),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "In: +${DateUtils.formatCurrency(account.totalReceived)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = FinanceIncomeGreen
                    )
                    if (account.totalTransferredOut > 0) {
                        Text(
                            text = "Out: -${DateUtils.formatCurrency(account.totalTransferredOut)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = FinanceExpenseRed
                        )
                    }
                }
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun AddAccountGridCard(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 96.dp)
            .clickable(onClick = onClick)
            .testTag("add_account_grid_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Account",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Add Account",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Bank, Wallet, Cash",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AccountTxRow(
    tx: TransactionEntity,
    currentAccount: String,
    accounts: List<AccountEntity>
) {
    val isIncoming = tx.toAccount.equals(currentAccount, ignoreCase = true)
    val counterpart = if (isIncoming) tx.fromAccount else tx.toAccount
    val counterpartIcon = resolveAccountIconByName(counterpart, accounts)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                val icon = when {
                    tx.type == "SAVINGS" -> Icons.Default.Savings
                    tx.type == "UDHAAR" -> Icons.Default.Handshake
                    tx.type == "TRANSFER" -> Icons.Default.SwapHoriz
                    isIncoming -> Icons.Default.ArrowDownward
                    else -> Icons.Default.ArrowUpward
                }
                val tint = when {
                    tx.type == "SAVINGS" -> FinanceSavingsTeal
                    tx.type == "TRANSFER" -> FinanceTransferBlue
                    isIncoming -> FinanceIncomeGreen
                    else -> FinanceExpenseRed
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = tx.description.ifBlank { tx.category },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val dateFormatted = DateUtils.formatReadableDateWithDay(tx.date, tx.dayOfWeek)
                    val timeFormatted = DateUtils.format12HourTime(tx.time)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (counterpartIcon != null) {
                            Icon(
                                imageVector = counterpartIcon,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Text(
                            text = "$dateFormatted $timeFormatted • ${tx.fromAccount} -> ${tx.toAccount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val prefix = if (tx.type == "TRANSFER") "" else if (isIncoming) "+" else "-"
                val color = if (tx.type == "TRANSFER") FinanceTransferBlue else if (isIncoming) FinanceIncomeGreen else FinanceExpenseRed

                Text(
                    text = "$prefix${DateUtils.formatCurrency(tx.amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = when (tx.type) {
                        "UDHAAR" -> "Udhaar"
                        "SAVINGS" -> "Savings"
                        else -> tx.type
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (tx.type) {
                        "UDHAAR" -> Color(0xFF7C3AED)
                        "SAVINGS" -> FinanceSavingsTeal
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (tx.type == "UDHAAR" || tx.type == "SAVINGS") FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
