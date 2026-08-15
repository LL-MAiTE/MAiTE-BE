package com.likelion.hackathon.domain.review.entity.enums;

public enum ReviewActionType {
    APPROVED,   // 승인
    REVISED,    // 수정
    WITHDRAWN,  // 철회
    RE_HELD     // 재보류 (기능 8-1) — hold_items에 새 레코드 생성
}
