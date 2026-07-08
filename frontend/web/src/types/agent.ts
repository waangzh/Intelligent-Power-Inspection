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
