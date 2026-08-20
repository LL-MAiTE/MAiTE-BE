package com.likelion.hackathon.domain.agenda.service;

import com.likelion.hackathon.domain.agenda.dto.*;
import com.likelion.hackathon.domain.agenda.entity.Agenda;
import com.likelion.hackathon.domain.agenda.entity.AgendaReferenceDocument;
import com.likelion.hackathon.domain.agenda.entity.Position;
import com.likelion.hackathon.domain.agenda.entity.enums.*;
import com.likelion.hackathon.domain.agenda.repository.AgendaReferenceDocumentRepository;
import com.likelion.hackathon.domain.agenda.repository.AgendaRepository;
import com.likelion.hackathon.domain.agenda.repository.PositionRepository;
import com.likelion.hackathon.domain.document.entity.SourceDocument;
import com.likelion.hackathon.domain.document.repository.SourceDocumentRepository;
import com.likelion.hackathon.domain.project.entity.Project;
import com.likelion.hackathon.domain.project.service.ProjectService;
import com.likelion.hackathon.domain.user.entity.User;
import com.likelion.hackathon.domain.user.repository.UserRepository;
import com.likelion.hackathon.global.exception.CustomException;
import com.likelion.hackathon.global.exception.ErrorCode;
import com.likelion.hackathon.global.openai.OpenAiService;
import com.likelion.hackathon.global.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgendaService {

    private final AgendaRepository agendaRepository;
    private final AgendaReferenceDocumentRepository refDocRepository;
    private final PositionRepository positionRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final ProjectService projectService;
    private final UserRepository userRepository;
    private final OpenAiService openAiService;

    @Transactional(readOnly = true)
    public List<AgendaResponse> getAgendas(UUID projectId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Project project = projectService.getProjectAndVerifyMember(projectId, userId);
        return agendaRepository.findAllByProjectOrderByCreatedAtDesc(project)
                .stream().map(AgendaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AgendaResponse getAgenda(UUID agendaId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Agenda agenda = getAgendaAndVerify(agendaId, userId);
        return AgendaResponse.from(agenda);
    }

    @Transactional
    public AgendaResponse createAgenda(CreateAgendaRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Project project = projectService.getProjectAndVerifyMember(request.projectId(), userId);
        User user = getUser(userId);

        Agenda agenda = Agenda.builder()
                .project(project)
                .title(request.title())
                .purpose(request.purpose())
                .counterpartCountry(request.counterpartCountry())
                .counterpartLanguage(request.counterpartLanguage())
                .transcriptLanguages(request.transcriptLanguages())
                .translationSourceLanguages(request.translationSourceLanguages())
                .translationTargetLanguages(request.translationTargetLanguages())
                .createdBy(user)
                .build();
        agendaRepository.save(agenda);
        return AgendaResponse.from(agenda);
    }

    @Transactional
    public List<AgendaReferenceDocumentResponse> selectReferenceDocuments(UUID agendaId, SelectDocumentsRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Agenda agenda = getAgendaAndVerify(agendaId, userId);

        List<AgendaReferenceDocument> saved = new ArrayList<>();
        for (UUID docId : request.sourceDocumentIds()) {
            SourceDocument doc = sourceDocumentRepository.findById(docId)
                    .orElseThrow(() -> new CustomException(ErrorCode.SOURCE_DOCUMENT_NOT_FOUND));
            if (!doc.getProject().getId().equals(agenda.getProject().getId())) {
                throw new CustomException(ErrorCode.DOCUMENT_NOT_IN_PROJECT);
            }
            // 이미 연결된 경우 제외 처리 해제
            AgendaReferenceDocument ref = refDocRepository
                    .findByAgendaAndSourceDocumentId(agenda, docId)
                    .orElseGet(() -> AgendaReferenceDocument.builder()
                            .agenda(agenda)
                            .sourceDocument(doc)
                            .addedBy(AddedBy.USER)
                            .addedAt(LocalDateTime.now())
                            .build());
            refDocRepository.save(ref);
            saved.add(ref);
        }
        return saved.stream().map(AgendaReferenceDocumentResponse::from).toList();
    }

    /** 이 안건에 등록된 참조 문서 전체(제외된 것 포함)를 가져온다. 회의 준비 화면을 새로
     * 열었을 때 "참고 문서" 체크 상태를 정확히 복원하는 데 쓴다 — 지금까지는 문서 선택
     * 직후 응답으로만 알 수 있었고, 다시 조회하는 길이 없었다. */
    @Transactional(readOnly = true)
    public List<AgendaReferenceDocumentResponse> getReferenceDocuments(UUID agendaId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Agenda agenda = getAgendaAndVerify(agendaId, userId);
        return refDocRepository.findAllByAgenda(agenda).stream()
                .map(AgendaReferenceDocumentResponse::from).toList();
    }

    @Transactional
    public AgendaReferenceDocumentResponse updateReferenceDocument(UUID refDocId, UpdateReferenceDocumentRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        AgendaReferenceDocument ref = refDocRepository.findById(refDocId)
                .orElseThrow(() -> new CustomException(ErrorCode.AGENDA_REFERENCE_DOCUMENT_NOT_FOUND));
        projectService.getProjectAndVerifyMember(ref.getAgenda().getProject().getId(), userId);
        // 예전엔 excluded=true로 켜는 것만 지원하고 다시 false로 되돌리는 길이 없었다
        // (실제로는 문서 선택 체크박스를 다시 켰을 때 반영이 안 되는 버그였음).
        if (request.excluded()) {
            ref.exclude();
        } else {
            ref.include();
        }
        return AgendaReferenceDocumentResponse.from(ref);
    }

    @Transactional
    public List<PositionResponse> generateDraftPositions(UUID agendaId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Agenda agenda = getAgendaAndVerify(agendaId, userId);

        List<AgendaReferenceDocument> refDocs = refDocRepository.findAllByAgendaAndExcludedFalse(agenda);
        if (refDocs.isEmpty()) {
            throw new CustomException(ErrorCode.NO_REFERENCE_DOCUMENTS);
        }

        // OpenAI로 안건 초안 생성, 실패 시 mock fallback
        List<OpenAiService.DraftPosition> aiDrafts = openAiService.generateDraftPositions(agenda, refDocs);
        List<Position> drafts = aiDrafts.isEmpty()
                ? generateMockPositions(agenda, refDocs)
                : aiDrafts.stream().map(d -> toPosition(agenda, refDocs, d)).toList();

        positionRepository.saveAll(drafts);
        agenda.updateStatus(AgendaStatus.PREPARING);

        return drafts.stream().map(PositionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> getPositions(UUID agendaId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Agenda agenda = getAgendaAndVerify(agendaId, userId);
        return positionRepository.findAllByAgendaAndIsLatestTrue(agenda)
                .stream().map(PositionResponse::from).toList();
    }

    @Transactional
    public PositionResponse createPosition(UUID agendaId, CreatePositionRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Agenda agenda = getAgendaAndVerify(agendaId, userId);

        Position position = Position.builder()
                .agenda(agenda)
                .topic(request.topic())
                .questionText(request.questionText())
                .generatedBy(GeneratedBy.USER)
                .answer(request.answer())
                .preference(request.preference())
                .concessionRange(request.concessionRange())
                .dealbreaker(request.dealbreaker())
                .priority(request.priority())
                .scheduleConstraint(request.scheduleConstraint())
                .activeFields(buildActiveFields(request))
                .build();
        positionRepository.save(position);
        return PositionResponse.from(position);
    }

    @Transactional
    public PositionResponse approvePosition(UUID positionId, ApprovePositionRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Position position = getPositionAndVerify(positionId, userId);
        User user = getUser(userId);
        position.approve(user, request.approvalStatus());
        checkAndUpdateAgendaStatus(position.getAgenda());
        return PositionResponse.from(position);
    }

    @Transactional
    public PositionResponse revisePosition(UUID positionId, RevisePositionRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Position old = getPositionAndVerify(positionId, userId);
        User user = getUser(userId);
        old.markNotLatest();

        Position revised = Position.builder()
                .agenda(old.getAgenda())
                .topic(request.topic() != null ? request.topic() : old.getTopic())
                .questionText(request.questionText() != null ? request.questionText() : old.getQuestionText())
                .generatedBy(old.getGeneratedBy())
                .sourceDocument(old.getSourceDocument())
                .activeFields(old.getActiveFields())
                .answer(request.answer() != null ? request.answer() : old.getAnswer())
                .preference(request.preference() != null ? request.preference() : old.getPreference())
                .concessionRange(request.concessionRange() != null ? request.concessionRange() : old.getConcessionRange())
                .dealbreaker(request.dealbreaker() != null ? request.dealbreaker() : old.getDealbreaker())
                .priority(request.priority() != null ? request.priority() : old.getPriority())
                .scheduleConstraint(request.scheduleConstraint() != null ? request.scheduleConstraint() : old.getScheduleConstraint())
                .confidenceLevel(old.getConfidenceLevel())
                .version(old.getVersion() + 1)
                .supersedes(old)
                .build();
        revised.approve(user, request.approvalStatus());
        positionRepository.save(revised);
        checkAndUpdateAgendaStatus(revised.getAgenda());
        return PositionResponse.from(revised);
    }

    @Transactional
    public PositionResponse rejectPosition(UUID positionId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Position position = getPositionAndVerify(positionId, userId);
        position.reject();
        return PositionResponse.from(position);
    }

    @Transactional
    public void deletePosition(UUID positionId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Position position = getPositionAndVerify(positionId, userId);
        position.reject(); // REJECTED = 삭제됨 (매칭 대상에서 제외)
        position.markNotLatest();
    }

    private Agenda getAgendaAndVerify(UUID agendaId, UUID userId) {
        Agenda agenda = agendaRepository.findById(agendaId)
                .orElseThrow(() -> new CustomException(ErrorCode.AGENDA_NOT_FOUND));
        projectService.getProjectAndVerifyMember(agenda.getProject().getId(), userId);
        return agenda;
    }

    private Position getPositionAndVerify(UUID positionId, UUID userId) {
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new CustomException(ErrorCode.POSITION_NOT_FOUND));
        projectService.getProjectAndVerifyMember(position.getAgenda().getProject().getId(), userId);
        return position;
    }

    private void checkAndUpdateAgendaStatus(Agenda agenda) {
        List<Position> latest = positionRepository.findAllByAgendaAndIsLatestTrue(agenda);
        boolean anyApproved = latest.stream()
                .anyMatch(p -> p.getApprovalStatus() == ApprovalStatus.APPROVED
                        || p.getApprovalStatus() == ApprovalStatus.REVISED_APPROVED);
        if (anyApproved) {
            agenda.updateStatus(AgendaStatus.APPROVED);
        }
    }

    // ⚠️ 예전엔 어떤 문서가 실제 근거인지 안 물어보고 항상 refDocs.get(0)으로 고정했었다
    // (실제 버그 — 여러 문서를 골라도 첫 번째 문서만 근거로 기록됨). 이제 AI가 응답한
    // sourceDocumentTitle로 실제 근거 문서를 찾는다. 못 찾으면(제목 불일치, 또는 애초에
    // 근거 문서가 없는 안건이면) sourceDocument는 null로 남긴다.
    private Position toPosition(Agenda agenda, List<AgendaReferenceDocument> refDocs, OpenAiService.DraftPosition d) {
        List<String> activeFields = new ArrayList<>();
        if (d.answer() != null) activeFields.add("answer");
        if (d.preference() != null) activeFields.add("preference");
        if (d.concessionRange() != null) activeFields.add("concessionRange");
        if (d.dealbreaker() != null) activeFields.add("dealbreaker");

        SourceDocument matchedDoc = d.sourceDocumentTitle() == null ? null : refDocs.stream()
                .map(AgendaReferenceDocument::getSourceDocument)
                .filter(doc -> d.sourceDocumentTitle().equals(doc.getTitle()))
                .findFirst()
                .orElse(null);

        return Position.builder()
                .agenda(agenda)
                .topic(d.topic())
                .questionText(d.questionText())
                .generatedBy(GeneratedBy.AI_DRAFT)
                .sourceDocument(matchedDoc)
                .activeFields(activeFields)
                .answer(d.answer())
                .preference(d.preference())
                .concessionRange(d.concessionRange())
                .dealbreaker(d.dealbreaker())
                .priority(d.priority())
                .confidenceLevel(parseConfidenceLevel(d.confidenceLevel()))
                .build();
    }

    // AI가 유효하지 않은 값을 주거나 아예 안 준 경우, 근거 없다고 과신하지 않도록
    // 보수적으로 ESTIMATED(추정)를 기본값으로 둔다.
    private ConfidenceLevel parseConfidenceLevel(String value) {
        if (value == null) return ConfidenceLevel.ESTIMATED;
        try {
            return ConfidenceLevel.valueOf(value);
        } catch (IllegalArgumentException e) {
            return ConfidenceLevel.ESTIMATED;
        }
    }

    private List<Position> generateMockPositions(Agenda agenda, List<AgendaReferenceDocument> refDocs) {
        String purpose = agenda.getPurpose() != null ? agenda.getPurpose() : agenda.getTitle();
        SourceDocument firstDoc = refDocs.get(0).getSourceDocument();

        return List.of(
                Position.builder()
                        .agenda(agenda)
                        .topic("일정")
                        .questionText(purpose + "와 관련하여 일정을 조율할 수 있나요?")
                        .generatedBy(GeneratedBy.AI_DRAFT)
                        .sourceDocument(firstDoc)
                        .activeFields(List.of("preference", "concessionRange"))
                        .preference("현재 계획된 일정 유지")
                        .concessionRange("최대 1주일 조율 가능")
                        .confidenceLevel(ConfidenceLevel.DOCUMENT_BASED)
                        .build(),
                Position.builder()
                        .agenda(agenda)
                        .topic("예산")
                        .questionText(purpose + "의 예산 범위에 대해 협의할 수 있나요?")
                        .generatedBy(GeneratedBy.AI_DRAFT)
                        .sourceDocument(firstDoc)
                        .activeFields(List.of("preference", "concessionRange", "dealbreaker"))
                        .preference("현재 책정된 예산 유지")
                        .concessionRange("10% 범위 내 조율 가능")
                        .dealbreaker("예산 20% 이상 증가 불가")
                        .confidenceLevel(ConfidenceLevel.ESTIMATED)
                        .build(),
                Position.builder()
                        .agenda(agenda)
                        .topic("범위")
                        .questionText(purpose + "의 작업 범위 조정이 필요한 부분이 있나요?")
                        .generatedBy(GeneratedBy.AI_DRAFT)
                        .sourceDocument(firstDoc)
                        .activeFields(List.of("answer", "dealbreaker"))
                        .answer("현재 합의된 범위 내에서 진행 예정")
                        .dealbreaker("핵심 기능 범위 축소 불가")
                        .confidenceLevel(ConfidenceLevel.DOCUMENT_BASED)
                        .build()
        );
    }

    private List<String> buildActiveFields(CreatePositionRequest request) {
        List<String> fields = new ArrayList<>();
        if (request.answer() != null) fields.add("answer");
        if (request.preference() != null) fields.add("preference");
        if (request.concessionRange() != null) fields.add("concessionRange");
        if (request.dealbreaker() != null) fields.add("dealbreaker");
        if (request.priority() != null) fields.add("priority");
        if (request.scheduleConstraint() != null) fields.add("scheduleConstraint");
        return fields;
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
