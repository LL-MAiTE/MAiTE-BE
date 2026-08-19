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

    // 라이브 화면이 진행 중인 실제 대화(양쪽 발화 원문)를 폴링으로 가져와 보여주기 위한
    // 조회 API. Agora message_subscriber 콜백이 /agora/callback에서 이미 각 발화를
    // createTranscript로 저장해두고 있었는데, 지금까지 이걸 다시 읽어오는 GET이 없어서
    // 프론트 라이브 화면이 실시간 대화 내용을 표시할 방법이 없었다.
    @GetMapping("/meetings/{id}/transcripts")
    public ApiResponse<List<TranscriptResponse>> getTranscripts(@PathVariable UUID id) {
        return ApiResponse.ok(meetingService.getTranscripts(id));
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
