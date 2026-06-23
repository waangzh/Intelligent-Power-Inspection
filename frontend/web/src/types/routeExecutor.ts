/** ROS map route executor JSON (version 2). */

export interface MapPose {
  x: number
  y: number
  yaw: number
}

export interface RouteExecutorTarget {
  id: string
  name: string
  x: number
  y: number
  yaw: number
  taskDuration: number
}

export interface RouteExecutorDocument {
  version: 2
  frame_id: 'map'
  active_route_id: string
  start_pose: {
    name: string
    pose: MapPose
    publish_initial_pose: boolean
    covariance: { x: number; y: number; yaw: number }
  }
  targets: Array<{
    id: string
    name: string
    pose: MapPose
    task_duration_sec: number
  }>
  routes: Array<{
    id: string
    name: string
    target_ids: string[]
    return_to_start: boolean
    loop: { enabled: boolean; wait_sec: number; max_cycles: number }
    goal_timeout_sec: number
    max_retries_per_checkpoint: number
    failure_policy: 'abort_and_return_home' | 'abort'
  }>
  schedules: unknown[]
}

export type EditorMode = 'start' | 'target' | 'yaw' | 'pan'

export interface RosMapState {
  width: number
  height: number
  pixels: Uint8Array | null
  yamlName: string
  pgmName: string
  image: string
  resolution: number
  origin: [number, number, number]
  negate: number
}
