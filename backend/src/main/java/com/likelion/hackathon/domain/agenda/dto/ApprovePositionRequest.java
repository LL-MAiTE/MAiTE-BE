package com.likelion.hackathon.domain.agenda.dto;

import com.likelion.hackathon.domain.agenda.entity.enums.ApprovalStatus;
import jakarta.validation.constraints.NotNull;

public record ApprovePositionRequest(@NotNull ApprovalStatus approvalStatus) {}
