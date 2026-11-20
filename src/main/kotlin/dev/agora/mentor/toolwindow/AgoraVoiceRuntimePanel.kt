package dev.agora.mentor.toolwindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import dev.agora.mentor.services.AgoraSessionService.ActiveAgoraSession
import dev.agora.mentor.services.JcefAvailabilityService
import dev.agora.mentor.services.VoiceClientHttpServerService
import dev.agora.mentor.toolwindow.AgoraAssistantPanel.Companion.border
import dev.agora.mentor.toolwindow.AgoraAssistantPanel.Companion.monoArea
import dev.agora.mentor.toolwindow.AgoraAssistantPanel.Companion.muted
import dev.agora.mentor.toolwindow.jcef.JcefVoicePanelFactory
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

class AgoraVoiceRuntimePanel(
    private val project: Project,
    private val onVoiceEvent: ((String) -> Unit)? = null,
) : JPanel(BorderLayout()) {

    private val logger = thisLogger()
    @Suppress("unused")
    private val jcefService = project.service<JcefAvailabilityService>()

    // ── Dashboard labels ──────────────────────────────────────────────────────
    private val channelLabel   = dashValue("—")
    private val agentLabel     = dashValue("—")
    private val uidLabel       = dashValue("—")
    private val expiresLabel   = dashValue("—")
    private val transportLabel = dashValue("—")

    // ── Technical details (collapsible) ───────────────────────────────────────
    private val techDetailsArea  = monoArea().apply { rows = 4 }
    private val techDetailsPanel = buildTechDetails()
    private var techExpanded     = false

    // ── Browser host ──────────────────────────────────────────────────────────
    private val browserHost = JPanel(BorderLayout())

    // ── Copy button ───────────────────────────────────────────────────────────
    private val copyButton = JButton("Copy Details")

    private var currentSession: ActiveAgoraSession? = null
    private var currentRuntime: VoiceRuntimeHandle? = null

    init {
        add(buildDashboard(), BorderLayout.NORTH)
        add(browserHost,      BorderLayout.CENTER)
        showEmptyState()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun loadSession(session: ActiveAgoraSession) {
        currentRuntime?.stop()
        currentSession = session

        val jcefRuntime  = JcefVoicePanelFactory.create(project, session)
        val jcefWorking  = jcefRuntime != null
        logger.info("JCEF voice runtime created: $jcefWorking")

        val runtime = jcefRuntime ?: run {
            logger.info("JCEF unavailable — using Swing fallback.")
            SwingFallbackVoicePanel(session) {
                runCatching { project.service<VoiceClientHttpServerService>().openInBrowser(session) }
                    .onFailure { logger.warn("openInBrowser failed", it) }
            }
        }

        project.service<VoiceClientHttpServerService>().setEventListener { event ->
            handleVoiceEvent(event)
        }

        currentRuntime = runtime
        updateDashboard(session, jcefWorking)
        swapBrowserHost(runtime)
    }

    fun stopSession() {
        currentRuntime?.stop()
        project.service<VoiceClientHttpServerService>().clearEventListener()
    }

    fun clearStatus() {
        SwingUtilities.invokeLater {
            currentSession  = null
            currentRuntime  = null
            project.service<VoiceClientHttpServerService>().clearEventListener()
            channelLabel.text   = "—"
            agentLabel.text     = "—"
            uidLabel.text       = "—"
            expiresLabel.text   = "—"
            transportLabel.text = "—"
            techDetailsArea.text = ""
            showEmptyState()
        }
    }

    private fun handleVoiceEvent(event: String) {
        onVoiceEvent?.invoke(event)
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    private fun buildDashboard(): JPanel {
        val dashboard = JPanel(BorderLayout(0, 0)).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(border(), 0, 0, 1, 0),
                JBUI.Borders.empty(8, 12)
            )
        }

        val topRow = JPanel(BorderLayout()).apply { isOpaque = false }
        topRow.add(JBLabel("Voice Runtime").apply {
            font = JBUI.Fonts.label().deriveFont(Font.BOLD)
        }, BorderLayout.WEST)
        topRow.add(copyButton.apply {
            font = JBUI.Fonts.miniFont()
            addActionListener { copySessionDetails() }
        }, BorderLayout.EAST)

        val grid = buildMetricGrid()

        val expandBtn = JButton("▶ Technical Details").apply {
            font = JBUI.Fonts.miniFont()
            isBorderPainted    = false
            isContentAreaFilled = false
            foreground         = muted()
            addActionListener {
                techExpanded = !techExpanded
                text = if (techExpanded) "▼ Technical Details" else "▶ Technical Details"
                techDetailsPanel.isVisible = techExpanded
                dashboard.revalidate()
            }
        }
        val expandRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false; add(expandBtn)
        }

        val body = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(topRow)
            add(Box.createVerticalStrut(JBUI.scale(8)))
            add(grid)
            add(Box.createVerticalStrut(JBUI.scale(4)))
            add(expandRow)
            add(techDetailsPanel)
        }

        dashboard.add(body, BorderLayout.CENTER)
        return dashboard
    }

    private fun buildMetricGrid(): JPanel {
        val grid = JPanel(GridLayout(2, 3, JBUI.scale(12), JBUI.scale(6))).apply { isOpaque = false }
        grid.add(metricCell("Channel",   channelLabel))
        grid.add(metricCell("Agent",     agentLabel))
        grid.add(metricCell("Voice UID", uidLabel))
        grid.add(metricCell("Expires",   expiresLabel))
        grid.add(metricCell("Transport", transportLabel))
        grid.add(JPanel().apply { isOpaque = false })
        return grid
    }

    private fun buildTechDetails(): JPanel = JPanel(BorderLayout()).apply {
        isOpaque  = false
        isVisible = false
        border    = JBUI.Borders.emptyTop(6)
        add(JBScrollPane(techDetailsArea).apply {
            border          = BorderFactory.createEmptyBorder()
            preferredSize   = Dimension(0, JBUI.scale(80))
        }, BorderLayout.CENTER)
    }

    // ── Browser host ──────────────────────────────────────────────────────────

    private fun showEmptyState() {
        browserHost.removeAll()
        browserHost.add(buildEmptyState(), BorderLayout.CENTER)
        browserHost.revalidate()
        browserHost.repaint()
    }

    private fun swapBrowserHost(runtime: VoiceRuntimeHandle) {
        browserHost.removeAll()
        browserHost.add(runtime.component, BorderLayout.CENTER)
        browserHost.revalidate()
        browserHost.repaint()
    }

    private fun buildEmptyState(): JPanel {
        val panel  = JPanel(BorderLayout()).apply { isOpaque = false }
        val center = JPanel().apply {
            layout   = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        val icon = JBLabel("🎙").apply {
            font       = font.deriveFont(32f)
            alignmentX = JLabel.CENTER_ALIGNMENT
        }
        val title = JBLabel("No active voice session").apply {
            font       = JBUI.Fonts.label().deriveFont(Font.BOLD)
            alignmentX = JLabel.CENTER_ALIGNMENT
        }
        val hint = JBLabel("Start a voice session to load the embedded voice client.").apply {
            foreground = muted()
            alignmentX = JLabel.CENTER_ALIGNMENT
        }
        center.add(Box.createVerticalGlue())
        center.add(icon)
        center.add(Box.createVerticalStrut(JBUI.scale(8)))
        center.add(title)
        center.add(Box.createVerticalStrut(JBUI.scale(4)))
        center.add(hint)
        center.add(Box.createVerticalGlue())
        panel.add(center, BorderLayout.CENTER)
        return panel
    }

    // ── Dashboard update ──────────────────────────────────────────────────────

    private fun updateDashboard(session: ActiveAgoraSession, jcefWorking: Boolean) {
        val fmt       = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
        val expiresAt = fmt.format(session.expiresAt)

        channelLabel.text   = truncate(session.channel, 24)
        agentLabel.text     = truncate(session.agentId, 16)
        uidLabel.text       = session.voiceBootstrap.rtcUid.toString()
        expiresLabel.text   = expiresAt
        transportLabel.text = if (jcefWorking) "Embedded (JCEF)" else "External browser"

        techDetailsArea.text = buildTechText(session, jcefWorking)
    }

    private fun buildTechText(session: ActiveAgoraSession, jcefWorking: Boolean): String = buildString {
        appendLine("Project   : ${project.name}")
        appendLine("Channel   : ${session.channel}")
        appendLine("Agent ID  : ${session.agentId}")
        appendLine("Voice UID : ${session.voiceBootstrap.rtcUid}")
        appendLine("RTM user  : ${session.voiceBootstrap.rtmUserId}")
        val fmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
        appendLine("Expires   : ${fmt.format(session.expiresAt)} (1-hour limit)")
        append("Transport : ${if (jcefWorking) "Embedded JCEF browser" else "External browser (JCEF unavailable)"}")
    }

    private fun copySessionDetails() {
        val text = techDetailsArea.text.ifBlank { "No active session." }
        val sel  = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
    }

    // ── Factories ─────────────────────────────────────────────────────────────

    private fun metricCell(label: String, value: JBLabel): JPanel {
        val cell = JPanel().apply {
            layout   = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        val lbl = JBLabel(label).apply {
            font       = JBUI.Fonts.miniFont()
            foreground = muted()
        }
        cell.add(lbl)
        cell.add(Box.createVerticalStrut(JBUI.scale(2)))
        cell.add(value)
        return cell
    }

    private fun dashValue(text: String) = JBLabel(text).apply {
        font = JBUI.Fonts.label().deriveFont(Font.BOLD)
    }

    private fun truncate(s: String, max: Int) = if (s.length <= max) s else s.take(max - 1) + "…"
}
