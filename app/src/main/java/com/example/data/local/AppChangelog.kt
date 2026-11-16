package com.example.data.local

data class ChangelogRelease(
    val versionCode: Int,
    val versionName: String,
    val releaseDate: String,
    val title: String,
    val changes: List<String>
)

object AppChangelog {
    val releases: List<ChangelogRelease> = listOf(
        ChangelogRelease(
            versionCode = 2,
            versionName = "1.1.0",
            releaseDate = "Current Release",
            title = "Flexible Accounts, Data Isolation & Safe Upgrades",
            changes = listOf(
                "All accounts—including HBL, UBL, Mashreq, Sadapay, Easypaisa, and JazzCash—can now be edited, hidden, or deleted.",
                "Smart delete flow when an account has history: delete all its records cleanly or keep history labeled as 'Deleted Account'.",
                "New users now start with a clean slate (no pre-created accounts) and helpful Add Account empty states.",
                "Per-user database isolation so each user account keeps completely private accounts, transactions, Udhaar, and Savings.",
                "Automatic SQLite database backup before schema upgrades—existing balances and records are never wiped.",
                "Added App Version tracking, What's New history, and optional Update Checker in Settings."
            )
        ),
        ChangelogRelease(
            versionCode = 1,
            versionName = "1.0.0",
            releaseDate = "Initial Release",
            title = "Finance Pro Foundation",
            changes = listOf(
                "Personal Dashboard, Daily Statements, Monthly & Annual financial summaries.",
                "Udhaar (Given / Taken) money & item tracking linked to accounts.",
                "Dedicated Savings Pots with deposits, withdrawals, and target progress.",
                "Petrol & Extra expenses, Salary & Loan management, and Excel/PDF/CSV exports.",
                "Biometric Strong hardware fingerprint & PIN security."
            )
        )
    )

    fun latestRelease(): ChangelogRelease = releases.first()

    fun releaseForVersion(versionCode: Int): ChangelogRelease {
        return releases.firstOrNull { it.versionCode == versionCode } ?: releases.first()
    }
}
