package dev.veschud.protocol

import org.junit.Assert.*
import org.junit.Test
import dev.veschud.config.BoardProfiles

class VescProtocolTest {
    @Test fun frameRoundTripsAcrossBleChunks() {
        val payload = byteArrayOf(4, 1, 2, 3, 4)
        val frame = VescProtocol.frame(payload)
        var decoded: ByteArray? = null
        val decoder = VescFrameDecoder { decoded = it }
        decoder.accept(frame.copyOfRange(0, 3)); decoder.accept(frame.copyOfRange(3, frame.size))
        assertArrayEquals(payload, decoded)
    }
    @Test fun rejectsBadCrc() {
        val frame = VescProtocol.frame(byteArrayOf(4, 1)).also { it[2] = 9 }
        var called = false
        VescFrameDecoder { called = true }.accept(frame)
        assertFalse(called)
    }
    @Test fun parsesMinimumGetValuesPayload() {
        val payload = ByteArray(54)
        payload[0] = VescProtocol.COMM_GET_VALUES.toByte()
        assertNotNull(VescProtocol.parseValues(payload))
    }
    @Test fun parsesHardwareNameFromFirmwareReply() {
        val payload = byteArrayOf(0, 6, 5) + "ADV500".toByteArray() + byteArrayOf(0)
        assertEquals("ADV500", VescProtocol.parseIdentity(payload)?.hardwareName)
    }
    @Test fun mapsFloatwheelHardwareNames() {
        assertEquals(BoardProfiles.ADV2, BoardProfiles.fromHardwareName("ADV500"))
        assertEquals(BoardProfiles.ATOM, BoardProfiles.fromHardwareName("ADV200_v1"))
    }
    @Test fun parsesCanPingReply() {
        assertEquals(listOf(2, 7, 42), VescProtocol.parseCanIds(byteArrayOf(62, 2, 7, 42)))
        assertNull(VescProtocol.parseCanIds(byteArrayOf(0, 2, 7)))
    }
    @Test fun framesForwardedCanRequests() {
        var decoded: ByteArray? = null
        VescFrameDecoder { decoded = it }.accept(VescProtocol.getFirmwareVersionPacket(7))
        assertArrayEquals(byteArrayOf(34, 7, 0), decoded)
        VescFrameDecoder { decoded = it }.accept(VescProtocol.getValuesPacket(7))
        assertArrayEquals(byteArrayOf(34, 7, 4), decoded)
    }
    @Test fun unwrapsMatchingForwardedCanReplies() {
        val reply = byteArrayOf(34, 7, 0, 6, 5, 0)
        assertArrayEquals(byteArrayOf(0, 6, 5, 0), VescProtocol.unwrapForwardedCan(reply, 7))
        assertSame(reply, VescProtocol.unwrapForwardedCan(reply, 8))
    }
}
