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
