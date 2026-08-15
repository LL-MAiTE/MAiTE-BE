package com.likelion.hackathon.domain.hold.entity.enums;

public enum CoordinationResult {
    ADJUSTABLE,     // 조율가능
    OUT_OF_RANGE    // 범위밖 — 자동으로 hold_item 생성
}
