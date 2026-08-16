package com.likelion.hackathon.domain.document.dto;

import com.likelion.hackathon.domain.document.entity.enums.ConnectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateConnectionRequest(
        @NotNull ConnectionType type,
        @NotBlank String workspaceOrRepoName,
        @NotBlank String accessToken
) {}
