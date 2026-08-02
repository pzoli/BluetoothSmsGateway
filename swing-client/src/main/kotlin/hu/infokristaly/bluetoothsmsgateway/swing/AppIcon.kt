package hu.infokristaly.bluetoothsmsgateway.swing

import java.awt.*
import java.awt.geom.*
import java.awt.image.BufferedImage

object AppIcon {

    fun createAppIcon(size: Int): Image {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        renderIcon(g, size)
        g.dispose()
        return image
    }

    private fun renderIcon(g: Graphics2D, size: Int) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        val center = size / 2.0
        val radius = size / 2.0
        
        // 1. Background: Radial Gradient
        val colors = arrayOf(Color(0x283593), Color(0x1A237E))
        val dist = floatArrayOf(0.0f, 1.0f)
        val paint = RadialGradientPaint(center.toFloat(), center.toFloat(), radius.toFloat(), dist, colors)
        g.paint = paint
        g.fill(Ellipse2D.Double(0.0, 0.0, size.toDouble(), size.toDouble()))

        // 2. Message Bubble (SMS Symbol)
        val bW = size * 0.54
        val bH = size * 0.38
        val bX = (size - bW) / 2.0
        val bY = (size - bH) / 2.0
        
        g.color = Color.WHITE
        val bubble = RoundRectangle2D.Double(bX, bY, bW, bH, size * 0.06, size * 0.06)
        g.fill(bubble)
        
        // Bubble Tail
        val tail = Path2D.Double()
        tail.moveTo(bX + bW * 0.4, bY + bH)
        tail.lineTo(bX + bW * 0.3, bY + bH + size * 0.08)
        tail.lineTo(bX + bW * 0.5, bY + bH)
        tail.closePath()
        g.fill(tail)

        // 3. Bluetooth Symbol
        g.color = Color(0x1A237E)
        g.stroke = BasicStroke((size * 0.03).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        
        val btW = bW * 0.3
        val btH = bH * 0.6
        val btX = bX + (bW - btW) / 2.0
        val btY = bY + (bH - btH) / 2.0
        
        val path = Path2D.Double()
        path.moveTo(btX, btY + btH * 0.25)
        path.lineTo(btX + btW, btY + btH * 0.75)
        path.lineTo(btX + btW * 0.5, btY + btH)
        path.lineTo(btX + btW * 0.5, btY)
        path.lineTo(btX + btW, btY + btH * 0.25)
        path.lineTo(btX, btY + btH * 0.75)
        g.draw(path)
    }

    fun saveToPng(file: java.io.File, size: Int) {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        renderIcon(g, size)
        g.dispose()
        javax.imageio.ImageIO.write(image, "PNG", file)
    }
}
