package dev.veschud.protocol

import dev.veschud.model.RawVescValues
import dev.veschud.model.VescIdentity
import java.io.ByteArrayOutputStream

/**
 * Small, Android-independent subset of the VESC UART protocol required for read-only telemetry.
 * Multi-byte numeric fields use the VESC protocol's big-endian representation.
 */
object VescProtocol {
    const val COMM_FW_VERSION = 0
    const val COMM_GET_VALUES = 4
    const val COMM_FORWARD_CAN = 34
    const val COMM_PING_CAN = 62

    fun getFirmwareVersionPacket(): ByteArray = frame(byteArrayOf(COMM_FW_VERSION.toByte()))
    fun getValuesPacket(): ByteArray = frame(byteArrayOf(COMM_GET_VALUES.toByte()))
    fun pingCanPacket(): ByteArray = frame(byteArrayOf(COMM_PING_CAN.toByte()))
    fun getFirmwareVersionPacket(canId: Int): ByteArray = forwardCan(canId, COMM_FW_VERSION)
    fun getValuesPacket(canId: Int): ByteArray = forwardCan(canId, COMM_GET_VALUES)

    private fun forwardCan(canId: Int, command: Int): ByteArray {
        require(canId in 0..255)
        return frame(byteArrayOf(COMM_FORWARD_CAN.toByte(), canId.toByte(), command.toByte()))
    }

    fun parseCanIds(payload: ByteArray): List<Int>? {
        if (payload.isEmpty() || payload[0].toInt() and 0xff != COMM_PING_CAN) return null
        return payload.drop(1).map { it.toInt() and 0xff }.distinct()
    }

    fun unwrapForwardedCan(payload: ByteArray, expectedCanId: Int?): ByteArray {
        if (payload.size < 3 || payload[0].toInt() and 0xff != COMM_FORWARD_CAN) return payload
        val canId = payload[1].toInt() and 0xff
        if (expectedCanId != null && canId != expectedCanId) return payload
        return payload.copyOfRange(2, payload.size)
    }

    fun frame(payload: ByteArray): ByteArray {
        require(payload.size <= 65535)
        val long = payload.size > 255
        val header = if (long) byteArrayOf(3, (payload.size shr 8).toByte(), payload.size.toByte())
                     else byteArrayOf(2, payload.size.toByte())
        val crc = crc16(payload)
        return header + payload + byteArrayOf((crc shr 8).toByte(), crc.toByte(), 3)
    }

    fun crc16(bytes: ByteArray): Int {
        var crc = 0
        for (b in bytes) {
            var x = (crc shr 8) xor (b.toInt() and 0xff)
            x = x xor (x shr 4)
            crc = ((crc shl 8) xor (x shl 12) xor (x shl 5) xor x) and 0xffff
        }
        return crc
    }

    /** Parses the stable prefix of COMM_GET_VALUES; unknown trailing firmware fields are ignored. */
    fun parseValues(payload: ByteArray): RawVescValues? {
        if (payload.size < 54 || payload[0].toInt() and 0xff != COMM_GET_VALUES) return null
        val r = Reader(payload, 1)
        return try {
            val fet = r.i16() / 10.0
            val motor = r.i16() / 10.0
            val motorCurrent = r.i32() / 100.0
            val inputCurrent = r.i32() / 100.0
            r.i32(); r.i32() // id, iq
            val duty = r.i16() / 1000.0
            val rpm = r.i32()
            val voltage = r.i16() / 10.0
            val ah = r.i32() / 10000.0
            val ahCharged = r.i32() / 10000.0
            val wh = r.i32() / 10000.0
            val whCharged = r.i32() / 10000.0
            val tacho = r.i32()
            val tachoAbs = r.i32()
            val fault = r.u8()
            RawVescValues(fet, motor, motorCurrent, inputCurrent, duty, rpm, voltage,
                ah, ahCharged, wh, whCharged, tacho, tachoAbs, fault)
        } catch (_: IndexOutOfBoundsException) { null }
    }

    /** Extracts firmware version and the NUL-terminated hardware name used by board matching. */
    fun parseIdentity(payload: ByteArray): VescIdentity? {
        if (payload.size < 4 || payload[0].toInt() and 0xff != COMM_FW_VERSION) return null
        val end = (3 until payload.size).firstOrNull { payload[it] == 0.toByte() } ?: return null
        val hardware = payload.copyOfRange(3, end).toString(Charsets.UTF_8).trim()
        if (hardware.isEmpty()) return null
        return VescIdentity(payload[1].toInt() and 0xff, payload[2].toInt() and 0xff, hardware)
    }

    private class Reader(private val b: ByteArray, private var i: Int) {
        fun u8() = b[i++].toInt() and 0xff
        fun i16(): Int { val v = (u8() shl 8) or u8(); return v.toShort().toInt() }
        fun i32(): Int = (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8()
    }
}

/** Reassembles VESC UART frames split across arbitrary BLE notifications. */
class VescFrameDecoder(private val onPayload: (ByteArray) -> Unit) {
    private val buffer = ByteArrayOutputStream()

    @Synchronized fun accept(chunk: ByteArray) {
        buffer.write(chunk)
        var bytes = buffer.toByteArray()
        var offset = 0
        while (offset < bytes.size) {
            while (offset < bytes.size && bytes[offset] != 2.toByte() && bytes[offset] != 3.toByte()) offset++
            if (offset >= bytes.size) break
            val long = bytes[offset] == 3.toByte()
            val headerSize = if (long) 3 else 2
            if (bytes.size - offset < headerSize) break
            val length = if (long) ((bytes[offset + 1].toInt() and 255) shl 8) or (bytes[offset + 2].toInt() and 255)
                         else bytes[offset + 1].toInt() and 255
            val total = headerSize + length + 3
            if (bytes.size - offset < total) break
            val payload = bytes.copyOfRange(offset + headerSize, offset + headerSize + length)
            val crcAt = offset + headerSize + length
            val crc = ((bytes[crcAt].toInt() and 255) shl 8) or (bytes[crcAt + 1].toInt() and 255)
            if (bytes[offset + total - 1] == 3.toByte() && VescProtocol.crc16(payload) == crc) onPayload(payload)
            offset += total
        }
        buffer.reset()
        if (offset < bytes.size) buffer.write(bytes, offset, bytes.size - offset)
    }
}
