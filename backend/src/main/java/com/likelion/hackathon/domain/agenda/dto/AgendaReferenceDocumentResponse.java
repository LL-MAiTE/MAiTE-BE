package com.likelion.hackathon.domain.agenda.dto;

import com.likelion.hackathon.domain.agenda.entity.AgendaReferenceDocument;
import com.likelion.hackathon.domain.agenda.entity.enums.AddedBy;

import java.time.LocalDateTime;
import java.util.UUID;

public record AgendaReferenceDocumentResponse(
        UUID id, UUID agendaId, UUID sourceDocumentId,
        String documentTitle, boolean isCoreContext,
        AddedBy addedBy, boolean excluded, LocalDateTime addedAt
) {
    public static AgendaReferenceDocumentResponse from(AgendaReferenceDocument ard) {
        return new AgendaReferenceDocumentResponse(
                ard.getId(), ard.getAgenda().getId(), ard.getSourceDocument().getId(),
                ard.getSourceDocument().getTitle(), ard.getSourceDocument().isCoreContext(),
                ard.getAddedBy(), ard.isExcluded(), ard.getAddedAt()
        );
    }
}
