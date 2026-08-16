package com.likelion.hackathon.domain.confirmation.dto;

import com.likelion.hackathon.domain.confirmation.entity.enums.ConfirmationResponseType;
import jakarta.validation.constraints.NotNull;

public record UpdateNumberConfirmationRequest(@NotNull ConfirmationResponseType responseType) {}
