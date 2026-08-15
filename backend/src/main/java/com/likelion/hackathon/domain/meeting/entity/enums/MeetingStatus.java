package com.likelion.hackathon.domain.meeting.entity.enums;

public enum MeetingStatus {
    IN_PROGRESS,        // 진행중
    PENDING_FOLLOWUP,   // 후속답변대기 — 음성 세션 종료, 보류 항목 비동기 처리 중
    CLOSED              // 종료 — 모든 보류 항목 종결 시 자동 전환
}
