package com.likelion.hackathon.domain.confirmation.controller;

import com.likelion.hackathon.domain.confirmation.dto.*;
import com.likelion.hackathon.domain.confirmation.service.ConfirmationService;
import com.likelion.hackathon.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ConfirmationController {

    private final ConfirmationService confirmationService;

    @PostMapping("/meeting-logs/{id}/number-confirmation")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<NumberConfirmationResponse> createConfirmation(
            @PathVariable UUID id,
            @RequestBody @Valid CreateNumberConfirmationRequest request) {
        return ApiResponse.ok(confirmationService.createConfirmation(id, request));
    }

    @PatchMapping("/number-confirmations/{id}")
    public ApiResponse<NumberConfirmationResponse> updateConfirmation(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateNumberConfirmationRequest request) {
        return ApiResponse.ok(confirmationService.updateConfirmation(id, request));
    }
}
