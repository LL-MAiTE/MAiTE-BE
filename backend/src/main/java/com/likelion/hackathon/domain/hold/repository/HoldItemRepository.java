package com.likelion.hackathon.domain.hold.repository;

import com.likelion.hackathon.domain.hold.entity.HoldItem;
import com.likelion.hackathon.domain.hold.entity.enums.HoldItemStatus;
import com.likelion.hackathon.domain.meeting.entity.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface HoldItemRepository extends JpaRepository<HoldItem, UUID> {
    List<HoldItem> findAllByMeeting(Meeting meeting);
    List<HoldItem> findAllByMeetingOrderByCreatedAtDesc(Meeting meeting);
    List<HoldItem> findAllByStatusAndDeliveredToCounterpartAtBefore(
            HoldItemStatus status, LocalDateTime threshold);
    boolean existsByMeetingAndStatusNotIn(Meeting meeting, List<HoldItemStatus> resolvedStatuses);
}
