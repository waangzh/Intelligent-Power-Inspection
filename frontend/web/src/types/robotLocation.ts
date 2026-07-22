export type GnssFixType =
  | 'NO_FIX'
  | 'SINGLE_POINT'
  | 'DGPS'
  | 'RTK_FIXED'
  | 'RTK_FLOAT'
  | 'UNKNOWN'

export interface GnssFix {
  valid: boolean
  stale: boolean
  frame?: string | null
  latitude?: number | null
  longitude?: number | null
  altitude?: number | null
  quality?: number | null
  fixType: GnssFixType
  satellites?: number | null
  hdop?: number | null
  differentialAge?: number | null
  baseStationId?: string | null
  ageSec?: number | null
  observedAt?: string | null
}

export interface RobotLocation {
  robotId: string
  online: boolean
  locationAvailable: boolean
  realtime: boolean
  state?: string | null
  executionId?: string | null
  gnssFix?: GnssFix | null
}

export interface RobotTrackPoint {
  latitude: number
  longitude: number
  altitude?: number | null
  fixType?: GnssFixType
  satellites?: number | null
  hdop?: number | null
  robotState?: string | null
  navigationPhase?: string | null
  targetId?: string | null
  cycleIndex?: number | null
  observedAt: string
}

export interface RobotTrackResponse {
  robotId: string
  executionId?: string | null
  start: string
  end: string
  points: RobotTrackPoint[]
}

export interface RobotLocationQuery {
  siteId?: string
  online?: boolean
}

export interface RobotTrackQuery {
  start?: string
  end?: string
  executionId?: string
  limit?: number
}
