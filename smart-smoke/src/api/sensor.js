import request from '@/utils/request'

// 获取最新一条传感器数据（可按设备编号过滤）
export function getSensorLatest(deviceId) {
  return request({
    url: '/latest',
    method: 'get',
    params: deviceId ? { deviceId } : {}
  })
}

// 获取历史记录（可按设备编号、时间段过滤；limit 取最近 N 条）
export function getSensorHistory(deviceId, startTime, endTime, limit) {
  const params = {}
  if (deviceId) params.deviceId = deviceId
  if (startTime) params.startTime = startTime
  if (endTime) params.endTime = endTime
  if (limit) params.limit = limit
  return request({
    url: '/history',
    method: 'get',
    params
  })
}

// 获取历史告警记录（仅 alarm>0，可按设备编号、时间段过滤）
export function getSensorAlarmHistory(deviceId, startTime, endTime) {
  const params = {}
  if (deviceId) params.deviceId = deviceId
  if (startTime) params.startTime = startTime
  if (endTime) params.endTime = endTime
  return request({
    url: '/history/alarm',
    method: 'get',
    params
  })
}

// 获取设备在线状态
export function getDeviceStatus() {
  return request({
    url: '/device/status',
    method: 'get'
  })
}

// 获取设备在线状态变更历史（上线/离线切换记录）
export function getDeviceStatusHistory(deviceId) {
  const params = {}
  if (deviceId) params.deviceId = deviceId
  return request({
    url: '/device/status/history',
    method: 'get',
    params
  })
}
