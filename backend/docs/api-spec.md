# API 명세서

> **범례**: ✅ 구현 완료 · ❌ 미구현  
> 모든 요청(회원가입·로그인 제외)은 `Authorization: Bearer {token}` 헤더 필요

---

## 인증

| 구현 | Method | Path | 설명 | Request Body | Response Body |
|------|--------|------|------|-------------|---------------|
| ✅ | POST | `/auth/signup` | 회원가입 | `{ email, password, name }` | `{ token, user: { id, email, name } }` |
| ✅ | POST | `/auth/login` | 로그인 | `{ email, password }` | `{ token, user: { id, email, name } }` |

---

## 사용자

| 구현 | Method | Path | 설명 | Request Body | Response Body |
|------|--------|------|------|-------------|---------------|
| ✅ | GET | `/users?email=` | 이메일로 사용자 검색 (멤버 초대 시 userId 조회용) | - | `{ id, email, name }` |

---

## 프로젝트 / 멤버

| 구현 | Method | Path | 설명 | Request Body | Response Body |
|------|--------|------|------|-------------|---------------|
| ✅ | POST | `/projects` | 프로젝트 생성 (생성자는 자동으로 TEAM_MANAGER) | `{ name }` | `{ id, name, createdAt }` |
| ✅ | GET | `/projects` | 내가 속한 프로젝트 목록 | - | `project[]` |
| ✅ | GET | `/projects/:id` | 프로젝트 단건 조회 | - | `{ id, name, createdAt }` |
| ✅ | POST | `/projects/:id/members` | 멤버 초대 및 역할 부여 (`role`: ANSWERER/QUESTIONER/TEAM_MANAGER) | `{ userId, role }` | `{ id, userId, userName, userEmail, role }` |
| ✅ | GET | `/projects/:id/members` | 프로젝트 멤버 목록 조회 | - | `projectMember[]` |

---

## 문서 연동

| 구현 | Method | Path | 설명 | Request Body | Response Body |
|------|--------|------|------|-------------|---------------|
| ✅ | POST | `/projects/:id/connections` | Notion/Git 연동 등록 (`type`: NOTION/GIT) | `{ type, workspaceOrRepoName, accessToken }` | `{ id, type, workspaceOrRepoName, connectedBy, connectedAt }` |
| ✅ | POST | `/connections/:id/sync` | 연동 소스에서 문서 동기화. **GIT은 실제 GitHub API로 동작** (기본 브랜치의 .md/.mdx/.txt/.rst 파일, 최대 30개). **NOTION은 아직 stub** | - | `{ syncedCount, latestFiles: string[] }` |
| ✅ | POST | `/projects/:id/documents` | md 파일 직접 업로드 | `{ title, content }` | `{ id, projectId, title, isCoreContext, lastModifiedAt, ... }` |
| ✅ | PATCH | `/documents/:id` | 핵심 맥락 md 지정 등 문서 속성 수정 | `{ isCoreContext }` | `{ id, projectId, title, isCoreContext, ... }` |
| ✅ | GET | `/projects/:id/documents` | 프로젝트 문서 목록 (핵심맥락 우선 정렬). **content(본문)는 포함되지 않음** — 필요 시 단건 조회 API 추가 필요 | - | `sourceDocument[]` |

---

## 회의 준비

| 구현 | Method | Path | 설명 | Request Body | Response Body |
|------|--------|------|------|-------------|---------------|
| ✅ | GET | `/projects/:id/agendas` | 프로젝트 내 회의 목록 | - | `agenda[]` |
| ✅ | GET | `/agendas/:id` | 회의 단건 조회 | - | `{ id, projectId, title, purpose, status, ... }` |
| ✅ | POST | `/agendas` | 회의 생성 | `{ projectId, title, purpose, counterpartCountry, counterpartLanguage, transcriptLanguages, translationSourceLanguages, translationTargetLanguages }` | `{ id, title, status: "READY", ... }` |
| ✅ | POST | `/agendas/:id/reference-documents` | 참조 문서 선택 (최소 1개 필수) | `{ sourceDocumentIds: uuid[] }` | `agendaReferenceDocument[]` |
| ✅ | PATCH | `/agenda-reference-documents/:id` | 참조 문서 제외 처리 | `{ excluded: true }` | `{ id, excluded, ... }` |
| ✅ | POST | `/agendas/:id/draft-positions` | AI 안건 초안 생성 (현재 stub, 문서 기반 샘플 3개 반환) | - | `position[]` (generatedBy: "AI_DRAFT") |
| ✅ | GET | `/agendas/:id/positions` | 안건 목록 조회 (최신 버전만) | - | `position[]` |
| ✅ | POST | `/agendas/:id/positions` | 안건 직접 추가 | `{ topic, questionText, answer, preference, concessionRange, dealbreaker, priority, scheduleConstraint }` | `{ id, generatedBy: "USER", version: 1, ... }` |
| ✅ | POST | `/positions/:id/approve` | 안건 승인 | `{ approvalStatus }` | `{ id, approvalStatus, approvedBy, approvedAt, ... }` |
| ✅ | POST | `/positions/:id/revise` | 안건 수정 후 승인 (구 레코드 isLatest=false, 새 레코드 생성) | `{ approvalStatus, topic?, questionText?, answer?, preference?, concessionRange?, dealbreaker?, priority?, scheduleConstraint? }` | `{ id(새 id), version+1, isLatest: true, ... }` |
| ✅ | POST | `/positions/:id/reject` | 안건 반려 | - | `{ id, approvalStatus: "REJECTED", ... }` |
| ✅ | DELETE | `/positions/:id` | 안건 삭제 (매칭 대상에서 제외) | - | `{ success: true }` |

---

## 미팅 실행

| 구현 | Method | Path | 설명 | Request Body | Response Body |
|------|--------|------|------|-------------|---------------|
| ✅ | POST | `/agendas/:id/meetings` | 미팅 세션 생성 (승인 안건 스냅샷 → meeting_positions 기록) | - | `{ id, agendaId, status: "IN_PROGRESS", ... }` |
| ✅ | GET | `/meetings/:id` | 미팅 단건 조회 | - | `{ id, agendaId, status, startedAt, disclosureCompletedAt, agoraAgentId, ... }` |
| ✅ | GET | `/meetings/:id/channel-info` | Agora 채널 정보 조회 (프론트가 채널 입장 전 호출) | - | `{ appId, channelName, token }` |
| ✅ | GET | `/meetings/:id/positions` | 미팅 시작 시 스냅샷된 승인 안건 목록 조회. 회의 종료 후엔 `resultStatus`/`agreedValue`로 실제 합의 결과도 함께 내려옴 (AI 매칭 / 화면 표시용) | - | `meetingPosition[]` |
| ✅ | POST | `/meetings/:id/start` | 미팅 시작 — 안건+문서 기반 시스템 프롬프트 생성 후 Agora Conversational AI 에이전트를 채널에 입장시킴 | - | `{ disclosureCompletedAt, agoraAppId, agoraChannel, agoraToken, agoraAgentUid: 100 }` |
| ✅ | POST | `/meetings/:id/end` | 음성 세션 종료 + AI 에이전트 퇴장 + **전체 대화를 안건 목록과 함께 OpenAI로 분석해 안건별 합의 결과(`meeting_positions.resultStatus`/`agreedValue`) 저장** | - | `{ success: true }` |
| ✅ | POST | `/meetings/:id/transcripts` | 수동 전사 텍스트 저장 (테스트·보완용) | `{ speakerLabel, language, text, spokenAt, confidence }` | `{ id, meetingId, speakerLabel, language, text, spokenAt, confidence }` |
| ✅ | POST | `/meetings/:id/meeting-logs` | 전사 ↔ 승인 안건 매칭 + 상태 기록 | `{ transcriptId }` | `{ id, matchedMeetingPositionId, translatedText, status: "DELIVERED"/"ON_HOLD" }` |
| ✅ | GET | `/meetings/:id/meeting-logs` | 미팅 로그 조회 (사후검토용) | - | `meetingLog[]` |

---

## 신뢰성 — 숫자 확인

| 구현 | Method | Path | 설명 | Request Body | Response Body |
|------|--------|------|------|-------------|---------------|
| ✅ | POST | `/meeting-logs/:id/number-confirmation` | 숫자/단위 포함 답변 확인 팝업 트리거 | `{ detectedValue }` | `{ id, meetingLogId, detectedValue, popupShownAt }` |
| ✅ | PATCH | `/number-confirmations/:id` | O/X 응답 또는 타임아웃 처리 (AUTO_HOLD 시 hold_item 자동 생성) | `{ responseType }` (`CONFIRMED`\|`REJECTED`\|`AUTO_HOLD`) | `{ id, responseType, respondedAt, resultedInHold }` |

---

## 알림

| 구현 | Method | Path | 설명 | Request Body | Response Body |
|------|--------|------|------|-------------|---------------|
| ✅ | GET | `/notifications` | 내 알림 목록 (최신순) | - | `{ id, type, referenceId, referenceType, isRead, createdAt }[]` |
| ✅ | PATCH | `/notifications/:id/read` | 알림 읽음 처리 | - | `{ id, isRead: true, ... }` |
| ✅ | PATCH | `/notifications/read-all` | 전체 읽음 처리 | - | `{ updatedCount }` |

---

## 협의 / 보류

| 구현 | Method | Path | 설명 | Request Body | Response Body |
|------|--------|------|------|-------------|---------------|
| ✅ | POST | `/coordination-records?meetingId=` | 승인 범위 내 대안 조율 결과 저장 (OUT_OF_RANGE 시 hold_item 자동 생성) | `{ positionId, proposedContent, result, nextAction }` | `{ id, meetingId, positionId, result, resultingHoldItemId, ... }` |
| ✅ | GET | `/meetings/:id/hold-items` | 보류 항목 목록 조회 | - | `holdItem[]` |
| ✅ | POST | `/hold-items/:id/answer` | 답변 작성자 후속 답변 → 비동기 전달 | `{ answerText }` | `{ id, answerText, answeredAt, deliveredToCounterpartAt, status, ... }` |
| ✅ | POST | `/hold-items/:id/reopen` | 상대방 재오픈 (최대 2회, 초과 시 NEEDS_REALTIME 자동 처리) | - | `{ id, reopenCount, status, ... }` |
| ✅ | PATCH | `/hold-items/:id` | 배치용 상태 변경 (타임아웃 자동확정 / 실시간조율필요) | `{ status }` (`CONFIRMED_TIMEOUT`\|`NEEDS_REALTIME`) | `{ id, status, resolvedAt, ... }` |

---

## 사후 검토

| 구현 | Method | Path | 설명 | Request Body | Response Body |
|------|--------|------|------|-------------|---------------|
| ✅ | POST | `/meeting-logs/:id/review-actions` | 승인/수정/철회/재보류 (RE_HELD 시 hold_item 자동 생성) | `{ action, note? }` (`APPROVED`\|`REVISED`\|`WITHDRAWN`\|`RE_HELD`) | `{ id, meetingLogId, action, resultingHoldItemId, note, ... }` |
| ✅ | POST | `/required-reviews?meetingLogId=` | 질문 참여자가 필수 검토 지정 | - | `{ id, meetingLogId, designatedBy, status: "CONDITIONAL", ... }` |
| ✅ | PATCH | `/required-reviews/:id` | 답변 작성자 확인 처리 (조건부합의 → 확정) | - | `{ id, status: "CONFIRMED", reviewedBy, reviewedAt }` |

---

## 공통 응답 형식

```json
// 성공
{ "success": true, "data": { ... } }

// 실패
{ "success": false, "message": "에러 메시지" }
```

## 공통 에러 코드

| HTTP | 상황 |
|------|------|
| 400 | 요청 필드 누락 / 유효성 오류 |
| 401 | 토큰 없음 또는 만료 |
| 403 | 프로젝트 멤버 아님 / 권한 없음 |
| 404 | 리소스 없음 |
| 409 | 이메일 중복 / 이미 멤버 |
| 500 | 서버 오류 |

## 내부 웹훅 (Agora → 백엔드, 인증 불필요)

| 구현 | Method | Path | 설명 | Request Body | Response Body |
|------|--------|------|------|-------------|---------------|
| ✅ | POST | `/agora/callback` | Agora Conversational AI 대화 턴 수신 — 사람 발화 + AI 응답을 transcript/meeting_log로 자동 저장 | `{ channel, agentId, turnId, userTranscription, agentResponse, startMs, endMs }` | `200 OK` |

> Agora 콘솔 Webhooks에 `https://{서버주소}/agora/callback` 등록 필요 (개발 시 ngrok 사용)

---

## Agora RTC 연동 (프론트엔드 참고)

### 미팅 시작 후 채널 입장 흐름

```
POST /meetings/:id/start
→ { agoraAppId, agoraChannel, agoraToken, agoraAgentUid: 100 }

// Agora RTC SDK (npm: agora-rtc-sdk-ng, 테스트 버전: 4.21.0)
const client = AgoraRTC.createClient({ mode: 'rtc', codec: 'vp8' })
await client.join(agoraAppId, agoraChannel, agoraToken, null)
await client.publish([cameraTrack, micTrack])
```

- `agoraToken`: UID=0(랜덤 배정)으로 발급 → `client.join()` 4번째 인자 `null`
- AI 에이전트 UID = **100** (예약됨) — `user.uid === 100`이면 AI
- AI는 오디오만 발행, 비디오 없음

### 실시간 전사 (stream-message)

Agora ConvAI가 채널 내 모든 참가자에게 전사 결과를 데이터 스트림으로 전송.

**수신 포맷**: `{hex_id}|{chunk_index}|{total_chunks}|{base64_json}`  
→ 같은 `hex_id`의 청크를 순서대로 합쳐서 base64 디코딩 → JSON 파싱

**JSON 필드**:

| 필드 | 값 |
|------|----|
| `object` | `"assistant.transcription"` (AI 발화) \| `"user.transcription"` (사용자 발화) |
| `text` | 전사 텍스트 |
| `final` | `true` = 확정 / `false` = 중간 결과 |

```javascript
const chunkBuf = {}
client.on('stream-message', (uid, data) => {
  const parts = new TextDecoder().decode(data).split('|')
  if (parts.length < 4) return
  const [msgId, idxStr, totalStr, b64] = parts
  const idx = parseInt(idxStr) - 1, total = parseInt(totalStr)
  if (!chunkBuf[msgId]) chunkBuf[msgId] = new Array(total).fill(null)
  chunkBuf[msgId][idx] = b64
  if (chunkBuf[msgId].some(c => c === null)) return
  const msg = JSON.parse(atob(chunkBuf[msgId].join('')))
  delete chunkBuf[msgId]
  // msg.object, msg.text, msg.final
})
```

---

## 미구현 / 추후 연동 필요

| 항목 | 설명 |
|------|------|
| 발화 의미 매칭 | OpenAI 호출로 매칭, 실패 시 키워드 매칭으로 fallback |
| Notion 실제 동기화 | 현재 stub — 연동 등록만 되고 문서를 가져오지 않음 (Git은 구현 완료) |
| 문서 단건 조회(content 포함) | 목록 API는 content 미포함 — 필요 시 단건 조회 API 추가 필요 |
| 24~48h 타임아웃 자동확정 | `PATCH /hold-items/:id` 호출하는 스케줄러 별도 구현 필요 |
| LLM 응답 실패 처리 | Agora ConvAI → OpenAI LLM 호출 실패 시 failure_message 출력 — 원인 미확정 (rate limit 의심) |
