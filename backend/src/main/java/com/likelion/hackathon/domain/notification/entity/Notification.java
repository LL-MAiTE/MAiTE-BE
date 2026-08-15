package com.likelion.hackathon.domain.notification.entity;

import com.likelion.hackathon.domain.notification.entity.enums.NotificationType;
import com.likelion.hackathon.domain.user.entity.User;
import com.likelion.hackathon.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    private UUID referenceId;       // 알림 대상 레코드 id (holdItemId, meetingId 등)

    private String referenceType;   // "hold_item" | "meeting" 등

    @Column(nullable = false)
    @Builder.Default
    private boolean isRead = false;
}
