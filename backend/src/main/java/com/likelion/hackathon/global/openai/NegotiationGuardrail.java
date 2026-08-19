package com.likelion.hackathon.global.openai;

import com.likelion.hackathon.domain.agenda.entity.Position;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
 */
@Slf4j
@Component
public class NegotiationGuardrail {

    // 날짜: 8/28, 8월 28일
    private static final Pattern DATE_PATTERN =
            Pattern.compile("(\\d{1,2})/(\\d{1,2})|(\\d{1,2})월\\s*(\\d{1,2})일");
    // 금액/수량: 100만원, $1000, 5개, 10명, 20%
    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:만원|달러|원|\\$|USD|개|명|%)");

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

    // "이전", "이후 불가" 등이 없으면 상한선으로 취급. generated > boundary면 위반.
    private boolean exceedsDateBoundary(int generated, int boundary, String source) {
        boolean isLower = source.contains("이전") && !source.contains("이전불가");
        return isLower ? generated < boundary : generated > boundary;
    }

    private boolean exceedsNumberBoundary(double generated, double boundary, String source) {
        boolean isLower = source.contains("이상") && !source.contains("이하");
        return isLower ? generated < boundary : generated > boundary;
    }
}
