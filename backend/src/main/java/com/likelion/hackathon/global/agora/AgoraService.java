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

    @Value("${openai.api-key:}")
    private String openAiKey;

    /**
     * Agora Conversational AI 에이전트를 생성하고 채널에 입장시킴.
     * Model Credentials(addon)에 등록된 LLM/TTS 이름을 참조.
     * @return agentId (나중에 종료할 때 사용)
     */
    @SuppressWarnings("unchecked")
    public String startConversationalAI(String channelName, String systemPrompt,
                                        String language, String greetingMessage) {
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

        Map<String, Object> llm = new LinkedHashMap<>();
        if (openAiKey != null && !openAiKey.isBlank()) {
            llm.put("url", "https://api.openai.com/v1/chat/completions");
            llm.put("api_key", openAiKey);
            llm.put("model", "gpt-4o-mini");
        } else {
            llm.put("addon", props.getLlmAddon());
        }
        llm.put("system_message", systemPrompt);
        llm.put("greeting_message", greetingMessage);
        llm.put("failure_message", "잠시 후 다시 말씀해 주세요.");
        llm.put("max_history", 10);

        // TTS: OpenAI TTS when key available, otherwise addon
        Map<String, Object> tts = new LinkedHashMap<>();
        if (openAiKey != null && !openAiKey.isBlank()) {
            Map<String, Object> ttsParams = new LinkedHashMap<>();
            ttsParams.put("api_key", openAiKey);
            ttsParams.put("model", "tts-1");
            ttsParams.put("voice", "nova");
            tts.put("vendor", "openai");
            tts.put("params", ttsParams);
        } else {
            tts.put("addon", props.getTtsAddon());
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("channel", channelName);
        properties.put("token", agentToken);
        properties.put("agent_rtc_uid", String.valueOf(AGENT_UID));
        properties.put("remote_rtc_uids", List.of("*"));
        properties.put("idle_timeout", 60);
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
