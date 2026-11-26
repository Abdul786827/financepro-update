package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.PakistanTaxConstants
import com.example.data.model.TaxDocumentEntity
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.ImageUtils
import com.example.util.TaxExportUtil
import java.io.File

@Composable
fun TaxDocumentsTab(
    viewModel: FinanceViewModel,
    taxYear: Int,
    isLocked: Boolean,
    documents: List<TaxDocumentEntity>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val yearDocs = remember(documents, taxYear) { documents.filter { it.taxYear == taxYear } }

    var docTitle by remember { mutableStateOf("") }
    var docCategory by remember { mutableStateOf(PakistanTaxConstants.DOCUMENT_CATEGORIES.first()) }
    var pendingCameraUri by remember { mutableStateOf<String?>(null) }
    var docToDelete by remember { mutableStateOf<TaxDocumentEntity?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        viewModel.setExternalPickerActive(false)
        if (uri != null) {
            viewModel.saveTaxDocumentFromUri(
                taxYear = taxYear,
                title = docTitle,
                category = docCategory,
                uri = uri,
                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                onSuccess = {
                    docTitle = ""
                    Toast.makeText(context, "Saved '${it.title}' to private tax vault", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        viewModel.setExternalPickerActive(false)
        if (uri != null) {
            viewModel.saveTaxDocumentFromUri(
                taxYear = taxYear,
                title = docTitle,
                category = docCategory,
                uri = uri,
                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                onSuccess = {
                    docTitle = ""
                    Toast.makeText(context, "Saved '${it.title}' to private tax vault", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        viewModel.setExternalPickerActive(false)
        if (success && pendingCameraUri != null) {
            viewModel.saveTaxDocumentFromUri(
                taxYear = taxYear,
                title = docTitle.ifBlank { "$docCategory Photo" },
                category = docCategory,
                uri = Uri.parse(pendingCameraUri),
                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                onSuccess = {
                    docTitle = ""
                    Toast.makeText(context, "Captured '${it.title}' to private tax vault", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = ImageUtils.createTempPictureUri(context)
            if (uri != null) {
                pendingCameraUri = uri.toString()
                viewModel.setExternalPickerActive(true)
                cameraLauncher.launch(uri)
            } else {
                Toast.makeText(context, "Could not create temporary camera file", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Camera permission required to scan documents", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderShared, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Private Tax Document Vault", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Stored inside app-private storage (not visible in public gallery)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!isLocked) {
                        OutlinedTextField(
                            value = docTitle,
                            onValueChange = { docTitle = it },
                            label = { Text("Document Title (optional)") },
                            placeholder = { Text("e.g. June Salary Slip, HBL Profit Certificate") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tax_doc_title_input")
                        )

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PakistanTaxConstants.DOCUMENT_CATEGORIES.forEach { cat ->
                                FilterChip(
                                    selected = docCategory == cat,
                                    onClick = { docCategory = cat },
                                    label = { Text(cat, fontSize = 11.sp) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.setExternalPickerActive(true)
                                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.testTag("tax_doc_pick_photo_button")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Photo", fontSize = 12.sp)
                            }
                            FilledTonalButton(
                                onClick = {
                                    viewModel.setExternalPickerActive(true)
                                    fileLauncher.launch(arrayOf("application/pdf", "image/*"))
                                },
                                modifier = Modifier.testTag("tax_doc_pick_pdf_button")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PDF / File", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier.testTag("tax_doc_camera_button")
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Camera", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        if (yearDocs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No documents attached for Tax Year $taxYear", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Attach salary slips, bank statements, withholding tax certificates, challans, or receipts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(yearDocs, key = { it.id }) { doc ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (doc.mimeType.contains("pdf", ignoreCase = true)) Icons.Default.PictureAsPdf else Icons.Default.Image,
                            contentDescription = doc.category,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(doc.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${doc.category} • ${(doc.fileSizeBytes / 1024L).coerceAtLeast(1L)} KB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            try {
                                val file = File(doc.filePath)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, doc.mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(viewIntent, "Open Document"))
                            } catch (_: Exception) {
                                Toast.makeText(context, "No viewer available for this file type", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Open document")
                        }
                        IconButton(onClick = {
                            try {
                                val file = File(doc.filePath)
                                TaxExportUtil.shareFile(context, file, doc.mimeType, doc.title, "Share Tax Document")
                            } catch (_: Exception) {
                                Toast.makeText(context, "Unable to share file", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share document")
                        }
                        if (!isLocked) {
                            IconButton(onClick = { docToDelete = doc }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete document", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    docToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            title = { Text("Delete Document?") },
            text = { Text("Remove '${doc.title}' permanently from private storage?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTaxDocument(doc.id,
                            onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                            onSuccess = { docToDelete = null }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { docToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
