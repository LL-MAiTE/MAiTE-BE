package com.likelion.hackathon.global.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.likelion.hackathon.domain.agenda.entity.Position;
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
public class MatchIntentService {

    private static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o-mini";

    @Value("${openai.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public record MatchResult(
            boolean matched,
            String matchedTopic,
            UUID matchedPositionId,
            String holdReason
    ) {}

    /**
     * 1차 필터: 상대방 발화가 승인된 안건 중 어느 것과 일치하는지 판단.
     * 대화 히스토리를 참고해 카운터오퍼/후속 질문도 올바른 안건으로 매칭.
     */
    public MatchResult matchIntentOrHold(String question, List<MeetingPosition> meetingPositions) {
        return matchIntentOrHold(question, meetingPositions, List.of());
    }

    public MatchResult matchIntentOrHold(String question, List<MeetingPosition> meetingPositions,
                                         List<Map<String, String>> conversationHistory) {
        if (meetingPositions.isEmpty()) {
            return new MatchResult(false, null, null, "승인된 협상 안건이 없습니다.");
        }

        StringBuilder positionsSb = new StringBuilder();
        for (MeetingPosition mp : meetingPositions) {
            Position p = mp.getPosition();
            positionsSb.append("- topic: ").append(p.getTopic()).append("\n");
            positionsSb.append("  questionText: ").append(p.getQuestionText()).append("\n");
        }

        String systemPrompt = """
                당신은 협상 발화 의도 매칭 엔진입니다.
                규칙:
                1. 절대 새 내용을 생성하지 않습니다.
                2. 상대방 발화의 핵심 의도가 안건의 questionText 주제와 실질적으로 일치해야만 매칭합니다.
                3. 이전 대화 맥락을 반드시 참고하세요. 카운터오퍼(날짜/수량/가격 제안), 재확인 질문,
                   압박 발언은 앞선 대화에서 다루던 안건 주제의 연속으로 판단하세요.
                4. 현재 발화만으로 판단이 어려워도 대화 맥락상 특정 안건과 연관되면 matched=true로 판단하세요.
                   맥락이 전혀 없을 때만 matched=false로 판단합니다.
                반드시 JSON으로만 응답: {"matched": true/false, "matchedTopic": "topic값 또는 null", "holdReason": "보류 사유 또는 null"}
                """;

        StringBuilder recentHistory = new StringBuilder();
        if (!conversationHistory.isEmpty()) {
            recentHistory.append("\n최근 대화 맥락:\n");
            int start = Math.max(0, conversationHistory.size() - 6);
            for (int i = start; i < conversationHistory.size(); i++) {
                Map<String, String> turn = conversationHistory.get(i);
                String role = "user".equals(turn.get("role")) ? "상대방" : "우리(AI)";
                recentHistory.append(role).append(": ").append(turn.get("content")).append("\n");
            }
        }

        String userPrompt = String.format("""
                현재 상대방 발화: "%s"
                %s
                승인된 협상 안건 목록:
                %s
                """, question, recentHistory, positionsSb);

        String json = callApi(systemPrompt, userPrompt, 0.0, true);
        if (json == null) {
            return new MatchResult(false, null, null, "AI 매칭 오류");
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            boolean matched = root.path("matched").asBoolean(false);
            String matchedTopic = root.path("matchedTopic").isNull() ? null : root.path("matchedTopic").asText(null);
            String holdReason = root.path("holdReason").isNull() ? null : root.path("holdReason").asText(null);

            UUID matchedPositionId = null;
            if (matched && matchedTopic != null) {
                matchedPositionId = meetingPositions.stream()
                        .filter(mp -> mp.getPosition().getTopic().equals(matchedTopic))
                        .map(mp -> mp.getPosition().getId())
                        .findFirst()
                        .orElse(null);
                if (matchedPositionId == null) matched = false;
            }

            return new MatchResult(matched, matchedTopic, matchedPositionId, holdReason);
        } catch (Exception e) {
            log.error("Failed to parse match result: {}", e.getMessage());
            return new MatchResult(false, null, null, "파싱 실패");
        }
    }

    /**
     * 2단계: 매칭된 안건 범위 내에서 자연스러운 협상 응답 생성.
     * 선호안부터 제시하고, 압박받을 때만 단계적으로 양보.
     */
    public String generateNaturalResponse(String question, Position position,
                                          List<Map<String, String>> conversationHistory) {
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("당신은 협상 대리인입니다. 아래 안건 범위 내에서 자연스럽게 협상하세요.\n\n");
        systemPrompt.append("현재 안건: ").append(position.getTopic()).append("\n");
        systemPrompt.append("예상 질문: ").append(position.getQuestionText()).append("\n");
        if (position.getAnswer() != null) {
            systemPrompt.append("공식 답변: ").append(position.getAnswer()).append("\n");
        }
        if (position.getPreference() != null) {
            systemPrompt.append("선호안: ").append(position.getPreference())
                    .append(" (이것을 먼저 제시하세요)\n");
        }
        if (position.getConcessionRange() != null) {
            systemPrompt.append("양보 가능 범위: ").append(position.getConcessionRange())
                    .append(" (처음부터 드러내지 마세요. 상대가 압박할 때만 단계적으로)\n");
        }
        if (position.getDealbreaker() != null) {
            systemPrompt.append("절대 양보 불가: ").append(position.getDealbreaker())
                    .append(" (어떤 상황에서도 이 선을 넘지 마세요)\n");
        }
        systemPrompt.append("\n협상 원칙:\n");
        systemPrompt.append("- 실제 협상가처럼 자연스럽게 말하세요.\n");
        systemPrompt.append("- 선호안부터 제시하고, 압박받을 때만 양보 범위 내에서 단계적으로 조율하세요.\n");
        systemPrompt.append("- 딜브레이커는 정중하지만 단호하게 거절하세요.\n");
        systemPrompt.append("- 안건에 없는 내용은 절대 만들어내지 마세요.\n");
        systemPrompt.append("- 2~3문장으로 간결하게 응답하세요.\n");

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt.toString()));
        for (Map<String, String> h : conversationHistory) {
            messages.add(Map.of("role", h.get("role"), "content", h.get("content")));
        }
        messages.add(Map.of("role", "user", "content", question));

        return callApiWithMessages(messages, 0.7, false);
    }

    @SuppressWarnings("unchecked")
    private String callApi(String systemPrompt, String userPrompt, double temperature, boolean jsonMode) {
        return callApiWithMessages(
                List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                temperature, jsonMode
        );
    }

    @SuppressWarnings("unchecked")
    private String callApiWithMessages(List<Map<String, Object>> messages, double temperature, boolean jsonMode) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", MODEL);
            body.put("messages", messages);
            body.put("temperature", temperature);
            if (jsonMode) {
                body.put("response_format", Map.of("type", "json_object"));
            }

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
}
