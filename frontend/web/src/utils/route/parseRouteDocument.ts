import type { RouteExecutorDocument } from '@/types/routeExecutor'
import { cloneJson, isJsonObject } from './common'

export class RouteDocumentParseError extends Error {
  constructor(public readonly code: string, public readonly jsonPointer: string, message: string) { super(message) }
}

export function parseRouteDocument(input: unknown): RouteExecutorDocument {
  if (!isJsonObject(input)) throw new RouteDocumentParseError('ROUTE_NOT_OBJECT', '', '路线 JSON 根节点必须是对象')
  if (input.version !== 2 && input.version !== 3) {
    throw new RouteDocumentParseError('UNSUPPORTED_VERSION', '/version', '仅支持 version=2 或 version=3 的路线 JSON')
  }
  if (input.frame_id !== 'map') throw new RouteDocumentParseError('INVALID_FRAME_ID', '/frame_id', 'frame_id 必须为 map')
  if (!Array.isArray(input.routes) || input.routes.length !== 1) {
    throw new RouteDocumentParseError('UNSUPPORTED_ROUTE_COUNT', '/routes', '路线编辑器仅支持包含一条 route 的文件')
  }
  if (!Array.isArray(input.schedules) || input.schedules.length !== 0) {
    throw new RouteDocumentParseError('UNSUPPORTED_SCHEDULES', '/schedules', '路线编辑器不支持非空 schedules，已拒绝导入以避免数据丢失')
  }
  return cloneJson(input) as RouteExecutorDocument
}
