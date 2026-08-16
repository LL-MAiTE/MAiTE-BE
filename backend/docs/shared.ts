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

// ── 문서 연동 (Notion/Git) ───────────────────────────────────────────────

/** GIT은 실제 동기화 구현됨. NOTION은 아직 stub (연동 등록만 되고 문서를 가져오지는 않음) */
export type ConnectionType = 'NOTION' | 'GIT'

export interface SourceConnection {
  id: string
  type: ConnectionType
  /** GIT일 때 "owner/repo" 형식 */
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

export interface Position {
  id: string
  agendaId: string
  topic: string
  questionText: string
  answer: string
  preference: string
  concessionRange: string
  dealbreaker: string
  priority: number
  scheduleConstraint?: string
  approvalStatus: ApprovalStatus
  generatedBy: PositionGeneratedBy
  version: number
  isLatest: boolean
  approvedBy?: string
  approvedAt?: string
  createdAt: string
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
  translatedText?: string
  transcriptText?: string
  speakerLabel?: string
  status: MeetingLogStatus
  createdAt: string
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
