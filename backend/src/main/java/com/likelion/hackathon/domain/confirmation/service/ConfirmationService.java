package com.likelion.hackathon.domain.confirmation.service;

import com.likelion.hackathon.domain.confirmation.dto.*;
import com.likelion.hackathon.domain.confirmation.entity.NumberConfirmation;
import com.likelion.hackathon.domain.confirmation.entity.enums.ConfirmationResponseType;
import com.likelion.hackathon.domain.confirmation.repository.NumberConfirmationRepository;
import com.likelion.hackathon.domain.hold.entity.HoldItem;
import com.likelion.hackathon.domain.hold.entity.enums.HoldItemOrigin;
import com.likelion.hackathon.domain.hold.repository.HoldItemRepository;
import com.likelion.hackathon.domain.meeting.entity.MeetingLog;
import com.likelion.hackathon.domain.meeting.repository.MeetingLogRepository;
import com.likelion.hackathon.domain.project.service.ProjectService;
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
public class ConfirmationService {

    private final NumberConfirmationRepository confirmationRepository;
    private final MeetingLogRepository meetingLogRepository;
    private final HoldItemRepository holdItemRepository;
    private final ProjectService projectService;

    @Transactional
    public NumberConfirmationResponse createConfirmation(UUID meetingLogId, CreateNumberConfirmationRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        MeetingLog log = meetingLogRepository.findById(meetingLogId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_LOG_NOT_FOUND));
        projectService.getProjectAndVerifyMember(
                log.getMeeting().getAgenda().getProject().getId(), userId);

        NumberConfirmation confirmation = NumberConfirmation.builder()
                .meetingLog(log)
                .detectedValue(request.detectedValue())
                .popupShownAt(LocalDateTime.now())
                .build();
        confirmationRepository.save(confirmation);
        return NumberConfirmationResponse.from(confirmation);
    }

    @Transactional
    public NumberConfirmationResponse updateConfirmation(UUID confirmationId, UpdateNumberConfirmationRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        NumberConfirmation confirmation = confirmationRepository.findById(confirmationId)
                .orElseThrow(() -> new CustomException(ErrorCode.NUMBER_CONFIRMATION_NOT_FOUND));
        projectService.getProjectAndVerifyMember(
                confirmation.getMeetingLog().getMeeting().getAgenda().getProject().getId(), userId);

        confirmation.respond(request.responseType());

        if (confirmation.isResultedInHold()) {
            HoldItem holdItem = HoldItem.builder()
                    .meeting(confirmation.getMeetingLog().getMeeting())
                    .meetingLog(confirmation.getMeetingLog())
                    .numberConfirmation(confirmation)
                    .origin(HoldItemOrigin.DURING_MEETING)
                    .reason("숫자확인 " + (request.responseType() == ConfirmationResponseType.AUTO_HOLD ? "미응답" : "거부"))
                    .build();
            holdItemRepository.save(holdItem);
        }

        return NumberConfirmationResponse.from(confirmation);
    }
}
