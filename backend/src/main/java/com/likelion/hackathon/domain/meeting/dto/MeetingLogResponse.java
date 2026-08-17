package com.likelion.hackathon.domain.meeting.dto;

import com.likelion.hackathon.domain.meeting.entity.MeetingLog;
import com.likelion.hackathon.domain.meeting.entity.enums.MeetingLogStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record MeetingLogResponse(
        UUID id, UUID meetingId, UUID transcriptId,
        UUID matchedMeetingPositionId, String translatedText,
        // 상대방 언어(agenda.counterpartLanguage)로 번역된 자막. 대상 언어가
        // 없거나 번역 실패 시 null (원문(translatedText)만 표시하면 됨)
        String translatedCaption,
        boolean containsCriticalNumber, String limitationNote,
        LocalDateTime deliveredAt, MeetingLogStatus status
) {
    public static MeetingLogResponse from(MeetingLog log) {
        return new MeetingLogResponse(
                log.getId(), log.getMeeting().getId(), log.getTranscript().getId(),
                log.getMatchedMeetingPosition() != null ? log.getMatchedMeetingPosition().getId() : null,
                log.getTranslatedText(), log.getTranslatedCaption(), log.isContainsCriticalNumber(),
                log.getLimitationNote(), log.getDeliveredAt(), log.getStatus()
        );
    }
}
