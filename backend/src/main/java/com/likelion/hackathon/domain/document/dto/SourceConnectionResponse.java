package com.likelion.hackathon.domain.document.dto;

import com.likelion.hackathon.domain.document.entity.SourceConnection;
import com.likelion.hackathon.domain.document.entity.enums.ConnectionType;

import java.time.LocalDateTime;
import java.util.UUID;

public record SourceConnectionResponse(
        UUID id, ConnectionType type, String workspaceOrRepoName,
        UUID connectedBy, LocalDateTime connectedAt
) {
    public static SourceConnectionResponse from(SourceConnection sc) {
        return new SourceConnectionResponse(
                sc.getId(), sc.getType(), sc.getWorkspaceOrRepoName(),
                sc.getConnectedBy().getId(), sc.getConnectedAt()
        );
    }
}
