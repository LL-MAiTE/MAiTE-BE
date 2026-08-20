package com.likelion.hackathon.domain.project.entity.enums;

public enum ProjectMemberStatus {
    PENDING,  // 초대는 갔지만 아직 수락 안 함 — 이 프로젝트가 초대받은 사람의 "내 프로젝트" 목록엔 안 뜬다.
    ACTIVE,   // 수락 완료(또는 프로젝트를 직접 만든 사람) — 정상 멤버.
    DECLINED  // 초대를 거절함.
}
