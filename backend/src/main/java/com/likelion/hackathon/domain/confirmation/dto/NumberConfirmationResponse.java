package com.likelion.hackathon.domain.confirmation.dto;

import com.likelion.hackathon.domain.confirmation.entity.NumberConfirmation;
import com.likelion.hackathon.domain.confirmation.entity.enums.ConfirmationResponseType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NumberConfirmationResponse(
        UUID id, UUID meetingLogId, String detectedValue,
        LocalDateTime popupShownAt, ConfirmationResponseType responseType,
        LocalDateTime respondedAt, boolean resultedInHold
) {
    public static NumberConfirmationResponse from(NumberConfirmation nc) {
        return new NumberConfirmationResponse(
                nc.getId(), nc.getMeetingLog().getId(), nc.getDetectedValue(),
                nc.getPopupShownAt(), nc.getResponseType(),
                nc.getRespondedAt(), nc.isResultedInHold()
        );
    }
}
