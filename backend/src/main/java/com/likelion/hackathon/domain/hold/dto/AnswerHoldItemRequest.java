package com.likelion.hackathon.domain.hold.dto;

import jakarta.validation.constraints.NotBlank;

public record AnswerHoldItemRequest(@NotBlank String answerText) {}
