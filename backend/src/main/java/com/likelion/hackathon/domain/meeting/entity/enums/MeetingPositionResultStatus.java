package com.likelion.hackathon.domain.meeting.entity.enums;

public enum MeetingPositionResultStatus {
    NOT_DISCUSSED,       // 회의에서 언급되지 않음
    AGREED,              // 승인 범위(concessionRange) 내에서 합의됨
    OUT_OF_RANGE_AGREED, // 딜브레이커를 벗어나 합의됨 — 사람 확인 필요
    NOT_AGREED           // 논의는 됐으나 결론 없음(보류 등)
}
