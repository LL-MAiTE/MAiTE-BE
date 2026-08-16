package com.likelion.hackathon.domain.project.dto;

import com.likelion.hackathon.domain.project.entity.Project;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectResponse(UUID id, String name, LocalDateTime createdAt) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getCreatedAt());
    }
}
