package com.example.util

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles encryption/decryption for sensitive taxpayer identifiers (CNIC, NTN)
 * and portable passphrase-encrypted backup archives.
 * Never logs sensitive plaintexts or keys.
 */
object TaxCryptoHelper {

    private const val KEYSTORE_NAME = "AndroidKeyStore"
    private const val TAX_KEY_ALIAS = "FinanceProTaxSensitiveKey"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val ENC_PREFIX = "enc:v1:"

    private val BACKUP_MAGIC = byteArrayOf(0x46, 0x50, 0x54, 0x41, 0x58, 0x31) // "FPTAX1"
    private const val PBKDF2_SALT_BYTES = 16
    private const val PBKDF2_ITERATIONS = 65_536

    @Synchronized
    private fun getOrCreateAppKey(context: Context): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_NAME).apply { load(null) }
            if (keyStore.containsAlias(TAX_KEY_ALIAS)) {
                val entry = keyStore.getEntry(TAX_KEY_ALIAS, null)
                if (entry is KeyStore.SecretKeyEntry) {
                    return entry.secretKey
                }
            }
            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_NAME)
            val specBuilder = KeyGenParameterSpec.Builder(
                TAX_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                specBuilder.setInvalidatedByBiometricEnrollment(false)
            }
            keyGen.init(specBuilder.build())
            return keyGen.generateKey()
        } catch (_: Exception) {
            // Fallback for JVM unit tests / devices without Keystore provider
            return getOrCreatePrivateFallbackKey(context)
        }
    }

    @Synchronized
    private fun getOrCreatePrivateFallbackKey(context: Context): SecretKey {
        val keyFile = File(context.applicationContext.filesDir, ".tax_keystore_fallback")
        val rawBytes = if (keyFile.exists() && keyFile.length() == 32L) {
            keyFile.readBytes()
        } else {
            ByteArray(32).also { bytes ->
                SecureRandom().nextBytes(bytes)
                keyFile.parentFile?.mkdirs()
                keyFile.writeBytes(bytes)
            }
        }
        return SecretKeySpec(rawBytes, "AES")
    }

    fun encryptSensitive(context: Context, plaintext: String): String {
        val clean = plaintext.trim()
        if (clean.isEmpty()) return ""
        return try {
            val key = getOrCreateAppKey(context)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            ENC_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            val fallbackKey = getOrCreatePrivateFallbackKey(context)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, fallbackKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            ENC_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        }
    }

    fun decryptSensitive(context: Context, storedValue: String): String {
        val trimmed = storedValue.trim()
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith(ENC_PREFIX)) {
            return trimmed
        }
        val payload = Base64.decode(trimmed.removePrefix(ENC_PREFIX), Base64.NO_WRAP)
        if (payload.size <= GCM_IV_BYTES) return ""
        val iv = payload.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = payload.copyOfRange(GCM_IV_BYTES, payload.size)

        return try {
            val key = getOrCreateAppKey(context)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            try {
                val fallbackKey = getOrCreatePrivateFallbackKey(context)
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, fallbackKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
        }
    }

    fun maskCnic(rawCnic: String): String {
        val trimmed = rawCnic.trim()
        if (trimmed.isEmpty()) return "Not set"
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length == 13) {
            return "${digits.substring(0, 5)}-•••••••-${digits.last()}"
        }
        if (trimmed.length <= 4) return "••••"
        return "••••" + trimmed.takeLast(4)
    }

    fun maskNtn(rawNtn: String): String {
        val trimmed = rawNtn.trim()
        if (trimmed.isEmpty()) return "Not set"
        if (trimmed.length <= 3) return "•••"
        return "•".repeat((trimmed.length - 3).coerceAtLeast(4)) + trimmed.takeLast(3)
    }

    fun isEncryptedValue(storedValue: String): Boolean {
        return storedValue.startsWith(ENC_PREFIX)
    }

    fun encryptBackupWithPassphrase(plainBytes: ByteArray, passphrase: String): ByteArray {
        val cleanPass = passphrase.trim()
        require(cleanPass.length >= 4) { "Backup passphrase must be at least 4 characters." }

        val random = SecureRandom()
        val salt = ByteArray(PBKDF2_SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(GCM_IV_BYTES).also { random.nextBytes(it) }
        val key = derivePassphraseKey(cleanPass, salt)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plainBytes)

        val out = ByteArray(BACKUP_MAGIC.size + salt.size + iv.size + encrypted.size)
        var offset = 0
        System.arraycopy(BACKUP_MAGIC, 0, out, offset, BACKUP_MAGIC.size)
        offset += BACKUP_MAGIC.size
        System.arraycopy(salt, 0, out, offset, salt.size)
        offset += salt.size
        System.arraycopy(iv, 0, out, offset, iv.size)
        offset += iv.size
        System.arraycopy(encrypted, 0, out, offset, encrypted.size)
        return out
    }

    fun decryptBackupWithPassphrase(encryptedBytes: ByteArray, passphrase: String): ByteArray {
        val cleanPass = passphrase.trim()
        require(cleanPass.isNotEmpty()) { "Passphrase is required to restore this backup." }
        val minSize = BACKUP_MAGIC.size + PBKDF2_SALT_BYTES + GCM_IV_BYTES + 16
        if (encryptedBytes.size < minSize) {
            throw IllegalArgumentException("Invalid or corrupted tax backup file.")
        }
        val header = encryptedBytes.copyOfRange(0, BACKUP_MAGIC.size)
        if (!header.contentEquals(BACKUP_MAGIC)) {
            throw IllegalArgumentException("Unrecognized tax backup file format.")
        }

        var offset = BACKUP_MAGIC.size
        val salt = encryptedBytes.copyOfRange(offset, offset + PBKDF2_SALT_BYTES)
        offset += PBKDF2_SALT_BYTES
        val iv = encryptedBytes.copyOfRange(offset, offset + GCM_IV_BYTES)
        offset += GCM_IV_BYTES
        val ciphertext = encryptedBytes.copyOfRange(offset, encryptedBytes.size)

        return try {
            val key = derivePassphraseKey(cleanPass, salt)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            throw IllegalArgumentException("Incorrect backup passphrase or corrupted backup file.")
        }
    }

    private fun derivePassphraseKey(passphrase: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }
}
