// 工单状态统一映射：pending待接单 / accepted已接单 / closed已关闭
export function orderStatus(status) {
  switch (status) {
    case 'pending':
      return { text: '待接单', type: 'warning', effect: 'light' }
    case 'accepted':
      return { text: '已接单', type: 'primary', effect: 'light' }
    case 'closed':
      return { text: '已关闭', type: 'success', effect: 'light' }
    default:
      return { text: '未知', type: 'info', effect: 'light' }
  }
}

// 设备运维只使用 repair；alarm 仅用于兼容拆分前的历史数据
export function orderType(type) {
  switch (type) {
    case 'repair':
      return { text: '设备报修', type: 'warning' }
    case 'alarm':
      return { text: '历史警情', type: 'danger' }
    default:
      return { text: '其他', type: 'info' }
  }
}
