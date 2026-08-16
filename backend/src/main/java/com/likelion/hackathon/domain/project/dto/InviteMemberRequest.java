package com.likelion.hackathon.domain.project.dto;

import com.likelion.hackathon.domain.project.entity.enums.ProjectMemberRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InviteMemberRequest(
        @NotNull UUID userId,
        @NotNull ProjectMemberRole role
) {}
