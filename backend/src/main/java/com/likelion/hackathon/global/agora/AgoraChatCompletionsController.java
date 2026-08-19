package com.likelion.hackathon.global.agora;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.likelion.hackathon.domain.agenda.entity.Position;
import com.likelion.hackathon.domain.agenda.entity.enums.ApprovalStatus;
import com.likelion.hackathon.domain.agenda.entity.enums.GeneratedBy;
import com.likelion.hackathon.domain.meeting.entity.Meeting;
import com.likelion.hackathon.domain.meeting.entity.MeetingPosition;
import com.likelion.hackathon.domain.meeting.repository.MeetingPositionRepository;
import com.likelion.hackathon.domain.meeting.repository.MeetingRepository;
import com.likelion.hackathon.global.openai.MatchIntentService;
import com.likelion.hackathon.global.openai.NegotiationGuardrail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Agora Conversational AI의 Custom LLM 엔드포인트.
 * OpenAI Chat Completions API와 프로토콜 호환(요청/응답, SSE streaming).
 *
 * 흐름:
 *   1. 상대방 발화 추출
 *   2. meetingId 기반으로 승인된 안건 로드
 *   3. matchIntentOrHold — 1차 필터 (범위 밖이면 보류 문구 반환)
 *   4. 범위 내이면 해당 안건 컨텍스트로 자연스러운 협상 응답 생성
 *   5. SSE 스트림으로 반환
 */
@Slf4j
@RestController
@RequestMapping("/agora")
@RequiredArgsConstructor
public class AgoraChatCompletionsController {

    private static final String HOLD_MESSAGE = "확인이 필요한 사항입니다. 내부 검토 후 답변드리겠습니다.";

    private static final String SMALL_TALK_FALLBACK = "네, 안녕하세요.";

    private static final String META_FALLBACK = "죄송합니다, 다시 한 번 말씀해 주시겠어요?";

    // TTS가 "1,000"처럼 자릿수 구분 쉼표가 들어간 숫자를 하나씩 끊어 읽는 문제가 있어서
    // (예: "1,000개" → "일, 영개"), 프롬프트로도 쉼표를 쓰지 말라고 지시했지만 LLM이 실수로
    // 섞어 넣을 수 있으니 마지막 방어선으로 한 번 더 제거한다.
    private static final java.util.regex.Pattern THOUSANDS_SEPARATOR =
            java.util.regex.Pattern.compile("(?<=\\d),(?=\\d{3}\\b)");

    private final MeetingRepository meetingRepository;
    private final MeetingPositionRepository meetingPositionRepository;
    private final MatchIntentService matchIntentService;
    private final NegotiationGuardrail guardrail;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/chat-completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chatCompletions(
            @RequestParam String meetingId,
            @RequestBody Map<String, Object> body) {

        String question = extractLastUserMessage(body);
        log.info("[chat-completions] meetingId={} question={}", meetingId, question);

        if (question == null || question.isBlank()) {
            return sseResponse("");
        }

        String responseText = resolveResponse(meetingId, question, body);
        return sseResponse(normalizeForTts(responseText));
    }

    private String normalizeForTts(String text) {
        if (text == null) return null;
        return THOUSANDS_SEPARATOR.matcher(text).replaceAll("");
    }

    private String resolveResponse(String meetingId, String question, Map<String, Object> body) {
        try {
            UUID uuid = UUID.fromString(meetingId);
            Optional<Meeting> meetingOpt = meetingRepository.findById(uuid);
            if (meetingOpt.isEmpty()) {
                log.warn("[chat-completions] meeting not found: {}", meetingId);
                return HOLD_MESSAGE;
            }

            List<MeetingPosition> positions = meetingPositionRepository.findAllByMeeting(meetingOpt.get());
            if (positions.isEmpty()) {
                log.info("[chat-completions] no positions in DB, using demo positions");
                positions = buildDemoPositions();
            }

            List<Map<String, String>> history = extractConversationHistory(body);

            // 분류 + 응답 생성을 LLM 호출 1번으로 처리한다 — 예전엔 "분류 콜 + 생성 콜"을
            // 매 턴마다 순차로 두 번 왕복해서 그 지연이 그대로 체감 지연("한 박자 늦다")으로
            // 쌓였다. 지금은 분류 결과와 그 분류에 맞는 응답을 한 JSON으로 같이 받는다.
            MatchIntentService.Resolution resolution = matchIntentService.resolve(question, positions, history);
            log.info("[chat-completions] category={} topic={}", resolution.category(), resolution.matchedTopic());

            // 인사·잡담: 안건과 무관하니 hold 문구를 붙일 이유가 없다 — 자연스럽게 짧게 응대만
            // 하고, 혹시 LLM이 실수로 숫자/날짜를 섞으면(비즈니스 내용 유출) 안전한 문구로 대체.
            if (resolution.isSmallTalk()) {
                if (resolution.response() == null || guardrail.containsFigure(resolution.response())) {
                    return SMALL_TALK_FALLBACK;
                }
                return resolution.response();
            }

            // AI/미팅/프로세스 자체에 대한 질문("AI이신 거죠?", "협상 잘 하실 수 있어요?",
            // "오늘 안건 뭐뭐 있어요?" 등): 비즈니스 결정이 필요 없으니 OUT_OF_SCOPE의
            // "내부 검토 필요" 톤을 붙일 이유가 없다 — 자연스럽게 바로 응대.
            if (resolution.isMeta()) {
                if (resolution.response() == null || guardrail.containsFigure(resolution.response())) {
                    return META_FALLBACK;
                }
                return resolution.response();
            }

            // 안건 밖 질문: 고정 문구를 매번 그대로 반복하면 로봇처럼 들린다는 피드백에 따라
            // 자연스럽게 보류 의사를 표현하되, 새 숫자/날짜가 섞여 나오면 안전한 고정 문구로 대체.
            if (!resolution.matched()) {
                if (resolution.response() == null || guardrail.containsFigure(resolution.response())) {
                    return HOLD_MESSAGE;
                }
                return resolution.response();
            }

            Position position = positions.stream()
                    .filter(mp -> mp.getPosition().getId().equals(resolution.matchedPositionId()))
                    .map(MeetingPosition::getPosition)
                    .findFirst()
                    .orElse(null);

            if (position == null) return HOLD_MESSAGE;

            // 마지막 방어선: 생성된 문장이 실제로 dealbreaker/양보범위를 넘는지 결정론적으로
            // 재검증. 넘으면 그 문장은 버리고 원본(승인된) answer로 안전하게 대체한다.
            String verified = guardrail.verify(resolution.response(), position);
            if (verified != null) return verified;

            log.warn("[chat-completions] guardrail rejected generated response, falling back to raw answer. topic={}",
                    position.getTopic());
            return position.getAnswer() != null ? position.getAnswer() : HOLD_MESSAGE;

        } catch (IllegalArgumentException e) {
            log.warn("[chat-completions] invalid meetingId format: {}", meetingId);
            return HOLD_MESSAGE;
        } catch (Exception e) {
            log.error("[chat-completions] unexpected error: {}", e.getMessage());
            return HOLD_MESSAGE;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractLastUserMessage(Map<String, Object> body) {
        Object messagesObj = body.get("messages");
        if (!(messagesObj instanceof List)) return null;
        List<Map<String, Object>> messages = (List<Map<String, Object>>) messagesObj;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> msg = messages.get(i);
            if (!"user".equals(msg.get("role"))) continue;
            Object content = msg.get("content");
            if (content instanceof String) return (String) content;
            if (content instanceof List) {
                return ((List<Map<String, Object>>) content).stream()
                        .filter(p -> "text".equals(p.get("type")))
                        .map(p -> String.valueOf(p.get("text")))
                        .findFirst().orElse(null);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> extractConversationHistory(Map<String, Object> body) {
        Object messagesObj = body.get("messages");
        if (!(messagesObj instanceof List)) return List.of();
        List<Map<String, Object>> messages = (List<Map<String, Object>>) messagesObj;
        List<Map<String, String>> history = new ArrayList<>();
        for (Map<String, Object> msg : messages) {
            String role = String.valueOf(msg.get("role"));
            if ("system".equals(role)) continue;
            Object content = msg.get("content");
            String text = content instanceof String ? (String) content : String.valueOf(content);
            history.add(Map.of("role", role, "content", text));
        }
        return history;
    }

    private ResponseEntity<StreamingResponseBody> sseResponse(String text) {
        String id = "chatcmpl-" + System.currentTimeMillis();
        long created = System.currentTimeMillis() / 1000;

        StreamingResponseBody stream = out -> {
            try {
                out.write(buildSseChunk(id, created, Map.of("role", "assistant", "content", text), null));
                out.write(buildSseChunk(id, created, Map.of(), "stop"));
                out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (Exception e) {
                log.error("[chat-completions] streaming error: {}", e.getMessage());
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .body(stream);
    }

    // 테스트용 데모 안건 — DB에 승인된 포지션이 없을 때 폴백으로 사용
    private List<MeetingPosition> buildDemoPositions() {
        Position p1 = Position.builder()
                .id(UUID.randomUUID())
                .topic("납기일")
                .questionText("납품을 언제까지 해줄 수 있나요?")
                .generatedBy(GeneratedBy.AI_DRAFT)
                .approvalStatus(ApprovalStatus.APPROVED)
                .preference("3월 15일까지 납품 가능")
                .concessionRange("최대 3월 22일까지 연장 가능")
                .dealbreaker("3월 22일 이후는 절대 불가")
                .activeFields(List.of("preference", "concessionRange", "dealbreaker"))
                .build();

        Position p2 = Position.builder()
                .id(UUID.randomUUID())
                .topic("납품_단가")
                .questionText("단가를 낮춰줄 수 있나요?")
                .generatedBy(GeneratedBy.AI_DRAFT)
                .approvalStatus(ApprovalStatus.APPROVED)
                .preference("개당 12,000원 유지")
                .concessionRange("최대 11,500원까지 조정 가능")
                .dealbreaker("11,000원 미만은 절대 불가")
                .activeFields(List.of("preference", "concessionRange", "dealbreaker"))
                .build();

        Position p3 = Position.builder()
                .id(UUID.randomUUID())
                .topic("최소_발주_수량")
                .questionText("최소 주문 수량이 얼마인가요?")
                .generatedBy(GeneratedBy.AI_DRAFT)
                .approvalStatus(ApprovalStatus.APPROVED)
                .answer("최소 500개 이상 주문 필요")
                .preference("1,000개 이상 주문 시 단가 우대")
                .concessionRange("500개까지 허용")
                .dealbreaker("500개 미만 주문은 불가")
                .activeFields(List.of("answer", "preference", "concessionRange", "dealbreaker"))
                .build();

        return List.of(p1, p2, p3).stream()
                .map(p -> MeetingPosition.builder()
                        .id(UUID.randomUUID())
                        .position(p)
                        .version(1)
                        .snappedAt(java.time.LocalDateTime.now())
                        .build())
                .toList();
    }

    private byte[] buildSseChunk(String id, long created, Map<String, Object> delta, String finishReason) {
        try {
            Map<String, Object> choice = new LinkedHashMap<>();
            choice.put("index", 0);
            choice.put("delta", delta);
            choice.put("finish_reason", finishReason);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", id);
            payload.put("object", "chat.completion.chunk");
            payload.put("created", created);
            payload.put("model", "match-intent-agent");
            payload.put("choices", List.of(choice));

            return ("data: " + objectMapper.writeValueAsString(payload) + "\n\n")
                    .getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "data: {}\n\n".getBytes(StandardCharsets.UTF_8);
        }
    }
}
