package app.infinitum.nether.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillSaveTest {
    @Test
    fun newWebLoginIsAddedAndFillsBack() {
        val doc = AutofillSave.apply(VaultDocument(), "accounts.example.com", "com.android.chrome", "ada", "pw1", now = 10)!!
        val entry = doc.entries.single()
        assertEquals("https://accounts.example.com", entry.url)
        assertEquals("ada", entry.username)
        assertEquals("pw1", entry.secret)
        assertEquals("accounts.example.com", entry.title)
        assertTrue(AutofillMatch.matches(entry.url, "accounts.example.com", null))
    }

    @Test
    fun newAppLoginUsesPackage() {
        val doc = AutofillSave.apply(VaultDocument(), null, "com.example.bank", "ada", "pw1")!!
        val entry = doc.entries.single()
        assertEquals("androidapp://com.example.bank", entry.url)
        assertEquals("Bank", entry.title)
        assertTrue(AutofillMatch.matches(entry.url, null, "com.example.bank"))
    }

    @Test
    fun samePlaceAndUserUpdatesPasswordWithHistory() {
        val start = AutofillSave.apply(VaultDocument(), "example.com", null, "ada", "old", now = 10)!!
        val doc = AutofillSave.apply(start, "example.com", null, "ADA", "new", now = 20)!!
        val entry = doc.entries.single()
        assertEquals("new", entry.secret)
        assertEquals("old", entry.history.first().secret)
    }

    @Test
    fun otherUserOnSameSiteIsANewLogin() {
        val start = AutofillSave.apply(VaultDocument(), "example.com", null, "ada", "pw", now = 10)!!
        val doc = AutofillSave.apply(start, "example.com", null, "bob", "pw2", now = 20)!!
        assertEquals(2, doc.entries.size)
    }

    @Test
    fun nothingToSave() {
        val start = AutofillSave.apply(VaultDocument(), "example.com", null, "ada", "pw", now = 10)!!
        assertNull(AutofillSave.apply(start, "example.com", null, "ada", "pw"))
        assertNull(AutofillSave.apply(VaultDocument(), "example.com", null, "ada", ""))
        assertNull(AutofillSave.apply(VaultDocument(), null, null, "ada", "pw"))
    }
}
