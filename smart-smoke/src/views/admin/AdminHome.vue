<template>
  <div class="admin-wrap">
    <!--侧边栏-->
    <el-aside width="220px" class="aside">
      <div class="sidebar-title">智慧烟感系统·管理员</div>
      <el-menu
        class="sidebar-menu"
        active-text-color="#165DFF"
        background-color="#ffffff"
        text-color="#606266"
        :default-active="activeMenu"
      >
        <el-menu-item index="home" @click="$router.push('/admin')">
          <el-icon><House /></el-icon>
          <span>小区总览</span>
        </el-menu-item>
        <el-menu-item index="devices" @click="$router.push('/admin/devices')">
          <el-icon><Monitor /></el-icon>
          <span>传感器概览</span>
        </el-menu-item>
        <el-menu-item index="alarms" @click="$router.push('/admin/alarms')">
          <el-icon><Bell /></el-icon>
          <span>警情事件</span>
        </el-menu-item>
        <el-menu-item index="smoke-history" @click="$router.push('/admin/smoke-history')">
          <el-icon><TrendCharts /></el-icon>
          <span>历史浓度</span>
        </el-menu-item>
        <el-menu-item index="orders" @click="$router.push('/admin/orders')">
          <el-icon><Tickets /></el-icon>
          <span>设备运维</span>
        </el-menu-item>
      </el-menu>
      <!--左下角用户区：默认只显示头像，悬停展开名字+退出登录-->
      <div class="sidebar-user">
        <el-avatar :size="38" class="user-avatar">{{ userInitial }}</el-avatar>
        <div class="user-meta">
          <span class="user-name">{{ adminName }}</span>
          <el-button class="logout-btn" text size="small" @click="logout">退出登录</el-button>
        </div>
      </div>
    </el-aside>

    <!--主内容区-->
    <el-main class="main-content">
      <!--页头-->
      <div class="page-header">
        <div>
          <h2 class="page-title">小区总览</h2>
          <p class="page-subtitle">欢迎回来，{{ adminName }} · 统一管理小区内所有烟雾传感器设备</p>
        </div>
        <div class="page-date">{{ today }}</div>
      </div>

      <!--小区设备统计-->
      <el-row :gutter="16" class="stat-row">
        <el-col :span="6" v-reveal>
          <el-card class="stat-card" v-tilt>
            <div class="stat-body">
              <div class="stat-icon stat-icon-blue"><el-icon><Monitor /></el-icon></div>
              <div class="stat-info">
                <div class="stat-label">设备总数</div>
                <div class="stat-num" v-count-to="stats.total"></div>
                <div class="stat-sub">在线 {{ stats.online }} · 离线 {{ stats.offline }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6" v-reveal="{ delay: 70 }">
          <el-card class="stat-card" v-tilt>
            <div class="stat-body">
              <div class="stat-icon stat-icon-green"><el-icon><Connection /></el-icon></div>
              <div class="stat-info">
                <div class="stat-label">在线设备</div>
                <div class="stat-num" v-count-to="stats.online"></div>
                <div class="stat-sub">在线率 {{ stats.onlineRate }}%</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6" v-reveal="{ delay: 140 }">
          <el-card class="stat-card" v-tilt>
            <div class="stat-body">
              <div class="stat-icon stat-icon-gray"><el-icon><CircleClose /></el-icon></div>
              <div class="stat-info">
                <div class="stat-label">离线设备</div>
                <div class="stat-num" v-count-to="stats.offline"></div>
                <div class="stat-sub">占 {{ stats.offlineRate }}%</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6" v-reveal="{ delay: 210 }">
          <el-card class="stat-card" v-tilt>
            <div class="stat-body">
              <div class="stat-icon stat-icon-red"><el-icon><Warning /></el-icon></div>
              <div class="stat-info">
                <div class="stat-label">告警中设备</div>
                <div class="stat-num" v-count-to="stats.alarming"></div>
                <div class="stat-sub">严重告警 {{ stats.serious }} 起</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!--告警统计分析：饼图 + 柱状图-->
      <el-card class="alarm-stats-card" v-reveal="{ delay: 100 }">
        <template #header>
          <div class="card-header-row">
            <span>告警统计分析</span>
            <el-tag v-if="alarmStats.demo" type="info" size="small">仿真演示数据</el-tag>
            <el-tag v-else type="success" size="small">真实数据</el-tag>
          </div>
        </template>
        <el-row :gutter="16">
          <el-col :span="8">
            <div class="chart-title">告警等级分布</div>
            <div ref="levelPieRef" class="chart chart-sm"></div>
          </el-col>
          <el-col :span="16">
            <div class="chart-title">近7天告警趋势</div>
            <LiveLineChart :data="trendData" :height="260" unit=" 次" subtitle="近 7 天告警次数" show-x-axis :max-x-ticks="7" />
          </el-col>
        </el-row>
        <el-row :gutter="16" style="margin-top:16px">
          <el-col :span="24">
            <div class="chart-title">各设备告警次数</div>
            <div ref="deviceBarRef" class="chart chart-sm"></div>
          </el-col>
        </el-row>
      </el-card>

      <!--AI 智能复核：现场画面 + 视觉识别-->
      <el-card class="review-card" v-reveal="{ delay: 140 }">
        <template #header>
          <div class="card-header-row">
            <span>AI 智能复核（报警自动抓拍 + 视觉识别）</span>
            <el-tag v-if="latestReview" :type="latestReview.alarmLevel >= 2 ? 'danger' : 'warning'" effect="dark">
              浓度 {{ latestReview.smokeConcentration }}ppm
            </el-tag>
          </div>
        </template>

        <div v-if="latestReview && latestReview.imageBase64" class="review-body">
          <!--左：现场画面 + 检测框-->
          <div class="review-left">
            <div class="review-img-wrap">
              <img :src="latestReview.imageBase64" class="review-img" alt="现场画面" />
              <div v-for="(b, i) in aiResult.boxes" :key="i" class="detect-box"
                   :style="{ left: b.x + '%', top: b.y + '%', width: b.w + '%', height: b.h + '%' }">
                <span class="box-label">{{ b.label }} {{ Math.round(b.conf * 100) }}%</span>
              </div>
            </div>
            <div class="review-meta">
              <span>抓拍时间：{{ latestReview.createTime }}</span>
              <span>设备编号：{{ latestReview.deviceId }}</span>
            </div>
          </div>

          <!--右：AI 复核结论-->
          <div class="ai-panel">
            <div class="ai-title">AI 识别结论</div>
            <div class="ai-verdict" :class="'verdict-' + aiResult.verdict">
              {{ aiResult.verdictText }}
            </div>
            <div class="ai-detail">
              <div class="detect-row" v-for="(c, i) in aiResult.detections" :key="i">
                <span class="detect-class">{{ c.label }}</span>
                <el-progress :percentage="Math.round(c.conf * 100)" :stroke-width="8"
                  :color="c.conf >= 0.8 ? '#f56c6c' : (c.conf >= 0.5 ? '#e6a23c' : '#409eff')" />
              </div>
            </div>
            <div class="ai-basis">
              <div class="basis-title">判定依据</div>
              <p class="basis-text">{{ aiResult.basis }}</p>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无现场画面，报警后自动抓拍上传" :image-size="80" />
      </el-card>

    </el-main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElNotification } from 'element-plus'
import * as echarts from 'echarts'
import { House, Monitor, Bell, Connection, CircleClose, Warning, TrendCharts, Tickets } from '@element-plus/icons-vue'
import { getDeviceList } from '@/api/device'
import { getLatestReview } from '@/api/review'
import { getAlarmStats } from '@/api/alarm'
import { alarmInfo } from '@/utils/alarm'
import LiveLineChart from '@/components/LiveLineChart.vue'

const router = useRouter()
const activeMenu = ref('home')

//当前登录管理员信息
const currentUser = localStorage.getItem('currentUser')
const adminName = ref('管理员')
if (currentUser) {
  try {
    adminName.value = JSON.parse(currentUser).username || '管理员'
  } catch (e) {}
}
const userInitial = computed(() => adminName.value.charAt(0).toUpperCase())

const logout = () => {
  ElMessageBox.confirm('确定要退出当前账号吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    localStorage.removeItem('token')
    localStorage.removeItem('currentUser')
    router.push('/login')
  }).catch(() => {})
}

// 小区设备统计 + 列表预览
const deviceList = ref([])
const stats = computed(() => {
  const list = deviceList.value
  const total = list.length
  const online = list.filter(d => d.online).length
  const offline = total - online
  const alarming = list.filter(d => Number(d.alarm) >= 1).length
  const serious = list.filter(d => Number(d.alarm) >= 2).length
  const onlineRate = total ? Math.round(online / total * 100) : 0
  const offlineRate = total ? Math.round(offline / total * 100) : 0
  return { total, online, offline, alarming, serious, onlineRate, offlineRate }
})

// 页头日期（星期）
const today = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day} 星期${week}`
})

async function loadDeviceList() {
  try {
    const res = await getDeviceList()
    deviceList.value = Array.isArray(res) ? res : []
  } catch (err) {
    console.error('读取设备列表失败', err)
  }
}

// 摄像头现场画面
const latestReview = ref(null)
let reviewTimer = null
let lastReviewId = null // 上次看到的复核记录id，用于避免重复弹窗

// ===== AI 复核结果 =====
// 当前为演示数据；后端接入 YOLO 识别服务后，改为从 latestReview 读取真实字段（见 applyAiResult）
const aiResult = ref({
  verdict: 'smoke',            // normal正常 / steam水汽 / smoke烟雾 / fire明火
  verdictText: '疑似烟雾',
  detections: [
    { label: 'smoke 烟雾', conf: 0.92 },
    { label: 'fire 明火', conf: 0.08 }
  ],
  basis: 'YOLO 检测到烟雾（92%）+ 烟雾浓度超标 → 判定为火灾烟雾，建议立即处置',
  boxes: [
    { x: 22, y: 18, w: 56, h: 48, label: 'smoke', conf: 0.92 }
  ]
})

// 后端接入后：从返回的 review 里读取真实 AI 字段并填充
function applyAiResult(r) {
  if (!r || !r.aiVerdict) return // 后端还没返回 AI 字段时，保留演示数据
  const map = {
    normal: '无异常',
    steam: '水汽（做饭）',
    smoke: '疑似烟雾',
    fire: '检测到明火'
  }
  aiResult.value.verdict = r.aiVerdict
  aiResult.value.verdictText = map[r.aiVerdict] || '无异常'
  if (r.aiDetections) aiResult.value.detections = r.aiDetections
  if (r.aiBoxes) aiResult.value.boxes = r.aiBoxes
  if (r.aiBasis) aiResult.value.basis = r.aiBasis
}

async function loadLatestReview() {
  try {
    const r = await getLatestReview()
    latestReview.value = r
    applyAiResult(r)
    checkReviewAlarm(r)
  } catch (err) {
    console.error('读取现场画面失败', err)
  }
}

//检测到新警情记录时，弹窗提示地址和警情程度
function checkReviewAlarm(r) {
  if (!r) return
  const alarm = Number(r.alarmLevel) || 0
  // 有警情 且 是新记录（和上次不同id）→ 弹一次
  if (alarm >= 1 && r.id !== lastReviewId.value) {
    const info = alarmInfo(alarm)
    ElNotification({
      title: `小区发生警情 · ${info.text}`,
      message: `地址：${r.building ?? '—'}栋 ${r.floor ?? '—'}层 ${r.room ?? '—'}户，烟雾浓度 ${r.smokeConcentration ?? '—'}ppm`,
      type: alarm >= 2 ? 'error' : 'warning',
      duration: 0, // 不自动关闭，确保管理员看到
      position: 'top-right'
    })
  }
  lastReviewId.value = r.id
}

// ===== 告警统计分析（饼图 + 柱状图）=====
const alarmStats = ref({ demo: false, levelDist: [], trend7d: [], deviceDist: [], totalAlarms: 0 })
const levelPieRef = ref(null)
const deviceBarRef = ref(null)
let levelPie = null
let deviceBar = null

// 近7天告警趋势 → LiveLineChart 数据
const trendData = computed(() => (alarmStats.value.trend7d || []).map(d => ({ label: d.name, value: d.value })))

async function loadAlarmStats() {
  try {
    const res = await getAlarmStats()
    if (res) {
      alarmStats.value = res
      renderLevelPie(res.levelDist || [])
      renderDeviceBar(res.deviceDist || [])
    }
  } catch (err) {
    console.error('读取告警统计失败', err)
  }
}

function renderLevelPie(data) {
  if (!levelPieRef.value) return
  if (!levelPie) levelPie = echarts.init(levelPieRef.value)
  const list = data || []
  const total = list.reduce((s, d) => s + (Number(d.value) || 0), 0)
  const palette = [
    { from: '#8adf97', to: '#4db36b' },   // 绿
    { from: '#ffd35c', to: '#f7a13c' },   // 橙
    { from: '#ff9a8b', to: '#f5564a' },   // 红
    { from: '#c6cedd', to: '#9099ab' }    // 灰
  ]
  levelPie.setOption({
    animationDuration: 1200,
    animationEasing: 'cubicOut',
    color: palette.map(c => new echarts.graphic.LinearGradient(0, 0, 0, 1, [
      { offset: 0, color: c.from },
      { offset: 1, color: c.to }
    ])),
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#e4eaf5',
      borderWidth: 1,
      padding: [10, 14],
      textStyle: { color: '#303133' },
      formatter: '{b}<br/><b>{c} 次</b>（{d}%）'
    },
    legend: {
      bottom: 0,
      left: 'center',
      icon: 'circle',
      itemWidth: 9,
      itemHeight: 9,
      itemGap: 18,
      textStyle: { color: '#66789c', fontSize: 12 }
    },
    graphic: total
      ? [{
          type: 'text',
          left: 'center',
          top: '36%',
          style: {
            text: total + '\n告警总数',
            textAlign: 'center',
            fill: '#303133',
            fontSize: 18,
            fontWeight: 'bold',
            lineHeight: 22
          }
        }]
      : [],
    series: [{
      type: 'pie',
      radius: ['46%', '72%'],
      center: ['50%', '42%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 3 },
      label: {
        color: '#66789c',
        fontSize: 12,
        formatter: '{b} {c}次',
        lineHeight: 18
      },
      labelLine: { length: 12, length2: 8, smooth: true, lineStyle: { color: '#c8d2e6' } },
      emphasis: {
        scale: true,
        scaleSize: 6,
        label: { fontWeight: 'bold', color: '#303133' },
        itemStyle: { shadowBlur: 18, shadowColor: 'rgba(22,93,255,0.25)', shadowOffsetY: 4 }
      },
      data: list
    }]
  })
}

function renderDeviceBar(data) {
  if (!deviceBarRef.value) return
  if (!deviceBar) deviceBar = echarts.init(deviceBarRef.value)
  const list = data || []
  const names = list.map(d => d.name)
  deviceBar.setOption({
    animationDuration: 1200,
    animationEasing: 'cubicOut',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(22,93,255,0.06)' } },
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#e4eaf5',
      borderWidth: 1,
      textStyle: { color: '#303133' },
      formatter: '{b}<br/>告警 <b>{c}</b> 次'
    },
    grid: { left: 8, right: 16, top: 32, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: names,
      axisLine: { lineStyle: { color: '#e4eaf5' } },
      axisTick: { show: false },
      axisLabel: {
        color: '#66789c',
        fontSize: 11,
        interval: 0,
        rotate: names.length > 6 ? 30 : 0
      }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#eef1f6' } },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#a3b1c8', fontSize: 11 }
    },
    series: [{
      type: 'bar',
      data: list.map(d => ({
        value: d.value,
        itemStyle: {
          borderRadius: [8, 8, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#6aa1ff' },
            { offset: 1, color: '#165DFF' }
          ])
        }
      })),
      barMaxWidth: 26,
      showBackground: true,
      backgroundStyle: { color: '#f2f5fb', borderRadius: 8 },
      label: {
        show: true,
        position: 'top',
        color: '#165DFF',
        fontWeight: 'bold',
        fontSize: 12,
        formatter: '{c} 次'
      },
      emphasis: {
        itemStyle: { shadowBlur: 12, shadowColor: 'rgba(22,93,255,0.3)' }
      }
    }]
  })
}

function resizeCharts() {
  levelPie && levelPie.resize()
  deviceBar && deviceBar.resize()
}

onMounted(() => {
  loadLatestReview()
  loadDeviceList()
  loadAlarmStats()
  window.addEventListener('resize', resizeCharts)
  reviewTimer = setInterval(() => {
    loadLatestReview()
    loadDeviceList()
  }, 3000)
})
onUnmounted(() => {
  clearInterval(reviewTimer)
  window.removeEventListener('resize', resizeCharts)
  levelPie && levelPie.dispose()
  deviceBar && deviceBar.dispose()
})
</script>

<style scoped>
.admin-wrap {
  display: flex;
  height: 100vh;
}
.aside {
  background: #fff;
  display: flex;
  flex-direction: column;
}
.sidebar-title {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-weight: bold;
  font-size: 15px;
  color: #165DFF;
  border-bottom: 1px solid #eef1f6;
  flex-shrink: 0;
}
.sidebar-menu {
  border-right: none;
  flex: 1;
  padding: 10px 8px;
}
.sidebar-menu .el-menu-item {
  height: 44px;
  line-height: 44px;
  border-radius: 8px;
  margin-bottom: 4px;
  font-size: 14px;
}
.sidebar-menu .el-menu-item:hover {
  background-color: #f2f6fc;
}
.sidebar-menu .el-menu-item.is-active {
  background-color: #e8f1ff;
  color: #165DFF;
  font-weight: 600;
}
/*左下角用户区：悬停展开名字+退出登录*/
.sidebar-user {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-top: 1px solid #eef1f6;
  cursor: pointer;
  overflow: hidden;
  flex-shrink: 0;
}
.user-avatar {
  background: #165DFF;
  color: #fff;
  font-weight: 600;
  flex-shrink: 0;
}
.user-meta {
  display: flex;
  flex-direction: column;
  margin-left: 12px;
  max-width: 0;
  opacity: 0;
  overflow: hidden;
  white-space: nowrap;
  transition: max-width 0.25s ease, opacity 0.2s ease;
}
.sidebar-user:hover .user-meta {
  max-width: 140px;
  opacity: 1;
}
.user-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.logout-btn {
  padding: 0;
  justify-content: flex-start;
  color: #f56c6c;
  font-size: 12px;
}
.main-content {
  background-color: #f5f7fa;
  overflow: auto;
}
/*页头*/
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 16px;
}
.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--text-main);
}
.page-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}
.page-date {
  font-size: 13px;
  color: var(--text-secondary);
}
/*统计栏（白卡 + 彩色点缀）*/
.stat-row {
  margin-bottom: 16px;
}
.stat-card {
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}
.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 28px rgba(31, 45, 61, 0.12);
}
.stat-body {
  display: flex;
  align-items: center;
  gap: 16px;
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}
.stat-icon-blue  { background: #ecf5ff; color: var(--primary); }
.stat-icon-green { background: #f0f9eb; color: var(--success); }
.stat-icon-gray  { background: #f4f4f5; color: var(--info); }
.stat-icon-red   { background: #fef0f0; color: var(--danger); }
.stat-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
}
.stat-num {
  font-size: 30px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.2;
}
.stat-sub {
  font-size: 12px;
  color: var(--info);
}
.alarm-stats-card {
  margin-bottom: 16px;
}
.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  text-align: center;
}
.chart {
  width: 100%;
}
.chart-sm {
  height: 260px;
}
.review-card {
  margin-bottom: 16px;
}
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.review-body {
  display: flex;
  gap: 24px;
  align-items: stretch;
}
.review-left {
  flex: 1.4;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.review-img-wrap {
  position: relative;
  display: inline-block;
  max-width: 100%;
}
.review-img {
  max-width: 100%;
  max-height: 380px;
  border-radius: 8px;
  border: 1px solid #e4eaf5;
  display: block;
}
.detect-box {
  position: absolute;
  border: 2px solid #f56c6c;
  border-radius: 4px;
  pointer-events: none;
}
.box-label {
  position: absolute;
  top: -24px;
  left: -2px;
  background: #f56c6c;
  color: #fff;
  font-size: 12px;
  padding: 1px 6px;
  border-radius: 3px;
  white-space: nowrap;
}
.review-meta {
  color: #66789c;
  font-size: 13px;
  display: flex;
  gap: 20px;
}
/*AI 结论面板*/
.ai-panel {
  flex: 1;
  border: 1px solid #eef1f6;
  border-radius: 10px;
  padding: 18px;
  background: #fafbfd;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.ai-title {
  font-size: 14px;
  font-weight: 600;
  color: #66789c;
}
.ai-verdict {
  font-size: 22px;
  font-weight: 700;
  text-align: center;
  padding: 16px;
  border-radius: 8px;
}
.verdict-normal { color: #67c23a; background: #f0f9eb; }
.verdict-steam { color: #409eff; background: #ecf5ff; }
.verdict-smoke { color: #e6a23c; background: #fdf6ec; }
.verdict-fire { color: #f56c6c; background: #fef0f0; }
.ai-detail {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.detect-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.detect-class {
  width: 100px;
  font-size: 13px;
  color: #303133;
  flex-shrink: 0;
}
.detect-row .el-progress {
  flex: 1;
}
.ai-basis {
  border-top: 1px dashed #e4eaf5;
  padding-top: 12px;
}
.basis-title {
  font-size: 13px;
  font-weight: 600;
  color: #66789c;
  margin-bottom: 6px;
}
.basis-text {
  font-size: 13px;
  color: #303133;
  line-height: 1.6;
  margin: 0;
}
</style>
