package com.likelion.hackathon.domain.meeting.repository;

import com.likelion.hackathon.domain.meeting.entity.Meeting;
import com.likelion.hackathon.domain.meeting.entity.MeetingPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MeetingPositionRepository extends JpaRepository<MeetingPosition, UUID> {
    List<MeetingPosition> findAllByMeeting(Meeting meeting);
}
