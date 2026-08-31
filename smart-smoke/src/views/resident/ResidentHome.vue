<template>
  <div class="resident-wrap">
    <!--侧边栏-->
    <el-aside width="220px" class="aside">
      <div class="sidebar-title">智慧烟感系统</div>
      <el-menu
        class="sidebar-menu"
        active-text-color="#165DFF"
        background-color="#ffffff"
        text-color="#606266"
        :default-active="activeMenu"
      >
        <el-menu-item index="home">
          <el-icon><House /></el-icon>
          <span>主页</span>
        </el-menu-item>
        <el-menu-item index="history" @click="$router.push('/resident/history')">
          <el-icon><Document /></el-icon>
          <span>告警历史</span>
        </el-menu-item>
        <el-menu-item index="repair" @click="$router.push('/resident/repair')">
          <el-icon><Tools /></el-icon>
          <span>设备报修</span>
        </el-menu-item>
      </el-menu>
      <!--左下角用户区：默认只显示头像，悬停展开名字+退出登录-->
      <div class="sidebar-user">
        <el-avatar :size="38" class="user-avatar">{{ userInitial }}</el-avatar>
        <div class="user-meta">
          <span class="user-name">{{ userName }}</span>
          <el-button class="logout-btn" text size="small" @click="logout">退出登录</el-button>
        </div>
      </div>
    </el-aside>
    <!--主内容区-->
    <el-main class="main-content">
      <!--页头-->
      <div class="page-header">
        <div>
          <h2 class="page-title">居家安全监测</h2>
          <p class="page-subtitle">欢迎回来，{{ userName }} · 实时掌握本户传感器状态与告警</p>
        </div>
        <div class="page-date">{{ today }}</div>
      </div>

      <!--首屏安全结论-->
      <el-card class="safety-card" :class="`safety-${safetyState.tone}`" v-reveal>
        <div class="safety-content">
          <div class="safety-main">
            <div class="safety-icon">
              <el-icon v-if="safetyState.tone === 'safe'"><CircleCheck /></el-icon>
              <el-icon v-else-if="safetyState.tone === 'offline'"><CircleClose /></el-icon>
              <el-icon v-else><Warning /></el-icon>
            </div>
            <div class="safety-copy">
              <div class="safety-eyebrow">本户安全状态</div>
              <h3 class="safety-title">{{ safetyState.title }}</h3>
              <p class="safety-desc">{{ safetyState.description }}</p>
              <div class="safety-meta">
                <span><el-icon><Location /></el-icon>{{ locationText }}</span>
                <span><el-icon><Monitor /></el-icon>{{ myDevice ? `设备 #${myDevice.deviceId}` : '暂无绑定设备' }}</span>
                <span><el-icon><Clock /></el-icon>{{ myDevice?.collectTime || '等待首次上报' }}</span>
              </div>
            </div>
          </div>
          <div class="safety-actions">
            <el-button type="primary" @click="$router.push('/resident/history')">查看监测记录</el-button>
            <el-button @click="$router.push('/resident/repair')">设备报修</el-button>
          </div>
        </div>
      </el-card>

      <div class="metric-grid">
        <el-card class="metric-card" v-reveal="{ delay: 50 }">
          <div class="metric-body"><div class="metric-icon metric-blue"><el-icon><Odometer /></el-icon></div><div><div class="metric-label">当前烟雾浓度</div><div class="metric-value">{{ concentrationText }}<small> ppm</small></div><div class="metric-sub">安全参考线 50 ppm</div></div></div>
        </el-card>
        <el-card class="metric-card" v-reveal="{ delay: 90 }">
          <div class="metric-body"><div class="metric-icon metric-orange"><el-icon><Bell /></el-icon></div><div><div class="metric-label">当前警情</div><div class="metric-value metric-text">{{ alarmInfo(myDevice?.alarm || 0).text }}</div><div class="metric-sub">持续监测并分级提醒</div></div></div>
        </el-card>
        <el-card class="metric-card" v-reveal="{ delay: 130 }">
          <div class="metric-body"><div class="metric-icon" :class="myDevice?.online ? 'metric-green' : 'metric-gray'"><el-icon><Connection /></el-icon></div><div><div class="metric-label">设备连接</div><div class="metric-value metric-text">{{ myDevice?.online ? '在线' : '离线' }}</div><div class="metric-sub">{{ myDevice?.online ? '正在接收实时数据' : '请检查电源或网络' }}</div></div></div>
        </el-card>
        <el-card class="metric-card" v-reveal="{ delay: 170 }">
          <div class="metric-body"><div class="metric-icon metric-purple"><el-icon><VideoCamera /></el-icon></div><div class="camera-metric"><div class="metric-label">联动摄像头</div><el-select v-model="selectedCamera" size="small" placeholder="未选择摄像头"><el-option v-for="(c, i) in cameras" :key="c.deviceId || i" :label="c.label || ('摄像头 ' + (i + 1))" :value="c.deviceId" /></el-select><div class="metric-sub">警情触发后用于现场复核</div></div></div>
        </el-card>
      </div>

      <div class="dashboard-grid">
        <el-card class="curve-card" v-reveal="{ delay: 210 }">
          <template #header><div class="card-header-row"><div><div class="section-title">实时烟雾浓度</div><div class="section-desc">趋势变化比单次读数更能反映风险</div></div><div class="header-actions"><span v-if="myDevice?.online" class="live-badge"><span class="live-dot"></span>实时监测</span><span class="window-tag">最近 {{ REALTIME_POINT_COUNT }} 条</span></div></div></template>
          <LiveLineChart :data="realtimeData" :height="286" unit=" ppm" :threshold="50" subtitle="烟雾浓度 (ppm)" hide-slider show-x-axis />
        </el-card>

        <el-card class="status-card" v-reveal="{ delay: 250 }">
          <template #header><div class="card-header-row"><div><div class="section-title">设备连接记录</div><div class="section-desc">最近上线与离线变化</div></div><el-button size="small" text @click="loadStatusHistory">刷新</el-button></div></template>
          <el-timeline v-if="statusHistory.length" class="status-timeline">
            <el-timeline-item v-for="(s, i) in statusHistory.slice(0, 6)" :key="s.id || i" :timestamp="s.changeTime" :type="s.online === 1 ? 'success' : 'danger'" :hollow="s.online !== 1">
              {{ s.online === 1 ? '设备恢复在线' : '设备连接中断' }}
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无连接变化记录" :image-size="60" />
        </el-card>
      </div>

      <el-card class="profile-card" v-reveal="{ delay: 290 }">
        <div class="profile-strip" v-if="user">
          <div class="profile-person"><el-avatar :size="44" class="profile-avatar">{{ userInitial }}</el-avatar><div><div class="profile-name">{{ userName }}</div><div class="profile-phone">{{ user.phone || '未填写手机号' }}</div></div></div>
          <div class="profile-facts">
            <div><span>住户地址</span><b>{{ user.building }}栋 {{ user.floor }}层 {{ user.room }}户</b></div>
            <div><span>绑定设备</span><b>{{ myDevice ? `#${myDevice.deviceId}` : '暂无' }}</b></div>
            <div><span>监测状态</span><b :class="myDevice?.online ? 'text-success' : 'text-danger'">{{ myDevice?.online ? '运行正常' : '连接异常' }}</b></div>
          </div>
        </div>
      </el-card>
    </el-main>
  </div>
</template>

<script setup>
import { ref, watch, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage, ElNotification } from 'element-plus'
import { House, Document, Tools, CircleCheck, CircleClose, Warning, Location, Monitor, Clock, Odometer, Bell, Connection, VideoCamera } from '@element-plus/icons-vue'
import { getDeviceStatusHistory, getSensorHistory } from '@/api/sensor'
import { getMyDevice } from '@/api/device'
import { uploadReview } from '@/api/review'
import { alarmInfo } from '@/utils/alarm'
import LiveLineChart from '@/components/LiveLineChart.vue'

const router = useRouter()
const activeMenu = ref('home')
const user = ref(null)
const userName = computed(() => (user.value && user.value.username) || '用户')
const userInitial = computed(() => userName.value.charAt(0).toUpperCase())

// 页头日期（星期）
const today = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day} 星期${week}`
})

const myDevice = ref(null)   // 本户绑定的传感器（位置 + 在线 + 最新烟雾）
const statusHistory = ref([]) // 设备在线状态变更历史
const REALTIME_POINT_COUNT = 60 // 实时曲线固定展示最近多少条采样
const realtimeData = ref([])  // 最近 N 条烟雾浓度曲线数据
let pollTimer = null
let lastAlarm = false // 上次是否处于告警状态，用于避免重复弹通知
let lastOnline = null // 上次在线状态，用于检测从「在线」变为「离线」

const concentrationText = computed(() => {
  const value = myDevice.value?.smokeConcentration
  return value == null ? '—' : Number(value).toFixed(1)
})

const locationText = computed(() => {
  const d = myDevice.value
  if (!d) return '尚未绑定安装位置'
  return `${d.building}栋 ${d.floor}层 ${d.room}户`
})

const safetyState = computed(() => {
  const device = myDevice.value
  if (!device) return { tone: 'offline', title: '正在获取设备状态', description: '系统正在读取本户烟感设备，请稍候。' }
  if (!device.online) return { tone: 'offline', title: '设备连接异常', description: '当前无法接收实时数据，请检查设备电源或联系物业维修。' }
  const level = Number(device.alarm) || 0
  if (level >= 3) return { tone: 'danger', title: '检测到高级警情', description: '请立即确认烟雾来源，注意疏散安全并联系物业人员。' }
  if (level === 2) return { tone: 'danger', title: '烟雾浓度明显升高', description: '请尽快检查现场情况，远离可能的危险源。' }
  if (level === 1) return { tone: 'warning', title: '烟雾浓度轻度异常', description: '系统正在持续观察，请留意厨房或室内烟雾来源。' }
  return { tone: 'safe', title: '当前居家环境安全', description: '烟感设备在线，烟雾浓度处于正常范围。' }
})

//从登录接口拿用户信息（登录时存进 localStorage 的 currentUser）
const currentUser = localStorage.getItem('currentUser')
if(currentUser){
  try {
    user.value = JSON.parse(currentUser)
  } catch (e) {
    user.value = null
  }
}

// 本户用户id / 绑定设备编号（登录接口返回；旧账号可能没有，回退为空）
const myUserId = computed(() => (user.value && user.value.id) || null)
const myDeviceId = computed(() => (user.value && user.value.deviceId) || null)

//加载本户设备 + 最新数据 + 在线状态（每2秒轮询一次）
async function loadMyDevice() {
  if (!myUserId.value) return
  try {
    const res = await getMyDevice(myUserId.value)
    myDevice.value = res
    checkAlarm(res)
    checkOffline(res)
  } catch (err) {
    ElMessage.error("读取本户传感器数据失败，请检查后端服务")
    console.error(err)
  }
}

//加载设备在线状态变更历史（上线/离线时间线）
async function loadStatusHistory() {
  try {
    const res = await getDeviceStatusHistory(myDeviceId.value)
    statusHistory.value = Array.isArray(res) ? res : []
  } catch (err) {
    console.error('读取在线状态历史失败', err)
  }
}

//加载最近 N 条采样，画实时烟雾浓度曲线（随轮询刷新，有新数据自动更新）
async function loadRealtimeCurve() {
  const deviceId = (myDevice.value && myDevice.value.deviceId) || myDeviceId.value
  if (!deviceId) return
  try {
    const list = await getSensorHistory(deviceId, null, null, REALTIME_POINT_COUNT)
    // 后端倒序返回，反转为正序（时间递增）画曲线
    realtimeData.value = (Array.isArray(list) ? [...list].reverse() : [])
      .map(r => ({
        label: (r.collectTime || '').slice(5, 16), // MM-dd HH:mm
        value: r.smokeConcentration,
        alarm: r.alarm
      }))
  } catch (err) {
    console.error('读取实时浓度曲线失败', err)
  }
}

//检测告警状态变化：从「正常」变为「告警」时推送分级通知
function checkAlarm(device) {
  if (!device) return
  const alarm = Number(device.alarm) || 0
  if (alarm > 0 && !lastAlarm) {
    const info = alarmInfo(alarm)
    const levelMsg = { 1: '轻度超标，请关注', 2: '明显超标，请及时处理', 3: '疑似火情，请立即处置！' }[alarm] || ''
    ElNotification({
      title: `${info.text}`,
      message: `当前烟雾浓度 ${device.smokeConcentration}ppm，${levelMsg}`,
      type: alarm >= 2 ? 'error' : 'warning',
      duration: 0, // 不自动关闭，需手动关闭，确保住户看到
      position: 'top-right'
    })
    // 报警同时抓拍现场画面，上传到管理员系统
    captureOnAlarm(alarm, device.smokeConcentration)
  }
  lastAlarm = alarm > 0
}

//核心：打开电脑摄像头抓一张，压缩后上传（绑定到本户设备）
async function doCapture(alarm, concentration) {
  const constraints = selectedCamera.value
    ? { video: { deviceId: { exact: selectedCamera.value } } }
    : { video: true }
  const stream = await navigator.mediaDevices.getUserMedia(constraints)
  const video = document.createElement('video')
  video.srcObject = stream
  await video.play()
  // 等摄像头真正出画面（loadeddata 事件），再等曝光稳定，避免拍到白屏
  await new Promise(resolve => {
    if (video.readyState >= 2) resolve()
    else video.addEventListener('loadeddata', resolve, { once: true })
  })
  await new Promise(resolve => setTimeout(resolve, 800))
  const canvas = document.createElement('canvas')
  let w = video.videoWidth || 640
  let h = video.videoHeight || 480
  const maxW = 640 // 压缩宽度，减小上传体积
  if (w > maxW) {
    h = Math.round(h * maxW / w)
    w = maxW
  }
  canvas.width = w
  canvas.height = h
  canvas.getContext('2d').drawImage(video, 0, 0, w, h)
  const base64 = canvas.toDataURL('image/jpeg', 0.6)
  stream.getTracks().forEach(t => t.stop())
  await uploadReview({
    deviceId: myDeviceId.value || 1,
    alarmLevel: alarm,
    smokeConcentration: concentration,
    imageBase64: base64
  })
}

//报警时自动抓拍（静默，失败只打印日志）
function captureOnAlarm(alarm, concentration) {
  doCapture(alarm, concentration).catch(err => console.error('抓拍上传失败', err))
}

// 摄像头列表与选择
const cameras = ref([])
const selectedCamera = ref('')
const CAMERA_KEY = 'selectedCameraId'

async function loadCameras() {
  try {
    const devices = await navigator.mediaDevices.enumerateDevices()
    cameras.value = devices.filter(d => d.kind === 'videoinput')
    const saved = localStorage.getItem(CAMERA_KEY)
    // 优先用上次保存的摄像头，否则选第一个
    if (saved && cameras.value.some(c => c.deviceId === saved)) {
      selectedCamera.value = saved
    } else if (cameras.value.length) {
      selectedCamera.value = cameras.value[0].deviceId
    }
  } catch (e) {
    console.error('获取摄像头列表失败', e)
  }
}

// 选择变化时记住，刷新后自动用上次选的摄像头
watch(selectedCamera, (val) => {
  if (val) localStorage.setItem(CAMERA_KEY, val)
})

//检测设备从「在线」变为「离线」时推送一次通知
function checkOffline(device) {
  if (!device) return
  const online = !!device.online
  if (lastOnline === true && !online) {
    ElNotification({
      title: '设备已离线',
      message: '烟雾传感器已断开连接，请检查设备电源或网络。',
      type: 'warning',
      duration: 0, // 不自动关闭，确保住户看到
      position: 'top-right'
    })
  }
  // 在线状态发生变化（上线或离线）时，刷新状态历史时间线
  if (lastOnline !== null && lastOnline !== online) {
    loadStatusHistory()
  }
  lastOnline = online
}

//退出登录
const logout = () => {
  ElMessageBox.confirm(
    '确定要退出当前账号吗？',
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => {
    clearInterval(pollTimer)
    localStorage.removeItem('token')
    localStorage.removeItem('currentUser')
    router.push('/login')
  }).catch(() => {
  })
}

onMounted(() => {
  loadStatusHistory()
  loadMyDevice()
  loadRealtimeCurve()
  loadCameras()
  pollTimer = setInterval(() => {
    loadMyDevice()
    loadRealtimeCurve()
  }, 2000)
})

onUnmounted(() => {
  clearInterval(pollTimer)
})
</script>



<style scoped>
.resident-wrap {
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
  font-size: 17px;
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
.safety-card,
.metric-grid,
.dashboard-grid,
.profile-card {
  margin-bottom: 16px;
}
.safety-card {
  position: relative;
  overflow: hidden;
  border-top: 3px solid var(--success);
}
.safety-card::after {
  content: '';
  position: absolute;
  right: -60px;
  top: -90px;
  width: 230px;
  height: 230px;
  border-radius: 50%;
  background: rgba(103, 194, 58, 0.06);
  pointer-events: none;
}
.safety-warning { border-top-color: var(--warning); }
.safety-warning::after { background: rgba(230, 162, 60, 0.08); }
.safety-danger { border-top-color: var(--danger); }
.safety-danger::after { background: rgba(245, 108, 108, 0.08); }
.safety-offline { border-top-color: var(--info); }
.safety-offline::after { background: rgba(144, 147, 153, 0.08); }
.safety-content {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-height: 124px;
}
.safety-main {
  display: flex;
  align-items: center;
  gap: 18px;
  min-width: 0;
}
.safety-icon {
  width: 64px;
  height: 64px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 18px;
  color: var(--success);
  background: #f0f9eb;
  font-size: 32px;
}
.safety-warning .safety-icon { color: var(--warning); background: #fdf6ec; }
.safety-danger .safety-icon { color: var(--danger); background: #fef0f0; }
.safety-offline .safety-icon { color: var(--info); background: #f4f4f5; }
.safety-eyebrow {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
}
.safety-title {
  margin: 4px 0 5px;
  color: var(--text-main);
  font-size: 24px;
  line-height: 1.25;
}
.safety-desc {
  margin: 0;
  color: var(--text-regular);
  font-size: 13px;
}
.safety-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin-top: 14px;
  color: var(--text-secondary);
  font-size: 12px;
}
.safety-meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.safety-actions {
  display: flex;
  flex-shrink: 0;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}
.metric-card {
  min-width: 0;
  height: 112px;
  overflow: hidden;
}
:deep(.metric-card .el-card__body) {
  display: flex;
  align-items: center;
  height: 100%;
  overflow: hidden;
  box-sizing: border-box;
}
.metric-body {
  display: flex;
  align-items: center;
  gap: 13px;
  width: 100%;
  min-width: 0;
}
.metric-icon {
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 13px;
  font-size: 22px;
}
.metric-blue { color: var(--primary); background: #ecf5ff; }
.metric-green { color: var(--success); background: #f0f9eb; }
.metric-orange { color: var(--warning); background: #fdf6ec; }
.metric-gray { color: var(--info); background: #f4f4f5; }
.metric-purple { color: #7c5ce5; background: #f2efff; }
.metric-label {
  color: var(--text-secondary);
  font-size: 12px;
}
.metric-value {
  margin-top: 3px;
  color: var(--text-main);
  font-size: 27px;
  font-weight: 700;
  line-height: 1.15;
  white-space: nowrap;
}
.metric-value small {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 500;
}
.metric-text {
  font-size: 21px;
}
.metric-sub,
.section-desc {
  margin-top: 4px;
  color: var(--info);
  font-size: 11px;
}
.camera-metric {
  flex: 1;
  min-width: 0;
}
.camera-metric .el-select {
  width: 100%;
  margin-top: 5px;
}
.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 0.8fr);
  gap: 16px;
  align-items: stretch;
}
.curve-card,
.status-card {
  min-width: 0;
}
.section-title {
  color: var(--text-main);
  font-size: 15px;
  font-weight: 650;
}
.window-tag {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
}
.status-timeline {
  max-height: 286px;
  padding: 4px 4px 0;
  overflow-y: auto;
}
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.header-actions,
.live-badge {
  display: flex;
  align-items: center;
}
.header-actions { gap: 10px; }
.live-badge {
  gap: 6px;
  color: var(--success);
  font-size: 12px;
  font-weight: 600;
}
.profile-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}
.profile-person {
  display: flex;
  align-items: center;
  gap: 12px;
}
.profile-avatar {
  flex-shrink: 0;
  color: #fff;
  background: var(--grad-blue);
  font-size: 18px;
  font-weight: 600;
}
.profile-name {
  color: var(--text-main);
  font-size: 16px;
  font-weight: 700;
}
.profile-phone {
  margin-top: 3px;
  color: var(--text-secondary);
  font-size: 12px;
}
.profile-facts {
  display: flex;
  align-items: center;
  gap: 48px;
}
.profile-facts div {
  display: grid;
  gap: 4px;
}
.profile-facts span {
  color: var(--info);
  font-size: 11px;
}
.profile-facts b {
  color: var(--text-main);
  font-size: 13px;
}
.text-success { color: var(--success) !important; }
.text-danger { color: var(--danger) !important; }
@media (max-width: 1180px) {
  .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .dashboard-grid { grid-template-columns: 1fr; }
}
@media (max-width: 760px) {
  .safety-content,
  .profile-strip { align-items: flex-start; flex-direction: column; }
  .safety-actions { width: 100%; }
  .safety-actions .el-button { flex: 1; }
  .metric-grid { grid-template-columns: 1fr; }
  .profile-facts { flex-wrap: wrap; gap: 20px 36px; }
  .page-date { display: none; }
}
</style>
