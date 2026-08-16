package com.likelion.hackathon.domain.agenda.repository;

import com.likelion.hackathon.domain.agenda.entity.Agenda;
import com.likelion.hackathon.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgendaRepository extends JpaRepository<Agenda, UUID> {
    List<Agenda> findAllByProjectOrderByCreatedAtDesc(Project project);
}
