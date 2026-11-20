package dev.agora.mentor.toolwindow

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.BrowserUtil
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import dev.agora.mentor.model.SelectedCodeContext
import dev.agora.mentor.prompt.AndroidSkillsPromptBuilder
import dev.agora.mentor.services.AgoraSessionService
import dev.agora.mentor.services.SelectionContextService
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.UIManager

class AgoraAssistantPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    // ── Services ─────────────────────────────────────────────────────────────
    private val selectionContextService = project.service<SelectionContextService>()
    private val sessionService          = project.service<AgoraSessionService>()
    private val promptBuilder           = AndroidSkillsPromptBuilder()
    private val voiceRuntimePanel       = AgoraVoiceRuntimePanel(project, ::onVoiceEvent)

    // Application-level props — NOT stored in .idea/ (per-user, not per-project)
    private val appProps = PropertiesComponent.getInstance()

    // ── Navigation ────────────────────────────────────────────────────────────
    private val tabs = JBTabbedPane()

    // ── Code tab ──────────────────────────────────────────────────────────────
    private val selectionStatusLabel = JBLabel("Right-click code → Ask Agora Conversational AI").apply {
        foreground = muted(); font = JBUI.Fonts.miniFont()
    }
    private val fileLabel           = metaLabel()
    private val languageLabel       = metaLabel()
    private val projectLabel        = metaLabel()
    private val selectionStatsLabel = metaLabel()
    private val filePathLabel       = metaLabel()
    private val codePreviewArea     = monoArea()
    private val contextPreviewArea  = monoArea()

    // ── Prompt tab ────────────────────────────────────────────────────────────
    private val promptPreviewArea = monoArea()
    private val copyPromptButton  = JButton("Copy")

    // ── Voice tab ─────────────────────────────────────────────────────────────
    private val transcriptArea = monoArea()
    private val userInputField = JBTextField().apply { emptyText.setText("Send a text message to the agent…") }
    private val startButton    = JButton("▶  Start Voice Session")
    private val stopButton     = JButton("■  Stop")
    private val sendButton     = JButton("Send")
    private val clearButton    = JButton("Clear")

    // ── Settings tab ──────────────────────────────────────────────────────────
    private val appIdField          = JBTextField()
    private val appCertificateField = JBTextField()
    private val agentUidField       = JBTextField()
    private val geofenceCombo       = JComboBox(AgoraSessionService.GEOFENCE_OPTIONS.toTypedArray())
    private val settingsStatusLabel = JBLabel().apply { font = JBUI.Fonts.miniFont(); foreground = muted() }

    // ── Advanced tab ──────────────────────────────────────────────────────────
    private val debugArea = monoArea()

    // ── State ─────────────────────────────────────────────────────────────────
    private var currentContext: SelectedCodeContext? = null
    private var currentPrompt = "Select code in the editor to generate an Android Skills prompt."

    init {
        setContent(buildContent())
        wireActions()
        loadStoredCredentials()
        refreshContext()
    }

    fun refreshContext() {
        SwingUtilities.invokeLater {
            currentContext = selectionContextService.getLatestContext()
            updateViews()
        }
    }

    // ── Root ──────────────────────────────────────────────────────────────────

    private fun buildContent(): JPanel {
        val root = JPanel(BorderLayout())
        root.add(buildHeader(), BorderLayout.NORTH)
        root.add(buildTabs(),   BorderLayout.CENTER)
        return root
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private fun buildHeader(): JPanel {
        val header = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(border(), 0, 0, 1, 0),
                JBUI.Borders.empty(8, 12, 8, 12)
            )
        }
        val logoIcon  = loadAgoraLogo()
        val logoLabel = JBLabel(logoIcon)
        val subtitle  = JBLabel("Android Expert · Voice Mentor").apply {
            font = JBUI.Fonts.miniFont(); foreground = muted()
        }
        val left = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false
            add(logoLabel); add(Box.createVerticalStrut(JBUI.scale(2))); add(subtitle)
        }
        header.add(left, BorderLayout.WEST)
        return header
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────

    private fun buildTabs(): JBTabbedPane {
        tabs.addTab("Code",     buildCodeTab())
        tabs.addTab("Prompt",   buildPromptTab())
        tabs.addTab("Voice",    buildVoiceTab())
        tabs.addTab("Settings", buildSettingsTab())
        tabs.addTab("Advanced", buildAdvancedTab())
        return tabs
    }

    // ── Code tab ──────────────────────────────────────────────────────────────

    private fun buildCodeTab(): JPanel {
        val panel = tabPanel()

        val metaBar = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(border(), 0, 0, 1, 0),
                JBUI.Borders.empty(6, 0, 6, 0)
            ); isOpaque = false
        }
        val statusRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { isOpaque = false; add(selectionStatusLabel) }
        val metaRow   = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(10), 0)).apply {
            isOpaque = false
            add(fileLabel); add(languageLabel); add(projectLabel); add(selectionStatsLabel)
        }
        metaBar.add(JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false
            add(statusRow); add(metaRow)
        }, BorderLayout.CENTER)

        val center = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false
            add(contentCard("Selected Code")        { scrollArea(codePreviewArea,    130) }); add(vgap(8))
            add(buildQuickActions());                                                          add(vgap(8))
            add(contentCard("Surrounding Context")  { scrollArea(contextPreviewArea,  70) })
        }
        panel.add(metaBar, BorderLayout.NORTH)
        panel.add(JBScrollPane(center).apply {
            border = BorderFactory.createEmptyBorder()
            horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }, BorderLayout.CENTER)
        return panel
    }

    private fun buildQuickActions(): JPanel {
        data class Action(val label: String, val focus: String)
        val actions = listOf(
            Action("Explain Code",         "Explain what this code does"),
            Action("Find Bugs",            "Find potential bugs in this code"),
            Action("Improve Code",         "Suggest improvements for this code"),
            Action("Optimize",             "Identify performance issues in this code"),
            Action("Generate Tests",       "Suggest unit tests for this code"),
            Action("▶ Start Voice Mentor", ""),
        )
        val row = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))).apply { isOpaque = false }
        actions.forEach { (label, focus) ->
            row.add(JButton(label).apply {
                font = JBUI.Fonts.miniFont()
                addActionListener { if (focus.isEmpty()) startButton.doClick() else startVoiceWithFocus(focus) }
            })
        }
        return row
    }

    // ── Prompt tab ────────────────────────────────────────────────────────────

    private fun buildPromptTab(): JPanel {
        val panel = tabPanel()
        val actionRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
            isOpaque = false
            add(copyPromptButton)
            add(JButton("Regenerate").apply {
                addActionListener {
                    currentContext?.let { ctx ->
                        currentPrompt = promptBuilder.buildPrompt(ctx)
                        promptPreviewArea.text = currentPrompt
                    }
                }
            })
        }
        panel.add(contentCard("Android Skills Prompt", footer = actionRow) {
            scrollArea(promptPreviewArea, 0)
        }, BorderLayout.CENTER)
        return panel
    }

    // ── Voice tab ─────────────────────────────────────────────────────────────

    private fun buildVoiceTab(): JPanel {
        val panel = JPanel(BorderLayout(0, 0))
        panel.add(voiceRuntimePanel,    BorderLayout.CENTER)
        panel.add(buildVoiceControls(), BorderLayout.SOUTH)
        return panel
    }

    private fun buildVoiceControls(): JPanel {
        val panel = JPanel(BorderLayout(0, 0)).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(border(), 1, 0, 0, 0),
                JBUI.Borders.empty(8, 12)
            )
        }
        val inputRow = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            isOpaque = false; border = JBUI.Borders.emptyTop(6)
            add(userInputField, BorderLayout.CENTER); add(sendButton, BorderLayout.EAST)
        }
        val ctrlRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
            isOpaque = false; border = JBUI.Borders.emptyTop(6)
            add(startButton); add(stopButton); add(clearButton)
        }
        panel.add(scrollArea(transcriptArea, 68), BorderLayout.CENTER)
        panel.add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(inputRow, BorderLayout.NORTH); add(ctrlRow, BorderLayout.SOUTH)
        }, BorderLayout.SOUTH)
        return panel
    }

    // ── Settings tab ──────────────────────────────────────────────────────────

    private fun buildSettingsTab(): JPanel {
        val panel = tabPanel()
        val stack = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false
            add(buildConnectionCard()); add(vgap(12)); add(buildAccountCard())
        }
        panel.add(JBScrollPane(stack).apply {
            border = BorderFactory.createEmptyBorder()
            horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }, BorderLayout.CENTER)
        return panel
    }

    private fun buildConnectionCard(): JPanel {
        val card = settingsCard("Agora Connection")
        val rows = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false
            add(settingsRow("App ID",          appIdField));          add(vgap(6))
            add(settingsRow("App Certificate", appCertificateField)); add(vgap(6))
            add(settingsRow("Agent RTC UID",   agentUidField));       add(vgap(6))
            add(settingsRow("Geofence",        geofenceCombo))
            add(vgap(10))
            add(JSeparator(SwingConstants.HORIZONTAL).apply { maximumSize = Dimension(Int.MAX_VALUE, 1) })
            add(vgap(8))
        }
        val saveRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
            isOpaque = false
            add(JButton("Save").apply { addActionListener { saveCredentials() } })
            add(settingsStatusLabel)
        }
        val hints = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false
            add(JBLabel("<html>" +
                "<b>App ID</b> and <b>App Certificate</b> come from your Agora Console project. " +
                "The certificate is saved to the <b>OS keychain</b> — never written to project files or version control.<br><br>" +
                "<b>Agent RTC UID</b>: the numeric UID for the AI agent in the channel (e.g. <tt>123456</tt>).<br>" +
                "<b>Geofence</b>: routes traffic to the nearest Agora data centre." +
                "</html>").apply { foreground = muted(); font = JBUI.Fonts.miniFont() })
            add(vgap(6))
            add(JBLabel("<html><b>⚠ Security note:</b> For a shipped product, move token generation " +
                "to a secure backend so the App Certificate never leaves your server.</html>"
            ).apply { foreground = muted(); font = JBUI.Fonts.miniFont() })
        }
        card.add(JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false
            add(rows); add(saveRow); add(vgap(8)); add(hints)
        }, BorderLayout.CENTER)
        return card
    }

    private fun buildAccountCard(): JPanel {
        val card = settingsCard("Agora Account")
        val note = JBLabel(
            "<html>Agora Conversational AI includes <b>300 free minutes</b> per account. " +
            "Monitor usage in the Agora Console.</html>"
        ).apply { foreground = muted() }
        val btnRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(JButton("Open Agora Console").apply { addActionListener { BrowserUtil.browse(AGORA_CONSOLE_URL) } })
        }
        card.add(JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false
            add(note); add(vgap(10)); add(btnRow)
        }, BorderLayout.CENTER)
        return card
    }

    // ── Advanced tab ──────────────────────────────────────────────────────────

    private fun buildAdvancedTab(): JPanel {
        val panel = tabPanel()
        panel.add(contentCard("Debug Log") { scrollArea(debugArea, 0) }, BorderLayout.CENTER)
        return panel
    }

    // ── Voice event bridge ────────────────────────────────────────────────────

    private fun onVoiceEvent(@Suppress("UNUSED_PARAMETER") event: String) {
        // Events consumed inside AgoraVoiceRuntimePanel; kept here for future extension.
    }

    // ── Wire actions ──────────────────────────────────────────────────────────

    private fun wireActions() {
        startButton.addActionListener {
            val context = currentContext ?: run {
                appendTranscript("No selected code context — right-click code and choose 'Ask Agora Conversational AI'.")
                return@addActionListener
            }
            val result = sessionService.startSession(
                context        = context,
                prompt         = currentPrompt,
                appId          = appIdField.text.trim(),
                appCertificate = loadCertificate(),
                geofenceArea   = geofenceCombo.selectedItem as String,
                agentUid       = agentUidField.text.trim(),
            )
            appendTranscript(result); appendDebug(result)
            sessionService.getActiveSession()?.let { session ->
                voiceRuntimePanel.loadSession(session)
                tabs.selectedIndex = VOICE_TAB_INDEX
            }
        }

        stopButton.addActionListener {
            val result = sessionService.stopSession()
            appendTranscript(result); appendDebug(result)
            voiceRuntimePanel.stopSession()
        }

        sendButton.addActionListener {
            val message = userInputField.text.trim()
            if (message.isEmpty()) { appendTranscript("Type a message before sending."); return@addActionListener }
            appendTranscript("You: $message")
            val response = sessionService.sendTextMessage(message)
            appendTranscript(response); appendDebug(response)
            userInputField.text = ""
        }

        copyPromptButton.addActionListener {
            copyToClipboard(currentPrompt); appendDebug("Prompt copied to clipboard.")
        }

        clearButton.addActionListener {
            transcriptArea.text = ""; debugArea.text = ""; userInputField.text = ""
            voiceRuntimePanel.clearStatus()
        }
    }

    private fun startVoiceWithFocus(focus: String) {
        val context = currentContext ?: run {
            appendTranscript("Select code first to use quick actions.")
            tabs.selectedIndex = CODE_TAB_INDEX; return
        }
        val result = sessionService.startSession(
            context        = context,
            prompt         = promptBuilder.buildPrompt(context) + "\n\nUser instruction: $focus",
            appId          = appIdField.text.trim(),
            appCertificate = loadCertificate(),
            geofenceArea   = geofenceCombo.selectedItem as String,
            agentUid       = agentUidField.text.trim(),
        )
        appendTranscript(result); appendDebug(result)
        sessionService.getActiveSession()?.let { session ->
            voiceRuntimePanel.loadSession(session)
            tabs.selectedIndex = VOICE_TAB_INDEX
        }
    }

    // ── Update views ──────────────────────────────────────────────────────────

    private fun updateViews() {
        val context = currentContext
        if (context == null) {
            selectionStatusLabel.text       = "Right-click code → Ask Agora Conversational AI"
            selectionStatusLabel.foreground = muted()
            listOf(fileLabel, languageLabel, projectLabel, selectionStatsLabel, filePathLabel).forEach { it.text = "" }
            codePreviewArea.text = ""; contextPreviewArea.text = ""
            currentPrompt        = "Select code in the editor to generate an Android Skills prompt."
            promptPreviewArea.text = currentPrompt
            return
        }
        selectionStatusLabel.text       = context.fileName
        selectionStatusLabel.foreground = UIManager.getColor("Label.foreground")
        fileLabel.text           = "📄 ${context.fileName}"
        filePathLabel.text       = context.filePath
        languageLabel.text       = "{ } ${context.language}"
        projectLabel.text        = "⬡ ${context.projectName}"
        selectionStatsLabel.text = "${context.selectedCode.length} chars"
        codePreviewArea.text     = context.selectedCode.trimEnd();    codePreviewArea.caretPosition    = 0
        contextPreviewArea.text  = context.surroundingContext?.trimEnd() ?: "(no surrounding context)"
        contextPreviewArea.caretPosition = 0
        currentPrompt            = promptBuilder.buildPrompt(context)
        promptPreviewArea.text   = currentPrompt; promptPreviewArea.caretPosition = 0
        appendDebug("Selection: ${context.fileName} | ${context.language} | ${context.selectedCode.length} chars")
    }

    // ── Credential helpers ────────────────────────────────────────────────────

    private fun loadStoredCredentials() {
        appIdField.text    = appProps.getValue(APP_ID_KEY, "")
        agentUidField.text = appProps.getValue(AGENT_UID_KEY, "")
        val geofence = appProps.getValue(GEOFENCE_KEY, AgoraSessionService.GEOFENCE_OPTIONS.first())
        val idx = AgoraSessionService.GEOFENCE_OPTIONS.indexOf(geofence)
        if (idx >= 0) geofenceCombo.selectedIndex = idx
        appCertificateField.text = loadCertificate()
        settingsStatusLabel.text = if (appIdField.text.isBlank()) "Not configured" else "Loaded"
    }

    private fun saveCredentials() {
        appProps.setValue(APP_ID_KEY,    appIdField.text.trim())
        appProps.setValue(AGENT_UID_KEY, agentUidField.text.trim())
        appProps.setValue(GEOFENCE_KEY,  geofenceCombo.selectedItem as String)
        // Certificate stored in OS keychain — never written to files
        PasswordSafe.instance.set(
            CredentialAttributes(CREDENTIAL_SERVICE, CREDENTIAL_USER),
            Credentials(CREDENTIAL_USER, appCertificateField.text.trim())
        )
        settingsStatusLabel.text = "Saved ✓"
    }

    private fun loadCertificate(): String =
        PasswordSafe.instance.getPassword(
            CredentialAttributes(CREDENTIAL_SERVICE, CREDENTIAL_USER)
        ) ?: ""

    // ── Misc helpers ──────────────────────────────────────────────────────────

    private fun appendTranscript(message: String) {
        if (transcriptArea.text.isBlank()) transcriptArea.text = message
        else transcriptArea.append("\n\n$message")
        transcriptArea.caretPosition = transcriptArea.document.length
    }

    private fun appendDebug(message: String) {
        if (debugArea.text.isBlank()) debugArea.text = message
        else debugArea.append("\n\n$message")
        debugArea.caretPosition = debugArea.document.length
    }

    private fun copyToClipboard(text: String) {
        val sel = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
    }

    private fun loadAgoraLogo(): javax.swing.Icon {
        val raw = IconLoader.getIcon("/icons/agora-logo.svg", AgoraAssistantPanel::class.java)
        return IconUtil.scale(raw, null, 22f / 342.7f)
    }

    // ── Layout factories ──────────────────────────────────────────────────────

    private fun tabPanel() = JPanel(BorderLayout(0, 0)).apply { border = JBUI.Borders.empty(10, 12) }

    private fun contentCard(
        title: String,
        footer: JPanel? = null,
        body: () -> java.awt.Component,
    ): JPanel {
        val card = JPanel(BorderLayout(0, JBUI.scale(6))).apply {
            border = JBUI.Borders.compound(JBUI.Borders.customLine(border()), JBUI.Borders.empty(8, 10))
        }
        card.add(sectionTitle(title), BorderLayout.NORTH)
        card.add(body(),              BorderLayout.CENTER)
        footer?.let { card.add(it, BorderLayout.SOUTH) }
        return card
    }

    private fun settingsCard(title: String): JPanel =
        JPanel(BorderLayout(0, JBUI.scale(10))).apply {
            border = JBUI.Borders.compound(JBUI.Borders.customLine(border()), JBUI.Borders.empty(10, 12))
            add(JBLabel(title).apply { font = JBUI.Fonts.label().deriveFont(Font.BOLD, 13f) }, BorderLayout.NORTH)
        }

    private fun settingsRow(label: String, field: java.awt.Component): JPanel =
        JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            isOpaque = false
            add(JBLabel(label).apply {
                preferredSize = Dimension(JBUI.scale(110), JBUI.Fonts.label().size + JBUI.scale(6))
            }, BorderLayout.WEST)
            add(field, BorderLayout.CENTER)
        }

    private fun sectionTitle(text: String) = JBLabel(text).apply {
        font = JBUI.Fonts.miniFont().deriveFont(Font.BOLD); foreground = muted()
        border = JBUI.Borders.emptyBottom(4)
    }

    private fun scrollArea(area: JBTextArea, height: Int): JBScrollPane =
        JBScrollPane(area).apply {
            border = BorderFactory.createEmptyBorder()
            if (height > 0) preferredSize = Dimension(0, JBUI.scale(height))
        }

    private fun vgap(px: Int) = Box.createVerticalStrut(JBUI.scale(px))

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        // Application-level keys — stored in IDE global settings, NOT in .idea/
        private const val APP_ID_KEY    = "dev.agora.mentor.appId"
        private const val AGENT_UID_KEY = "dev.agora.mentor.agentUid"
        private const val GEOFENCE_KEY  = "dev.agora.mentor.geofence"

        // OS keychain identifiers for the App Certificate
        private const val CREDENTIAL_SERVICE = "dev.agora.mentor"
        private const val CREDENTIAL_USER    = "app_certificate"

        private const val AGORA_CONSOLE_URL = "https://console.agora.io"

        const val CODE_TAB_INDEX     = 0
        const val PROMPT_TAB_INDEX   = 1
        const val VOICE_TAB_INDEX    = 2
        const val SETTINGS_TAB_INDEX = 3
        const val ADVANCED_TAB_INDEX = 4

        val CHIP_IDLE:       Color get() = JBColor(Color(0x6E6E6E), Color(0x8C8C8C))
        val CHIP_CONNECTING: Color get() = JBColor(Color(0x9E6A03), Color(0xD29922))
        val CHIP_CONNECTED:  Color get() = JBColor(Color(0x2DA44E), Color(0x3FB950))
        val CHIP_ERROR:      Color get() = JBColor(Color(0xB31D28), Color(0xF85149))

        fun muted(): Color = UIManager.getColor("Label.disabledForeground")
            ?: JBColor(Color(0x6E6E6E), Color(0x8C8C8C))

        fun border(): Color = UIManager.getColor("Component.borderColor")
            ?: JBColor(Color(0xC9C9C9), Color(0x3C3F41))

        fun monoArea(): JBTextArea = JBTextArea().apply {
            isEditable = false; lineWrap = true; wrapStyleWord = true
            font       = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scale(12))
            background = UIManager.getColor("EditorPane.background") ?: UIManager.getColor("Panel.background")
        }

        private fun metaLabel() = JBLabel().apply { font = JBUI.Fonts.miniFont(); foreground = muted() }
    }
}
