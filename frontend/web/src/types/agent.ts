export type AgentStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED'
export type AgentInputType = 'TASK' | 'ALARM' | 'FREE_TEXT'
export type AgentActionStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED'

export interface AgentAnalysis {
  defectLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  cause: string
  recommendedActions: string[]
  citations: string[]
  confidence: number
}

export interface AgentStep {
  id: string
  runId: string
  sessionId: string
  type: string
  message: string
  payload: Record<string, unknown>
  createdAt: string
}

export interface AgentRun {
  id: string
  sessionId: string
  status: AgentStatus
  startedAt: string
  completedAt?: string
  summary?: AgentAnalysis
  errorMessage?: string
  steps?: AgentStep[]
}

export interface AgentEvidence {
  id: string
  sessionId: string
  runId: string
  type: string
  sourceId?: string
  title: string
  content: string
  imageUrl?: string
  payload: Record<string, unknown>
  createdAt: string
}

export interface AgentAction {
  id: string
  sessionId: string
  runId: string
  type: 'CREATE_WORK_ORDER_DRAFT' | 'PUSH_NOTIFICATION' | string
  status: AgentActionStatus
  title: string
  description: string
  payload: Record<string, unknown>
  resultRef?: Record<string, unknown>
  createdAt: string
  updatedAt: string
}

export interface AgentSession {
  id: string
  title: string
  inputType: AgentInputType
  taskId?: string
  alarmId?: string
  prompt?: string
  status: AgentStatus
  createdById: string
  createdAt: string
  updatedAt: string
  runs?: AgentRun[]
  latestRun?: AgentRun
  evidence?: AgentEvidence[]
  actions?: AgentAction[]
  analysis?: AgentAnalysis
}

export interface CreateAgentSessionRequest {
  taskId?: string
  alarmId?: string
  prompt?: string
}

export interface AgentRealtimeEvent {
  type: string
  sessionId: string
  payload: AgentStep | AgentAction | Record<string, unknown>
  createdAt: string
}

export type AgentCaseStatus =
  | 'OPEN'
  | 'ANALYZING'
  | 'WAITING_HUMAN'
  | 'WAITING_APPROVAL'
  | 'ACTION_EXECUTING'
  | 'RESOLVED'
  | 'FAILED'
  | 'CLOSED'

export type AgentRunStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'WAITING_TOOL'
  | 'WAITING_HUMAN'
  | 'WAITING_APPROVAL'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'
  | 'TIMED_OUT'
  | 'STEP_LIMIT_REACHED'

export type AgentStepType =
  | 'RUN_STARTED'
  | 'TOOL_CALL_REQUESTED'
  | 'TOOL_CALL_STARTED'
  | 'TOOL_CALL_SUCCEEDED'
  | 'TOOL_CALL_FAILED'
  | 'EVIDENCE_ADDED'
  | 'LLM_ANALYZED'
  | 'ACTION_PROPOSED'
  | 'ACTION_APPROVED'
  | 'ACTION_REJECTED'
  | 'ACTION_STARTED'
  | 'ACTION_SUCCEEDED'
  | 'ACTION_FAILED'
  | 'RUN_FINISHED'
  | 'RUN_FAILED'

export type AuditedAgentActionStatus = 'PROPOSED' | 'APPROVED' | 'REJECTED' | 'EXECUTING' | 'SUCCEEDED' | 'FAILED' | 'EXPIRED' | 'CANCELLED'
export type AgentRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type AgentActionType = 'CREATE_WORK_ORDER_DRAFT' | 'PUSH_NOTIFICATION' | 'ACKNOWLEDGE_ALARM' | 'REQUEST_TASK_PAUSE' | 'CANCEL_TASK' | 'ROBOT_MANUAL_CONTROL'
export type AgentEvidenceSourceType = 'TASK' | 'ALARM' | 'WORK_ORDER' | 'VISION_RESULT' | 'OPERATOR_INPUT' | 'LLM_FALLBACK'
export type AgentPolicyDecision = 'AUTO_EXECUTE' | 'REQUIRE_APPROVAL' | 'DENY'

export interface AgentRunSummary {
  id: string
  runNumber: number
  status: AgentRunStatus
  reanalysisReason?: string
  startedAt?: string
  completedAt?: string
  errorCode?: string
  errorMessage?: string
  version: number
}

export interface AgentCaseSummary {
  id: string
  title: string
  goal: string
  taskId?: string
  alarmId?: string
  status: AgentCaseStatus
  priority: string
  createdAt: string
  updatedAt: string
  version: number
  latestRun?: AgentRunSummary | null
}

export interface AgentCaseDetail {
  item: AgentCaseSummary
  runs: AgentRunSummary[]
}

export interface AgentEvidenceReference {
  evidenceId: string
  role: string
  statement: string
}

export interface AuditedAgentConclusion {
  defectLevel: AgentRiskLevel
  cause: string
  recommendedActions: string[]
  evidenceReferences: AgentEvidenceReference[]
  confidence: number
}

export interface AuditedAgentStep {
  id: string
  sequenceNo: number
  type: AgentStepType
  summary: string
  detail?: Record<string, unknown>
  createdAt: string
}

export interface AuditedAgentToolCall {
  id: string
  stepNo: number
  toolName: string
  arguments: Record<string, unknown>
  status: 'REQUESTED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'TIMED_OUT' | 'CANCELLED'
  reason: string
  startedAt: string
  completedAt?: string
  durationMs?: number
  resultSummary?: string
  errorCode?: string
  errorMessage?: string
}

export interface AuditedAgentEvidence {
  id: string
  toolCallId?: string
  sourceType: AgentEvidenceSourceType
  sourceId?: string
  title: string
  summary: string
  contentType: string
  payload: Record<string, unknown>
  contentHash: string
  collectedAt: string
}

export interface AuditedAgentAction {
  id: string
  type: AgentActionType
  title: string
  reason: string
  riskLevel: AgentRiskLevel
  status: AuditedAgentActionStatus
  payload: Record<string, unknown>
  policyDecision: AgentPolicyDecision
  policyCode: string
  requiresApproval: boolean
  idempotencyKey: string
  approvedById?: string
  approvedAt?: string
  approvalComment?: string
  rejectedById?: string
  rejectedAt?: string
  rejectionComment?: string
  executionStartedAt?: string
  executionCompletedAt?: string
  result?: Record<string, unknown>
  errorCode?: string
  errorMessage?: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface AgentRunDetail {
  run: AgentRunSummary
  conclusion?: AuditedAgentConclusion
  steps: AuditedAgentStep[]
  toolCalls: AuditedAgentToolCall[]
  evidence: AuditedAgentEvidence[]
  actions: AuditedAgentAction[]
}

export interface CreateAgentCaseRequest {
  goal: string
  taskId?: string
  alarmId?: string
  priority?: string
  operatorNote?: string
}

export interface StartAgentRunRequest {
  reason: string
}

export interface AgentActionDecisionRequest {
  version: number
  comment: string
}

export interface AuditedAgentRealtimeEvent {
  eventId: string
  caseId: string
  runId: string
  type: AgentStepType
  sequenceNo: number
  summary: string
  occurredAt: string
}
