package com.likelion.hackathon.global.agora;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.likelion.hackathon.domain.agenda.entity.Agenda;
import com.likelion.hackathon.domain.agenda.entity.Position;
import com.likelion.hackathon.domain.agenda.entity.enums.ApprovalStatus;
import com.likelion.hackathon.domain.agenda.entity.enums.GeneratedBy;
import com.likelion.hackathon.domain.meeting.entity.Meeting;
import com.likelion.hackathon.domain.meeting.entity.MeetingPosition;
import com.likelion.hackathon.domain.meeting.repository.MeetingPositionRepository;
import com.likelion.hackathon.domain.meeting.repository.MeetingRepository;
import com.likelion.hackathon.domain.meeting.service.MeetingService;
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

    // meeting/agenda를 아직 못 찾았거나(잘못된 meetingId 등) 처리 중 예외가 나서 상대방
    // 언어를 알 수 없는 극단적 케이스에서만 쓰는 한국어 기본값. 정상 흐름에서는 항상
    // AgoraLanguage.from(agenda.getCounterpartLanguage())가 돌려주는 언어별 문구를 쓴다 —
    // 예전엔 이 세 문구가 전부 하드코딩이라 영어 상대방과 통화해도 보류/스몰토크 응답이
    // 한국어로 나가는 문제가 있었다(사용자가 직접 발견).
    private static final String HOLD_MESSAGE = AgoraLanguage.KO.getHoldMessage();

    // TTS가 "1,000"처럼 자릿수 구분 쉼표가 들어간 숫자를 하나씩 끊어 읽는 문제가 있어서
    // (예: "1,000개" → "일, 영개"), 프롬프트로도 쉼표를 쓰지 말라고 지시했지만 LLM이 실수로
    // 섞어 넣을 수 있으니 마지막 방어선으로 한 번 더 제거한다.
    private static final java.util.regex.Pattern THOUSANDS_SEPARATOR =
            java.util.regex.Pattern.compile("(?<=\\d),(?=\\d{3}\\b)");

    private final MeetingRepository meetingRepository;
    private final MeetingPositionRepository meetingPositionRepository;
    private final MeetingService meetingService;
    private final MatchIntentService matchIntentService;
    private final NegotiationGuardrail guardrail;
    private final ObjectMapper objectMapper;

    // 이 턴을 어떻게 처리했는지 — meetingService.recordLiveTurn()에 그대로 넘겨서
    // 보류함/알림/사후검토용 Transcript·MeetingLog·HoldItem을 실제로 남기는 데 쓴다.
    private record ResolvedTurn(String text, boolean isHold, UUID matchedPositionId, String holdReason) {
        static ResolvedTurn of(String text) {
            return new ResolvedTurn(text, false, null, null);
        }
    }

    @PostMapping(value = "/chat-completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chatCompletions(
            @RequestParam String meetingId,
            @RequestBody Map<String, Object> body) {

        String question = extractLastUserMessage(body);
        log.info("[chat-completions] meetingId={} question={}", meetingId, question);

        if (question == null || question.isBlank()) {
            return sseResponse("");
        }

        ResolvedTurn resolved = resolveResponse(meetingId, question, body);
        String normalized = normalizeForTts(resolved.text());
        log.info("[chat-completions] response={}", normalized);

        // ⚠️ 예전엔 실시간 통화의 대화가 어디에도 저장되지 않았다 — message_subscriber
        // 웹훅(/agora/callback)이 실제로 동작하지 않는 설정이라 Agora가 호출한 적이
        // 없었고(로그로 확인), 그 결과 보류함/알림/사후검토가 전부 빈 화면이었다. 이제
        // 응답을 확정한 바로 이 자리에서 직접 저장한다. 저장 실패가 통화 자체를
        // 끊으면 안 되니 실패해도 조용히 무시하고 응답은 그대로 내보낸다.
        try {
            meetingService.recordLiveTurn(
                    UUID.fromString(meetingId), question, normalized,
                    resolved.matchedPositionId(), resolved.isHold(), resolved.holdReason());
        } catch (Exception e) {
            log.warn("[chat-completions] failed to record live turn: {}", e.getMessage());
        }

        return sseResponse(normalized);
    }

    private String normalizeForTts(String text) {
        if (text == null) return null;
        return THOUSANDS_SEPARATOR.matcher(text).replaceAll("");
    }

    private ResolvedTurn resolveResponse(String meetingId, String question, Map<String, Object> body) {
        try {
            UUID uuid = UUID.fromString(meetingId);
            Optional<Meeting> meetingOpt = meetingRepository.findById(uuid);
            if (meetingOpt.isEmpty()) {
                log.warn("[chat-completions] meeting not found: {}", meetingId);
                return ResolvedTurn.of(HOLD_MESSAGE);
            }

            List<MeetingPosition> positions = meetingPositionRepository.findAllByMeeting(meetingOpt.get());
            if (positions.isEmpty()) {
                log.info("[chat-completions] no positions in DB, using demo positions");
                positions = buildDemoPositions();
            }

            Agenda agenda = meetingOpt.get().getAgenda();
            AgoraLanguage language = AgoraLanguage.from(agenda != null ? agenda.getCounterpartLanguage() : null);

            List<Map<String, String>> history = extractConversationHistory(body);

            // 분류 + 응답 생성을 LLM 호출 1번으로 처리한다 — 예전엔 "분류 콜 + 생성 콜"을
            // 매 턴마다 순차로 두 번 왕복해서 그 지연이 그대로 체감 지연("한 박자 늦다")으로
            // 쌓였다. 지금은 분류 결과와 그 분류에 맞는 응답을 한 JSON으로 같이 받는다.
            MatchIntentService.Resolution resolution = matchIntentService.resolve(question, positions, history, language);
            log.info("[chat-completions] category={} topic={} language={}",
                    resolution.category(), resolution.matchedTopic(), language);

            // 인사·잡담: 안건과 무관하니 hold 문구를 붙일 이유가 없다 — 자연스럽게 짧게 응대만
            // 하고, 혹시 LLM이 실수로 숫자/날짜를 섞으면(비즈니스 내용 유출) 안전한 문구로 대체.
            if (resolution.isSmallTalk()) {
                if (resolution.response() == null || guardrail.containsFigure(resolution.response())) {
                    return ResolvedTurn.of(language.getSmallTalkFallback());
                }
                return ResolvedTurn.of(resolution.response());
            }

            // AI/미팅/프로세스 자체에 대한 질문("AI이신 거죠?", "협상 잘 하실 수 있어요?",
            // "오늘 안건 뭐뭐 있어요?" 등): 비즈니스 결정이 필요 없으니 OUT_OF_SCOPE의
            // "내부 검토 필요" 톤을 붙일 이유가 없다 — 자연스럽게 바로 응대.
            if (resolution.isMeta()) {
                if (resolution.response() == null || guardrail.containsFigure(resolution.response())) {
                    return ResolvedTurn.of(language.getMetaFallback());
                }
                return ResolvedTurn.of(resolution.response());
            }

            // 안건 밖 질문: 고정 문구를 매번 그대로 반복하면 로봇처럼 들린다는 피드백에 따라
            // 자연스럽게 보류 의사를 표현하되, 새 숫자/날짜가 섞여 나오면 안전한 고정 문구로 대체.
            // 실제 협상 내용이 못 넘어갔다는 뜻이므로 보류함/알림 대상이다.
            if (!resolution.matched()) {
                String text = (resolution.response() == null || guardrail.containsFigure(resolution.response()))
                        ? language.getHoldMessage() : resolution.response();
                return new ResolvedTurn(text, true, null, "매칭된 안건 없음: " + question);
            }

            Position position = positions.stream()
                    .filter(mp -> mp.getPosition().getId().equals(resolution.matchedPositionId()))
                    .map(MeetingPosition::getPosition)
                    .findFirst()
                    .orElse(null);

            if (position == null) return ResolvedTurn.of(language.getHoldMessage());

            // 마지막 방어선: 생성된 문장이 실제로 dealbreaker/양보범위를 넘는지 결정론적으로
            // 재검증. 넘으면 그 문장은 버리고 원본(승인된) answer로 안전하게 대체한다.
            String verified = guardrail.verify(resolution.response(), position);
            if (verified != null) {
                return new ResolvedTurn(verified, false, position.getId(), null);
            }

            // ⚠️ position.getAnswer()는 항상 한국어다(안건 초안 생성 규칙상 topic 외 모든
            // 텍스트 필드가 한국어로 저장됨) — 이 폴백은 guardrail이 LLM 생성 문장을 거부한
            // 드문 경우에만 타므로, 비한국어 상대방에게는 원문(한국어)이 그대로 나갈 수 있는
            // 잔여 한계가 있다(알려진 이슈, 별도 개선 필요 — 실시간 번역 호출을 추가하면
            // 이 경로의 지연이 늘어나서 지금은 보류).
            log.warn("[chat-completions] guardrail rejected generated response, falling back to raw answer. topic={}",
                    position.getTopic());
            return new ResolvedTurn(
                    position.getAnswer() != null ? position.getAnswer() : language.getHoldMessage(),
                    false, position.getId(), null);

        } catch (IllegalArgumentException e) {
            log.warn("[chat-completions] invalid meetingId format: {}", meetingId);
            return ResolvedTurn.of(HOLD_MESSAGE);
        } catch (Exception e) {
            log.error("[chat-completions] unexpected error: {}", e.getMessage());
            return ResolvedTurn.of(HOLD_MESSAGE);
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
