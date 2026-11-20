package dev.agora.mentor.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import dev.agora.mentor.model.SelectedCodeContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random

@Service(Service.Level.PROJECT)
class AgoraSessionService(private val project: Project) {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()
    private val logger = thisLogger()

    @Volatile
    private var activeSession: ActiveAgoraSession? = null

    fun startSession(
        context: SelectedCodeContext,
        prompt: String,
        appId: String,
        appCertificate: String,
        geofenceArea: String,
        agentUid: String,
    ): String {
        return try {
            if (appId.isBlank() || appCertificate.isBlank()) {
                return "Enter both App ID and App Certificate in the Settings tab before starting."
            }
            if (agentUid.isBlank()) {
                return "Enter an Agent RTC UID in the Settings tab before starting."
            }

            val channel = buildChannelName(context)
            logger.info("Starting Agora session for project=${context.projectName}, file=${context.fileName}, channel=$channel, agentUid=$agentUid")
            val agentToken = AgoraTokenBuilder.buildAgentRestToken(
                appId = appId,
                appCertificate = appCertificate,
                channelName = channel,
                account = agentUid,
                tokenExpireSeconds = SESSION_EXPIRY_SECONDS,
            )
            logger.info("ConvoAI auth token minted successfully for channel=$channel")

            // Agent names must be unique per project. On HTTP 409 (name collision) the skill
            // requires generating a new name and retrying exactly once — do not reuse the colliding name.
            var agentName = buildAgentName()
            var response: HttpResponse<String>? = null
            for (attempt in 0..1) {
                val requestJson = buildStartRequestJson(
                    name = agentName,
                    channel = channel,
                    token = agentToken,
                    agentUid = agentUid,
                    systemPrompt = prompt,
                    fileName = context.fileName,
                    geofenceArea = geofenceArea,
                )
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("$BASE_URL/$appId/join"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "agora token=$agentToken")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build()

                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                logger.info("Agora join response attempt=$attempt status=${response.statusCode()} body=${response.body()}")

                if (response.statusCode() != 409) break
                logger.warn("Agent name collision (409) on attempt=$attempt — retrying with a new name.")
                agentName = buildAgentName()
            }

            checkNotNull(response) { "No response received from Agora ConvoAI API." }

            if (response.statusCode() !in 200..299) {
                val authHint = if (response.statusCode() == 401) {
                    " This usually means the App ID/App Certificate pair is mismatched, ConvoAI is not enabled for the project, or the generated token does not match Agora's expected AccessToken2 format."
                } else {
                    ""
                }
                return buildString {
                    append("Agora session failed to start (${response.statusCode()}): ${response.body()}")
                    append(" | channel=$channel agentUid=$agentUid")
                    append(" | verify that the App ID and App Certificate belong to the same Agora project and that ConvoAI is enabled.")
                    append(authHint)
                }
            }

            val agentId = extractJsonString(response.body(), "agent_id")
                ?: extractJsonString(response.body(), "agentId")
                ?: "unknown-agent"
            val state = extractJsonString(response.body(), "state") ?: "STARTING"
            val voiceBootstrap = buildVoiceBootstrap(
                appId = appId,
                appCertificate = appCertificate,
                channel = channel,
            )
            val expiresAt = Instant.now().plusSeconds(SESSION_EXPIRY_SECONDS.toLong())
            activeSession = ActiveAgoraSession(
                appId = appId,
                appCertificate = appCertificate,
                channel = channel,
                agentId = agentId,
                agentRtcUid = agentUid,
                token = agentToken,
                voiceBootstrap = voiceBootstrap,
                context = context,
                expiresAt = expiresAt,
            )

            val expiryLabel = DateTimeFormatter
                .ofPattern("HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(expiresAt)

            // POST /join success means the request was accepted and the agent is STARTING.
            // It is NOT yet in the RTC channel. The voice client will detect the agent via the
            // RTC user-published event and update its status automatically.
            buildString {
                appendLine("Agora agent accepted. agentId=$agentId state=$state channel=$channel")
                appendLine("Voice UID: ${voiceBootstrap.rtcUid} | Geofence: $geofenceArea")
                appendLine("Session token expires at $expiryLabel (1-hour limit).")
                append("The agent is joining the channel. Speak after the voice client shows 'Agent connected'.")
            }
        } catch (error: Exception) {
            logger.warn("Failed to start Agora agent", error)
            "Failed to start Agora agent: ${error.message ?: error::class.java.simpleName}"
        }
    }

    fun getActiveSession(): ActiveAgoraSession? = activeSession

    fun stopSession(): String {
        val session = activeSession ?: return "No active Agora session."

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$BASE_URL/${session.appId}/agents/${session.agentId}/leave"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Authorization", "agora token=${session.token}")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()

        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            logger.info("Agora stop response status=${response.statusCode()} body=${response.body()}")
            if (response.statusCode() == 404) {
                activeSession = null
                return "Agora agent already stopped."
            }
            if (response.statusCode() !in 200..299) {
                return "Failed to stop Agora agent (${response.statusCode()}): ${response.body()}"
            }
            activeSession = null
            "Agora session stopped."
        } catch (error: Exception) {
            logger.warn("Failed to stop Agora agent", error)
            "Failed to stop Agora agent: ${error.message ?: error::class.java.simpleName}"
        }
    }

    fun sendTextMessage(message: String): String {
        val session = activeSession ?: return "Start an Agora session before sending text."
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$BASE_URL/${session.appId}/agents/${session.agentId}/speak"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Authorization", "agora token=${session.token}")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    """
                    {
                      "appid": "${session.appId}",
                      "agentId": "${session.agentId}",
                      "text": "${message.escapeJson()}",
                      "priority": "INTERRUPT",
                      "interruptable": false
                    }
                    """.trimIndent(),
                )
            )
            .build()

        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            logger.info("Agora speak response status=${response.statusCode()} body=${response.body()}")
            if (response.statusCode() !in 200..299) {
                return "Failed to send text to Agora agent (${response.statusCode()}): ${response.body()}"
            }
            "Sent text to Agora agent on channel ${session.channel}."
        } catch (error: Exception) {
            logger.warn("Failed to send text to Agora agent", error)
            "Failed to send text to Agora agent: ${error.message ?: error::class.java.simpleName}"
        }
    }

    private fun buildStartRequestJson(
        name: String,
        channel: String,
        token: String,
        agentUid: String,
        systemPrompt: String,
        fileName: String,
        geofenceArea: String,
    ): String {
        val safeName = name.escapeJson()
        val safeChannel = channel.escapeJson()
        val safeToken = token.escapeJson()
        val safeAgentUid = agentUid.escapeJson()
        val safeSystemPrompt = systemPrompt.escapeJson()
        val safeGreeting = "Hi! Let's go through the code you selected in $fileName.".escapeJson()
        val safeGeofence = geofenceArea.escapeJson()

        return """
            {
              "name": "$safeName",
              "preset": "$DEFAULT_PRESET",
              "properties": {
                "channel": "$safeChannel",
                "token": "$safeToken",
                "agent_rtc_uid": "$safeAgentUid",
                "remote_rtc_uids": ["*"],
                "enable_string_uid": false,
                "idle_timeout": 30,
                "geofence": {
                  "area": "$safeGeofence"
                },
                "advanced_features": {
                  "enable_rtm": true
                },
                "asr": {
                  "vendor": "deepgram",
                  "params": {
                    "language": "en"
                  }
                },
                "llm": {
                  "system_messages": [
                    {
                      "role": "system",
                      "content": "$safeSystemPrompt"
                    }
                  ],
                  "max_history": 15,
                  "greeting_message": "$safeGreeting",
                  "failure_message": "$DEFAULT_FAILURE_MESSAGE",
                  "params": {
                    "max_tokens": 1024,
                    "temperature": 0.7,
                    "top_p": 0.95
                  }
                },
                "tts": {
                  "vendor": "minimax",
                  "params": {
                    "voice_setting": {
                      "voice_id": "English_captivating_female1"
                    }
                  }
                },
                "turn_detection": {
                  "mode": "default",
                  "config": {
                    "speech_threshold": 0.38,
                    "start_of_speech": {
                      "mode": "vad",
                      "vad_config": {
                        "interrupt_duration_ms": 160,
                        "speaking_interrupt_duration_ms": 160,
                        "prefix_padding_ms": 480
                      }
                    },
                    "end_of_speech": {
                      "mode": "vad",
                      "vad_config": {
                        "silence_duration_ms": 720
                      }
                    }
                  }
                },
                "interruption": {
                  "enable": true,
                  "mode": "start_of_speech"
                },
                "parameters": {
                  "data_channel": "rtm",
                  "enable_metrics": true,
                  "enable_error_message": true
                }
              }
            }
        """.trimIndent()
    }

    private fun buildChannelName(context: SelectedCodeContext): String {
        val base = listOf(project.name, context.fileName)
            .joinToString("-")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        val suffix = UUID.randomUUID().toString().take(8)
        return "mentor-$base-$suffix".take(63)
    }

    private fun buildAgentName(): String {
        return "mentor-agent-${UUID.randomUUID().toString().take(8)}"
    }

    private fun extractJsonString(body: String, key: String): String? {
        val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"")
        return regex.find(body)?.groupValues?.getOrNull(1)
    }

    private fun buildVoiceBootstrap(
        appId: String,
        appCertificate: String,
        channel: String,
    ): AgoraVoiceBootstrap {
        val rtcUid = Random.nextInt(100_000, 900_000)
        val token = AgoraTokenBuilder.buildClientVoiceToken(
            appId = appId,
            appCertificate = appCertificate,
            channelName = channel,
            rtcUid = rtcUid,
            tokenExpireSeconds = SESSION_EXPIRY_SECONDS,
        )
        return AgoraVoiceBootstrap(
            appId = appId,
            channel = channel,
            rtcUid = rtcUid,
            rtmUserId = rtcUid.toString(),
            token = token,
        )
    }

    private fun String.escapeJson(): String = buildString(length) {
        for (char in this@escapeJson) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

    data class ActiveAgoraSession(
        val appId: String,
        val appCertificate: String,
        val channel: String,
        val agentId: String,
        val agentRtcUid: String,
        val token: String,
        val voiceBootstrap: AgoraVoiceBootstrap,
        val context: SelectedCodeContext,
        /** When the RTC + RTM tokens expire (SESSION_EXPIRY_SECONDS after session start). */
        val expiresAt: Instant,
    )

    data class AgoraVoiceBootstrap(
        val appId: String,
        val channel: String,
        val rtcUid: Int,
        val rtmUserId: String,
        val token: String,
    )

    companion object {
        private const val BASE_URL = "https://api.agora.io/api/conversational-ai-agent/v2/projects"
        const val SESSION_EXPIRY_SECONDS = 60 * 60
        private const val DEFAULT_PRESET = "deepgram_nova_3,openai_gpt_4o_mini,minimax_speech_2_6_turbo"
        private const val DEFAULT_FAILURE_MESSAGE = "Please wait a moment."

        val GEOFENCE_OPTIONS = listOf("NORTH_AMERICA", "EUROPE", "ASIA", "JAPAN", "INDIA", "OVERSEAS")
    }
}
