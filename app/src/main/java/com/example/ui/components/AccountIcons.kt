package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Payments
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.model.ACCOUNT_TYPE_BANK
import com.example.data.model.ACCOUNT_TYPE_CASH
import com.example.data.model.ACCOUNT_TYPE_WALLET
import com.example.data.model.AccountEntity
import com.example.data.model.normalizeAccountType

/**
 * Centralized icon resolver for accounts across every screen in Finance Pro.
 * Strictly maps explicit accountType:
 *  - BANK   -> Bank building icon (never a wallet icon, regardless of bank name)
 *  - WALLET -> Wallet icon
 *  - CASH   -> Cash/Payments icon
 */
fun accountTypeIcon(accountType: String): ImageVector {
    return when (normalizeAccountType(accountType)) {
        ACCOUNT_TYPE_WALLET -> Icons.Default.AccountBalanceWallet
        ACCOUNT_TYPE_CASH -> Icons.Default.Payments
        else -> Icons.Default.AccountBalance
    }
}

fun accountTypeIcon(account: AccountEntity): ImageVector {
    return accountTypeIcon(account.accountType)
}

fun accountTypeLabel(accountType: String): String {
    return when (normalizeAccountType(accountType)) {
        ACCOUNT_TYPE_WALLET -> "Wallet"
        ACCOUNT_TYPE_CASH -> "Cash"
        else -> "Bank"
    }
}

fun accountTypeBadgeTag(accountType: String): String {
    return "[${accountTypeLabel(accountType)}]"
}

fun resolveAccountIconByName(
    accountName: String,
    accounts: List<AccountEntity>
): ImageVector? {
    val clean = accountName.trim()
    if (clean.isBlank()) return null
    val match = accounts.find { it.name.trim().equals(clean, ignoreCase = true) } ?: return null
    return accountTypeIcon(match.accountType)
}
