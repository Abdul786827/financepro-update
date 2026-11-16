package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserProfileEntity
import com.example.ui.theme.EmeraldPrimaryLight
import com.example.ui.theme.FinanceIncomeBg
import com.example.ui.theme.FinanceIncomeGreen
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.ImageUtils
import com.example.util.SecurityUtils

import android.content.Intent
import androidx.fragment.app.FragmentActivity
import com.example.config.AppVersionConfig
import com.example.data.local.AppChangelog
import com.example.util.BiometricSecurityManager
import com.example.util.UpdateCheckResult

@Composable
fun SettingsScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val profileBitmap = remember(userProfile?.profileImageBase64) {
        userProfile?.profileImageBase64?.let { ImageUtils.base64ToBitmap(it) }
    }

    var showEditProfileDialog by rememberSaveable { mutableStateOf(false) }
    var showChangePasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showResetConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showChangelogDialog by rememberSaveable { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateCheckResult by remember { mutableStateOf<UpdateCheckResult?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Profile Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(54.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (profileBitmap != null) {
                                    Image(
                                        bitmap = profileBitmap.asImageBitmap(),
                                        contentDescription = "Profile Picture",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = userProfile?.fullName ?: "Finance Pro User",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (userProfile?.isEmailVerified == true) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = FinanceIncomeBg
                                    ) {
                                        Text(
                                            text = "VERIFIED",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = FinanceIncomeGreen
                                        )
                                    }
                                }
                            }
                            Text(
                                text = userProfile?.email ?: "user@financepro.com",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = userProfile?.mobile ?: "+92 300 1234567",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { showEditProfileDialog = true },
                            modifier = Modifier.testTag("edit_profile_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Profile")
                        }
                    }
                }
            }
        }

        // Security & Authentication
        item {
            Text(
                text = "Security & Protection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text("Password Protection", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Change your login & encryption password", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { showChangePasswordDialog = true },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("change_password_button")
                        ) {
                            Text("Update", maxLines = 1, softWrap = false)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(14.dp))

                    val hasBiometricHardware = remember(context) {
                        BiometricSecurityManager.canAuthenticate(context)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fingerprint / Biometric Login", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (hasBiometricHardware) "Allow fingerprint sensor authentication on login screen" else "No enrolled fingerprint or hardware sensor detected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = (userProfile?.isBiometricEnabled ?: false) && hasBiometricHardware,
                            enabled = hasBiometricHardware,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    val activity = context as? FragmentActivity
                                    if (activity != null) {
                                        BiometricSecurityManager.authenticate(
                                            activity = activity,
                                            title = "Confirm Fingerprint",
                                            subtitle = "Touch sensor to enable fingerprint login",
                                            onSucceeded = {
                                                viewModel.setBiometricEnabled(true)
                                                Toast.makeText(context, "Fingerprint Login enabled", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, "Authentication failed: $err", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    } else {
                                        Toast.makeText(context, "FragmentActivity required for BiometricPrompt", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    viewModel.setBiometricEnabled(false)
                                    Toast.makeText(context, "Fingerprint Login disabled", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("biometric_settings_switch")
                        )
                    }
                }
            }
        }

        // Backup, Restore & Demo Reset
        item {
            Text(
                text = "Data Management & Backups",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Auto-Save & Persistent Storage", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("SQLite relational database active with atomic transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FinanceIncomeGreen)
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text("Reset All Data", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Clear transactions & records (optionally keeping your accounts at 0.00 or deleting everything).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = { showResetConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("reset_zero_button")
                        ) {
                            Text("Reset Data", maxLines = 1, softWrap = false)
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Account Session", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Sign out of your active account session and return to Login/Signup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.logout()
                                Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("logout_button")
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Log Out")
                        }
                    }
                }
            }
        }

        // About / App Info, Changelog & Update Checker
        item {
            Text(
                text = "About / App Info",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Finance Pro",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = AppVersionConfig.formattedVersion,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("settings_version_text")
                            )
                            Text(
                                text = "Database Schema v${AppVersionConfig.DB_SCHEMA_VERSION} • Timezone: Asia/Karachi (UTC+5)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "App Version",
                            tint = EmeraldPrimaryLight
                        )
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showChangelogDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(0.9f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("settings_whats_new_button")
                        ) {
                            Icon(Icons.Default.NewReleases, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                "What's New",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = {
                                if (!isCheckingUpdate) {
                                    isCheckingUpdate = true
                                    viewModel.checkForAppUpdate { result ->
                                        isCheckingUpdate = false
                                        updateCheckResult = result
                                    }
                                }
                            },
                            enabled = !isCheckingUpdate,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1.1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("settings_check_update_button")
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                if (isCheckingUpdate) "Checking..." else "Check for Update",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Profile Dialog with Avatar Photo & Camera Picker
    if (showEditProfileDialog) {
        userProfile?.let { prof ->
            var name by rememberSaveable { mutableStateOf(prof.fullName) }
            var email by rememberSaveable { mutableStateOf(prof.email) }
            var mobile by rememberSaveable { mutableStateOf(prof.mobile) }
            var currentImageBase64 by rememberSaveable { mutableStateOf(prof.profileImageBase64) }
            var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
            var showPhotoSourceSheet by rememberSaveable { mutableStateOf(false) }

            val previewBitmap = remember(currentImageBase64) {
                currentImageBase64.takeIf { it.isNotBlank() }?.let { ImageUtils.base64ToBitmap(it) }
            }

            // Photo Gallery Picker (Android Photo Picker - PickVisualMedia)
            val photoPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickVisualMedia()
            ) { uri: Uri? ->
                viewModel.setExternalPickerActive(false)
                if (uri != null) {
                    val base64 = ImageUtils.uriToBase64(context, uri, maxDimension = 512)
                    if (base64 != null) {
                        currentImageBase64 = base64
                    } else {
                        Toast.makeText(context, "Could not load selected photo", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Camera TakePicture contract with FileProvider URI
            val cameraLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.TakePicture()
            ) { success: Boolean ->
                viewModel.setExternalPickerActive(false)
                if (success && pendingCameraUri != null) {
                    val uri = Uri.parse(pendingCameraUri)
                    val base64 = ImageUtils.uriToBase64(context, uri, maxDimension = 512)
                    if (base64 != null) {
                        currentImageBase64 = base64
                    } else {
                        Toast.makeText(context, "Could not load captured photo", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Camera Permission Request
            val cameraPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    val tempUri = ImageUtils.createTempPictureUri(context)
                    if (tempUri != null) {
                        pendingCameraUri = tempUri.toString()
                        viewModel.setExternalPickerActive(true)
                        cameraLauncher.launch(tempUri)
                    } else {
                        Toast.makeText(context, "Could not initialize camera storage", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Camera permission is required to take a profile photo", Toast.LENGTH_SHORT).show()
                }
            }

            AlertDialog(
                onDismissRequest = {
                    showEditProfileDialog = false
                    showPhotoSourceSheet = false
                },
                title = { Text("Update User Profile") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Tappable Avatar with Camera/Edit Badge
                        Box(
                            contentAlignment = Alignment.BottomEnd,
                            modifier = Modifier
                                .size(90.dp)
                                .clickable {
                                    showPhotoSourceSheet = true
                                }
                                .testTag("profile_avatar_picker")
                        ) {
                            Surface(
                                modifier = Modifier.size(86.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (previewBitmap != null) {
                                        Image(
                                            bitmap = previewBitmap.asImageBitmap(),
                                            contentDescription = "Profile Picture Preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }
                                }
                            }

                            // Camera badge icon
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 3.dp,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Change Photo",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Photo Action Buttons (Choose / Camera / Remove)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.setExternalPickerActive(true)
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gallery", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Camera", fontSize = 12.sp)
                            }

                            if (currentImageBase64.isNotBlank()) {
                                TextButton(
                                    onClick = { currentImageBase64 = "" },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Remove", fontSize = 12.sp)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Full Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = mobile,
                            onValueChange = { mobile = it },
                            label = { Text("Mobile Number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateProfile(
                                fullName = name.trim(),
                                email = SecurityUtils.normalizeEmail(email),
                                mobile = mobile.trim(),
                                profileImageBase64 = currentImageBase64
                            )
                            showEditProfileDialog = false
                            Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditProfileDialog = false }) { Text("Cancel") }
                }
            )

            // Choose Source Dialog if Avatar was tapped
            if (showPhotoSourceSheet) {
                AlertDialog(
                    onDismissRequest = { showPhotoSourceSheet = false },
                    title = { Text("Select Photo Source") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ListItem(
                                headlineContent = { Text("Choose from Gallery") },
                                leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = EmeraldPrimaryLight) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showPhotoSourceSheet = false
                                        viewModel.setExternalPickerActive(true)
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                            )
                            ListItem(
                                headlineContent = { Text("Take New Photo with Camera") },
                                leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = EmeraldPrimaryLight) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showPhotoSourceSheet = false
                                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                    }
                            )
                            if (currentImageBase64.isNotBlank()) {
                                ListItem(
                                    headlineContent = { Text("Remove Current Photo", color = MaterialTheme.colorScheme.error) },
                                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentImageBase64 = ""
                                            showPhotoSourceSheet = false
                                        }
                                )
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showPhotoSourceSheet = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }

    // Change Password Dialog with verification and SHA-256 hashing
    if (showChangePasswordDialog) {
        var oldPassword by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var changePasswordError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("Change Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    changePasswordError?.let { err ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(8.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    OutlinedTextField(
                        value = oldPassword,
                        onValueChange = {
                            oldPassword = it
                            changePasswordError = null
                        },
                        label = { Text("Current Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            changePasswordError = null
                        },
                        label = { Text("New Password (Min 6 chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            changePasswordError = null
                        },
                        label = { Text("Confirm New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanOld = oldPassword.trim()
                        val cleanNew = newPassword.trim()
                        val cleanConfirm = confirmPassword.trim()

                        if (cleanOld.isEmpty()) {
                            changePasswordError = "Please enter your current password."
                            return@Button
                        }
                        if (cleanNew.length < 6) {
                            changePasswordError = "New password must be at least 6 characters."
                            return@Button
                        }
                        if (cleanNew != cleanConfirm) {
                            changePasswordError = "New passwords do not match."
                            return@Button
                        }

                        viewModel.changePassword(cleanOld, cleanNew) { success, message ->
                            if (success) {
                                showChangePasswordDialog = false
                                Toast.makeText(context, "Password updated successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                changePasswordError = message
                            }
                        }
                    }
                ) {
                    Text("Change Password")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) { Text("Cancel") }
            }
        )
    }


    // Reset All Data Confirm Dialog (2 modes)
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset All Data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose how you want to reset your ledger. Your choice only affects your active user account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            viewModel.resetAllDataToZero(deleteAccounts = false) {
                                showResetConfirmDialog = false
                                Toast.makeText(context, "Transactions cleared; accounts reset to 0.00", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("reset_keep_accounts_button")
                    ) {
                        Text("Reset Transactions Only (Keep Accounts at 0)", maxLines = 2)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.resetAllDataToZero(deleteAccounts = true) {
                                showResetConfirmDialog = false
                                Toast.makeText(context, "All accounts, transactions, and records deleted", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("reset_delete_everything_button")
                    ) {
                        Text("Delete Everything Including Accounts", maxLines = 2)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // What's New / Version History Dialog
    if (showChangelogDialog) {
        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NewReleases, contentDescription = null, tint = EmeraldPrimaryLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("What's New & History", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(AppChangelog.releases.size) { index ->
                        val release = AppChangelog.releases[index]
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "v${release.versionName} (build ${release.versionCode})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = release.releaseDate,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = release.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            release.changes.forEach { bullet ->
                                Text(
                                    text = "• $bullet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (index < AppChangelog.releases.lastIndex) {
                                Spacer(modifier = Modifier.height(4.dp))
                                HorizontalDivider(thickness = 0.5.dp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showChangelogDialog = false }) { Text("Close") }
            }
        )
    }

    // Update Check Result Dialog
    updateCheckResult?.let { result ->
        val validDownloadUrl = (result as? UpdateCheckResult.UpdateAvailable)?.info?.downloadUrl?.trim()?.takeIf {
            it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { updateCheckResult = null },
            title = {
                Text(
                    when (result) {
                        is UpdateCheckResult.UpdateAvailable -> "Update Available: v${result.info.latestVersionName}"
                        is UpdateCheckResult.UpToDate -> "You Are Up To Date"
                        is UpdateCheckResult.NotConfigured -> "App Version Check"
                        is UpdateCheckResult.Error -> "Could Not Check for Update"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                when (result) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Installed: ${AppVersionConfig.formattedVersion}\nLatest: Version ${result.info.latestVersionName} (build ${result.info.latestVersionCode})",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (result.info.changes.isNotEmpty()) {
                                Text("What's New:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                result.info.changes.forEach { item ->
                                    Text("• $item", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    is UpdateCheckResult.UpToDate -> {
                        Text("You are running the latest version (${AppVersionConfig.formattedVersion}).")
                    }
                    is UpdateCheckResult.NotConfigured -> {
                        Text("Installed: ${AppVersionConfig.formattedVersion}.\n${result.message}")
                    }
                    is UpdateCheckResult.Error -> {
                        Text(result.message)
                    }
                }
            },
            confirmButton = {
                if (validDownloadUrl != null) {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validDownloadUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Unable to open download link", Toast.LENGTH_SHORT).show()
                            }
                            updateCheckResult = null
                        },
                        modifier = Modifier.testTag("update_download_button")
                    ) {
                        Text("Download")
                    }
                } else {
                    Button(onClick = { updateCheckResult = null }) { Text("OK") }
                }
            },
            dismissButton = {
                if (validDownloadUrl != null) {
                    TextButton(onClick = { updateCheckResult = null }) { Text("Later") }
                }
            }
        )
    }
}
