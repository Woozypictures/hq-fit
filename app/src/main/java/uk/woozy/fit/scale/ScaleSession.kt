package uk.woozy.fit.scale

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class FoundDevice(val address: String, val name: String, val rssi: Int)

data class ScaleUi(
    val status: String = "Idle",
    val scanning: Boolean = false,
    val connected: Boolean = false,
    val devices: List<FoundDevice> = emptyList(),
    val liveKg: Float? = null,
    val stable: Boolean = false,
    val log: List<String> = emptyList(),
)

/**
 * Talks to a Qingniu-protocol scale over Bluetooth LE. Written from openScale's documented behaviour and not yet
 * tried against a real Kamtron CS20M, so everything is logged for the Scale screen's diagnostic view.
 * Permissions must be granted by the caller before [scan] or [connect].
 */
@SuppressLint("MissingPermission")
class ScaleSession(
    private val context: Context,
    private val onReading: (kg: Float, epochMs: Long, stored: Boolean) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val _ui = MutableStateFlow(ScaleUi())
    val ui: StateFlow<ScaleUi> = _ui

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var timeChar: BluetoothGattCharacteristic? = null
    private val notifyChars = ArrayDeque<BluetoothGattCharacteristic>()
    private val writeQueue = ArrayDeque<() -> Boolean>()
    private var busy = false
    private var factor = 100f
    private var protocolType: Byte = 0
    private var type1 = true
    private var lastReported = 0L
    private var scanCb: ScanCallback? = null

    private val adapter get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private fun log(line: String) {
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.UK).format(java.util.Date())
        _ui.update { it.copy(log = (it.log + "$t $line").takeLast(200)) }
    }

    private fun status(s: String) = _ui.update { it.copy(status = s) }

    fun scan() {
        val a = adapter
        if (a == null || !a.isEnabled) { status("Bluetooth is off"); return }
        stopScan()
        _ui.update { it.copy(devices = emptyList(), scanning = true, status = "Scanning, step on the scale to wake it") }
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device.name ?: return
                val d = FoundDevice(result.device.address, name, result.rssi)
                _ui.update { s -> s.copy(devices = (s.devices.filterNot { it.address == d.address } + d).sortedByDescending { it.rssi }) }
            }
            override fun onScanFailed(errorCode: Int) { status("Scan failed ($errorCode)"); log("scan failed $errorCode") }
        }
        scanCb = cb
        a.bluetoothLeScanner?.startScan(cb)
        main.postDelayed({ stopScan() }, 20_000)
    }

    fun stopScan() {
        scanCb?.let { runCatching { adapter?.bluetoothLeScanner?.stopScan(it) } }
        scanCb = null
        _ui.update { it.copy(scanning = false) }
    }

    fun connect(address: String) {
        val a = adapter ?: return
        stopScan()
        disconnect()
        status("Connecting")
        log("connect $address")
        val device: BluetoothDevice = runCatching { a.getRemoteDevice(address) }.getOrNull() ?: run { status("Bad address"); return }
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        runCatching { gatt?.disconnect(); gatt?.close() }
        gatt = null; writeChar = null; timeChar = null
        notifyChars.clear(); writeQueue.clear(); busy = false
        _ui.update { it.copy(connected = false, liveKg = null, stable = false) }
    }

    // ---- queued GATT operations: Android allows one at a time
    private fun enqueue(op: () -> Boolean) { writeQueue.addLast(op); pump() }

    private fun pump() {
        if (busy) return
        val op = writeQueue.removeFirstOrNull() ?: return
        busy = true
        if (!op()) { busy = false; pump() }
    }

    @Suppress("DEPRECATION")
    private fun write(c: BluetoothGattCharacteristic?, data: ByteArray) {
        val g = gatt ?: return
        if (c == null) return
        log("tx ${QnProtocol.hex(data)}")
        enqueue {
            c.value = data
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            g.writeCharacteristic(c)
        }
    }

    @Suppress("DEPRECATION")
    private fun enableNext() {
        val g = gatt ?: return
        val c = notifyChars.removeFirstOrNull() ?: run { status("Ready: step on the scale"); return }
        g.setCharacteristicNotification(c, true)
        val d = c.getDescriptor(CCCD) ?: run { enableNext(); return }
        val indicate = c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 &&
            c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0
        d.value = if (indicate) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        enqueue { g.writeDescriptor(d) }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, st: Int, newState: Int) {
            main.post {
                log("state $st -> $newState")
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    _ui.update { it.copy(connected = true) }
                    status("Connected, reading services")
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    status("Disconnected")
                    _ui.update { it.copy(connected = false) }
                    runCatching { g.close() }
                    if (gatt === g) gatt = null
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, st: Int) {
            main.post {
                g.services.forEach { s -> log("service ${s.uuid.short()} ${s.characteristics.joinToString { it.uuid.short() }}") }
                val s1 = g.getService(uuid16(0xFFE0))
                val s2 = g.getService(uuid16(0xFFF0))
                notifyChars.clear()
                when {
                    s1 != null -> {
                        type1 = true
                        writeChar = s1.getCharacteristic(uuid16(0xFFE3))
                        timeChar = s1.getCharacteristic(uuid16(0xFFE4))
                        listOfNotNull(s1.getCharacteristic(uuid16(0xFFE1)), s1.getCharacteristic(uuid16(0xFFE2))).forEach { notifyChars.addLast(it) }
                    }
                    s2 != null -> {
                        type1 = false
                        writeChar = s2.getCharacteristic(uuid16(0xFFF2))
                        timeChar = null
                        listOfNotNull(s2.getCharacteristic(uuid16(0xFFF1))).forEach { notifyChars.addLast(it) }
                    }
                    else -> { status("This device does not look like a Qingniu scale"); return@post }
                }
                log(if (type1) "layout 0xFFE0" else "layout 0xFFF0")
                enableNext()
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, st: Int) {
            main.post { log("subscribed ${d.characteristic.uuid.short()} status $st"); busy = false; enableNext(); pump() }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, st: Int) {
            main.post { if (st != 0) log("write ${c.uuid.short()} failed $st"); busy = false; pump() }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val data = c.value?.copyOf() ?: return
            main.post { handle(data) }
        }
    }

    private fun handle(f: ByteArray) {
        if (f.isEmpty()) return
        log("rx ${QnProtocol.hex(f)}")
        val now = System.currentTimeMillis()
        when (f[0].toInt() and 0xFF) {
            0x12 -> {
                factor = QnProtocol.scaleFactor(f) ?: factor
                if (f.size > 2) protocolType = f[2]
                write(writeChar, QnProtocol.configFrame(protocolType))
                if (type1) write(timeChar, QnProtocol.timeMagic(now))
            }
            0x10 -> {
                val universal = (protocolType.toInt() and 0xFF) == 0xFF
                val r = QnProtocol.liveWeight(f, factor, universal) ?: return
                _ui.update { it.copy(liveKg = r.kg, stable = r.stable) }
                if (r.stable) {
                    if (universal) write(writeChar, QnProtocol.stableAck(protocolType))
                    if (now - lastReported > 15_000) { lastReported = now; onReading(r.kg, now, false); log("saved ${r.kg} kg") }
                }
            }
            0x14 -> write(writeChar, QnProtocol.timeSyncFrame(protocolType, now))
            0x21 -> {
                QnProtocol.handshakeReplies().forEach { write(writeChar, it) }
                write(writeChar, QnProtocol.storedQuery(protocolType))
            }
            0x23 -> QnProtocol.storedWeight(f)?.let { (r, seconds) ->
                onReading(r.kg, (seconds + QnProtocol.SCALE_EPOCH_OFFSET) * 1000L, true)
                log("stored ${r.kg} kg")
            }
        }
    }

    private fun uuid16(v: Int): UUID = UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", v))
    private fun UUID.short(): String = toString().substring(4, 8)

    private companion object {
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
