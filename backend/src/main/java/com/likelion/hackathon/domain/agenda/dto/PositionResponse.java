package com.likelion.hackathon.domain.agenda.dto;

import com.likelion.hackathon.domain.agenda.entity.Position;
import com.likelion.hackathon.domain.agenda.entity.enums.ApprovalStatus;
import com.likelion.hackathon.domain.agenda.entity.enums.ConfidenceLevel;
import com.likelion.hackathon.domain.agenda.entity.enums.GeneratedBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PositionResponse(
        UUID id, UUID agendaId, String topic, String questionText,
        GeneratedBy generatedBy, UUID sourceDocumentId,
        List<String> activeFields, String answer,
        String preference, String concessionRange, String dealbreaker,
        Integer priority, String scheduleConstraint,
        ConfidenceLevel confidenceLevel, ApprovalStatus approvalStatus,
        int version, boolean isLatest, UUID supersedesId,
        UUID approvedBy, LocalDateTime approvedAt
) {
    public static PositionResponse from(Position p) {
        return new PositionResponse(
                p.getId(), p.getAgenda().getId(), p.getTopic(), p.getQuestionText(),
                p.getGeneratedBy(),
                p.getSourceDocument() != null ? p.getSourceDocument().getId() : null,
                p.getActiveFields(), p.getAnswer(),
                p.getPreference(), p.getConcessionRange(), p.getDealbreaker(),
                p.getPriority(), p.getScheduleConstraint(),
                p.getConfidenceLevel(), p.getApprovalStatus(),
                p.getVersion(), p.isLatest(),
                p.getSupersedes() != null ? p.getSupersedes().getId() : null,
                p.getApprovedBy() != null ? p.getApprovedBy().getId() : null,
                p.getApprovedAt()
        );
    }
}
