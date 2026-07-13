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
  return cloneJson(input) as RouteExecutorDocument
}
