import request from '@/utils/request'

// 上传现场画面（居民端报警时抓拍）
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
