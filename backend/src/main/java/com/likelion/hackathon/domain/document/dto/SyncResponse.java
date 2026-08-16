package com.likelion.hackathon.domain.document.dto;

import java.util.List;

public record SyncResponse(int syncedCount, List<String> latestFiles) {}
