package sfv2.app

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/** Draws the tray icon (route + card; waiting adds a filled dot) with the DESIGN.md colors, on a transparent ground. */
object TrayIcons {
    val SIZES = listOf(22, 24, 48)

    private val TEXT = Color(0xF2F1EE)
    private val MUTED = Color(0xA3ABB6)
    private val SAND = Color(0xC7B9A6)

    fun render(size: Int, waiting: Boolean): BufferedImage {
        val s = size.toDouble()
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            val stroke = maxOf(1.6f, size * 0.085f)

            g.color = if (waiting) SAND else MUTED
            g.stroke = BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            val route = Path2D.Double()
            route.moveTo(s * 0.14, s * 0.80)
            route.lineTo(s * 0.14, s * 0.52)
            route.lineTo(s * 0.40, s * 0.52)
            route.lineTo(s * 0.40, s * 0.30)
            g.draw(route)
            val r = s * 0.075
            g.fill(Ellipse2D.Double(s * 0.14 - r, s * 0.80 - r, 2 * r, 2 * r))

            g.color = TEXT
            g.draw(RoundRectangle2D.Double(s * 0.52, s * 0.42, s * 0.36, s * 0.44, s * 0.10, s * 0.10))

            if (waiting) {
                g.color = SAND
                val d = s * 0.15
                g.fill(Ellipse2D.Double(s * 0.80 - d, s * 0.20 - d, 2 * d, 2 * d))
            }
        } finally {
            g.dispose()
        }
        return image
    }

    /** The image as the StatusNotifierItem `IconPixmap` wants it: ARGB, network byte order. */
    fun pixmap(size: Int, waiting: Boolean): IconPixmap {
        val image = render(size, waiting)
        val bytes = ByteArray(size * size * 4)
        var at = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                val argb = image.getRGB(x, y)
                bytes[at++] = (argb ushr 24).toByte()
                bytes[at++] = (argb ushr 16).toByte()
                bytes[at++] = (argb ushr 8).toByte()
                bytes[at++] = argb.toByte()
            }
        }
        return IconPixmap(size, size, bytes)
    }
}
