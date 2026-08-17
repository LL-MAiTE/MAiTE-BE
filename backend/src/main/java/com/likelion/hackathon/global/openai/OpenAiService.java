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
            Integer priority,
            String scheduleConstraint,
            List<String> activeFields,
            String confidenceLevel,
            String sourceDocumentTitle,
            String reasoning
    ) {}

    private static final String DRAFT_SYSTEM_PROMPT = """
            너는 "특기전력" 서비스의 안건 초안 생성 엔진이다.
            특기전력은 시차가 큰 글로벌 팀이 실시간 회의 없이도 협업할 수 있도록,
            답변 작성자가 사전에 승인한 내용만 AI가 상대방에게 대신 전달하는 서비스다.

            너의 역할은 답변 작성자가 업로드한 문서를 근거로, 다가올 회의에서 상대방이 물어볼 만한
            "예상 질문(안건)"과 그에 대한 "답변 초안"을 미리 만들어 두는 것이다.
            답변 작성자는 이 초안을 확인하고 승인/수정만 하면 된다.

            # 반드시 지켜야 할 핵심 규칙

            ## 규칙 1: 질문 성격에 맞는 필드만 채워라
            - 일정/기한 관련 질문 → preference, concessionRange, scheduleConstraint 위주
            - 계약/조건/범위 관련 질문 → dealbreaker까지 포함
            - 우선순위가 문서에서 드러나는 경우에만 priority를 숫자로 채운다
            - 단순 사실 확인성 질문이면 answer 하나만 채우고 나머지는 비워도 된다
            해당 안건과 무관한 필드는 반드시 null로 남기고 activeFields에도 포함시키지 마라.

            ## 규칙 2: 문서에 없는 내용은 만들지 마라
            - 문서에 직접 근거 있으면 confidenceLevel: "문서근거명확"
            - 문맥상 합리적 추론이면 confidenceLevel: "추정" (reasoning에 근거 명시)
            - 근거 없는 안건은 만들지 마라. 안건 수가 적어지는 게 낫다.

            ## 규칙 3: 핵심 맥락 문서(isCoreContext=true)는 큰 틀만 참고
            구체적 답변 근거(날짜, 조건, 수치)는 반드시 isCoreContext=false 문서에서 찾아라.
            핵심 맥락 문서를 sourceDocumentTitle로 지정하지 마라.

            ## 규칙 4: 안건은 3~6개
            회의 목적과 관련 있고 근거가 명확한 것만 선별해라.

            # 출력 형식
            반드시 아래 JSON 스키마를 따르는 JSON 객체 하나만 출력해라.
            {
              "positions": [
                {
                  "topic": string,
                  "questionText": string,
                  "answer": string | null,
                  "preference": string | null,
                  "concessionRange": string | null,
                  "dealbreaker": string | null,
                  "priority": number | null,
                  "scheduleConstraint": string | null,
                  "activeFields": string[],
                  "confidenceLevel": "문서근거명확" | "추정",
                  "sourceDocumentTitle": string | null,
                  "reasoning": string
                }
              ]
            }
            """;

    /**
     * 참조 문서 기반으로 협상 안건 초안 생성 (예준 generateDraftPositions 포팅)
     */
    public List<DraftPosition> generateDraftPositions(Agenda agenda, List<AgendaReferenceDocument> refDocs) {
        StringBuilder coreDocs = new StringBuilder();
        StringBuilder otherDocs = new StringBuilder();

        for (AgendaReferenceDocument ref : refDocs) {
            var doc = ref.getSourceDocument();
            if (doc.getContent() == null || doc.getContent().isBlank()) continue;
            String block = "### " + doc.getTitle() + "\n" + doc.getContent() + "\n\n";
            if (Boolean.TRUE.equals(doc.isCoreContext())) {
                coreDocs.append(block);
            } else {
                otherDocs.append(block);
            }
        }

        String userPrompt = String.format("""
                # 회의 정보
                - 회의 이름: %s
                - 회의 목적: %s
                - 상대방 정보: %s

                # 핵심 맥락 문서 (큰 틀 참고용 — 구체적 답변 근거로 쓰지 말 것)
                %s

                # 그 외 참고 문서 (구체적 답변 근거는 여기서 찾을 것)
                %s

                위 문서들을 근거로, 이번 회의에서 상대방이 물어볼 만한 안건 초안을 생성해라.
                """,
                agenda.getTitle(),
                agenda.getPurpose() != null ? agenda.getPurpose() : "-",
                agenda.getCounterpartCountry() != null ? agenda.getCounterpartCountry() : "-",
                coreDocs.isEmpty() ? "(없음)" : coreDocs,
                otherDocs.isEmpty() ? "(없음)" : otherDocs
        );

        String responseJson = callChatApi(DRAFT_SYSTEM_PROMPT, userPrompt);
        if (responseJson == null) return List.of();

        try {
            JsonNode root = objectMapper.readTree(responseJson);
            List<DraftPosition> result = new ArrayList<>();
            for (JsonNode p : root.path("positions")) {
                List<String> activeFields = new ArrayList<>();
                if (p.path("activeFields").isArray()) {
                    p.path("activeFields").forEach(f -> activeFields.add(f.asText()));
                }
                result.add(new DraftPosition(
                        text(p, "topic"),
                        text(p, "questionText"),
                        text(p, "answer"),
                        text(p, "preference"),
                        text(p, "concessionRange"),
                        text(p, "dealbreaker"),
                        p.path("priority").isNull() || p.path("priority").isMissingNode() ? null : p.path("priority").asInt(),
                        text(p, "scheduleConstraint"),
                        activeFields,
                        p.path("confidenceLevel").asText("문서근거명확"),
                        text(p, "sourceDocumentTitle"),
                        text(p, "reasoning")
                ));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse OpenAI draft positions response: {}", e.getMessage());
            return List.of();
        }
    }

    public record MatchResult(
            boolean matched,
            String matchedTopic,
            String intentMatchReasoning,
            String responseText,
            boolean containsCriticalNumber,
            String limitationNote,
            String holdReason
    ) {
        public static MatchResult hold(String reason) {
            return new MatchResult(false, null, "", null, false, null, reason);
        }
    }

    private static final String MATCH_SYSTEM_PROMPT = """
            너는 "특기전력" 서비스의 실시간 의도 매칭 엔진이다.
            특기전력은 시차가 큰 글로벌 팀이 실시간 회의 없이도 협업할 수 있도록,
            답변 작성자가 사전에 승인한 내용만 AI가 상대방에게 대신 전달하는 서비스다.

            지금 너에게는 실시간 회의 중 상대방이 한 발화(STT 결과, 오타/어색한 문장부호가 있을 수 있음)와
            답변 작성자가 사전에 승인한 안건 목록이 주어진다. 승인된 안건 중 상대방 질문과
            "핵심 의도"가 일치하는 것이 있으면 그 안건의 필드를 조합해 전달용 답변을 만들고,
            없으면 절대 지어내지 말고 보류로 처리해라.

            # 반드시 지켜야 할 핵심 규칙

            ## 규칙 1: 절대 새로운 내용을 생성하지 마라
            승인된 안건 목록에 없는 정보로 답변을 만드는 것은 이 서비스에서 가장 심각한 위반이다.
            responseText는 오직 매칭된 안건의 필드(answer, preference, concessionRange, dealbreaker,
            priority, scheduleConstraint 등)를 자연스러운 문장으로 조합한 것이어야 하며,
            그 안건에 없는 숫자/조건/약속을 새로 덧붙이면 안 된다.

            ## 규칙 2: 핵심 의도가 일치해야 매칭이다
            표면적으로 키워드가 비슷하더라도 실제로 묻는 바(질문의 핵심 의도)가 다르면 매칭시키지 마라.
            예: "마감일을 앞당길 수 있나요"에 대한 승인 답변이 있다고 해서, "계약 기간도 늘려야 하나요"
            같은 다른 주제의 질문에 그 답변을 억지로 갖다 붙이면 안 된다. 이런 경우는 matched: false다.
            안건의 topic/questionText가 다루는 주제와 실제 질문의 주제가 같은지를 기준으로 판단해라.

            ## 규칙 3: 조금이라도 불확실하면 matched: false로 보류시켜라
            확신이 서지 않으면 항상 안전한 쪽(보류)으로 기울어야 한다.
            보류가 지나치게 많은 것이 잘못된 답변을 전달하는 것보다 훨씬 안전하다.

            ## 규칙 4: 핵심 수치 표시
            responseText에 날짜/금액/수량 등 구체적인 숫자가 포함되면 containsCriticalNumber: true로
            표시해라. matched가 false면 responseText는 null이고 containsCriticalNumber도 false다.

            ## limitationNote
            매칭은 되었지만 세부 조건이 있다면 limitationNote에 적어라. 없으면 null.

            # 출력 형식
            반드시 아래 JSON 스키마를 따르는 JSON 객체 하나만 출력해라.
            {
              "matched": boolean,
              "matchedTopic": string | null,
              "intentMatchReasoning": string,
              "responseText": string | null,
              "containsCriticalNumber": boolean,
              "limitationNote": string | null,
              "holdReason": string | null
            }
            """;

    /**
     * 실시간 발화를 사전 승인된 안건과 매칭하거나, 없으면 보류 처리 (예준 matchIntentOrHold 포팅)
     */
    public MatchResult matchIntentOrHold(String question, List<MeetingPosition> positions) {
        if (positions.isEmpty()) return MatchResult.hold("승인된 안건 없음");

        StringBuilder positionsBlock = new StringBuilder();
        for (int i = 0; i < positions.size(); i++) {
            var p = positions.get(i).getPosition();
            positionsBlock.append(String.format("## 승인된 안건 %d: %s\n", i + 1, p.getTopic()));
            positionsBlock.append(String.format("- 예상 질문: %s\n", p.getQuestionText()));
            if (p.getAnswer() != null)         positionsBlock.append("- answer: ").append(p.getAnswer()).append("\n");
            if (p.getPreference() != null)     positionsBlock.append("- preference(선호안): ").append(p.getPreference()).append("\n");
            if (p.getConcessionRange() != null) positionsBlock.append("- concessionRange(양보 가능 범위): ").append(p.getConcessionRange()).append("\n");
            if (p.getDealbreaker() != null)    positionsBlock.append("- dealbreaker(양보 불가 사항): ").append(p.getDealbreaker()).append("\n");
            if (p.getPriority() != null)       positionsBlock.append("- priority: ").append(p.getPriority()).append("\n");
            if (p.getScheduleConstraint() != null) positionsBlock.append("- scheduleConstraint: ").append(p.getScheduleConstraint()).append("\n");
            positionsBlock.append("\n");
        }

        String userPrompt = String.format("""
                # 상대방 발화 (실시간 STT 결과)
                "%s"

                # 답변 작성자가 사전 승인한 안건 목록
                %s
                위 발화의 핵심 의도가 승인된 안건 중 하나와 일치하는지 판단하고,
                일치하면 그 안건의 필드만 사용해 전달용 답변을 만들고, 일치하지 않으면 보류로 처리해라.
                """, question, positionsBlock);

        String responseJson = callChatApi(MATCH_SYSTEM_PROMPT, userPrompt);
        if (responseJson == null) return MatchResult.hold("OpenAI 호출 실패");

        try {
            JsonNode root = objectMapper.readTree(responseJson);
            boolean matched = root.path("matched").asBoolean(false);
            return new MatchResult(
                    matched,
                    textOrNull(root, "matchedTopic"),
                    root.path("intentMatchReasoning").asText(""),
                    matched ? textOrNull(root, "responseText") : null,
                    matched && root.path("containsCriticalNumber").asBoolean(false),
                    matched ? textOrNull(root, "limitationNote") : null,
                    !matched ? root.path("holdReason").asText("매칭 안건 없음") : null
            );
        } catch (Exception e) {
            log.error("Failed to parse matchIntentOrHold response: {}", e.getMessage());
            return MatchResult.hold("응답 파싱 실패");
        }
    }

    private static final String NATURALIZE_SYSTEM_PROMPT = """
            당신은 협상 대리인입니다. 안건 담당자가 사전에 정해둔 범위 안에서만 협상하세요.

            ## 핵심 규칙
            1. preference에서 시작해서, 상대방 요청이 합리적이면 concessionRange 범위 내에서 조율해라
            2. concessionRange를 절대 초과하지 마라. dealbreaker는 무슨 일이 있어도 지켜라
            3. 안건에 없는 새로운 사실, 숫자, 날짜, 조건을 지어내지 마라
            4. 1~2문장으로 자연스럽고 명확하게 말해라
            5. 상대방이 사용한 언어로 답해라
            """;

    /**
     * 2단계: 매칭된 안건의 preference~dealbreaker 범위 안에서 협상 대화를 자연화.
     * JSON이 아닌 plain text를 반환.
     */
    public String naturalizeWithinBounds(String question, MeetingPosition mp,
                                         List<Map<String, String>> messages, MatchResult match) {
        var p = mp.getPosition();

        StringBuilder positionInfo = new StringBuilder();
        positionInfo.append("## 안건: ").append(p.getTopic()).append("\n");
        if (p.getAnswer() != null)          positionInfo.append("- 우리 입장: ").append(p.getAnswer()).append("\n");
        if (p.getPreference() != null)      positionInfo.append("- 선호안(여기서 시작): ").append(p.getPreference()).append("\n");
        if (p.getConcessionRange() != null) positionInfo.append("- 양보 가능 범위(절대 초과 금지): ").append(p.getConcessionRange()).append("\n");
        if (p.getDealbreaker() != null)     positionInfo.append("- Dealbreaker(무조건 사수): ").append(p.getDealbreaker()).append("\n");
        if (match.limitationNote() != null) positionInfo.append("- 주의사항: ").append(match.limitationNote()).append("\n");

        List<Map<String, String>> recent = (messages != null && messages.size() > 4)
                ? messages.subList(messages.size() - 4, messages.size())
                : (messages != null ? messages : List.of());

        StringBuilder contextStr = new StringBuilder();
        for (Map<String, String> msg : recent) {
            String role = msg.getOrDefault("role", "");
            String content = msg.getOrDefault("content", "");
            if ("system".equals(role)) continue;
            contextStr.append(role).append(": ").append(content).append("\n");
        }

        String userPrompt = String.format("""
                %s

                ## 대화 맥락
                %s

                ## 상대방 최신 발화
                "%s"

                위 안건 범위 내에서 자연스럽게 협상 답변을 생성해라.
                """, positionInfo, contextStr.isEmpty() ? "(없음)" : contextStr, question);

        return callChatApiText(NATURALIZE_SYSTEM_PROMPT, userPrompt);
    }

    @SuppressWarnings("unchecked")
    private String callChatApiText(String systemPrompt, String userPrompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "temperature", 0.3,
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
            log.error("OpenAI text API call failed: {}", e.getMessage());
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

    private String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isNull() || v.isMissingNode()) return null;
        String s = v.asText();
        return s.equals("null") || s.isBlank() ? null : s;
    }
}
