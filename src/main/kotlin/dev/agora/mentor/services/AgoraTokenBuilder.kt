package dev.agora.mentor.services

import io.agora.media.AccessToken2

object AgoraTokenBuilder {

    /**
     * Mints the combined RTC + RTM token used in the `Authorization: agora token=<value>`
     * header when calling the ConvoAI REST API (POST /join, POST /leave, etc.).
     *
     * This token is scoped to the agent's [account] on the given [channelName]. It must be
     * generated server-side (here: inside the IDE plugin) and must never be exposed to
     * the end-user's client code in production.
     *
     * The ConvoAI REST API requires a token that carries both RTC and RTM privileges because
     * the agent joins both the RTC channel and the RTM signalling channel.
     */
    fun buildAgentRestToken(
        appId: String,
        appCertificate: String,
        channelName: String,
        account: String,
        tokenExpireSeconds: Int,
    ): String {
        val token = AccessToken2(appId, appCertificate, tokenExpireSeconds)

        val rtcService = AccessToken2.ServiceRtc(channelName, account)
        rtcService.addPrivilegeRtc(AccessToken2.PrivilegeRtc.PRIVILEGE_JOIN_CHANNEL, tokenExpireSeconds)
        rtcService.addPrivilegeRtc(AccessToken2.PrivilegeRtc.PRIVILEGE_PUBLISH_AUDIO_STREAM, tokenExpireSeconds)
        rtcService.addPrivilegeRtc(AccessToken2.PrivilegeRtc.PRIVILEGE_PUBLISH_VIDEO_STREAM, tokenExpireSeconds)
        rtcService.addPrivilegeRtc(AccessToken2.PrivilegeRtc.PRIVILEGE_PUBLISH_DATA_STREAM, tokenExpireSeconds)
        token.addService(rtcService)

        val rtmService = AccessToken2.ServiceRtm(account)
        rtmService.addPrivilegeRtm(AccessToken2.PrivilegeRtm.PRIVILEGE_LOGIN, tokenExpireSeconds)
        token.addService(rtmService)

        return token.buildOrThrow("combined RTC + RTM agent token")
    }

    /**
     * Mints the combined RTC + RTM token passed to the browser voice client.
     *
     * The Agora Next.js quickstart (agent-quickstart-nextjs) uses a single AccessToken2 that
     * carries both RTC and RTM privileges. The browser client uses this same token for:
     *   - `rtcClient.join(appId, channel, token, uid)` — RTC channel join
     *   - `rtmClient.login({ token })` — RTM signalling login
     *
     * Both RTM login identity ([rtcUid] converted to string) and the RTC token subject must
     * match exactly; any mismatch causes a generic "Failed to start conversation" error.
     *
     * Token renewal (on `token-privilege-will-expire`) requires fetching fresh tokens and
     * calling `rtcClient.renewToken(newRtcToken)` and `rtmClient.renewToken(newRtmToken)`
     * independently. In the plugin this is surfaced as a user-visible expiry warning since
     * there is no backend to issue renewal tokens on demand.
     */
    fun buildClientVoiceToken(
        appId: String,
        appCertificate: String,
        channelName: String,
        rtcUid: Int,
        tokenExpireSeconds: Int,
    ): String {
        val account = rtcUid.toString()
        val token = AccessToken2(appId, appCertificate, tokenExpireSeconds)

        val rtcService = AccessToken2.ServiceRtc(channelName, account)
        rtcService.addPrivilegeRtc(AccessToken2.PrivilegeRtc.PRIVILEGE_JOIN_CHANNEL, tokenExpireSeconds)
        rtcService.addPrivilegeRtc(AccessToken2.PrivilegeRtc.PRIVILEGE_PUBLISH_AUDIO_STREAM, tokenExpireSeconds)
        rtcService.addPrivilegeRtc(AccessToken2.PrivilegeRtc.PRIVILEGE_PUBLISH_VIDEO_STREAM, tokenExpireSeconds)
        rtcService.addPrivilegeRtc(AccessToken2.PrivilegeRtc.PRIVILEGE_PUBLISH_DATA_STREAM, tokenExpireSeconds)
        token.addService(rtcService)

        val rtmService = AccessToken2.ServiceRtm(account)
        rtmService.addPrivilegeRtm(AccessToken2.PrivilegeRtm.PRIVILEGE_LOGIN, tokenExpireSeconds)
        token.addService(rtmService)

        return token.buildOrThrow("combined RTC + RTM client token")
    }

    private fun AccessToken2.buildOrThrow(label: String): String {
        return try {
            build().takeIf { it.isNotBlank() }
                ?: error("Failed to generate $label. Verify App ID and App Certificate.")
        } catch (error: Exception) {
            throw IllegalStateException("Failed to generate $label.", error)
        }
    }
}
