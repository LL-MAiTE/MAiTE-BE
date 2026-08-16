package com.likelion.hackathon.domain.meeting.dto;

import com.likelion.hackathon.domain.meeting.entity.MeetingPosition;

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
        LocalDateTime snappedAt
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
                mp.getSnappedAt()
        );
    }
}
