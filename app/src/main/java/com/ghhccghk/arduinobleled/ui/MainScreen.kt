package com.ghhccghk.arduinobleled.ui


import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ghhccghk.arduinobleled.tools.BleViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BlePermissionScreen(
    onAllPermissionsGranted: () -> Unit = {}
) {
    // 定义需要的权限列表，安卓12+和以下差异处理
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    } else {
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }

    val multiplePermissionsState = rememberMultiplePermissionsState(permissions)

    LaunchedEffect(multiplePermissionsState.allPermissionsGranted) {
        if (multiplePermissionsState.allPermissionsGranted) {
            onAllPermissionsGranted()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
    // 输入框状态
    var x by remember { mutableStateOf("1") }
    var y by remember { mutableStateOf("1") }
    var r by remember { mutableStateOf("255") }
    var g by remember { mutableStateOf("255") }
    var b by remember { mutableStateOf("255") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { vm.scan() },
                modifier = Modifier.weight(1f)
            ) {
                Text("扫描")
            }
            Button(
                onClick = { vm.stopScan() },
                modifier = Modifier.weight(1f)
            ) {
                Text("停止")
            }
        }

        Spacer(Modifier.height(12.dp))

        Text("设备列表", style = MaterialTheme.typography.titleMedium)
        Divider(modifier = Modifier.padding(vertical = 4.dp))

        LazyColumn(
            modifier = Modifier
                .heightIn(max = 160.dp)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            items(devices) { dev ->
                Text(
                    text = dev.name ?: dev.address,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.connect(dev) }
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                Divider()
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text("连接状态：${if (connected) "已连接" else "未连接"}", style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { vm.disconnect() },
                modifier = Modifier.weight(1f)
            ) {
                Text("断开连接")
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { vm.send("CLEAR" + "\n") },
                modifier = Modifier.weight(1f)
            ) {
                Text("清屏")
            }
            Button(
                onClick = { vm.send("FILL 255 255 255" + "\n") },
                modifier = Modifier.weight(1f)
            ) {
                Text("填充白")
            }
        }

        Spacer(Modifier.height(20.dp))

        Text("单点控制", style = MaterialTheme.typography.titleMedium)
        Divider(modifier = Modifier.padding(vertical = 6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = x,
                onValueChange = { x = it.filter { c -> c.isDigit() } },
                label = { Text("X") },
                singleLine = true,
                modifier = Modifier.width(70.dp),
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )
            TextField(
                value = y,
                onValueChange = { y = it.filter { c -> c.isDigit() } },
                label = { Text("Y") },
                singleLine = true,
                modifier = Modifier.width(70.dp),
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )
            TextField(
                value = r,
                onValueChange = { r = it.filter { c -> c.isDigit() } },
                label = { Text("R") },
                singleLine = true,
                modifier = Modifier.width(70.dp),
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )
            TextField(
                value = g,
                onValueChange = { g = it.filter { c -> c.isDigit() } },
                label = { Text("G") },
                singleLine = true,
                modifier = Modifier.width(70.dp),
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )
            TextField(
                value = b,
                onValueChange = { b = it.filter { c -> c.isDigit() } },
                label = { Text("B") },
                singleLine = true,
                modifier = Modifier.width(70.dp),
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                vm.send(
                    "PIX ${x.toIntOrNull() ?: 0} ${y.toIntOrNull() ?: 0} " +
                            "${r.toIntOrNull() ?: 0} ${g.toIntOrNull() ?: 0} ${b.toIntOrNull() ?: 0}" + "\n"
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("设置单点颜色")
        }

        Spacer(Modifier.height(24.dp))

        Text("日志", style = MaterialTheme.typography.titleMedium)
        Divider(modifier = Modifier.padding(vertical = 6.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            items(logs) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
                Divider()
            }
        }
    }
}
