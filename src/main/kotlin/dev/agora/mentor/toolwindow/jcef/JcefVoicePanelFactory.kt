package dev.agora.mentor.toolwindow.jcef

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import dev.agora.mentor.services.AgoraSessionService.ActiveAgoraSession
import dev.agora.mentor.services.VoiceClientHttpServerService
import dev.agora.mentor.toolwindow.VoiceRuntimeHandle
import javax.swing.JComponent

object JcefVoicePanelFactory {

    private val logger = thisLogger()

    fun create(project: Project, session: ActiveAgoraSession): VoiceRuntimeHandle? {
        // Some Android Studio builds ship JCEF but leave ide.browser.jcef.enabled=false.
        // Try to flip the flag before we attempt to construct the browser.
        enableJcefIfNeeded()

        return runCatching {
            val browserClass = Class.forName("com.intellij.ui.jcef.JBCefBrowser")
            val browser = browserClass.getDeclaredConstructor().newInstance()
            val component = browserClass.getMethod("getComponent").invoke(browser) as JComponent
            val url = project.service<VoiceClientHttpServerService>().sessionUrl(session)
            browserClass.getMethod("loadURL", String::class.java).invoke(browser, url)
            logger.info("Created JCEF voice runtime for ${session.channel}.")
            ReflectiveJcefVoiceHandle(browser, component)
        }.getOrElse { error ->
            logger.warn("Failed to create JCEF voice runtime: ${error.message ?: error::class.java.simpleName}", error)
            null
        }
    }

    private fun enableJcefIfNeeded() {
        runCatching {
            val key = Registry.get("ide.browser.jcef.enabled")
            if (!key.asBoolean()) {
                key.setValue(true)
                logger.info("Enabled JCEF via ide.browser.jcef.enabled registry key.")
            }
        }.onFailure {
            logger.info("Could not read/set ide.browser.jcef.enabled registry key: ${it.message}")
        }
    }

    fun openBrowser(project: Project, session: ActiveAgoraSession) {
        project.service<VoiceClientHttpServerService>().openInBrowser(session)
    }

    private class ReflectiveJcefVoiceHandle(
        private val browser: Any,
        override val component: JComponent,
    ) : VoiceRuntimeHandle {

        override fun stop() {
            runCatching {
                val cefBrowser = browser.javaClass.getMethod("getCefBrowser").invoke(browser)
                cefBrowser.javaClass.getMethod(
                    "executeJavaScript",
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType,
                ).invoke(
                    cefBrowser,
                    "window.agoraMentor && window.agoraMentor.stop && window.agoraMentor.stop();",
                    "",
                    0,
                )
            }
        }
    }
}
