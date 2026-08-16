package com.likelion.hackathon.domain.review.repository;

import com.likelion.hackathon.domain.meeting.entity.MeetingLog;
import com.likelion.hackathon.domain.review.entity.ReviewAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReviewActionRepository extends JpaRepository<ReviewAction, UUID> {
    List<ReviewAction> findAllByMeetingLog(MeetingLog meetingLog);
}
