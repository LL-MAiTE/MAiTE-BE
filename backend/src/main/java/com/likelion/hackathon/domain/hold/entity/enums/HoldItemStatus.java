package com.likelion.hackathon.domain.hold.entity.enums;

public enum HoldItemStatus {
    UNRESOLVED,         // 미해결
    AWAITING_ANSWER,    // 답변대기
    CONFIRMED_IMMEDIATE,// 확정_즉시만족
    CONFIRMED_TIMEOUT,  // 확정_타임아웃 — 24~48h 무반응 자동 확정
    REOPENED,           // 재오픈됨
    NEEDS_REALTIME      // 실시간조율필요 — 재오픈 상한(2회) 도달 시 종결
}
