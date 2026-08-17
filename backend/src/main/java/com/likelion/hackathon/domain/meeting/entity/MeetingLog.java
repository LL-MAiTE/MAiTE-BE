package com.likelion.hackathon.domain.meeting.entity;

import com.likelion.hackathon.domain.meeting.entity.enums.MeetingLogStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "meeting_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MeetingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transcript_id", nullable = false)
    private Transcript transcript;

    // 매칭 안 되면 NULL → 보류. meeting_positions를 참조해 스냅샷 버전 추적
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_meeting_position_id")
    private MeetingPosition matchedMeetingPosition;

    @Column(columnDefinition = "TEXT")
    private String translatedText;

    private String audioUrl;

    // true면 number_confirmations 트리거
    @Column(nullable = false)
    @Builder.Default
    private boolean containsCriticalNumber = false;

    private String limitationNote; // 세부 조건 차이 시 제한사항

    private LocalDateTime deliveredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MeetingLogStatus status = MeetingLogStatus.PENDING;

    public void deliver() {
        this.status = MeetingLogStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
    }

    public void hold() {
        this.status = MeetingLogStatus.ON_HOLD;
    }

    public void markContainsCriticalNumber() {
        this.containsCriticalNumber = true;
    }
}
