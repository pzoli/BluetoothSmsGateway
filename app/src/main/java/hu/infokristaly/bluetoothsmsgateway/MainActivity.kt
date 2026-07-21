package hu.infokristaly.bluetoothsmsgateway

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import hu.infokristaly.bluetoothsmsgateway.ui.theme.BluetoothSmsGatewayTheme

class MainActivity : ComponentActivity() {
    private lateinit var bleServer: BleServer

    private val bluetoothManager by lazy {
        getSystemService(BluetoothManager::class.java)
    }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startBleServerIfReady()
        } else {
            Toast.makeText(this, "Bluetooth must be enabled to run the gateway", Toast.LENGTH_LONG).show()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkBluetoothAndStart()
        } else {
            Toast.makeText(this, "Permissions are required to run the gateway", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BluetoothSmsGatewayTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
        
        bleServer = BleServer(this)
        
        // Start the permission flow
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.SEND_SMS
            )
        )
    }

    private fun checkBluetoothAndStart() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show()
            return
        }

        if (!adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            startBleServerIfReady()
        }
    }

    private fun startBleServerIfReady() {
        try {
            bleServer.start()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Security error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    override fun onDestroy() {
        super.onDestroy()
        if (::bleServer.isInitialized) {
            bleServer.stop()
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BluetoothSmsGatewayTheme {
        Greeting("Android")
    }
}
