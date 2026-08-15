package com.likelion.hackathon.domain.review.entity;

import com.likelion.hackathon.domain.hold.entity.HoldItem;
import com.likelion.hackathon.domain.meeting.entity.MeetingLog;
import com.likelion.hackathon.domain.review.entity.enums.ReviewActionType;
import com.likelion.hackathon.domain.user.entity.User;
import com.likelion.hackathon.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "review_actions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ReviewAction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_log_id", nullable = false)
    private MeetingLog meetingLog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewActionType action;

    // action=RE_HELD일 때만 값 존재
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resulting_hold_item_id")
    private HoldItem resultingHoldItem;

    @Column(columnDefinition = "TEXT")
    private String note;
}
