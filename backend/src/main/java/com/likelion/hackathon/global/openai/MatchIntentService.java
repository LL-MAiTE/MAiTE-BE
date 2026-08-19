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

    /** MATCHED=안건과 실질적으로 일치, SMALL_TALK=인사·잡담 등 협상과 무관한 사교적 발화,
     *  OUT_OF_SCOPE=비즈니스 질문이지만 승인된 안건 어디에도 해당 안 됨(보류 대상). */
    public enum Category { MATCHED, SMALL_TALK, OUT_OF_SCOPE }

    public record MatchResult(
            Category category,
            String matchedTopic,
            UUID matchedPositionId,
            String holdReason
    ) {
        public boolean matched() { return category == Category.MATCHED; }
        public boolean isSmallTalk() { return category == Category.SMALL_TALK; }
    }

    /**
     * 1차 필터: 상대방 발화를 MATCHED/SMALL_TALK/OUT_OF_SCOPE 세 갈래로 분류.
     * 대화 히스토리를 참고해 카운터오퍼/후속 질문도 올바른 안건으로 매칭.
     */
    public MatchResult matchIntentOrHold(String question, List<MeetingPosition> meetingPositions) {
        return matchIntentOrHold(question, meetingPositions, List.of());
    }

    public MatchResult matchIntentOrHold(String question, List<MeetingPosition> meetingPositions,
                                         List<Map<String, String>> conversationHistory) {
        if (meetingPositions.isEmpty()) {
            return new MatchResult(Category.OUT_OF_SCOPE, null, null, "승인된 협상 안건이 없습니다.");
        }

        StringBuilder positionsSb = new StringBuilder();
        for (MeetingPosition mp : meetingPositions) {
            Position p = mp.getPosition();
            positionsSb.append("- topic: ").append(p.getTopic()).append("\n");
            positionsSb.append("  questionText: ").append(p.getQuestionText()).append("\n");
        }

        String systemPrompt = """
                당신은 협상 발화 의도 분류 엔진입니다. 상대방 발화를 아래 세 가지 중 하나로 분류하세요.
                - MATCHED: 핵심 의도가 승인된 안건 목록 중 하나와 실질적으로 일치
                - SMALL_TALK: 인사, 감사 표현, 잡담, 회의 진행을 위한 사교적 발화 등 협상 내용과
                  무관한 발화 (예: "안녕하세요", "반갑습니다", "오늘 날씨가 좋네요", "감사합니다")
                - OUT_OF_SCOPE: 협상/비즈니스 관련 질문이지만 승인된 안건 어디에도 해당하지 않음

                규칙:
                1. 절대 새 내용을 생성하지 않습니다.
                2. MATCHED는 안건의 questionText 주제와 실질적으로 일치할 때만 판단합니다.
                3. 이전 대화 맥락을 반드시 참고하세요. 카운터오퍼(날짜/수량/가격 제안), 재확인 질문,
                   압박 발언은 앞선 대화에서 다루던 안건 주제의 연속으로 판단하세요.
                4. 현재 발화만으로 판단이 어려워도 대화 맥락상 특정 안건과 연관되면 MATCHED로 판단하세요.
                5. 인사말이나 사교적 표현은 무조건 SMALL_TALK입니다 — OUT_OF_SCOPE로 분류하지 마세요.
                6. MATCHED도 SMALL_TALK도 아니면 안전하게 OUT_OF_SCOPE로 분류하세요.
                반드시 JSON으로만 응답: {"category": "MATCHED"|"SMALL_TALK"|"OUT_OF_SCOPE", "matchedTopic": "topic값 또는 null", "holdReason": "보류 사유 또는 null"}
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
            return new MatchResult(Category.OUT_OF_SCOPE, null, null, "AI 매칭 오류");
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            Category category;
            try {
                category = Category.valueOf(root.path("category").asText("OUT_OF_SCOPE"));
            } catch (IllegalArgumentException e) {
                category = Category.OUT_OF_SCOPE;
            }
            String matchedTopic = root.path("matchedTopic").isNull() ? null : root.path("matchedTopic").asText(null);
            String holdReason = root.path("holdReason").isNull() ? null : root.path("holdReason").asText(null);

            UUID matchedPositionId = null;
            if (category == Category.MATCHED) {
                // LLM이 MATCHED라고 답했지만 topic을 못 골랐거나(matchedTopic == null) 안건
                // 목록에 없는 topic을 지어냈으면(찾은 게 없으면) — 이건 사실상 매칭 실패다.
                // MATCHED로 잘못 남으면 controller가 matched()==true로 보고 hold 분기를
                // 건너뛰는데, matchedPositionId가 null이라 안건 조회도 실패해서 아무 자연 응대
                // 없이 고정 문구가 그대로 나가버린다 — 반드시 OUT_OF_SCOPE로 강등시켜야 한다.
                if (matchedTopic != null) {
                    matchedPositionId = meetingPositions.stream()
                            .filter(mp -> mp.getPosition().getTopic().equals(matchedTopic))
                            .map(mp -> mp.getPosition().getId())
                            .findFirst()
                            .orElse(null);
                }
                if (matchedPositionId == null) category = Category.OUT_OF_SCOPE;
            }

            return new MatchResult(category, matchedTopic, matchedPositionId, holdReason);
        } catch (Exception e) {
            log.error("Failed to parse match result: {}", e.getMessage());
            return new MatchResult(Category.OUT_OF_SCOPE, null, null, "파싱 실패");
        }
    }

    /**
     * 스몰토크(인사·잡담) 전용 응답 생성. 비즈니스 내용은 절대 언급하지 않게 강하게 제약한다 —
     * 호출부는 그래도 NegotiationGuardrail.containsFigure()로 한 번 더 확인해야 한다
     * (LLM이 실수로 숫자/날짜를 섞어 넣을 가능성 자체는 남으므로).
     */
    public String generateSmallTalkResponse(String question, List<Map<String, String>> conversationHistory) {
        String systemPrompt = """
                당신은 협상 회의에서 답변 작성자를 대리하는 AI입니다. 방금 상대방 발화는 협상 내용과
                무관한 인사/잡담입니다. 짧고 자연스럽게 한국어로 1문장만 응답하세요.
                절대 숫자, 날짜, 금액, 조건, 약속 등 비즈니스 내용을 언급하지 마세요.
                """;

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        for (Map<String, String> h : conversationHistory) {
            messages.add(Map.of("role", h.get("role"), "content", h.get("content")));
        }
        messages.add(Map.of("role", "user", "content", question));

        return callApiWithMessages(messages, 0.7, false);
    }

    /**
     * OUT_OF_SCOPE(승인된 안건 밖의 비즈니스 질문) 전용 자연 응답 생성.
     * "확인이 필요한 사항입니다. 내부 검토 후 답변드리겠습니다." 같은 고정 문구를 매번 그대로
     * 반복하면 로봇처럼 들린다는 피드백에 따라, 왜 지금 답할 수 없는지를 상황에 맞게 자연스러운
     * 한국어로 표현하되 — 절대 새로운 약속/숫자/날짜/조건을 만들어내지 않도록 강하게 제약한다.
     * 호출부는 그래도 NegotiationGuardrail.containsFigure()로 한 번 더 확인해야 한다.
     */
    public String generateHoldResponse(String question, List<Map<String, String>> conversationHistory) {
        String systemPrompt = """
                당신은 협상 회의에서 답변 작성자를 대리하는 AI입니다. 방금 상대방 발화는 사전에
                승인받은 협상 안건 범위 밖의 질문/요청입니다. 당신은 이 내용에 대해 임의로 판단하거나
                결정할 권한이 없습니다.

                아래 원칙을 지키며, 실제 협상가가 즉석에서 대응하듯 자연스럽고 정중한 한국어로
                1~2문장만 응답하세요:
                - 이 사안은 당신이 지금 이 자리에서 결정할 수 없고, 내부 확인/검토가 필요하다는
                  취지를 상황에 맞게 표현하세요. 매번 똑같은 문장을 기계적으로 반복하지 마세요.
                - 절대 숫자, 날짜, 금액, 수량, 조건, 약속 등 어떤 구체적 내용도 새로 만들어
                  언급하지 마세요. "검토해보겠다" 수준의 태도만 표현하고 결론은 내지 마세요.
                - 상대방 발화를 무시하지 말고, 무엇에 대한 질문인지는 자연스럽게 받아준 뒤 보류
                  의사를 전하세요.
                """;

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        for (Map<String, String> h : conversationHistory) {
            messages.add(Map.of("role", h.get("role"), "content", h.get("content")));
        }
        messages.add(Map.of("role", "user", "content", question));

        return callApiWithMessages(messages, 0.7, false);
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
