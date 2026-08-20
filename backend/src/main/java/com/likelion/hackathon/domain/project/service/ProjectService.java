package com.likelion.hackathon.domain.project.service;

import com.likelion.hackathon.domain.agenda.entity.Agenda;
import com.likelion.hackathon.domain.agenda.entity.Position;
import com.likelion.hackathon.domain.agenda.repository.AgendaReferenceDocumentRepository;
import com.likelion.hackathon.domain.agenda.repository.AgendaRepository;
import com.likelion.hackathon.domain.agenda.repository.PositionRepository;
import com.likelion.hackathon.domain.confirmation.repository.NumberConfirmationRepository;
import com.likelion.hackathon.domain.document.repository.SourceConnectionRepository;
import com.likelion.hackathon.domain.document.repository.SourceDocumentRepository;
import com.likelion.hackathon.domain.hold.repository.CoordinationRecordRepository;
import com.likelion.hackathon.domain.hold.repository.HoldItemRepository;
import com.likelion.hackathon.domain.meeting.entity.Meeting;
import com.likelion.hackathon.domain.meeting.entity.MeetingLog;
import com.likelion.hackathon.domain.meeting.repository.MeetingLogRepository;
import com.likelion.hackathon.domain.meeting.repository.MeetingPositionRepository;
import com.likelion.hackathon.domain.meeting.repository.MeetingRepository;
import com.likelion.hackathon.domain.meeting.repository.TranscriptRepository;
import com.likelion.hackathon.domain.project.dto.*;
import com.likelion.hackathon.domain.project.entity.Project;
import com.likelion.hackathon.domain.project.entity.ProjectMember;
import com.likelion.hackathon.domain.project.entity.enums.ProjectMemberRole;
import com.likelion.hackathon.domain.project.repository.ProjectMemberRepository;
import com.likelion.hackathon.domain.project.repository.ProjectRepository;
import com.likelion.hackathon.domain.review.repository.RequiredReviewRepository;
import com.likelion.hackathon.domain.review.repository.ReviewActionRepository;
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
    private final AgendaReferenceDocumentRepository agendaReferenceDocumentRepository;
    private final PositionRepository positionRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingLogRepository meetingLogRepository;
    private final MeetingPositionRepository meetingPositionRepository;
    private final TranscriptRepository transcriptRepository;
    private final HoldItemRepository holdItemRepository;
    private final CoordinationRecordRepository coordinationRecordRepository;
    private final NumberConfirmationRepository numberConfirmationRepository;
    private final RequiredReviewRepository requiredReviewRepository;
    private final ReviewActionRepository reviewActionRepository;
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
     * 프로젝트를 자식 레코드 전체와 함께 삭제한다.
     *
     * FK 의존 순서(자식 → 부모):
     *   hold_items → (meeting, meeting_log, number_confirmation, transcript)
     *   number_confirmations, required_reviews, review_actions → meeting_logs
     *   meeting_logs → (meeting, transcript, meeting_positions)
     *   meeting_positions → (meeting, positions)
     *   transcripts, coordination_records → meeting
     *   meetings → agenda
     *   agenda_reference_documents, positions(전 버전) → agenda
     *   source_documents → (project, source_connections)
     *   source_connections, project_members → project
     */
    @Transactional
    public void delete(UUID projectId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Project project = getProjectAndVerifyMember(projectId, userId);

        List<Agenda> agendas = agendaRepository.findAllByProjectOrderByCreatedAtDesc(project);

        for (Agenda agenda : agendas) {
            List<Meeting> meetings = meetingRepository.findAllByAgenda(agenda);

            for (Meeting meeting : meetings) {
                // 1. hold_items — meeting_log, number_confirmation, transcript를 FK로 참조하므로 먼저 삭제
                holdItemRepository.deleteAll(holdItemRepository.findAllByMeeting(meeting));

                // 2. meeting_log 종속 레코드 (number_confirmations, required_reviews, review_actions)
                List<MeetingLog> meetingLogs = meetingLogRepository.findAllByMeetingOrderByTranscriptSpokenAt(meeting);
                for (MeetingLog log : meetingLogs) {
                    numberConfirmationRepository.findByMeetingLog(log).ifPresent(numberConfirmationRepository::delete);
                    requiredReviewRepository.deleteAll(requiredReviewRepository.findAllByMeetingLog(log));
                    reviewActionRepository.deleteAll(reviewActionRepository.findAllByMeetingLog(log));
                }

                // 3. meeting_logs — transcript, meeting_positions를 FK로 참조하므로 먼저 삭제
                meetingLogRepository.deleteAll(meetingLogs);

                // 4. meeting_positions, transcripts, coordination_records
                meetingPositionRepository.deleteAll(meetingPositionRepository.findAllByMeeting(meeting));
                transcriptRepository.deleteAll(transcriptRepository.findAllByMeetingOrderBySpokenAt(meeting));
                coordinationRecordRepository.deleteAll(coordinationRecordRepository.findAllByMeeting(meeting));
            }

            meetingRepository.deleteAll(meetings);

            // agenda 종속 레코드
            agendaReferenceDocumentRepository.deleteAll(agendaReferenceDocumentRepository.findAllByAgenda(agenda));
            positionRepository.deleteAll(positionRepository.findAllByAgenda(agenda));
        }

        agendaRepository.deleteAll(agendas);

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
