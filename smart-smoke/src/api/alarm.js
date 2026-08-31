import request from '@/utils/request'

// 管理员：警情处置列表（status: 0待处置 / 1已处置，可不传）
export function getAlarmIncidents(status) {
  return request({
    url: '/api/alarm/incidents',
    method: 'get',
    params: status === undefined ? undefined : { status }
  })
}

// 管理员：确认警情并移交应急消防人员
export function confirmAlarmIncident(id, operatorId) {
  return request({
    url: `/api/alarm/incidents/${id}/confirm`,
    method: 'post',
    data: { operatorId }
  })
}

// 应急消防员：确认本人到场
export function arriveAlarmIncident(id, operatorId) {
  return request({
    url: `/api/alarm/incidents/${id}/arrive`,
    method: 'post',
    data: { operatorId }
  })
}

// 应急消防员：填写结论并上报处置结果
export function handleAlarmIncident(id, data) {
  return request({
    url: `/api/alarm/incidents/${id}/handle`,
    method: 'post',
    data
  })
}

// 管理员：所有历史告警事件列表
export function getAlarmHistory() {
  return request({
    url: '/api/alarm/history',
    method: 'get'
  })
}

// 单次告警详情：时间段浓度变化 + 同时段报警设备
export function getAlarmDetail(params) {
  return request({
    url: '/api/alarm/detail',
    method: 'get',
    params
  })
}

// 告警统计分析（等级分布 + 近7天趋势 + 各设备告警次数）
export function getAlarmStats() {
  return request({
    url: '/api/alarm/stats',
    method: 'get'
  })
}
