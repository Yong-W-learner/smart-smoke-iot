import request from '@/utils/request'

export function getDeviceSelfTests(deviceId) {
  return request({
    url: '/api/device-self-test/list',
    method: 'get',
    params: deviceId ? { deviceId } : {}
  })
}

export function createDeviceSelfTest(data) {
  return request({
    url: '/api/device-self-test/create',
    method: 'post',
    data
  })
}
