package com.likelion.hackathon.domain.review.dto;

import com.likelion.hackathon.domain.review.entity.enums.ReviewActionType;
import jakarta.validation.constraints.NotNull;

public record CreateReviewActionRequest(
        @NotNull ReviewActionType action,
        String note
) {}
