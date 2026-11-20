package dev.agora.mentor.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger

@Service(Service.Level.PROJECT)
class JcefAvailabilityService {

    private val logger = thisLogger()

    @Volatile
    private var cached: Boolean? = null

    fun isJcefAvailable(): Boolean {
        cached?.let { return it }

        val available = runCatching {
            val appClass = Class.forName("com.intellij.ui.jcef.JBCefApp")
            val method = appClass.getMethod("isSupported")
            method.invoke(null) as Boolean
        }.getOrElse { error ->
            logger.info("JCEF unavailable: ${error.message ?: error::class.java.simpleName}")
            false
        }

        cached = available
        logger.info("JCEF availability checked: ${if (available) "available" else "unavailable"}")
        return available
    }

    fun availabilityReason(): String {
        return if (isJcefAvailable()) {
            "JCEF is available in this Android Studio build."
        } else {
            "This Android Studio build does not include JCEF support."
        }
    }
}
