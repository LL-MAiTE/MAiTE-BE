package com.likelion.hackathon.domain.confirmation.repository;

import com.likelion.hackathon.domain.confirmation.entity.NumberConfirmation;
import com.likelion.hackathon.domain.meeting.entity.MeetingLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NumberConfirmationRepository extends JpaRepository<NumberConfirmation, UUID> {
    Optional<NumberConfirmation> findByMeetingLog(MeetingLog meetingLog);
}
