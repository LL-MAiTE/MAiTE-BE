package com.likelion.hackathon.domain.hold.controller;

import com.likelion.hackathon.domain.hold.dto.*;
import com.likelion.hackathon.domain.hold.entity.enums.HoldItemStatus;
import com.likelion.hackathon.domain.hold.service.HoldService;
import com.likelion.hackathon.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class HoldController {

    private final HoldService holdService;

    @PostMapping("/coordination-records")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CoordinationRecordResponse> createCoordinationRecord(
            @RequestParam UUID meetingId,
            @RequestBody @Valid CreateCoordinationRecordRequest request) {
        return ApiResponse.ok(holdService.createCoordinationRecord(meetingId, request));
    }

    @GetMapping("/meetings/{id}/hold-items")
    public ApiResponse<List<HoldItemResponse>> getHoldItems(@PathVariable UUID id) {
        return ApiResponse.ok(holdService.getHoldItems(id));
    }

    @PostMapping("/hold-items/{id}/answer")
    public ApiResponse<HoldItemResponse> answerHoldItem(
            @PathVariable UUID id,
            @RequestBody @Valid AnswerHoldItemRequest request) {
        return ApiResponse.ok(holdService.answerHoldItem(id, request));
    }

    @PostMapping("/hold-items/{id}/reopen")
    public ApiResponse<HoldItemResponse> reopenHoldItem(@PathVariable UUID id) {
        return ApiResponse.ok(holdService.reopenHoldItem(id));
    }

    @PatchMapping("/hold-items/{id}")
    public ApiResponse<HoldItemResponse> updateHoldItem(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        HoldItemStatus status = HoldItemStatus.valueOf(body.get("status"));
        return ApiResponse.ok(holdService.updateHoldItem(id, status));
    }
}
