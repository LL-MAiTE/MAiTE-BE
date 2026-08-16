package com.likelion.hackathon.domain.notification.service;

import com.likelion.hackathon.domain.notification.dto.NotificationResponse;
import com.likelion.hackathon.domain.notification.entity.Notification;
import com.likelion.hackathon.domain.notification.repository.NotificationRepository;
import com.likelion.hackathon.domain.user.entity.User;
import com.likelion.hackathon.domain.user.repository.UserRepository;
import com.likelion.hackathon.global.exception.CustomException;
import com.likelion.hackathon.global.exception.ErrorCode;
import com.likelion.hackathon.global.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications() {
        UUID userId = SecurityUtil.getCurrentUserId();
        User user = getUser(userId);
        return notificationRepository.findAllByUserOrderByCreatedAtDesc(user)
                .stream().map(NotificationResponse::from).toList();
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        notification.markRead();
        return NotificationResponse.from(notification);
    }

    @Transactional
    public Map<String, Integer> markAllRead() {
        UUID userId = SecurityUtil.getCurrentUserId();
        User user = getUser(userId);
        int updatedCount = notificationRepository.markAllRead(user);
        return Map.of("updatedCount", updatedCount);
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
