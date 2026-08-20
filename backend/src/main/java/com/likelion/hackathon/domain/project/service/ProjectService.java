package com.likelion.hackathon.domain.project.service;

import com.likelion.hackathon.domain.document.repository.SourceConnectionRepository;
import com.likelion.hackathon.domain.document.repository.SourceDocumentRepository;
import com.likelion.hackathon.domain.agenda.repository.AgendaRepository;
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
    private final AgendaRepository agendaRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final SourceConnectionRepository sourceConnectionRepository;

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        User user = getUser(userId);

        Project project = Project.builder().name(request.name()).description(request.description()).build();
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

    /**
     * 프로젝트 삭제. agenda(=프론트의 "회의 준비")가 하나라도 생겼으면 거부한다 — agenda
     * 밑으로 meeting/position/hold_item/transcript 등 테이블이 깊게 얽혀있어서(FK 그래프
     * 참고), 이미 회의 준비/진행이 시작된 프로젝트를 통째로 지우는 cascade는 안전하게
     * 구현하기엔 리스크가 너무 크다. agenda가 없으면 문서/연동/멤버만 지우면 되므로
     * (문서/연동도 agenda가 없으면 자식 레코드가 없는 게 보장됨) 안전하게 지울 수 있다.
     */
    @Transactional
    public void delete(UUID projectId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Project project = getProjectAndVerifyMember(projectId, userId);

        if (!agendaRepository.findAllByProjectOrderByCreatedAtDesc(project).isEmpty()) {
            throw new CustomException(ErrorCode.PROJECT_HAS_AGENDAS);
        }

        // source_documents.connection_id가 source_connections를 참조하므로 문서를 먼저 지운다.
        sourceDocumentRepository.deleteAll(
                sourceDocumentRepository.findAllByProjectOrderByIsCoreContextDescLastModifiedAtDesc(project));
        sourceConnectionRepository.deleteAll(sourceConnectionRepository.findAllByProject(project));
        projectMemberRepository.deleteAll(projectMemberRepository.findAllByProject(project));
        projectRepository.delete(project);
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
