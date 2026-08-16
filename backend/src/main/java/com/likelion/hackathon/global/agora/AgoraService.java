package com.likelion.hackathon.global.agora;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgoraService {

    private static final String CONV_AI_BASE = "https://api.agora.io/api/conversational-ai/v2/projects/%s/agents";
    private static final int AGENT_UID = 100;

    private final AgoraProperties props;
    private final RestTemplate restTemplate;

    @Value("${openai.api-key}")
    private String openAiKey;

    /**
     * Agora Conversational AI 에이전트를 생성하고 채널에 입장시킴.
     * OpenAI Realtime API 사용 (TTS 별도 불필요).
     * @return agentId (나중에 종료할 때 사용)
     */
    @SuppressWarnings("unchecked")
    public String startConversationalAI(String channelName, String systemPrompt,
                                        String language, String greetingMessage) {
        String url = String.format(CONV_AI_BASE, props.getAppId());

        Map<String, Object> turnDetection = Map.of(
                "type", "server_vad",
                "silence_duration_ms", 600,
                "prefix_padding_ms", 300,
                "threshold", 0.5
        );

        Map<String, Object> llmParams = Map.of(
                "voice", "alloy",
                "turn_detection", turnDetection,
                "input_audio_format", "pcm16",
                "output_audio_format", "pcm16"
        );

        Map<String, Object> llm = new LinkedHashMap<>();
        llm.put("url", "wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview");
        llm.put("api_key", openAiKey);
        llm.put("model", "gpt-4o-realtime-preview");
        llm.put("system_messages", List.of(Map.of("role", "system", "content", systemPrompt)));
        llm.put("greeting_message", greetingMessage);
        llm.put("failure_message", "잠시 후 다시 말씀해 주세요.");
        llm.put("max_history", 30);
        llm.put("params", llmParams);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("channel", channelName);
        properties.put("token", "");   // Agora 콘솔에서 No Authentication 설정 필요
        properties.put("agent_rtc_uid", String.valueOf(AGENT_UID));
        properties.put("remote_rtc_uids", List.of("*"));
        properties.put("idle_timeout", 60);
        properties.put("advanced_features", Map.of("enable_aivad", true));
        properties.put("llm", llm);

        // 콜백 URL이 설정되어 있으면 대화 내용 저장
        if (props.getCallbackUrl() != null && !props.getCallbackUrl().isBlank()) {
            properties.put("message_subscriber", Map.of(
                    "url", props.getCallbackUrl() + "/agora/callback"
            ));
        }

        Map<String, Object> requestBody = Map.of(
                "name", "agent-" + channelName,
                "properties", properties
        );

        ResponseEntity<Map> resp = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(requestBody, basicAuthHeaders()), Map.class);

        String agentId = (String) resp.getBody().get("agent_id");
        log.info("Agora AI agent started: channel={}, agentId={}", channelName, agentId);
        return agentId;
    }

    public void stopConversationalAI(String agentId) {
        if (agentId == null || agentId.isBlank()) return;
        try {
            String url = String.format(CONV_AI_BASE + "/%s", props.getAppId(), agentId);
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
