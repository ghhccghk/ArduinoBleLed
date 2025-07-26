package com.ghhccghk.arduinobleled.tools

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.collections.emptyList

/**
 * 统一封装所有 BLE GATT 操作：
 * - 扫描 / 停止
 * - 连接 / 断开
 * - 发现服务
 * - 写特征 / 开启 notify
 * - 回调数据推送
 */

class BleManager(private val context: Context) {
    companion object {
        val SERVICE_UUID = UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB")
        val NOTIFY_UUID  = UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB")
        val WRITE_UUID   = UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB")
        val CCC_DESC_UUID= UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null

    private val _scanResults = MutableSharedFlow<BluetoothDevice>(replay = 10)
    val scanResults = _scanResults.asSharedFlow()

    private val _connected = MutableSharedFlow<Boolean>(replay = 1)
    val connected = _connected.asSharedFlow()

    private val _notifyLines = MutableSharedFlow<String>(replay = 100)
    val notifyLines = _notifyLines.asSharedFlow()

    private val adapter by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val scanner by lazy { adapter.bluetoothLeScanner }
    private val scope = CoroutineScope(Dispatchers.IO)

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    suspend fun startScan() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED) return
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(listOf(filter), settings, scanCb)
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() = scanner.stopScan(scanCb)

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(ct: Int, result: ScanResult) {
            result.device?.let { scope.launch { _scanResults.emit(it) } }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun connect(device: BluetoothDevice) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) return
        bluetoothGatt = device.connectGatt(context, false, gattCb)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        bluetoothGatt?.disconnect()
    }

    private val gattCb = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                scope.launch { _connected.emit(true) }
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                scope.launch { _connected.emit(false) }
                writeChar = null
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
        }
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(SERVICE_UUID) ?: return
            writeChar = svc.getCharacteristic(WRITE_UUID)
            val notifyChar = svc.getCharacteristic(NOTIFY_UUID)
            g.setCharacteristicNotification(notifyChar, true)
            val desc = notifyChar.getDescriptor(CCC_DESC_UUID) ?: return
            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            g.writeDescriptor(desc)
        }
        private val receiveBuffer = StringBuilder()

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val chunk = c.value.decodeToString()
            receiveBuffer.append(chunk)
            var index: Int
            while (true) {
                index = receiveBuffer.indexOf("\n")
                if (index == -1) break

                // 提取一行，去除 \r
                val line = receiveBuffer.substring(0, index).trim('\r', '\n', ' ')
                if (line.isNotEmpty()) {
                    scope.launch { _notifyLines.emit(line) }
                }
                receiveBuffer.delete(0, index + 1) // 移除已处理内容
            }
        }

    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun sendCommand(cmd: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) return
        writeChar?.let {
            it.value = (cmd + "\r\n").toByteArray(Charsets.UTF_8)
            bluetoothGatt?.writeCharacteristic(it)
        }
    }
}


class BleViewModel(app: Application) : AndroidViewModel(app) {
    private val manager = BleManager(app)
    private val context = app.applicationContext

    val scanResults: StateFlow<List<BluetoothDevice>> =
        manager.scanResults
            .scan(emptyList<BluetoothDevice>()) { list, dev ->
                if (list.any { it.address == dev.address }) list else list + dev
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val connected = manager.connected
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    // 新增缓存状态Flow
    val logs: StateFlow<List<String>> = manager.notifyLines
        .scan(emptyList<String>()) { list, newLine ->
            (list + newLine).takeLast(100) // 保留最新100条
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    fun scan() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            == PackageManager.PERMISSION_GRANTED
            && isBluetoothEnabled()) {
            viewModelScope.launch { manager.startScan() }
        } else {
            Toast.makeText(context, "没有蓝牙扫描权限", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopScan() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            == PackageManager.PERMISSION_GRANTED
            && isBluetoothEnabled()) {
            manager.stopScan()
        } else {
            Toast.makeText(context, "没有蓝牙扫描权限", Toast.LENGTH_SHORT).show()
        }
    }

    fun connect(dev: BluetoothDevice) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED
            && isBluetoothEnabled()) {
            viewModelScope.launch { manager.connect(dev) }
        } else {
            Toast.makeText(context, "没有蓝牙连接权限", Toast.LENGTH_SHORT).show()
        }
    }

    fun send(cmd: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED
            && isBluetoothEnabled() ) {
            viewModelScope.launch { manager.sendCommand(cmd) }
        } else {
            Toast.makeText(context, "没有蓝牙连接权限", Toast.LENGTH_SHORT).show()
        }
    }

    fun disconnect() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            && isBluetoothEnabled()) {
            manager.disconnect()
        }   else {
            Toast.makeText(context, "没有蓝牙连接权限", Toast.LENGTH_SHORT).show()
        }
    }

    fun isBluetoothEnabled(): Boolean {
        val bluetoothManager =
            getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        return adapter?.isEnabled == true
    }

}