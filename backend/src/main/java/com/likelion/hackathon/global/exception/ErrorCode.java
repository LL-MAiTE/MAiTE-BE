package com.likelion.hackathon.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // Auth
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // Project
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."),
    NOT_PROJECT_MEMBER(HttpStatus.FORBIDDEN, "프로젝트 멤버가 아닙니다."),
    ALREADY_PROJECT_MEMBER(HttpStatus.CONFLICT, "이미 프로젝트 멤버입니다."),

    // Document
    SOURCE_CONNECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "연동 소스를 찾을 수 없습니다."),
    SOURCE_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."),
    DOCUMENT_NOT_IN_PROJECT(HttpStatus.BAD_REQUEST, "문서가 해당 프로젝트에 속하지 않습니다."),
    GITHUB_REPO_ACCESS_FAILED(HttpStatus.BAD_REQUEST, "GitHub 저장소에 접근할 수 없습니다. accessToken 또는 저장소 이름(owner/repo)을 확인하세요."),

    // Agenda
    AGENDA_NOT_FOUND(HttpStatus.NOT_FOUND, "회의를 찾을 수 없습니다."),
    AGENDA_REFERENCE_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "회의 참조 문서를 찾을 수 없습니다."),
    NO_REFERENCE_DOCUMENTS(HttpStatus.BAD_REQUEST, "최소 1개 이상의 참조 문서가 필요합니다."),
    POSITION_NOT_FOUND(HttpStatus.NOT_FOUND, "안건을 찾을 수 없습니다."),

    // Meeting
    MEETING_NOT_FOUND(HttpStatus.NOT_FOUND, "미팅을 찾을 수 없습니다."),
    TRANSCRIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "전사 텍스트를 찾을 수 없습니다."),
    MEETING_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "미팅 로그를 찾을 수 없습니다."),
    MEETING_POSITION_NOT_FOUND(HttpStatus.NOT_FOUND, "미팅 안건 스냅샷을 찾을 수 없습니다."),

    // Confirmation
    NUMBER_CONFIRMATION_NOT_FOUND(HttpStatus.NOT_FOUND, "숫자 확인 항목을 찾을 수 없습니다."),

    // Hold
    HOLD_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "보류 항목을 찾을 수 없습니다."),
    REOPEN_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "재오픈 횟수 상한(2회)에 도달했습니다."),

    // Notification
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),

    // Review
    REVIEW_ACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "검토 액션을 찾을 수 없습니다."),
    REQUIRED_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "필수 검토 항목을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
