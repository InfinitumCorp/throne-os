package app.infinitum.nether.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillMatchTest {
    @Test
    fun sameHostAndSubdomainsMatch() {
        assertTrue(AutofillMatch.matches("https://google.com", "google.com", null))
        assertTrue(AutofillMatch.matches("https://google.com/login", "accounts.google.com", null))
        assertTrue(AutofillMatch.matches("https://www.google.com", "accounts.google.com", null))
        assertTrue(AutofillMatch.matches("google.com", "GOOGLE.COM", null))
    }

    @Test
    fun lookalikeDomainsDoNotMatch() {
        assertFalse(AutofillMatch.matches("https://google.com", "le.com", null))
        assertFalse(AutofillMatch.matches("https://gmail.com", "mail.com", null))
        assertFalse(AutofillMatch.matches("https://google.com", "google.com.evil.io", null))
        assertFalse(AutofillMatch.matches("https://google.com", "evilgoogle.com", null))
        assertFalse(AutofillMatch.matches("https://accounts.google.com", "google.com", null))
    }

    @Test
    fun appsMatchOnlyTheirSavedPackage() {
        assertTrue(AutofillMatch.matches("androidapp://com.example.bank", null, "com.example.bank"))
        assertFalse(AutofillMatch.matches("androidapp://com.example.bank", null, "com.evil.bank"))
        assertFalse(AutofillMatch.matches("https://bank.example.com", null, "com.example.bank"))
        assertFalse(AutofillMatch.matches("androidapp://com.example.bank", "bank.example.com", null))
    }

    @Test
    fun emptyOrUnknownNeverMatches() {
        assertFalse(AutofillMatch.matches("", "google.com", null))
        assertFalse(AutofillMatch.matches("https://google.com", null, null))
        assertFalse(AutofillMatch.matches("https://google.com", "", ""))
    }
}
