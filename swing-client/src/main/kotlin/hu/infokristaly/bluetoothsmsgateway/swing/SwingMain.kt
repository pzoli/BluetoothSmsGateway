package hu.infokristaly.bluetoothsmsgateway.swing

import hu.infokristaly.bluetoothsmsgateway.ble.BLECodec
import hu.infokristaly.bluetoothsmsgateway.ble.BLEMessage
import hu.infokristaly.bluetoothsmsgateway.ble.SmsReceivedPayload
import hu.infokristaly.bluetoothsmsgateway.client.KableBleClient
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*
import javax.swing.border.EmptyBorder

class SwingClient : JFrame("Bluetooth SMS Gateway") {

    private val client = KableBleClient()
    private val logArea = JTextArea()
    private val phoneField = JTextField()
    private val msgArea = JTextArea(3, 20)
    private val sendBtn = JButton("Send SMS")
    private val statusLabel = JLabel("Disconnected")

    init {
        setupUI()
        startBle()
    }

    private fun setupUI() {
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(500, 600)
        setLocationRelativeTo(null)

        val mainPanel = JPanel(BorderLayout(10, 10))
        mainPanel.border = EmptyBorder(10, 10, 10, 10)

        // Status
        val topPanel = JPanel(BorderLayout())
        topPanel.add(statusLabel, BorderLayout.WEST)
        mainPanel.add(topPanel, BorderLayout.NORTH)

        // Log
        logArea.isEditable = false
        val scrollPane = JScrollPane(logArea)
        scrollPane.border = BorderFactory.createTitledBorder("Event Log")
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        // Control
        val bottomPanel = JPanel()
        bottomPanel.layout = BoxLayout(bottomPanel, BoxLayout.Y_AXIS)
        
        val phonePanel = JPanel(BorderLayout())
        phonePanel.add(JLabel("Phone: "), BorderLayout.WEST)
        phonePanel.add(phoneField, BorderLayout.CENTER)
        bottomPanel.add(phonePanel)

        val msgPanel = JPanel(BorderLayout())
        msgPanel.add(JLabel("Message: "), BorderLayout.WEST)
        msgPanel.add(JScrollPane(msgArea), BorderLayout.CENTER)
        bottomPanel.add(msgPanel)

        sendBtn.addActionListener {
            sendSms()
        }
        bottomPanel.add(sendBtn)

        mainPanel.add(bottomPanel, BorderLayout.SOUTH)
        add(mainPanel)
    }

    private fun startBle() {
        client.start(
            onStatusChange = { status ->
                SwingUtilities.invokeLater {
                    statusLabel.text = status
                }
            },
            onEvent = { message ->
                SwingUtilities.invokeLater {
                    handleBleEvent(message)
                }
            }
        )
    }

    private fun handleBleEvent(message: BLEMessage) {
        logArea.append("[EVENT] ${message.action}\n")
        if (message.action == "sms_received") {
            val payload = BLECodec.json.decodeFromJsonElement(SmsReceivedPayload.serializer(), message.payload!!)
            logArea.append("SMS from ${payload.from}: ${payload.text}\n")
        }
    }

    private fun sendSms() {
        val phone = phoneField.text
        val text = msgArea.text
        if (phone.isBlank() || text.isBlank()) {
            JOptionPane.showMessageDialog(this, "Please enter phone and message")
            return
        }

        val request = hu.infokristaly.bluetoothsmsgateway.ble.BLEProtocol.sendSms(
            System.currentTimeMillis(),
            phone,
            text
        )
        client.sendCommand(request)
        logArea.append("[SENT] SMS to $phone\n")
        msgArea.text = ""
    }
}

fun main() {
    SwingUtilities.invokeLater {
        SwingClient().isVisible = true
    }
}
