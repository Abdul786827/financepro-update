package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import com.example.data.model.SavingsEntryEntity
import com.example.data.model.TransactionEntity
import com.example.ui.components.AddTransactionDialog
import com.example.ui.components.SavingsEntryDialog
import com.example.ui.components.ScrollableFinanceBottomBar
import com.example.ui.components.buildFinanceBottomNavItems
import com.example.ui.navigation.AppScreen
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.FinanceViewModel

import androidx.fragment.app.FragmentActivity
import com.example.config.AppVersionConfig
import com.example.data.local.AppChangelog
import com.example.ui.components.AccountFormDialog
import com.example.util.BiometricSecurityManager
import com.example.util.DatabaseBackupManager

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

class MainActivity : FragmentActivity() {

    private val viewModel: FinanceViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
                val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
                val isExternalPickerActive by viewModel.isExternalPickerActive.collectAsStateWithLifecycle()

                // Session lock: Persisted across configuration changes and picker launches
                var isSessionUnlocked by rememberSaveable { mutableStateOf(false) }
                var isTaxUnlocked by rememberSaveable { mutableStateOf(false) }
                var lastBackgroundTime by rememberSaveable { mutableLongStateOf(0L) }
                val lifecycleOwner = LocalLifecycleOwner.current

                LaunchedEffect(isLoggedIn, isSessionUnlocked) {
                    if (!isLoggedIn || !isSessionUnlocked) {
                        isTaxUnlocked = false
                    }
                }

                // Function to prompt biometric unlock automatically
                val promptBiometricAutoUnlock = {
                    if (isLoggedIn && !isSessionUnlocked && userProfile?.isBiometricEnabled == true && BiometricSecurityManager.canAuthenticate(this@MainActivity)) {
                        BiometricSecurityManager.authenticate(
                            activity = this@MainActivity,
                            title = "Unlock Finance Pro",
                            subtitle = "Touch fingerprint sensor to unlock",
                            onSucceeded = {
                                isSessionUnlocked = true
                            },
                            onError = {
                                // Keep locked on failure/cancel, allowing fallback to email/password
                            }
                        )
                    }
                }

                // Automatic prompt on initial open
                LaunchedEffect(isLoggedIn, userProfile?.isBiometricEnabled) {
                    if (isLoggedIn && !isSessionUnlocked && userProfile?.isBiometricEnabled == true) {
                        promptBiometricAutoUnlock()
                    }
                }

                // Auto-lock: if backgrounded/minimized for >= 1 minute (60,000ms), lock app
                // CRITICAL FIX: Skip locking when an external picker (camera / photo gallery) launched by the app is active
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> {
                                if (!viewModel.isExternalPickerActive.value) {
                                    lastBackgroundTime = System.currentTimeMillis()
                                    isTaxUnlocked = false
                                }
                            }
                            Lifecycle.Event.ON_START -> {
                                if (viewModel.isExternalPickerActive.value) {
                                    // Returned from external camera or photo gallery picker, do NOT lock
                                    lastBackgroundTime = 0L
                                } else if (lastBackgroundTime > 0L) {
                                    val elapsed = System.currentTimeMillis() - lastBackgroundTime
                                    if (elapsed >= 60_000L) {
                                        isSessionUnlocked = false
                                        isTaxUnlocked = false
                                        // Prompt biometric immediately upon return
                                        promptBiometricAutoUnlock()
                                    }
                                    lastBackgroundTime = 0L
                                }
                            }
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                var currentScreen by rememberSaveable { mutableStateOf(AppScreen.DASHBOARD) }
                var showAddDialog by rememberSaveable { mutableStateOf(false) }
                var dialogInitialType by rememberSaveable { mutableStateOf("INCOME") }
                var transactionToEdit by remember { mutableStateOf<TransactionEntity?>(null) }
                var savingsEntryToEdit by remember { mutableStateOf<SavingsEntryEntity?>(null) }
                var showTransferDialog by rememberSaveable { mutableStateOf(false) }
                var showAddAccountDialog by rememberSaveable { mutableStateOf(false) }
                var showWhatsNewDialog by rememberSaveable { mutableStateOf(false) }

                val accounts by viewModel.accounts.collectAsStateWithLifecycle()
                val categories by viewModel.categories.collectAsStateWithLifecycle()
                val savingsPots by viewModel.savingsPots.collectAsStateWithLifecycle()
                val savingsEntries by viewModel.savingsEntries.collectAsStateWithLifecycle()
                val migrationError by DatabaseBackupManager.migrationError.collectAsStateWithLifecycle()
                val lastBackupPath by DatabaseBackupManager.lastBackupPath.collectAsStateWithLifecycle()

                LaunchedEffect(isLoggedIn, isSessionUnlocked) {
                    if (isLoggedIn && isSessionUnlocked && AppVersionConfig.shouldShowWhatsNew(this@MainActivity)) {
                        showWhatsNewDialog = true
                    }
                }

                if (!isLoggedIn || !isSessionUnlocked) {
                    AuthScreen(
                        viewModel = viewModel,
                        initialMode = AuthMode.LOGIN,
                        onAuthSuccess = {
                            isSessionUnlocked = true
                            currentScreen = AppScreen.DASHBOARD
                        }
                    )
                } else {
                    // Intercept back button to return to dashboard if on sub-screen
                    BackHandler(enabled = currentScreen != AppScreen.DASHBOARD) {
                        currentScreen = AppScreen.DASHBOARD
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            if (currentScreen != AppScreen.ADD_TRANSACTION) {
                                TopAppBar(
                                    title = {
                                        Column {
                                            Text(
                                                text = "Finance Pro",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "${currentScreen.title} • ${userProfile?.fullName?.ifBlank { "User" } ?: "User"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    actions = {
                                        IconButton(
                                            onClick = { currentScreen = AppScreen.UDHAAR },
                                            modifier = Modifier.testTag("topbar_udhaar_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Handshake,
                                                contentDescription = "Udhaar (Lend & Borrow)",
                                                tint = if (currentScreen == AppScreen.UDHAAR) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        IconButton(
                                            onClick = { currentScreen = AppScreen.SALARY_LOANS },
                                            modifier = Modifier.testTag("topbar_salary_loans_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Payments,
                                                contentDescription = "Salary & Loans",
                                                tint = if (currentScreen == AppScreen.SALARY_LOANS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        IconButton(
                                            onClick = { currentScreen = AppScreen.REPORTS },
                                            modifier = Modifier.testTag("topbar_reports_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PictureAsPdf,
                                                contentDescription = "Reports & Export",
                                                tint = if (currentScreen == AppScreen.REPORTS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        IconButton(
                                            onClick = { currentScreen = AppScreen.SETTINGS },
                                            modifier = Modifier.testTag("topbar_settings_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccountCircle,
                                                contentDescription = "Profile & Settings",
                                                tint = if (currentScreen == AppScreen.SETTINGS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                isSessionUnlocked = false
                                                viewModel.logout()
                                            },
                                            modifier = Modifier.testTag("topbar_logout_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Logout,
                                                contentDescription = "Log Out",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                            }
                        },
                        bottomBar = {
                            if (currentScreen != AppScreen.ADD_TRANSACTION) {
                                ScrollableFinanceBottomBar(
                                    currentScreen = currentScreen,
                                    items = buildFinanceBottomNavItems(showTax = userProfile?.isTaxpayerEnabled == true),
                                    onSelectScreen = { currentScreen = it }
                                )
                            }
                        },
                        floatingActionButton = {
                            if (currentScreen != AppScreen.ADD_TRANSACTION) {
                                FloatingActionButton(
                                    onClick = {
                                        transactionToEdit = null
                                        dialogInitialType = "EXPENSE"
                                        currentScreen = AppScreen.ADD_TRANSACTION
                                    },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.testTag("fab_add_transaction")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (currentScreen) {
                                AppScreen.ADD_TRANSACTION -> AddTransactionScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = {
                                        transactionToEdit = null
                                        currentScreen = AppScreen.DAILY_STATEMENT
                                    },
                                    initialType = dialogInitialType,
                                    transactionToEdit = transactionToEdit
                                )

                                AppScreen.DASHBOARD -> DashboardScreen(
                                    viewModel = viewModel,
                                    onNavigateToStatement = { currentScreen = AppScreen.DAILY_STATEMENT },
                                    onNavigateToAccounts = { currentScreen = AppScreen.ACCOUNTS },
                                    onOpenAddAccount = { showAddAccountDialog = true },
                                    onQuickAdd = { type ->
                                        if (accounts.isEmpty()) {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Add your first bank or wallet account before recording a transaction",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            showAddAccountDialog = true
                                        } else if (type == "TRANSFER") {
                                            if (accounts.size < 2) {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Add at least 2 accounts to transfer between them",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                showAddAccountDialog = true
                                            } else {
                                                showTransferDialog = true
                                            }
                                        } else {
                                            transactionToEdit = null
                                            dialogInitialType = type
                                            currentScreen = AppScreen.ADD_TRANSACTION
                                        }
                                    },
                                    onNavigateToUdhaar = { currentScreen = AppScreen.UDHAAR },
                                    onNavigateToSavings = { currentScreen = AppScreen.SAVINGS }
                                )

                                AppScreen.DAILY_STATEMENT -> DailyStatementScreen(
                                    viewModel = viewModel,
                                    onAddTransaction = {
                                        transactionToEdit = null
                                        dialogInitialType = "EXPENSE"
                                        currentScreen = AppScreen.ADD_TRANSACTION
                                    },
                                    onEditTransaction = { tx ->
                                        if (tx.type == "SAVINGS") {
                                            val linked = savingsEntries.find { it.transactionId == tx.id }
                                            if (linked != null) {
                                                savingsEntryToEdit = linked
                                            } else {
                                                currentScreen = AppScreen.SAVINGS
                                            }
                                        } else {
                                            transactionToEdit = tx
                                            dialogInitialType = tx.type
                                            currentScreen = AppScreen.ADD_TRANSACTION
                                        }
                                    }
                                )

                                AppScreen.ACCOUNTS -> AccountsScreen(
                                    viewModel = viewModel,
                                    onOpenTransfer = { showTransferDialog = true }
                                )

                                AppScreen.PETROL_EXTRA -> PetrolExtraScreen(
                                    viewModel = viewModel,
                                    onAddPetrol = {
                                        transactionToEdit = null
                                        dialogInitialType = "PETROL"
                                        currentScreen = AppScreen.ADD_TRANSACTION
                                    },
                                    onAddExtra = {
                                        transactionToEdit = null
                                        dialogInitialType = "EXTRA_EXPENSE"
                                        currentScreen = AppScreen.ADD_TRANSACTION
                                    }
                                )

                                AppScreen.STATEMENTS -> MonthlyAnnualScreen(
                                    viewModel = viewModel
                                )

                                AppScreen.SALARY_LOANS -> SalaryLoansScreen(
                                    viewModel = viewModel
                                )

                                AppScreen.UDHAAR -> UdhaarScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentScreen = AppScreen.DASHBOARD }
                                )

                                AppScreen.SAVINGS -> SavingsScreen(
                                    viewModel = viewModel
                                )

                                AppScreen.TAX -> TaxScreen(
                                    viewModel = viewModel,
                                    isTaxUnlocked = isTaxUnlocked,
                                    onUnlockTax = { isTaxUnlocked = true },
                                    onEditLinkedTransaction = { tx ->
                                        transactionToEdit = tx
                                        dialogInitialType = tx.type
                                        currentScreen = AppScreen.ADD_TRANSACTION
                                    }
                                )

                                AppScreen.REPORTS -> ReportsExportScreen(
                                    viewModel = viewModel
                                )

                                AppScreen.SETTINGS -> SettingsScreen(
                                    viewModel = viewModel
                                )
                            }
                        }

                        // Add or Edit Transaction Dialog
                        if (showAddDialog) {
                            AddTransactionDialog(
                                initialType = dialogInitialType,
                                transactionToEdit = transactionToEdit,
                                accounts = accounts,
                                categories = categories,
                                onDismiss = {
                                    showAddDialog = false
                                    transactionToEdit = null
                                },
                                onSave = { type, amount, date, time, from, to, cat, comments, extra, liters, rate ->
                                    val editing = transactionToEdit
                                    if (editing != null) {
                                        viewModel.updateTransaction(
                                            oldTx = editing,
                                            date = date,
                                            time = time,
                                            type = type,
                                            amount = amount,
                                            fromAccount = from,
                                            toAccount = to,
                                            category = cat,
                                            comments = comments,
                                            extraItem = extra,
                                            petrolLiters = liters,
                                            petrolRate = rate
                                        ) {
                                            showAddDialog = false
                                            transactionToEdit = null
                                        }
                                    } else {
                                        viewModel.addTransaction(
                                            date = date,
                                            time = time,
                                            type = type,
                                            amount = amount,
                                            fromAccount = from,
                                            toAccount = to,
                                            category = cat,
                                            comments = comments,
                                            extraItem = extra,
                                            petrolLiters = liters,
                                            petrolRate = rate
                                        ) {
                                            showAddDialog = false
                                        }
                                    }
                                }
                            )
                        }

                        // Edit Savings entry tapped from Daily Statement
                        savingsEntryToEdit?.let { entry ->
                            SavingsEntryDialog(
                                pots = savingsPots,
                                accounts = accounts,
                                entryToEdit = entry,
                                onDismiss = { savingsEntryToEdit = null },
                                onSave = { potId, type, amount, accountName, date, time, note, onError ->
                                    viewModel.updateSavingsEntry(
                                        entryId = entry.id,
                                        potId = potId,
                                        type = type,
                                        amount = amount,
                                        accountName = accountName,
                                        date = date,
                                        time = time,
                                        note = note,
                                        onError = onError,
                                        onSuccess = {
                                            savingsEntryToEdit = null
                                            Toast.makeText(this@MainActivity, "Savings entry updated", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            )
                        }

                        // Quick Transfer Dialog
                        if (showTransferDialog) {
                            TransferDialog(
                                accounts = accounts,
                                onDismiss = { showTransferDialog = false },
                                onTransfer = { from, to, amount, comments ->
                                    viewModel.addTransaction(
                                        type = "TRANSFER",
                                        amount = amount,
                                        fromAccount = from,
                                        toAccount = to,
                                        category = "Account Transfer",
                                        comments = comments
                                    ) {
                                        showTransferDialog = false
                                    }
                                }
                            )
                        }

                        // Global Add Account Dialog (triggered from Dashboard empty state / quick action guard)
                        if (showAddAccountDialog) {
                            AccountFormDialog(
                                existingAccounts = accounts,
                                accountToEdit = null,
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
                                            showAddAccountDialog = false
                                            Toast.makeText(this@MainActivity, "${created.name} added", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            )
                        }

                        // One-time What's New dialog after upgrade / first open of new version
                        if (showWhatsNewDialog) {
                            val latestRelease = AppChangelog.releases.firstOrNull()
                            AlertDialog(
                                onDismissRequest = {
                                    AppVersionConfig.markCurrentVersionSeen(this@MainActivity)
                                    showWhatsNewDialog = false
                                },
                                title = {
                                    Text(
                                        text = "What's New in Finance Pro v${AppVersionConfig.versionName}",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Column(
                                        modifier = Modifier.verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (latestRelease != null) {
                                            Text(
                                                text = "${latestRelease.title} (Build ${latestRelease.versionCode})",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            latestRelease.changes.forEach { bullet ->
                                                Text(
                                                    text = "• $bullet",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            AppVersionConfig.markCurrentVersionSeen(this@MainActivity)
                                            showWhatsNewDialog = false
                                        },
                                        modifier = Modifier.testTag("whats_new_got_it_button")
                                    ) {
                                        Text("Got It")
                                    }
                                }
                            )
                        }
                    }
                }

                // Non-destructive database migration failure warning (always shown above content, preserves backups)
                migrationError?.let { errMsg ->
                    AlertDialog(
                        onDismissRequest = { DatabaseBackupManager.clearMigrationError() },
                        title = {
                            Text("Database Update Protection", fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "A problem occurred while upgrading your local database. Your existing data and automatic backup were preserved untouched.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = errMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                lastBackupPath?.let { path ->
                                    Text(
                                        text = "Backup saved at: $path",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = { DatabaseBackupManager.clearMigrationError() }) {
                                Text("OK")
                            }
                        }
                    )
                }
            }
        }
    }
}
