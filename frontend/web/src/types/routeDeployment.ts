export type RouteDeploymentState = 'PENDING' | 'INSTALLING' | 'READY_FOR_ROBOT' | 'FAILED' | 'UNKNOWN'

export interface RouteDeployment {
  id: string
  routeRevisionId: string
  robotId: string
  requestId: string
  state: RouteDeploymentState
  attemptCount: number
  lastAttemptAt?: string | null
  nextReconcileAt?: string | null
  errorCode?: string | null
  errorMessage?: string | null
  routeContentSha256: string
  mapAssetId: string
  mapImageSha256: string
  createdAt: string
  updatedAt: string
  stateVersion: number
}
