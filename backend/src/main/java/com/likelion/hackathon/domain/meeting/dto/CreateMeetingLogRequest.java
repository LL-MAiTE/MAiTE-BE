package com.likelion.hackathon.domain.meeting.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateMeetingLogRequest(@NotNull UUID transcriptId) {}
