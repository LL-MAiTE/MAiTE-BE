package com.likelion.hackathon.domain.confirmation.entity;

import com.likelion.hackathon.domain.confirmation.entity.enums.ConfirmationResponseType;
import com.likelion.hackathon.domain.meeting.entity.MeetingLog;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "number_confirmations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NumberConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_log_id", nullable = false)
    private MeetingLog meetingLog;

    @Column(nullable = false)
    private String detectedValue; // 감지된 숫자/단위 (예: "8/28")

    @Column(nullable = false)
    private LocalDateTime popupShownAt;

    @Enumerated(EnumType.STRING)
    private ConfirmationResponseType responseType;

    private LocalDateTime respondedAt;

    // X 또는 미응답이면 true
    @Column(nullable = false)
    @Builder.Default
    private boolean resultedInHold = false;

    public void respond(ConfirmationResponseType responseType) {
        this.responseType = responseType;
        this.respondedAt = LocalDateTime.now();
        this.resultedInHold = responseType != ConfirmationResponseType.CONFIRMED;
    }
}
