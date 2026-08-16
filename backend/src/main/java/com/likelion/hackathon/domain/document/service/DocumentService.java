package com.likelion.hackathon.domain.document.service;

import com.likelion.hackathon.domain.document.dto.*;
import com.likelion.hackathon.domain.document.entity.SourceConnection;
import com.likelion.hackathon.domain.document.entity.SourceDocument;
import com.likelion.hackathon.domain.document.repository.SourceConnectionRepository;
import com.likelion.hackathon.domain.document.repository.SourceDocumentRepository;
import com.likelion.hackathon.domain.project.entity.Project;
import com.likelion.hackathon.domain.project.service.ProjectService;
import com.likelion.hackathon.domain.user.entity.User;
import com.likelion.hackathon.domain.user.repository.UserRepository;
import com.likelion.hackathon.global.exception.CustomException;
import com.likelion.hackathon.global.exception.ErrorCode;
import com.likelion.hackathon.global.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final SourceConnectionRepository connectionRepository;
    private final SourceDocumentRepository documentRepository;
    private final ProjectService projectService;
    private final UserRepository userRepository;

    @Transactional
    public SourceConnectionResponse createConnection(UUID projectId, CreateConnectionRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Project project = projectService.getProjectAndVerifyMember(projectId, userId);
        User user = getUser(userId);

        SourceConnection connection = SourceConnection.builder()
                .project(project)
                .type(request.type())
                .workspaceOrRepoName(request.workspaceOrRepoName())
                .accessToken(request.accessToken())
                .connectedBy(user)
                .connectedAt(LocalDateTime.now())
                .build();
        connectionRepository.save(connection);
        return SourceConnectionResponse.from(connection);
    }

    @Transactional
    public SyncResponse sync(UUID connectionId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        SourceConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SOURCE_CONNECTION_NOT_FOUND));
        projectService.getProjectAndVerifyMember(connection.getProject().getId(), userId);

        // 실제 외부 연동 대신 stub: 기존 문서 syncedAt 갱신
        List<SourceDocument> docs = documentRepository.findAllByConnection(connection);
        LocalDateTime now = LocalDateTime.now();
        docs.forEach(doc -> doc.updateSyncInfo(now));

        List<String> latestFiles = docs.stream().map(SourceDocument::getTitle).toList();
        return new SyncResponse(docs.size(), latestFiles);
    }

    @Transactional
    public SourceDocumentResponse uploadDocument(UUID projectId, UploadDocumentRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Project project = projectService.getProjectAndVerifyMember(projectId, userId);

        SourceDocument doc = SourceDocument.builder()
                .project(project)
                .title(request.title())
                .content(request.content())
                .lastModifiedAt(LocalDateTime.now())
                .build();
        documentRepository.save(doc);
        return SourceDocumentResponse.from(doc);
    }

    @Transactional
    public SourceDocumentResponse updateDocument(UUID documentId, UpdateDocumentRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        SourceDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.SOURCE_DOCUMENT_NOT_FOUND));
        projectService.getProjectAndVerifyMember(doc.getProject().getId(), userId);

        if (request.isCoreContext() != null) {
            doc.setCoreContext(request.isCoreContext());
        }
        return SourceDocumentResponse.from(doc);
    }

    @Transactional(readOnly = true)
    public List<SourceDocumentResponse> getDocuments(UUID projectId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Project project = projectService.getProjectAndVerifyMember(projectId, userId);
        return documentRepository.findAllByProjectOrderByIsCoreContextDescLastModifiedAtDesc(project)
                .stream().map(SourceDocumentResponse::from).toList();
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
