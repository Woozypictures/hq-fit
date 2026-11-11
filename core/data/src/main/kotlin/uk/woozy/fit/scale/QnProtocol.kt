package uk.woozy.fit.scale

/**
 * The packet side of the Qingniu (QN) scale protocol used by the Kamtron CS20M, Renpho ES-CS20M and similar.
 * Ported from openScale's QNHandler (GPL-3.0, https://github.com/oliexdev/openScale, credited in NOTICE).
 * Pure functions on bytes, so they are unit tested; the Bluetooth plumbing lives in [QnScaleSession].
 *
 * Only weight is decoded. Body composition needs vendor formulas and is left out on purpose.
 */
object QnProtocol {
    /** The scale's clock counts seconds from 2000-01-01 00:00 UTC, with openScale's offset constant kept as is. */
    const val SCALE_EPOCH_OFFSET = 946_702_800L

    fun checksum(buf: ByteArray, from: Int, toInclusive: Int): Byte {
        var s = 0
        for (i in from..toInclusive) s = (s + (buf[i].toInt() and 0xFF)) and 0xFF
        return s.toByte()
    }

    fun withChecksum(frame: ByteArray): ByteArray {
        frame[frame.lastIndex] = checksum(frame, 0, frame.lastIndex - 1)
        return frame
    }

    private fun u16be(a: Byte, b: Byte): Int = ((a.toInt() and 0xFF) shl 8) or (b.toInt() and 0xFF)

    /** 0x12 frame: how the scale counts weight. Byte 10 equal to 1 means hundredths of a kilo, else tenths. */
    fun scaleFactor(frame: ByteArray): Float? =
        if (frame.size > 10 && (frame[0].toInt() and 0xFF) == 0x12) (if (frame[10].toInt() == 1) 100f else 10f) else null

    /** Config frame sent once the scale has told us its protocol type: unit kilograms, nothing else changed. */
    fun configFrame(protocolType: Byte): ByteArray =
        withChecksum(byteArrayOf(0x13, 0x09, protocolType, 0x01, 0x10, 0x00, 0x00, 0x00, 0x00))

    /** Time sync sent on the shared write characteristic after the 0x14 frame. */
    fun timeSyncFrame(protocolType: Byte, nowMs: Long): ByteArray {
        val t = (nowMs / 1000L - SCALE_EPOCH_OFFSET).toInt()
        return withChecksum(
            byteArrayOf(
                0x20, 0x08, protocolType,
                (t and 0xFF).toByte(), ((t ushr 8) and 0xFF).toByte(), ((t ushr 16) and 0xFF).toByte(), ((t ushr 24) and 0xFF).toByte(),
                0x00,
            ),
        )
    }

    /** The time bytes some scales want on a separate time characteristic. */
    fun timeMagic(nowMs: Long): ByteArray {
        val t = (nowMs / 1000L - SCALE_EPOCH_OFFSET).toInt()
        return byteArrayOf(0x02, (t and 0xFF).toByte(), ((t ushr 8) and 0xFF).toByte(), ((t ushr 16) and 0xFF).toByte(), ((t ushr 24) and 0xFF).toByte())
    }

    fun storedQuery(protocolType: Byte): ByteArray = withChecksum(byteArrayOf(0x22, 0x06, protocolType, 0x00, 0x03, 0x00))

    /** Acknowledges a stable reading (the "Fit Plus" family, protocol type 0xFF). */
    fun stableAck(protocolType: Byte): ByteArray = withChecksum(byteArrayOf(0x1F, 0x05, protocolType, 0x10, 0x00))

    /** The two replies the ES-30M style scales expect to the 0x21 frame. */
    fun handshakeReplies(): List<ByteArray> = listOf(
        withChecksum(byteArrayOf(0xA0.toByte(), 0x0D, 0x04, 0xFE.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0)),
        withChecksum(byteArrayOf(0xA0.toByte(), 0x0D, 0x02, 0x01, 0x00, 0x08, 0x00, 0x21, 0x06, 0xB8.toByte(), 0x04, 0x02, 0x00)),
    )

    /** Renpho guest profile: height in millimetres, age and sex. Only affects the scale's own body composition. */
    fun guestProfile(heightCm: Int, age: Int, male: Boolean): ByteArray {
        val mm = heightCm.coerceIn(40, 240) * 10
        return withChecksum(
            byteArrayOf(
                0xA0.toByte(), 0x0D, 0x02, 0xFE.toByte(), 0xFF.toByte(), 0xEE.toByte(),
                if (male) 0x00 else 0x01, age.coerceIn(0, 255).toByte(), (mm shr 8).toByte(), mm.toByte(), 0x04, 0x02, 0x00,
            ),
        )
    }

    /** A weight reading: [kg] and whether the scale says it has settled. */
    data class Reading(val kg: Float, val stable: Boolean, val stored: Boolean = false)

    /**
     * 0x10 frame: live weight. Two layouts exist, told apart as in openScale: the original (weight in bytes 3-4,
     * stable flag in byte 5) and the ES-30M style (stable flag in byte 4, weight in bytes 5-6). [factor] comes from
     * the 0x12 frame. Returns null for frames too short to read.
     */
    fun liveWeight(frame: ByteArray, factor: Float, universalVariant: Boolean): Reading? {
        if (frame.size < 6 || (frame[0].toInt() and 0xFF) != 0x10) return null
        val byte4 = frame[4].toInt() and 0xFF
        val es30 = byte4 <= 0x02 && factor == 10f
        val raw: Int
        val stable: Boolean
        if (es30) {
            if (frame.size < 11) return null
            raw = u16be(frame[5], frame[6])
            stable = if (universalVariant) byte4 == 0x02 else (byte4 == 0x02 || byte4 == 0x01)
        } else {
            if (frame.size < 10) return null
            raw = u16be(frame[3], frame[4])
            stable = frame[5].toInt() == 1
        }
        var kg = raw / factor
        if (kg <= 5f || kg >= 250f) kg /= 10f // some scales report tenths before the 0x12 frame arrives
        return if (kg > 0f) Reading(kg, stable) else null
    }

    /** 0x23 frame: a measurement stored on the scale. Hundredths of a kilo in bytes 10-11, timestamp in bytes 6-9. */
    fun storedWeight(frame: ByteArray): Pair<Reading, Long>? {
        if (frame.size < 17 || (frame[0].toInt() and 0xFF) != 0x23) return null
        val kg = u16be(frame[10], frame[11]) / 100f
        if (kg <= 5f || kg >= 300f) return null
        val seconds = (frame[6].toLong() and 0xFF) or ((frame[7].toLong() and 0xFF) shl 8) or
            ((frame[8].toLong() and 0xFF) shl 16) or ((frame[9].toLong() and 0xFF) shl 24)
        return Reading(kg, stable = true, stored = true) to seconds
    }

    fun hex(data: ByteArray, max: Int = 32): String =
        data.take(max).joinToString(" ") { "%02x".format(it.toInt() and 0xFF) } + if (data.size > max) " …" else ""

}
