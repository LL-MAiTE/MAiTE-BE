package com.likelion.hackathon.domain.meeting.service;

import com.likelion.hackathon.domain.agenda.entity.Agenda;
import com.likelion.hackathon.domain.agenda.entity.AgendaReferenceDocument;
import com.likelion.hackathon.domain.agenda.entity.Position;
import com.likelion.hackathon.domain.agenda.entity.enums.ApprovalStatus;
import com.likelion.hackathon.domain.agenda.repository.AgendaReferenceDocumentRepository;
import com.likelion.hackathon.domain.agenda.repository.AgendaRepository;
import com.likelion.hackathon.domain.agenda.repository.PositionRepository;
import com.likelion.hackathon.domain.confirmation.entity.NumberConfirmation;
import com.likelion.hackathon.domain.confirmation.repository.NumberConfirmationRepository;
import com.likelion.hackathon.domain.hold.entity.HoldItem;
import com.likelion.hackathon.domain.hold.entity.enums.HoldItemOrigin;
import com.likelion.hackathon.domain.hold.repository.HoldItemRepository;
import com.likelion.hackathon.domain.meeting.dto.*;
import com.likelion.hackathon.domain.meeting.entity.*;
import com.likelion.hackathon.domain.meeting.entity.enums.MeetingPositionResultStatus;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingService {

    // 계약/가격/일정/수량 등 "핵심 수치" 감지용. 매 숫자마다가 아니라
    // 통화/퍼센트/날짜/단위가 붙은 값만 걸러서 미팅 흐름 방해를 최소화한다.
    private static final Pattern CRITICAL_NUMBER_PATTERN = Pattern.compile(
            "\\d[\\d,]*\\s*(?:원|달러|USD|\\$|만원|천원|억원|%|개|명|박스|톤|kg|대|건)"
                    + "|\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일"
                    + "|\\d{4}-\\d{2}-\\d{2}"
                    + "|\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?"
    );

    private final MeetingRepository meetingRepository;
    private final MeetingPositionRepository meetingPositionRepository;
    private final TranscriptRepository transcriptRepository;
    private final MeetingLogRepository meetingLogRepository;
    private final AgendaRepository agendaRepository;
    private final PositionRepository positionRepository;
    private final AgendaReferenceDocumentRepository refDocRepository;
    private final HoldItemRepository holdItemRepository;
    private final NotificationRepository notificationRepository;
    private final NumberConfirmationRepository numberConfirmationRepository;
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

        String greeting = promptBuilder.buildGreeting(agenda);

        // Agora Conversational AI 에이전트 시작
        try {
            String agentId = agoraService.startConversationalAI(
                    meetingId.toString(), greeting);
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
                // AgoraService.HUMAN_UID로 고정 — remote_rtc_uids도 이 값 하나만 명시하므로
                // (avatar.enable=true일 때 "*" 전체구독이 Agora에서 거부됨) 사람 참여자가
                // 실제로 이 uid로 join해야 에이전트가 그 사람 음성을 구독할 수 있다.
                rtcToken = AgoraTokenUtil.buildTokenWithUid(
                        agoraProperties.getAppId(), cert, meetingId.toString(), AgoraService.HUMAN_UID, 3600);
            } catch (Exception e) {
                log.warn("Agora token generation failed: {}", e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("disclosureCompletedAt", meeting.getDisclosureCompletedAt());
        result.put("agoraAppId", agoraProperties.getAppId());
        result.put("agoraChannel", meetingId.toString());
        result.put("agoraToken", rtcToken);
        result.put("agoraUid", AgoraService.HUMAN_UID);
        result.put("agoraAgentUid", 9999);
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

        extractAndSaveAgreements(meeting);
    }

    // 회의 전체 대화를 안건 목록과 함께 한 번에 분석해 안건별 합의 결과를 저장
    private void extractAndSaveAgreements(Meeting meeting) {
        List<MeetingPosition> meetingPositions = meetingPositionRepository.findAllByMeeting(meeting);
        if (meetingPositions.isEmpty()) return;

        List<Transcript> transcripts = transcriptRepository.findAllByMeetingOrderBySpokenAt(meeting);
        if (transcripts.isEmpty()) return;

        List<OpenAiService.AgreedOutcome> outcomes;
        try {
            outcomes = openAiService.extractAgreedOutcomes(meetingPositions, transcripts);
        } catch (Exception e) {
            log.error("Failed to extract agreed outcomes for meeting {}: {}", meeting.getId(), e.getMessage());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (OpenAiService.AgreedOutcome outcome : outcomes) {
            if (outcome.topic() == null) continue;
            meetingPositions.stream()
                    .filter(mp -> mp.getPosition().getTopic().equals(outcome.topic()))
                    .findFirst()
                    .ifPresent(mp -> mp.recordResult(parseResultStatus(outcome.status()), outcome.agreedValue(), now));
        }
    }

    private MeetingPositionResultStatus parseResultStatus(String status) {
        if (status == null) return MeetingPositionResultStatus.NOT_DISCUSSED;
        try {
            return MeetingPositionResultStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return MeetingPositionResultStatus.NOT_DISCUSSED;
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
        MeetingPosition matched = findBestMatch(transcript.getText(), meetingPositions);

        MeetingLog log;
        if (matched != null) {
            log = MeetingLog.builder()
                    .meeting(meeting)
                    .transcript(transcript)
                    .matchedMeetingPosition(matched)
                    .translatedText(matched.getPosition().getAnswer())
                    .build();
            log.deliver();
            meetingLogRepository.save(log);
            detectAndFlagCriticalNumber(log, log.getTranslatedText());
            translateCaption(log, meeting.getAgenda());
            return MeetingLogResponse.from(log);
        } else {
            log = MeetingLog.builder()
                    .meeting(meeting)
                    .transcript(transcript)
                    .build();
            log.hold();
            meetingLogRepository.save(log);
            createHoldItem(meeting, log, "매칭된 안건 없음");
            notificationRepository.save(Notification.builder()
                    .user(meeting.getAgenda().getCreatedBy())
                    .type(NotificationType.HOLD_RECEIVED)
                    .referenceId(log.getId())
                    .referenceType("meeting_log")
                    .build());
            return MeetingLogResponse.from(log);
        }
    }

    @Transactional(readOnly = true)
    public List<MeetingPositionResponse> getMeetingPositions(UUID meetingId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = getMeetingAndVerify(meetingId, userId);
        return meetingPositionRepository.findAllByMeeting(meeting)
                .stream().map(MeetingPositionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TranscriptResponse> getTranscripts(UUID meetingId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = getMeetingAndVerify(meetingId, userId);
        return transcriptRepository.findAllByMeetingOrderBySpokenAt(meeting)
                .stream().map(TranscriptResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MeetingLogResponse> getMeetingLogs(UUID meetingId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = getMeetingAndVerify(meetingId, userId);
        return meetingLogRepository.findAllByMeetingOrderByTranscriptSpokenAt(meeting)
                .stream().map(MeetingLogResponse::from).toList();
    }

    /**
     * 실시간 협상 콜(AgoraChatCompletionsController)에서 매 턴마다 직접 호출한다.
     *
     * ⚠️ 예전엔 saveConversationTurn()이 Agora의 message_subscriber 웹훅(/agora/callback)
     * 으로만 대화를 저장했는데, 그 웹훅이 실제로는 존재하지 않는 설정이라 Agora가 단 한 번도
     * 호출한 적이 없었다(로그로 확인) — 즉 실제 라이브 통화의 대화는 어디에도 저장된 적이
     * 없었고, 그 결과 보류함/알림/사후검토가 전부 실데이터를 못 받고 있었다. 여기서는 그
     * 웹훅을 기다리는 대신, 이미 응답을 결정한 controller가 그 자리에서 바로 저장까지 하게
     * 한다 — 매칭된 안건 ID와 보류 여부도 controller가 이미 알고 있으니 그대로 넘겨받아서
     * 재매칭(findBestMatch, 훨씬 부정확한 키워드 매칭) 없이 정확하게 기록한다.
     */
    @Transactional
    public void recordLiveTurn(UUID meetingId, String userText, String aiText,
                                UUID matchedPositionId, boolean isHold, String holdReason) {
        Meeting meeting = meetingRepository.findById(meetingId).orElse(null);
        if (meeting == null) return;

        LocalDateTime now = LocalDateTime.now();

        if (userText != null && !userText.isBlank()) {
            transcriptRepository.save(Transcript.builder()
                    .meeting(meeting)
                    .speakerLabel("USER")
                    .language("ko-KR")
                    .text(userText)
                    .spokenAt(now)
                    .confidence(1.0)
                    .build());
        }

        if (aiText == null || aiText.isBlank()) return;

        Transcript aiTranscript = transcriptRepository.save(Transcript.builder()
                .meeting(meeting)
                .speakerLabel("AI_AGENT")
                .language("ko-KR")
                .text(aiText)
                .spokenAt(now.plusSeconds(1))
                .confidence(1.0)
                .build());

        MeetingLog log;
        if (isHold) {
            log = MeetingLog.builder().meeting(meeting).transcript(aiTranscript).build();
            log.hold();
            meetingLogRepository.save(log);
            createHoldItem(meeting, log, holdReason != null ? holdReason : "매칭된 안건 없음");
            notificationRepository.save(Notification.builder()
                    .user(meeting.getAgenda().getCreatedBy())
                    .type(NotificationType.HOLD_RECEIVED)
                    .referenceId(log.getId())
                    .referenceType("meeting_log")
                    .build());
        } else {
            MeetingPosition matched = matchedPositionId != null
                    ? meetingPositionRepository.findAllByMeeting(meeting).stream()
                            .filter(mp -> mp.getPosition().getId().equals(matchedPositionId))
                            .findFirst().orElse(null)
                    : null;
            log = MeetingLog.builder()
                    .meeting(meeting)
                    .transcript(aiTranscript)
                    .matchedMeetingPosition(matched)
                    .translatedText(aiText)
                    .build();
            log.deliver();
            meetingLogRepository.save(log);
        }
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
            detectAndFlagCriticalNumber(agentLog, agentText);
            translateCaption(agentLog, meeting.getAgenda());
        }
    }

    // 전달된 답변에 계약/가격/일정/수량 등 핵심 수치가 있으면
    // NumberConfirmation을 자동 생성해 확인 팝업 트리거 대상으로 표시한다.
    private void detectAndFlagCriticalNumber(MeetingLog log, String deliveredText) {
        if (deliveredText == null || deliveredText.isBlank()) return;

        Matcher matcher = CRITICAL_NUMBER_PATTERN.matcher(deliveredText);
        if (!matcher.find()) return;

        log.markContainsCriticalNumber();

        numberConfirmationRepository.save(NumberConfirmation.builder()
                .meetingLog(log)
                .detectedValue(matcher.group().trim())
                .popupShownAt(LocalDateTime.now())
                .build());
    }

    // 전달된 답변(원문)을 상대방 언어(agenda.counterpartLanguage)로 번역해
    // 자막 병기용 필드에 채운다. 대상 언어가 없거나 번역 실패 시 조용히 건너뛴다
    // (원문 전달 자체는 이미 끝난 뒤라 실패해도 회의 흐름에 영향 없음).
    private void translateCaption(MeetingLog meetingLog, Agenda agenda) {
        String targetLanguage = agenda.getCounterpartLanguage();
        if (targetLanguage == null || targetLanguage.isBlank()) return;

        try {
            String caption = openAiService.translate(meetingLog.getTranslatedText(), targetLanguage);
            if (caption != null && !caption.isBlank()) {
                meetingLog.updateTranslatedCaption(caption);
            }
        } catch (Exception e) {
            log.warn("Failed to translate caption for meetingLog {}: {}", meetingLog.getId(), e.getMessage());
        }
    }

    private MeetingPosition findBestMatch(String text, List<MeetingPosition> positions) {
        if (positions.isEmpty()) return null;

        // OpenAI 의미 매칭 시도, 실패 시 키워드 매칭 fallback
        UUID matchedId = openAiService.findMatchingPositionId(text, positions);
        if (matchedId != null) {
            UUID finalMatchedId = matchedId;
            return positions.stream()
                    .filter(mp -> mp.getId().equals(finalMatchedId))
                    .findFirst().orElse(null);
        }

        // fallback: 키워드 매칭
        String lowerText = text.toLowerCase();
        for (MeetingPosition mp : positions) {
            String topic = mp.getPosition().getTopic().toLowerCase();
            String question = mp.getPosition().getQuestionText().toLowerCase();
            if (lowerText.contains(topic) || question.contains(lowerText.substring(0, Math.min(10, lowerText.length())))) {
                return mp;
            }
        }
        return null;
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
