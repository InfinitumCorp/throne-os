package app.infinitum.nether.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultFormatTest {
    private val password = "correct horse battery".toCharArray()
    private val doc = VaultDocument(listOf(NetherEntry(title = "Mail", username = "ada", secret = "s3cret")))

    @Test
    fun newVaultsAreVersionTwo() {
        val blob = NetherVault.create(password, doc)
        assertEquals(NetherCipher.VERSION, NetherCipher.parse(blob).version)
        assertEquals("s3cret", NetherVault.unlock(password, blob).document.entries.single().secret)
    }

    @Test
    fun anyHeaderChangeFailsToOpen() {
        val blob = NetherVault.create(password, doc)
        val salt = NetherCipher.parse(blob).salt
        val key = NetherKdf.derive(password, salt)
        // Every header byte after the magic and version: kdf, cost, salt, nonce.
        for (i in 6 until NetherCipher.HEADER_LEN) {
            val tampered = blob.copyOf()
            tampered[i] = (tampered[i].toInt() xor 0x01).toByte()
            val opened = runCatching { NetherCipher.open(key, tampered) }
            assertTrue("byte $i should break the vault", opened.isFailure)
        }
    }

    @Test
    fun versionOneVaultsStillOpenAndUpgradeOnSave() {
        val salt = NetherKdf.randomSalt()
        val key = NetherKdf.derive(password, salt)
        val v1 = NetherCipher.sealVersion(
            NetherCipher.VERSION_1, key, doc.toJson(), salt, NetherKdf.KDF_ARGON2ID, NetherKdf.Params.DEFAULT,
        )
        assertEquals(NetherCipher.VERSION_1, NetherCipher.parse(v1).version)
        val unlock = NetherVault.unlock(password, v1)
        assertEquals("ada", unlock.document.entries.single().username)

        val saved = NetherVault.save(unlock.key, v1, unlock.document)
        assertEquals(NetherCipher.VERSION, NetherCipher.parse(saved).version)
        assertEquals("ada", NetherVault.unlock(password, saved).document.entries.single().username)
    }

    @Test
    fun recordedCostIsUsedToOpen() {
        val salt = NetherKdf.randomSalt()
        val stronger = NetherKdf.Params(NetherKdf.MEMORY_KIB * 2, 3, 1)
        val key = NetherKdf.derive(password, salt, NetherKdf.KDF_ARGON2ID, stronger)
        val blob = NetherCipher.seal(key, doc.toJson(), salt, NetherKdf.KDF_ARGON2ID, stronger)
        assertEquals(stronger, NetherCipher.parse(blob).params)
        assertEquals("Mail", NetherVault.unlock(password, blob).document.entries.single().title)
        // Saving keeps the stronger cost.
        val saved = NetherVault.save(key, blob, doc)
        assertEquals(stronger, NetherCipher.parse(saved).params)
    }

    @Test
    fun outOfRangeCostIsRefused() {
        val salt = NetherKdf.randomSalt()
        assertThrows(IllegalArgumentException::class.java) {
            NetherKdf.derive(password, salt, NetherKdf.KDF_ARGON2ID, NetherKdf.Params(1_024, 2, 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NetherKdf.derive(password, salt, NetherKdf.KDF_ARGON2ID, NetherKdf.Params(Int.MAX_VALUE, 2, 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NetherKdf.derive(password, salt, NetherKdf.KDF_ARGON2ID, NetherKdf.Params(NetherKdf.MEMORY_KIB, 1_000, 1))
        }
    }

    @Test
    fun wrongPasswordDoesNotOpen() {
        val blob = NetherVault.create(password, doc)
        assertNull(NetherCipher.passwordOpens("wrong".toCharArray(), blob))
    }
}
