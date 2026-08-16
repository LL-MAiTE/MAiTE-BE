package com.likelion.hackathon.domain.hold.entity;

import com.likelion.hackathon.domain.confirmation.entity.NumberConfirmation;
import com.likelion.hackathon.domain.hold.entity.enums.HoldItemOrigin;
import com.likelion.hackathon.domain.hold.entity.enums.HoldItemStatus;
import com.likelion.hackathon.domain.meeting.entity.MeetingLog;
import com.likelion.hackathon.domain.meeting.entity.Transcript;
import com.likelion.hackathon.domain.meeting.entity.Meeting;
import com.likelion.hackathon.domain.user.entity.User;
import com.likelion.hackathon.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "hold_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class HoldItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_log_id")
    private MeetingLog meetingLog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "number_confirmation_id")
    private NumberConfirmation numberConfirmation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HoldItemOrigin origin;

    private String reason; // "핵심의도 불일치", "숫자확인 미응답", "사후 재보류"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_transcript_id")
    private Transcript relatedTranscript;

    @Column(columnDefinition = "TEXT")
    private String answerText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answered_by")
    private User answeredBy;

    private LocalDateTime answeredAt;

    // 비동기 전달 시각 — 여기서부터 24~48시간 타임아웃 카운트 시작
    private LocalDateTime deliveredToCounterpartAt;

    // 최대 2
    @Column(nullable = false)
    @Builder.Default
    private int reopenCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private HoldItemStatus status = HoldItemStatus.UNRESOLVED;

    private LocalDateTime resolvedAt;

    public void answer(String answerText, User answeredBy) {
        this.answerText = answerText;
        this.answeredBy = answeredBy;
        this.answeredAt = LocalDateTime.now();
        this.status = HoldItemStatus.AWAITING_ANSWER;
    }

    public void deliver() {
        this.deliveredToCounterpartAt = LocalDateTime.now();
    }

    public void reopen() {
        if (this.reopenCount >= 2) {
            throw new IllegalStateException("재오픈 횟수 상한(2회)에 도달했습니다.");
        }
        this.reopenCount++;
        this.status = HoldItemStatus.REOPENED;
    }

    public void confirmImmediate() {
        this.status = HoldItemStatus.CONFIRMED_IMMEDIATE;
        this.resolvedAt = LocalDateTime.now();
    }

    public void confirmByTimeout() {
        this.status = HoldItemStatus.CONFIRMED_TIMEOUT;
        this.resolvedAt = LocalDateTime.now();
    }

    public void needsRealtime() {
        this.status = HoldItemStatus.NEEDS_REALTIME;
        this.resolvedAt = LocalDateTime.now();
    }
}
