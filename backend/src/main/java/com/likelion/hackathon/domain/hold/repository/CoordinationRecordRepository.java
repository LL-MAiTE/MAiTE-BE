package com.likelion.hackathon.domain.hold.repository;

import com.likelion.hackathon.domain.hold.entity.CoordinationRecord;
import com.likelion.hackathon.domain.meeting.entity.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CoordinationRecordRepository extends JpaRepository<CoordinationRecord, UUID> {
    List<CoordinationRecord> findAllByMeeting(Meeting meeting);
}
