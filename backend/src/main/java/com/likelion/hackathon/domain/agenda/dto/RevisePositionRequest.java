package com.likelion.hackathon.domain.agenda.dto;

import com.likelion.hackathon.domain.agenda.entity.enums.ApprovalStatus;
import jakarta.validation.constraints.NotNull;

public record RevisePositionRequest(
        @NotNull ApprovalStatus approvalStatus,
        String topic,
        String questionText,
        String answer,
        String preference,
        String concessionRange,
        String dealbreaker,
        Integer priority,
        String scheduleConstraint
) {}
