package com.likelion.hackathon.domain.hold.dto;

import com.likelion.hackathon.domain.hold.entity.HoldItem;
import com.likelion.hackathon.domain.hold.entity.enums.HoldItemOrigin;
import com.likelion.hackathon.domain.hold.entity.enums.HoldItemStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record HoldItemResponse(
        UUID id, UUID meetingId, UUID meetingLogId,
        HoldItemOrigin origin, String reason,
        String answerText, UUID answeredBy, LocalDateTime answeredAt,
        LocalDateTime deliveredToCounterpartAt, int reopenCount,
        HoldItemStatus status, LocalDateTime resolvedAt, LocalDateTime createdAt
) {
    public static HoldItemResponse from(HoldItem item) {
        return new HoldItemResponse(
                item.getId(), item.getMeeting().getId(),
                item.getMeetingLog() != null ? item.getMeetingLog().getId() : null,
                item.getOrigin(), item.getReason(),
                item.getAnswerText(),
                item.getAnsweredBy() != null ? item.getAnsweredBy().getId() : null,
                item.getAnsweredAt(), item.getDeliveredToCounterpartAt(),
                item.getReopenCount(), item.getStatus(), item.getResolvedAt(),
                item.getCreatedAt()
        );
    }
}
