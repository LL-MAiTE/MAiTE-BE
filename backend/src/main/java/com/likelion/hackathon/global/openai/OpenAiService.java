package com.likelion.hackathon.global.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.likelion.hackathon.domain.agenda.entity.Agenda;
import com.likelion.hackathon.domain.agenda.entity.AgendaReferenceDocument;
import com.likelion.hackathon.domain.meeting.entity.MeetingPosition;
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
public class OpenAiService {

    private static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o-mini";

    @Value("${openai.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public record DraftPosition(
            String topic,
            String questionText,
            String answer,
            String preference,
            String concessionRange,
            String dealbreaker,
            Integer priority
    ) {}

    /**
     * 참조 문서 기반으로 협상 안건 초안 생성
     */
    public List<DraftPosition> generateDraftPositions(Agenda agenda, List<AgendaReferenceDocument> refDocs) {
        StringBuilder docContent = new StringBuilder();
        for (AgendaReferenceDocument ref : refDocs) {
            String content = ref.getSourceDocument().getContent();
            if (content != null && !content.isBlank()) {
                docContent.append("=== ").append(ref.getSourceDocument().getTitle()).append(" ===\n");
                docContent.append(content, 0, Math.min(content.length(), 1500)).append("\n\n");
            }
        }

        String userPrompt = String.format("""
                회의 제목: %s
                회의 목적: %s
                상대방 국가: %s

                참고 문서:
                %s

                위 내용을 바탕으로 협상에서 우리 측이 다뤄야 할 안건을 3~5개 생성하세요.
                반드시 아래 JSON 형식으로 응답하세요:
                {
                  "positions": [
                    {
                      "topic": "안건 주제 (짧게)",
                      "questionText": "상대방이 물어볼 수 있는 질문",
                      "answer": "우리 측 공식 답변",
                      "preference": "선호하는 조건",
                      "concessionRange": "양보 가능한 범위",
                      "dealbreaker": "절대 양보 불가 조건",
                      "priority": 1
                    }
                  ]
                }
                priority는 1(최고)~5(낮음) 숫자입니다. 없으면 null.
                문서에서 확인 불가한 항목은 null로 두세요.
                """,
                agenda.getTitle(),
                agenda.getPurpose() != null ? agenda.getPurpose() : "-",
                agenda.getCounterpartCountry() != null ? agenda.getCounterpartCountry() : "-",
                docContent
        );

        String responseJson = callChatApi(
                "당신은 국제 협상 전문가입니다. 주어진 문서를 분석하여 협상 안건을 JSON으로 생성합니다.",
                userPrompt
        );

        if (responseJson == null) return List.of();

        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode positions = root.path("positions");
            List<DraftPosition> result = new ArrayList<>();
            for (JsonNode p : positions) {
                result.add(new DraftPosition(
                        text(p, "topic"),
                        text(p, "questionText"),
                        text(p, "answer"),
                        text(p, "preference"),
                        text(p, "concessionRange"),
                        text(p, "dealbreaker"),
                        p.path("priority").isNull() ? null : p.path("priority").asInt()
                ));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse OpenAI draft positions response: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 발화 텍스트와 가장 관련 있는 협상 안건 ID 반환 (없으면 null)
     */
    public UUID findMatchingPositionId(String transcriptText, List<MeetingPosition> positions) {
        if (positions.isEmpty()) return null;

        StringBuilder positionList = new StringBuilder();
        for (MeetingPosition mp : positions) {
            positionList.append(String.format("- id: %s | 주제: %s | 질문: %s\n",
                    mp.getId(),
                    mp.getPosition().getTopic(),
                    mp.getPosition().getQuestionText()));
        }

        String userPrompt = String.format("""
                발화 내용: "%s"

                협상 안건 목록:
                %s

                위 발화가 어느 안건과 가장 관련 있는지 판단하세요.
                관련 안건이 있으면 해당 id를, 없으면 null을 반환하세요.
                반드시 아래 JSON 형식으로 응답하세요:
                {"matchedId": "uuid 또는 null"}
                """,
                transcriptText,
                positionList
        );

        String responseJson = callChatApi(
                "당신은 회의 발화와 협상 안건을 매칭하는 전문가입니다. JSON으로만 응답합니다.",
                userPrompt
        );

        if (responseJson == null) return null;

        try {
            JsonNode root = objectMapper.readTree(responseJson);
            String matchedId = root.path("matchedId").asText(null);
            if (matchedId == null || matchedId.equals("null") || matchedId.isBlank()) return null;
            return UUID.fromString(matchedId);
        } catch (Exception e) {
            log.error("Failed to parse OpenAI matching response: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String callChatApi(String systemPrompt, String userPrompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );

            ResponseEntity<Map> resp = restTemplate.exchange(
                    CHAT_URL, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.getBody().get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");

        } catch (Exception e) {
            log.error("OpenAI API call failed: {}", e.getMessage());
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return (v.isNull() || v.isMissingNode()) ? null : v.asText();
    }
}
