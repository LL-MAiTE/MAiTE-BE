package com.likelion.hackathon.domain.meeting.dto;

import com.likelion.hackathon.domain.meeting.entity.Transcript;

import java.time.LocalDateTime;
import java.util.UUID;

public record TranscriptResponse(
        UUID id, UUID meetingId, String speakerLabel,
        String language, String text, LocalDateTime spokenAt, Double confidence
) {
    public static TranscriptResponse from(Transcript t) {
        return new TranscriptResponse(
                t.getId(), t.getMeeting().getId(), t.getSpeakerLabel(),
                t.getLanguage(), t.getText(), t.getSpokenAt(), t.getConfidence()
        );
    }
}
