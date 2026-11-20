package dev.agora.mentor.toolwindow.jcef

import com.intellij.openapi.project.Project
import dev.agora.mentor.services.AgoraSessionService.ActiveAgoraSession

object VoiceClientPageBuilder {

    private const val AGORA_LOGO_SVG = """<svg width="28" height="28" viewBox="0 0 50 50" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M24.9924 36.0241C22.81 36.0241 20.6766 35.3775 18.862 34.1662C17.0474 32.9548 15.6331 31.2331 14.798 29.2187C13.9628 27.2043 13.7443 24.9878 14.17 22.8493C14.5958 20.7109 15.6467 18.7466 17.1899 17.2048C18.7331 15.6631 20.6992 14.6131 22.8397 14.1878C24.9802 13.7624 27.1988 13.9807 29.2151 14.8151C31.2314 15.6495 32.9547 17.0625 34.1672 18.8754C35.3797 20.6883 36.0268 22.8196 36.0268 25C36.0239 27.9229 34.8604 30.7251 32.7917 32.7919C30.7229 34.8587 27.918 36.0211 24.9924 36.0241ZM37.8416 8.93956L37.6736 9.1634L37.5056 9.38724L37.2815 9.21936L37.0687 9.05148C34.5373 7.14076 31.5875 5.85883 28.4626 5.31148C25.3376 4.76414 22.1272 4.96708 19.0963 5.90355C16.0653 6.84003 13.3007 8.48318 11.0306 10.6974C8.76056 12.9117 7.05008 15.6335 6.04036 18.6383C5.03063 21.6431 4.75062 24.8447 5.22343 27.9789C5.69624 31.1131 6.90831 34.0901 8.75962 36.6641C10.6109 39.2381 13.0484 41.3354 15.8708 42.7828C18.6933 44.2303 21.8198 44.9864 24.9924 44.9888C29.356 45.0014 33.6015 43.5732 37.0687 40.9261L37.2815 40.7694L37.5056 40.5904L37.6736 40.8254L37.8416 41.0492C38.5966 42.0901 39.5544 42.9677 40.6575 43.6293C41.7606 44.2909 42.9862 44.7229 44.2606 44.8993L45 45V5L44.2606 5.10073C42.9873 5.27635 41.7624 5.70686 40.6595 6.36647C39.5565 7.02608 38.5981 7.90124 37.8416 8.93956Z" fill="#00C2FF"/></svg>"""

    fun buildHtml(project: Project, session: ActiveAgoraSession): String {
        val bootstrapJson = """
            {
              "appId": "${escapeJs(session.voiceBootstrap.appId)}",
              "channel": "${escapeJs(session.voiceBootstrap.channel)}",
              "rtcUid": ${session.voiceBootstrap.rtcUid},
              "rtmUserId": "${escapeJs(session.voiceBootstrap.rtmUserId)}",
              "token": "${escapeJs(session.voiceBootstrap.token)}",
              "agentRtcUid": "${escapeJs(session.agentRtcUid)}",
              "projectName": "${escapeJs(project.name)}"
            }
        """.trimIndent()
        val selectedCode = session.context.selectedCode.trimEnd()

        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>Agora Voice Runtime</title>
              <style>
                :root {
                  color-scheme: dark;
                  --bg: #111318;
                  --panel: #171a21;
                  --border: #2a2f3a;
                  --text: #e8ebf2;
                  --muted: #8e97ab;
                  --good: #66d19e;
                  --bad: #ff6b6b;
                }
                html, body {
                  margin: 0;
                  padding: 0;
                  height: 100%;
                  background: var(--bg);
                  color: var(--text);
                  font-family: Inter, Segoe UI, -apple-system, BlinkMacSystemFont, sans-serif;
                }
                body {
                  display: flex;
                  flex-direction: column;
                }
                header {
                  padding: 16px 18px;
                  border-bottom: 1px solid var(--border);
                  display: flex;
                  align-items: center;
                  gap: 12px;
                }
                header .brand-mark {
                  flex: none;
                  display: flex;
                }
                h1 {
                  margin: 0 0 6px 0;
                  font-size: 18px;
                }
                .meta {
                  color: var(--muted);
                  font-size: 12px;
                  line-height: 1.5;
                }
                main {
                  display: grid;
                  grid-template-columns: 1fr;
                  gap: 12px;
                  padding: 14px 18px 18px;
                  flex: 1;
                  overflow: auto;
                }
                .card {
                  background: var(--panel);
                  border: 1px solid var(--border);
                  border-radius: 14px;
                  padding: 12px 14px;
                }
                .status {
                  font-weight: 700;
                  margin-bottom: 8px;
                }
                .ok { color: var(--good); }
                .bad { color: var(--bad); }
                pre {
                  margin: 0;
                  white-space: pre-wrap;
                  word-break: break-word;
                  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
                  font-size: 12px;
                  line-height: 1.45;
                  min-height: 180px;
                }
                /* Chat bubbles */
                .messages {
                  display: flex;
                  flex-direction: column;
                  gap: 10px;
                  min-height: 180px;
                }
                .msg {
                  display: flex;
                  flex-direction: column;
                  align-self: flex-start;
                  align-items: flex-start;
                  width: 100%;
                }
                .msg-label {
                  font-size: 11px;
                  font-weight: 600;
                  color: var(--muted);
                  margin-bottom: 3px;
                  padding: 0 4px;
                }
                .bubble {
                  padding: 8px 12px;
                  border-radius: 12px;
                  font-size: 13px;
                  line-height: 1.55;
                  word-break: break-word;
                }
                .msg.agent .bubble {
                  background: #1e2230;
                  border: 1px solid var(--border);
                  color: var(--text);
                  border-bottom-left-radius: 4px;
                }
                .msg.user .bubble {
                  background: rgba(0,194,255,0.10);
                  border: 1px solid rgba(0,194,255,0.22);
                  color: var(--text);
                  border-bottom-left-radius: 4px;
                }
                .empty-transcript {
                  color: var(--muted);
                  font-size: 13px;
                  text-align: center;
                  padding: 32px 0;
                }
                details.card {
                  padding: 0;
                }
                details.card > summary {
                  list-style: none;
                  cursor: pointer;
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  gap: 10px;
                  padding: 12px 14px;
                  font-weight: 700;
                }
                details.card > summary::-webkit-details-marker {
                  display: none;
                }
                details.card > summary::after {
                  content: '▾';
                  color: var(--muted);
                  font-size: 12px;
                  transition: transform 0.18s ease;
                }
                details.card[open] > summary::after {
                  transform: rotate(180deg);
                }
                .debug-log {
                  border-top: 1px solid var(--border);
                  padding: 12px 14px 14px;
                }
              </style>
              <!-- Agora RTC + RTM loaded from CDN — no local bundle needed -->
              <script src="https://cdn.jsdelivr.net/npm/agora-rtc-sdk-ng@4.24.3/AgoraRTC_N-production.js"
                      onerror="document.getElementById('status').textContent='SDK load failed: could not reach cdn.jsdelivr.net. Check your internet connection.';document.getElementById('status').className='status bad';"></script>
              <script src="https://cdn.jsdelivr.net/npm/agora-rtm@2.2.3/agora-rtm.js"
                      onerror="document.getElementById('status').textContent='SDK load failed: could not reach cdn.jsdelivr.net. Check your internet connection.';document.getElementById('status').className='status bad';"></script>
            </head>
            <body>
              <header>
                <div class="brand-mark">$AGORA_LOGO_SVG</div>
                <div>
                  <h1>Agora Android Voice Mentor</h1>
                  <div class="meta">
                    Channel: ${escapeHtml(session.channel)} &nbsp;|&nbsp; Agent: ${escapeHtml(session.agentId)} &nbsp;|&nbsp; UID: ${session.voiceBootstrap.rtcUid}
                  </div>
                </div>
              </header>
              <main>
                <section class="card">
                  <div id="status" class="status ok">Voice runtime ready. Click Start Voice Client to request microphone access.</div>
                  <div class="meta">The mic permission prompt will appear after you click Start Voice Client.</div>
                  <div style="margin-top: 12px; display: flex; gap: 8px; flex-wrap: wrap;">
                    <button id="startButton">Start Voice Client</button>
                    <button id="stopButton">Stop Voice Client</button>
                  </div>
                </section>
                <section class="card">
                  <div class="status ok" style="margin-bottom: 4px;">Talking about</div>
                  <div class="meta" style="margin-bottom: 8px;">${escapeHtml(session.context.fileName)} (${escapeHtml(session.context.language)}) &mdash; ${session.context.selectedCode.length} characters</div>
                  <pre id="selectedCode" style="max-height: 220px; overflow: auto; min-height: 0;">${escapeHtml(selectedCode)}</pre>
                </section>
                <section class="card">
                  <div class="status ok" style="margin-bottom: 8px;">Transcript</div>
                  <div class="messages" id="messages">
                    <div class="empty-transcript" id="emptyTranscript">Start speaking to see the live transcript here.</div>
                  </div>
                </section>
                <details class="card" open>
                  <summary><span class="status ok" style="margin: 0;">Debug log</span></summary>
                  <div class="debug-log">
                    <pre id="log"></pre>
                  </div>
                </details>
              </main>
              <script>
                window.AGORA_BOOTSTRAP = ${bootstrapJson};
                window.process = window.process || { env: { NODE_ENV: 'production' } };
              </script>
              <script type="module">
                (function() {
                  const logEl        = document.getElementById('log');
                  const messagesEl   = document.getElementById('messages');
                  const emptyEl      = document.getElementById('emptyTranscript');
                  const statusEl     = document.getElementById('status');
                  const startButton  = document.getElementById('startButton');
                  const stopButton   = document.getElementById('stopButton');
                  const AgoraRTC     = window.AgoraRTC;
                  const AgoraRTM     = window.AgoraRTM;
                  const state        = { client: null, rtm: null, micTrack: null, ai: null, started: false };
                  const bubbleMap    = new Map();   // entryKey → { msgEl, bubbleEl }

                  function log(message) {
                    const line = '[' + new Date().toLocaleTimeString() + '] ' + message;
                    logEl.textContent = logEl.textContent ? (logEl.textContent + '\n' + line) : line;
                  }

                  function setStatus(message, good = true) {
                    statusEl.textContent = message;
                    statusEl.className   = 'status ' + (good ? 'ok' : 'bad');
                  }

                  function notify(type) {
                    fetch('/event?type=' + encodeURIComponent(type)).catch(() => {});
                  }

                  function renderTranscript(items) {
                    const agentUid = String(window.AGORA_BOOTSTRAP.agentRtcUid);
                    items.forEach((item, index) => {
                      const uid      = String(item.uid);
                      // uid "0" is local user in the toolkit before RTC assigns an ID
                      const isAgent  = uid === agentUid;
                      const turnId   = item.turn_id != null
                        ? String(item.turn_id)
                        : 'idx_' + index;
                      const entryKey = turnId + '_' + uid;
                      const text     = (item.text || '').trim();
                      const done     = item.status && item.status !== 'IN_PROGRESS';

                      if (!bubbleMap.has(entryKey)) {
                        if (emptyEl) emptyEl.style.display = 'none';
                        const msgEl    = document.createElement('article');
                        msgEl.className = 'msg ' + (isAgent ? 'agent' : 'user');
                        const labelEl  = document.createElement('div');
                        labelEl.className   = 'msg-label';
                        labelEl.textContent = isAgent ? 'Agent' : 'You';
                        const bubbleEl = document.createElement('div');
                        bubbleEl.className  = 'bubble';
                        msgEl.append(labelEl, bubbleEl);
                        messagesEl.appendChild(msgEl);
                        bubbleMap.set(entryKey, { msgEl, bubbleEl });
                      }

                      const { bubbleEl } = bubbleMap.get(entryKey);
                      bubbleEl.textContent = text || '…';
                    });
                    messagesEl.scrollTop = messagesEl.scrollHeight;
                  }

                  async function start() {
                    if (state.started) return;
                    const bootstrap = window.AGORA_BOOTSTRAP;
                    state.started   = true;
                    notify('voice_starting');

                    if (!AgoraRTC || !AgoraRTM) {
                      const missing = [!AgoraRTC ? 'AgoraRTC' : null, !AgoraRTM ? 'AgoraRTM' : null].filter(Boolean).join(', ');
                      setStatus('Voice runtime could not load Agora SDK bundles.', false);
                      log('Missing browser globals: ' + missing);
                      notify('voice_error');
                      state.started = false;
                      return;
                    }

                    let AgoraVoiceAI, AgoraVoiceAIEvents;
                    try {
                      const toolkit      = await import('/sdk/toolkit.mjs');
                      AgoraVoiceAI       = toolkit.AgoraVoiceAI;
                      AgoraVoiceAIEvents = toolkit.AgoraVoiceAIEvents;
                    } catch (error) {
                      setStatus('Voice runtime could not load the ConvoAI client toolkit.', false);
                      log('Toolkit import error: ' + (error && error.stack ? error.stack : error));
                      state.started = false;
                      return;
                    }

                    try {
                      if (AgoraRTC.setParameter) AgoraRTC.setParameter('ENABLE_AUDIO_PTS', true);
                    } catch {}

                    // Step 1: RTM
                    setStatus('Connecting to Agora signalling (RTM)…', true);
                    state.rtm = new AgoraRTM.RTM(bootstrap.appId, bootstrap.rtmUserId, { useStringUserId: true, logLevel: 'info' });
                    await state.rtm.login({ token: bootstrap.token });
                    await state.rtm.subscribe(bootstrap.channel);
                    notify('rtm_ready');
                    log('RTM ready — subscribed to channel ' + bootstrap.channel);

                    // Step 2: RTC
                    setStatus('Joining RTC channel…', true);
                    state.client = AgoraRTC.createClient({ mode: 'rtc', codec: 'vp8' });

                    state.client.on('user-published', async (user, mediaType) => {
                      await state.client.subscribe(user, mediaType);
                      if (mediaType === 'audio') {
                        const target = document.getElementById('remote-player') || (() => {
                          const div = document.createElement('div');
                          div.id = 'remote-player'; div.style.display = 'none';
                          document.body.appendChild(div); return div;
                        })();
                        user.audioTrack.play(target);
                        notify('agent_connected');
                        log('Agent audio stream active: uid=' + user.uid);
                        setStatus('Agent connected — speak to begin the conversation.', true);
                      }
                    });

                    state.client.on('user-unpublished', (user, mediaType) => {
                      if (mediaType === 'audio') {
                        notify('agent_disconnected');
                        log('Agent audio stopped: uid=' + user.uid);
                        setStatus('Agent disconnected. If the session expired (1-hour limit), stop and restart.', false);
                      }
                    });

                    state.client.on('token-privilege-will-expire', () => {
                      notify('token_expiring');
                      log('Token expiring soon. Stop and restart the session to continue.');
                      setStatus('Session token expiring soon — stop and restart before it disconnects.', false);
                    });

                    state.client.on('connection-state-change', (curState, prevState, reason) => {
                      log('RTC connection: ' + prevState + ' → ' + curState + (reason ? ' (' + reason + ')' : ''));
                      if (reason === 'INVALID_TOKEN' || reason === 'TOKEN_EXPIRED') {
                        notify('token_expired');
                        setStatus('Session token expired. Stop the voice client and restart the session.', false);
                      }
                    });

                    await state.client.join(bootstrap.appId, bootstrap.channel, bootstrap.token, bootstrap.rtcUid);
                    state.micTrack = await AgoraRTC.createMicrophoneAudioTrack();
                    await state.client.publish([state.micTrack]);
                    notify('rtc_joined');
                    log('RTC joined and mic published. Waiting for agent to join the channel…');

                    // Step 3: AgoraVoiceAI
                    state.ai = await AgoraVoiceAI.init({
                      rtcEngine: state.client,
                      rtmConfig: { rtmEngine: state.rtm },
                      renderMode: 'text',
                      enableLog: true,
                    });
                    state.ai.on(AgoraVoiceAIEvents.TRANSCRIPT_UPDATED, (transcript) => {
                      renderTranscript(transcript);
                    });
                    state.ai.on(AgoraVoiceAIEvents.AGENT_STATE_CHANGED, (agentUserId, event) => {
                      log('Agent state: ' + event.state);
                    });
                    state.ai.on(AgoraVoiceAIEvents.AGENT_METRICS, (agentUserId, metric) => {
                      log('[metrics] ' + metric.type + ' ' + metric.name + '=' + metric.value + 'ms');
                    });
                    state.ai.on(AgoraVoiceAIEvents.AGENT_ERROR, (agentUserId, error) => {
                      log('Agent error: ' + error.message);
                    });
                    state.ai.on(AgoraVoiceAIEvents.MESSAGE_ERROR, (agentUserId, error) => {
                      log('RTM message error: ' + (error && error.message ? error.message : error));
                    });
                    state.ai.on(AgoraVoiceAIEvents.DEBUG_LOG, (message) => {
                      log('[toolkit] ' + message);
                    });
                    state.ai.subscribeMessage(bootstrap.channel);

                    notify('waiting_for_agent');
                    setStatus('Joined channel. Waiting for AI agent to join…', true);
                    log('RTC and RTM connected. POST /join was accepted — agent is starting.');
                  }

                  async function stop() {
                    try {
                      if (state.ai)       { try { state.ai.unsubscribe(); state.ai.destroy(); } catch(e) {} }
                      if (state.rtm)      { try { await state.rtm.logout(); } catch(e) {} }
                      if (state.client)   { try { await state.client.leave(); } catch(e) {} }
                      if (state.micTrack) { try { state.micTrack.stop(); state.micTrack.close(); } catch(e) {} }
                    } finally {
                      state.started  = false;
                      state.ai       = null;
                      state.rtm      = null;
                      state.client   = null;
                      state.micTrack = null;
                      notify('voice_stopped');
                      setStatus('Voice client stopped.', true);
                    }
                  }

                  window.agoraMentor = { start, stop };

                  startButton.addEventListener('click', () => {
                    start().catch((error) => {
                      stop().finally(() => {
                        state.started = false;
                        setStatus('Voice client failed to start.', false);
                        log('Start error: ' + (error && error.stack ? error.stack : error));
                      });
                    });
                  });

                  stopButton.addEventListener('click', () => {
                    stop().catch((error) => {
                      setStatus('Voice client failed to stop.', false);
                      log('Stop error: ' + (error && error.stack ? error.stack : error));
                    });
                  });
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(value: String): String = buildString(value.length) {
        for (char in value) when (char) {
            '&'  -> append("&amp;")
            '<'  -> append("&lt;")
            '>'  -> append("&gt;")
            '"'  -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(char)
        }
    }

    private fun escapeJs(value: String): String = buildString(value.length) {
        for (char in value) when (char) {
            '"'  -> append("\\\"")
            '\'' -> append("\\'")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\\' -> append("\\\\")
            else -> append(char)
        }
    }
}
