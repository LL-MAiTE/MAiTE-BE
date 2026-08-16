package com.likelion.hackathon.domain.meeting.repository;

import com.likelion.hackathon.domain.agenda.entity.Agenda;
import com.likelion.hackathon.domain.meeting.entity.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {
    List<Meeting> findAllByAgenda(Agenda agenda);
}
