package com.likelion.hackathon.domain.review.service;

import com.likelion.hackathon.domain.hold.entity.HoldItem;
import com.likelion.hackathon.domain.hold.entity.enums.HoldItemOrigin;
import com.likelion.hackathon.domain.hold.repository.HoldItemRepository;
import com.likelion.hackathon.domain.meeting.entity.Meeting;
import com.likelion.hackathon.domain.meeting.entity.MeetingLog;
import com.likelion.hackathon.domain.meeting.repository.MeetingLogRepository;
import com.likelion.hackathon.domain.meeting.repository.MeetingRepository;
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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewActionRepository reviewActionRepository;
    private final RequiredReviewRepository requiredReviewRepository;
    private final MeetingLogRepository meetingLogRepository;
    private final MeetingRepository meetingRepository;
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

    /**
     * 미팅 하나에 걸린 필수 검토 항목 전체 조회 (프론트 "결과 검토" 화면용). 필수 검토는
     * meeting_log 단위로 지정되는데, 이걸 모아보는 GET이 없어서 프론트가 로컬 mock에
     * 머물러 있었다 — hold-items가 /meetings/:id/hold-items로 모아보는 것과 같은 패턴으로,
     * 미팅에 속한 로그들을 먼저 찾고 그 로그들에 걸린 required_review를 모아서 돌려준다.
     */
    @Transactional(readOnly = true)
    public List<RequiredReviewResponse> getRequiredReviews(UUID meetingId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_NOT_FOUND));
        projectService.getProjectAndVerifyMember(meeting.getAgenda().getProject().getId(), userId);

        List<MeetingLog> logs = meetingLogRepository.findAllByMeetingOrderByTranscriptSpokenAt(meeting);
        if (logs.isEmpty()) return List.of();
        return requiredReviewRepository.findAllByMeetingLogIn(logs)
                .stream().map(RequiredReviewResponse::from).toList();
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
