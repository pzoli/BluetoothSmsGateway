package hu.infokristaly.bluetoothsmsgateway.swing

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.client.j2se.MatrixToImageWriter
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.image.BufferedImage
import javax.swing.*
import javax.swing.border.EmptyBorder

class SettingsDialog(parent: JFrame, private val onKeyUpdate: (String) -> Unit) : JDialog(parent, "Security Settings", true) {

    private val keyLabel = JLabel()
    private val qrLabel = JLabel("", SwingConstants.CENTER)

    init {
        setupUI()
        updateUIWithKey(KeypassManager.currentKeypass)
    }

    private fun setupUI() {
        size = Dimension(450, 600)
        setLocationRelativeTo(parent)
        layout = BorderLayout(10, 10)

        val mainPanel = JPanel(BorderLayout(15, 15))
        mainPanel.border = EmptyBorder(20, 20, 20, 20)

        // Key Display
        val keyPanel = JPanel(BorderLayout(5, 5))
        keyPanel.add(JLabel("Authentication Key:"), BorderLayout.NORTH)
        keyLabel.font = Font("Monospaced", Font.BOLD, 14)
        keyLabel.foreground = Color(0x3498DB)
        keyPanel.add(keyLabel, BorderLayout.CENTER)
        
        val regenBtn = JButton("Regenerate Key")
        regenBtn.addActionListener {
            val newKey = KeypassManager.generateNewKeypass()
            updateUIWithKey(newKey)
            onKeyUpdate(newKey)
        }
        keyPanel.add(regenBtn, BorderLayout.SOUTH)
        
        mainPanel.add(keyPanel, BorderLayout.NORTH)

        // QR Code
        qrLabel.border = BorderFactory.createTitledBorder("Scan this with Android App")
        mainPanel.add(qrLabel, BorderLayout.CENTER)

        val footer = JLabel("<html><center>Each message sent via BLE will be signed with this key.<br>Ensure the Android Gateway has scanned this code.</center></html>")
        footer.horizontalAlignment = SwingConstants.CENTER
        footer.foreground = Color.GRAY
        mainPanel.add(footer, BorderLayout.SOUTH)

        add(mainPanel)
    }

    private fun updateUIWithKey(key: String) {
        keyLabel.text = key
        generateQRCode(key)
    }

    private fun generateQRCode(text: String) {
        try {
            val width = 350
            val height = 350
            val matrix = MultiFormatWriter().encode(
                text, BarcodeFormat.QR_CODE, width, height
            )
            val image = MatrixToImageWriter.toBufferedImage(matrix)
            qrLabel.icon = ImageIcon(image)
        } catch (e: Exception) {
            qrLabel.text = "Error generating QR: ${e.message}"
        }
    }
}
