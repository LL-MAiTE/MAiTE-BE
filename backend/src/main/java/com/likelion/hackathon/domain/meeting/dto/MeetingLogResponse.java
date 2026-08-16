package com.likelion.hackathon.domain.meeting.dto;

import com.likelion.hackathon.domain.meeting.entity.MeetingLog;
import com.likelion.hackathon.domain.meeting.entity.enums.MeetingLogStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record MeetingLogResponse(
        UUID id, UUID meetingId, UUID transcriptId,
        UUID matchedMeetingPositionId, String translatedText,
        boolean containsCriticalNumber, String limitationNote,
        LocalDateTime deliveredAt, MeetingLogStatus status
) {
    public static MeetingLogResponse from(MeetingLog log) {
        return new MeetingLogResponse(
                log.getId(), log.getMeeting().getId(), log.getTranscript().getId(),
                log.getMatchedMeetingPosition() != null ? log.getMatchedMeetingPosition().getId() : null,
                log.getTranslatedText(), log.isContainsCriticalNumber(),
                log.getLimitationNote(), log.getDeliveredAt(), log.getStatus()
        );
    }
}
