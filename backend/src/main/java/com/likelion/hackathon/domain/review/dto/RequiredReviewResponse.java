package com.likelion.hackathon.domain.review.dto;

import com.likelion.hackathon.domain.review.entity.RequiredReview;
import com.likelion.hackathon.domain.review.entity.enums.RequiredReviewStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record RequiredReviewResponse(
        UUID id, UUID meetingLogId, UUID designatedBy,
        LocalDateTime designatedAt, RequiredReviewStatus status,
        UUID reviewedBy, LocalDateTime reviewedAt
) {
    public static RequiredReviewResponse from(RequiredReview rr) {
        return new RequiredReviewResponse(
                rr.getId(), rr.getMeetingLog().getId(), rr.getDesignatedBy().getId(),
                rr.getDesignatedAt(), rr.getStatus(),
                rr.getReviewedBy() != null ? rr.getReviewedBy().getId() : null,
                rr.getReviewedAt()
        );
    }
}
