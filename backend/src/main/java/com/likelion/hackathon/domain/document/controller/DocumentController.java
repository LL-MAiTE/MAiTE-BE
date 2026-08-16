package com.likelion.hackathon.domain.document.controller;

import com.likelion.hackathon.domain.document.dto.*;
import com.likelion.hackathon.domain.document.service.DocumentService;
import com.likelion.hackathon.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/projects/{id}/connections")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SourceConnectionResponse> createConnection(
            @PathVariable UUID id,
            @RequestBody @Valid CreateConnectionRequest request) {
        return ApiResponse.ok(documentService.createConnection(id, request));
    }

    @PostMapping("/connections/{id}/sync")
    public ApiResponse<SyncResponse> sync(@PathVariable UUID id) {
        return ApiResponse.ok(documentService.sync(id));
    }

    @PostMapping("/projects/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SourceDocumentResponse> uploadDocument(
            @PathVariable UUID id,
            @RequestBody @Valid UploadDocumentRequest request) {
        return ApiResponse.ok(documentService.uploadDocument(id, request));
    }

    @PatchMapping("/documents/{id}")
    public ApiResponse<SourceDocumentResponse> updateDocument(
            @PathVariable UUID id,
            @RequestBody UpdateDocumentRequest request) {
        return ApiResponse.ok(documentService.updateDocument(id, request));
    }

    @GetMapping("/projects/{id}/documents")
    public ApiResponse<List<SourceDocumentResponse>> getDocuments(@PathVariable UUID id) {
        return ApiResponse.ok(documentService.getDocuments(id));
    }
}
