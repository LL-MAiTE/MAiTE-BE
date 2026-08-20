package com.likelion.hackathon.domain.review.repository;

import com.likelion.hackathon.domain.meeting.entity.MeetingLog;
import com.likelion.hackathon.domain.review.entity.RequiredReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RequiredReviewRepository extends JpaRepository<RequiredReview, UUID> {
    List<RequiredReview> findAllByMeetingLog(MeetingLog meetingLog);
    List<RequiredReview> findAllByMeetingLogIn(List<MeetingLog> meetingLogs);
}
