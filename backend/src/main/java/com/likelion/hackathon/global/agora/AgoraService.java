package com.likelion.hackathon.global.agora;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private static final int AGENT_UID = 100;

    private final AgoraProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Agora Conversational AI 에이전트를 생성하고 채널에 입장시킴.
     * LLM: 우리 백엔드 /agora/chat-completions (3단계 파이프라인)
     * TTS: MiniMax managed credential
     * ASR: Deepgram managed credential
     * @return agentId (나중에 종료할 때 사용)
     */
    @SuppressWarnings("unchecked")
    public String startConversationalAI(String channelName, String systemPrompt,
                                        String language, String greetingMessage, String meetingId) {
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

        // LLM: 우리 백엔드 3단계 파이프라인 엔드포인트
        String llmUrl = props.getCallbackUrl() + "/agora/chat-completions?meetingId=" + meetingId;
        Map<String, Object> llm = new LinkedHashMap<>();
        llm.put("url", llmUrl);
        llm.put("system_message", systemPrompt);
        llm.put("greeting_message", greetingMessage);
        llm.put("failure_message", "잠시 후 다시 말씀해 주세요.");
        llm.put("max_history", 10);

        // TTS: MiniMax managed credential
        Map<String, Object> ttsVoiceSetting = new LinkedHashMap<>();
        ttsVoiceSetting.put("voice_id", "female-shaonv");
        ttsVoiceSetting.put("speed", 1.0);
        ttsVoiceSetting.put("vol", 1.0);
        ttsVoiceSetting.put("pitch", 0);

        Map<String, Object> ttsParams = new LinkedHashMap<>();
        ttsParams.put("model", "speech-02-turbo");
        ttsParams.put("voice_setting", ttsVoiceSetting);

        Map<String, Object> tts = new LinkedHashMap<>();
        tts.put("vendor", "minimax");
        tts.put("credential_mode", "managed");
        tts.put("resource_id", "66449ca1947a4fd0bbc6b400f1e2004d");
        tts.put("params", ttsParams);

        Map<String, Object> asrParams = new LinkedHashMap<>();
        asrParams.put("url", "wss://api.deepgram.com/v1/listen");
        asrParams.put("model", "nova-3");
        asrParams.put("keyterm", "");
        asrParams.put("language", "ko");

        Map<String, Object> asr = new LinkedHashMap<>();
        asr.put("vendor", "deepgram");
        asr.put("credential_mode", "managed");
        asr.put("resource_id", "dfcbdd6c-d453-4e9f-bbc8-1d94a63d70c0");
        asr.put("language", "en");   // Agora 필드 (en 고정)
        asr.put("params", asrParams); // Deepgram 실제 언어는 params.language=ko
        asr.put("model", "nova-3");

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
                    "url", props.getCallbackUrl() + "/agora/callback"
            ));
        }

        Map<String, Object> requestBody = Map.of(
                "name", "agent-" + channelName,
                "properties", properties
        );

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
            String url = String.format("https://api.agora.io/api/conversational-ai-agent/v2/projects/%s/agents/%s/leave",
                    props.getAppId(), agentId);
            restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(basicAuthHeaders()), Void.class);
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
