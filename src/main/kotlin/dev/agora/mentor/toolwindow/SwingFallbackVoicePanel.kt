package dev.agora.mentor.toolwindow

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import dev.agora.mentor.services.AgoraSessionService.ActiveAgoraSession
import dev.agora.mentor.toolwindow.AgoraAssistantPanel.Companion.muted
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class SwingFallbackVoicePanel(
    private val session: ActiveAgoraSession,
    private val onOpenBrowser: (() -> Unit)? = null,
) : JPanel(BorderLayout()), VoiceRuntimeHandle {

    override val component: JComponent = this

    init {
        border = JBUI.Borders.empty(16)
        add(buildBody(), BorderLayout.CENTER)
    }

    override fun stop() { /* nothing to stop — browser tab is external */ }

    private fun buildBody(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        val icon = JBLabel("🌐").apply {
            font = font.deriveFont(28f)
            alignmentX = JLabel.CENTER_ALIGNMENT
        }
        val title = JBLabel("Embedded browser unavailable").apply {
            font = JBUI.Fonts.label().deriveFont(Font.BOLD, 14f)
            alignmentX = JLabel.CENTER_ALIGNMENT
        }
        val sub = JBLabel(
            "<html><div style='text-align:center;'>" +
            "The Agora agent is running. Open the voice client in your browser to connect audio." +
            "</div></html>"
        ).apply {
            foreground = muted()
            alignmentX = JLabel.CENTER_ALIGNMENT
        }

        val fixHint = JBLabel(
            "<html><div style='text-align:center; color:gray; font-size:11px;'>" +
            "To enable the embedded panel: <b>Settings → Tools → Experimental → Built-in browser</b> → restart." +
            "</div></html>"
        ).apply { alignmentX = JLabel.CENTER_ALIGNMENT }

        val openBtn = JButton("Open Voice Client in Browser").apply {
            alignmentX = JLabel.CENTER_ALIGNMENT
            addActionListener { onOpenBrowser?.invoke() }
        }

        panel.add(Box.createVerticalGlue())
        panel.add(icon)
        panel.add(Box.createVerticalStrut(JBUI.scale(10)))
        panel.add(title)
        panel.add(Box.createVerticalStrut(JBUI.scale(6)))
        panel.add(sub)
        panel.add(Box.createVerticalStrut(JBUI.scale(16)))
        panel.add(openBtn)
        panel.add(Box.createVerticalStrut(JBUI.scale(12)))
        panel.add(fixHint)
        panel.add(Box.createVerticalGlue())
        return panel
    }
}
