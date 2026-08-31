package dev.veschud.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import dev.veschud.config.BoardProfiles
import dev.veschud.domain.TelemetryMapper
import dev.veschud.model.BoardIdentity
import dev.veschud.model.BoardProfile
import dev.veschud.protocol.VescFrameDecoder
import dev.veschud.protocol.VescProtocol
import java.util.UUID

@SuppressLint("MissingPermission")
/**
 * Direct BLE telemetry source for VESC adapters exposing Nordic UART Service.
 *
 * Discovery proceeds from the local device identity to CAN probing when the local device is only
 * a bridge. Android GATT accepts one acknowledged write at a time, so every command passes through
 * [writeQueue]. This class deliberately sends no motor-control or configuration-write commands.
 */
class VescBleSource(private val context: Context) : TelemetrySource {
    companion object {
        val NUS_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_RX: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // app writes
        val NUS_TX: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") // app receives
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
    private val main = Handler(Looper.getMainLooper())
    private val mapper = TelemetryMapper()
    private enum class LinkState { DISCONNECTED, IDENTIFYING_LOCAL, SCANNING_CAN, PROBING_CAN, LIVE }
    private var linkState = LinkState.DISCONNECTED
    private var detectedProfile: BoardProfile? = null
    private var motorCanId: Int? = null
    private var bleAddress: String? = null
    private var canCandidates = emptyList<Int>()
    private var probeIndex = 0
    private var probeToken = 0
    private val writeQueue = ArrayDeque<ByteArray>()
    private var writeInFlight = false
    private var listener: TelemetrySource.Listener? = null
    private var gatt: BluetoothGatt? = null
    private var rx: BluetoothGattCharacteristic? = null
    private var stopped = true
    private var reconnectMs = 1_000L
    private val decoder = VescFrameDecoder { payload ->
        VescProtocol.parseCanIds(payload)?.let(::onCanIds)
        VescProtocol.parseIdentity(payload)?.let(::onIdentity)
        // Most CAN replies are bare, but some Express/firmware combinations
        // retain [COMM_FORWARD_CAN, canId] around FW/BMS replies.
        val innerPayload = VescProtocol.unwrapForwardedCan(payload, motorCanId ?: canCandidates.getOrNull(probeIndex))
        if (innerPayload !== payload) VescProtocol.parseIdentity(innerPayload)?.let(::onIdentity)
        val valuesPayload = if (innerPayload.isNotEmpty() &&
            (innerPayload[0].toInt() and 0xff) == VescProtocol.COMM_GET_VALUES) innerPayload else payload
        VescProtocol.parseValues(valuesPayload)?.let { raw ->
            // A pack above a fully charged 20s voltage is unambiguously the 22s Atom.
            // Lower voltages overlap, so they are never used to infer ADV2.
            if (linkState == LinkState.LIVE) {
                if (detectedProfile == null) {
                    BoardProfiles.fromUnambiguousPackVoltage(raw.inputVoltageV)?.let(::selectProfile)
                }
                listener?.onTelemetry(mapper.map(raw))
            }
        }
    }
    private val poll = object : Runnable {
        override fun run() {
            val characteristic = rx
            val connection = gatt
            if (!stopped && characteristic != null && connection != null) {
                val packet = motorCanId?.let(VescProtocol::getValuesPacket)
                    ?: VescProtocol.getValuesPacket()
                writePacket(connection, characteristic, packet)
                main.postDelayed(this, 250)
            }
        }
    }

    override fun start(listener: TelemetrySource.Listener) {
        this.listener = listener; stopped = false; scan()
    }
    override fun stop() { stopped = true; main.removeCallbacksAndMessages(null); gatt?.close(); gatt = null }

    private fun scan() {
        if (stopped) return
        listener?.onState("SCANNING")
        val manager = context.getSystemService(BluetoothManager::class.java)
        val scanner = manager?.adapter?.bluetoothLeScanner
        if (scanner == null) { retry("Bluetooth is off") ; return }
        scanner.startScan(listOf(ScanFilter.Builder().setServiceUuid(android.os.ParcelUuid(NUS_SERVICE)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        main.postDelayed({ scanner.stopScan(scanCallback); if (gatt == null) retry("No Nordic UART VESC found") }, 10_000)
    }
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) {
            context.getSystemService(BluetoothManager::class.java).adapter.bluetoothLeScanner.stopScan(this)
            listener?.onState("CONNECTING")
            val advertisedName = result.scanRecord?.deviceName ?: result.device.name
            bleAddress = result.device.address
            BoardProfiles.fromBleName(advertisedName)?.let(::selectProfile)
            gatt = result.device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        }
        override fun onScanFailed(code: Int) = retry("BLE scan failed: $code")
    }
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) g.discoverServices()
            else { g.close(); if (gatt === g) gatt = null; retry("Disconnected") }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(NUS_SERVICE)
            rx = service?.getCharacteristic(NUS_RX)
            val tx = service?.getCharacteristic(NUS_TX)
            if (rx == null || tx == null) { retry("Nordic UART characteristics missing"); return }
            g.setCharacteristicNotification(tx, true)
            val descriptor = tx.getDescriptor(CCCD)
            if (android.os.Build.VERSION.SDK_INT >= 33) g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            else { descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(descriptor) }
        }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (d.uuid == CCCD && status == BluetoothGatt.GATT_SUCCESS) {
                reconnectMs = 1_000
                resetDiscovery()
                linkState = LinkState.IDENTIFYING_LOCAL
                listener?.onState("IDENTIFYING")
                // Give the bridge time to finish its CCCD handshake before the first
                // acknowledged command. Sending immediately can lose the initial request.
                main.postDelayed({
                    rx?.let { writePacket(g, it, VescProtocol.getFirmwareVersionPacket()) }
                }, 300)
                main.postDelayed({
                    if (linkState == LinkState.IDENTIFYING_LOCAL) beginCanScan()
                }, 1_500)
            }
        }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (c.uuid != NUS_RX) return
            writeInFlight = false
            drainWriteQueue(g, c)
        }
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) = decoder.accept(c.value)
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) = decoder.accept(value)
    }
    private fun retry(message: String) {
        if (stopped) return
        listener?.onError(message); main.removeCallbacks(poll); resetDiscovery(); gatt?.close(); gatt = null; rx = null
        val wait = reconnectMs; reconnectMs = (reconnectMs * 2).coerceAtMost(30_000)
        main.postDelayed({ scan() }, wait)
    }

    private fun selectProfile(value: BoardProfile) {
        detectedProfile = value
        mapper.setProfile(value)
        val can = motorCanId?.let { " • CAN $it" }.orEmpty()
        bleAddress?.let { address ->
            listener?.onBoardIdentified(BoardIdentity(value.id, value.displayName, address, motorCanId))
        }
        listener?.onState("LIVE • ${value.displayName}$can")
    }

    private fun onIdentity(identity: dev.veschud.model.VescIdentity) {
        when (linkState) {
            LinkState.IDENTIFYING_LOCAL -> {
                val profile = BoardProfiles.fromHardwareName(identity.hardwareName)
                if (profile != null) {
                    motorCanId = null
                    selectProfile(profile)
                    startLivePolling()
                } else {
                    // VESC Express is the BLE-to-CAN bridge on ADV2. Unknown local
                    // hardware also gets a CAN scan so future bridge names work.
                    // Do not issue a new GATT write from inside the notification
                    // callback. Express needs a short gap after FW_VERSION.
                    main.postDelayed(::beginCanScan, 300)
                }
            }
            LinkState.PROBING_CAN -> {
                val candidate = canCandidates.getOrNull(probeIndex) ?: return
                val profile = BoardProfiles.fromHardwareName(identity.hardwareName)
                if (profile != null) {
                    motorCanId = candidate
                    selectProfile(profile)
                    startLivePolling()
                } else {
                    probeNextCanNode()
                }
            }
            else -> Unit
        }
    }

    private fun beginCanScan() {
        if (stopped || linkState == LinkState.SCANNING_CAN || linkState == LinkState.PROBING_CAN || linkState == LinkState.LIVE) return
        linkState = LinkState.SCANNING_CAN
        listener?.onState("SEARCHING CAN")
        val connection = gatt ?: return
        val characteristic = rx ?: return
        writePacket(connection, characteristic, VescProtocol.pingCanPacket())
        main.postDelayed({
            if (linkState == LinkState.SCANNING_CAN) {
                writePacket(connection, characteristic, VescProtocol.pingCanPacket())
            }
        }, 1_200)
        main.postDelayed({
            if (linkState == LinkState.SCANNING_CAN) retry("No CAN devices found")
        }, 4_000)
    }

    private fun onCanIds(ids: List<Int>) {
        if (linkState != LinkState.SCANNING_CAN) return
        if (ids.isEmpty()) { retry("CAN bus is empty"); return }
        canCandidates = ids
        probeIndex = 0
        linkState = LinkState.PROBING_CAN
        listener?.onState("CHECKING CAN 1/${ids.size}")
        sendCurrentCanProbe()
    }

    private fun sendCurrentCanProbe() {
        if (linkState != LinkState.PROBING_CAN) return
        val candidate = canCandidates.getOrNull(probeIndex)
        if (candidate == null) { retry("Supported motor controller not found on CAN"); return }
        listener?.onState("CHECKING CAN ${probeIndex + 1}/${canCandidates.size}")
        val connection = gatt ?: return
        val characteristic = rx ?: return
        val token = ++probeToken
        writePacket(connection, characteristic, VescProtocol.getFirmwareVersionPacket(candidate))
        main.postDelayed({
            if (linkState == LinkState.PROBING_CAN && token == probeToken) probeNextCanNode()
        }, 700)
    }

    private fun probeNextCanNode() {
        if (linkState != LinkState.PROBING_CAN) return
        probeToken++
        probeIndex++
        main.postDelayed(::sendCurrentCanProbe, 80)
    }

    private fun startLivePolling() {
        linkState = LinkState.LIVE
        main.removeCallbacks(poll)
        main.postDelayed(poll, 100)
    }

    private fun resetDiscovery() {
        main.removeCallbacks(poll)
        linkState = LinkState.DISCONNECTED
        detectedProfile = null
        motorCanId = null
        canCandidates = emptyList()
        probeIndex = 0
        probeToken++
        writeQueue.clear()
        writeInFlight = false
    }

    private fun writePacket(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, packet: ByteArray) {
        writeQueue.addLast(packet)
        drainWriteQueue(g, characteristic)
    }

    /** Android permits only one characteristic write in flight. Using writes
     * with response prevents discovery and telemetry commands being silently
     * dropped by the BLE stack. */
    private fun drainWriteQueue(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (writeInFlight) return
        val packet = writeQueue.removeFirstOrNull() ?: return
        val started = if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(characteristic, packet, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                android.bluetooth.BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = packet
            g.writeCharacteristic(characteristic)
        }
        if (started) {
            writeInFlight = true
        } else {
            writeQueue.addFirst(packet)
            main.postDelayed({ drainWriteQueue(g, characteristic) }, 30)
        }
    }
}
