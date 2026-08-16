package com.likelion.hackathon.domain.agenda.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record SelectDocumentsRequest(@NotEmpty List<UUID> sourceDocumentIds) {}
