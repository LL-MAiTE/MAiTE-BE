package com.likelion.hackathon.global.agora;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.likelion.hackathon.domain.meeting.entity.Meeting;
import com.likelion.hackathon.domain.meeting.entity.MeetingPosition;
import com.likelion.hackathon.domain.meeting.repository.MeetingPositionRepository;
import com.likelion.hackathon.domain.meeting.repository.MeetingRepository;
import com.likelion.hackathon.global.openai.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgoraChatService {

    private final MeetingRepository meetingRepository;
    private final MeetingPositionRepository meetingPositionRepository;
    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;

    private static final String HOLD_MESSAGE =
            "해당 사항은 보류로 기록하고, 담당자 확인 후 전달드리겠습니다.";
    private static final String FALLBACK_MESSAGE =
            "해당 범위는 내부 기준상 전달드리기 어렵습니다. 담당자 확인 후 전달드리겠습니다.";

    // 날짜: 8/28, 8월 28일
    private static final Pattern DATE_PATTERN =
            Pattern.compile("(\\d{1,2})/(\\d{1,2})|(\\d{1,2})월\\s*(\\d{1,2})일");
    // 금액/수량: 100만원, $1000, 5개, 10명
    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:만원|달러|원|\\$|USD|개|명|%)");

    public void stream(UUID meetingId, String question,
                       List<Map<String, String>> messages, SseEmitter emitter) {
        try {
            Meeting meeting = meetingRepository.findById(meetingId).orElse(null);
            if (meeting == null) {
                streamText(HOLD_MESSAGE, emitter);
                return;
            }

            List<MeetingPosition> positions = meetingPositionRepository.findAllByMeeting(meeting);

            // ── 1단계: 결정론적 매칭 ─────────────────────────────────────
            OpenAiService.MatchResult match = openAiService.matchIntentOrHold(question, positions);

            if (!match.matched()) {
                log.info("No match — hold reason: {}", match.holdReason());
                streamText(HOLD_MESSAGE, emitter);
                return;
            }

            MeetingPosition matched = positions.stream()
                    .filter(mp -> mp.getPosition().getTopic().equals(match.matchedTopic()))
                    .findFirst().orElse(null);

            // ── boundary 없는 안건: responseText 그대로 ──────────────────
            if (matched == null || !hasParsableBoundary(matched)) {
                String response = match.responseText() != null
                        ? match.responseText()
                        : (matched != null ? matched.getPosition().getAnswer() : HOLD_MESSAGE);
                streamText(response, emitter);
                return;
            }

            // ── 2단계: 범위 내 협상 자연화 ───────────────────────────────
            String naturalized = openAiService.naturalizeWithinBounds(question, matched, messages, match);
            if (naturalized == null || naturalized.isBlank()) {
                streamText(match.responseText() != null ? match.responseText() : HOLD_MESSAGE, emitter);
                return;
            }

            // ── 3단계: 결정론적 검증 ─────────────────────────────────────
            String verified = verifyBoundary(naturalized, matched);
            streamText(verified, emitter);

        } catch (Exception e) {
            log.error("AgoraChatService error: {}", e.getMessage());
            try { streamText(HOLD_MESSAGE, emitter); } catch (Exception ex) { emitter.completeWithError(ex); }
        }
    }

    // ── boundary 감지 ─────────────────────────────────────────────────────

    private boolean hasParsableBoundary(MeetingPosition mp) {
        var p = mp.getPosition();
        String concession = p.getConcessionRange();
        String dealbreaker = p.getDealbreaker();
        return hasPattern(concession) || hasPattern(dealbreaker);
    }

    private boolean hasPattern(String text) {
        if (text == null) return false;
        return DATE_PATTERN.matcher(text).find() || NUMBER_PATTERN.matcher(text).find();
    }

    // ── 3단계: 수치 검증 ──────────────────────────────────────────────────

    private String verifyBoundary(String text, MeetingPosition mp) {
        var p = mp.getPosition();
        // dealbreaker 우선, 없으면 concessionRange
        String boundarySource = p.getDealbreaker() != null ? p.getDealbreaker() : p.getConcessionRange();
        if (boundarySource == null) return text;

        // 날짜 검증
        OptionalInt bDate = extractFirstDate(boundarySource);
        if (bDate.isPresent()) {
            OptionalInt gDate = extractFirstDate(text);
            if (gDate.isPresent() && exceedsDateBoundary(gDate.getAsInt(), bDate.getAsInt(), boundarySource)) {
                log.warn("Date boundary violation: generated={} boundary={}", gDate.getAsInt(), bDate.getAsInt());
                return FALLBACK_MESSAGE;
            }
            // 날짜 파싱 가능한 boundary인데 생성 텍스트에서 날짜를 못 읽으면 → 안전하게 통과
        }

        // 금액/수량 검증
        OptionalDouble bNum = extractFirstNumber(boundarySource);
        if (bNum.isPresent()) {
            OptionalDouble gNum = extractFirstNumber(text);
            if (gNum.isPresent() && exceedsNumberBoundary(gNum.getAsDouble(), bNum.getAsDouble(), boundarySource)) {
                log.warn("Number boundary violation: generated={} boundary={}", gNum.getAsDouble(), bNum.getAsDouble());
                return FALLBACK_MESSAGE;
            }
        }

        return text;
    }

    // MMDD 정수로 변환 (8/28 → 828)
    private OptionalInt extractFirstDate(String text) {
        Matcher m = DATE_PATTERN.matcher(text);
        while (m.find()) {
            if (m.group(1) != null && m.group(2) != null)
                return OptionalInt.of(Integer.parseInt(m.group(1)) * 100 + Integer.parseInt(m.group(2)));
            if (m.group(3) != null && m.group(4) != null)
                return OptionalInt.of(Integer.parseInt(m.group(3)) * 100 + Integer.parseInt(m.group(4)));
        }
        return OptionalInt.empty();
    }

    private OptionalDouble extractFirstNumber(String text) {
        Matcher m = NUMBER_PATTERN.matcher(text);
        if (m.find()) return OptionalDouble.of(Double.parseDouble(m.group(1)));
        return OptionalDouble.empty();
    }

    // "까지", "이후 불가" → 상한선. generated > boundary 이면 위반
    private boolean exceedsDateBoundary(int generated, int boundary, String source) {
        boolean isLower = source.contains("이전") && !source.contains("이전불가");
        if (isLower) return generated < boundary;
        return generated > boundary;
    }

    private boolean exceedsNumberBoundary(double generated, double boundary, String source) {
        boolean isLower = source.contains("이상") && !source.contains("이하");
        if (isLower) return generated < boundary;
        return generated > boundary;
    }

    // ── SSE 스트리밍 ──────────────────────────────────────────────────────

    private void streamText(String text, SseEmitter emitter) throws Exception {
        String id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long created = System.currentTimeMillis() / 1000;

        // 단어 단위로 청크 분할
        for (String chunk : text.split("(?<=\\s)|(?=\\s)")) {
            if (chunk.isEmpty()) continue;
            emitter.send(SseEmitter.event().data(buildChunk(id, created, chunk)));
        }
        emitter.send(SseEmitter.event().data(buildStopChunk(id, created)));
        emitter.send(SseEmitter.event().data("[DONE]"));
        emitter.complete();
    }

    private String buildChunk(String id, long created, String content) throws Exception {
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("delta", Map.of("content", content));
        choice.put("finish_reason", null);
        return objectMapper.writeValueAsString(Map.of(
                "id", id, "object", "chat.completion.chunk",
                "created", created, "model", "gpt-4o-mini", "choices", List.of(choice)));
    }

    private String buildStopChunk(String id, long created) throws Exception {
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("delta", Map.of());
        choice.put("finish_reason", "stop");
        return objectMapper.writeValueAsString(Map.of(
                "id", id, "object", "chat.completion.chunk",
                "created", created, "model", "gpt-4o-mini", "choices", List.of(choice)));
    }
}
