package com.likelion.hackathon.domain.document.dto;

import com.likelion.hackathon.domain.document.entity.SourceDocument;

import java.time.LocalDateTime;
import java.util.UUID;

public record SourceDocumentResponse(
        UUID id, UUID projectId, UUID connectionId,
        String title, String path, String sourceUrl,
        boolean isCoreContext, LocalDateTime lastModifiedAt, LocalDateTime syncedAt
) {
    public static SourceDocumentResponse from(SourceDocument doc) {
        return new SourceDocumentResponse(
                doc.getId(), doc.getProject().getId(),
                doc.getConnection() != null ? doc.getConnection().getId() : null,
                doc.getTitle(), doc.getPath(), doc.getSourceUrl(),
                doc.isCoreContext(), doc.getLastModifiedAt(), doc.getSyncedAt()
        );
    }
}
