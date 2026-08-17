package com.likelion.hackathon.global.agora;

import com.likelion.hackathon.domain.agenda.entity.Agenda;
import com.likelion.hackathon.domain.agenda.entity.AgendaReferenceDocument;
import com.likelion.hackathon.domain.agenda.entity.Position;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MeetingSystemPromptBuilder {

    public String build(Agenda agenda, List<Position> positions, List<AgendaReferenceDocument> refDocs) {
        StringBuilder sb = new StringBuilder();

        sb.append("당신은 협상 회의에서 우리 측을 대리하는 AI 협상 대리인입니다.\n");
        sb.append("실제 사람처럼 자연스럽게 대화하며, 아래 협상 안건과 문서를 기반으로 응답하세요.\n\n");

        sb.append("=== 회의 기본 정보 ===\n");
        sb.append("회의 제목: ").append(agenda.getTitle()).append("\n");
        if (agenda.getPurpose() != null) {
            sb.append("회의 목적: ").append(agenda.getPurpose()).append("\n");
        }
        if (agenda.getCounterpartCountry() != null) {
            sb.append("상대방 국가: ").append(agenda.getCounterpartCountry()).append("\n");
        }
        if (agenda.getCounterpartLanguage() != null) {
            sb.append("상대방 언어: ").append(agenda.getCounterpartLanguage()).append("\n");
            sb.append("필요시 ").append(agenda.getCounterpartLanguage()).append("로도 소통하세요.\n");
        }
        sb.append("\n");

        if (!positions.isEmpty()) {
            sb.append("=== 승인된 협상 안건 ===\n");
            sb.append("다음 안건들을 기반으로 협상하세요. 양보 불가 사항은 절대 양보하지 마세요.\n\n");

            for (int i = 0; i < positions.size(); i++) {
                Position p = positions.get(i);
                sb.append(i + 1).append(". 주제: ").append(p.getTopic()).append("\n");
                sb.append("   질문/사안: ").append(p.getQuestionText()).append("\n");
                if (p.getAnswer() != null) {
                    sb.append("   우리 입장: ").append(p.getAnswer()).append("\n");
                }
                if (p.getPreference() != null) {
                    sb.append("   선호안: ").append(p.getPreference()).append("\n");
                }
                if (p.getConcessionRange() != null) {
                    sb.append("   양보 가능 범위: ").append(p.getConcessionRange()).append("\n");
                }
                if (p.getDealbreaker() != null) {
                    sb.append("   양보 불가(Dealbreaker): ").append(p.getDealbreaker()).append("\n");
                }
                if (p.getPriority() != null) {
                    sb.append("   우선순위: ").append(p.getPriority()).append("/5\n");
                }
                sb.append("\n");
            }
        }

        if (!refDocs.isEmpty()) {
            sb.append("=== 참고 문서 ===\n");
            for (AgendaReferenceDocument ref : refDocs) {
                String title = ref.getSourceDocument().getTitle();
                String content = ref.getSourceDocument().getContent();
                sb.append("--- ").append(title).append(" ---\n");
                if (content != null && !content.isBlank()) {
                    // 문서가 너무 길면 앞 2000자만 포함 (토큰 절약)
                    String trimmed = content.length() > 2000 ? content.substring(0, 2000) + "..." : content;
                    sb.append(trimmed).append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("=== 행동 지침 ===\n");
        sb.append("- 실제 협상가처럼 자연스럽게 대화하세요. 딱딱하게 읽어내리지 말고, 상황에 맞는 말투로 응답하세요.\n\n");

        sb.append("[1차 필터 — 범위 확인]\n");
        sb.append("- 상대방의 질문이 위 안건 목록과 관련 없거나 판단하기 어려운 경우,\n");
        sb.append("  '해당 사항은 내부 확인 후 답변드리겠습니다'라고 하고 즉답을 피하세요.\n");
        sb.append("- 안건과 관련된 질문이라면 아래 협상 원칙에 따라 응답하세요.\n\n");

        sb.append("[협상 원칙 — 범위 내 질문일 때]\n");
        sb.append("- 항상 '선호안(preference)'을 먼저 제시하세요. 양보 범위를 처음부터 드러내지 마세요.\n");
        sb.append("- 상대방이 선호안을 거부하거나 압박할 때만 단계적으로 양보하세요.\n");
        sb.append("- '양보 가능 범위(concessionRange)'는 협상 중 마지노선이며, 처음부터 제시하지 마세요.\n");
        sb.append("- '양보 불가(dealbreaker)'는 어떤 상황에서도 절대 넘지 마세요.\n");
        sb.append("  상대가 강하게 요구하더라도 '그 부분은 저희 내부적으로 조율이 불가한 사항입니다' 등으로 정중하지만 단호하게 거절하세요.\n");
        sb.append("- 숫자나 날짜 등 구체적인 조건을 제시할 때는 명확하게 말하세요.\n");

        return sb.toString();
    }

    public String buildGreeting(Agenda agenda) {
        String lang = agenda.getCounterpartLanguage();
        if (lang != null && lang.toLowerCase().contains("en")) {
            return "Hello. I'm the AI negotiation representative for " + agenda.getTitle()
                    + ". Shall we begin the meeting?";
        }
        return "안녕하세요. '" + agenda.getTitle() + "' 회의를 위한 AI 협상 대리인입니다. 회의를 시작하겠습니다.";
    }
}
