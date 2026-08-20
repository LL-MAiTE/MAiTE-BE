package com.likelion.hackathon.domain.project.controller;

import com.likelion.hackathon.domain.project.dto.*;
import com.likelion.hackathon.domain.project.service.ProjectService;
import com.likelion.hackathon.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectResponse> create(@RequestBody @Valid CreateProjectRequest request) {
        return ApiResponse.ok(projectService.create(request));
    }

    @GetMapping
    public ApiResponse<List<ProjectResponse>> getMyProjects() {
        return ApiResponse.ok(projectService.getMyProjects());
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectResponse> getProject(@PathVariable UUID id) {
        return ApiResponse.ok(projectService.getProject(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProject(@PathVariable UUID id) {
        projectService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectMemberResponse> inviteMember(
            @PathVariable UUID id,
            @RequestBody @Valid InviteMemberRequest request) {
        return ApiResponse.ok(projectService.inviteMember(id, request));
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<ProjectMemberResponse>> getMembers(@PathVariable UUID id) {
        return ApiResponse.ok(projectService.getMembers(id));
    }
}
