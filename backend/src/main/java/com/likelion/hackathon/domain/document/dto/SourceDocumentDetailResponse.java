package com.likelion.hackathon.domain.document.dto;

import com.likelion.hackathon.domain.document.entity.SourceDocument;

import java.time.LocalDateTime;
import java.util.UUID;

// 목록 API(SourceDocumentResponse)는 응답을 가볍게 유지하려고 content를 안 주는데,
// 정작 본문 내용을 조회할 방법이 없었어서 만든 단건 조회 전용 응답
public record SourceDocumentDetailResponse(
        UUID id, UUID projectId, UUID connectionId,
        String title, String path, String sourceUrl, String content,
        boolean isCoreContext, LocalDateTime lastModifiedAt, LocalDateTime syncedAt
) {
    public static SourceDocumentDetailResponse from(SourceDocument doc) {
        return new SourceDocumentDetailResponse(
                doc.getId(), doc.getProject().getId(),
                doc.getConnection() != null ? doc.getConnection().getId() : null,
                doc.getTitle(), doc.getPath(), doc.getSourceUrl(), doc.getContent(),
                doc.isCoreContext(), doc.getLastModifiedAt(), doc.getSyncedAt()
        );
    }
}
