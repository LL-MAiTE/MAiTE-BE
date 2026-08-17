package com.likelion.hackathon.domain.meeting.entity;

import com.likelion.hackathon.domain.agenda.entity.Position;
import com.likelion.hackathon.domain.meeting.entity.enums.MeetingPositionResultStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "meeting_positions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"meeting_id", "position_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MeetingPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id", nullable = false)
    private Position position;

    @Column(nullable = false)
    private int version; // 스냅샷 시점의 position.version

    @Column(nullable = false)
    private LocalDateTime snappedAt;

    // 회의 종료 시 대화 전체를 분석해 채워짐 (endMeeting)
    @Column(columnDefinition = "TEXT")
    private String agreedValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MeetingPositionResultStatus resultStatus = MeetingPositionResultStatus.NOT_DISCUSSED;

    private LocalDateTime resolvedAt;

    public void recordResult(MeetingPositionResultStatus status, String agreedValue, LocalDateTime resolvedAt) {
        this.resultStatus = status;
        this.agreedValue = agreedValue;
        this.resolvedAt = resolvedAt;
    }
}
