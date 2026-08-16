package com.likelion.hackathon.domain.agenda.repository;

import com.likelion.hackathon.domain.agenda.entity.Agenda;
import com.likelion.hackathon.domain.agenda.entity.Position;
import com.likelion.hackathon.domain.agenda.entity.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PositionRepository extends JpaRepository<Position, UUID> {
    List<Position> findAllByAgendaAndIsLatestTrue(Agenda agenda);
    List<Position> findAllByAgendaAndIsLatestTrueAndApprovalStatusIn(
            Agenda agenda, List<ApprovalStatus> statuses);
}
