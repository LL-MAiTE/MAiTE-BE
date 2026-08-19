package com.likelion.hackathon.global.agora;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgoraService {

    private static final String CONV_AI_BASE = "https://api.agora.io/api/conversational-ai-agent/v2/projects/%s/join";
    private static final int AGENT_UID = 9999;
    // 아바타(LiveAvatar)는 오디오 에이전트(AGENT_UID)와 별개의 RTC 참가자로 join해서
    // 영상+립싱크 오디오를 직접 퍼블리시한다 — 그래서 uid를 따로 둔다.
    private static final int AVATAR_UID = 9998;

    private final AgoraProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Agora Conversational AI 에이전트를 채널에 입장시킴.
     * LLM은 우리 Custom LLM 엔드포인트(/agora/chat-completions)로 교체되어 있음.
     * ASR: Deepgram(Agora Managed Key), TTS: MiniMax(Agora Managed Key).
     *
     * @return agentId (나중에 종료할 때 사용)
     */
    @SuppressWarnings("unchecked")
    public String startConversationalAI(String channelName, String greetingMessage) {
        String url = String.format(CONV_AI_BASE, props.getAppId());

        String agentToken = "";
        String cert = props.getAppCertificate();
        if (cert != null && !cert.isBlank()) {
            try {
                agentToken = AgoraTokenUtil.buildTokenWithUid(
                        props.getAppId(), cert, channelName, AGENT_UID, 3600);
            } catch (Exception e) {
                log.warn("Failed to generate agent token: {}", e.getMessage());
            }
        }

        // LLM: Custom LLM — Agora가 우리 서버를 OpenAI 호환 API로 호출
        String callbackBase = props.getCallbackUrl() != null ? props.getCallbackUrl().replaceAll("/$", "") : "";
        Map<String, Object> llm = new LinkedHashMap<>();
        llm.put("url", callbackBase + "/agora/chat-completions?meetingId=" + channelName);
        llm.put("greeting_message", greetingMessage);
        llm.put("failure_message", "확인하는 데 시간이 조금 걸리고 있습니다. 잠시만 기다려주세요.");

        // ASR: Deepgram (Agora Managed Key)
        Map<String, Object> asrParams = new LinkedHashMap<>();
        asrParams.put("url", "wss://api.deepgram.com/v1/listen");
        asrParams.put("model", "nova-3");
        asrParams.put("keyterm", "");
        asrParams.put("language", "ko");

        Map<String, Object> asr = new LinkedHashMap<>();
        asr.put("vendor", "deepgram");
        asr.put("credential_mode", "managed");
        asr.put("resource_id", props.getAsrResourceId());
        asr.put("language", "en");  // Agora 최상위 언어 필드
        asr.put("params", asrParams);
        asr.put("model", "nova-3");

        // TTS: MiniMax (Agora Managed Key)
        // ⚠️ 예전엔 English_radiant_girl(영어 전용 목소리)이 박혀있었음 — AI는 한국어로
        // 대답하는데 목소리 모델이 영어용이라 발음이 어색했던 게 이것 때문이었을 가능성 높음.
        // 한국어 목소리로 교체(협상 대리인 톤에 맞게 차분한 쪽으로 선택, 필요하면 교체 가능:
        // Korean_GentleWoman/Korean_IntellectualMan/Korean_ConsiderateSenior 등도 있음).
        Map<String, Object> voiceSetting = new LinkedHashMap<>();
        voiceSetting.put("voice_id", "Korean_CalmLady");

        Map<String, Object> ttsParams = new LinkedHashMap<>();
        ttsParams.put("url", "wss://api.minimax.io/ws/v1/t2a_v2");
        ttsParams.put("model", "speech-2.8-turbo");
        ttsParams.put("voice_setting", voiceSetting);
        // LiveAvatar는 24kHz만 받는다(다른 샘플레이트면 세션 시작부터 에러) — 기본값은 44100이라
        // 아바타 없이 음성만 쓸 때도 명시적으로 24000으로 맞춰둔다(음질 차이는 거의 없음).
        ttsParams.put("audio_setting", Map.of("sample_rate", 24000));

        Map<String, Object> tts = new LinkedHashMap<>();
        tts.put("vendor", "minimax");
        tts.put("credential_mode", "managed");
        tts.put("resource_id", props.getTtsResourceId());
        tts.put("params", ttsParams);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("channel", channelName);
        properties.put("token", agentToken);
        properties.put("agent_rtc_uid", String.valueOf(AGENT_UID));
        properties.put("remote_rtc_uids", List.of("*"));
        properties.put("idle_timeout", 60);
        properties.put("asr", asr);
        properties.put("llm", llm);
        properties.put("tts", tts);

        // 아바타(LiveAvatar): API 키가 설정돼있을 때만 활성화. 없으면 지금처럼 음성 전용으로
        // 동작한다 — 아바타는 순전히 추가 기능이라 설정 안 해도 기존 흐름이 안 깨진다.
        Map<String, Object> avatar = buildAvatarConfig(channelName, cert);
        if (avatar != null) {
            properties.put("avatar", avatar);
        }

        if (props.getCallbackUrl() != null && !props.getCallbackUrl().isBlank()) {
            properties.put("message_subscriber", Map.of(
                    "url", callbackBase + "/agora/callback"
            ));
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("name", "agent-" + channelName);
        requestBody.put("properties", properties);

        try {
            log.info("Agora ConvAI request: {}", objectMapper.writeValueAsString(requestBody));
        } catch (Exception ignored) {}

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(requestBody, basicAuthHeaders()), Map.class);
            String agentId = (String) resp.getBody().get("agent_id");
            log.info("Agora AI agent started: channel={}, agentId={}", channelName, agentId);
            return agentId;
        } catch (HttpClientErrorException e) {
            log.error("Agora ConvAI error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    /**
     * LiveAvatar(HeyGen) 아바타 설정. Agora가 자기 TTS(MiniMax) 결과를 알아서 LiveAvatar로
     * 라우팅해주는 구조라, 우리 TTS 파이프라인은 안 건드려도 된다 — 여기서 하는 건 별도
     * RTC uid(AVATAR_UID)로 join용 토큰 하나 더 만들고, avatar 블록에 넣어주는 것뿐.
     * liveavatar-api-key가 비어있으면 null을 반환해서 기존 음성 전용 흐름을 그대로 유지한다.
     */
    private Map<String, Object> buildAvatarConfig(String channelName, String cert) {
        String apiKey = props.getLiveavatarApiKey();
        String avatarId = props.getLiveavatarAvatarId();
        if (apiKey == null || apiKey.isBlank() || avatarId == null || avatarId.isBlank()) {
            return null;
        }

        String avatarToken = "";
        if (cert != null && !cert.isBlank()) {
            try {
                avatarToken = AgoraTokenUtil.buildTokenWithUid(
                        props.getAppId(), cert, channelName, AVATAR_UID, 3600);
            } catch (Exception e) {
                log.warn("Failed to generate avatar token: {}", e.getMessage());
                return null;
            }
        }

        Map<String, Object> avatarParams = new LinkedHashMap<>();
        avatarParams.put("api_key", apiKey);
        avatarParams.put("avatar_id", avatarId);
        avatarParams.put("quality", "high");
        avatarParams.put("agora_uid", String.valueOf(AVATAR_UID));
        avatarParams.put("agora_token", avatarToken);

        Map<String, Object> avatar = new LinkedHashMap<>();
        avatar.put("vendor", "liveavatar");
        avatar.put("enable", true);
        avatar.put("params", avatarParams);
        return avatar;
    }

    public void stopConversationalAI(String agentId) {
        if (agentId == null || agentId.isBlank()) return;
        try {
            String url = String.format(
                    "https://api.agora.io/api/conversational-ai-agent/v2/projects/%s/agents/%s/leave",
                    props.getAppId(), agentId);
            // Agora leave는 POST (DELETE 아님)
            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(basicAuthHeaders()), Void.class);
            log.info("Agora AI agent stopped: agentId={}", agentId);
        } catch (Exception e) {
            log.warn("Failed to stop Agora agent {}: {}", agentId, e.getMessage());
        }
    }

    private HttpHeaders basicAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String encoded = Base64.getEncoder().encodeToString(
                (props.getCustomerKey() + ":" + props.getCustomerSecret()).getBytes());
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        return headers;
    }
}
