package com.likelion.hackathon.domain.meeting.dto;

import com.likelion.hackathon.domain.meeting.entity.MeetingPosition;
import com.likelion.hackathon.domain.meeting.entity.enums.MeetingPositionResultStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record MeetingPositionResponse(
        UUID id,
        UUID positionId,
        String topic,
        String questionText,
        String answer,
        String preference,
        String concessionRange,
        String dealbreaker,
        Integer priority,
        String scheduleConstraint,
        int snappedVersion,
        LocalDateTime snappedAt,
        // 회의 종료 시 대화 분석으로 채워짐 (그 전엔 NOT_DISCUSSED / null)
        MeetingPositionResultStatus resultStatus,
        String agreedValue,
        LocalDateTime resolvedAt
) {
    public static MeetingPositionResponse from(MeetingPosition mp) {
        var p = mp.getPosition();
        return new MeetingPositionResponse(
                mp.getId(),
                p.getId(),
                p.getTopic(),
                p.getQuestionText(),
                p.getAnswer(),
                p.getPreference(),
                p.getConcessionRange(),
                p.getDealbreaker(),
                p.getPriority(),
                p.getScheduleConstraint(),
                mp.getVersion(),
                mp.getSnappedAt(),
                mp.getResultStatus(),
                mp.getAgreedValue(),
                mp.getResolvedAt()
        );
    }
}
