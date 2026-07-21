package hu.infokristaly.bluetoothsmsgateway

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hu.infokristaly.bluetoothsmsgateway.ble.BLEMessage
import hu.infokristaly.bluetoothsmsgateway.ble.MessageType
import hu.infokristaly.bluetoothsmsgateway.ui.theme.BluetoothSmsGatewayTheme
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class MainActivity : ComponentActivity() {

    private val bluetoothManager by lazy {
        getSystemService(BluetoothManager::class.java)
    }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startGatewayService()
        } else {
            Toast.makeText(this, "Bluetooth must be enabled", Toast.LENGTH_LONG).show()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkBluetoothAndStart()
        } else {
            Toast.makeText(this, "Permissions are required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BluetoothSmsGatewayTheme {
                var isServiceRunning by remember { mutableStateOf(isServiceRunning()) }
                
                // Update state periodically or when returning to app
                LaunchedEffect(Unit) {
                    while(true) {
                        isServiceRunning = isServiceRunning()
                        kotlinx.coroutines.delay(1000)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GatewayDashboard(
                        modifier = Modifier.padding(innerPadding),
                        isRunning = isServiceRunning,
                        onStart = { handleStart() },
                        onStop = { handleStop() },
                        onSimulateSms = { simulateIncomingSms() }
                    )
                }
            }
        }
    }

    private fun handleStart() {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun handleStop() {
        BleForegroundService.stop(this)
    }

    private fun checkBluetoothAndStart() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
            return
        }

        if (!adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            startGatewayService()
        }
    }

    private fun startGatewayService() {
        BleForegroundService.start(this)
    }

    @SuppressLint("MissingPermission")
    private fun simulateIncomingSms() {
        try {
            val server = BleServer.instance
            val event = BLEMessage(
                action = "sms_received",
                type = MessageType.event,
                payload = buildJsonObject {
                    put("from", JsonPrimitive("+36301112233"))
                    put("text", JsonPrimitive("Test simulation from UI!"))
                }
            )
            server.sendEvent(event)
            Toast.makeText(this, "Simulation sent", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Service not running or error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (BleForegroundService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
}

@Composable
fun GatewayDashboard(
    modifier: Modifier = Modifier,
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSimulateSms: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Gateway Status: ${if (isRunning) "RUNNING" else "STOPPED"}",
            style = MaterialTheme.typography.headlineSmall,
            color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        if (!isRunning) {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(0.7f).padding(8.dp)
            ) {
                Text("Start Gateway")
            }
        } else {
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(0.7f).padding(8.dp)
            ) {
                Text("Stop Gateway")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onSimulateSms,
                modifier = Modifier.fillMaxWidth(0.7f).padding(8.dp)
            ) {
                Text("Simulate Incoming SMS")
            }
        }
    }
}
