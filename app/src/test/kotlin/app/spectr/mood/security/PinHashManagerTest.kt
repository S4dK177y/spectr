package app.spectr.mood.security

import app.spectr.mood.domain.model.StoredPinCredential
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHashManagerTest {

    @Test
    fun `validates 4-digit numeric PIN format strictly`() {
        assertTrue(PinHashManager.isValidPinFormat("0429"))
        assertFalse(PinHashManager.isValidPinFormat("123"))
        assertFalse(PinHashManager.isValidPinFormat("12345"))
        assertFalse(PinHashManager.isValidPinFormat("12a4"))
        assertFalse(PinHashManager.isValidPinFormat(""))
    }

    @Test
    fun `hashes with unique random salt and verifies matching PIN while rejecting wrong ones`() {
        val cred1 = PinHashManager.createCredential("2580")
        val cred2 = PinHashManager.createCredential("2580")

        assertNotEquals(cred1.saltBase64, cred2.saltBase64)
        assertNotEquals(cred1.hashBase64, cred2.hashBase64)

        assertTrue(PinHashManager.verifyPin("2580", cred1))
        assertTrue(PinHashManager.verifyPin("2580", cred2))
        assertFalse(PinHashManager.verifyPin("2581", cred1))
        assertFalse(PinHashManager.verifyPin("0000", cred1))
        assertFalse(PinHashManager.verifyPin("abcd", cred1))
    }

    @Test
    fun `returns false cleanly on corrupted stored Base64 credential`() {
        val corrupted = StoredPinCredential(saltBase64 = "!!!not-base64!!!", hashBase64 = "bad")
        assertFalse(PinHashManager.verifyPin("1234", corrupted))
    }
}
