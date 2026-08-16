package com.likelion.hackathon.domain.agenda.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePositionRequest(
        @NotBlank String topic,
        @NotBlank String questionText,
        String answer,
        String preference,
        String concessionRange,
        String dealbreaker,
        Integer priority,
        String scheduleConstraint
) {}
