package com.likelion.hackathon.domain.project.dto;

import com.likelion.hackathon.domain.project.entity.ProjectMember;
import com.likelion.hackathon.domain.project.entity.enums.ProjectMemberRole;

import java.util.UUID;

public record ProjectMemberResponse(UUID id, UUID userId, String userName, String userEmail, ProjectMemberRole role) {
    public static ProjectMemberResponse from(ProjectMember pm) {
        return new ProjectMemberResponse(
                pm.getId(),
                pm.getUser().getId(),
                pm.getUser().getName(),
                pm.getUser().getEmail(),
                pm.getRole()
        );
    }
}
