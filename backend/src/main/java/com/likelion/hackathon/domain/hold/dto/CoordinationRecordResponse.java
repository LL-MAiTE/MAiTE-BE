package com.likelion.hackathon.domain.hold.dto;

import com.likelion.hackathon.domain.hold.entity.CoordinationRecord;
import com.likelion.hackathon.domain.hold.entity.enums.CoordinationResult;

import java.util.UUID;

public record CoordinationRecordResponse(
        UUID id, UUID meetingId, UUID positionId,
        String proposedContent, CoordinationResult result,
        String nextAction, UUID resultingHoldItemId
) {
    public static CoordinationRecordResponse from(CoordinationRecord cr) {
        return new CoordinationRecordResponse(
                cr.getId(), cr.getMeeting().getId(), cr.getPosition().getId(),
                cr.getProposedContent(), cr.getResult(), cr.getNextAction(),
                cr.getResultingHoldItem() != null ? cr.getResultingHoldItem().getId() : null
        );
    }
}
