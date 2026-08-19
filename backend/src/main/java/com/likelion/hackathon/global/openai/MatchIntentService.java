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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchIntentService {

    private static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";
    // gpt-4o-mini는 이 조직 키에서 일일 요청 한도(RPD 50)가 낮게 걸려 있어 하루 종일
    // 테스트하다 보면 금방 소진된다. gpt-4.1로 교체 — gpt-4o보다 판단력이 좋으면서도
    // 지연은 비슷하게 빠름(실측 ~0.8s). gpt-5도 이 키로 되지만 reasoning 모델이라
    // 트리비얼한 응답에도 ~5s가 걸려 실시간 음성 협상에는 못 씀(직접 실측 확인).
    private static final String MODEL = "gpt-4.1";

    @Value("${openai.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** MATCHED=안건과 실질적으로 일치, SMALL_TALK=인사·잡담 등 협상과 무관한 사교적 발화,
     *  META=이 AI/미팅/프로세스 자체에 대한 질문(비즈니스 결정이 필요 없어 자연스럽게 바로
     *  답해도 되는 것), OUT_OF_SCOPE=비즈니스 질문이지만 승인된 안건 어디에도 해당 안 됨
     *  (보류 대상).
     *  META를 따로 둔 이유: "오늘 어떤 안건 다룰 수 있어요?" 하나만 프롬프트에 예외로
     *  박아두면 "AI이신 거죠?", "협상 잘 하실 수 있어요?" 같은 비슷한 성격의 다른 질문들은
     *  여전히 OUT_OF_SCOPE의 "내부 검토 필요" 톤으로 새버린다 — 문구를 하나씩 늘리는 대신
     *  의미 단위 카테고리를 하나 추가해서 LLM이 알아서 분류하게 하면 표현이 뭐가 됐든
     *  일관되게 커버된다. */
    public enum Category { MATCHED, SMALL_TALK, META, OUT_OF_SCOPE }

    /**
     * 분류 결과와 그 분류에 맞는 자연스러운 응답까지 한 번에 담는다.
     * 예전엔 "분류 1콜 + 응답 생성 1콜"로 나눠서 매 턴마다 OpenAI를 두 번 순차 호출했는데,
     * 그게 그대로 왕복 지연 두 배로 쌓여 "대답이 한 박자 늦다"는 체감으로 이어졌다.
     * 분류에 필요한 컨텍스트(안건 목록)와 생성에 필요한 컨텍스트(선호안/양보범위/딜브레이커)를
     * 처음부터 같은 프롬프트에 다 주고, LLM이 분류+응답을 한 JSON으로 같이 뱉게 해서 콜 수를
     * 하나로 줄인다. 안전장치(guardrail.verify/containsFigure)는 호출부에서 그대로 유지.
     */
    public record Resolution(
            Category category,
            String matchedTopic,
            UUID matchedPositionId,
            String response
    ) {
        public boolean matched() { return category == Category.MATCHED; }
        public boolean isSmallTalk() { return category == Category.SMALL_TALK; }
        public boolean isMeta() { return category == Category.META; }
    }

    public Resolution resolve(String question, List<MeetingPosition> meetingPositions,
                               List<Map<String, String>> conversationHistory) {
        if (meetingPositions.isEmpty()) {
            return new Resolution(Category.OUT_OF_SCOPE, null, null, null);
        }

        StringBuilder positionsSb = new StringBuilder();
        for (MeetingPosition mp : meetingPositions) {
            Position p = mp.getPosition();
            positionsSb.append("- topic: ").append(p.getTopic()).append("\n");
            positionsSb.append("  questionText: ").append(p.getQuestionText()).append("\n");
            if (p.getAnswer() != null) {
                positionsSb.append("  공식 답변: ").append(p.getAnswer()).append("\n");
            }
            if (p.getPreference() != null) {
                positionsSb.append("  선호안(먼저 제시): ").append(p.getPreference()).append("\n");
            }
            if (p.getConcessionRange() != null) {
                positionsSb.append("  양보 가능 범위(처음엔 숨기고 압박받을 때만 단계적으로): ")
                        .append(p.getConcessionRange()).append("\n");
            }
            if (p.getDealbreaker() != null) {
                positionsSb.append("  절대 양보 불가(어떤 상황에서도 이 선은 넘지 않음): ")
                        .append(p.getDealbreaker()).append("\n");
            }
        }

        String systemPrompt = """
                당신은 협상 회의에서 답변 작성자를 대리하는 AI 협상 대리인입니다. 상대방 발화를
                분석해서 아래 네 카테고리 중 하나로 분류하는 동시에, 그 분류에 맞는 응답까지
                자연스러운 한국어 구어체로 함께 생성하세요. 실제 협상가와 대화하는 것 같은
                자연스러운 대화 품질을 내되, 비즈니스 내용에 대해서는 절대 임의로 결정하지 않습니다.

                [카테고리]
                - MATCHED: 핵심 의도가 승인된 안건 목록 중 하나와 실질적으로 일치. 카운터오퍼
                  (날짜/수량/가격 제안), 재확인 질문, 압박 발언은 앞선 대화에서 다루던 안건 주제의
                  연속으로 판단하세요. 현재 발화만으로 판단이 어려워도 대화 맥락상 특정 안건과
                  연관되면 MATCHED로 판단하세요.
                - SMALL_TALK: 인사, 감사 표현, 잡담, 회의 진행을 위한 사교적 발화 등 협상 내용과
                  무관한 발화 (예: "안녕하세요", "오늘 날씨가 좋네요"). 인사말/사교적 표현은
                  무조건 SMALL_TALK입니다 — OUT_OF_SCOPE로 분류하지 마세요.
                - META: 협상 안건 자체는 아니지만 이 AI/미팅/협상 프로세스에 대한 질문이나
                  발언 — 예: "당신은 AI인가요?", "협상을 제대로 하실 수 있어요?", "오늘 어떤
                  안건들을 다룰 수 있나요?", "이 툴 저도 쓰고 싶은데요", "좀 못 미더운데요".
                  비즈니스 결정이 필요 없는 내용이므로 OUT_OF_SCOPE의 "내부 검토가 필요"
                  톤을 쓰지 말고 META로 분류하세요.
                - OUT_OF_SCOPE: 승인된 안건 어디에도 해당하지 않는 "비즈니스" 질문/요청
                  (META가 아닌 것 — 실제 조건/금액/일정 등 새로운 결정이 필요한 내용).

                [응답 생성 원칙 — 카테고리별]
                - MATCHED: 매칭된 안건의 선호안부터 제시하고, 상대가 압박할 때만 양보 가능
                  범위 내에서 단계적으로 조율하세요. 딜브레이커는 정중하지만 단호하게
                  거절하세요. 그 안건 필드에 없는 숫자/날짜/조건은 절대 만들어내지 마세요.
                - SMALL_TALK: 짧고 자연스럽게 1문장. 숫자, 날짜, 금액, 조건, 약속 등 비즈니스
                  내용은 절대 언급하지 마세요.
                - META: 자신감 있고 자연스럽게 1~2문장으로 바로 답하세요. "내부 검토가
                  필요하다" 같은 보류 톤은 쓰지 마세요. 안건 목록을 묻는 질문이면 승인된
                  안건들의 topic을 그대로 나열해서 알려주세요(이미 승인된 주제 이름을
                  알려주는 것은 새로운 결정이 아니므로 허용). 그 외 새로운 비즈니스 숫자/
                  날짜/조건은 만들어내지 마세요.
                - OUT_OF_SCOPE: 이 사안은 지금 이 자리에서 결정할 권한이 없고 내부 확인/검토가
                  필요하다는 취지를, 매번 다른 자연스러운 표현으로 1~2문장 전달하세요.
                  숫자/날짜/금액/조건/약속 등 새로운 구체적 내용은 절대 만들어내지 마세요.
                  "검토해보겠다" 수준의 태도만 표현하고 결론은 내지 마세요.

                [절대 원칙]
                1. 승인된 안건 필드(topic/questionText/공식 답변/선호안/양보 가능 범위/절대
                   양보 불가)에 없는 내용은 어떤 카테고리에서도 지어내지 않습니다.
                2. 응답은 항상 2~3문장 이내로 간결하게.
                3. 숫자는 항상 자릿수 구분 쉼표 없이 씁니다(예: "1000개", "12000원") — 이
                   응답은 음성 합성(TTS)으로 읽히는데, 쉼표가 들어가면 "천 개"가 아니라
                   숫자를 하나씩 끊어 읽는 오류가 생깁니다.

                승인된 협상 안건 목록:
                %s

                반드시 JSON으로만 응답:
                {"category": "MATCHED"|"SMALL_TALK"|"META"|"OUT_OF_SCOPE", "matchedTopic": "MATCHED일 때 그 안건의 topic값, 아니면 null", "response": "위 원칙에 따른 실제 응답 문장"}
                """.formatted(positionsSb);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        int start = Math.max(0, conversationHistory.size() - 6);
        for (int i = start; i < conversationHistory.size(); i++) {
            Map<String, String> h = conversationHistory.get(i);
            messages.add(Map.of("role", h.get("role"), "content", h.get("content")));
        }
        messages.add(Map.of("role", "user", "content", question));

        String json = callApiWithMessages(messages, 0.4, true);
        if (json == null) {
            return new Resolution(Category.OUT_OF_SCOPE, null, null, null);
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
            String response = root.path("response").isNull() ? null : root.path("response").asText(null);

            UUID matchedPositionId = null;
            if (category == Category.MATCHED) {
                // LLM이 MATCHED라고 답했지만 topic을 못 골랐거나 안건 목록에 없는 topic을
                // 지어낸 경우(찾은 게 없으면) 사실상 매칭 실패다 — OUT_OF_SCOPE로 강등한다.
                if (matchedTopic != null) {
                    matchedPositionId = meetingPositions.stream()
                            .filter(mp -> mp.getPosition().getTopic().equals(matchedTopic))
                            .map(mp -> mp.getPosition().getId())
                            .findFirst()
                            .orElse(null);
                }
                if (matchedPositionId == null) category = Category.OUT_OF_SCOPE;
            }

            return new Resolution(category, matchedTopic, matchedPositionId, response);
        } catch (Exception e) {
            log.error("Failed to parse resolution: {}", e.getMessage());
            return new Resolution(Category.OUT_OF_SCOPE, null, null, null);
        }
    }

    @SuppressWarnings("unchecked")
    private String callApiWithMessages(List<Map<String, Object>> messages, double temperature, boolean jsonMode) {
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
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        // 429(rate limit)는 한 번만 짧게 대기 후 재시도한다. 결제/티어 승격 직후엔 OpenAI
        // 엣지 노드 간 반영 시차 때문에 같은 키로도 순간적으로 429가 섞여 나올 수 있는데,
        // 라이브 협상 중 한 턴이 통째로 고정 문구 폴백으로 죽는 것보다는 낫다.
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                ResponseEntity<Map> resp = restTemplate.exchange(
                        CHAT_URL, HttpMethod.POST, request, Map.class);

                List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.getBody().get("choices");
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                return (String) message.get("content");
            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt == 0) {
                    log.warn("OpenAI 429, retrying once after backoff: {}", e.getMessage());
                    try {
                        Thread.sleep(800);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    log.error("OpenAI API call failed after retry: {}", e.getMessage());
                    return null;
                }
            } catch (Exception e) {
                log.error("OpenAI API call failed: {}", e.getMessage());
                return null;
            }
        }
        return null;
    }
}
