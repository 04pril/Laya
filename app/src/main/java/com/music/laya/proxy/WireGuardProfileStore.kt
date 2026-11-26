package com.music.laya.proxy

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import app.morphe.extension.shared.wireguard.NativeTunnel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Protects imported WireGuard private keys at rest with AndroidKeyStore AES-GCM.
 * Stores ciphertext in noBackupFilesDir, not SharedPreferences/backups/logs.
 */
object WireGuardProfileStore {
    const val MAX_PROFILE_BYTES = 64 * 1024
    private const val ALIAS = "laya.wireguard.profile.v1"
    private const val FILE_NAME = "laya-wireguard-profile-v1.bin"
    private const val AES = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val magic = byteArrayOf(0x42, 0x43, 0x57, 0x47, 0x01)

    fun hasProfile(context: Context): Boolean = try {
        file(context).openRead().use { true }
    } catch (_: FileNotFoundException) {
        false
    } catch (_: IOException) {
        false
    }

    /** Import via SAF only; reject truncated and invalid UTF-8 files. */
    fun readPlaintext(input: InputStream): String {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (bytes.size() + count > MAX_PROFILE_BYTES) throw IOException("Profile exceeds 64 KiB")
            bytes.write(buffer, 0, count)
        }
        buffer.fill(0)
        val data = bytes.toByteArray()
        return try {
            decode(data)
        } finally {
            data.fill(0)
        }
    }

    @Synchronized
    fun saveValidated(context: Context, profile: String) {
        val plaintext = profile.toByteArray(StandardCharsets.UTF_8)
        require(plaintext.isNotEmpty() && plaintext.size <= MAX_PROFILE_BYTES) { "Invalid profile size" }
        try {
            // Native validates the exact supported subset; unsupported directives
            // (especially PreUp/PostUp shell hooks) are rejected, never executed.
            NativeTunnel.validate(profile)
            val cipher = Cipher.getInstance(AES)
            cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
            cipher.updateAAD(magic)
            val iv = cipher.iv
            require(iv.size == IV_BYTES) { "Unexpected encryption IV" }
            val ciphertext = cipher.doFinal(plaintext)
            val destination = file(context)
            var stream: java.io.FileOutputStream? = null
            try {
                stream = destination.startWrite()
                stream.write(magic)
                stream.write(iv)
                stream.write(ciphertext)
                destination.finishWrite(stream)
            } catch (error: IOException) {
                stream?.let(destination::failWrite)
                throw error
            }
        } finally {
            plaintext.fill(0)
        }
    }

    @Synchronized
    fun load(context: Context): String {
        val max = MAX_PROFILE_BYTES + magic.size + IV_BYTES + TAG_BITS / 8
        val encrypted = file(context).openRead().use { input ->
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                if (bytes.size() + size > max) throw IOException("Stored profile is oversized")
                bytes.write(buffer, 0, size)
            }
            bytes.toByteArray()
        }
        try {
            if (encrypted.size < magic.size + IV_BYTES + TAG_BITS / 8 ||
                !encrypted.copyOfRange(0, magic.size).contentEquals(magic)
            ) throw GeneralSecurityException("Invalid encrypted profile")
            val cipher = Cipher.getInstance(AES)
            cipher.init(
                Cipher.DECRYPT_MODE, key(create = false),
                GCMParameterSpec(TAG_BITS, encrypted, magic.size, IV_BYTES),
            )
            cipher.updateAAD(magic)
            val offset = magic.size + IV_BYTES
            val plaintext = cipher.doFinal(encrypted, offset, encrypted.size - offset)
            return try {
                decode(plaintext)
            } finally {
                plaintext.fill(0)
            }
        } finally {
            encrypted.fill(0)
        }
    }

    private fun decode(bytes: ByteArray): String =
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")

    private fun file(context: Context): AtomicFile =
        AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    private fun key(create: Boolean): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keystore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) throw GeneralSecurityException("WireGuard profile key not available")
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
