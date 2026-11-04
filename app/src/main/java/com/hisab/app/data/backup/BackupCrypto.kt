package com.hisab.app.data.backup

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Optional password protection for backup files: AES-256-GCM with a key derived by PBKDF2-HMAC-SHA256
 * (210,000 iterations, random 16-byte salt, random 12-byte IV). GCM authenticates the data, so a wrong
 * password or a modified file is detected instead of producing garbage.
 *
 * The encrypted file is itself JSON: {"hisabBackupEncrypted":1,"kdf":"PBKDF2-SHA256","iterations":N,
 * "salt":"..","iv":"..","data":".."} (base64) so it can be recognised without trying to decrypt it.
 */
object BackupCrypto {
    class WrongPasswordException : Exception("Wrong password, or the backup file was modified")

    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256

    fun isEncrypted(text: String): Boolean = try {
        (MiniJson.parse(text) as? Map<*, *>)?.get("hisabBackupEncrypted") == 1L
    } catch (e: MiniJson.JsonException) { false }

    fun encrypt(plain: String, password: CharArray): String {
        require(password.isNotEmpty()) { "Password must not be empty" }
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val b64 = Base64.getEncoder()
        return MiniJson.write(
            linkedMapOf(
                "hisabBackupEncrypted" to 1L, "kdf" to "PBKDF2-SHA256", "iterations" to ITERATIONS.toLong(),
                "salt" to b64.encodeToString(salt), "iv" to b64.encodeToString(iv), "data" to b64.encodeToString(data)
            )
        )
    }

    fun decrypt(text: String, password: CharArray): String {
        val m = MiniJson.parse(text) as? Map<*, *> ?: throw WrongPasswordException()
        val b64 = Base64.getDecoder()
        try {
            val salt = b64.decode(m["salt"] as String)
            val iv = b64.decode(m["iv"] as String)
            val data = b64.decode(m["data"] as String)
            val iterations = (m["iterations"] as Long).toInt()
            require(iterations in 10_000..5_000_000)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
            return String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongPasswordException()
        } catch (e: IllegalArgumentException) {
            throw WrongPasswordException()
        } catch (e: ClassCastException) {
            throw WrongPasswordException()
        } catch (e: NullPointerException) {
            throw WrongPasswordException()
        }
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = f.generateSecret(PBEKeySpec(password, salt, iterations, KEY_BITS)).encoded
        return SecretKeySpec(bytes, "AES")
    }
}
