package com.likelion.hackathon.domain.meeting.repository;

import com.likelion.hackathon.domain.meeting.entity.Meeting;
import com.likelion.hackathon.domain.meeting.entity.MeetingLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MeetingLogRepository extends JpaRepository<MeetingLog, UUID> {
    List<MeetingLog> findAllByMeetingOrderByTranscriptSpokenAt(Meeting meeting);
}
