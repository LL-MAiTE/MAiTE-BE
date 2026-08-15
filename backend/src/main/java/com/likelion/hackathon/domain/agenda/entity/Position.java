package com.likelion.hackathon.domain.agenda.entity;

import com.likelion.hackathon.domain.agenda.entity.enums.ApprovalStatus;
import com.likelion.hackathon.domain.agenda.entity.enums.ConfidenceLevel;
import com.likelion.hackathon.domain.agenda.entity.enums.GeneratedBy;
import com.likelion.hackathon.domain.document.entity.SourceDocument;
import com.likelion.hackathon.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "positions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agenda_id", nullable = false)
    private Agenda agenda;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GeneratedBy generatedBy;

    // 사용자 직접추가면 NULL 가능
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id")
    private SourceDocument sourceDocument;

    // AI가 질문 성격에 맞게 채운 필드 목록 (예: ["preference","concessionRange"])
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> activeFields;

    @Column(columnDefinition = "TEXT")
    private String answer;

    private String preference;         // 선호안

    private String concessionRange;    // 양보 가능 범위

    private String dealbreaker;        // 양보 불가 사항

    private Integer priority;

    private String scheduleConstraint;

    // AI 초안일 때만 사용
    @Enumerated(EnumType.STRING)
    private ConfidenceLevel confidenceLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ApprovalStatus approvalStatus = ApprovalStatus.DRAFT;

    @Column(nullable = false)
    @Builder.Default
    private int version = 1;

    @Column(nullable = false)
    @Builder.Default
    private boolean isLatest = true;

    // 이전 버전 참조 (self-referential)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_id")
    private Position supersedes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    private LocalDateTime approvedAt;
}
