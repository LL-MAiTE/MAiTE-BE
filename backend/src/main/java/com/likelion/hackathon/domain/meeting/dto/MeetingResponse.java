package com.likelion.hackathon.domain.meeting.dto;

import com.likelion.hackathon.domain.meeting.entity.Meeting;
import com.likelion.hackathon.domain.meeting.entity.enums.MeetingStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record MeetingResponse(
        UUID id, UUID agendaId, MeetingStatus status,
        LocalDateTime startedAt, LocalDateTime disclosureCompletedAt,
        LocalDateTime voiceSessionEndedAt, LocalDateTime closedAt
) {
    public static MeetingResponse from(Meeting meeting) {
        return new MeetingResponse(
                meeting.getId(), meeting.getAgenda().getId(), meeting.getStatus(),
                meeting.getStartedAt(), meeting.getDisclosureCompletedAt(),
                meeting.getVoiceSessionEndedAt(), meeting.getClosedAt()
        );
    }
}
