package com.likelion.hackathon.global.openai;

import com.likelion.hackathon.domain.agenda.entity.Position;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM이 자연스럽게 생성한 협상 응답이 승인된 안건의 dealbreaker/양보범위를 실제로
 * 넘는지 결정론적으로(정규식) 재검증하는 안전장치.
 *
 * "지어내지 않기" 원칙의 마지막 방어선이다 — matchIntentOrHold(1단계)가 안건을 골라주고
 * generateNaturalResponse(2단계)가 그 안건 필드만 갖고 자연스럽게 문장을 만들지만,
 * LLM이 프롬프트를 무시하고 실수로 선을 넘는 문장을 만들 가능성 자체는 남는다. 여기서
 * 그 문장에 실제로 언급된 날짜/숫자를 다시 뽑아 dealbreaker와 기계적으로 비교해서,
 * 넘으면 그 문장 자체를 폐기시킨다 — 호출부는 항상 안전한 값(원본 answer 등)으로
 * 대체해야 한다.
 *
 * 다국어 지원: 경계(dealbreaker/concessionRange)는 항상 한국어로 저장되므로 한국어 패턴으로
 * 파싱한다. 생성된 텍스트는 어떤 언어든 올 수 있으므로 영어 날짜·금액 표기도 함께 탐지한다.
 */
@Slf4j
@Component
public class NegotiationGuardrail {

    // 날짜 탐지 — 한국어 + 영어 표기 모두 지원
    // Group 1,2: 8/28 (슬래시 형식)
    // Group 3,4: 8월 28일 (한국어)
    // Group 5,6: March 15 / Mar 15 (영어 월 이름 + 일)
    // Group 7,8: 15 March / 15 Mar (일 + 영어 월 이름)
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(\\d{1,2})/(\\d{1,2})" +
            "|(\\d{1,2})월\\s*(\\d{1,2})일" +
            "|(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?" +
            "|(\\d{1,2})(?:st|nd|rd|th)?\\s+(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\.?",
            Pattern.CASE_INSENSITIVE
    );

    // 금액/수량 탐지 — 한국어 + 영어 단위 모두 지원
    // Group 1: 숫자 + 한국어 단위 (100만원, 5개, 20%)
    // Group 2: 통화 기호 + 숫자 ($1,000  €500  £200)
    // Group 3: 숫자 + K 접미사 (15K → 15,000)
    // Group 4: 숫자 + M/million 접미사 (1.5M → 1,500,000)
    // Group 5: 숫자 + 영어 단위 (15 units, 10 pieces, 5 persons)
    private static final Pattern NUMBER_PATTERN = Pattern.compile(
            "([\\d,]+(?:\\.\\d+)?)\\s*(?:만원|달러|원|USD|EUR|GBP|CNY|JPY|개|명|%)" +
            "|[\\$€£¥]\\s*([\\d,]+(?:\\.\\d+)?)" +
            "|([\\d,]+(?:\\.\\d+)?)\\s*[Kk](?![a-zA-Z])" +
            "|([\\d,]+(?:\\.\\d+)?)\\s*(?:[Mm]illion|[Mm](?![a-zA-Z]))" +
            "|([\\d,]+(?:\\.\\d+)?)\\s*(?:units?|pieces?|persons?|people|items?)(?![a-zA-Z])"
    );

    private static final Map<String, Integer> MONTH_MAP = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3),
            Map.entry("apr", 4), Map.entry("may", 5), Map.entry("jun", 6),
            Map.entry("jul", 7), Map.entry("aug", 8), Map.entry("sep", 9),
            Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12)
    );

    /** 텍스트에 날짜/숫자가 하나라도 있으면 true — 스몰토크 응답이 비즈니스 내용을 흘렸는지 체크용. */
    public boolean containsFigure(String text) {
        if (text == null) return false;
        return DATE_PATTERN.matcher(text).find() || NUMBER_PATTERN.matcher(text).find();
    }

    /**
     * 생성된 텍스트가 이 안건의 dealbreaker(없으면 concessionRange)를 넘는지 검증한다.
     * 넘으면 null(호출부가 안전한 값으로 대체해야 함), 안 넘거나 경계를 파싱할 수 없으면
     * 원문 그대로 반환한다(파싱 불가 = 판단 보류이지 위반 아님 — 과도한 차단 방지).
     */
    public String verify(String generatedText, Position position) {
        if (generatedText == null) return null;
        String boundarySource = position.getDealbreaker() != null
                ? position.getDealbreaker() : position.getConcessionRange();
        if (boundarySource == null) return generatedText;

        OptionalInt boundaryDate = extractFirstDate(boundarySource);
        if (boundaryDate.isPresent()) {
            OptionalInt generatedDate = extractFirstDate(generatedText);
            if (generatedDate.isPresent()
                    && exceedsDateBoundary(generatedDate.getAsInt(), boundaryDate.getAsInt(), boundarySource)) {
                log.warn("Date boundary violation: generated={} boundary={} topic={}",
                        generatedDate.getAsInt(), boundaryDate.getAsInt(), position.getTopic());
                return null;
            }
        }

        OptionalDouble boundaryNum = extractFirstNumber(boundarySource);
        if (boundaryNum.isPresent()) {
            OptionalDouble generatedNum = extractFirstNumber(generatedText);
            if (generatedNum.isPresent()
                    && exceedsNumberBoundary(generatedNum.getAsDouble(), boundaryNum.getAsDouble(), boundarySource)) {
                log.warn("Number boundary violation: generated={} boundary={} topic={}",
                        generatedNum.getAsDouble(), boundaryNum.getAsDouble(), position.getTopic());
                return null;
            }
        }

        return generatedText;
    }

    // 날짜를 MMDD 정수로 변환 (8/28 → 828, March 15 → 315)
    private OptionalInt extractFirstDate(String text) {
        Matcher m = DATE_PATTERN.matcher(text);
        while (m.find()) {
            // MM/DD 슬래시 형식
            if (m.group(1) != null)
                return OptionalInt.of(Integer.parseInt(m.group(1)) * 100 + Integer.parseInt(m.group(2)));
            // 한국어: MM월 DD일
            if (m.group(3) != null)
                return OptionalInt.of(Integer.parseInt(m.group(3)) * 100 + Integer.parseInt(m.group(4)));
            // 영어: Month DD
            if (m.group(5) != null) {
                int month = monthNameToNum(m.group(5));
                if (month > 0) return OptionalInt.of(month * 100 + Integer.parseInt(m.group(6)));
            }
            // 영어: DD Month
            if (m.group(7) != null) {
                int month = monthNameToNum(m.group(8));
                if (month > 0) return OptionalInt.of(month * 100 + Integer.parseInt(m.group(7)));
            }
        }
        return OptionalInt.empty();
    }

    private int monthNameToNum(String name) {
        if (name == null || name.length() < 3) return 0;
        return MONTH_MAP.getOrDefault(name.substring(0, 3).toLowerCase(), 0);
    }

    private OptionalDouble extractFirstNumber(String text) {
        Matcher m = NUMBER_PATTERN.matcher(text);
        if (!m.find()) return OptionalDouble.empty();
        // Group 1: 한국어 단위 / Group 2: 통화 기호 앞 / Group 5: 영어 단위 — 쉼표 제거 후 파싱
        for (int i : new int[]{1, 2, 5}) {
            if (m.group(i) != null)
                return OptionalDouble.of(Double.parseDouble(m.group(i).replace(",", "")));
        }
        // Group 3: K 접미사 → *1,000
        if (m.group(3) != null)
            return OptionalDouble.of(Double.parseDouble(m.group(3).replace(",", "")) * 1_000);
        // Group 4: M/million 접미사 → *1,000,000
        if (m.group(4) != null)
            return OptionalDouble.of(Double.parseDouble(m.group(4).replace(",", "")) * 1_000_000);
        return OptionalDouble.empty();
    }

    // 경계 방향 판단 — 한국어("이전", "이후불가") + 영어("by", "no later than", "at least") 키워드 모두 인식
    private boolean exceedsDateBoundary(int generated, int boundary, String source) {
        String lower = source.toLowerCase();
        // "이전"(deadline) 또는 "by/no later than"(영어 deadline) = generated가 더 늦으면 위반
        boolean isDeadline = (source.contains("이전") && !source.contains("이전불가"))
                || lower.contains("by ") || lower.contains("no later than") || lower.contains("before ");
        return isDeadline ? generated > boundary : generated < boundary;
    }

    private boolean exceedsNumberBoundary(double generated, double boundary, String source) {
        String lower = source.toLowerCase();
        // "이상"(minimum) 또는 "at least/minimum"(영어) = generated가 더 낮으면 위반
        boolean isMinimum = (source.contains("이상") && !source.contains("이하"))
                || lower.contains("at least") || lower.contains("minimum") || lower.contains("no less than");
        return isMinimum ? generated < boundary : generated > boundary;
    }
}
