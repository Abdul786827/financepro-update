package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SavingsEntryEntity
import com.example.data.model.SavingsPotEntity
import com.example.ui.components.SavingsEntryDialog
import com.example.ui.components.SavingsPotDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.DateUtils

@Composable
fun SavingsScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pots by viewModel.savingsPots.collectAsStateWithLifecycle()
    val entries by viewModel.savingsEntries.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val totalSavings by viewModel.totalSavings.collectAsStateWithLifecycle()

    var selectedPotFilterId by remember { mutableStateOf<Long?>(null) }

    // Dialog States
    var showPotDialog by remember { mutableStateOf(false) }
    var potToEdit by remember { mutableStateOf<SavingsPotEntity?>(null) }
    var potToDelete by remember { mutableStateOf<SavingsPotEntity?>(null) }

    var showEntryDialog by remember { mutableStateOf(false) }
    var entryInitialType by remember { mutableStateOf("DEPOSIT") }
    var entryInitialPotId by remember { mutableStateOf<Long?>(null) }
    var entryToEdit by remember { mutableStateOf<SavingsEntryEntity?>(null) }
    var entryToDelete by remember { mutableStateOf<SavingsEntryEntity?>(null) }

    val potMap = remember(pots) { pots.associateBy { it.id } }
    val filteredEntries = remember(entries, selectedPotFilterId) {
        if (selectedPotFilterId == null) entries else entries.filter { it.potId == selectedPotFilterId }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Total Savings Hero Card
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("total_savings_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF0F766E),
                                    EmeraldPrimaryLight,
                                    Color(0xFF062C22)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "TOTAL SAVINGS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Kept separate from account balances",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Savings,
                                contentDescription = "Savings",
                                tint = GoldTertiaryDark,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = DateUtils.formatCurrency(totalSavings),
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${pots.size} Savings Pot${if (pots.size == 1) "" else "s"} • ${entries.size} Entries",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Quick Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (pots.isEmpty()) {
                                        Toast.makeText(context, "Create a savings pot first", Toast.LENGTH_SHORT).show()
                                        potToEdit = null
                                        showPotDialog = true
                                    } else if (accounts.isEmpty()) {
                                        Toast.makeText(context, "Please add a bank or wallet account first", Toast.LENGTH_SHORT).show()
                                    } else {
                                        entryToEdit = null
                                        entryInitialType = "DEPOSIT"
                                        entryInitialPotId = selectedPotFilterId ?: pots.firstOrNull()?.id
                                        showEntryDialog = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF064E3B)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_deposit_savings")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    if (pots.isEmpty()) {
                                        Toast.makeText(context, "No savings pots available", Toast.LENGTH_SHORT).show()
                                    } else if (accounts.isEmpty()) {
                                        Toast.makeText(context, "Please add a bank or wallet account first", Toast.LENGTH_SHORT).show()
                                    } else {
                                        entryToEdit = null
                                        entryInitialType = "WITHDRAW"
                                        entryInitialPotId = selectedPotFilterId ?: pots.firstOrNull()?.id
                                        showEntryDialog = true
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_withdraw_savings")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Withdraw", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    potToEdit = null
                                    showPotDialog = true
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_new_savings_pot")
                            ) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Pot", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 2. Savings Pots Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Savings Goals & Pots (${pots.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = {
                        potToEdit = null
                        showPotDialog = true
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Create Pot")
                }
            }
        }

        if (pots.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = null,
                            tint = FinanceSavingsTeal,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Savings Pots Yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Create goals like Emergency, Hajj, or Car to set money aside safely without counting it in spendable balances.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                potToEdit = null
                                showPotDialog = true
                            }
                        ) {
                            Text("Create First Pot")
                        }
                    }
                }
            }
        } else {
            items(pots, key = { it.id }) { pot ->
                SavingsPotCard(
                    pot = pot,
                    isFiltered = selectedPotFilterId == pot.id,
                    onSelectFilter = {
                        selectedPotFilterId = if (selectedPotFilterId == pot.id) null else pot.id
                    },
                    onDeposit = {
                        if (accounts.isEmpty()) {
                            Toast.makeText(context, "Please add a bank or wallet account first", Toast.LENGTH_SHORT).show()
                        } else {
                            entryToEdit = null
                            entryInitialType = "DEPOSIT"
                            entryInitialPotId = pot.id
                            showEntryDialog = true
                        }
                    },
                    onWithdraw = {
                        if (accounts.isEmpty()) {
                            Toast.makeText(context, "Please add a bank or wallet account first", Toast.LENGTH_SHORT).show()
                        } else {
                            entryToEdit = null
                            entryInitialType = "WITHDRAW"
                            entryInitialPotId = pot.id
                            showEntryDialog = true
                        }
                    },
                    onEdit = {
                        potToEdit = pot
                        showPotDialog = true
                    },
                    onDelete = {
                        potToDelete = pot
                    }
                )
            }
        }

        // 3. Deposit / Withdrawal History Section
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Savings History (${filteredEntries.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (pots.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedPotFilterId == null,
                            onClick = { selectedPotFilterId = null },
                            label = { Text("All Pots") }
                        )
                    }
                    items(pots, key = { "filter_${it.id}" }) { pot ->
                        FilterChip(
                            selected = selectedPotFilterId == pot.id,
                            onClick = {
                                selectedPotFilterId = if (selectedPotFilterId == pot.id) null else pot.id
                            },
                            label = { Text(pot.name) }
                        )
                    }
                }
            }
        }

        if (filteredEntries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No savings deposits or withdrawals recorded yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredEntries, key = { "entry_${it.id}" }) { entry ->
                val potName = potMap[entry.potId]?.name ?: "Savings Pot"
                SavingsEntryHistoryCard(
                    entry = entry,
                    potName = potName,
                    onEdit = {
                        entryToEdit = entry
                        showEntryDialog = true
                    },
                    onDelete = {
                        entryToDelete = entry
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    // Create / Edit Pot Dialog
    if (showPotDialog) {
        SavingsPotDialog(
            potToEdit = potToEdit,
            onDismiss = {
                showPotDialog = false
                potToEdit = null
            },
            onSave = { name, targetAmt, targetDate, onError ->
                val editing = potToEdit
                if (editing == null) {
                    viewModel.createSavingsPot(
                        name = name,
                        targetAmount = targetAmt,
                        targetDate = targetDate,
                        onError = onError,
                        onSuccess = {
                            showPotDialog = false
                            Toast.makeText(context, "Pot '$name' created", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    viewModel.updateSavingsPot(
                        id = editing.id,
                        name = name,
                        targetAmount = targetAmt,
                        targetDate = targetDate,
                        onError = onError,
                        onSuccess = {
                            showPotDialog = false
                            potToEdit = null
                            Toast.makeText(context, "Pot updated", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        )
    }

    // Add / Edit Deposit or Withdrawal Dialog
    if (showEntryDialog) {
        SavingsEntryDialog(
            pots = pots,
            accounts = accounts,
            initialType = entryInitialType,
            initialPotId = entryInitialPotId,
            entryToEdit = entryToEdit,
            onDismiss = {
                showEntryDialog = false
                entryToEdit = null
            },
            onSave = { potId, type, amount, accountName, date, time, note, onError ->
                val editing = entryToEdit
                if (editing == null) {
                    viewModel.addSavingsEntry(
                        potId = potId,
                        type = type,
                        amount = amount,
                        accountName = accountName,
                        date = date,
                        time = time,
                        note = note,
                        onError = onError,
                        onSuccess = {
                            showEntryDialog = false
                            Toast.makeText(
                                context,
                                if (type == "DEPOSIT") "Added to savings" else "Withdrawn from savings",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                } else {
                    viewModel.updateSavingsEntry(
                        entryId = editing.id,
                        potId = potId,
                        type = type,
                        amount = amount,
                        accountName = accountName,
                        date = date,
                        time = time,
                        note = note,
                        onError = onError,
                        onSuccess = {
                            showEntryDialog = false
                            entryToEdit = null
                            Toast.makeText(context, "Savings entry updated", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        )
    }

    // Delete Pot Confirmation
    potToDelete?.let { pot ->
        AlertDialog(
            onDismissRequest = { potToDelete = null },
            title = { Text("Delete '${pot.name}' Pot?") },
            text = {
                Text(
                    if (pot.savedAmount > 0.0 || entries.any { it.potId == pot.id }) {
                        "Deleting '${pot.name}' will remove all its entries and restore linked account balances."
                    } else {
                        "Are you sure you want to delete the '${pot.name}' savings pot?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSavingsPot(
                            id = pot.id,
                            onError = { err ->
                                potToDelete = null
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            },
                            onSuccess = {
                                if (selectedPotFilterId == pot.id) selectedPotFilterId = null
                                potToDelete = null
                                Toast.makeText(context, "Pot deleted", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { potToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Delete Entry Confirmation
    entryToDelete?.let { entry ->
        val potName = potMap[entry.potId]?.name ?: "pot"
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Savings Entry?") },
            text = {
                Text(
                    "Delete this ${if (entry.type == "DEPOSIT") "deposit" else "withdrawal"} of ${DateUtils.formatCurrency(entry.amount)} ($potName / ${entry.accountName})? Account and pot balances will recalculate automatically."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSavingsEntry(
                            entryId = entry.id,
                            onError = { err ->
                                entryToDelete = null
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            },
                            onSuccess = {
                                entryToDelete = null
                                Toast.makeText(context, "Savings entry deleted", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SavingsPotCard(
    pot: SavingsPotEntity,
    isFiltered: Boolean,
    onSelectFilter: () -> Unit,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val hasTarget = pot.targetAmount != null && pot.targetAmount > 0.0
    val target = pot.targetAmount ?: 0.0
    val rawRatio = if (hasTarget) (pot.savedAmount / target).toFloat() else 0f
    val clampedProgress = rawRatio.coerceIn(0f, 1f)
    val percentInt = if (hasTarget) ((pot.savedAmount / target) * 100.0).toInt().coerceAtLeast(0) else 0
    val remaining = if (hasTarget) (target - pot.savedAmount).coerceAtLeast(0.0) else 0.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectFilter() }
            .testTag("savings_pot_card_${pot.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFiltered) {
                FinanceSavingsTeal.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isFiltered) androidx.compose.foundation.BorderStroke(1.5.dp, FinanceSavingsTeal) else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(FinanceSavingsTeal.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = pot.name,
                            tint = FinanceSavingsTeal,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = pot.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (pot.targetDate.isNotBlank()) {
                            Text(
                                text = "Target Date: ${DateUtils.formatReadableDate(pot.targetDate)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("edit_pot_${pot.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit pot",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_pot_${pot.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete pot",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Saved Amount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = DateUtils.formatCurrency(pot.savedAmount),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = FinanceSavingsTeal
                    )
                }

                if (hasTarget) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Target: ${DateUtils.formatCurrency(target)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$percentInt% Complete",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (remaining <= 0.0) FinanceIncomeGreen else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (hasTarget) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { clampedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .testTag("pot_progress_${pot.id}"),
                    color = if (remaining <= 0.0) FinanceIncomeGreen else FinanceSavingsTeal,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${DateUtils.formatCurrency(pot.savedAmount)} / ${DateUtils.formatCurrency(target)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (remaining <= 0.0) "Goal Reached!" else "Remaining: ${DateUtils.formatCurrency(remaining)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (remaining <= 0.0) FinanceIncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onDeposit,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                    onClick = onWithdraw,
                    enabled = pot.savedAmount > 0.0,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Withdraw", fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun SavingsEntryHistoryCard(
    entry: SavingsEntryEntity,
    potName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isDeposit = entry.type == "DEPOSIT"
    val tint = if (isDeposit) FinanceSavingsTeal else FinanceExpenseRed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("savings_entry_${entry.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDeposit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = if (isDeposit) "Deposit" else "Withdraw",
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isDeposit) "Saved to $potName" else "Withdrawn from $potName",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isDeposit) "From ${entry.accountName}" else "To ${entry.accountName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = FinanceSavingsTeal,
                    fontWeight = FontWeight.SemiBold
                )
                val dayText = entry.dayOfWeek.ifBlank { DateUtils.getDayOfWeek(entry.date) }
                val dateStr = DateUtils.formatReadableDateWithDay(entry.date, dayText)
                val timeStr = DateUtils.format12HourTime(entry.time)
                Text(
                    text = "$dateStr • $timeStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.note.isNotBlank()) {
                    Text(
                        text = entry.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isDeposit) "+" else "-"}${DateUtils.formatCurrency(entry.amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDeposit) FinanceIncomeGreen else FinanceExpenseRed
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("edit_savings_entry_${entry.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit savings entry",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("delete_savings_entry_${entry.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete savings entry",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
