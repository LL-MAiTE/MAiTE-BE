package com.likelion.hackathon.domain.agenda.repository;

import com.likelion.hackathon.domain.agenda.entity.Agenda;
import com.likelion.hackathon.domain.agenda.entity.AgendaReferenceDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgendaReferenceDocumentRepository extends JpaRepository<AgendaReferenceDocument, UUID> {
    List<AgendaReferenceDocument> findAllByAgenda(Agenda agenda);
    List<AgendaReferenceDocument> findAllByAgendaAndExcludedFalse(Agenda agenda);
    Optional<AgendaReferenceDocument> findByAgendaAndSourceDocumentId(Agenda agenda, UUID sourceDocumentId);
}
