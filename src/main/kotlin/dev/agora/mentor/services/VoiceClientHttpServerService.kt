package dev.agora.mentor.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import dev.agora.mentor.toolwindow.jcef.VoiceClientPageBuilder
import java.awt.Desktop
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import com.sun.net.httpserver.HttpServer

@Service(Service.Level.PROJECT)
class VoiceClientHttpServerService(private val project: Project) : Disposable {

    private val logger = thisLogger()
    private val htmlRef = AtomicReference<String?>(null)
    private val serverLock = Any()

    @Volatile private var server: HttpServer? = null
    @Volatile private var port: Int = -1

    // Registered by AgoraVoiceRuntimePanel so JS notify() calls reach the plugin UI.
    @Volatile private var eventListener: ((String) -> Unit)? = null

    // ── Public API ────────────────────────────────────────────────────────────

    fun setEventListener(listener: (String) -> Unit) { eventListener = listener }
    fun clearEventListener() { eventListener = null }

    fun sessionUrl(session: AgoraSessionService.ActiveAgoraSession): String {
        htmlRef.set(VoiceClientPageBuilder.buildHtml(project, session))
        ensureServer()
        return "http://127.0.0.1:$port/voice-client"
    }

    fun openInBrowser(session: AgoraSessionService.ActiveAgoraSession) {
        if (!Desktop.isDesktopSupported()) {
            throw IOException("Desktop browser launching is not supported on this system.")
        }
        Desktop.getDesktop().browse(URI.create(sessionUrl(session)))
    }

    // ── Disposable ────────────────────────────────────────────────────────────

    override fun dispose() {
        clearEventListener()
        server?.stop(0)
        server = null
        logger.info("Voice client HTTP server stopped.")
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun ensureServer() {
        if (server != null) return
        synchronized(serverLock) {
            if (server != null) return

            val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

            // Main voice-client page
            httpServer.createContext("/voice-client") { exchange ->
                val html  = htmlRef.get() ?: "<html><body>Voice client not initialised.</body></html>"
                val bytes = html.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }

            // JS event bridge: fetch('/event?type=…') → plugin UI update
            httpServer.createContext("/event") { exchange ->
                val type = exchange.requestURI.query
                    ?.split("&")
                    ?.firstOrNull { it.startsWith("type=") }
                    ?.removePrefix("type=")
                    ?: "unknown"
                logger.info("Voice event received: $type")
                exchange.responseHeaders.add("Cache-Control", "no-store")
                exchange.sendResponseHeaders(200, -1)
                exchange.responseBody.close()
                eventListener?.let { listener ->
                    SwingUtilities.invokeLater { listener(type) }
                }
            }

            // ConvoAI client toolkit — bundled (~72 KB), loaded as ES module by the page
            httpServer.createContext("/sdk/toolkit.mjs") { exchange ->
                serveResource(exchange, "/sdk/toolkit.mjs", "application/javascript; charset=utf-8")
            }

            httpServer.executor = Executors.newCachedThreadPool()
            httpServer.start()
            server = httpServer
            port   = httpServer.address.port
            logger.info("Voice client HTTP server started at http://127.0.0.1:$port/voice-client")
        }
    }

    private fun serveResource(
        exchange: com.sun.net.httpserver.HttpExchange,
        resourcePath: String,
        contentType: String,
    ) {
        val bytes = javaClass.getResourceAsStream(resourcePath)?.readBytes()
        if (bytes == null) {
            val msg = "Plugin resource not found: $resourcePath"
            val err = msg.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
            exchange.sendResponseHeaders(404, err.size.toLong())
            exchange.responseBody.use { it.write(err) }
            logger.warn(msg)
            return
        }
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
