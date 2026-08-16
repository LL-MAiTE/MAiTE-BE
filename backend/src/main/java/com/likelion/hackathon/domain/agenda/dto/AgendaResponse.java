package com.likelion.hackathon.domain.agenda.dto;

import com.likelion.hackathon.domain.agenda.entity.Agenda;
import com.likelion.hackathon.domain.agenda.entity.enums.AgendaStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AgendaResponse(
        UUID id, UUID projectId, String title, String purpose,
        String counterpartCountry, String counterpartLanguage,
        List<String> transcriptLanguages, List<String> translationSourceLanguages,
        List<String> translationTargetLanguages,
        AgendaStatus status, UUID createdBy, LocalDateTime createdAt
) {
    public static AgendaResponse from(Agenda agenda) {
        return new AgendaResponse(
                agenda.getId(), agenda.getProject().getId(),
                agenda.getTitle(), agenda.getPurpose(),
                agenda.getCounterpartCountry(), agenda.getCounterpartLanguage(),
                agenda.getTranscriptLanguages(), agenda.getTranslationSourceLanguages(),
                agenda.getTranslationTargetLanguages(),
                agenda.getStatus(), agenda.getCreatedBy().getId(), agenda.getCreatedAt()
        );
    }
}
