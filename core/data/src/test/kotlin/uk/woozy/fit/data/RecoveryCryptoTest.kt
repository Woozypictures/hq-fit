package uk.woozy.fit.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import uk.woozy.fit.data.crypto.RecoveryCrypto
import uk.woozy.fit.data.crypto.WrongRecoveryKeyException

class RecoveryCryptoTest {
    @Test
    fun recoveryKeyHasSixGroupsOfFour() {
        val key = RecoveryCrypto.newRecoveryKey()
        val groups = key.split("-")
        assertEquals(6, groups.size)
        assertTrue(groups.all { it.length == 4 })
        assertTrue(RecoveryCrypto.isWellFormed(key))
    }

    @Test
    fun keysDiffer() {
        assertNotEquals(RecoveryCrypto.newRecoveryKey(), RecoveryCrypto.newRecoveryKey())
    }

    @Test
    fun normaliseIgnoresCaseSpacesAndDashes() {
        val key = RecoveryCrypto.newRecoveryKey()
        val typed = key.lowercase().replace("-", " ")
        assertEquals(RecoveryCrypto.normalise(key), RecoveryCrypto.normalise(typed))
    }

    @Test
    fun wrapThenUnwrapGivesBackTheKey() {
        val dbKey = RecoveryCrypto.newDbKey()
        val recovery = RecoveryCrypto.newRecoveryKey()
        val salt = RecoveryCrypto.newSalt()
        val blob = RecoveryCrypto.wrap(dbKey, recovery, salt)
        assertArrayEquals(dbKey, RecoveryCrypto.unwrap(blob, recovery, salt))
        // Typed back in a different style it still opens.
        assertArrayEquals(dbKey, RecoveryCrypto.unwrap(blob, recovery.lowercase().replace("-", " "), salt))
    }

    @Test
    fun wrongRecoveryKeyIsRefused() {
        val dbKey = RecoveryCrypto.newDbKey()
        val salt = RecoveryCrypto.newSalt()
        val blob = RecoveryCrypto.wrap(dbKey, RecoveryCrypto.newRecoveryKey(), salt)
        try {
            RecoveryCrypto.unwrap(blob, RecoveryCrypto.newRecoveryKey(), salt)
            fail("A wrong key must not open the blob")
        } catch (expected: WrongRecoveryKeyException) {
        }
    }

    @Test
    fun alteredBlobIsRefused() {
        val dbKey = RecoveryCrypto.newDbKey()
        val recovery = RecoveryCrypto.newRecoveryKey()
        val salt = RecoveryCrypto.newSalt()
        val blob = RecoveryCrypto.wrap(dbKey, recovery, salt)
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 1).toByte()
        try {
            RecoveryCrypto.unwrap(blob, recovery, salt)
            fail("A damaged blob must not open")
        } catch (expected: WrongRecoveryKeyException) {
        }
    }

    @Test
    fun shortOrMistypedKeysAreNotWellFormed() {
        assertFalse(RecoveryCrypto.isWellFormed("ABCD-EFGH"))
        assertFalse(RecoveryCrypto.isWellFormed(""))
    }

    @Test
    fun wrappingTwiceGivesDifferentBlobs() {
        val dbKey = RecoveryCrypto.newDbKey()
        val recovery = RecoveryCrypto.newRecoveryKey()
        val salt = RecoveryCrypto.newSalt()
        assertNotEquals(
            RecoveryCrypto.wrap(dbKey, recovery, salt).toList(),
            RecoveryCrypto.wrap(dbKey, recovery, salt).toList(),
        )
    }
}
