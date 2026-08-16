package com.likelion.hackathon.domain.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateTranscriptRequest(
        @NotBlank String speakerLabel,
        @NotBlank String language,
        @NotBlank String text,
        @NotNull LocalDateTime spokenAt,
        Double confidence
) {}
