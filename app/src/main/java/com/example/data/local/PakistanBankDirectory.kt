package com.example.data.local

import com.example.data.model.ACCOUNT_TYPE_BANK
import com.example.data.model.ACCOUNT_TYPE_CASH
import com.example.data.model.ACCOUNT_TYPE_WALLET

data class BankDirectoryItem(
    val name: String,
    val accountType: String,
    val categoryGroup: String
)

/**
 * Dedicated built-in directory of Pakistani Commercial Banks, Digital & Microfinance Banks,
 * Mobile Wallets, and Cash presets. Stored here so entries can be updated cleanly without
 * duplicating existing built-in app accounts.
 */
object PakistanBankDirectory {

    val commercialBanks: List<BankDirectoryItem> = listOf(
        BankDirectoryItem("HBL", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("UBL", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("MCB Bank", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Allied Bank (ABL)", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Bank Alfalah", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Meezan Bank", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Askari Bank", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Bank Al Habib", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Faysal Bank", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Standard Chartered Pakistan", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Soneri Bank", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Habib Metropolitan Bank", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Bank of Punjab", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Bank of Khyber", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Sindh Bank", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Summit Bank", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Silk Bank", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("JS Bank", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Samba Bank", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Citibank Pakistan", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Deutsche Bank Pakistan", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("National Bank of Pakistan (NBP)", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("First Women Bank", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Zarai Taraqiati Bank (ZTBL)", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("SME Bank", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Industrial Development Bank (IDBP)", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Punjab Provincial Cooperative Bank", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Mashreq Bank", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Bank of China", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Industrial and Commercial Bank of China (ICBC)", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Al Baraka Bank Pakistan", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("BankIslami Pakistan", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Dubai Islamic Bank Pakistan", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("MCB Islamic Bank", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Habib Bank AG Zurich", ACCOUNT_TYPE_BANK, "Commercial Bank"),
        BankDirectoryItem("Khushhali Microfinance Bank", ACCOUNT_TYPE_BANK, "Commercial Bank")
    )

    val digitalAndMicrofinanceBanks: List<BankDirectoryItem> = listOf(
        BankDirectoryItem("Mobilink Microfinance Bank (Jazz)", ACCOUNT_TYPE_BANK, "Digital & Microfinance Bank"),
        BankDirectoryItem("Telenor Microfinance Bank (Easypaisa)", ACCOUNT_TYPE_BANK, "Digital & Microfinance Bank"),
        BankDirectoryItem("U Microfinance Bank (U Paisa)", ACCOUNT_TYPE_BANK, "Digital & Microfinance Bank"),
        BankDirectoryItem("FINCA Microfinance Bank", ACCOUNT_TYPE_BANK, "Digital & Microfinance Bank"),
        BankDirectoryItem("NRSP Microfinance Bank", ACCOUNT_TYPE_BANK, "Digital & Microfinance Bank"),
        BankDirectoryItem("Apna Microfinance Bank", ACCOUNT_TYPE_BANK, "Digital & Microfinance Bank"),
        BankDirectoryItem("Advans Pakistan Microfinance Bank", ACCOUNT_TYPE_BANK, "Digital & Microfinance Bank"),
        BankDirectoryItem("Kashf Microfinance Bank", ACCOUNT_TYPE_BANK, "Digital & Microfinance Bank"),
        BankDirectoryItem("Sada Pay / SadaPay", ACCOUNT_TYPE_BANK, "Digital & Microfinance Bank"),
        BankDirectoryItem("NayaPay", ACCOUNT_TYPE_BANK, "Digital & Microfinance Bank"),
        BankDirectoryItem("Raast-linked accounts", ACCOUNT_TYPE_BANK, "Digital & Microfinance Bank"),
        BankDirectoryItem("UPaisa", ACCOUNT_TYPE_BANK, "Digital & Microfinance Bank"),
        BankDirectoryItem("Zindigi (JS Bank)", ACCOUNT_TYPE_BANK, "Digital & Microfinance Bank"),
        BankDirectoryItem("Mashreq Neo Pakistan", ACCOUNT_TYPE_BANK, "Digital & Microfinance Bank"),
        BankDirectoryItem("Allied Bank Ubank", ACCOUNT_TYPE_BANK, "Digital & Microfinance Bank")
    )

    val wallets: List<BankDirectoryItem> = listOf(
        BankDirectoryItem("JazzCash", ACCOUNT_TYPE_WALLET, "Wallet"),
        BankDirectoryItem("Easypaisa", ACCOUNT_TYPE_WALLET, "Wallet"),
        BankDirectoryItem("SadaPay", ACCOUNT_TYPE_WALLET, "Wallet"),
        BankDirectoryItem("NayaPay", ACCOUNT_TYPE_WALLET, "Wallet"),
        BankDirectoryItem("UPaisa", ACCOUNT_TYPE_WALLET, "Wallet"),
        BankDirectoryItem("Zindigi", ACCOUNT_TYPE_WALLET, "Wallet"),
        BankDirectoryItem("Konnect by HBL", ACCOUNT_TYPE_WALLET, "Wallet"),
        BankDirectoryItem("Finja", ACCOUNT_TYPE_WALLET, "Wallet"),
        BankDirectoryItem("Keenu", ACCOUNT_TYPE_WALLET, "Wallet"),
        BankDirectoryItem("SimSim", ACCOUNT_TYPE_WALLET, "Wallet"),
        BankDirectoryItem("PayMax", ACCOUNT_TYPE_WALLET, "Wallet")
    )

    val cashPresets: List<BankDirectoryItem> = listOf(
        BankDirectoryItem("Cash in Hand", ACCOUNT_TYPE_CASH, "Cash"),
        BankDirectoryItem("Petty Cash", ACCOUNT_TYPE_CASH, "Cash"),
        BankDirectoryItem("Home Locker Cash", ACCOUNT_TYPE_CASH, "Cash"),
        BankDirectoryItem("Office Cash", ACCOUNT_TYPE_CASH, "Cash")
    )

    val allEntries: List<BankDirectoryItem> = (
        commercialBanks + digitalAndMicrofinanceBanks + wallets + cashPresets
    ).distinctBy { "${it.name.trim().lowercase()}|${it.accountType}" }

    /**
     * Returns filtered directory items for the given account type, excluding any account name
     * that already exists (case-insensitive) in the user's managed accounts list.
     */
    fun getOptionsForType(
        accountType: String,
        existingAccountNames: Set<String>,
        excludeOwnName: String? = null
    ): List<BankDirectoryItem> {
        val normalizedExisting = existingAccountNames
            .map { it.trim().lowercase() }
            .filter { own -> excludeOwnName == null || !own.equals(excludeOwnName.trim(), ignoreCase = true) }
            .toSet()

        val candidates = when (accountType.uppercase()) {
            ACCOUNT_TYPE_WALLET -> wallets + digitalAndMicrofinanceBanks
            ACCOUNT_TYPE_CASH -> cashPresets
            else -> commercialBanks + digitalAndMicrofinanceBanks
        }

        return candidates
            .distinctBy { it.name.trim().lowercase() }
            .filter { item -> item.name.trim().lowercase() !in normalizedExisting }
    }
}
