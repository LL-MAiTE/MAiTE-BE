package com.likelion.hackathon.domain.notification.entity.enums;

public enum NotificationType {
    HOLD_DELIVERED,     // 보류항목_전달 — 답변 작성자 후속 답변이 상대방에게 전달됨
    HOLD_RECEIVED,      // 보류항목_수신 — 새 보류 항목 발생, 답변 작성자에게
    REOPEN_REQUESTED,   // 재오픈_요청 — 상대방이 재오픈, 답변 작성자에게
    AUTO_CONFIRMED,     // 자동확정 — 24~48h 타임아웃으로 자동 확정
    NEEDS_REALTIME,     // 실시간조율필요 — 재오픈 상한 도달, 양측에게
    MEETING_CLOSED      // 미팅_종료 — 모든 보류 항목 종결, 양측에게
}
