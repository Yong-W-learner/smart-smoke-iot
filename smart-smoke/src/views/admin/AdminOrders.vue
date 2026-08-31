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
        <el-menu-item index="orders">
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
      <div class="page-header">
        <div>
          <h2 class="page-title">设备运维</h2>
          <p class="page-subtitle">跟踪居民设备报修、维修员接单及维修闭环，不包含警情处置</p>
        </div>
        <div class="page-date">{{ today }}</div>
      </div>

      <div class="stat-row">
        <div class="stat-col">
          <el-card class="stat-card">
            <div class="stat-body">
              <div class="stat-icon icon-blue"><el-icon><Monitor /></el-icon></div>
              <div><div class="stat-label">设备总数</div><div class="stat-num">{{ deviceList.length }}</div><div class="stat-sub">纳入运维台账</div></div>
            </div>
          </el-card>
        </div>
        <div class="stat-col">
          <el-card class="stat-card">
            <div class="stat-body">
              <div class="stat-icon icon-green"><el-icon><CircleCheck /></el-icon></div>
              <div><div class="stat-label">在线设备</div><div class="stat-num">{{ onlineCount }}</div><div class="stat-sub">在线率 {{ onlineRate }}%</div></div>
            </div>
          </el-card>
        </div>
        <div class="stat-col">
          <el-card class="stat-card">
            <div class="stat-body">
              <div class="stat-icon icon-orange"><el-icon><Tools /></el-icon></div>
              <div><div class="stat-label">待完成维修</div><div class="stat-num">{{ unfinishedOrderCount }}</div><div class="stat-sub">待接单与维修中</div></div>
            </div>
          </el-card>
        </div>
        <div class="stat-col">
          <el-card class="stat-card">
            <div class="stat-body">
              <div class="stat-icon icon-red"><el-icon><Warning /></el-icon></div>
              <div><div class="stat-label">自检异常设备</div><div class="stat-num">{{ abnormalDeviceCount }}</div><div class="stat-sub">按最近一次结果统计</div></div>
            </div>
          </el-card>
        </div>
      </div>

      <el-card class="self-test-card">
        <template #header>
          <div class="card-header-row">
            <div>
              <div class="section-title">设备自检</div>
              <div class="section-desc">检查通信、数据上报、蜂鸣器和LED，并保存可追溯记录</div>
            </div>
            <el-button type="primary" :icon="Operation" @click="openSelfTest">开始设备自检</el-button>
          </div>
        </template>
        <el-table :data="selfTestList.slice(0, 8)" border stripe v-loading="selfTestLoading">
          <el-table-column prop="testTime" label="自检时间" width="170" />
          <el-table-column prop="deviceId" label="设备编号" width="100" />
          <el-table-column label="检查项目" min-width="300">
            <template #default="scope">
              <div class="check-tags">
                <el-tag size="small" :type="scope.row.onlineOk ? 'success' : 'danger'">通信{{ scope.row.onlineOk ? '正常' : '异常' }}</el-tag>
                <el-tag size="small" :type="scope.row.telemetryOk ? 'success' : 'danger'">数据{{ scope.row.telemetryOk ? '正常' : '异常' }}</el-tag>
                <el-tag size="small" :type="scope.row.beepObservedOk ? 'success' : 'danger'">蜂鸣器{{ scope.row.beepObservedOk ? '正常' : '异常' }}</el-tag>
                <el-tag size="small" :type="scope.row.ledObservedOk ? 'success' : 'danger'">LED{{ scope.row.ledObservedOk ? '正常' : '异常' }}</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="自检结论" width="110">
            <template #default="scope">
              <el-tag :type="scope.row.result === 'passed' ? 'success' : 'danger'" effect="dark">
                {{ scope.row.result === 'passed' ? '通过' : '存在异常' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="operatorName" label="操作人" width="100" />
          <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="!selfTestLoading && !selfTestList.length" description="尚无自检记录" :image-size="68" />
      </el-card>

      <el-card class="order-card">
        <template #header>
          <div class="card-header-row">
            <div>
              <div class="section-title">维修工单</div>
              <div class="section-desc">居民报修、维修员接单和维修结果闭环</div>
            </div>
            <div class="header-actions">
              <el-radio-group v-model="statusFilter" size="small">
                <el-radio-button value="all">全部</el-radio-button>
                <el-radio-button value="pending">待接单</el-radio-button>
                <el-radio-button value="accepted">已接单</el-radio-button>
                <el-radio-button value="closed">已关闭</el-radio-button>
              </el-radio-group>
              <el-button size="small" @click="loadAll">刷新</el-button>
            </div>
          </div>
        </template>

        <el-table :data="filteredList" border stripe v-loading="loading">
          <el-table-column prop="orderNo" label="工单号" width="180"/>
          <el-table-column label="类型" width="90">
            <template #default="scope">
              <el-tag :type="orderType(scope.row.type).type" effect="plain">
                {{ orderType(scope.row.type).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="170" show-overflow-tooltip/>
          <el-table-column prop="description" label="故障描述" min-width="190" show-overflow-tooltip/>
          <el-table-column label="位置" width="140">
            <template #default="scope">{{ locText(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="报修人" width="100">
            <template #default="scope">
              <span v-if="scope.row.reporterName">{{ scope.row.reporterName }}</span>
              <span v-else class="empty-cell">—</span>
            </template>
          </el-table-column>
          <el-table-column label="维修员" width="100">
            <template #default="scope">
              <span v-if="scope.row.repairerName">{{ scope.row.repairerName }}</span>
              <span v-else class="empty-cell">—</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="scope">
              <el-tag :type="orderStatus(scope.row.status).type" :effect="orderStatus(scope.row.status).effect">
                {{ orderStatus(scope.row.status).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="170"/>
          <el-table-column label="最近进度" width="170">
            <template #default="scope">{{ scope.row.closeTime || scope.row.acceptTime || scope.row.createTime || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="scope">
              <el-button
                v-if="scope.row.status !== 'closed'"
                type="danger"
                size="small"
                plain
                @click="handleClose(scope.row)"
              >关闭工单</el-button>
              <span v-else class="empty-cell">—</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && !filteredList.length" description="暂无设备运维工单" :image-size="80" />
      </el-card>
    </el-main>

    <el-dialog v-model="selfTestVisible" title="设备自检" width="640px" :close-on-click-modal="!testing">
      <div class="self-test-dialog">
        <el-alert
          title="蜂鸣器将短暂鸣响、LED将短暂亮起，请提前告知现场人员，避免引起恐慌。"
          type="warning"
          :closable="false"
          show-icon
        />

        <el-form label-position="top">
          <el-form-item label="选择设备">
            <el-select v-model="selfTestDeviceId" placeholder="请选择在线真机" style="width: 100%" :disabled="testing || testExecuted">
              <el-option
                v-for="device in deviceList"
                :key="device.deviceId"
                :value="device.deviceId"
                :disabled="device.deviceId !== 1 || !device.online"
                :label="`设备 #${device.deviceId} · ${device.building}栋 ${device.floor}层 ${device.room}户 · ${device.online ? '在线' : '离线'}`"
              />
            </el-select>
          </el-form-item>
        </el-form>

        <div class="test-steps">
          <div v-for="item in selfTestSteps" :key="item.key" class="test-step">
            <div class="step-icon" :class="`step-${item.state}`">
              <el-icon v-if="item.state === 'success'"><Check /></el-icon>
              <el-icon v-else-if="item.state === 'failed'"><Close /></el-icon>
              <el-icon v-else><MoreFilled /></el-icon>
            </div>
            <div class="step-copy"><div class="step-name">{{ item.name }}</div><div class="step-desc">{{ item.desc }}</div></div>
            <el-tag :type="stepTag(item.state).type" size="small">{{ stepTag(item.state).text }}</el-tag>
          </div>
        </div>

        <div v-if="testExecuted" class="observe-panel">
          <div class="observe-title">现场观察确认</div>
          <div class="observe-row">
            <span>蜂鸣器是否实际鸣响？</span>
            <el-radio-group v-model="beepObserved">
              <el-radio-button :value="true">正常</el-radio-button>
              <el-radio-button :value="false">异常</el-radio-button>
            </el-radio-group>
          </div>
          <div class="observe-row">
            <span>LED是否实际亮起？</span>
            <el-radio-group v-model="ledObserved">
              <el-radio-button :value="true">正常</el-radio-button>
              <el-radio-button :value="false">异常</el-radio-button>
            </el-radio-group>
          </div>
          <el-input v-model="selfTestRemark" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="选填：记录异常现象或现场情况" />
        </div>
      </div>
      <template #footer>
        <el-button @click="selfTestVisible = false" :disabled="testing">取消</el-button>
        <el-button v-if="!testExecuted" type="primary" :loading="testing" @click="runSelfTest">{{ testing ? '正在自检' : '开始自检' }}</el-button>
        <el-button v-else type="primary" :loading="savingTest" @click="saveSelfTest">保存自检结果</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { House, Monitor, Bell, TrendCharts, Tickets, CircleCheck, Tools, Warning, Operation, Check, Close, MoreFilled } from '@element-plus/icons-vue'
import { getWorkOrderList, closeWorkOrder } from '@/api/workOrder'
import { getDeviceList, sendDeviceCommand } from '@/api/device'
import { getDeviceSelfTests, createDeviceSelfTest } from '@/api/deviceSelfTest'
import { orderStatus, orderType } from '@/utils/workOrder'

const router = useRouter()
const activeMenu = ref('orders')

//当前登录管理员信息
const currentUser = JSON.parse(localStorage.getItem('currentUser') || '{}')
const userName = computed(() => currentUser.username || '管理员')
const userInitial = computed(() => userName.value.charAt(0).toUpperCase())

const today = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day} 星期${week}`
})

const orderList = ref([])
const loading = ref(false)
const statusFilter = ref('all')
const deviceList = ref([])
const selfTestList = ref([])
const selfTestLoading = ref(false)

const onlineCount = computed(() => deviceList.value.filter(d => d.online).length)
const onlineRate = computed(() => deviceList.value.length ? Math.round(onlineCount.value / deviceList.value.length * 100) : 0)
const unfinishedOrderCount = computed(() => orderList.value.filter(o => o.status !== 'closed').length)
const abnormalDeviceCount = computed(() => {
  const latest = new Map()
  selfTestList.value.forEach(item => {
    if (!latest.has(item.deviceId)) latest.set(item.deviceId, item)
  })
  return [...latest.values()].filter(item => item.result !== 'passed').length
})

const filteredList = computed(() => {
  if (statusFilter.value === 'all') return orderList.value
  return orderList.value.filter(o => o.status === statusFilter.value)
})

function locText(row) {
  const b = row.building, f = row.floor, r = row.room
  if (b == null || f == null || r == null) return '—'
  return `${b}栋 ${f}层 ${r}户`
}

async function loadList() {
  loading.value = true
  try {
    const res = await getWorkOrderList()
    orderList.value = Array.isArray(res) ? res : []
  } catch (err) {
    ElMessage.error('读取工单列表失败，请检查后端服务')
    console.error(err)
  } finally {
    loading.value = false
  }
}

async function loadDevices() {
  try {
    const res = await getDeviceList()
    deviceList.value = Array.isArray(res) ? res : []
  } catch (err) {
    console.error('读取设备运维概览失败', err)
  }
}

async function loadSelfTests() {
  selfTestLoading.value = true
  try {
    const res = await getDeviceSelfTests()
    selfTestList.value = Array.isArray(res) ? res : []
  } catch (err) {
    ElMessage.error('读取设备自检记录失败')
    console.error(err)
  } finally {
    selfTestLoading.value = false
  }
}

function loadAll() {
  loadList()
  loadDevices()
  loadSelfTests()
}

const selfTestVisible = ref(false)
const selfTestDeviceId = ref(null)
const testing = ref(false)
const savingTest = ref(false)
const testExecuted = ref(false)
const beepObserved = ref(null)
const ledObserved = ref(null)
const selfTestRemark = ref('')
const testState = ref({ online: 'waiting', telemetry: 'waiting', beep: 'waiting', led: 'waiting' })

const selectedDevice = computed(() => deviceList.value.find(d => d.deviceId === selfTestDeviceId.value))
const selfTestSteps = computed(() => [
  { key: 'online', name: '设备通信', desc: '检查设备是否连接华为云 IoTDA', state: testState.value.online },
  { key: 'telemetry', name: '数据上报', desc: '检查是否已有实时烟雾浓度', state: testState.value.telemetry },
  { key: 'beep', name: '蜂鸣器指令', desc: '短时开启后自动关闭', state: testState.value.beep },
  { key: 'led', name: 'LED 指令', desc: '短时开启后自动关闭', state: testState.value.led }
])

function stepTag(state) {
  if (state === 'success') return { text: '通过', type: 'success' }
  if (state === 'failed') return { text: '异常', type: 'danger' }
  if (state === 'running') return { text: '检查中', type: 'warning' }
  return { text: '待检查', type: 'info' }
}

function openSelfTest() {
  const realDevice = deviceList.value.find(d => d.deviceId === 1 && d.online)
  selfTestDeviceId.value = realDevice?.deviceId || null
  testExecuted.value = false
  beepObserved.value = null
  ledObserved.value = null
  selfTestRemark.value = ''
  testState.value = { online: 'waiting', telemetry: 'waiting', beep: 'waiting', led: 'waiting' }
  selfTestVisible.value = true
}

const wait = ms => new Promise(resolve => setTimeout(resolve, ms))

async function issueCommand(target, state) {
  const res = await sendDeviceCommand(selfTestDeviceId.value, { target, state })
  if (res?.code !== 200) throw new Error(res?.msg || '命令下发失败')
}

async function runSelfTest() {
  const device = selectedDevice.value
  if (!device) {
    ElMessage.warning('请选择可自检的在线真机')
    return
  }
  testing.value = true
  testState.value.online = device.online ? 'success' : 'failed'
  testState.value.telemetry = device.smokeConcentration != null ? 'success' : 'failed'

  try {
    testState.value.beep = 'running'
    await issueCommand('BEEP', 'ON')
    await wait(1500)
    await issueCommand('BEEP', 'OFF')
    testState.value.beep = 'success'
  } catch (err) {
    testState.value.beep = 'failed'
    ElMessage.error(err.message || '蜂鸣器自检失败')
  }

  try {
    testState.value.led = 'running'
    await issueCommand('LED', 'ON')
    await wait(1500)
    await issueCommand('LED', 'OFF')
    testState.value.led = 'success'
  } catch (err) {
    testState.value.led = 'failed'
    ElMessage.error(err.message || 'LED自检失败')
  } finally {
    // 无论中途是否异常，都补发关闭命令，避免声光设备保持开启。
    await Promise.allSettled([
      sendDeviceCommand(device.deviceId, { target: 'BEEP', state: 'OFF' }),
      sendDeviceCommand(device.deviceId, { target: 'LED', state: 'OFF' })
    ])
    testing.value = false
    testExecuted.value = true
  }
}

async function saveSelfTest() {
  if (beepObserved.value === null || ledObserved.value === null) {
    ElMessage.warning('请确认蜂鸣器和LED的现场实际表现')
    return
  }
  savingTest.value = true
  try {
    const res = await createDeviceSelfTest({
      deviceId: selfTestDeviceId.value,
      operatorId: currentUser.id,
      onlineOk: testState.value.online === 'success',
      telemetryOk: testState.value.telemetry === 'success',
      beepCommandOk: testState.value.beep === 'success',
      beepObservedOk: beepObserved.value,
      ledCommandOk: testState.value.led === 'success',
      ledObservedOk: ledObserved.value,
      remark: selfTestRemark.value
    })
    if (res?.code === 200) {
      ElMessage.success(res.data?.result === 'passed' ? '设备自检通过' : '已记录设备自检异常')
      selfTestVisible.value = false
      loadSelfTests()
    } else {
      ElMessage.error(res?.msg || '保存自检结果失败')
    }
  } catch (err) {
    ElMessage.error('保存自检结果失败，请检查后端服务')
    console.error(err)
  } finally {
    savingTest.value = false
  }
}

async function handleClose(row) {
  try {
    const { value } = await ElMessageBox.prompt('请输入关闭备注（可留空）', '关闭工单', {
      confirmButtonText: '确认关闭',
      cancelButtonText: '取消',
      inputPlaceholder: '如：维修员长时间未处理，管理员兜底关闭',
      inputType: 'textarea'
    })
    const res = await closeWorkOrder({ id: row.id, operatorId: currentUser.id, remark: value || '' })
    if (res.code === 200) {
      ElMessage.success('工单已关闭')
      loadList()
    } else {
      ElMessage.error(res.msg || '关闭失败')
    }
  } catch (err) {
    if (err === 'cancel' || err === 'close') return
    ElMessage.error('关闭失败，请检查后端服务')
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
  loadAll()
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
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.main-content::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
}
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
  color: #303133;
}
.page-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: #909399;
}
.page-date {
  font-size: 13px;
  color: #909399;
}
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.self-test-card {
  margin-bottom: 16px;
}
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.stat-col {
  min-width: 0;
}
.stat-card {
  height: 112px;
  overflow: hidden;
}
:deep(.stat-card .el-card__body) {
  display: flex;
  align-items: center;
  height: 100%;
  overflow: hidden;
  box-sizing: border-box;
}
.stat-body {
  display: flex;
  align-items: center;
  gap: 14px;
}
.stat-icon {
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  border-radius: 13px;
  font-size: 22px;
}
.icon-blue { color: #165dff; background: #eaf2ff; }
.icon-green { color: #2f9e44; background: #ecf9ee; }
.icon-orange { color: #d97706; background: #fff5e5; }
.icon-red { color: #e5484d; background: #fff0f0; }
.stat-label {
  color: #667085;
  font-size: 13px;
}
.stat-num {
  margin-top: 2px;
  color: #1d2939;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.15;
}
.stat-sub,
.section-desc {
  margin-top: 4px;
  color: #98a2b3;
  font-size: 12px;
}
.section-title {
  color: #303133;
  font-size: 15px;
  font-weight: 650;
}
.self-test-card {
  border-top: 3px solid #165dff;
}
.order-card {
  margin-bottom: 20px;
}
.check-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.self-test-dialog {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.test-steps {
  display: grid;
  gap: 10px;
}
.test-step {
  display: grid;
  grid-template-columns: 38px 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #e7ebf2;
  border-radius: 12px;
  background: #fbfcfe;
}
.step-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  color: #8b95a5;
  background: #eef1f5;
}
.step-success { color: #2f9e44; background: #eaf8ed; }
.step-failed { color: #e5484d; background: #fff0f0; }
.step-running { color: #d97706; background: #fff5e5; }
.step-name {
  color: #344054;
  font-size: 14px;
  font-weight: 600;
}
.step-desc {
  margin-top: 3px;
  color: #98a2b3;
  font-size: 12px;
}
.observe-panel {
  display: grid;
  gap: 12px;
  padding: 15px;
  border-radius: 12px;
  background: #f6f8fb;
}
.observe-title {
  color: #344054;
  font-weight: 650;
}
.observe-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #475467;
  font-size: 13px;
}
@media (max-width: 1100px) {
  .stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 680px) {
  .stat-row {
    grid-template-columns: 1fr;
  }
}
.empty-cell {
  color: #aaa;
}
</style>
