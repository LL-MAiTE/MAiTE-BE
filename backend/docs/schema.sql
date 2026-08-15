-- ============================================================
-- AI 비동기 협업 대리 진행 서비스 — ERD v4 Schema
-- ============================================================

-- ============================================================
-- 1. 기반
-- ============================================================

CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    name        TEXT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE projects (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE project_members (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        TEXT NOT NULL CHECK (role IN ('답변작성자', '질문참여자', '팀관리자')),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (project_id, user_id)
);

-- ============================================================
-- 2. 문서연동 — 🔴 최상 (기능 1)
-- ============================================================

CREATE TABLE source_connections (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id              UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    type                    TEXT NOT NULL CHECK (type IN ('notion', 'git')),
    -- md 직접 업로드는 connection 없이 source_documents에 바로 저장 (connection_id = NULL)
    workspace_or_repo_name  TEXT,
    access_token            TEXT NOT NULL, -- 암호화 저장
    connected_by            UUID NOT NULL REFERENCES users(id),
    connected_at            TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE source_documents (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id       UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    connection_id    UUID REFERENCES source_connections(id) ON DELETE SET NULL, -- 직접 업로드면 NULL
    title            TEXT NOT NULL,
    path             TEXT, -- 예: docs/decisions/2026-08-pricing.md
    content          TEXT, -- 문서 원문 (길면 별도 스토리지 참조로 대체 가능)
    source_url       TEXT,
    is_core_context  BOOLEAN NOT NULL DEFAULT false, -- true면 회의 생성 시 파일 선택 목록에 항상 우선 노출
    last_modified_at TIMESTAMP,
    synced_at        TIMESTAMP
);

-- ============================================================
-- 3. 회의준비 — 🔴 최상 ~ 🟠 높음 (기능 1, 2, 3)
-- ============================================================

-- agendas = 회의
-- (테이블명은 agendas 유지, 실체는 "회의")
CREATE TABLE agendas (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id                   UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title                        TEXT NOT NULL,                -- 회의 이름
    purpose                      TEXT,                         -- 회의 목적
    counterpart_country          TEXT,
    counterpart_language         TEXT,
    culture_guide_enabled        BOOLEAN NOT NULL DEFAULT false,
    transcript_languages         JSONB,                        -- 최대 2개 (Agora 채널 제약)
    translation_source_languages JSONB,                        -- 최대 4개
    translation_target_languages JSONB,                        -- 최대 10개
    status                       TEXT NOT NULL DEFAULT '준비전'
                                     CHECK (status IN ('준비전', '준비중', '승인완료', '진행중', '종료')),
    created_by                   UUID NOT NULL REFERENCES users(id),
    created_at                   TIMESTAMP NOT NULL DEFAULT now()
);

-- agenda_reference_documents = 회의별 선택 파일
-- 회의 생성 시 사용자가 직접 선택하는 필수 단계 (최소 1개)
CREATE TABLE agenda_reference_documents (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agenda_id          UUID NOT NULL REFERENCES agendas(id) ON DELETE CASCADE,
    source_document_id UUID NOT NULL REFERENCES source_documents(id) ON DELETE CASCADE,
    added_by           TEXT NOT NULL DEFAULT 'user'
                           CHECK (added_by IN ('user', 'ai_suggested')),
    excluded           BOOLEAN NOT NULL DEFAULT false, -- 생성 후 사용자가 목록에서 제외했으면 true
    added_at           TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (agenda_id, source_document_id)
);

-- positions = 안건(항목)
-- 회의에 속한 개별 예상 질문-답변 단위
-- 버전관리 + AI 필드 자동판단 + 사용자 직접추가를 이 테이블 하나로 수용
CREATE TABLE positions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agenda_id          UUID NOT NULL REFERENCES agendas(id) ON DELETE CASCADE,
    topic              TEXT NOT NULL,         -- 같은 주제 판별 기준
    question_text      TEXT NOT NULL,
    generated_by       TEXT NOT NULL CHECK (generated_by IN ('ai_draft', 'user')),
    source_document_id UUID REFERENCES source_documents(id) ON DELETE SET NULL, -- 사용자 직접추가면 NULL 가능
    -- active_fields: AI가 질문 성격에 맞게 채운 필드 목록을 명시적으로 기록
    -- 예: ["preference", "concession_range"]
    -- 필드 값이 비어있는 것과 "이 질문엔 해당 없음"을 구분하기 위해 사용
    active_fields      JSONB,
    answer             TEXT,
    preference         TEXT,                 -- 선호안
    concession_range   TEXT,                 -- 양보 가능 범위
    dealbreaker        TEXT,                 -- 양보 불가 사항
    priority           INT,
    schedule_constraint TEXT,
    confidence_level   TEXT CHECK (confidence_level IN ('문서근거명확', '추정')), -- AI 초안일 때만 사용
    approval_status    TEXT NOT NULL DEFAULT '초안'
                           CHECK (approval_status IN ('초안', '수정후승인', '승인', '반려', '보류')),
    version            INT NOT NULL DEFAULT 1,
    is_latest          BOOLEAN NOT NULL DEFAULT true,
    supersedes_id      UUID REFERENCES positions(id) ON DELETE SET NULL, -- 이전 버전 참조
    approved_by        UUID REFERENCES users(id),
    approved_at        TIMESTAMP
);

-- ============================================================
-- 4. 미팅 실행 — 🟠 높음 (기능 4, 5, 6)
-- ============================================================

CREATE TABLE meetings (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agenda_id                UUID NOT NULL REFERENCES agendas(id) ON DELETE CASCADE,
    started_at               TIMESTAMP,      -- 미팅 세션 시작 시각
    status                   TEXT NOT NULL DEFAULT '진행중'
                                 CHECK (status IN ('진행중', '후속답변대기', '종료')),
    -- 후속답변대기: 실시간 음성 세션은 끝났지만 보류 항목이 비동기로 남아있는 상태
    disclosure_completed_at  TIMESTAMP,      -- AI 대리진행 고지 완료 시각
    voice_session_ended_at   TIMESTAMP,      -- 실시간 음성 세션 종료 시각 (전체 미팅 종료와는 별개)
    closed_at                TIMESTAMP       -- 모든 보류 항목이 종결되어 미팅 전체가 자동 종료된 시각
);

-- 미팅 시작 시 승인된 안건의 스냅샷을 고정
-- meetings.started_at 시점의 is_latest=true, approval_status=승인 인 positions를 여기에 기록
-- 이후 positions가 수정되더라도 해당 미팅에서 실제 사용된 버전을 추적 가능
CREATE TABLE meeting_positions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id  UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    position_id UUID NOT NULL REFERENCES positions(id),
    version     INT NOT NULL,   -- 스냅샷 시점의 positions.version
    snapped_at  TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (meeting_id, position_id)
);

CREATE TABLE transcripts (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id     UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    speaker_label  TEXT NOT NULL, -- 화자A / 화자B(AI) 등, 최대 3명 (Agora 제약)
    language       TEXT NOT NULL,
    text           TEXT NOT NULL,
    spoken_at      TIMESTAMP NOT NULL,
    confidence     FLOAT,         -- 음성인식 신뢰도
    vtt_segment_ref TEXT          -- .vtt export용 구간 참조
);

CREATE TABLE meeting_logs (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id                UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    transcript_id             UUID NOT NULL REFERENCES transcripts(id),
    matched_meeting_position_id UUID REFERENCES meeting_positions(id), -- 매칭 안 되면 NULL → 보류. meeting_positions를 참조해 스냅샷 버전 추적 가능
    translated_text           TEXT,
    audio_url                 TEXT,
    contains_critical_number  BOOLEAN NOT NULL DEFAULT false, -- true면 number_confirmations 트리거
    limitation_note           TEXT,   -- 세부 조건 차이 시 제한사항
    delivered_at              TIMESTAMP,
    status                    TEXT NOT NULL DEFAULT '확인대기'
                                  CHECK (status IN ('전달됨', '보류', '확인대기'))
);

-- ============================================================
-- 5. 신뢰성 — 🟠 높음 (기능 6, 숫자 확인)
-- ============================================================

CREATE TABLE number_confirmations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_log_id  UUID NOT NULL REFERENCES meeting_logs(id) ON DELETE CASCADE,
    detected_value  TEXT NOT NULL,  -- 감지된 숫자/단위 (예: "8/28")
    popup_shown_at  TIMESTAMP NOT NULL,
    response_type   TEXT CHECK (response_type IN ('O', 'X', '미응답_자동보류')),
    responded_at    TIMESTAMP,
    resulted_in_hold BOOLEAN NOT NULL DEFAULT false  -- X 또는 미응답이면 true
);

-- ============================================================
-- 6. 협의/보류 — 🟡 중간 (기능 7, 8)
-- ============================================================

CREATE TABLE coordination_records (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id       UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    position_id      UUID NOT NULL REFERENCES positions(id),
    proposed_content TEXT,
    result           TEXT NOT NULL CHECK (result IN ('조율가능', '범위밖')),
    next_action      TEXT,
    -- result='범위밖'이면 hold_item이 자동 생성됨. 어떤 hold_item으로 연결됐는지 역추적 가능하도록 참조
    resulting_hold_item_id UUID REFERENCES hold_items(id)
);

-- hold_items: 보류 항목 전체 라이프사이클 관리
-- 발생 → 답변 → 비동기 전달 → 확정/재오픈 → 필요시 종결
-- 핑퐁 방지: 재오픈 횟수 상한(최대 2회) + 타임아웃 기반 자동확정
CREATE TABLE hold_items (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id                  UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    meeting_log_id              UUID REFERENCES meeting_logs(id),
    number_confirmation_id      UUID REFERENCES number_confirmations(id),
    origin                      TEXT NOT NULL CHECK (origin IN ('회의중_발생', '사후_재보류')),
    -- 회의중_발생: 실시간 미팅 중 생긴 보류
    -- 사후_재보류: 사후검토에서 재보류로 만들어진 건 (기능 8-1)
    reason                      TEXT,  -- 예: "핵심의도 불일치", "숫자확인 미응답", "사후 재보류"
    related_transcript_id       UUID REFERENCES transcripts(id),
    answer_text                 TEXT,  -- 답변 작성자가 작성한 후속 답변
    answered_by                 UUID REFERENCES users(id),
    answered_at                 TIMESTAMP,
    delivered_to_counterpart_at TIMESTAMP, -- 비동기 전달 시각 — 여기서부터 24~48시간 타임아웃 카운트 시작
    reopen_count                INT NOT NULL DEFAULT 0,  -- 최대 2
    status                      TEXT NOT NULL DEFAULT '미해결'
                                    CHECK (status IN (
                                        '미해결',
                                        '답변대기',
                                        '확정_즉시만족',
                                        '확정_타임아웃',
                                        '재오픈됨',
                                        '실시간조율필요'
                                    )),
    created_at                  TIMESTAMP NOT NULL DEFAULT now(),
    resolved_at                 TIMESTAMP  -- 최종 확정 또는 실시간조율필요로 종결된 시각
);

-- ============================================================
-- 7. 사후검토 — 🟢 낮음 (기능 9, 10)
-- ============================================================

CREATE TABLE review_actions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_log_id        UUID NOT NULL REFERENCES meeting_logs(id) ON DELETE CASCADE,
    reviewer_id           UUID NOT NULL REFERENCES users(id),
    action                TEXT NOT NULL CHECK (action IN ('승인', '수정', '철회', '재보류')),
    -- 재보류 선택 시 hold_items에 새 레코드 생성 (origin=사후_재보류), 기능 8-1
    resulting_hold_item_id UUID REFERENCES hold_items(id),  -- action=재보류일 때만 값 존재
    note                  TEXT,
    created_at            TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE required_reviews (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_log_id  UUID NOT NULL REFERENCES meeting_logs(id) ON DELETE CASCADE,
    designated_by   UUID NOT NULL REFERENCES users(id),
    designated_at   TIMESTAMP NOT NULL DEFAULT now(),
    status          TEXT NOT NULL DEFAULT '조건부합의'
                        CHECK (status IN ('조건부합의', '확정', '수정', '철회')),
    reviewed_by     UUID REFERENCES users(id),
    reviewed_at     TIMESTAMP
);

-- ============================================================
-- 8. 알림 — 🟠 중간
-- ============================================================

-- 비동기 후속 처리(기능 8)에서 "답변 작성자에게 전달", "상대방에게 전달" 등
-- 실제 전달 수단(이메일/푸시/인앱)과 무관하게 알림 이력을 여기서 관리.
-- 타임아웃 배치(hold_items 자동확정)도 이 테이블 기준으로 트리거 여부 판단 가능.
CREATE TABLE notifications (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type         TEXT NOT NULL CHECK (type IN (
                     '보류항목_전달',        -- 답변 작성자 → 상대방: 보류 답변 전달됨
                     '보류항목_수신',        -- 상대방 → 답변 작성자: 새 보류 항목 생김
                     '재오픈_요청',          -- 상대방이 재오픈 눌렀을 때 답변 작성자에게
                     '자동확정',             -- 24~48h 타임아웃으로 자동 확정됨
                     '실시간조율필요',       -- 재오픈 상한 도달, 실시간 미팅 권고
                     '미팅_종료'             -- 모든 보류 항목 종결, 미팅 자동 종료
                 )),
    reference_id UUID,          -- 알림 대상 레코드 id (hold_item_id, meeting_id 등)
    reference_type TEXT,        -- 'hold_item' | 'meeting' 등
    is_read      BOOLEAN NOT NULL DEFAULT false,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

-- ============================================================
-- 9. 운영관리 — 🟢 낮음 (백로그)
-- ============================================================

CREATE TABLE data_retention_policies (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id     UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    data_type      TEXT NOT NULL,
    retention_days INT NOT NULL,
    deletion_policy TEXT,
    updated_by     UUID NOT NULL REFERENCES users(id),
    updated_at     TIMESTAMP NOT NULL DEFAULT now()
);
