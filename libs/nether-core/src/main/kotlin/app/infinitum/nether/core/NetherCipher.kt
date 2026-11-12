package app.infinitum.nether.core

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Vault file: a 47-byte header, then AES-256-GCM ciphertext.
 *
 * Header: "NTHR" | version (u16) | kdf (u8) | memory KiB (i32) | iterations (i32) |
 * parallelism (i32) | salt (16) | nonce (12).
 *
 * Version 2 binds the whole header to the ciphertext as GCM associated data, so any
 * change to the header fails to open. Version 1 files (no associated data) still open,
 * and are rewritten as version 2 on the next save.
 */
object NetherCipher {
    const val MAGIC = "NTHR"
    const val VERSION: Int = 2
    const val VERSION_1: Int = 1
    const val HEADER_LEN = 47
    private const val NONCE_LEN = 12
    private const val TAG_BITS = 128

    fun seal(
        key: ByteArray,
        plaintext: ByteArray,
        salt: ByteArray,
        kdf: Int = NetherKdf.KDF_ARGON2ID,
        params: NetherKdf.Params = NetherKdf.Params.DEFAULT,
    ): ByteArray = sealVersion(VERSION, key, plaintext, salt, kdf, params)

    internal fun sealVersion(
        version: Int,
        key: ByteArray,
        plaintext: ByteArray,
        salt: ByteArray,
        kdf: Int,
        params: NetherKdf.Params,
    ): ByteArray {
        require(key.size == NetherKdf.KEY_LEN)
        require(salt.size == NetherKdf.SALT_LEN)
        val nonce = ByteArray(NONCE_LEN).also { SecureRandom().nextBytes(it) }
        val header = ByteBuffer.allocate(HEADER_LEN)
        header.put(MAGIC.toByteArray(Charsets.US_ASCII))
        header.putShort(version.toShort())
        header.put(kdf.toByte())
        header.putInt(params.memoryKib)
        header.putInt(params.iterations)
        header.putInt(params.parallelism)
        header.put(salt)
        header.put(nonce)
        val headerBytes = header.array()
        val aad = if (version >= 2) headerBytes else null
        val body = aes(Cipher.ENCRYPT_MODE, key, nonce, plaintext, aad)
        return headerBytes + body
    }

    fun open(key: ByteArray, blob: ByteArray): ByteArray {
        val parsed = parse(blob)
        val aad = if (parsed.version >= 2) blob.copyOfRange(0, HEADER_LEN) else null
        return aes(Cipher.DECRYPT_MODE, key, parsed.nonce, parsed.body, aad)
    }

    fun parse(blob: ByteArray): Header {
        require(blob.size > HEADER_LEN) { "Vault file is too short." }
        val buf = ByteBuffer.wrap(blob)
        val magic = ByteArray(4).also { buf.get(it) }.toString(Charsets.US_ASCII)
        require(magic == MAGIC) { "Not a Nether vault." }
        val version = buf.short.toInt()
        require(version == VERSION || version == VERSION_1) { "Unsupported Nether vault." }
        val kdf = buf.get().toInt() and 0xFF
        val memory = buf.int
        val iterations = buf.int
        val parallelism = buf.int
        val salt = ByteArray(NetherKdf.SALT_LEN).also { buf.get(it) }
        val nonce = ByteArray(NONCE_LEN).also { buf.get(it) }
        val body = ByteArray(buf.remaining()).also { buf.get(it) }
        return Header(version, kdf, memory, iterations, parallelism, salt, nonce, body)
    }

    fun passwordOpens(password: CharArray, blob: ByteArray): ByteArray? {
        val header = parse(blob)
        val key = NetherKdf.derive(password, header.salt, header.kdf, header.params)
        return try {
            open(key, blob)
            key
        } catch (_: AEADBadTagException) {
            key.fill(0)
            null
        }
    }

    private fun aes(mode: Int, key: ByteArray, nonce: ByteArray, input: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(input)
    }

    data class Header(
        val version: Int,
        val kdf: Int,
        val memoryKib: Int,
        val iterations: Int,
        val parallelism: Int,
        val salt: ByteArray,
        val nonce: ByteArray,
        val body: ByteArray,
    ) {
        val params: NetherKdf.Params get() = NetherKdf.Params(memoryKib, iterations, parallelism)
    }
}
