package com.likelion.hackathon.domain.project.service;

import com.likelion.hackathon.domain.project.dto.*;
import com.likelion.hackathon.domain.project.entity.Project;
import com.likelion.hackathon.domain.project.entity.ProjectMember;
import com.likelion.hackathon.domain.project.entity.enums.ProjectMemberRole;
import com.likelion.hackathon.domain.project.repository.ProjectMemberRepository;
import com.likelion.hackathon.domain.project.repository.ProjectRepository;
import com.likelion.hackathon.domain.user.entity.User;
import com.likelion.hackathon.domain.user.repository.UserRepository;
import com.likelion.hackathon.global.exception.CustomException;
import com.likelion.hackathon.global.exception.ErrorCode;
import com.likelion.hackathon.global.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        User user = getUser(userId);

        Project project = Project.builder().name(request.name()).build();
        projectRepository.save(project);

        ProjectMember member = ProjectMember.builder()
                .project(project)
                .user(user)
                .role(ProjectMemberRole.TEAM_MANAGER)
                .build();
        projectMemberRepository.save(member);

        return ProjectResponse.from(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getMyProjects() {
        UUID userId = SecurityUtil.getCurrentUserId();
        return projectRepository.findAllByMemberId(userId).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID projectId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Project project = getProjectAndVerifyMember(projectId, userId);
        return ProjectResponse.from(project);
    }

    @Transactional
    public ProjectMemberResponse inviteMember(UUID projectId, InviteMemberRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Project project = getProjectAndVerifyMember(projectId, userId);
        User invitee = userRepository.findById(request.userId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (projectMemberRepository.existsByProjectAndUser(project, invitee)) {
            throw new CustomException(ErrorCode.ALREADY_PROJECT_MEMBER);
        }

        ProjectMember member = ProjectMember.builder()
                .project(project)
                .user(invitee)
                .role(request.role())
                .build();
        projectMemberRepository.save(member);
        return ProjectMemberResponse.from(member);
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> getMembers(UUID projectId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Project project = getProjectAndVerifyMember(projectId, userId);
        return projectMemberRepository.findAllByProject(project).stream()
                .map(ProjectMemberResponse::from)
                .toList();
    }

    public Project getProjectAndVerifyMember(UUID projectId, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));
        User user = getUser(userId);
        if (!projectMemberRepository.existsByProjectAndUser(project, user)) {
            throw new CustomException(ErrorCode.NOT_PROJECT_MEMBER);
        }
        return project;
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
