package com.likelion.hackathon.domain.notification.controller;

import com.likelion.hackathon.domain.notification.dto.NotificationResponse;
import com.likelion.hackathon.domain.notification.service.NotificationService;
import com.likelion.hackathon.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<List<NotificationResponse>> getMyNotifications() {
        return ApiResponse.ok(notificationService.getMyNotifications());
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markRead(@PathVariable UUID id) {
        return ApiResponse.ok(notificationService.markRead(id));
    }

    @PatchMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead() {
        return ApiResponse.ok(notificationService.markAllRead());
    }
}
