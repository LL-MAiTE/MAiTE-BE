package com.likelion.hackathon.domain.confirmation.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateNumberConfirmationRequest(@NotBlank String detectedValue) {}
