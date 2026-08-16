package com.likelion.hackathon.domain.hold.dto;

import com.likelion.hackathon.domain.hold.entity.enums.CoordinationResult;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateCoordinationRecordRequest(
        @NotNull UUID positionId,
        String proposedContent,
        @NotNull CoordinationResult result,
        String nextAction
) {}
