package com.example.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DbBackupResult(
    val backupCreated: Boolean,
    val backupFilePath: String? = null,
    val detectedOldVersion: Int = 0
)

/**
 * Safely creates a snapshot copy of the SQLite database (plus WAL/SHM sidecars if present)
 * before Room executes any schema migration. Backups are never automatically deleted on error.
 */
object DatabaseBackupManager {

    private val _migrationError = MutableStateFlow<String?>(null)
    val migrationError: StateFlow<String?> = _migrationError.asStateFlow()

    private val _lastBackupPath = MutableStateFlow<String?>(null)
    val lastBackupPath: StateFlow<String?> = _lastBackupPath.asStateFlow()

    fun reportMigrationError(message: String) {
        _migrationError.value = message
    }

    fun clearMigrationError() {
        _migrationError.value = null
    }

    /**
     * Reads SQLite PRAGMA user_version directly from file header bytes 60..63 (big-endian 32-bit int)
     * without opening SQLite or triggering Room's migration hooks.
     */
    fun readSqliteUserVersion(dbFile: File): Int {
        if (!dbFile.exists() || dbFile.length() < 64L) return 0
        return try {
            RandomAccessFile(dbFile, "r").use { raf ->
                raf.seek(60L)
                val b0 = raf.read()
                val b1 = raf.read()
                val b2 = raf.read()
                val b3 = raf.read()
                if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) 0
                else (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
            }
        } catch (_: Exception) {
            0
        }
    }

    fun backupBeforeMigrationIfNeeded(
        context: Context,
        dbName: String,
        targetVersion: Int,
        timestampProvider: () -> String = {
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        }
    ): DbBackupResult {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) {
            return DbBackupResult(backupCreated = false, detectedOldVersion = 0)
        }

        val existingVersion = readSqliteUserVersion(dbFile)
        if (existingVersion <= 0 || existingVersion >= targetVersion) {
            return DbBackupResult(backupCreated = false, detectedOldVersion = existingVersion)
        }

        val backupDir = File(context.filesDir, "db_backups").apply {
            if (!exists()) mkdirs()
        }
        val stamp = timestampProvider()
        val baseName = dbName.removeSuffix(".db")
        val backupDbFile = File(backupDir, "${baseName}_v${existingVersion}_to_v${targetVersion}_$stamp.db")

        dbFile.copyTo(backupDbFile, overwrite = true)

        val walFile = File("${dbFile.absolutePath}-wal")
        if (walFile.exists()) {
            walFile.copyTo(File("${backupDbFile.absolutePath}-wal"), overwrite = true)
        }
        val shmFile = File("${dbFile.absolutePath}-shm")
        if (shmFile.exists()) {
            shmFile.copyTo(File("${backupDbFile.absolutePath}-shm"), overwrite = true)
        }

        _lastBackupPath.value = backupDbFile.absolutePath
        return DbBackupResult(
            backupCreated = true,
            backupFilePath = backupDbFile.absolutePath,
            detectedOldVersion = existingVersion
        )
    }

    fun listBackups(context: Context): List<File> {
        val backupDir = File(context.filesDir, "db_backups")
        if (!backupDir.exists()) return emptyList()
        return backupDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
}
