import request from '@/utils/request'

// 居民报修：创建工单
export function createWorkOrder(data) {
  return request({
    url: '/api/work-order/create',
    method: 'post',
    data
  })
}

// 我的工单（居民）
export function getMyOrders(userId) {
  return request({
    url: '/api/work-order/mine',
    method: 'get',
    params: { userId }
  })
}

// 全部工单（管理员 + 维修员）
export function getWorkOrderList() {
  return request({
    url: '/api/work-order/list',
    method: 'get'
  })
}

// 维修员接单
export function acceptWorkOrder(data) {
  return request({
    url: '/api/work-order/accept',
    method: 'post',
    data
  })
}

// 关闭工单（维修员确认完成 / 管理员兜底）
export function closeWorkOrder(data) {
  return request({
    url: '/api/work-order/close',
    method: 'post',
    data
  })
}
