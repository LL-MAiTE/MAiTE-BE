/**
 * 백엔드 API 클라이언트
 *
 * 사용법:
 *   import { api } from './api'
 *
 *   // 로그인
 *   const { token } = await api.auth.login({ email, password })
 *
 *   // 미팅 시작
 *   const info = await api.meetings.start(meetingId)
 *   await client.join(info.agoraAppId, info.agoraChannel, info.agoraToken, null)
 */

import type {
  ApiResponse,
  AuthResponse,
  SignupRequest,
  LoginRequest,
  User,
  Project,
  ProjectMember,
  ProjectMemberRole,
  SourceDocument,
  SourceDocumentDetail,
  ConnectionType,
  SourceConnection,
  SyncResponse,
  Agenda,
  CreateAgendaRequest,
  Position,
  CreatePositionRequest,
  ApprovePositionRequest,
  Meeting,
  MeetingStartResponse,
  MeetingPosition,
  ChannelInfo,
  MeetingLog,
  Transcript,
  CreateTranscriptRequest,
  HoldItem,
  Notification,
  NumberConfirmation,
  NumberConfirmationResponseType,
  RequiredReview,
  ReviewAction,
  ReviewActionResult,
  CoordinationRecord,
  CoordinationResult,
} from './shared'

// ── 설정 ──────────────────────────────────────────────────────────────────

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'

let _token: string | null = null

export function setToken(token: string) {
  _token = token
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(_token ? { Authorization: `Bearer ${_token}` } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  const json: ApiResponse<T> = await res.json()

  if (!json.success) {
    throw new Error(json.message ?? `API error: ${res.status}`)
  }

  return json.data
}

// ── API ───────────────────────────────────────────────────────────────────

export const api = {

  // ── 인증 ────────────────────────────────────────────────────────────────

  auth: {
    /** POST /auth/signup */
    signup: (body: SignupRequest) =>
      request<AuthResponse>('POST', '/auth/signup', body),

    /** POST /auth/login */
    login: async (body: LoginRequest) => {
      const data = await request<AuthResponse>('POST', '/auth/login', body)
      setToken(data.token)
      return data
    },
  },

  // ── 사용자 ────────────────────────────────────────────────────────────────

  users: {
    /** GET /users?email= — 이메일로 사용자 검색 (멤버 초대 시 userId 조회용) */
    searchByEmail: (email: string) =>
      request<User>('GET', `/users?email=${encodeURIComponent(email)}`),
  },

  // ── 프로젝트 ──────────────────────────────────────────────────────────

  projects: {
    /** POST /projects */
    create: (name: string) =>
      request<Project>('POST', '/projects', { name }),

    /** GET /projects */
    list: () =>
      request<Project[]>('GET', '/projects'),

    /** GET /projects/:id */
    get: (id: string) =>
      request<Project>('GET', `/projects/${id}`),

    /** GET /projects/:id/members */
    listMembers: (projectId: string) =>
      request<ProjectMember[]>('GET', `/projects/${projectId}/members`),

    /** POST /projects/:id/members */
    addMember: (projectId: string, userId: string, role: ProjectMemberRole) =>
      request<ProjectMember>('POST', `/projects/${projectId}/members`, { userId, role }),
  },

  // ── 문서 ────────────────────────────────────────────────────────────────

  documents: {
    /** POST /projects/:id/documents */
    upload: (projectId: string, title: string, content: string) =>
      request<SourceDocument>('POST', `/projects/${projectId}/documents`, { title, content }),

    /** GET /projects/:id/documents — 목록만 반환, content(본문)는 포함되지 않음 */
    list: (projectId: string) =>
      request<SourceDocument[]>('GET', `/projects/${projectId}/documents`),

    /** PATCH /documents/:id — 핵심 맥락 문서 지정 등 */
    update: (documentId: string, isCoreContext: boolean) =>
      request<SourceDocument>('PATCH', `/documents/${documentId}`, { isCoreContext }),

    /** GET /documents/:id — 단건 조회, content(본문) 포함 */
    get: (documentId: string) =>
      request<SourceDocumentDetail>('GET', `/documents/${documentId}`),
  },

  // ── 문서 연동 (Notion/Git) ─────────────────────────────────────────────

  connections: {
    /**
     * POST /projects/:id/connections — Notion/Git 연동 등록.
     * workspaceOrRepoName: GIT은 "owner/repo", NOTION은 루트로 삼을 페이지 ID
     * (그 페이지에 Notion 통합을 미리 공유해둬야 함).
     */
    create: (projectId: string, type: ConnectionType, workspaceOrRepoName: string, accessToken: string) =>
      request<SourceConnection>('POST', `/projects/${projectId}/connections`, {
        type,
        workspaceOrRepoName,
        accessToken,
      }),

    /**
     * POST /connections/:id/sync
     * GIT: 저장소에서 문서(.md/.mdx/.txt/.rst) 동기화.
     * NOTION: 루트 페이지부터 하위 페이지까지 재귀 동기화 (최대 30개, 데이터베이스/워크스페이스 전체 검색은 미지원).
     */
    sync: (connectionId: string) =>
      request<SyncResponse>('POST', `/connections/${connectionId}/sync`),
  },

  // ── 안건 ────────────────────────────────────────────────────────────────

  agendas: {
    /** GET /projects/:id/agendas */
    listByProject: (projectId: string) =>
      request<Agenda[]>('GET', `/projects/${projectId}/agendas`),

    /** GET /agendas/:id */
    get: (id: string) =>
      request<Agenda>('GET', `/agendas/${id}`),

    /** POST /agendas */
    create: (body: CreateAgendaRequest) =>
      request<Agenda>('POST', '/agendas', body),

    /** POST /agendas/:id/reference-documents */
    addReferenceDocs: (agendaId: string, sourceDocumentIds: string[]) =>
      request<unknown>('POST', `/agendas/${agendaId}/reference-documents`, { sourceDocumentIds }),

    /** POST /agendas/:id/draft-positions — OpenAI 호출됨 */
    draftPositions: (agendaId: string) =>
      request<Position[]>('POST', `/agendas/${agendaId}/draft-positions`),

    /** GET /agendas/:id/positions */
    listPositions: (agendaId: string) =>
      request<Position[]>('GET', `/agendas/${agendaId}/positions`),

    /** POST /agendas/:id/positions */
    createPosition: (agendaId: string, body: CreatePositionRequest) =>
      request<Position>('POST', `/agendas/${agendaId}/positions`, body),
  },

  // ── 안건 (Position) ───────────────────────────────────────────────────

  positions: {
    /** POST /positions/:id/approve */
    approve: (id: string, body: ApprovePositionRequest) =>
      request<Position>('POST', `/positions/${id}/approve`, body),

    /** POST /positions/:id/revise */
    revise: (id: string, body: Partial<CreatePositionRequest> & ApprovePositionRequest) =>
      request<Position>('POST', `/positions/${id}/revise`, body),

    /** POST /positions/:id/reject */
    reject: (id: string) =>
      request<Position>('POST', `/positions/${id}/reject`),

    /** DELETE /positions/:id */
    delete: (id: string) =>
      request<{ success: boolean }>('DELETE', `/positions/${id}`),
  },

  // ── 미팅 ────────────────────────────────────────────────────────────────

  meetings: {
    /** POST /agendas/:id/meetings */
    create: (agendaId: string) =>
      request<Meeting>('POST', `/agendas/${agendaId}/meetings`),

    /** GET /meetings/:id */
    get: (id: string) =>
      request<Meeting>('GET', `/meetings/${id}`),

    /**
     * POST /meetings/:id/start
     * AI 에이전트를 채널에 입장시키고 Agora RTC 연결 정보를 반환.
     *
     * 반환값으로 Agora SDK 연결:
     *   const info = await api.meetings.start(meetingId)
     *   await client.join(info.agoraAppId, info.agoraChannel, info.agoraToken, null)
     *
     * AI 에이전트 UID = 100 (예약) — user.uid === 100 이면 AI
     */
    start: (id: string) =>
      request<MeetingStartResponse>('POST', `/meetings/${id}/start`),

    /**
     * POST /meetings/:id/end
     * 음성 세션 종료 + AI 에이전트 퇴장 + 전체 대화를 분석해 안건별 합의 결과 저장.
     * 이후 meetings.positions()를 다시 호출하면 resultStatus/agreedValue가 채워져 있음.
     */
    end: (id: string) =>
      request<{ success: boolean }>('POST', `/meetings/${id}/end`),

    /** GET /meetings/:id/channel-info */
    channelInfo: (id: string) =>
      request<ChannelInfo>('GET', `/meetings/${id}/channel-info`),

    /**
     * GET /meetings/:id/positions
     * 미팅 시작 시 스냅샷된 승인 안건 목록. 회의 종료 후엔 각 항목의
     * resultStatus('AGREED' 등)/agreedValue로 실제 합의 결과도 함께 내려옴.
     */
    positions: (id: string) =>
      request<MeetingPosition[]>('GET', `/meetings/${id}/positions`),

    /** GET /meetings/:id/meeting-logs */
    logs: (id: string) =>
      request<MeetingLog[]>('GET', `/meetings/${id}/meeting-logs`),

    /** POST /meetings/:id/meeting-logs */
    createLog: (id: string, transcriptId: string) =>
      request<MeetingLog>('POST', `/meetings/${id}/meeting-logs`, { transcriptId }),

    /** POST /meetings/:id/transcripts */
    createTranscript: (id: string, body: CreateTranscriptRequest) =>
      request<Transcript>('POST', `/meetings/${id}/transcripts`, body),
  },

  // ── 보류 항목 ──────────────────────────────────────────────────────────

  holdItems: {
    /** GET /meetings/:id/hold-items */
    list: (meetingId: string) =>
      request<HoldItem[]>('GET', `/meetings/${meetingId}/hold-items`),

    /** POST /hold-items/:id/answer */
    answer: (id: string, answerText: string) =>
      request<HoldItem>('POST', `/hold-items/${id}/answer`, { answerText }),

    /** POST /hold-items/:id/reopen */
    reopen: (id: string) =>
      request<HoldItem>('POST', `/hold-items/${id}/reopen`),
  },

  // ── 숫자 확인 팝업 ────────────────────────────────────────────────────────

  numberConfirmations: {
    /**
     * GET /meeting-logs/:id/number-confirmation
     * 답변이 전달될 때(미팅 로그 생성 / 실시간 AI 응답 저장) 금액·날짜·수량·퍼센트 등
     * 핵심 수치가 감지되면 서버가 자동으로 생성해둔 숫자확인을 조회. 없으면 404.
     * 프론트는 meetingLog.containsCriticalNumber === true일 때 이걸 불러서 팝업 표시.
     */
    get: (meetingLogId: string) =>
      request<NumberConfirmation>('GET', `/meeting-logs/${meetingLogId}/number-confirmation`),

    /**
     * POST /meeting-logs/:id/number-confirmation
     * 숫자/단위 포함 답변 시 확인 팝업 수동 트리거 (테스트·보완용, 실제로는 자동 생성됨)
     */
    create: (meetingLogId: string, detectedValue: string) =>
      request<NumberConfirmation>('POST', `/meeting-logs/${meetingLogId}/number-confirmation`, { detectedValue }),

    /**
     * PATCH /number-confirmations/:id
     * O/X 응답 또는 타임아웃 처리.
     * AUTO_HOLD 선택 시 hold_item이 자동 생성됨.
     */
    respond: (id: string, responseType: NumberConfirmationResponseType) =>
      request<NumberConfirmation>('PATCH', `/number-confirmations/${id}`, { responseType }),
  },

  // ── 필수 검토 ─────────────────────────────────────────────────────────────

  requiredReviews: {
    /**
     * POST /required-reviews?meetingLogId=
     * 사라(질문 참여자)가 특정 meeting-log를 필수검토로 지정.
     * 재현이 PATCH로 확인해야 확정 상태로 전환됨.
     */
    create: (meetingLogId: string) =>
      request<RequiredReview>('POST', `/required-reviews?meetingLogId=${meetingLogId}`),

    /** PATCH /required-reviews/:id — 재현(답변 작성자)이 확인 처리 → status: "CONFIRMED" */
    confirm: (id: string) =>
      request<RequiredReview>('PATCH', `/required-reviews/${id}`),
  },

  // ── 사후 검토 ─────────────────────────────────────────────────────────────

  reviewActions: {
    /**
     * POST /meeting-logs/:id/review-actions
     * 재현이 사후검토 화면에서 각 meeting-log에 대해 처리.
     * RE_HELD 선택 시 hold_item이 자동 생성됨.
     */
    create: (meetingLogId: string, action: ReviewAction, note?: string) =>
      request<ReviewActionResult>('POST', `/meeting-logs/${meetingLogId}/review-actions`, { action, note }),
  },

  // ── 협의 조율 기록 ────────────────────────────────────────────────────────

  coordinationRecords: {
    /**
     * POST /coordination-records?meetingId=
     * 승인 범위 내 대안 조율 결과 저장.
     * OUT_OF_RANGE 선택 시 hold_item이 자동 생성됨.
     */
    create: (
      meetingId: string,
      body: { positionId: string; proposedContent: string; result: CoordinationResult; nextAction?: string }
    ) =>
      request<CoordinationRecord>('POST', `/coordination-records?meetingId=${meetingId}`, body),
  },

  // ── 알림 ────────────────────────────────────────────────────────────────

  notifications: {
    /** GET /notifications */
    list: () =>
      request<Notification[]>('GET', '/notifications'),

    /** PATCH /notifications/:id/read */
    read: (id: string) =>
      request<Notification>('PATCH', `/notifications/${id}/read`),

    /** PATCH /notifications/read-all */
    readAll: () =>
      request<{ updatedCount: number }>('PATCH', '/notifications/read-all'),
  },
}
