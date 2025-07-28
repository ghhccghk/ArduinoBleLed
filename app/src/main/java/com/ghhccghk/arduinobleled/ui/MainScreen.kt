package com.ghhccghk.arduinobleled.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ghhccghk.arduinobleled.tools.BleViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BlePermissionScreen(
    onAllPermissionsGranted: () -> Unit = {}
) {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val multiplePermissionsState = rememberMultiplePermissionsState(permissions)

    LaunchedEffect(multiplePermissionsState.allPermissionsGranted) {
        if (multiplePermissionsState.allPermissionsGranted) {
            onAllPermissionsGranted()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            multiplePermissionsState.allPermissionsGranted -> {
                Text("所有权限已授予，准备连接蓝牙", style = MaterialTheme.typography.titleMedium)
            }
            multiplePermissionsState.shouldShowRationale -> {
                Text(
                    "此应用需要蓝牙和位置权限才能正常工作",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(onClick = { multiplePermissionsState.launchMultiplePermissionRequest() }) {
                    Text("请求权限")
                }
            }
            else -> {
                Text(
                    "权限被拒绝，请到设置页手动开启",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(onClick = { multiplePermissionsState.launchMultiplePermissionRequest() }) {
                    Text("再次请求权限")
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun MainScreen(vm: BleViewModel, paddingValues: PaddingValues) {
    val devices by vm.scanResults.collectAsState()
    val connected by vm.connected.collectAsState()
    val logs by vm.logs.collectAsState(initial = emptyList())
    BleControlScreen(vm, devices, connected, logs, paddingValues)
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleControlScreen(
    vm: BleViewModel,
    devices: List<BluetoothDevice>,
    connected: Boolean,
    logs: List<String>,
    paddingValues: PaddingValues
) {
    var x by remember { mutableStateOf("1") }
    var y by remember { mutableStateOf("1") }
    var r by remember { mutableStateOf("255") }
    var g by remember { mutableStateOf("255") }
    var b by remember { mutableStateOf("255") }

    var showColorPicker by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(Color.White) }
    // 从 ViewModel 收集颜色历史记录
    val colorHistory by vm.colorHistoryFlow.collectAsState(initial = emptyList())
    var brightness by remember { mutableFloatStateOf(128f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
    ) {
        val isBluetoothOn = BluetoothStatus()
        Text(text = if (isBluetoothOn) "蓝牙已开启" else "蓝牙已关闭")

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = { vm.scan() }, modifier = Modifier.weight(1f)) { Text("扫描") }
            Button(onClick = { vm.stopScan() }, modifier = Modifier.weight(1f)) { Text("停止") }
        }

        Spacer(Modifier.height(12.dp))

        Text("设备列表", style = MaterialTheme.typography.titleMedium)
        HorizontalDivider(
            Modifier.padding(vertical = 4.dp),
            DividerDefaults.Thickness,
            DividerDefaults.color
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .heightIn(max = 160.dp)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            items(devices) { dev ->
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                Text(
                    text = dev.name ?: dev.address,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.connect(dev) }
                        .padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("连接状态：${if (connected) "已连接" else "未连接"}")
            Button(onClick = { vm.disconnect() }, modifier = Modifier.weight(1f)) { Text("断开") }
        }

        Spacer(Modifier.height(16.dp))

        Text("屏幕控制")
        HorizontalDivider(
            Modifier.padding(vertical = 6.dp),
            DividerDefaults.Thickness,
            DividerDefaults.color
        )
        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { vm.send("CLEAR\n") },
                modifier = Modifier.weight(1f)
            ) { Text("清屏") }
            Button(
                onClick = { showColorPicker = true },
                modifier = Modifier.weight(1f)
            ) { Text("颜色填充") }
        }

        Spacer(Modifier.height(20.dp))

        Text("单点控制")
        HorizontalDivider(
            Modifier.padding(vertical = 6.dp),
            DividerDefaults.Thickness,
            DividerDefaults.color
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("X" to x, "Y" to y, "R" to r, "G" to g, "B" to b).forEachIndexed { index, pair ->
                TextField(
                    value = pair.second,
                    onValueChange = {
                        when (index) {
                            0 -> x = it.filter(Char::isDigit)
                            1 -> y = it.filter(Char::isDigit)
                            2 -> r = it.filter(Char::isDigit)
                            3 -> g = it.filter(Char::isDigit)
                            4 -> b = it.filter(Char::isDigit)
                        }
                    },
                    label = { Text(pair.first) },
                    singleLine = true,
                    modifier = Modifier.width(60.dp),
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(onClick = {
            vm.send("PIX ${x.toIntOrNull() ?: 0} ${y.toIntOrNull() ?: 0} ${r.toIntOrNull() ?: 0} ${g.toIntOrNull() ?: 0} ${b.toIntOrNull() ?: 0}\n")
        }, modifier = Modifier.fillMaxWidth()) {
            Text("设置单点颜色")
        }

        Spacer(Modifier.height(24.dp))
        Text("亮度")
        HorizontalDivider(
            Modifier.padding(vertical = 6.dp),
            DividerDefaults.Thickness,
            DividerDefaults.color
        )
        Spacer(Modifier.height(5.dp))

        Slider(
            value = brightness,
            onValueChange = { brightness = it },
            valueRange = 0f..255f,
            steps = 254, // 让滑块只能取整数
        )

        // 防抖发送亮度
        LaunchedEffect(brightness) {
            delay(40) // 避免每次滑动疯狂发
            vm.send("BGN ${brightness.roundToInt()}\n")
        }

        Spacer(Modifier.height(12.dp))

        Text("日志")
        HorizontalDivider(
            Modifier.padding(vertical = 6.dp),
            DividerDefaults.Thickness,
            DividerDefaults.color
        )
        Spacer(Modifier.height(5.dp))
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            items(logs) { line -> Text(line) }
        }
    }

    if (showColorPicker) {
//        AlertDialog(
//            onDismissRequest = {
//                showColorPicker = false
////                vm.send("CLEAR\n") // 取消清屏
//            },
//            title = { Text("选择颜色") },
//            text = {
//                Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                    Box(
//                        modifier = Modifier
//                            .size(60.dp)
//                            .background(selectedColor, shape = RoundedCornerShape(15.dp))
//                    )
//
//                    Spacer(Modifier.height(16.dp))
//
//                    var hue by remember { mutableFloatStateOf(0f) }
//                    var saturation by remember { mutableFloatStateOf(1f) }
//                    var value by remember { mutableFloatStateOf(1f) }
//                    var lastSentColor by remember { mutableStateOf(Color.Transparent) }
//
//                    fun updateColor() {
//                        selectedColor = Color.hsv(hue, saturation, value)
//                        if (selectedColor != lastSentColor) {
//                            sendColorToArduino(selectedColor, vm)
//                            lastSentColor = selectedColor
//                        }
//                    }
//
//                    Slider(value = hue, onValueChange = { hue = it; updateColor() }, valueRange = 0f..360f)
//                    Text("色相: ${hue.toInt()}°")
//
//                    Slider(value = saturation, onValueChange = { saturation = it; updateColor() }, valueRange = 0f..1f)
//                    Text("饱和度: ${(saturation * 100).toInt()}%")
//
//                    Slider(value = value, onValueChange = { value = it; updateColor() }, valueRange = 0f..1f)
//                    Text("亮度: ${(value * 100).toInt()}%")
//                }
//            },
//            confirmButton = {
//                TextButton(onClick = { showColorPicker = false }) { Text("完成") }
//            },
//            dismissButton = {
//                TextButton(onClick = {
//                    showColorPicker = false
//                    vm.send("CLEAR\n")
//                }) { Text("取消") }
//            }
//        )
        ColorPickerDialog(
            selectedColor = selectedColor,
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                selectedColor = color
                sendColorToArduino(color, vm)

            },
            colorHistory = colorHistory, // 从 ViewModel 来的历史颜色
            onSaveColorHistory = { color ->
                vm.saveColorToHistory(color) // 保存用户选择
            }
        )
    }
}

private fun sendColorToArduino(color: Color, vm: BleViewModel) {
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    vm.send("FILL $r $g $b\n")
}

@Composable
fun BluetoothStatus(): Boolean {
    val context = LocalContext.current
    val isBluetoothOn = remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter
        isBluetoothOn.value = bluetoothAdapter?.isEnabled == true

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (BluetoothAdapter.ACTION_STATE_CHANGED == intent?.action) {
                    val state =
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    isBluetoothOn.value = (state == BluetoothAdapter.STATE_ON)
                }
            }
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(receiver, filter)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    return isBluetoothOn.value
}

@Composable
fun ColorPickerDialog(
    selectedColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit,
    colorHistory: List<Color>,               // 历史颜色（从 ViewModel Flow 收集）
    onSaveColorHistory: (Color) -> Unit      // 保存颜色到历史记录
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var value by remember { mutableFloatStateOf(1f) }
    var currentColor by remember { mutableStateOf(selectedColor) }

    // 滚动状态
    val scrollStateForDef = rememberScrollState()
    val scrollStateForHistory = rememberScrollState()

    // 默认颜色
    val defaultColors = listOf(
        Color.Red,
        Color.Green,
        Color.Blue,
        Color.Yellow,
        Color.Cyan,
        Color.Magenta,
        Color.White,
        Color.Black
    )

    fun updateColor() {
        currentColor = Color.hsv(hue, saturation, value)
        onColorSelected(currentColor) // 实时回调
    }

    AlertDialog(
        onDismissRequest = {
            onDismiss()
        },
        title = { Text("选择颜色") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(currentColor, shape = RoundedCornerShape(15.dp))
                )

                Spacer(Modifier.height(16.dp))

                //  色相滑块
                Slider(
                    value = hue,
                    onValueChange = {
                        hue = it
                        updateColor()
                    },
                    valueRange = 0f..360f
                )
                Text("色相: ${hue.toInt()}°")

                //  饱和度滑块
                Slider(
                    value = saturation,
                    onValueChange = {
                        saturation = it
                        updateColor()
                    },
                    valueRange = 0f..1f
                )
                Text("饱和度: ${(saturation * 100).toInt()}%")

                // 亮度滑块
                Slider(
                    value = value,
                    onValueChange = {
                        value = it
                        updateColor()
                    },
                    valueRange = 0f..1f
                )
                Text("亮度: ${(value * 100).toInt()}%")

                Spacer(Modifier.height(20.dp))

                // 默认颜色选择
                Text("默认颜色", style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .horizontalScroll(scrollStateForDef)
                ) {
                    defaultColors.forEach { color ->
                        ColorBox(color) {
                            currentColor = color
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(color.toArgb(), hsv)
                            hue = hsv[0]
                            saturation = hsv[1]
                            value = hsv[2]
                            updateColor()
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 历史颜色选择
                if (colorHistory.isNotEmpty()) {
                    Text("历史颜色", style = MaterialTheme.typography.titleMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .horizontalScroll(scrollStateForHistory)
                    ) {
                        colorHistory.forEach { color ->
                            ColorBox(color) {
                                currentColor = color
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(color.toArgb(), hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                                value = hsv[2]
                                updateColor()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSaveColorHistory(currentColor) // 保存颜色到历史
                onDismiss()
            }) {
                Text("完成")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
            }) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ColorBox(color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color, RoundedCornerShape(8.dp))
            .clickable { onClick() }
    )
}

