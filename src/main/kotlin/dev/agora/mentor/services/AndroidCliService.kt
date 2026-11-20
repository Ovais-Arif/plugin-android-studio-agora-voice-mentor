package dev.agora.mentor.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class AndroidCliService(private val project: Project) {

    fun isAndroidCliAvailable(): Boolean = getAndroidCliVersion() != null

    fun getAndroidCliVersion(): String? {
        val output = runAndroidCommand(listOf("--version"), 5_000L) ?: return null
        return output.firstOrNull { it.isNotBlank() }?.trim()
    }

    fun searchAndroidDocs(query: String): String {
        if (query.isBlank()) {
            return "Android CLI docs search skipped because the query was empty."
        }

        return runAndroidCommand(listOf("docs", "search", query), 15_000L)
            ?.joinToString(System.lineSeparator())
            ?: "Android CLI is not available. Install the Android CLI to search Android docs from the plugin."
    }

    private fun runAndroidCommand(arguments: List<String>, timeoutMillis: Long): List<String>? {
        val process = try {
            val command = mutableListOf("android")
            command.addAll(arguments)
            ProcessBuilder(command)
                .directory(project.basePath?.let(::File))
                .redirectErrorStream(true)
                .start()
        } catch (_: IOException) {
            return null
        }

        val finished = try {
            process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

        if (!finished) {
            process.destroyForcibly()
            return listOf("Android CLI command timed out after ${timeoutMillis}ms.")
        }

        return process.inputStream.bufferedReader().useLines { lines ->
            lines.toList()
        }
    }
}
