export type RobotConnectionStatus =
  | 'CONNECTED'
  | 'OFFLINE'
  | 'UNKNOWN'
  | 'BRIDGE_UNREACHABLE'
  | 'BRIDGE_UNCONFIGURED'

export interface RobotHeartbeatSource {
  name: string
  bridgeConfigured: boolean
}

export interface RobotHeartbeatStatus {
  robotId: string
  serialNo?: string | null
  displayName?: string | null
  connectionStatus: RobotConnectionStatus
  online: boolean
  lastHeartbeatAt?: string | null
  lastOnlineAt?: string | null
  statusUpdatedAt?: string | null
  offlineReason?: string | null
  source: RobotHeartbeatSource
  protocolVersion?: string | null
  bootId?: string | null
  softwareVersion?: string | null
  robotState?: string | null
  acceptedEventSequence: number
  diagnosticSummary?: string | null
  reportedSupportsRemoteImmediateStart?: boolean
  reportedSupportsLocalConfirmStart?: boolean
  localConfirmProtocolVersion?: string | null
  localConfirmProtocolCompatible?: boolean
  localConfirmStartReady?: boolean
  localConfirmStartError?: string | null
  capabilityReportedAt?: string | null
}

export interface RobotHeartbeatStatusPage {
  items: RobotHeartbeatStatus[]
  page: number
  size: number
  total: number
}

export interface RobotHeartbeatStatusQuery {
  online?: boolean
  connectionStatus?: RobotConnectionStatus
  sort?: 'robotId' | 'displayName' | 'lastHeartbeatAt' | 'statusUpdatedAt'
  direction?: 'asc' | 'desc'
  page?: number
  size?: number
}
