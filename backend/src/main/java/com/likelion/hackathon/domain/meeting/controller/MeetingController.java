package com.likelion.hackathon.domain.meeting.controller;

import com.likelion.hackathon.domain.meeting.dto.*;
import com.likelion.hackathon.domain.meeting.service.MeetingService;
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
public class MeetingController {

    private final MeetingService meetingService;

    @PostMapping("/agendas/{id}/meetings")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MeetingResponse> createMeeting(@PathVariable UUID id) {
        return ApiResponse.ok(meetingService.createMeeting(id));
    }

    @GetMapping("/meetings/{id}")
    public ApiResponse<MeetingResponse> getMeeting(@PathVariable UUID id) {
        return ApiResponse.ok(meetingService.getMeeting(id));
    }

    @PostMapping("/meetings/{id}/start")
    public ApiResponse<Map<String, Object>> startMeeting(@PathVariable UUID id) {
        return ApiResponse.ok(meetingService.startMeeting(id));
    }

    @PostMapping("/meetings/{id}/transcripts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TranscriptResponse> createTranscript(
            @PathVariable UUID id,
            @RequestBody @Valid CreateTranscriptRequest request) {
        return ApiResponse.ok(meetingService.createTranscript(id, request));
    }

    @PostMapping("/meetings/{id}/meeting-logs")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MeetingLogResponse> createMeetingLog(
            @PathVariable UUID id,
            @RequestBody @Valid CreateMeetingLogRequest request) {
        return ApiResponse.ok(meetingService.createMeetingLog(id, request));
    }

    @GetMapping("/meetings/{id}/positions")
    public ApiResponse<List<MeetingPositionResponse>> getMeetingPositions(@PathVariable UUID id) {
        return ApiResponse.ok(meetingService.getMeetingPositions(id));
    }

    @GetMapping("/meetings/{id}/meeting-logs")
    public ApiResponse<List<MeetingLogResponse>> getMeetingLogs(@PathVariable UUID id) {
        return ApiResponse.ok(meetingService.getMeetingLogs(id));
    }

    @PostMapping("/meetings/{id}/end")
    public ApiResponse<Void> endMeeting(@PathVariable UUID id) {
        meetingService.endMeeting(id);
        return ApiResponse.ok();
    }

    @GetMapping("/meetings/{id}/channel-info")
    public ApiResponse<ChannelInfoResponse> getChannelInfo(@PathVariable UUID id) {
        return ApiResponse.ok(meetingService.getChannelInfo(id));
    }
}
