import request from '@/utils/request'

// 上传森林摄像头现场画面并执行 AI 复核
export function uploadReview(data) {
  return request({
    url: '/api/review/upload',
    method: 'post',
    data
  })
}

// 获取最新一条现场画面
export function getLatestReview() {
  return request({
    url: '/api/review/latest',
    method: 'get'
  })
}
