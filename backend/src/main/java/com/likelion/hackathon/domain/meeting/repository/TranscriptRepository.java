package com.likelion.hackathon.domain.meeting.repository;

import com.likelion.hackathon.domain.meeting.entity.Meeting;
import com.likelion.hackathon.domain.meeting.entity.Transcript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TranscriptRepository extends JpaRepository<Transcript, UUID> {
    List<Transcript> findAllByMeetingOrderBySpokenAt(Meeting meeting);
}
