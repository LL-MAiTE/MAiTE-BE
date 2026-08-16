package com.likelion.hackathon.domain.document.dto;

import jakarta.validation.constraints.NotBlank;

public record UploadDocumentRequest(
        @NotBlank String title,
        String content
) {}
