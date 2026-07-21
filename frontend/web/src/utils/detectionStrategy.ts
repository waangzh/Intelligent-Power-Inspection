import {
  CHECKPOINT_DETECTIONS,
  DETECTION_TARGET_LABELS,
  DETECTION_LABELS,
  type DetectionItem,
  type PresetDetectionType,
  type RobotInspectionImage,
  type Route,
} from '@/types'

const DEFAULT_PROMPTS: Record<string, string> = {
  PERSON: '巡检区域内的人员',
  HELMET: '人员头部佩戴的安全帽',
  OBSTACLE: '机器人行进路线上的障碍物',
  FIRE: '图像中清晰可见的火焰、火光或明显烟雾区域',
  SWITCH: '变电设备上的刀闸开关操作手柄、连杆及触头区域',
  METER: '圆形机械压力表的完整表盘和指针区域',
  OIL_LEAK: '变压器或电气设备表面、法兰、阀门、接口及底部可见的油渍、油迹或积油区域',
  FOREIGN_OBJECT: '设备操作区域内不属于设备本体的遗留物，例如工具、纸箱、塑料袋、布料或其他杂物',
}

export function defaultDetectionItem(type: PresetDetectionType): DetectionItem {
  return {
    itemId: type,
    type,
    name: DETECTION_LABELS[type],
    enabled: true,
    displayLabel: DETECTION_TARGET_LABELS[type],
    prompt: DEFAULT_PROMPTS[type],
    threshold: 0.75,
  }
}

export function cloneDetectionItems(items: DetectionItem[]): DetectionItem[] {
  return items.map((item) => ({
    ...item,
    itemId: item.itemId?.trim() || item.type,
    name: item.name?.trim() || DETECTION_LABELS[item.type] || item.type,
    displayLabel: item.displayLabel?.trim() || DETECTION_TARGET_LABELS[item.type] || '',
    prompt: item.prompt?.trim() || DEFAULT_PROMPTS[item.type] || '',
    threshold: 0.75,
  }))
}

export function formatDetectionElapsed(startedAt: string | number | undefined, now = Date.now()): string {
  const start = typeof startedAt === 'number' ? startedAt : Date.parse(startedAt || '')
  const elapsedSeconds = Number.isFinite(start) ? Math.max(0, Math.floor((now - start) / 1000)) : 0
  const minutes = Math.floor(elapsedSeconds / 60)
  const seconds = elapsedSeconds % 60
  return minutes ? `已运行 ${minutes} 分 ${seconds} 秒` : `已运行 ${seconds} 秒`
}

export function ensureDetectionPrompts(items: DetectionItem[]): DetectionItem[] {
  return items.map((item) => ({
    ...item,
    prompt: item.prompt?.trim() || DEFAULT_PROMPTS[item.type] || '',
  }))
}

export function defaultCheckpointDetectionItems(): DetectionItem[] {
  return CHECKPOINT_DETECTIONS.map(defaultDetectionItem)
}

export function resolveRobotImageDetectionItems(
  image: RobotInspectionImage,
  route?: Route,
): DetectionItem[] {
  const checkpoint = route?.id === image.routeId
    ? route.checkpoints.find((item) => item.id === image.checkpointId)
    : undefined
  return checkpoint?.detections?.length
    ? cloneDetectionItems(checkpoint.detections)
    : defaultCheckpointDetectionItems()
}
