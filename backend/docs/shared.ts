/**
 * 백엔드 API 공유 타입 정의
 * Base URL: http://localhost:8080 (배포 시 환경변수로 교체)
 * 인증: Authorization: Bearer {token} (로그인/회원가입 제외 모든 요청)
 */

// ── 공통 ──────────────────────────────────────────────────────────────────

export interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

// ── 인증 ──────────────────────────────────────────────────────────────────

export interface SignupRequest {
  email: string
  password: string
  name: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface User {
  id: string
  email: string
  name: string
}

export interface AuthResponse {
  token: string
  user: User
}

// ── 프로젝트 ───────────────────────────────────────────────────────────────

export interface Project {
  id: string
  name: string
  createdAt: string
}

export type ProjectMemberRole = 'ANSWERER' | 'QUESTIONER' | 'TEAM_MANAGER'

export interface ProjectMember {
  id: string
  userId: string
  userName: string
  userEmail: string
  role: ProjectMemberRole
}

// ── 문서 ──────────────────────────────────────────────────────────────────

export interface SourceDocument {
  id: string
  projectId: string
  connectionId: string | null
  title: string
  path: string | null
  sourceUrl: string | null
  isCoreContext: boolean
  lastModifiedAt: string
  syncedAt: string | null
}

/** GET /documents/:id 전용 — 목록과 달리 본문(content) 포함 */
export interface SourceDocumentDetail extends SourceDocument {
  content: string | null
}

// ── 문서 연동 (Notion/Git) ───────────────────────────────────────────────

export type ConnectionType = 'NOTION' | 'GIT'

export interface SourceConnection {
  id: string
  type: ConnectionType
  /** GIT: "owner/repo" 형식. NOTION: 루트로 삼을 페이지 ID (그 페이지에 미리 integration 공유 필요) */
  workspaceOrRepoName: string
  connectedBy: string
  connectedAt: string
}

export interface SyncResponse {
  syncedCount: number
  latestFiles: string[]
}

// ── 안건 (Agenda / Position) ───────────────────────────────────────────────

export type AgendaStatus = 'READY' | 'IN_PROGRESS' | 'COMPLETED'
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REVISED_APPROVED' | 'REJECTED'
export type PositionGeneratedBy = 'AI_DRAFT' | 'USER'

export interface Agenda {
  id: string
  projectId: string
  title: string
  purpose: string
  counterpartCountry: string
  counterpartLanguage: string
  transcriptLanguages: string[]
  status: AgendaStatus
  createdAt: string
}

export interface CreateAgendaRequest {
  projectId: string
  title: string
  purpose: string
  counterpartCountry: string
  counterpartLanguage: string          // 예: "ko-KR"
  transcriptLanguages: string[]        // 예: ["ko-KR"]
  translationSourceLanguages?: string[]
  translationTargetLanguages?: string[]
}

/**
 * DOCUMENT_BASED: 참조 문서에 명시된 내용에 근거함
 * ESTIMATED: 문서에 직접 근거 없이 일반적인 협상 관행 등으로 추정함 — 검토 시 더 꼼꼼히 볼 것
 */
export type ConfidenceLevel = 'DOCUMENT_BASED' | 'ESTIMATED'

export interface Position {
  id: string
  agendaId: string
  topic: string
  questionText: string
  generatedBy: PositionGeneratedBy
  sourceDocumentId?: string
  activeFields: string[]
  answer?: string
  preference?: string
  concessionRange?: string
  dealbreaker?: string
  priority?: number
  scheduleConstraint?: string
  /** AI_DRAFT일 때만 의미 있음 (사용자 직접 추가 안건은 null) */
  confidenceLevel?: ConfidenceLevel
  approvalStatus: ApprovalStatus
  version: number
  isLatest: boolean
  supersedesId?: string
  approvedBy?: string
  approvedAt?: string
}

export interface CreatePositionRequest {
  topic: string
  questionText: string
  answer: string
  preference: string
  concessionRange: string
  dealbreaker: string
  priority: number
  scheduleConstraint?: string
}

export interface ApprovePositionRequest {
  approvalStatus: 'APPROVED' | 'REVISED_APPROVED' | 'REJECTED'
}

// ── 미팅 ──────────────────────────────────────────────────────────────────

export type MeetingStatus = 'IN_PROGRESS' | 'VOICE_ENDED' | 'COMPLETED'

export interface Meeting {
  id: string
  agendaId: string
  status: MeetingStatus
  startedAt: string
  disclosureCompletedAt?: string
  agoraAgentId?: string
}

/** POST /meetings/:id/start 응답 */
export interface MeetingStartResponse {
  disclosureCompletedAt: string
  agoraAppId: string
  agoraChannel: string    // meetingId와 동일
  agoraToken: string      // Agora RTC 입장 토큰 (UID=0 기반, client.join 4번째 인자는 null)
  agoraAgentUid: 100      // AI 에이전트 UID (예약됨)
}

/** GET /meetings/:id/channel-info 응답 */
export interface ChannelInfo {
  appId: string
  channelName: string
  token: string
}

// ── 미팅 안건 스냅샷 ──────────────────────────────────────────────────────

/**
 * NOT_DISCUSSED: 회의에서 언급 안 됨
 * AGREED: 승인된 양보 범위(concessionRange) 내에서 합의됨
 * OUT_OF_RANGE_AGREED: 딜브레이커를 벗어나 합의됨 — 사람 확인 필요
 * NOT_AGREED: 논의는 됐으나 결론 없음
 *
 * ⚠️ OpenAI 판단 결과라 가끔 부정확함(예: 범위 내 값인데 OUT_OF_RANGE_AGREED로 오판).
 *    화면엔 참고용으로 노출하되 최종 확정은 사람이 하는 걸 전제로 설계됨.
 */
export type MeetingPositionResultStatus =
  | 'NOT_DISCUSSED'
  | 'AGREED'
  | 'OUT_OF_RANGE_AGREED'
  | 'NOT_AGREED'

export interface MeetingPosition {
  id: string
  positionId: string
  topic: string
  questionText: string
  answer: string
  preference: string
  concessionRange: string
  dealbreaker: string
  priority: number
  scheduleConstraint?: string
  snappedVersion: number
  snappedAt: string
  /** 회의 종료(POST /meetings/:id/end) 시 대화 전체를 분석해 채워짐 */
  resultStatus: MeetingPositionResultStatus
  /** 사람이 읽을 합의 결과 요약, 예: "8월 17일까지 납품하기로 합의". 없으면 null */
  agreedValue: string | null
  resolvedAt: string | null
}

// ── 전사 / 미팅 로그 ──────────────────────────────────────────────────────

export interface Transcript {
  id: string
  meetingId: string
  speakerLabel: string
  language: string
  text: string
  spokenAt: string
  confidence?: number
}

export interface CreateTranscriptRequest {
  speakerLabel: string
  language: string
  text: string
  spokenAt: string
  confidence?: number
}

export type MeetingLogStatus = 'DELIVERED' | 'ON_HOLD'

export interface MeetingLog {
  id: string
  meetingId: string
  transcriptId: string
  matchedMeetingPositionId?: string
  /** 전달된 답변 원문 */
  translatedText?: string
  /** 원문을 상대방 언어(agenda.counterpartLanguage)로 번역한 자막. 번역 실패/대상언어 없음 시 없음 */
  translatedCaption?: string
  /** true면 숫자확인 팝업 대상 — GET /meeting-logs/:id/number-confirmation 로 조회 */
  containsCriticalNumber: boolean
  limitationNote?: string
  deliveredAt?: string
  status: MeetingLogStatus
}

// ── 보류 항목 ─────────────────────────────────────────────────────────────

export type HoldItemStatus =
  | 'PENDING'
  | 'ANSWERED'
  | 'REOPENED'
  | 'CONFIRMED_TIMEOUT'
  | 'NEEDS_REALTIME'

export interface HoldItem {
  id: string
  meetingId: string
  positionId: string
  status: HoldItemStatus
  answerText?: string
  answeredAt?: string
  reopenCount: number
  resolvedAt?: string
}

// ── Agora 실시간 전사 (stream-message) ────────────────────────────────────
//
// 수신 포맷: "{hex_id}|{chunk_index}|{total_chunks}|{base64_json}"
// 같은 hex_id 청크를 모아 base64 디코딩 → JSON 파싱
//
// 사용 예시:
//   const chunkBuf: Record<string, (string | null)[]> = {}
//   client.on('stream-message', (uid: number, data: Uint8Array) => {
//     const parts = new TextDecoder().decode(data).split('|')
//     if (parts.length < 4) return
//     const [msgId, idxStr, totalStr, b64] = parts
//     const idx = parseInt(idxStr) - 1
//     const total = parseInt(totalStr)
//     if (!chunkBuf[msgId]) chunkBuf[msgId] = new Array(total).fill(null)
//     chunkBuf[msgId][idx] = b64
//     if (chunkBuf[msgId].some(c => c === null)) return
//     const msg: AgoraStreamMessage = JSON.parse(atob(chunkBuf[msgId].join('')))
//     delete chunkBuf[msgId]
//   })

export type AgoraStreamMessageObject =
  | 'assistant.transcription'   // AI 발화
  | 'user.transcription'        // 사용자 발화
  | 'message.state'             // silent/speaking 상태 (표시 불필요)

export interface AgoraStreamMessage {
  object: AgoraStreamMessageObject
  text: string
  final: boolean          // true = 확정 텍스트, false = 중간 결과
  turn_id?: number
  ts_ms?: number
}

// ── 숫자 확인 ─────────────────────────────────────────────────────────────

export type NumberConfirmationResponseType = 'CONFIRMED' | 'REJECTED' | 'AUTO_HOLD'

export interface NumberConfirmation {
  id: string
  meetingLogId: string
  detectedValue: string
  popupShownAt: string
  responseType?: NumberConfirmationResponseType
  respondedAt?: string
  resultedInHold: boolean
}

// ── 필수 검토 ─────────────────────────────────────────────────────────────

export type RequiredReviewStatus = 'CONDITIONAL' | 'CONFIRMED'

export interface RequiredReview {
  id: string
  meetingLogId: string
  designatedBy: string
  status: RequiredReviewStatus
  reviewedBy?: string
  reviewedAt?: string
}

// ── 사후 검토 액션 ────────────────────────────────────────────────────────

export type ReviewAction = 'APPROVED' | 'REVISED' | 'WITHDRAWN' | 'RE_HELD'

export interface ReviewActionResult {
  id: string
  meetingLogId: string
  action: ReviewAction
  resultingHoldItemId?: string
  note?: string
  createdAt: string
}

// ── 협의 조율 ─────────────────────────────────────────────────────────────

export type CoordinationResult = 'AGREED' | 'OUT_OF_RANGE' | 'DEFERRED'

export interface CoordinationRecord {
  id: string
  meetingId: string
  positionId: string
  proposedContent: string
  result: CoordinationResult
  nextAction?: string
  resultingHoldItemId?: string
  createdAt: string
}

// ── 알림 ──────────────────────────────────────────────────────────────────

export interface Notification {
  id: string
  type: string
  referenceId: string
  referenceType: string
  isRead: boolean
  createdAt: string
}
