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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TransactionEntity
import com.example.ui.components.AccountDistributionDonutChart
import com.example.ui.components.IncomeExpenseBarChart
import com.example.ui.components.accountTypeIcon
import com.example.ui.components.accountTypeLabel
import com.example.ui.theme.*
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.DateUtils

@Composable
fun DashboardScreen(
    viewModel: FinanceViewModel,
    onNavigateToStatement: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onQuickAdd: (String) -> Unit,
    onNavigateToUdhaar: () -> Unit = {},
    onNavigateToSavings: () -> Unit = {},
    onOpenAddAccount: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalBalance by viewModel.totalAvailableBalance.collectAsStateWithLifecycle()
    val totalIncome by viewModel.totalIncome.collectAsStateWithLifecycle()
    val totalExpenses by viewModel.totalExpenses.collectAsStateWithLifecycle()
    val totalTransfers by viewModel.totalTransfers.collectAsStateWithLifecycle()
    val totalPetrol by viewModel.totalPetrol.collectAsStateWithLifecycle()
    val totalExtra by viewModel.totalExtra.collectAsStateWithLifecycle()
    val accounts by viewModel.visibleAccounts.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Hero Total Balance Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_balance_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    EmeraldPrimaryLight,
                                    Color(0xFF0D4738),
                                    Color(0xFF042017)
                                )
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOTAL AVAILABLE BALANCE",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = GoldTertiaryDark
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = DateUtils.formatCurrency(totalBalance),
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Total Received",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "+${DateUtils.formatCurrency(totalIncome)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = FinanceIncomeGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Total Expenses",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "-${DateUtils.formatCurrency(totalExpenses)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFFF8B8B),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Actions Row
        item {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickActionButton(
                    label = "Income",
                    icon = Icons.Default.AddCircle,
                    color = FinanceIncomeGreen,
                    onClick = { onQuickAdd("INCOME") }
                )
                QuickActionButton(
                    label = "Expense",
                    icon = Icons.Default.RemoveCircle,
                    color = FinanceExpenseRed,
                    onClick = { onQuickAdd("EXPENSE") }
                )
                QuickActionButton(
                    label = "Transfer",
                    icon = Icons.Default.SwapHoriz,
                    color = FinanceTransferBlue,
                    onClick = { onQuickAdd("TRANSFER") }
                )
                QuickActionButton(
                    label = "Petrol",
                    icon = Icons.Default.LocalGasStation,
                    color = FinancePetrolOrange,
                    onClick = { onQuickAdd("PETROL") }
                )
                QuickActionButton(
                    label = "Extra",
                    icon = Icons.Default.ReceiptLong,
                    color = FinanceExtraPurple,
                    onClick = { onQuickAdd("EXTRA_EXPENSE") }
                )
            }
        }

        // Secondary Metrics Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricSmallCard(
                    title = "Transfers",
                    amount = totalTransfers,
                    icon = Icons.Default.SwapHoriz,
                    color = FinanceTransferBlue,
                    modifier = Modifier.weight(1f)
                )
                MetricSmallCard(
                    title = "Petrol",
                    amount = totalPetrol,
                    icon = Icons.Default.LocalGasStation,
                    color = FinancePetrolOrange,
                    modifier = Modifier.weight(1f)
                )
                MetricSmallCard(
                    title = "Extra Expenses",
                    amount = totalExtra,
                    icon = Icons.Default.ReceiptLong,
                    color = FinanceExtraPurple,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Separate Total Savings Card (never included in account balance or income/expense)
        item {
            val totalSavings by viewModel.totalSavings.collectAsStateWithLifecycle()
            val savingsPots by viewModel.savingsPots.collectAsStateWithLifecycle()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToSavings() }
                    .testTag("dashboard_savings_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = FinanceSavingsTeal.copy(alpha = 0.12f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = FinanceSavingsTeal.copy(alpha = 0.2f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Savings,
                                    contentDescription = "Total Savings",
                                    tint = FinanceSavingsTeal,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Total Savings (Separate)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${savingsPots.size} Pot${if (savingsPots.size == 1) "" else "s"} • Excluded from spendable balance",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = DateUtils.formatCurrency(totalSavings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = FinanceSavingsTeal
                        )
                        Text(
                            text = "Open >",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Udhaar / Loans Summary Card (Completely separate from bank/wallet accounts)
        item {
            val udhaarToReceive by viewModel.udhaarTotalToReceive.collectAsStateWithLifecycle()
            val udhaarToPay by viewModel.udhaarTotalToPay.collectAsStateWithLifecycle()
            val udhaarNet by viewModel.udhaarNetBalance.collectAsStateWithLifecycle()
            val pendingItems by viewModel.udhaarPendingItemsCount.collectAsStateWithLifecycle()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToUdhaar() }
                    .testTag("dashboard_udhaar_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = EmeraldPrimaryLight.copy(alpha = 0.15f),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Handshake,
                                        contentDescription = null,
                                        tint = EmeraldPrimaryLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Udhaar Ledger (Separate)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Independent loans & lent items • Zero account impact",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        TextButton(
                            onClick = onNavigateToUdhaar,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Open", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("To Receive (Given)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = DateUtils.formatCurrency(udhaarToReceive),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = FinanceIncomeGreen
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("To Pay (Taken)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = DateUtils.formatCurrency(udhaarToPay),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = FinanceExpenseRed
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Net Udhaar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = DateUtils.formatCurrency(udhaarNet),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (udhaarNet >= 0) EmeraldPrimaryLight else FinanceExpenseRed
                            )
                        }
                    }

                    if (pendingItems > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• $pendingItems lent/borrowed item(s) pending return",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Charts
        item {
            IncomeExpenseBarChart(income = totalIncome, expense = totalExpenses)
        }

        item {
            AccountDistributionDonutChart(accounts = accounts)
        }

        // Account Chips Carousel
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Accounts (${accounts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onNavigateToAccounts) {
                    Text("View All")
                }
            }

            if (accounts.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_empty_accounts_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No accounts yet. Add your first bank or wallet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = onOpenAddAccount,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("dashboard_add_account_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Account", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(accounts) { acc ->
                        Card(
                            modifier = Modifier
                                .width(148.dp)
                                .clickable { onNavigateToAccounts() },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = acc.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                                    )
                                    Icon(
                                        imageVector = accountTypeIcon(acc),
                                        contentDescription = accountTypeLabel(acc.accountType),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = DateUtils.formatCurrency(acc.currentBalance),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "In: +${DateUtils.formatCurrency(acc.totalReceived)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FinanceIncomeGreen
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent Transactions Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onNavigateToStatement) {
                    Text("View Statement")
                }
            }
        }

        items(transactions.take(5), key = { it.id }) { tx ->
            RecentTransactionItem(tx = tx)
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MetricSmallCard(
    title: String,
    amount: Double,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = DateUtils.formatCurrency(amount),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RecentTransactionItem(tx: TransactionEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isUdhaarIncoming = tx.type == "UDHAAR" && (tx.description.contains("Taken", ignoreCase = true) || tx.description.contains("Repayment Received", ignoreCase = true))
            val isSavingsIncoming = tx.type == "SAVINGS" && tx.description.startsWith("Withdrawn from", ignoreCase = true)
            val isPositiveForAccount = tx.type == "INCOME" || isUdhaarIncoming || isSavingsIncoming

            val (icon, color) = when (tx.type) {
                "INCOME" -> Icons.Default.ArrowDownward to FinanceIncomeGreen
                "TRANSFER" -> Icons.Default.SwapHoriz to FinanceTransferBlue
                "PETROL" -> Icons.Default.LocalGasStation to FinancePetrolOrange
                "EXTRA_EXPENSE" -> Icons.Default.ReceiptLong to FinanceExtraPurple
                "UDHAAR" -> Icons.Default.Handshake to Color(0xFF7C3AED)
                "SAVINGS" -> Icons.Default.Savings to FinanceSavingsTeal
                else -> Icons.Default.ArrowUpward to FinanceExpenseRed
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.description.ifBlank { tx.category },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val dateFormatted = DateUtils.formatReadableDateWithDay(tx.date, tx.dayOfWeek)
                val timeFormatted = DateUtils.format12HourTime(tx.time)
                Text(
                    text = "${tx.fromAccount} -> ${tx.toAccount} • $dateFormatted $timeFormatted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val prefix = if (tx.type == "TRANSFER") "" else if (isPositiveForAccount) "+" else "-"
                Text(
                    text = "$prefix${DateUtils.formatCurrency(tx.amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPositiveForAccount) FinanceIncomeGreen else if (tx.type == "TRANSFER") MaterialTheme.colorScheme.onSurface else FinanceExpenseRed
                )
                Text(
                    text = "Bal: ${DateUtils.formatCurrency(tx.totalAccountBalance)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
