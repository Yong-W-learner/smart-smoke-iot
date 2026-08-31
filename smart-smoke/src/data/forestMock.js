export const parkInfo = {
  name: '福州国家森林公园',
  area: 5.2,
  ancientTrees: 186,
  visitors: 428,
  fireRisk: '橙色'
}

export const zones = [
  { id: 1, name: '千年银杏古树区', type: '古树核心区', risk: '高', x: 24, y: 30, trees: 68 },
  { id: 2, name: '香樟古树群', type: '古树保护区', risk: '中', x: 58, y: 24, trees: 74 },
  { id: 3, name: '游客服务中心', type: '游客活动区', risk: '低', x: 46, y: 68, trees: 0 },
  { id: 4, name: '无火露营区', type: '游客活动区', risk: '中', x: 72, y: 58, trees: 0 },
  { id: 5, name: '南坡生态林', type: '巡护林区', risk: '高', x: 78, y: 34, trees: 44 }
]

export const sensorNodes = [
  { id: 'GT-01', name: '银杏区主节点', zone: '千年银杏古树区', smoke: 82.6, temperature: 36.8, humidity: 31, co: 28, online: true, source: 'real', status: 'alarm', camera: 'CAM-01' },
  { id: 'GT-02', name: '银杏区北侧节点', zone: '千年银杏古树区', smoke: 41.2, temperature: 32.4, humidity: 34, co: 12, online: true, source: 'simulated', status: 'warning', camera: 'CAM-01' },
  { id: 'GT-03', name: '香樟区东侧节点', zone: '香樟古树群', smoke: 14.8, temperature: 29.7, humidity: 46, co: 4, online: true, source: 'simulated', status: 'normal', camera: 'CAM-02' },
  { id: 'GT-04', name: '香樟区西侧节点', zone: '香樟古树群', smoke: 16.3, temperature: 30.1, humidity: 45, co: 5, online: true, source: 'simulated', status: 'normal', camera: 'CAM-02' },
  { id: 'GT-05', name: '露营区边界节点', zone: '无火露营区', smoke: 25.6, temperature: 31.5, humidity: 41, co: 8, online: true, source: 'simulated', status: 'normal', camera: 'CAM-03' },
  { id: 'GT-06', name: '南坡巡护节点', zone: '南坡生态林', smoke: null, temperature: null, humidity: null, co: null, online: false, source: 'simulated', status: 'offline', camera: null }
]

export const drones = [
  { id: 'UAV-01', name: '云巡一号', model: 'M300 RTK + H20T + 环境采样载荷', battery: 86, status: 'flying', location: '银杏区北侧上空', thermal: true, operator: '林海', payloads: ['可见光','辐射测温热成像','PM2.5','温湿度','CO'], x: 59, y: 38, latitude: 26.164459, longitude: 119.288590, altitude: 86, speed: 8.4, satellites: 18, linkQuality: 92, etaSec: 480, phase: '航线巡检', updatedAt: '15:06:28', telemetry: { pm25: 36.8, temperature: 32.7, humidity: 38, co: 6, surfaceTemperature: 42.8, windEstimate: 3.4 } },
  { id: 'UAV-02', name: '云巡二号', model: 'Mavic 3T 轻型热成像巡检机', battery: 74, status: 'idle', location: '游客中心起降坪', thermal: true, operator: '周岚', payloads: ['可见光','热成像'], x: 49, y: 79, latitude: 26.154619, longitude: 119.285990, altitude: 0, speed: 0, satellites: 16, linkQuality: 100, etaSec: 0, phase: '地面待命', updatedAt: '15:06:28', telemetry: null }
]

export const fireIncidents = [
  { id: 'FIRE-20260830-001', time: '2026-08-30 14:26:18', zone: '千年银杏古树区', level: '三级', source: '多传感器融合', status: 'pending', smoke: 82.6, temperature: 36.8, confidence: 88, reason: '烟雾持续上升，CO同步升高，固定摄像头发现疑似烟羽', result: '', ranger: '' },
  { id: 'FIRE-20260829-003', time: '2026-08-29 16:42:03', zone: '无火露营区', level: '二级', source: '摄像头AI', status: 'closed', smoke: 38.2, temperature: 30.6, confidence: 74, reason: '画面发现局部白烟', result: '游客使用卡式炉，护林员已劝阻并完成安全检查', ranger: '周岚' },
  { id: 'FIRE-20260828-002', time: '2026-08-28 10:18:46', zone: '香樟古树群', level: '一级', source: '护林员上报', status: 'closed', smoke: 22.4, temperature: 29.8, confidence: 51, reason: '林区出现短时烟雾', result: '确认为晨雾与逆光干扰，无火情', ranger: '林海' }
]

export const patrolTasks = [
  { id: 'PATROL-0830-01', name: '古树核心区午后热源巡查', route: '护林员现场操控 → 银杏区、香樟区 → 返回起降点', ranger: '林海', drone: '云巡一号', mode: 'manual', status: '待执行', progress: 0, planTime: '15:00', coverage: '待计算', images: 0, hotspots: 0, samples: 0, etaSec: 0 },
  { id: 'PATROL-0830-02', name: '游客活动区违规用火巡查', route: '游客中心 → 露营区 → 南坡入口', ranger: '周岚', drone: '云巡二号', status: '待执行', progress: 0, planTime: '16:30', coverage: '待计算', images: 0, hotspots: 0, samples: 0 },
  { id: 'PATROL-0829-04', name: '西线闭园前巡查', route: '西门 → 银杏区 → 北侧巡护站', ranger: '林海', drone: '云巡一号', status: '已完成', progress: 100, planTime: '17:20', coverage: '1.6 km²', images: 42, hotspots: 0, samples: 96, maxTemperature: 34.6, report: '航线巡护完成，未发现烟雾、明火或异常高温点。' }
]

export function makeHistory(base, amplitude = 5, count = 24, spike = false) {
  return Array.from({ length: count }, (_, i) => {
    const hour = String((i + 8) % 24).padStart(2, '0')
    let value = base + Math.sin(i / 3) * amplitude + ((i * 7) % 5 - 2) * 0.45
    if (spike && i > count - 6) value += (i - (count - 6)) * amplitude * 1.35
    return { label: `${hour}:00`, value: Number(Math.max(0, value).toFixed(1)) }
  })
}

export const smokeHistory = makeHistory(18, 4, 24, true)
export const temperatureHistory = makeHistory(27, 2.5, 24, true)
export const humidityHistory = makeHistory(46, 6, 24, false)

export function getPublicAlert() {
  try {
    return JSON.parse(localStorage.getItem('forest_public_alert') || 'null')
  } catch {
    return null
  }
}

export function savePublicAlert(alert) {
  localStorage.setItem('forest_public_alert', JSON.stringify(alert))
}
