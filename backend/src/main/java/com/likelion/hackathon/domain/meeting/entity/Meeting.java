package com.likelion.hackathon.domain.meeting.entity;

import com.likelion.hackathon.domain.agenda.entity.Agenda;
import com.likelion.hackathon.domain.meeting.entity.enums.MeetingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "meetings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agenda_id", nullable = false)
    private Agenda agenda;

    private LocalDateTime startedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MeetingStatus status = MeetingStatus.IN_PROGRESS;

    private LocalDateTime disclosureCompletedAt;    // AI 대리진행 고지 완료 시각

    private LocalDateTime voiceSessionEndedAt;      // 실시간 음성 세션 종료 시각

    private LocalDateTime closedAt;                 // 모든 보류 항목 종결 후 자동 종료 시각

    private String agoraAgentId;

    public void setAgoraAgentId(String agentId) {
        this.agoraAgentId = agentId;
    }

    public void start() {
        this.startedAt = LocalDateTime.now();
        this.status = MeetingStatus.IN_PROGRESS;
    }

    public void completeDisclosure() {
        this.disclosureCompletedAt = LocalDateTime.now();
    }

    public void endVoiceSession() {
        this.voiceSessionEndedAt = LocalDateTime.now();
        this.status = MeetingStatus.PENDING_FOLLOWUP;
    }

    public void close() {
        this.closedAt = LocalDateTime.now();
        this.status = MeetingStatus.CLOSED;
    }
}
