<template>
  <div class="resident-wrap">
    <el-aside width="220px" class="aside">
      <div class="sidebar-title">智慧烟感系统</div>
      <el-menu
        class="sidebar-menu"
        active-text-color="#165DFF"
        background-color="#ffffff"
        text-color="#606266"
        :default-active="activeMenu"
      >
        <el-menu-item index="home" @click="$router.push('/resident')">
          <el-icon><House /></el-icon>
          <span>主页</span>
        </el-menu-item>
        <el-menu-item index="history">
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

    <el-main class="main-content">
      <!--页头-->
      <div class="page-header">
        <div>
          <h2 class="page-title">告警历史</h2>
          <p class="page-subtitle">本户传感器历史浓度与告警记录</p>
        </div>
        <div class="page-date">{{ today }}</div>
      </div>

      <!--统计卡片-->
      <el-row :gutter="16" class="stat-row">
        <el-col :span="8">
          <el-card class="stat-card">
            <div class="stat-body">
              <div class="stat-icon stat-icon-blue"><el-icon><Document /></el-icon></div>
              <div class="stat-info">
                <div class="stat-label">历史记录数</div>
                <div class="stat-num">{{ totalCount }}</div>
                <div class="stat-sub">本户传感器采样</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card class="stat-card">
            <div class="stat-body">
              <div class="stat-icon stat-icon-orange"><el-icon><Bell /></el-icon></div>
              <div class="stat-info">
                <div class="stat-label">告警次数</div>
                <div class="stat-num">{{ alarmCount }}</div>
                <div class="stat-sub">告警率 {{ alarmRate }}%</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card class="stat-card">
            <div class="stat-body">
              <div class="stat-icon stat-icon-red"><el-icon><Odometer /></el-icon></div>
              <div class="stat-info">
                <div class="stat-label">最高浓度</div>
                <div class="stat-num">{{ maxValue }}</div>
                <div class="stat-sub">告警阈值 50ppm</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!--浓度折线图-->
      <el-card class="chart-card">
        <template #header>
          <div class="card-header-row">
            <span>历史烟雾浓度趋势</span>
            <div class="header-actions">
              <el-date-picker
                v-model="dateRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                size="small"
                @change="load"
              />
              <el-button size="small" @click="resetRange">全部</el-button>
            </div>
          </div>
        </template>
        <LiveLineChart :data="chartData" :height="360" unit=" ppm" :threshold="50" subtitle="烟雾浓度 (ppm)" show-x-axis />
      </el-card>

      <!--告警记录-->
      <el-card>
        <template #header>
          <span>历史告警记录</span>
        </template>
        <el-table :data="alarmRecords" border stripe empty-text="暂无告警记录">
          <el-table-column prop="collectTime" label="告警时间" width="180"/>
          <el-table-column prop="deviceId" label="设备编号" width="100"/>
          <el-table-column prop="smokeConcentration" label="烟雾浓度(ppm)"/>
          <el-table-column label="警情等级" width="120">
            <template #default="scope">
              <el-tag :type="alarmInfo(scope.row.alarm).type" :effect="alarmInfo(scope.row.alarm).effect">
                {{ alarmInfo(scope.row.alarm).text }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { House, Document, Bell, Odometer, Tools } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { getSensorHistory, getSensorAlarmHistory } from '@/api/sensor'
import { alarmInfo } from '@/utils/alarm'
import LiveLineChart from '@/components/LiveLineChart.vue'

const router = useRouter()
const activeMenu = ref('history')

const currentUser = localStorage.getItem('currentUser')
const userName = computed(() => {
  if (currentUser) {
    try { return JSON.parse(currentUser).username || '用户' } catch (e) {}
  }
  return '用户'
})
// 本户绑定设备编号（登录接口返回，用于只查本户历史）
let deviceId = null
if (currentUser) {
  try { deviceId = JSON.parse(currentUser).deviceId || null } catch (e) {}
}
const userInitial = computed(() => userName.value.charAt(0).toUpperCase())

const totalCount = ref(0)
const alarmCount = ref(0)
const maxValue = ref(0)
const alarmRecords = ref([])
const dateRange = ref(null) // 用户选择的时间范围 [开始, 结束]
const chartData = ref([]) // 历史浓度 → LiveLineChart

// 告警率 = 告警次数 / 历史记录数
const alarmRate = computed(() => totalCount.value ? Math.round(alarmCount.value / totalCount.value * 100) : 0)

// 页头日期（星期）
const today = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day} 星期${week}`
})

async function load() {
  const [startTime, endTime] = dateRange.value || []
  try {
    const res = await getSensorHistory(deviceId, startTime, endTime)
    const list = Array.isArray(res) ? res : []
    // 后端倒序返回，反转成正序（时间递增）画折线
    const data = [...list].reverse()

    totalCount.value = data.length
    const vals = data.map(r => r.smokeConcentration || 0)
    maxValue.value = data.length ? Math.max(...vals).toFixed(1) : 0

    // 按分钟聚合：每分钟只取一条，避免折线点过密
    const minuteData = downsampleByMinute(data)
    chartData.value = minuteData.map(r => ({
      label: (r.collectTime || '').slice(5, 16),
      value: r.smokeConcentration,
      alarm: r.alarm
    }))
  } catch (err) {
    ElMessage.error('读取历史数据失败，请检查后端服务')
    console.error(err)
  }

  // 告警记录单独查询（只查 alarm>0，避免被最新正常记录挤出 LIMIT）
  try {
    const alarms = await getSensorAlarmHistory(deviceId, startTime, endTime)
    alarmRecords.value = Array.isArray(alarms) ? alarms : []
    alarmCount.value = alarmRecords.value.length
  } catch (err) {
    console.error('读取历史告警记录失败', err)
  }
}

// 按分钟聚合：每分钟取一条（保留该分钟内浓度最高的一条，避免漏掉峰值）
function downsampleByMinute(list) {
  const map = new Map()
  for (const r of list) {
    const key = (r.collectTime || '').slice(0, 16) // YYYY-MM-DD HH:mm
    const cur = map.get(key)
    const val = Number(r.smokeConcentration) || 0
    if (!cur || val >= (Number(cur.smokeConcentration) || 0)) {
      map.set(key, r)
    }
  }
  return Array.from(map.values())
}

function resetRange() {
  dateRange.value = null
  load()
}

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
    localStorage.removeItem('token')
    localStorage.removeItem('currentUser')
    router.push('/login')
  }).catch(() => {
  })
}

onMounted(() => {
  load()
})
</script>

<style scoped>
.resident-wrap {
  display: flex;
  min-height: 100vh;
  background: #f2f6fc;
}
.aside {
  background: #fff;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  height: 100vh;
  position: sticky;
  top: 0;
}
.sidebar-title {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-size: 17px;
  font-weight: 600;
  color: #165DFF;
  border-bottom: 1px solid #eee;
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
  padding: 24px;
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
/*统计卡片（白卡 + 彩色点缀）*/
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
.chart-card {
  margin-bottom: 16px;
}
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
</style>
