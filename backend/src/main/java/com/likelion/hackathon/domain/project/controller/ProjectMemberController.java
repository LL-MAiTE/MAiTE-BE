package com.likelion.hackathon.domain.project.controller;

import com.likelion.hackathon.domain.project.dto.ProjectMemberResponse;
import com.likelion.hackathon.domain.project.service.ProjectService;
import com.likelion.hackathon.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** 프로젝트 팀원 초대 수락/거절. ProjectController는 /projects/{id}/... 아래에서 프로젝트
 * 기준으로 멤버를 다루고, 여기는 멤버(초대) 자체를 id로 다룬다 — 초대받은 사람은 아직
 * 프로젝트 멤버가 아니라서 /projects/{id}/... 경로로는 접근 권한이 없기 때문. */
@RestController
@RequestMapping("/project-members")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectService projectService;

    @GetMapping("/pending")
    public ApiResponse<List<ProjectMemberResponse>> getMyPendingInvitations() {
        return ApiResponse.ok(projectService.getMyPendingInvitations());
    }

    @PostMapping("/{id}/accept")
    public ApiResponse<ProjectMemberResponse> accept(@PathVariable UUID id) {
        return ApiResponse.ok(projectService.respondToInvitation(id, true));
    }

    @PostMapping("/{id}/decline")
    public ApiResponse<ProjectMemberResponse> decline(@PathVariable UUID id) {
        return ApiResponse.ok(projectService.respondToInvitation(id, false));
    }
}
