package com.likelion.hackathon.domain.review.dto;

import com.likelion.hackathon.domain.review.entity.ReviewAction;
import com.likelion.hackathon.domain.review.entity.enums.ReviewActionType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewActionResponse(
        UUID id, UUID meetingLogId, UUID reviewerId,
        ReviewActionType action, UUID resultingHoldItemId,
        String note, LocalDateTime createdAt
) {
    public static ReviewActionResponse from(ReviewAction ra) {
        return new ReviewActionResponse(
                ra.getId(), ra.getMeetingLog().getId(), ra.getReviewer().getId(),
                ra.getAction(),
                ra.getResultingHoldItem() != null ? ra.getResultingHoldItem().getId() : null,
                ra.getNote(), ra.getCreatedAt()
        );
    }
}
