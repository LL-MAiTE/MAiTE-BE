package com.likelion.hackathon.domain.agenda.controller;

import com.likelion.hackathon.domain.agenda.dto.*;
import com.likelion.hackathon.domain.agenda.service.AgendaService;
import com.likelion.hackathon.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AgendaController {

    private final AgendaService agendaService;

    @GetMapping("/projects/{id}/agendas")
    public ApiResponse<List<AgendaResponse>> getAgendas(@PathVariable UUID id) {
        return ApiResponse.ok(agendaService.getAgendas(id));
    }

    @GetMapping("/agendas/{id}")
    public ApiResponse<AgendaResponse> getAgenda(@PathVariable UUID id) {
        return ApiResponse.ok(agendaService.getAgenda(id));
    }

    @PostMapping("/agendas")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AgendaResponse> createAgenda(@RequestBody @Valid CreateAgendaRequest request) {
        return ApiResponse.ok(agendaService.createAgenda(request));
    }

    @PostMapping("/agendas/{id}/reference-documents")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<List<AgendaReferenceDocumentResponse>> selectReferenceDocuments(
            @PathVariable UUID id,
            @RequestBody @Valid SelectDocumentsRequest request) {
        return ApiResponse.ok(agendaService.selectReferenceDocuments(id, request));
    }

    @PatchMapping("/agenda-reference-documents/{id}")
    public ApiResponse<AgendaReferenceDocumentResponse> updateReferenceDocument(
            @PathVariable UUID id,
            @RequestBody UpdateReferenceDocumentRequest request) {
        return ApiResponse.ok(agendaService.updateReferenceDocument(id, request));
    }

    @PostMapping("/agendas/{id}/draft-positions")
    public ApiResponse<List<PositionResponse>> generateDraftPositions(@PathVariable UUID id) {
        return ApiResponse.ok(agendaService.generateDraftPositions(id));
    }

    @GetMapping("/agendas/{id}/positions")
    public ApiResponse<List<PositionResponse>> getPositions(@PathVariable UUID id) {
        return ApiResponse.ok(agendaService.getPositions(id));
    }

    @PostMapping("/agendas/{id}/positions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PositionResponse> createPosition(
            @PathVariable UUID id,
            @RequestBody @Valid CreatePositionRequest request) {
        return ApiResponse.ok(agendaService.createPosition(id, request));
    }

    @PostMapping("/positions/{id}/approve")
    public ApiResponse<PositionResponse> approvePosition(
            @PathVariable UUID id,
            @RequestBody @Valid ApprovePositionRequest request) {
        return ApiResponse.ok(agendaService.approvePosition(id, request));
    }

    @PostMapping("/positions/{id}/revise")
    public ApiResponse<PositionResponse> revisePosition(
            @PathVariable UUID id,
            @RequestBody @Valid RevisePositionRequest request) {
        return ApiResponse.ok(agendaService.revisePosition(id, request));
    }

    @PostMapping("/positions/{id}/reject")
    public ApiResponse<PositionResponse> rejectPosition(@PathVariable UUID id) {
        return ApiResponse.ok(agendaService.rejectPosition(id));
    }

    @DeleteMapping("/positions/{id}")
    public ApiResponse<Void> deletePosition(@PathVariable UUID id) {
        agendaService.deletePosition(id);
        return ApiResponse.ok();
    }
}
