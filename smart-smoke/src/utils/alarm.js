// 警情等级统一映射：alarm 字段 0正常 1一级 2二级 3三级
export function alarmInfo(alarm) {
  const a = Number(alarm) || 0
  switch (a) {
    case 3:
      return { text: '三级警情', type: 'danger', effect: 'dark' }   // 深红，最醒目
    case 2:
      return { text: '二级警情', type: 'danger', effect: 'light' }  // 红
    case 1:
      return { text: '一级警情', type: 'warning', effect: 'light' } // 橙
    default:
      return { text: '正常', type: 'success', effect: 'light' }      // 绿
  }
}

// 是否处于告警状态（alarm > 0）
export function isAlarm(alarm) {
  return Number(alarm) > 0
}
