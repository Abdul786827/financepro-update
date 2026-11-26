package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.EmeraldPrimaryLight
import com.example.ui.theme.FinanceExpenseRed
import com.example.ui.theme.FinanceIncomeGreen
import com.example.util.DateUtils
import com.example.util.PakistanTaxYearUtils

@Composable
fun TaxSummaryTab(
    taxYearEntity: TaxYearEntity,
    summary: TaxYearSummary,
    slabs: List<TaxSlabItem>,
    onEditSlabs: () -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit,
    onExportDocsZip: () -> Unit,
    onSharePackage: () -> Unit,
    onCreateEncryptedBackup: () -> Unit,
    onRestoreEncryptedBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estimatedGrossTax = remember(summary.totalIncome, slabs) {
        PakistanTaxYearUtils.estimateTaxFromUserSlabs(summary.totalIncome, slabs)
    }
    val totalTaxAlreadyPaid = summary.totalWithholdingTax + summary.totalDirectTaxPaid
    val estimatedNetBalance = estimatedGrossTax - totalTaxAlreadyPaid

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // Record-Keeping Disclaimer Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tax_disclaimer_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Disclaimer",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = TAX_DISCLAIMER_TEXT,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Return Filing Checklist Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tax_checklist_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val completedCount = summary.checklist.count { it.completed }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Return Checklist", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "$completedCount / ${summary.checklist.size} Ready",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldPrimaryLight,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LinearProgressIndicator(
                        progress = { summary.checklistProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )

                    summary.checklist.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (item.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (item.completed) FinanceIncomeGreen else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Key Totals & Income by Category
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Tax Year ${taxYearEntity.taxYear} Totals",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    SummaryMetricRow("Total Income", summary.totalIncome, FinanceIncomeGreen)
                    SummaryMetricRow("Withholding Tax (At Source)", summary.totalWithholdingTax, MaterialTheme.colorScheme.primary)
                    SummaryMetricRow("Direct Tax Paid (Challan/CPR)", summary.totalDirectTaxPaid, MaterialTheme.colorScheme.primary)
                    SummaryMetricRow("Deductions & Tax Credits", summary.totalDeductions, MaterialTheme.colorScheme.secondary)
                    SummaryMetricRow("Tax-Relevant Expenses", summary.totalTaxExpenses, FinanceExpenseRed)
                    SummaryMetricRow("Opening -> Closing Net Wealth", summary.closingNetWealth, MaterialTheme.colorScheme.primary)

                    if (summary.incomeByCategory.isNotEmpty()) {
                        HorizontalDivider(thickness = 0.5.dp)
                        Text("Income Breakdown", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        summary.incomeByCategory.forEach { (cat, amt) ->
                            SummaryMetricRow(cat, amt, MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        // Optional User-Editable Slab Estimator Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Custom Tax Slab Estimator", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                if (slabs.isEmpty()) "No slabs entered. Add your own rates to preview an estimate." else "${slabs.size} custom slab(s) active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = onEditSlabs,
                            modifier = Modifier.testTag("tax_edit_slabs_button")
                        ) {
                            Text("Edit Slabs", fontSize = 12.sp)
                        }
                    }

                    if (slabs.isNotEmpty()) {
                        HorizontalDivider(thickness = 0.5.dp)
                        SummaryMetricRow("Estimated Tax (Per Your Slabs)", estimatedGrossTax, MaterialTheme.colorScheme.onSurface)
                        SummaryMetricRow("Less: WHT + Direct Tax Paid", totalTaxAlreadyPaid, FinanceIncomeGreen)
                        SummaryMetricRow(
                            if (estimatedNetBalance >= 0) "Est. Remaining Payable" else "Est. Excess / Refundable",
                            kotlin.math.abs(estimatedNetBalance),
                            if (estimatedNetBalance >= 0) FinanceExpenseRed else FinanceIncomeGreen
                        )
                    }
                }
            }
        }

        // FBR-Order Export & Share Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Export & Share Return Package", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Exported in FBR return section order (Income, Withholding, Direct Tax, Deductions, Wealth) with masked CNIC/NTN.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onExportPdf,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tax_export_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF", fontSize = 12.sp)
                        }
                        FilledTonalButton(
                            onClick = onExportCsv,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tax_export_csv_button")
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excel CSV", fontSize = 12.sp)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onExportDocsZip,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tax_export_zip_button")
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Docs ZIP (${summary.documentCount})", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onSharePackage,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tax_share_package_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share All", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Encrypted Backup & Restore Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Encrypted Tax Backup (.fptax)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Create a passphrase-encrypted backup file for Google Drive or device storage, or restore an existing .fptax backup.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onCreateEncryptedBackup,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tax_backup_export_button")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Encrypted Backup", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = onRestoreEncryptedBackup,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tax_backup_restore_button")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restore Backup", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricRow(
    label: String,
    amount: Double,
    valueColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            DateUtils.formatCurrency(amount),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
