import request from '@/utils/request'

// 查看全部传感器（设备信息 + 最新烟雾 + 归属居民）
export function getDeviceList() {
  return request({
    url: '/api/device/list',
    method: 'get'
  })
}

// 居民端：查当前住户绑定的传感器（位置 + 在线 + 最新烟雾）
export function getMyDevice(userId) {
  return request({
    url: '/api/device/mine',
    method: 'get',
    params: { userId }
  })
}

// 新增传感器
export function addDevice(data) {
  return request({
    url: '/api/device/add',
    method: 'post',
    data
  })
}

// 删除传感器
export function deleteDevice(deviceId) {
  return request({
    url: `/api/device/${deviceId}`,
    method: 'delete'
  })
}

// 管理员：向真实 BearPi 设备下发蜂鸣器/LED控制命令
export function sendDeviceCommand(deviceId, data) {
  return request({
    url: `/api/admin/devices/${deviceId}/command`,
    method: 'post',
    data,
    timeout: 25000
  })
}
