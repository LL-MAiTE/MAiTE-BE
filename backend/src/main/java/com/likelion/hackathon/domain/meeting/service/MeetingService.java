package com.likelion.hackathon.domain.meeting.service;

import com.likelion.hackathon.domain.agenda.entity.Agenda;
import com.likelion.hackathon.domain.agenda.entity.AgendaReferenceDocument;
import com.likelion.hackathon.domain.agenda.entity.Position;
import com.likelion.hackathon.domain.agenda.entity.enums.ApprovalStatus;
import com.likelion.hackathon.domain.agenda.repository.AgendaReferenceDocumentRepository;
import com.likelion.hackathon.domain.agenda.repository.AgendaRepository;
import com.likelion.hackathon.domain.agenda.repository.PositionRepository;
import com.likelion.hackathon.domain.hold.entity.HoldItem;
import com.likelion.hackathon.domain.hold.entity.enums.HoldItemOrigin;
import com.likelion.hackathon.domain.hold.repository.HoldItemRepository;
import com.likelion.hackathon.domain.meeting.dto.*;
import com.likelion.hackathon.domain.meeting.entity.*;
import com.likelion.hackathon.domain.meeting.repository.*;
import com.likelion.hackathon.domain.notification.entity.Notification;
import com.likelion.hackathon.domain.notification.entity.enums.NotificationType;
import com.likelion.hackathon.domain.notification.repository.NotificationRepository;
import com.likelion.hackathon.domain.project.service.ProjectService;
import com.likelion.hackathon.global.agora.AgoraProperties;
import com.likelion.hackathon.global.agora.AgoraService;
import com.likelion.hackathon.global.agora.AgoraTokenUtil;
import com.likelion.hackathon.global.agora.MeetingSystemPromptBuilder;
import com.likelion.hackathon.global.openai.OpenAiService;
import com.likelion.hackathon.global.exception.CustomException;
import com.likelion.hackathon.global.exception.ErrorCode;
import com.likelion.hackathon.global.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final MeetingPositionRepository meetingPositionRepository;
    private final TranscriptRepository transcriptRepository;
    private final MeetingLogRepository meetingLogRepository;
    private final AgendaRepository agendaRepository;
    private final PositionRepository positionRepository;
    private final AgendaReferenceDocumentRepository refDocRepository;
    private final HoldItemRepository holdItemRepository;
    private final NotificationRepository notificationRepository;
    private final ProjectService projectService;
    private final AgoraService agoraService;
    private final AgoraProperties agoraProperties;
    private final MeetingSystemPromptBuilder promptBuilder;
    private final OpenAiService openAiService;

    @Transactional
    public MeetingResponse createMeeting(UUID agendaId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Agenda agenda = getAgendaAndVerify(agendaId, userId);

        Meeting meeting = Meeting.builder()
                .agenda(agenda)
                .build();
        meetingRepository.save(meeting);

        List<Position> approvedPositions = positionRepository
                .findAllByAgendaAndIsLatestTrueAndApprovalStatusIn(
                        agenda, List.of(ApprovalStatus.APPROVED, ApprovalStatus.REVISED_APPROVED));
        for (Position pos : approvedPositions) {
            MeetingPosition mp = MeetingPosition.builder()
                    .meeting(meeting)
                    .position(pos)
                    .version(pos.getVersion())
                    .snappedAt(LocalDateTime.now())
                    .build();
            meetingPositionRepository.save(mp);
        }
        return MeetingResponse.from(meeting);
    }

    @Transactional(readOnly = true)
    public MeetingResponse getMeeting(UUID meetingId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = getMeetingAndVerify(meetingId, userId);
        return MeetingResponse.from(meeting);
    }

    @Transactional
    public Map<String, Object> startMeeting(UUID meetingId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = getMeetingAndVerify(meetingId, userId);
        meeting.start();
        meeting.completeDisclosure();

        Agenda agenda = meeting.getAgenda();

        // 승인 안건 + 참조 문서 조회
        List<Position> positions = positionRepository
                .findAllByAgendaAndIsLatestTrueAndApprovalStatusIn(
                        agenda, List.of(ApprovalStatus.APPROVED, ApprovalStatus.REVISED_APPROVED));
        List<AgendaReferenceDocument> refDocs = refDocRepository.findAllByAgendaAndExcludedFalse(agenda);

        // 시스템 프롬프트 생성
        String systemPrompt = promptBuilder.build(agenda, positions, refDocs);
        String greeting = promptBuilder.buildGreeting(agenda);

        // Agora Conversational AI 에이전트 시작
        try {
            String agentId = agoraService.startConversationalAI(
                    meetingId.toString(), systemPrompt, "ko-KR", greeting, meetingId.toString());
            meeting.setAgoraAgentId(agentId);
            log.info("Meeting {} AI agent started: {}", meetingId, agentId);
        } catch (Exception e) {
            log.error("Failed to start Agora AI agent for meeting {}: {}", meetingId, e.getMessage());
            // 에이전트 실패해도 미팅은 진행
        }

        String rtcToken = null;
        String cert = agoraProperties.getAppCertificate();
        if (cert != null && !cert.isBlank()) {
            try {
                rtcToken = AgoraTokenUtil.buildTokenWithUid(
                        agoraProperties.getAppId(), cert, meetingId.toString(), 0, 3600);
            } catch (Exception e) {
                log.warn("Agora token generation failed: {}", e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("disclosureCompletedAt", meeting.getDisclosureCompletedAt());
        result.put("agoraAppId", agoraProperties.getAppId());
        result.put("agoraChannel", meetingId.toString());
        result.put("agoraToken", rtcToken);
        result.put("agoraAgentUid", 100);
        return result;
    }

    @Transactional
    public void endMeeting(UUID meetingId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = getMeetingAndVerify(meetingId, userId);
        meeting.endVoiceSession();

        // AI 에이전트 종료
        if (meeting.getAgoraAgentId() != null) {
            agoraService.stopConversationalAI(meeting.getAgoraAgentId());
        }
    }

    @Transactional(readOnly = true)
    public ChannelInfoResponse getChannelInfo(UUID meetingId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        getMeetingAndVerify(meetingId, userId);
        String token = null;
        String cert = agoraProperties.getAppCertificate();
        if (cert != null && !cert.isBlank()) {
            try {
                token = AgoraTokenUtil.buildTokenWithUid(
                        agoraProperties.getAppId(), cert, meetingId.toString(), 0, 3600);
            } catch (Exception e) {
                log.warn("Agora token generation failed: {}", e.getMessage());
            }
        }
        return new ChannelInfoResponse(agoraProperties.getAppId(), meetingId.toString(), token);
    }

    @Transactional
    public TranscriptResponse createTranscript(UUID meetingId, CreateTranscriptRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = getMeetingAndVerify(meetingId, userId);

        Transcript transcript = Transcript.builder()
                .meeting(meeting)
                .speakerLabel(request.speakerLabel())
                .language(request.language())
                .text(request.text())
                .spokenAt(request.spokenAt())
                .confidence(request.confidence())
                .build();
        transcriptRepository.save(transcript);
        return TranscriptResponse.from(transcript);
    }

    @Transactional
    public MeetingLogResponse createMeetingLog(UUID meetingId, CreateMeetingLogRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = getMeetingAndVerify(meetingId, userId);

        Transcript transcript = transcriptRepository.findById(request.transcriptId())
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSCRIPT_NOT_FOUND));

        List<MeetingPosition> meetingPositions = meetingPositionRepository.findAllByMeeting(meeting);
        OpenAiService.MatchResult matchResult = openAiService.matchIntentOrHold(transcript.getText(), meetingPositions);

        MeetingLog log;
        if (matchResult.matched()) {
            MeetingPosition matched = meetingPositions.stream()
                    .filter(mp -> mp.getPosition().getTopic().equals(matchResult.matchedTopic()))
                    .findFirst().orElse(null);
            log = MeetingLog.builder()
                    .meeting(meeting)
                    .transcript(transcript)
                    .matchedMeetingPosition(matched)
                    .translatedText(matchResult.responseText())
                    .build();
            log.deliver();
        } else {
            log = MeetingLog.builder()
                    .meeting(meeting)
                    .transcript(transcript)
                    .build();
            log.hold();
            meetingLogRepository.save(log);
            createHoldItem(meeting, log, matchResult.holdReason() != null ? matchResult.holdReason() : "매칭된 안건 없음");
            notificationRepository.save(Notification.builder()
                    .user(meeting.getAgenda().getCreatedBy())
                    .type(NotificationType.HOLD_RECEIVED)
                    .referenceId(log.getId())
                    .referenceType("meeting_log")
                    .build());
        }
        meetingLogRepository.save(log);
        return MeetingLogResponse.from(log);
    }

    @Transactional(readOnly = true)
    public List<MeetingPositionResponse> getMeetingPositions(UUID meetingId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = getMeetingAndVerify(meetingId, userId);
        return meetingPositionRepository.findAllByMeeting(meeting)
                .stream().map(MeetingPositionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MeetingLogResponse> getMeetingLogs(UUID meetingId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = getMeetingAndVerify(meetingId, userId);
        return meetingLogRepository.findAllByMeetingOrderByTranscriptSpokenAt(meeting)
                .stream().map(MeetingLogResponse::from).toList();
    }

    // Agora 콜백에서 호출 (인증 없음) — 대화 턴 한 쌍(사람 발화 + AI 응답)을 저장
    @Transactional
    public void saveConversationTurn(String channelName, String userText, String agentText, long startMs) {
        UUID meetingId;
        try {
            meetingId = UUID.fromString(channelName);
        } catch (IllegalArgumentException e) {
            return;
        }

        Meeting meeting = meetingRepository.findById(meetingId).orElse(null);
        if (meeting == null) return;

        LocalDateTime spokenAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(startMs > 0 ? startMs : System.currentTimeMillis()),
                ZoneId.systemDefault());

        // 사람 발화 저장
        if (userText != null && !userText.isBlank()) {
            Transcript userTranscript = Transcript.builder()
                    .meeting(meeting)
                    .speakerLabel("USER")
                    .language("ko-KR")
                    .text(userText)
                    .spokenAt(spokenAt)
                    .confidence(1.0)
                    .build();
            transcriptRepository.save(userTranscript);

            MeetingLog userLog = MeetingLog.builder()
                    .meeting(meeting)
                    .transcript(userTranscript)
                    .build();
            userLog.deliver();
            meetingLogRepository.save(userLog);
        }

        // AI 응답 저장
        if (agentText != null && !agentText.isBlank()) {
            Transcript agentTranscript = Transcript.builder()
                    .meeting(meeting)
                    .speakerLabel("AI_AGENT")
                    .language("ko-KR")
                    .text(agentText)
                    .spokenAt(spokenAt.plusSeconds(1))
                    .confidence(1.0)
                    .build();
            transcriptRepository.save(agentTranscript);

            MeetingLog agentLog = MeetingLog.builder()
                    .meeting(meeting)
                    .transcript(agentTranscript)
                    .translatedText(agentText)
                    .build();
            agentLog.deliver();
            meetingLogRepository.save(agentLog);
        }
    }

    private void createHoldItem(Meeting meeting, MeetingLog log, String reason) {
        HoldItem holdItem = HoldItem.builder()
                .meeting(meeting)
                .meetingLog(log)
                .origin(HoldItemOrigin.DURING_MEETING)
                .reason(reason)
                .build();
        holdItemRepository.save(holdItem);
    }

    private Agenda getAgendaAndVerify(UUID agendaId, UUID userId) {
        Agenda agenda = agendaRepository.findById(agendaId)
                .orElseThrow(() -> new CustomException(ErrorCode.AGENDA_NOT_FOUND));
        projectService.getProjectAndVerifyMember(agenda.getProject().getId(), userId);
        return agenda;
    }

    private Meeting getMeetingAndVerify(UUID meetingId, UUID userId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_NOT_FOUND));
        projectService.getProjectAndVerifyMember(meeting.getAgenda().getProject().getId(), userId);
        return meeting;
    }
}
