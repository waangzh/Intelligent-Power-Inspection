/** ROS map_server yaml（与 route_map_tool 解析规则一致） */
function parseMapYaml(text) {
  const config = {}
  for (const rawLine of String(text).split(/\r?\n/)) {
    const line = rawLine.split('#')[0].trim()
    if (!line) continue
    const match = line.match(/^([A-Za-z_][\w-]*)\s*:\s*(.*)$/)
    if (!match) continue
    const key = match[1]
    let value = match[2].trim()
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1)
    } else if (value.startsWith('[') && value.endsWith(']')) {
      value = value.slice(1, -1).split(',').map((part) => Number(part.trim()))
    } else if (/^-?\d+(\.\d+)?$/.test(value)) {
      value = Number(value)
    }
    config[key] = value
  }

  const meta = {
    resolution: typeof config.resolution === 'number' ? config.resolution : 0.05,
    origin: Array.isArray(config.origin) && config.origin.length >= 2
      ? config.origin.slice(0, 3)
      : [0, 0, 0],
    negate: typeof config.negate === 'number' ? config.negate : 0,
    frame_id: 'map',
  }
  if (typeof config.image === 'string') meta.image = config.image
  if (typeof config.occupied_thresh === 'number') meta.occupied_thresh = config.occupied_thresh
  if (typeof config.free_thresh === 'number') meta.free_thresh = config.free_thresh
  return meta
}

module.exports = { parseMapYaml }
