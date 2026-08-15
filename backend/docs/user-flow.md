# 유저플로우 및 API 명세

두 역할(답변 작성자 / 질문 참여자) 기준으로 정리. 각 단계 옆에 관련 API·테이블을 같이 적어서, ERD → 유저플로우 → API 명세서로 바로 이어지게 했습니다.

> **버전**: v4 (비동기 후속 처리, 핑퐁 방지) 기준. 회의 생성 시 파일 선택을 필수 단계로 변경.
> 계층 구조: **프로젝트** (문서를 쌓는 단위) → **회의** (이름·목적 입력 + 파일 선택) → **안건** (회의 안의 개별 질문-답변)

---

## 1. 답변 작성자 플로우 (회의 전 → 자는 동안 → 회의 후)

```mermaid
flowchart TD
    A[로그인/가입] --> B[프로젝트 진입]
    B --> C{프로젝트에 자료 업로드 되어있나?}
    C -- 아니오 --> C1[Notion/Git 연동 또는 md 업로드 - 프로젝트 단위로 계속 누적]
    C1 --> D
    C -- 예 --> D[새 회의 생성 - 이름 + 목적 입력]
    D --> E[프로젝트 문서 목록에서 관련 파일 선택 - 핵심맥락md 기본노출, 필수 최소 1개]
    E --> F[선택한 파일 범위 안에서 AI가 예상 안건과 답변 초안 생성 - 안건별 필요 필드만 자동 판단]
    F --> F0{생성된 근거 문서 조정하고 싶은가?}
    F0 -- 예 --> F0B[문서 추가/제외 조정]
    F0B --> F0C[AI가 조정된 범위로 안건 재생성]
    F0C --> G
    F0 -- 아니오 --> G[안건별 승인/수정/반려]
    G --> G1{AI가 놓친 질문이 있나?}
    G1 -- 예 --> G2[답변 작성자가 안건 직접 추가]
    G2 --> G
    G1 -- 아니오 --> H[최종 승인 완료]
    H --> I((미팅 대리진행 가능 상태))
    I -.본인은 잠듦.-> J[AI가 미팅 진행]
    J --> K[실시간 음성 세션 종료 알림 받음]
    K --> L[화자별 대화 전문 + 음성구간 검토]
    L --> M{전달된 내용이 잘못 매칭된 것 같은가?}
    M -- 예 --> M1[재보류 - 기능 8-1]
    M -- 아니오 --> M2[승인/수정/철회]
    M1 --> N
    M2 --> O[필수검토 항목 확인: 조건부합의 -> 확정]
    L --> N[보류 항목들 확인 후 답변 작성 → 상대에게 비동기 전달]
    N --> P((후속처리는 3번 플로우로 이어짐))
```

| 단계 | 관련 API (예시) | 관련 테이블 |
|------|----------------|-------------|
| 문서 업로드 (프로젝트 단위, 계속 누적) | POST /projects/:id/connections, POST /connections/:id/sync | source_connections, source_documents |
| **회의 생성 (이름 + 목적)** | **POST /agendas** | **agendas** |
| **관련 파일 선택 (필수, 최소 1개)** | **POST /agendas/:id/reference-documents** | **agenda_reference_documents (added_by=user)** |
| 선택 범위 내 AI 안건 생성 (안건별 필요 필드만 자동 판단) | POST /agendas/:id/draft-positions | positions (generated_by=ai_draft, active_fields로 채워진 필드 기록) |
| 근거 문서 추가/제외 조정 (생성 후) | POST /agendas/:id/reference-documents, PATCH /agenda-reference-documents/:id | agenda_reference_documents (added_by=user 또는 excluded=true) |
| 안건 승인/수정/반려 | PATCH /positions/:id | positions (approval_status, version) |
| 안건 직접 추가 | POST /agendas/:id/positions | positions (generated_by=user, topic 신규 생성, version=1) |
| 사후검토 목록 조회 | GET /meetings/:id/meeting-logs | meeting_logs, transcripts |
| 전달내용 승인/수정/철회 | POST /meeting-logs/:id/review-actions | review_actions |
| 재보류 (전달된 내용을 다시 보류로) | POST /meeting-logs/:id/review-actions (action=재보류) | review_actions, hold_items (origin=사후_재보류) |
| 보류 항목 목록 조회 | GET /meetings/:id/hold-items | hold_items |
| 보류 항목 답변 작성 → 비동기 전달 | POST /hold-items/:id/answer | hold_items (answer_text, answered_at, delivered_to_counterpart_at) |
| 필수검토 확인 | PATCH /required-reviews/:id | required_reviews |

---

## 2. 질문 참여자(상대방) 플로우 (회의 중)

```mermaid
flowchart TD
    A[음성 미팅 참여] --> B[AI 대리진행 고지 듣기]
    B --> C[질문/논의 발화]
    C --> D{승인된 답변과 의도 일치?}
    D -- 일치 --> D1{숫자/단위 포함된 답변인가?}
    D -- 일부 조건 다름 --> F[답변 + 제한사항 함께 전달]
    D -- 불일치/범위밖 --> G[보류로 기록, 회의 중엔 언급 안 됨]
    D1 -- 아니오 --> E[통역된 답변 음성으로 전달]
    D1 -- 예 --> Q["통역 답변 전달 + 숫자 확인 팝업: '8/28이 맞습니까? O/X'"]
    Q --> R{10초 내 응답?}
    R -- O 응답 --> E
    R -- X 응답 --> G
    R -- 미응답 10초 경과 --> S[자동 보류 처리 - 자동 O 아님]
    S --> G
    E --> H{대안/일정 제안?}
    F --> H
    H -- 예 --> I[승인범위 내 조율 결과 받음]
    H -- 아니오 --> J[다음 질문으로]
    I --> K{이 항목 중요해서 꼭 확인받고 싶음?}
    K -- 예 --> L[필수검토로 지정 -> 조건부합의 표시]
    K -- 아니오 --> J
    L --> J
    J --> M[실시간 음성 세션 종료]
```

| 단계 | 관련 API (예시) | 관련 테이블 |
|------|----------------|-------------|
| 미팅 참여/고지 | POST /meetings/:id/start | meetings |
| 발화 인식 | (Agora 웹훅) POST /transcripts | transcripts |
| 답변 매칭·전달 | (내부 로직) POST /meeting-logs | meeting_logs, positions |
| 숫자/단위 감지 및 확인 팝업 노출 | POST /meeting-logs/:id/number-confirmation | number_confirmations |
| 숫자 확인 응답 (O/X/타임아웃) | PATCH /number-confirmations/:id | number_confirmations |
| 대안 조율 | POST /coordination-records | coordination_records |
| 필수검토 지정 | POST /required-reviews | required_reviews |

---

## 3. 보류 항목 비동기 후속 처리 (양쪽 역할 공통)

회의 종료 후 며칠에 걸쳐 진행될 수 있습니다.

**핵심 원칙**: 기본은 1회 답변으로 종결. 상대방의 "만족합니다" 확인을 매번 요구하지 않고, 재오픈은 능동적 행동으로만 발생. 재오픈은 항목당 최대 2회, 그 이상은 실시간 미팅 권고로 종결.

```mermaid
flowchart TD
    A[답변 작성자가 보류 항목에 답변] --> B[상대방에게 비동기 전달, 상태: 답변대기]
    B --> C{상대방 반응은?}
    C -- 아무 반응 없음 --> D{24~48시간 경과?}
    D -- 아니오 --> C
    D -- 예 --> E[자동 확정 - 확정_타임아웃]
    C -- 만족/추가 반응 없이 넘어감 --> F[확정 - 확정_즉시만족]
    C -- 재오픈 요청 --> G{reopen_count < 2?}
    G -- 예 --> H[reopen_count +1, 상태: 재오픈됨]
    H --> A
    G -- 아니오 --> I[실시간 미팅 권고 - 실시간조율필요로 종결]
    E --> J{이 미팅의 모든 보류항목이 종결됐나?}
    F --> J
    I --> J
    J -- 아니오 --> K[다른 보류항목은 계속 대기]
    J -- 예 --> L[미팅 상태 자동 -> 종료]
```

| 단계 | 관련 API (예시) | 관련 테이블 |
|------|----------------|-------------|
| 답변 작성자 답변 → 비동기 전달 | POST /hold-items/:id/answer | hold_items |
| 상대방 재오픈 | POST /hold-items/:id/reopen | hold_items (reopen_count +1, status=재오픈됨) |
| 타임아웃 자동 확정 (배치/스케줄러) | (내부 배치) PATCH /hold-items/:id | hold_items (status=확정_타임아웃) |
| 재오픈 상한 도달 → 실시간조율필요 | (내부 로직) PATCH /hold-items/:id | hold_items (status=실시간조율필요) |
| 미팅 전체 종료 여부 체크 (배치/트리거) | (내부 로직) PATCH /meetings/:id | meetings (status=종료, closed_at) |

**비즈니스 규칙**

- 미팅은 실시간 음성 세션이 끝나도 곧바로 "종료"되지 않고 "후속답변대기"로 전환됨.
- 모든 `hold_items`가 종결 상태(확정_즉시만족 / 확정_타임아웃 / 실시간조율필요)에 도달하면, 미팅은 자동으로 "종료"로 전환됨. 양측이 각자 종료 버튼을 누르는 절차는 없음 — 그 확인 과정 자체가 핑퐁을 만들기 때문.
- 재오픈은 원래 회의 중 발생한 보류(`origin=회의중_발생`)뿐 아니라, 사후 재보류(`origin=사후_재보류`)로 생긴 항목에도 동일하게 적용됨.

---

> **참고**: 다이어그램은 GitHub에서 `.md` 파일로 열면 Mermaid가 자동 렌더링됩니다.
