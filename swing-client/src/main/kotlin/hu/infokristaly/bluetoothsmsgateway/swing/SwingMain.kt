package hu.infokristaly.bluetoothsmsgateway.swing

import com.formdev.flatlaf.themes.FlatMacDarkLaf
import hu.infokristaly.bluetoothsmsgateway.ble.BLECodec
import hu.infokristaly.bluetoothsmsgateway.ble.BLEMessage
import hu.infokristaly.bluetoothsmsgateway.ble.SmsReceivedPayload
import hu.infokristaly.bluetoothsmsgateway.client.KableBleClient
import kotlinx.coroutines.*
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

class SwingClient : JFrame("Bluetooth SMS Gateway") {

    private val client = KableBleClient()
    private val logPane = JTextPane()
    private val phoneField = JTextField()
    private val msgArea = JTextArea(3, 20)
    private val sendBtn = JButton("Send SMS")
    private val statusLabel = JLabel("Status: Disconnected")
    private val connectionSwitch = JToggleButton("Connect")

    init {
        setupUI()
    }

    private fun setupUI() {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        
        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent?) {
                println("Closing application...")
                runBlocking {
                    client.disconnectSync()
                }
                System.exit(0)
            }
        })
        
        size = Dimension(600, 700)
        setLocationRelativeTo(null)

        val mainPanel = JPanel(BorderLayout(15, 15))
        mainPanel.border = EmptyBorder(20, 20, 20, 20)

        // Status Header
        val headerPanel = JPanel(BorderLayout())
        statusLabel.font = statusLabel.font.deriveFont(Font.BOLD, 14f)
        statusLabel.foreground = Color(0xAAAAAA)
        headerPanel.add(statusLabel, BorderLayout.WEST)
        
        val titleLabel = JLabel("SMSGW Client")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 18f)
        headerPanel.add(titleLabel, BorderLayout.CENTER)
        titleLabel.horizontalAlignment = SwingConstants.CENTER

        // Switch Toggle
        connectionSwitch.putClientProperty("JButton.buttonType", "switch")
        connectionSwitch.addActionListener {
            if (connectionSwitch.isSelected) {
                startBle()
            } else {
                stopBle()
            }
        }
        headerPanel.add(connectionSwitch, BorderLayout.EAST)
        
        mainPanel.add(headerPanel, BorderLayout.NORTH)

        // Log Area
        logPane.isEditable = false
        logPane.font = Font("Monospaced", Font.PLAIN, 12)
        val scrollPane = JScrollPane(logPane)
        scrollPane.border = BorderFactory.createTitledBorder("Activity Log")
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        // Control Panel
        val controlPanel = JPanel()
        controlPanel.layout = GridBagLayout()
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(5, 0, 5, 0)
        gbc.weightx = 1.0

        // Phone Input
        gbc.gridy = 0
        controlPanel.add(JLabel("Recipient Phone Number:"), gbc)
        
        gbc.gridy = 1
        phoneField.putClientProperty("JTextField.placeholderText", "+36301234567")
        controlPanel.add(phoneField, gbc)

        // Message Input
        gbc.gridy = 2
        controlPanel.add(JLabel("Message Text:"), gbc)
        
        gbc.gridy = 3
        msgArea.lineWrap = true
        msgArea.wrapStyleWord = true
        val msgScroll = JScrollPane(msgArea)
        msgScroll.preferredSize = Dimension(0, 80)
        controlPanel.add(msgScroll, gbc)

        // Send Button
        gbc.gridy = 4
        gbc.insets = Insets(15, 0, 0, 0)
        sendBtn.putClientProperty("JButton.buttonType", "roundRect")
        sendBtn.background = Color(0x3498DB)
        sendBtn.foreground = Color.WHITE
        sendBtn.font = sendBtn.font.deriveFont(Font.BOLD)
        sendBtn.isEnabled = false // Disabled until connected
        sendBtn.addActionListener {
            sendSms()
        }
        controlPanel.add(sendBtn, gbc)

        mainPanel.add(controlPanel, BorderLayout.SOUTH)
        add(mainPanel)
    }

    private fun startBle() {
        client.start(
            onStatusChange = { status ->
                SwingUtilities.invokeLater {
                    statusLabel.text = "Status: $status"
                    when (status) {
                        "Connected" -> {
                            statusLabel.foreground = Color(0x2ECC71)
                            sendBtn.isEnabled = true
                            connectionSwitch.isSelected = true
                        }
                        "Scanning", "Connecting" -> {
                            statusLabel.foreground = Color(0xF1C40F)
                            sendBtn.isEnabled = false
                        }
                        else -> {
                            statusLabel.foreground = Color(0xE74C3C)
                            sendBtn.isEnabled = false
                            connectionSwitch.isSelected = false
                        }
                    }
                }
            },
            onLog = { message ->
                SwingUtilities.invokeLater {
                    appendLog("Log", message, Color.GRAY)
                }
            },
            onEvent = { message ->
                SwingUtilities.invokeLater {
                    handleBleEvent(message)
                }
            }
        )
    }

    private fun stopBle() {
        client.stop()
        sendBtn.isEnabled = false
        statusLabel.text = "Status: Disconnected"
        statusLabel.foreground = Color(0xAAAAAA)
        appendLog("System", "Manual disconnection triggered", Color.LIGHT_GRAY)
    }

    private fun handleBleEvent(message: BLEMessage) {
        if (message.type == hu.infokristaly.bluetoothsmsgateway.ble.MessageType.response) {
            val color = if (message.status == hu.infokristaly.bluetoothsmsgateway.ble.Status.ok) Color(0x3498DB) else Color(0xE74C3C)
            appendLog("RESPONSE", "${message.status} (ID: ${message.id})", color)
            return
        }
        
        if (message.action == "sms_received") {
            val payload = BLECodec.json.decodeFromJsonElement(SmsReceivedPayload.serializer(), message.payload!!)
            appendLog("SMS", "From ${payload.from}: ${payload.text}", Color(0x2ECC71))
        } else {
            appendLog("EVENT", "${message.action}", Color.ORANGE)
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
        appendLog("SENDING", "To $phone: $text", Color.WHITE)
        msgArea.text = ""
    }

    private fun appendLog(tag: String, text: String, color: Color) {
        val doc = logPane.styledDocument
        val attr = SimpleAttributeSet()
        
        // Tag
        StyleConstants.setBold(attr, true)
        StyleConstants.setForeground(attr, color)
        doc.insertString(doc.length, "[$tag] ", attr)
        
        // Text
        StyleConstants.setBold(attr, false)
        StyleConstants.setForeground(attr, Color.WHITE)
        doc.insertString(doc.length, "$text\n", attr)
        
        logPane.caretPosition = doc.length
    }
}

fun main() {
    FlatMacDarkLaf.setup()
    UIManager.put("Component.focusWidth", 1)
    UIManager.put("Button.arc", 10)
    UIManager.put("Component.arc", 10)
    
    SwingUtilities.invokeLater {
        SwingClient().isVisible = true
    }
}
