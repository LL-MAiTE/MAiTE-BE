# API 명세서

ERD v4 / 유저플로우 v5 기준. MVP 12개 테이블(기반/문서연동/회의준비/미팅실행/신뢰성) 우선순위 엔드포인트를 먼저 상세히 정리했고, 중간/낮음 우선순위는 뒤에 간단히 붙였습니다. 프론트/AI 팀과 이 문서를 기준으로 필드명 맞추면 됩니다.

---

## 인증

| Method | Path | 설명 | Request | Response |
|--------|------|------|---------|----------|
| POST | /auth/signup | 계정 가입 | `{ email, password, name }` | `{ id, email }` |
| POST | /auth/login | 로그인 | `{ email, password }` | `{ token, user }` |

---

## 프로젝트 / 멤버 (기반)

| Method | Path | 설명 | Request | Response |
|--------|------|------|---------|----------|
| POST | /projects | 프로젝트 생성 | `{ name }` | `{ id, name, createdAt }` |
| GET | /projects | 내 프로젝트 목록 | - | `project[]` |
| GET | /projects/:id | 프로젝트 단건 조회 | - | `project` |
| POST | /projects/:id/members | 멤버 초대/역할 부여 | `{ userId, role }` | `projectMember` |

---

## 문서연동 — 🔴 최상 (기능 1)

| Method | Path | 설명 | Request | Response |
|--------|------|------|---------|----------|
| POST | /projects/:id/connections | Notion/Git 연동 등록 | `{ type, workspaceOrRepoName, accessToken }` | `sourceConnection` |
| POST | /connections/:id/sync | 연동 소스에서 문서 동기화 | - | `{ syncedCount, latestFiles: [...] }` |
| POST | /projects/:id/documents | md 파일 직접 업로드 | `{ title, content }` 또는 `file` | `sourceDocument` |
| PATCH | /documents/:id | 핵심 맥락 md 지정 등 | `{ isCoreContext }` | `sourceDocument` |
| GET | /projects/:id/documents | 프로젝트에 쌓인 문서 목록 (회의 생성 시 파일 선택 화면에서 호출) | - | `sourceDocument[]` |

---

## 회의 준비 — 🔴 최상 ~ 🟠 높음 (기능 1, 2, 3)

| Method | Path | 설명 | Request | Response |
|--------|------|------|---------|----------|
| GET | /projects/:id/agendas | 프로젝트 내 회의 목록 | - | `agenda[]` |
| GET | /agendas/:id | 회의 단건 조회 | - | `agenda` |
| POST | /agendas | 회의 생성 (이름+목적) | `{ projectId, title, purpose, counterpartCountry, counterpartLanguage, transcriptLanguages, translationSourceLanguages, translationTargetLanguages }` | `agenda` (status=준비전) |
| POST | /agendas/:id/reference-documents | **관련 파일 선택 (필수, 최소 1개)** | `{ sourceDocumentIds: [...] }` | `agendaReferenceDocument[]` |
| PATCH | /agenda-reference-documents/:id | 문서 제외 처리 | `{ excluded: true }` | `agendaReferenceDocument` |
| POST | /agendas/:id/draft-positions | 선택 파일 범위 내 AI 안건 초안 생성 | - | `position[]` (generatedBy=ai_draft, activeFields 포함) |
| GET | /agendas/:id/positions | 안건(항목) 목록 조회 | - | `position[]` |
| POST | /agendas/:id/positions | 안건 직접 추가 | `{ topic, questionText, answer, preference, concessionRange, dealbreaker, priority, scheduleConstraint }` | `position` (generatedBy=user, version=1) |
| POST | /positions/:id/approve | 안건 승인 (수정 없이) | `{ approvalStatus }` | `position` |
| POST | /positions/:id/revise | 안건 수정 후 승인 — 기존 레코드는 isLatest=false, 새 version 레코드 생성. **응답의 id는 새로 생성된 레코드의 id** (호출 시 넘긴 `:id`와 다름) | `{ approvalStatus, ...수정필드 }` | `position` (새 id, version +1, isLatest=true) |
| POST | /positions/:id/reject | 안건 반려 | `{ approvalStatus }` | `position` |
| DELETE | /positions/:id | 안건 삭제 | - | `{ status: 삭제됨 }` (매칭 대상에서 제외) |

---

## 미팅 실행 — 🟠 높음 (기능 4, 5, 6)

| Method | Path | 설명 | Request | Response |
|--------|------|------|---------|----------|
| POST | /agendas/:id/meetings | 미팅 세션 생성 (승인된 안건 스냅샷 확정 → meeting_positions 기록) | - | `meeting` (status=진행중) |
| GET | /meetings/:id | 미팅 단건 조회 (현재 status 포함) | - | `meeting` |
| POST | /meetings/:id/start | 미팅 시작 + AI 대리진행 고지 트리거 | - | `{ disclosureCompletedAt }` |
| POST | /meetings/:id/transcripts | (Agora 웹훅) 실시간 전사 텍스트 수신 | `{ speakerLabel, language, text, spokenAt, confidence }` | `transcript` |
| POST | /meetings/:id/meeting-logs | (내부 로직) 발화 ↔ 승인 안건 매칭 + 통역 전달 | `{ transcriptId }` | `meetingLog` (matchedMeetingPositionId, translatedText, status) |
| GET | /meetings/:id/meeting-logs | 미팅 로그 조회 (사후검토용) | - | `meetingLog[]` |

---

## 신뢰성 — 🟠 높음 (기능 6, 숫자 확인)

| Method | Path | 설명 | Request | Response |
|--------|------|------|---------|----------|
| POST | /meeting-logs/:id/number-confirmation | 숫자/단위 포함 답변 → 확인 팝업 트리거 | `{ detectedValue }` | `numberConfirmation` (popupShownAt) |
| PATCH | /number-confirmations/:id | O/X 응답 또는 타임아웃 처리 | `{ responseType }` | `numberConfirmation` (미응답 10초 경과 시 자동 responseType=미응답_자동보류) |

---

## 알림 — 🟠 중간

| Method | Path | 설명 | Request | Response |
|--------|------|------|---------|----------|
| GET | /notifications | 내 알림 목록 | - | `notification[]` |
| PATCH | /notifications/:id/read | 알림 읽음 처리 | - | `notification` |
| PATCH | /notifications/read-all | 전체 읽음 처리 | - | `{ updatedCount }` |

> 알림 발생 시점: 보류 항목 전달, 재오픈 요청, 자동확정, 실시간조율필요, 미팅 자동 종료. 실제 전달 수단(이메일/푸시/인앱)은 `notifications` 테이블 기반으로 확장 가능.

---

## 협의/보류 — 🟡 중간 (기능 7, 8, 8-1)

| Method | Path | 설명 |
|--------|------|------|
| POST | /coordination-records | 승인범위 내 대안 조율 결과 저장 |
| GET | /meetings/:id/hold-items | 보류 항목 목록 조회 |
| POST | /hold-items/:id/answer | 답변 작성자 후속 답변 → 비동기 전달 (deliveredToCounterpartAt 기록, 이때부터 24~48h 타임아웃 카운트) |
| POST | /hold-items/:id/reopen | 상대방 재오픈 (reopenCount +1, 최대 2회) |
| PATCH | /hold-items/:id | 내부 배치: 타임아웃 자동확정 / 재오픈 상한 도달 시 실시간조율필요 처리 |

---

## 사후검토 — 🟢 낮음 (기능 9, 10, 8-1)

| Method | Path | 설명 |
|--------|------|------|
| POST | /meeting-logs/:id/review-actions | 승인/수정/철회/**재보류** (재보류 시 holdItems 새 레코드 자동 생성) |
| POST | /required-reviews | 질문 참여자가 필수검토 지정 |
| PATCH | /required-reviews/:id | 답변 작성자 확인 처리 (조건부합의 → 확정) |

---

## 운영관리 — 🟢 낮음 (백로그)

| Method | Path | 설명 |
|--------|------|------|
| PATCH | /projects/:id/retention-policy | 데이터 보관기간/삭제정책 설정 |

---

## 인증/권한 공통 규칙

- 모든 요청은 `Authorization: Bearer {token}` 필요 (회원가입/로그인 제외)
- `PATCH /positions/:id`, `POST /agendas/:id/reference-documents` 등 승인 관련 액션은 **해당 안건의 답변 작성자 본인만** 호출 가능
- `Agora 웹훅` 계열(`/transcripts`, 내부 매칭 로직)은 별도 웹훅 시크릿 검증 필요

---

## 다음에 할 일

1. 이 문서 그대로 팀 전체(프론트/AI)에 공유해서 필드명 이견 있는지 확인
2. Request/Response 예시 값까지 채워서 Postman 컬렉션 또는 Swagger로 옮기기 (FastAPI면 자동 생성됨)
3. MVP 그룹(인증 → 문서연동 → 회의준비 → 미팅실행 → 신뢰성) 순서로 실제 구현 착수
