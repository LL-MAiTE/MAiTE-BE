package com.likelion.hackathon.domain.hold.service;

import com.likelion.hackathon.domain.agenda.entity.Position;
import com.likelion.hackathon.domain.agenda.repository.PositionRepository;
import com.likelion.hackathon.domain.hold.dto.*;
import com.likelion.hackathon.domain.hold.entity.CoordinationRecord;
import com.likelion.hackathon.domain.hold.entity.HoldItem;
import com.likelion.hackathon.domain.hold.entity.enums.CoordinationResult;
import com.likelion.hackathon.domain.hold.entity.enums.HoldItemOrigin;
import com.likelion.hackathon.domain.hold.entity.enums.HoldItemStatus;
import com.likelion.hackathon.domain.hold.repository.CoordinationRecordRepository;
import com.likelion.hackathon.domain.hold.repository.HoldItemRepository;
import com.likelion.hackathon.domain.meeting.entity.Meeting;
import com.likelion.hackathon.domain.meeting.entity.enums.MeetingStatus;
import com.likelion.hackathon.domain.meeting.repository.MeetingRepository;
import com.likelion.hackathon.domain.notification.entity.Notification;
import com.likelion.hackathon.domain.notification.entity.enums.NotificationType;
import com.likelion.hackathon.domain.notification.repository.NotificationRepository;
import com.likelion.hackathon.domain.project.service.ProjectService;
import com.likelion.hackathon.domain.user.entity.User;
import com.likelion.hackathon.domain.user.repository.UserRepository;
import com.likelion.hackathon.global.exception.CustomException;
import com.likelion.hackathon.global.exception.ErrorCode;
import com.likelion.hackathon.global.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldService {

    private final HoldItemRepository holdItemRepository;
    private final CoordinationRecordRepository coordinationRecordRepository;
    private final MeetingRepository meetingRepository;
    private final PositionRepository positionRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ProjectService projectService;

    // 답변 전달 후 이 시간(시간 단위)이 지나도 상대방이 재오픈하지 않으면 자동 확정.
    // 명세서 기준 24~48시간 범위 중 하한값을 기본값으로 사용.
    @Value("${hold-item.timeout-hours:24}")
    private int timeoutHours;

    private static final List<HoldItemStatus> RESOLVED_STATUSES = List.of(
            HoldItemStatus.CONFIRMED_IMMEDIATE,
            HoldItemStatus.CONFIRMED_TIMEOUT,
            HoldItemStatus.NEEDS_REALTIME
    );

    @Transactional
    public CoordinationRecordResponse createCoordinationRecord(UUID meetingId, CreateCoordinationRecordRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = getMeetingAndVerify(meetingId, userId);
        Position position = positionRepository.findById(request.positionId())
                .orElseThrow(() -> new CustomException(ErrorCode.POSITION_NOT_FOUND));

        HoldItem resultingHoldItem = null;
        if (request.result() == CoordinationResult.OUT_OF_RANGE) {
            resultingHoldItem = HoldItem.builder()
                    .meeting(meeting)
                    .origin(HoldItemOrigin.DURING_MEETING)
                    .reason("승인범위 밖 제안: " + request.proposedContent())
                    .build();
            holdItemRepository.save(resultingHoldItem);
        }

        CoordinationRecord record = CoordinationRecord.builder()
                .meeting(meeting)
                .position(position)
                .proposedContent(request.proposedContent())
                .result(request.result())
                .nextAction(request.nextAction())
                .resultingHoldItem(resultingHoldItem)
                .build();
        coordinationRecordRepository.save(record);
        return CoordinationRecordResponse.from(record);
    }

    @Transactional(readOnly = true)
    public List<HoldItemResponse> getHoldItems(UUID meetingId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = getMeetingAndVerify(meetingId, userId);
        return holdItemRepository.findAllByMeetingOrderByCreatedAtDesc(meeting)
                .stream().map(HoldItemResponse::from).toList();
    }

    @Transactional
    public HoldItemResponse answerHoldItem(UUID holdItemId, AnswerHoldItemRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        HoldItem item = holdItemRepository.findById(holdItemId)
                .orElseThrow(() -> new CustomException(ErrorCode.HOLD_ITEM_NOT_FOUND));
        projectService.getProjectAndVerifyMember(
                item.getMeeting().getAgenda().getProject().getId(), userId);
        User user = getUser(userId);

        item.answer(request.answerText(), user);
        item.deliver();

        // 상대방에게 알림 (실제로는 상대방 userId를 알아야 하지만, 여기서는 답변자 본인에게)
        notificationRepository.save(Notification.builder()
                .user(user)
                .type(NotificationType.HOLD_DELIVERED)
                .referenceId(item.getId())
                .referenceType("hold_item")
                .build());

        return HoldItemResponse.from(item);
    }

    @Transactional
    public HoldItemResponse reopenHoldItem(UUID holdItemId) {
        UUID userId = SecurityUtil.getCurrentUserId();
        HoldItem item = holdItemRepository.findById(holdItemId)
                .orElseThrow(() -> new CustomException(ErrorCode.HOLD_ITEM_NOT_FOUND));
        projectService.getProjectAndVerifyMember(
                item.getMeeting().getAgenda().getProject().getId(), userId);

        if (item.getReopenCount() >= 2) {
            throw new CustomException(ErrorCode.REOPEN_LIMIT_EXCEEDED);
        }
        item.reopen();

        if (item.getReopenCount() >= 2) {
            item.needsRealtime();
            notifyBothSides(item, NotificationType.NEEDS_REALTIME);
            checkAndCloseMeeting(item.getMeeting());
        } else {
            // 재오픈 요청 알림 → 답변 작성자에게
            notificationRepository.save(Notification.builder()
                    .user(item.getMeeting().getAgenda().getCreatedBy())
                    .type(NotificationType.REOPEN_REQUESTED)
                    .referenceId(item.getId())
                    .referenceType("hold_item")
                    .build());
        }

        return HoldItemResponse.from(item);
    }

    @Transactional
    public HoldItemResponse updateHoldItem(UUID holdItemId, HoldItemStatus newStatus) {
        UUID userId = SecurityUtil.getCurrentUserId();
        HoldItem item = holdItemRepository.findById(holdItemId)
                .orElseThrow(() -> new CustomException(ErrorCode.HOLD_ITEM_NOT_FOUND));
        projectService.getProjectAndVerifyMember(
                item.getMeeting().getAgenda().getProject().getId(), userId);

        if (newStatus == HoldItemStatus.CONFIRMED_TIMEOUT) {
            confirmTimeout(item);
        } else if (newStatus == HoldItemStatus.NEEDS_REALTIME) {
            item.needsRealtime();
            notifyBothSides(item, NotificationType.NEEDS_REALTIME);
            checkAndCloseMeeting(item.getMeeting());
        }

        return HoldItemResponse.from(item);
    }

    // 답변 전달 후 timeoutHours가 지나도 재오픈 안 된 항목을 자동 확정 처리.
    // 15분마다 확인 — 24~48시간 규모 창에 비해 충분히 촘촘함.
    @Scheduled(initialDelay = 0, fixedRate = 15 * 60 * 1000)
    @Transactional
    public void autoConfirmExpiredHoldItems() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(timeoutHours);
        List<HoldItem> expired = holdItemRepository
                .findAllByStatusAndDeliveredToCounterpartAtBefore(HoldItemStatus.AWAITING_ANSWER, threshold);

        if (expired.isEmpty()) return;

        log.info("Auto-confirming {} hold item(s) past {}h timeout", expired.size(), timeoutHours);
        for (HoldItem item : expired) {
            confirmTimeout(item);
        }
    }

    private void confirmTimeout(HoldItem item) {
        item.confirmByTimeout();
        notifyBothSides(item, NotificationType.AUTO_CONFIRMED);
        checkAndCloseMeeting(item.getMeeting());
    }

    private void notifyBothSides(HoldItem item, NotificationType type) {
        User answerer = item.getMeeting().getAgenda().getCreatedBy();
        notificationRepository.save(Notification.builder()
                .user(answerer).type(type)
                .referenceId(item.getId()).referenceType("hold_item").build());
    }

    private void checkAndCloseMeeting(Meeting meeting) {
        boolean hasUnresolved = holdItemRepository
                .existsByMeetingAndStatusNotIn(meeting, RESOLVED_STATUSES);
        if (!hasUnresolved && meeting.getStatus() == MeetingStatus.PENDING_FOLLOWUP) {
            meeting.close();
            User answerer = meeting.getAgenda().getCreatedBy();
            notificationRepository.save(Notification.builder()
                    .user(answerer).type(NotificationType.MEETING_CLOSED)
                    .referenceId(meeting.getId()).referenceType("meeting").build());
        }
    }

    private Meeting getMeetingAndVerify(UUID meetingId, UUID userId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_NOT_FOUND));
        projectService.getProjectAndVerifyMember(
                meeting.getAgenda().getProject().getId(), userId);
        return meeting;
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
