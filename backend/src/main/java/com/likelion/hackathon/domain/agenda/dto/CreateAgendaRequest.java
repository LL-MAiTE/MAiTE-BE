package com.likelion.hackathon.domain.agenda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateAgendaRequest(
        @NotNull UUID projectId,
        @NotBlank String title,
        String purpose,
        String counterpartCountry,
        String counterpartLanguage,
        List<String> transcriptLanguages,
        List<String> translationSourceLanguages,
        List<String> translationTargetLanguages
) {}
