package dev.agora.mentor.toolwindow

import javax.swing.JComponent

interface VoiceRuntimeHandle {
    val component: JComponent
    fun stop()
}
