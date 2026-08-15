package com.likelion.hackathon.domain.confirmation.entity.enums;

public enum ConfirmationResponseType {
    CONFIRMED,      // O — 숫자 확인 완료
    REJECTED,       // X — 숫자 불일치
    AUTO_HOLD       // 미응답_자동보류 — 10초 내 미응답 시 자동 보류
}
