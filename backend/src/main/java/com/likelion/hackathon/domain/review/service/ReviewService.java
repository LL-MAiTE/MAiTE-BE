package com.likelion.hackathon.domain.review.service;

import com.likelion.hackathon.domain.hold.entity.HoldItem;
import com.likelion.hackathon.domain.hold.entity.enums.HoldItemOrigin;
import com.likelion.hackathon.domain.hold.repository.HoldItemRepository;
import com.likelion.hackathon.domain.meeting.entity.MeetingLog;
import com.likelion.hackathon.domain.meeting.repository.MeetingLogRepository;
import com.likelion.hackathon.domain.project.service.ProjectService;
import com.likelion.hackathon.domain.review.dto.*;
import com.likelion.hackathon.domain.review.entity.RequiredReview;
import com.likelion.hackathon.domain.review.entity.ReviewAction;
import com.likelion.hackathon.domain.review.entity.enums.ReviewActionType;
import com.likelion.hackathon.domain.review.repository.RequiredReviewRepository;
import com.likelion.hackathon.domain.review.repository.ReviewActionRepository;
import com.likelion.hackathon.domain.user.entity.User;
import com.likelion.hackathon.domain.user.repository.UserRepository;
import com.likelion.hackathon.global.exception.CustomException;
import com.likelion.hackathon.global.exception.ErrorCode;
import com.likelion.hackathon.global.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewActionRepository reviewActionRepository;
    private final RequiredReviewRepository requiredReviewRepository;
    private final MeetingLogRepository meetingLogRepository;
    private final HoldItemRepository holdItemRepository;
    private final UserRepository userRepository;
    private final ProjectService projectService;

    @Transactional
    public ReviewActionResponse createReviewAction(UUID meetingLogId, CreateReviewActionRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        MeetingLog log = getMeetingLogAndVerify(meetingLogId, userId);
        User user = getUser(userId);

        HoldItem resultingHoldItem = null;
        if (request.action() == ReviewActionType.RE_HELD) {
            resultingHoldItem = HoldItem.builder()
                    .meeting(log.getMeeting())
                    .meetingLog(log)
                    .origin(HoldItemOrigin.POST_RE_HOLD)
                    .reason("사후 재보류: " + (request.note() != null ? request.note() : "검토 결과 재보류 처리"))
                    .build();
            holdItemRepository.save(resultingHoldItem);
        }

        ReviewAction action = ReviewAction.builder()
                .meetingLog(log)
                .reviewer(user)
                .action(request.action())
                .resultingHoldItem(resultingHoldItem)
                .note(request.note())
                .build();
        reviewActionRepository.save(action);
        return ReviewActionResponse.from(action);
    }

    @Transactional
    public RequiredReviewResponse createRequiredReview(UUID meetingLogId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        MeetingLog log = getMeetingLogAndVerify(meetingLogId, userId);
        User user = getUser(userId);

        RequiredReview review = RequiredReview.builder()
                .meetingLog(log)
                .designatedBy(user)
                .designatedAt(LocalDateTime.now())
                .build();
        requiredReviewRepository.save(review);
        return RequiredReviewResponse.from(review);
    }

    @Transactional
    public RequiredReviewResponse confirmRequiredReview(UUID reviewId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        RequiredReview review = requiredReviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REQUIRED_REVIEW_NOT_FOUND));
        projectService.getProjectAndVerifyMember(
                review.getMeetingLog().getMeeting().getAgenda().getProject().getId(), userId);
        User user = getUser(userId);
        review.confirm(user);
        return RequiredReviewResponse.from(review);
    }

    private MeetingLog getMeetingLogAndVerify(UUID meetingLogId, UUID userId) {
        MeetingLog log = meetingLogRepository.findById(meetingLogId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_LOG_NOT_FOUND));
        projectService.getProjectAndVerifyMember(
                log.getMeeting().getAgenda().getProject().getId(), userId);
        return log;
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
