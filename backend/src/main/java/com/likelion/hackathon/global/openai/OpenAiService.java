package com.likelion.hackathon.global.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.likelion.hackathon.domain.agenda.entity.Agenda;
import com.likelion.hackathon.domain.agenda.entity.AgendaReferenceDocument;
import com.likelion.hackathon.domain.meeting.entity.MeetingPosition;
import com.likelion.hackathon.domain.meeting.entity.Transcript;
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
    // gpt-4o-mini는 MatchIntentService와 같은 RPD 한도 풀을 공유해서(이 조직 키 기준 하루
    // 50회) 라이브 회의 중 소진되면 안건 초안 생성도 같이 막힌다. gpt-4.1로 교체.
    private static final String MODEL = "gpt-4.1";

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
            String confidenceLevel, // "DOCUMENT_BASED" | "ESTIMATED"
            String sourceDocumentTitle // 근거 문서 제목(핵심 맥락 문서는 절대 여기 안 옴). 없으면 null.
    ) {}

    /**
     * 참조 문서 기반으로 협상 안건 초안 생성.
     *
     * ⚠️ 예전 프롬프트는 "3~5개 생성하세요"로 개수를 강제하고, 핵심 맥락(isCoreContext)
     * 문서를 구분 없이 그냥 다 섞어 넣었고, "문서에 없으면 만들지 마라"는 지시가 약했다 —
     * 프론트 ai-core/generateDraftPositions.ts(이미 실제 API로 검증된 프롬프트)의 규칙을
     * 그대로 옮겨왔다: (1) 질문 성격에 맞는 필드만 채움, (2) 근거 없으면 안건 자체를 안
     * 만듦(개수 강제 없음, 빈 배열 허용), (3) 핵심 맥락 문서는 큰 틀 참고용일 뿐 직접
     * 근거·sourceDocumentTitle로 안 씀, (4) 관련성 낮은 안건보다 적은 개수를 우선.
     * 또한 예전엔 어떤 문서가 근거인지 안 물어보고 항상 refDocs.get(0)으로 고정했었는데
     * (실제 버그), 이제 sourceDocumentTitle을 물어봐서 실제 근거 문서를 정확히 연결한다.
     */
    public List<DraftPosition> generateDraftPositions(Agenda agenda, List<AgendaReferenceDocument> refDocs) {
        List<AgendaReferenceDocument> coreDocs = refDocs.stream()
                .filter(r -> r.getSourceDocument().isCoreContext()).toList();
        List<AgendaReferenceDocument> otherDocs = refDocs.stream()
                .filter(r -> !r.getSourceDocument().isCoreContext()).toList();

        StringBuilder coreContent = new StringBuilder();
        for (AgendaReferenceDocument ref : coreDocs) {
            appendDoc(coreContent, ref);
        }
        StringBuilder otherContent = new StringBuilder();
        for (AgendaReferenceDocument ref : otherDocs) {
            appendDoc(otherContent, ref);
        }

        String systemPrompt = """
                당신은 "특기전력" 서비스의 안건 초안 생성 엔진입니다. 특기전력은 시차가 큰
                글로벌 팀이 실시간 회의 없이도 협업할 수 있도록, 답변 작성자가 사전에 승인한
                내용만 AI가 상대방에게 대신 전달하는 서비스입니다.

                당신의 역할은 답변 작성자가 업로드한 문서를 근거로, 다가올 회의에서 상대방이
                물어볼 만한 "예상 질문(안건)"과 그에 대한 "답변 초안"을 미리 만들어 두는
                것입니다. 답변 작성자는 이 초안을 확인하고 승인/수정만 하면 됩니다 — 당신의
                결과물은 최종 발화가 아니라 사람이 검토할 "초안"입니다.

                # 반드시 지켜야 할 핵심 규칙

                ## 규칙 1: 질문 성격에 맞는 필드만 채워라
                일정/기한 질문 → preference/concessionRange/scheduleConstraint 위주.
                계약/조건/범위 질문 → dealbreaker까지 포함. 우선순위가 문서에서 드러날 때만
                priority를 채운다. 단순 사실 확인성 질문이면 answer 하나만 채워도 된다.
                무관한 필드는 반드시 null로 남기고 activeFields에도 포함하지 마라. 모든
                필드를 습관적으로 채우는 것은 이 기능의 핵심 규칙 위반이다.

                ## 규칙 2: 문서에 없는 내용은 추정하지 말고 만들지 마라
                직접적이고 명확한 근거가 있으면 confidenceLevel: "DOCUMENT_BASED".
                직접 근거는 없지만 문맥상 합리적으로 추론 가능한 경우에만 "ESTIMATED".
                근거가 전혀 없는 안건은 만들지 마라 — "그럴듯해 보이는" 질문을 지어내는 것
                자체가 금지다. 차라리 안건 개수가 적어지는 게 낫다.

                ## 규칙 3: 핵심 맥락 문서는 큰 틀만 잡는 데 쓴다
                "핵심 맥락 문서"는 팀 구성, 프로젝트 목적, 배경 같은 큰 틀을 이해하는 데만
                참고하라. 구체적인 답변, 협상 조건, 일정, 수치는 반드시 "그 외 참고 문서"에서
                찾아라. 핵심 맥락 문서의 제목을 sourceDocumentTitle로 지정하지 마라.

                ## 규칙 4: 개수보다 관련성과 근거성을 우선해라
                각 안건은 반드시 회의 목적과 직접 관련 있어야 한다. 일반적으로 소수의 핵심
                안건만 생성하고, 비슷한 안건은 하나로 통합해라. 최소 개수는 없다 — 적합한
                안건이 없으면 positions를 빈 배열로 반환해라. 안건 수를 늘리는 것보다
                근거가 약한 안건을 제외하는 것을 항상 우선해라.

                ## 규칙 5: topic을 제외한 모든 텍스트는 한국어로 작성해라
                topic만 예외로 영문 snake_case를 쓴다(버전관리용 식별자이기 때문).

                # 출력 형식
                반드시 아래 JSON 하나만 출력해라. 그 외 텍스트는 절대 출력하지 마라.
                {
                  "positions": [
                    {
                      "topic": "snake_case 영문 식별자, 예: api_deadline",
                      "questionText": "예상 질문",
                      "answer": "string 또는 null",
                      "preference": "string 또는 null",
                      "concessionRange": "string 또는 null",
                      "dealbreaker": "string 또는 null",
                      "priority": "number 또는 null",
                      "confidenceLevel": "DOCUMENT_BASED 또는 ESTIMATED",
                      "sourceDocumentTitle": "근거가 된 '그 외 참고 문서'의 제목, 없으면 null"
                    }
                  ]
                }
                """;

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
                coreDocs.isEmpty() ? "(없음)" : coreContent.toString().trim(),
                otherDocs.isEmpty() ? "(없음)" : otherContent.toString().trim()
        );

        String responseJson = callChatApi(systemPrompt, userPrompt);
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
                        p.path("priority").isNull() ? null : p.path("priority").asInt(),
                        text(p, "confidenceLevel"),
                        text(p, "sourceDocumentTitle")
                ));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse OpenAI draft positions response: {}", e.getMessage());
            return List.of();
        }
    }

    private void appendDoc(StringBuilder sb, AgendaReferenceDocument ref) {
        String content = ref.getSourceDocument().getContent();
        if (content == null || content.isBlank()) return;
        sb.append("### ").append(ref.getSourceDocument().getTitle()).append("\n");
        sb.append(content).append("\n\n");
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

    public record AgreedOutcome(
            String topic,
            String status,      // AGREED / OUT_OF_RANGE_AGREED / NOT_AGREED / NOT_DISCUSSED
            String agreedValue  // 사람이 읽을 요약, 예: "8월 17일까지 납품하기로 합의"
    ) {}

    /**
     * 회의 종료 시 전체 대화를 안건 목록과 함께 한 번에 넘겨
     * 안건별 최종 합의 결과를 추출한다.
     */
    public List<AgreedOutcome> extractAgreedOutcomes(List<MeetingPosition> meetingPositions,
                                                       List<Transcript> transcripts) {
        if (meetingPositions.isEmpty() || transcripts.isEmpty()) return List.of();

        StringBuilder positionsSb = new StringBuilder();
        for (MeetingPosition mp : meetingPositions) {
            var p = mp.getPosition();
            positionsSb.append("- topic: ").append(p.getTopic()).append("\n");
            positionsSb.append("  질문: ").append(p.getQuestionText()).append("\n");
            if (p.getPreference() != null) {
                positionsSb.append("  선호안: ").append(p.getPreference()).append("\n");
            }
            if (p.getConcessionRange() != null) {
                positionsSb.append("  승인된 양보 범위: ").append(p.getConcessionRange()).append("\n");
            }
            if (p.getDealbreaker() != null) {
                positionsSb.append("  딜브레이커(넘으면 안 되는 선): ").append(p.getDealbreaker()).append("\n");
            }
        }

        StringBuilder transcriptSb = new StringBuilder();
        for (Transcript t : transcripts) {
            transcriptSb.append(t.getSpeakerLabel()).append(": ").append(t.getText()).append("\n");
        }

        String userPrompt = String.format("""
                아래는 하나의 협상 회의 전체 대화 기록과, 사전에 승인된 안건 목록입니다.

                안건 목록:
                %s

                전체 대화 기록:
                %s

                각 안건에 대해 이 대화에서 최종적으로 어떻게 됐는지 판단하세요.
                status는 다음 중 하나입니다:
                - AGREED: 승인된 양보 범위 내에서 합의됨
                - OUT_OF_RANGE_AGREED: 딜브레이커를 벗어난 조건으로 합의됨 (사람 확인 필요)
                - NOT_AGREED: 논의는 됐으나 결론이 나지 않음
                - NOT_DISCUSSED: 이 대화에서 언급되지 않음
                agreedValue는 실제 합의된 내용을 사람이 읽을 수 있게 한 문장으로 요약하세요
                (예: "8월 17일까지 납품하기로 합의"). 합의된 게 없으면 null.
                대화에 없는 내용은 추측해서 만들어내지 마세요.

                반드시 아래 JSON 형식으로만 응답하세요:
                {
                  "results": [
                    { "topic": "안건 topic 값 그대로", "status": "AGREED", "agreedValue": "..." }
                  ]
                }
                """,
                positionsSb, transcriptSb
        );

        String responseJson = callChatApi(
                "당신은 협상 회의록을 분석해 안건별 합의 결과를 정리하는 전문가입니다. JSON으로만 응답합니다.",
                userPrompt
        );

        if (responseJson == null) return List.of();

        try {
            JsonNode root = objectMapper.readTree(responseJson);
            List<AgreedOutcome> result = new ArrayList<>();
            for (JsonNode r : root.path("results")) {
                result.add(new AgreedOutcome(text(r, "topic"), text(r, "status"), text(r, "agreedValue")));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse OpenAI agreed-outcome response: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 전달된 답변(원문)을 상대방 언어로 번역한다. 자막 병기용.
     * 실패해도 예외를 던지지 않고 null을 반환한다 (원문 전달 자체는 막지 않기 위함).
     */
    public String translate(String text, String targetLanguage) {
        if (text == null || text.isBlank() || targetLanguage == null || targetLanguage.isBlank()) {
            return null;
        }

        String userPrompt = String.format("""
                다음 텍스트를 "%s" 언어로 번역하세요. 협상 회의에서 오간 발화이니
                의미와 뉘앙스를 정확하고 자연스럽게 옮기세요. 원문에 없는 내용을
                추가하거나 생략하지 마세요.
                반드시 아래 JSON 형식으로만 응답하세요:
                {"translation": "번역된 텍스트"}

                원문: %s
                """, targetLanguage, text);

        String responseJson = callChatApi(
                "당신은 국제 협상 회의 전문 통역사입니다. JSON으로만 응답합니다.",
                userPrompt
        );
        if (responseJson == null) return null;

        try {
            JsonNode root = objectMapper.readTree(responseJson);
            return text(root, "translation");
        } catch (Exception e) {
            log.error("Failed to parse OpenAI translation response: {}", e.getMessage());
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
