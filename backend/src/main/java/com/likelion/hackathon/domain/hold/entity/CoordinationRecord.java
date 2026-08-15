package com.likelion.hackathon.domain.hold.entity;

import com.likelion.hackathon.domain.agenda.entity.Position;
import com.likelion.hackathon.domain.hold.entity.enums.CoordinationResult;
import com.likelion.hackathon.domain.meeting.entity.Meeting;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "coordination_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CoordinationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id", nullable = false)
    private Position position;

    @Column(columnDefinition = "TEXT")
    private String proposedContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CoordinationResult result;

    private String nextAction;

    // result=OUT_OF_RANGE일 때 자동 생성된 hold_item 역추적용
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resulting_hold_item_id")
    private HoldItem resultingHoldItem;
}
