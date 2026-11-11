package uk.woozy.fit.scale

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The frames below are built by hand to the layouts documented in openScale's QNHandler, not recorded from your
 * scale. A real recording (the Scale screen's log) should be added here once you have one.
 */
class QnProtocolTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun checksumIsTheLowByteOfTheSum() {
        assertEquals(0x04.toByte(), QnProtocol.checksum(bytes(0xFF, 0x05), 0, 1))
        val f = QnProtocol.configFrame(0xFF.toByte())
        assertEquals(QnProtocol.checksum(f, 0, f.lastIndex - 1), f.last())
    }

    @Test
    fun scaleFactorFromTheInfoFrame() {
        val hundredths = ByteArray(12).also { it[0] = 0x12; it[10] = 1 }
        val tenths = ByteArray(12).also { it[0] = 0x12; it[10] = 0 }
        assertEquals(100f, QnProtocol.scaleFactor(hundredths)!!, 0f)
        assertEquals(10f, QnProtocol.scaleFactor(tenths)!!, 0f)
        assertNull(QnProtocol.scaleFactor(ByteArray(5)))
    }

    @Test
    fun originalLiveFrameReadsWeightAndStableFlag() {
        // 86.40 kg = 8640 = 0x21C0, stable flag 1
        val frame = bytes(0x10, 0x0B, 0x00, 0x21, 0xC0, 0x01, 0x00, 0x00, 0x00, 0x00)
        val r = QnProtocol.liveWeight(frame, 100f, universalVariant = false)!!
        assertEquals(86.4f, r.kg, 0.001f)
        assertTrue(r.stable)
    }

    @Test
    fun unstableOriginalFrameIsMarkedUnstable() {
        val frame = bytes(0x10, 0x0B, 0x00, 0x21, 0xC0, 0x00, 0x00, 0x00, 0x00, 0x00)
        assertFalse(QnProtocol.liveWeight(frame, 100f, false)!!.stable)
    }

    @Test
    fun es30FrameWithTenthsFactor() {
        // 86.4 kg at /10 = 864 = 0x0360; byte 3 unit, byte 4 stable flag 2, weight in bytes 5-6
        val frame = bytes(0x10, 0x0F, 0xFF, 0x01, 0x02, 0x03, 0x60, 0, 0, 0, 0)
        val r = QnProtocol.liveWeight(frame, 10f, universalVariant = true)!!
        assertEquals(86.4f, r.kg, 0.001f)
        assertTrue(r.stable)
        // state 1 is still settling on the universal variant
        val settling = frame.copyOf().also { it[4] = 0x01 }
        assertFalse(QnProtocol.liveWeight(settling, 10f, universalVariant = true)!!.stable)
    }

    @Test
    fun implausibleWeightIsRescaledOnce() {
        // 864 read at /100 is 8.64 kg, which is out of plausible range for an adult; the /10 fallback gives 0.86 so it is dropped
        val frame = bytes(0x10, 0x0B, 0x00, 0x03, 0x60, 0x01, 0, 0, 0, 0)
        assertNull(QnProtocol.liveWeight(frame, 100f, false)?.takeIf { it.kg > 20f })
    }

    @Test
    fun shortOrWrongFramesAreIgnored() {
        assertNull(QnProtocol.liveWeight(bytes(0x10, 0x01), 100f, false))
        assertNull(QnProtocol.liveWeight(bytes(0x14, 0, 0, 0, 0, 0, 0, 0, 0, 0), 100f, false))
        assertNull(QnProtocol.storedWeight(ByteArray(10)))
    }

    @Test
    fun storedFrameReadsWeightAndTimestamp() {
        val f = ByteArray(19)
        f[0] = 0x23
        f[6] = 0x10; f[7] = 0x00; f[8] = 0x00; f[9] = 0x00 // 16 seconds
        f[10] = 0x21; f[11] = 0xC0.toByte() // 86.40 kg
        val (reading, seconds) = QnProtocol.storedWeight(f)!!
        assertEquals(86.4f, reading.kg, 0.001f)
        assertEquals(16L, seconds)
        assertTrue(reading.stored)
    }

    @Test
    fun timeSyncCarriesTheProtocolTypeAndAValidChecksum() {
        val f = QnProtocol.timeSyncFrame(0xFF.toByte(), nowMs = 1_800_000_000_000L)
        assertEquals(0x20, f[0].toInt())
        assertEquals(0xFF.toByte(), f[2])
        assertEquals(QnProtocol.checksum(f, 0, f.lastIndex - 1), f.last())
        val seconds = (f[3].toLong() and 0xFF) or ((f[4].toLong() and 0xFF) shl 8) or ((f[5].toLong() and 0xFF) shl 16) or ((f[6].toLong() and 0xFF) shl 24)
        assertEquals(1_800_000_000L - QnProtocol.SCALE_EPOCH_OFFSET, seconds)
    }

    @Test
    fun fixedFramesHaveRightLengthAndChecksums() {
        val q = QnProtocol.storedQuery(0x00)
        assertEquals(6, q.size)
        assertEquals(QnProtocol.checksum(q, 0, 4), q[5])
        QnProtocol.handshakeReplies().forEach { assertEquals(13, it.size); assertEquals(QnProtocol.checksum(it, 0, 11), it[12]) }
        val g = QnProtocol.guestProfile(175, 28, true)
        assertEquals(13, g.size)
        assertEquals((1750 shr 8).toByte(), g[8])
        assertNotNull(QnProtocol.stableAck(0xFF.toByte()))
        assertArrayEquals(byteArrayOf(0x1F, 0x05, 0x00, 0x10), QnProtocol.stableAck(0x00).copyOf(4))
    }
}
