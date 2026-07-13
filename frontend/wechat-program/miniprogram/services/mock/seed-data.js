const { ROUTE_DETECTIONS, CHECKPOINT_DETECTIONS } = require('../../utils/constants')

function detectionItems(types) {
  return types.map((type) => ({
    type,
    enabled: true,
    threshold: 0.75,
    prompt: type === 'SWITCH' ? '红色刀闸开关' : type === 'OIL_LEAK' ? '设备底部渗油区域' : type === 'METER' ? '压力表读数区域' : undefined,
  }))
}

function createDemoRoutes() {
  const c = { lat: 30.2741, lng: 120.1551 }
  const d = 0.0003
  const path = [
    { lat: c.lat, lng: c.lng },
    { lat: c.lat + d, lng: c.lng + d * 0.5 },
    { lat: c.lat + d * 0.5, lng: c.lng + d },
    { lat: c.lat - d * 0.3, lng: c.lng + d * 0.8 },
  ]
  const rosRoute = {
    version: 2,
    frame_id: 'map',
    active_route_id: 'route_patrol_001',
    start_pose: {
      name: '初始起点',
      pose: { x: 0.5, y: -0.5, yaw: 0.2 },
      publish_initial_pose: true,
      covariance: { x: 0.25, y: 0.25, yaw: 0.0685 },
    },
    targets: [
      { id: 'target_001', name: '1# 主变', pose: { x: 2.5, y: 0.8, yaw: 1.2 }, task_duration_sec: 25 },
      { id: 'target_002', name: 'GIS 刀闸', pose: { x: 4.2, y: -0.6, yaw: 0.5 }, task_duration_sec: 30 },
      { id: 'target_003', name: '电容器组', pose: { x: 1.8, y: 1.5, yaw: -0.8 }, task_duration_sec: 20 },
    ],
    routes: [{
      id: 'route_patrol_001',
      name: '主变区例行巡检',
      target_ids: ['target_001', 'target_002', 'target_003'],
      return_to_start: true,
      loop: { enabled: false, wait_sec: 600, max_cycles: 0 },
      goal_timeout_sec: 120,
      max_retries_per_checkpoint: 1,
      failure_policy: 'abort_and_return_home',
    }],
    schedules: [],
  }

  return [{
    id: 'route_demo_001',
    siteId: 'site_001',
    name: '主变区例行巡检',
    description: '覆盖主变、GIS 设备区',
    path,
    mapMode: 'ros2d',
    rosRoute,
    routeDetections: detectionItems(ROUTE_DETECTIONS),
    checkpoints: [
      { id: 'cp_demo_001', routeId: 'route_demo_001', name: '1# 主变', seq: 1, position: path[1], pan: 45, tilt: -20, dwellSeconds: 25, detections: detectionItems(CHECKPOINT_DETECTIONS) },
      { id: 'cp_demo_002', routeId: 'route_demo_001', name: 'GIS 刀闸', seq: 2, position: path[2], pan: 90, tilt: -15, dwellSeconds: 30, detections: detectionItems(CHECKPOINT_DETECTIONS) },
      { id: 'cp_demo_003', routeId: 'route_demo_001', name: '电容器组', seq: 3, position: path[3], pan: 120, tilt: -25, dwellSeconds: 20, detections: detectionItems(CHECKPOINT_DETECTIONS) },
    ],
    createdAt: '2026-03-01T08:00:00Z',
  }]
}

const defaultSites = [
  { id: 'site_001', name: '城东 220kV 变电站', address: '浙江省杭州市余杭区', description: '主变 2 台，户外 GIS 设备区', center: { lat: 30.2741, lng: 120.1551 }, createdAt: '2026-01-15T08:00:00Z' },
  { id: 'site_002', name: '城西 110kV 变电站', address: '浙江省杭州市西湖区', description: '室内开关室 + 室外电容器组', center: { lat: 30.2599, lng: 120.12 }, createdAt: '2026-02-01T08:00:00Z' },
  { id: 'site_003', name: '城南 500kV 变电站', address: '浙江省杭州市萧山区', description: '特高压枢纽站，户外设备规模大', center: { lat: 30.185, lng: 120.265 }, createdAt: '2026-03-10T08:00:00Z' },
]

const defaultAreas = [
  { id: 'area_001', siteId: 'site_001', name: '主变区域', polygon: [{ lat: 30.2745, lng: 120.1545 }, { lat: 30.2745, lng: 120.1558 }, { lat: 30.2738, lng: 120.1558 }, { lat: 30.2738, lng: 120.1545 }] },
  { id: 'area_002', siteId: 'site_001', name: 'GIS 设备区', polygon: [{ lat: 30.2737, lng: 120.1546 }, { lat: 30.2737, lng: 120.1556 }, { lat: 30.2732, lng: 120.1556 }, { lat: 30.2732, lng: 120.1546 }] },
  { id: 'area_003', siteId: 'site_002', name: '开关室', polygon: [{ lat: 30.2602, lng: 120.1195 }, { lat: 30.2602, lng: 120.1205 }, { lat: 30.2596, lng: 120.1205 }, { lat: 30.2596, lng: 120.1195 }] },
]

const defaultRobots = [
  { id: 'robot_001', name: '巡检机器人', model: 'Unitree B2', serialNo: 'UT-B2-2024-001', siteId: 'site_001', status: 'ONLINE', battery: 87, position: { lat: 30.274, lng: 120.1548 }, firmware: 'v2.3.1', lastOnlineAt: new Date().toISOString() },
]

const seedAlarms = [
  { id: 'alarm_seed_001', taskId: 'task_demo', routeName: '主变区例行巡检', type: 'HELMET', severity: 'HIGH', message: '检测到作业人员未佩戴安全帽', imageUrl: 'https://picsum.photos/seed/alarm1/400/240', acknowledged: false, createdAt: new Date(Date.now() - 7200000).toISOString() },
  { id: 'alarm_seed_002', taskId: 'task_demo', routeName: '主变区例行巡检', checkpointName: 'GIS 刀闸', type: 'SWITCH', severity: 'HIGH', message: '检查点「GIS 刀闸」开关/刀闸状态异常', imageUrl: 'https://picsum.photos/seed/alarm2/400/240', acknowledged: true, createdAt: new Date(Date.now() - 86400000).toISOString() },
  { id: 'alarm_seed_003', taskId: 'task_demo', routeName: '电容器组巡检', type: 'FIRE', severity: 'CRITICAL', message: '路线视野内检测到疑似火源/烟雾', imageUrl: 'https://picsum.photos/seed/alarm3/400/240', acknowledged: false, createdAt: new Date(Date.now() - 3600000).toISOString() },
]

const seedRecords = [
  { id: 'record_seed_001', taskId: 'task_hist_001', taskName: '主变区夜间巡检', routeName: '主变区例行巡检', robotName: '巡检机器人', alarmCount: 2, checkpointCount: 3, duration: '28 分钟', summary: '完成城东 220kV 变电站巡检，共 3 个检查点，触发 2 条告警', completedAt: new Date(Date.now() - 172800000).toISOString() },
  { id: 'record_seed_002', taskId: 'task_hist_002', taskName: 'GIS 设备专项巡检', routeName: 'GIS 专项路线', robotName: '巡检机器人', alarmCount: 0, checkpointCount: 5, duration: '35 分钟', summary: '完成城东 220kV 变电站巡检，共 5 个检查点，无异常告警', completedAt: new Date(Date.now() - 432000000).toISOString() },
]

const seedWorkOrders = [
  { id: 'wo_seed_1', title: '主变区漏油异常处置', description: '告警：检查点「主变 A 相」漏油检测异常，需现场复核', status: 'PROCESSING', priority: 'HIGH', assigneeId: 'user_dispatcher', assigneeName: '张调度', createdById: 'user_admin', createdByName: '系统管理员', location: { siteName: '城东 220kV 变电站', routeName: '主变区例行巡检', checkpointName: '主变 A 相', areaName: '主变区例行巡检 · 主变 A 相', address: '浙江省杭州市余杭区' }, createdAt: '2026-06-10T08:00:00Z', updatedAt: '2026-06-11T10:00:00Z' },
  { id: 'wo_seed_2', title: 'GIS 区未佩戴安全帽', description: '路线行进中检测到作业人员未佩戴安全帽', status: 'PENDING', priority: 'URGENT', createdById: 'user_admin', createdByName: '系统管理员', location: { siteName: '城东 220kV 变电站', routeName: '主变区例行巡检', areaName: '主变区例行巡检', address: '浙江省杭州市余杭区' }, createdAt: '2026-06-12T14:30:00Z', updatedAt: '2026-06-12T14:30:00Z' },
]

/** 云端地图库默认条目（yaml + png；site_002/003 演示复用 site_001 栅格） */
const defaultSlamMapRegistry = {
  site_001: { yamlUrl: '/assets/maps/site_001/map.txt', pngUrl: '/assets/maps/site_001/map.png', source: 'bundled' },
  site_002: { yamlUrl: '/assets/maps/site_002/map.txt', pngUrl: '/assets/maps/site_001/map.png', source: 'bundled' },
  site_003: { yamlUrl: '/assets/maps/site_003/map.txt', pngUrl: '/assets/maps/site_001/map.png', source: 'bundled' },
}

module.exports = {
  createDemoRoutes,
  defaultSites,
  defaultSlamMapRegistry,
  defaultAreas,
  defaultRobots,
  defaultDetectionTemplates: [
    { id: 'tpl_route_001', name: '路线标准检测', scope: 'ROUTE', types: ['PERSON', 'HELMET', 'OBSTACLE', 'FIRE'], description: '行进过程中持续检测', prompts: {}, createdAt: '2026-01-10T08:00:00Z' },
    { id: 'tpl_cp_001', name: '刀闸开关检测', scope: 'CHECKPOINT', types: ['SWITCH', 'METER'], description: '刀闸分合状态与表计读数', prompts: { SWITCH: '红色刀闸开关', METER: '压力表读数区域' }, createdAt: '2026-01-10T08:00:00Z' },
    { id: 'tpl_cp_002', name: '设备渗漏检测', scope: 'CHECKPOINT', types: ['OIL_LEAK', 'FOREIGN_OBJECT', 'FIRE'], description: '渗漏、异物与烟火', prompts: { OIL_LEAK: '设备底部渗油区域' }, createdAt: '2026-02-15T08:00:00Z' },
  ],
  seedAlarms,
  seedRecords,
  seedWorkOrders,
}
