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
        <el-menu-item index="alarms">
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
      <!--左下角用户区-->
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
          <h2 class="page-title">警情事件</h2>
          <p class="page-subtitle">管理员确认警情并监督全过程，到场与结果由应急消防人员上报</p>
        </div>
        <div class="page-date">{{ today }}</div>
      </div>

      <!--统计-->
      <el-row :gutter="16" class="stat-row">
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-body">
              <div class="stat-icon stat-icon-blue"><el-icon><Bell /></el-icon></div>
              <div class="stat-info">
                <div class="stat-label">告警事件总数</div>
                <div class="stat-num">{{ stats.total }}</div>
                <div class="stat-sub">今日 {{ stats.today }} 起</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-body">
              <div class="stat-icon stat-icon-orange"><el-icon><Warning /></el-icon></div>
              <div class="stat-info">
                <div class="stat-label">今日告警</div>
                <div class="stat-num">{{ stats.today }}</div>
                <div class="stat-sub">占总数 {{ stats.todayRate }}%</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-body">
              <div class="stat-icon stat-icon-red"><el-icon><Top /></el-icon></div>
              <div class="stat-info">
                <div class="stat-label">最高警情等级</div>
                <div class="stat-num">{{ stats.levelText }}</div>
                <div class="stat-sub">三级为疑似火情</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-body">
              <div class="stat-icon stat-icon-gray"><el-icon><Monitor /></el-icon></div>
              <div class="stat-info">
                <div class="stat-label">报警设备数</div>
                <div class="stat-num">{{ stats.devices }}</div>
                <div class="stat-sub">平均 {{ stats.avgPerDevice }} 起/台</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!--待处置警情：与设备维修工单完全分离-->
      <el-card class="incident-card">
        <template #header>
          <div class="card-header-row">
            <div>
              <span class="section-heading">警情处置</span>
              <span class="section-hint">待处置 {{ pendingCount }} 起</span>
            </div>
            <el-radio-group v-model="incidentFilter" size="small">
              <el-radio-button value="all">全部</el-radio-button>
              <el-radio-button value="pending">待处置</el-radio-button>
              <el-radio-button value="handled">已处置</el-radio-button>
            </el-radio-group>
          </div>
        </template>
        <el-table :data="filteredIncidents" border stripe v-loading="incidentLoading">
          <el-table-column prop="id" label="事件编号" width="100" />
          <el-table-column prop="alarmTime" label="触发时间" width="180" />
          <el-table-column prop="location" label="安装位置" min-width="180" />
          <el-table-column prop="deviceId" label="设备编号" width="110" />
          <el-table-column label="处置进度" width="120">
            <template #default="scope">
              <el-tag :type="processInfo(scope.row).type">
                {{ processInfo(scope.row).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="管理员确认" width="110">
            <template #default="scope">{{ scope.row.confirmerName || '—' }}</template>
          </el-table-column>
          <el-table-column label="应急消防员" width="120">
            <template #default="scope">{{ scope.row.handlerName || '待到场' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="210" fixed="right">
            <template #default="scope">
              <el-button size="small" plain @click="openProcessDetail(scope.row)">流程详情</el-button>
              <el-button v-if="processKey(scope.row) === 'pending'" type="danger" size="small" @click="confirmIncident(scope.row)">确认警情</el-button>
              <span v-else class="handled-text">{{ processKey(scope.row) === 'handled' ? '已完成' : '消防员处理中' }}</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!incidentLoading && !filteredIncidents.length" description="暂无相关警情事件" :image-size="72" />
      </el-card>

      <!--告警事件列表-->
      <el-card>
        <template #header>
          <div class="card-header-row">
            <span>历史警情追溯</span>
            <el-button type="primary" size="small" @click="loadList">刷新</el-button>
          </div>
        </template>
        <el-table :data="alarmList" border stripe v-loading="loading">
          <el-table-column label="告警时间" width="300">
            <template #default="scope">
              <div>{{ scope.row.startTime }}</div>
              <div class="sub-time">至 {{ scope.row.endTime }}</div>
            </template>
          </el-table-column>
          <el-table-column label="安装位置" width="150">
            <template #default="scope">
              {{ scope.row.building }}栋 {{ scope.row.floor }}层 {{ scope.row.room }}户
            </template>
          </el-table-column>
          <el-table-column prop="peakConcentration" label="峰值浓度(ppm)" width="130"/>
          <el-table-column label="警情等级" width="120">
            <template #default="scope">
              <el-tag :type="alarmInfo(scope.row.maxLevel).type" :effect="alarmInfo(scope.row.maxLevel).effect">
                {{ alarmInfo(scope.row.maxLevel).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="recordCount" label="持续点数" width="100"/>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="scope">
              <el-button type="primary" size="small" @click="openDetail(scope.row)">查看详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-main>

    <!--告警详情抽屉-->
    <el-drawer v-model="drawerVisible" title="告警详情" size="55%">
      <div v-if="detail" class="detail-body">
        <div class="sec-title">告警时段浓度变化</div>
        <LiveLineChart :data="detailPoints" :height="300" unit=" ppm" :threshold="50" subtitle="浓度 (ppm)" show-x-axis />

        <div class="sec-title" style="margin-top: 24px">同时段报警设备（{{ detail.simultaneousDevices.length }}）</div>
        <div class="simu-list">
          <el-tag v-for="(d, i) in detail.simultaneousDevices" :key="i" effect="plain" class="simu-tag">
            {{ d.building }}栋 {{ d.floor }}层 {{ d.room }}户
          </el-tag>
          <el-empty v-if="!detail.simultaneousDevices.length" description="该时段无其他设备报警" :image-size="60" />
        </div>

        <div class="sec-title" style="margin-top: 24px">AI 智能分析建议</div>
        <el-alert :title="detail.aiSuggestion" type="info" :closable="false" show-icon />
      </div>
    </el-drawer>

    <!--警情处置全过程-->
    <el-drawer v-model="processDrawerVisible" title="警情处置流程" size="480px">
      <div v-if="processIncident" class="process-detail">
        <div class="process-summary">
          <div><span>事件编号</span><b>#{{ processIncident.id }}</b></div>
          <div><span>报警位置</span><b>{{ processIncident.location }}</b></div>
          <div><span>当前进度</span><el-tag :type="processInfo(processIncident).type">{{ processInfo(processIncident).text }}</el-tag></div>
        </div>

        <div class="sec-title">处置时间轴</div>
        <el-timeline class="process-timeline">
          <el-timeline-item :timestamp="processIncident.alarmTime" type="danger">
            <b>警情触发</b><p>烟感设备 #{{ processIncident.deviceId }} 生成警情事件</p>
          </el-timeline-item>
          <el-timeline-item :timestamp="processIncident.confirmTime || processIncident.responseTime || '尚未确认'" :type="(processIncident.confirmTime || processIncident.responseTime) ? 'primary' : 'info'" :hollow="!(processIncident.confirmTime || processIncident.responseTime)">
            <b>管理员确认</b><p>{{ (processIncident.confirmTime || processIncident.responseTime) ? `${processIncident.confirmerName || '管理员'} 已确认，任务已移交应急消防人员` : '等待管理员确认警情' }}</p>
          </el-timeline-item>
          <el-timeline-item :timestamp="processIncident.arrivalTime || '尚未到场'" :type="processIncident.arrivalTime ? 'warning' : 'info'" :hollow="!processIncident.arrivalTime">
            <b>消防人员到场</b><p>{{ processIncident.arrivalTime ? `${processIncident.handlerName || '应急消防员'} 已确认到场并开展核查` : '等待应急消防人员确认到场' }}</p>
          </el-timeline-item>
          <el-timeline-item :timestamp="processIncident.handleTime || '尚未完成'" :type="processIncident.handleTime ? 'success' : 'info'" :hollow="!processIncident.handleTime">
            <b>结果上报</b><p>{{ processIncident.handleTime ? resultText(processIncident.handleResult) : '等待应急消防人员上报现场结果' }}</p>
          </el-timeline-item>
        </el-timeline>

        <div v-if="processIncident.status === 1" class="result-panel">
          <div class="result-title">现场处置结论</div>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="处置结果">{{ resultText(processIncident.handleResult) }}</el-descriptions-item>
            <el-descriptions-item label="处置人员">{{ processIncident.handlerName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="现场说明">{{ processIncident.handleRemark || '未填写' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div v-if="processIncident.status !== 1" class="drawer-actions">
          <el-button v-if="processKey(processIncident) === 'pending'" type="danger" @click="confirmIncident(processIncident)">管理员确认警情</el-button>
          <el-alert v-else title="已移交应急消防人员，管理员仅查看处置进度" type="info" :closable="false" show-icon />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { House, Monitor, Bell, Warning, Top, TrendCharts, Tickets } from '@element-plus/icons-vue'
import { getAlarmHistory, getAlarmDetail, getAlarmIncidents, confirmAlarmIncident } from '@/api/alarm'
import { alarmInfo } from '@/utils/alarm'
import LiveLineChart from '@/components/LiveLineChart.vue'

const router = useRouter()
const activeMenu = ref('alarms')

//当前登录管理员信息
const currentUser = localStorage.getItem('currentUser')
const userName = computed(() => {
  if (currentUser) {
    try { return JSON.parse(currentUser).username || '管理员' } catch (e) {}
  }
  return '管理员'
})
const userInitial = computed(() => userName.value.charAt(0).toUpperCase())

//告警事件列表
const alarmList = ref([])
const loading = ref(false)
const incidents = ref([])
const incidentLoading = ref(false)
const incidentFilter = ref('all')
const processDrawerVisible = ref(false)
const processIncident = ref(null)

const currentAdminId = computed(() => {
  try { return JSON.parse(currentUser || '{}').id || null } catch (e) { return null }
})

const pendingCount = computed(() => incidents.value.filter(item => item.status !== 1).length)
const filteredIncidents = computed(() => {
  if (incidentFilter.value === 'pending') return incidents.value.filter(item => item.status !== 1)
  if (incidentFilter.value === 'handled') return incidents.value.filter(item => item.status === 1)
  return incidents.value
})

function localDateStr() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const stats = computed(() => {
  const list = alarmList.value
  const deviceIds = new Set(list.map(e => e.deviceId))
  const maxLevel = list.reduce((m, e) => Math.max(m, e.maxLevel || 0), 0)
  const total = list.length
  const todayCount = list.filter(e => (e.startTime || '').startsWith(localDateStr())).length
  const devices = deviceIds.size
  return {
    total,
    today: todayCount,
    maxLevel,
    devices,
    todayRate: total ? Math.round(todayCount / total * 100) : 0,
    avgPerDevice: devices ? (total / devices).toFixed(1) : 0,
    levelText: maxLevel === 0 ? '暂无' : (['一级', '二级', '三级'][maxLevel - 1] || '三级')
  }
})

// 页头日期（星期）
const today = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day} 星期${week}`
})

async function loadList() {
  loading.value = true
  try {
    const res = await getAlarmHistory()
    alarmList.value = Array.isArray(res) ? res : []
  } catch (err) {
    ElMessage.error('读取告警记录失败，请检查后端服务')
    console.error(err)
  } finally {
    loading.value = false
  }
}

async function loadIncidents() {
  incidentLoading.value = true
  try {
    const res = await getAlarmIncidents()
    incidents.value = Array.isArray(res) ? res : []
  } catch (err) {
    ElMessage.error('读取警情处置列表失败，请检查后端服务')
    console.error(err)
  } finally {
    incidentLoading.value = false
  }
}

function processKey(row) {
  if (row.status === 1) return 'handled'
  return row.processStatus || 'pending'
}

function processInfo(row) {
  return {
    pending: { text: '待管理员确认', type: 'danger' },
    confirmed: { text: '待消防员到场', type: 'primary' },
    responding: { text: '待消防员到场', type: 'primary' },
    arrived: { text: '现场处置中', type: 'warning' },
    handled: { text: '已完成', type: 'success' }
  }[processKey(row)] || { text: '待管理员确认', type: 'danger' }
}

function resultText(result) {
  const map = {
    '真实火情': '确认真实火情，已启动应急处置',
    '生活烟雾': '确认为烹饪或吸烟等生活烟雾',
    '环境干扰': '确认为水蒸气等环境干扰',
    '设备异常': '确认为设备误报或硬件故障',
    '现场无异常': '现场检查未发现异常'
  }
  return map[result] || result || '未填写'
}

function openProcessDetail(row) {
  processIncident.value = row
  processDrawerVisible.value = true
}

function replaceIncident(updated) {
  const index = incidents.value.findIndex(item => item.id === updated.id)
  if (index >= 0) incidents.value[index] = updated
  if (processIncident.value?.id === updated.id) processIncident.value = updated
}

async function confirmIncident(row) {
  try {
    await ElMessageBox.confirm(
      `确认 ${row.location || `设备 #${row.deviceId}`} 的警情并移交应急消防人员？`,
      '确认警情',
      { confirmButtonText: '确认并移交', cancelButtonText: '取消', type: 'warning' }
    )
    const res = await confirmAlarmIncident(row.id, currentAdminId.value)
    if (res?.code !== 200) return ElMessage.error(res?.msg || '确认失败')
    replaceIncident(res.data)
    ElMessage.success('警情已确认，已进入应急消防任务池')
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error('确认警情失败，请检查后端服务')
  }
}

//详情抽屉
const drawerVisible = ref(false)
const detail = ref(null)
const detailPoints = ref([]) // 告警时段浓度 → LiveLineChart

async function openDetail(row) {
  try {
    const res = await getAlarmDetail({
      deviceId: row.deviceId,
      startTime: row.startTime,
      endTime: row.endTime
    })
    detail.value = res
    detailPoints.value = (res && res.points ? res.points : []).map(p => ({
      label: (p.collectTime || '').slice(5, 16),
      value: p.smokeConcentration
    }))
    drawerVisible.value = true
  } catch (err) {
    ElMessage.error('读取告警详情失败')
    console.error(err)
  }
}

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

onMounted(() => {
  loadIncidents()
  loadList()
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
.stat-icon-orange{ background: #fdf6ec; color: var(--warning); }
.stat-icon-red   { background: #fef0f0; color: var(--danger); }
.stat-icon-gray  { background: #f4f4f5; color: var(--info); }
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
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.incident-card {
  margin-bottom: 16px;
  border-top: 3px solid #f56c6c;
}
.section-heading {
  font-weight: 600;
  color: #303133;
}
.section-hint {
  margin-left: 10px;
  font-size: 12px;
  color: #f56c6c;
}
.handled-text {
  color: #67c23a;
  font-size: 13px;
}
.process-detail {
  padding: 0 4px 24px;
}
.process-summary {
  display: grid;
  gap: 10px;
  margin-bottom: 24px;
  padding: 16px;
  border: 1px solid #e7ebf2;
  border-radius: 14px;
  background: #f8faff;
}
.process-summary div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.process-summary span {
  color: #909399;
  font-size: 12px;
}
.process-summary b {
  color: #303133;
  font-size: 13px;
}
.process-timeline {
  padding: 5px 4px 0;
}
.process-timeline b {
  color: #303133;
  font-size: 14px;
}
.process-timeline p {
  margin: 5px 0 0;
  color: #66789c;
  font-size: 12px;
  line-height: 1.6;
}
.result-panel {
  margin-top: 8px;
}
.result-title {
  margin-bottom: 12px;
  color: #303133;
  font-size: 15px;
  font-weight: 600;
}
.drawer-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #eef1f6;
}
.sub-time {
  color: #909399;
  font-size: 12px;
}
.detail-body {
  padding: 0 4px;
}
.sec-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}
.simu-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.simu-tag {
  font-size: 13px;
}
</style>
