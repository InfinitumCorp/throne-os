package app.infinitum.nether.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class GeneratorAndTotpTest {
    @Test
    fun wordListGivesEightBitsPerWord() {
        assertEquals(256, PasswordGenerator.words.size)
        assertEquals(256, PasswordGenerator.words.toSet().size)
        assertTrue(PasswordGenerator.words.all { it.matches(Regex("[a-z]{3,8}")) })
    }

    @Test
    fun defaultPassphraseHasAtLeast64Bits() {
        val bits = PasswordGenerator.PASSPHRASE_WORDS * ln(PasswordGenerator.words.size.toDouble()) / ln(2.0)
        assertTrue(bits >= 64.0)
        val phrase = PasswordGenerator.passphrase()
        assertEquals(PasswordGenerator.PASSPHRASE_WORDS, phrase.split("-").size)
        assertTrue(phrase.split("-").all { it in PasswordGenerator.words })
        // Too-short requests are raised to the 6-word floor.
        assertEquals(6, PasswordGenerator.passphrase(2).split("-").size)
    }

    @Test
    fun badOtpAuthLinksAreRefused() {
        val ok = "otpauth://totp/Mail:ada?secret=GEZDGNBVGY3TQOJQ"
        assertEquals(30, Totp.parseOtpAuth(ok).period)
        for (bad in listOf(
            "$ok&period=0",
            "$ok&period=-5",
            "$ok&period=100000",
            "$ok&digits=0",
            "$ok&digits=10",
            "otpauth://totp/Mail:ada?secret=1111",
            "otpauth://totp/Mail:ada",
            "otpauth://hotp/Mail:ada?secret=GEZDGNBVGY3TQOJQ&counter=1",
            "https://example.com/?secret=GEZDGNBVGY3TQOJQ",
        )) {
            assertThrows(bad, IllegalArgumentException::class.java) { Totp.parseOtpAuth(bad) }
        }
    }

    @Test
    fun codeRefusesValuesThatWouldCrashOrOverflow() {
        val secret = "12345678901234567890".toByteArray()
        assertThrows(IllegalArgumentException::class.java) { Totp.code(secret, 0, periodSeconds = 0) }
        assertThrows(IllegalArgumentException::class.java) { Totp.code(secret, 0, digits = 10) }
        assertThrows(IllegalArgumentException::class.java) { Totp.code(ByteArray(0), 0) }
        assertEquals(8, Totp.code(secret, 59_000L, digits = 8).length)
    }
}
