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
          <h2 class="page-title">传感器概览</h2>
          <p class="page-subtitle">查看并管理小区内所有烟雾传感器设备</p>
        </div>
        <div class="page-date">{{ today }}</div>
      </div>

      <el-card class="table-card">
        <template #header>
          <div class="card-header-row">
            <span>所有烟雾传感器</span>
            <div class="header-actions">
              <el-button type="primary" size="small" @click="openAddDialog">＋ 新增传感器</el-button>
              <el-button size="small" @click="loadList">刷新</el-button>
            </div>
          </div>
        </template>

        <el-table :data="deviceList" border stripe v-loading="loading">
          <el-table-column prop="deviceId" label="设备编号" width="100"/>
          <el-table-column label="数据来源" width="100">
            <template #default="scope">
              <el-tag :type="scope.row.simulated ? 'info' : 'primary'" effect="plain">
                {{ scope.row.simulated ? '仿真设备' : '华为云真机' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="安装位置" width="140">
            <template #default="scope">
              {{ scope.row.building }}栋 {{ scope.row.floor }}层 {{ scope.row.room }}户
            </template>
          </el-table-column>
          <el-table-column label="在线状态" width="110">
            <template #default="scope">
              <el-tag :type="scope.row.online ? 'success' : 'danger'" effect="dark">
                {{ scope.row.online ? '在线' : '离线' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最新烟雾浓度">
            <template #default="scope">
              {{ scope.row.smokeConcentration != null ? scope.row.smokeConcentration : '—' }}
            </template>
          </el-table-column>
          <el-table-column label="警情等级" width="120">
            <template #default="scope">
              <el-tag :type="alarmInfo(scope.row.alarm).type" :effect="alarmInfo(scope.row.alarm).effect">
                {{ alarmInfo(scope.row.alarm).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="归属居民">
            <template #default="scope">
              <span v-if="scope.row.userName">{{ scope.row.userName }}（{{ scope.row.phone }}）</span>
              <span v-else class="empty-cell">未绑定</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="scope">
              <el-button
                type="primary"
                size="small"
                plain
                :disabled="scope.row.deviceId !== 1 || !scope.row.online"
                :title="controlButtonTitle(scope.row)"
                @click="openControlDialog(scope.row)"
              >远程控制</el-button>
              <el-button type="danger" size="small" plain @click="handleDelete(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-main>

    <!--新增传感器弹窗-->
    <el-dialog v-model="dialogVisible" title="新增烟雾传感器" width="520px">
      <el-form :model="addForm" label-width="110px" ref="addFormRef">
        <el-form-item label="设备编号" required>
          <el-input v-model.number="addForm.deviceId" placeholder="请输入设备编号（数字）" />
        </el-form-item>
        <el-form-item label="安装位置" required>
          <div class="location-row">
            <el-input v-model.number="addForm.building" placeholder="栋" style="width:80px" />
            <span class="loc-sep">栋</span>
            <el-input v-model.number="addForm.floor" placeholder="层" style="width:80px" />
            <span class="loc-sep">层</span>
            <el-input v-model.number="addForm.room" placeholder="户" style="width:80px" />
            <span class="loc-sep">户</span>
          </div>
        </el-form-item>
        <el-form-item label="居民手机号">
          <el-input v-model="addForm.phone" placeholder="选填，用于绑定归属居民（11位）" maxlength="11" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAdd">确定新增</el-button>
      </template>
    </el-dialog>

    <!--真实设备远程控制-->
    <el-dialog v-model="controlDialogVisible" title="远程设备控制" width="560px" class="control-dialog">
      <div v-if="controlDevice" class="control-panel">
        <div class="control-device-head">
          <div>
            <div class="control-device-title">设备 #{{ controlDevice.deviceId }}</div>
            <div class="control-device-location">
              {{ controlDevice.building }}栋 {{ controlDevice.floor }}层 {{ controlDevice.room }}户
            </div>
          </div>
          <el-tag :type="controlDevice.online ? 'success' : 'danger'" effect="dark">
            {{ controlDevice.online ? '在线可控' : '设备离线' }}
          </el-tag>
        </div>

        <el-alert
          title="控制命令将通过华为云 IoTDA 实时下发到小熊派"
          type="info"
          :closable="false"
          show-icon
        />

        <div class="control-grid">
          <section class="control-item">
            <div class="control-icon control-icon-beep"><el-icon><Bell /></el-icon></div>
            <div class="control-copy">
              <div class="control-name">蜂鸣器</div>
              <div class="control-desc">用于现场报警和远程设备自检</div>
            </div>
            <div class="control-actions">
              <el-button
                type="danger"
                :loading="commandLoading === 'BEEP_ON'"
                :disabled="!!commandLoading || !controlDevice.online"
                @click="handleCommand('BEEP', 'ON')"
              >开启</el-button>
              <el-button
                :loading="commandLoading === 'BEEP_OFF'"
                :disabled="!!commandLoading || !controlDevice.online"
                @click="handleCommand('BEEP', 'OFF')"
              >关闭</el-button>
            </div>
          </section>

          <section class="control-item">
            <div class="control-icon control-icon-led"><el-icon><Sunny /></el-icon></div>
            <div class="control-copy">
              <div class="control-name">状态指示灯</div>
              <div class="control-desc">用于定位设备和检查硬件响应</div>
            </div>
            <div class="control-actions">
              <el-button
                type="primary"
                :loading="commandLoading === 'LED_ON'"
                :disabled="!!commandLoading || !controlDevice.online"
                @click="handleCommand('LED', 'ON')"
              >开启</el-button>
              <el-button
                :loading="commandLoading === 'LED_OFF'"
                :disabled="!!commandLoading || !controlDevice.online"
                @click="handleCommand('LED', 'OFF')"
              >关闭</el-button>
            </div>
          </section>
        </div>

        <div v-if="lastCommand" class="command-result">
          <span class="result-dot"></span>
          最近指令：{{ commandTargetText(lastCommand.target) }}已{{ commandStateText(lastCommand.state) }}
          <span v-if="lastCommand.commandId" class="command-id">命令ID {{ lastCommand.commandId }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="controlDialogVisible = false">完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { House, Monitor, Bell, TrendCharts, Tickets, Sunny } from '@element-plus/icons-vue'
import { getDeviceList, deleteDevice, addDevice, sendDeviceCommand } from '@/api/device'
import { alarmInfo } from '@/utils/alarm'

const router = useRouter()
const activeMenu = ref('devices')

const currentUser = localStorage.getItem('currentUser')
const userName = computed(() => {
  if (currentUser) {
    try { return JSON.parse(currentUser).username || '管理员' } catch (e) {}
  }
  return '管理员'
})
const userInitial = computed(() => userName.value.charAt(0).toUpperCase())

// 页头日期（星期）
const today = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day} 星期${week}`
})

const deviceList = ref([])
const loading = ref(false)

// 真实设备远程控制
const controlDialogVisible = ref(false)
const controlDevice = ref(null)
const commandLoading = ref('')
const lastCommand = ref(null)

function controlButtonTitle(row) {
  if (row.deviceId !== 1) return '仿真设备不支持远程硬件控制'
  if (!row.online) return '设备离线，暂时无法控制'
  return '控制蜂鸣器和状态指示灯'
}

function openControlDialog(row) {
  if (row.deviceId !== 1 || !row.online) return
  controlDevice.value = row
  lastCommand.value = null
  controlDialogVisible.value = true
}

function commandTargetText(target) {
  return target === 'BEEP' ? '蜂鸣器' : '状态指示灯'
}

function commandStateText(state) {
  return state === 'ON' ? '开启' : '关闭'
}

async function handleCommand(target, state) {
  if (!controlDevice.value || commandLoading.value) return

  if (target === 'BEEP' && state === 'ON') {
    try {
      await ElMessageBox.confirm(
        '开启后现场蜂鸣器将立即鸣响，请确认当前适合执行设备自检。',
        '开启蜂鸣器',
        { confirmButtonText: '确认开启', cancelButtonText: '取消', type: 'warning' }
      )
    } catch (err) {
      return
    }
  }

  commandLoading.value = `${target}_${state}`
  try {
    const res = await sendDeviceCommand(controlDevice.value.deviceId, { target, state })
    if (res.code === 200) {
      lastCommand.value = res.data || { target, state }
      ElMessage.success(`${commandTargetText(target)}${commandStateText(state)}命令执行成功`)
    } else {
      ElMessage.error(res.msg || '命令下发失败')
    }
  } catch (err) {
    ElMessage.error('命令下发失败，请检查设备和后端服务')
    console.error(err)
  } finally {
    commandLoading.value = ''
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await getDeviceList()
    deviceList.value = Array.isArray(res) ? res : []
  } catch (err) {
    ElMessage.error('读取设备列表失败，请检查后端服务')
    console.error(err)
  } finally {
    loading.value = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定删除设备「${row.deviceId}」（${row.building}栋 ${row.floor}层 ${row.room}户）吗？删除后不可恢复。`,
    '删除传感器',
    {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      const res = await deleteDevice(row.deviceId)
      if (res.code === 200) {
        ElMessage.success('删除成功')
        loadList()
      } else {
        ElMessage.error(res.msg || '删除失败')
      }
    } catch (err) {
      ElMessage.error('删除失败，请检查后端服务')
      console.error(err)
    }
  }).catch(() => {})
}

//新增传感器
const dialogVisible = ref(false)
const submitting = ref(false)
const addFormRef = ref(null)
const addForm = ref({
  deviceId: null,
  building: null,
  floor: null,
  room: null,
  phone: ''
})

const openAddDialog = () => {
  addForm.value = { deviceId: null, building: null, floor: null, room: null, phone: '' }
  dialogVisible.value = true
}

const handleAdd = async () => {
  const f = addForm.value
  if (!f.deviceId || !f.building || !f.floor || !f.room) {
    ElMessage.warning('请填写设备编号和安装位置（栋/层/户）')
    return
  }
  if (f.phone && !/^\d{11}$/.test(String(f.phone))) {
    ElMessage.warning('居民手机号需为11位数字')
    return
  }
  submitting.value = true
  try {
    const res = await addDevice(f)
    if (res.code === 200) {
      ElMessage.success(res.data || '新增设备成功')
      dialogVisible.value = false
      loadList()
    } else {
      ElMessage.error(res.msg || '新增失败')
    }
  } catch (err) {
    ElMessage.error('新增失败，请检查后端服务')
    console.error(err)
  } finally {
    submitting.value = false
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

let timer = null
onMounted(() => {
  loadList()
  // 每5秒刷新，让在线状态自动更新
  timer = setInterval(loadList, 5000)
})
onUnmounted(() => {
  clearInterval(timer)
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
.location-row {
  display: flex;
  align-items: center;
}
.loc-sep {
  margin: 0 6px;
  color: #66789c;
}
.empty-cell {
  color: #aaa;
}
.control-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.control-device-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 2px 0;
}
.control-device-title {
  color: var(--text-main);
  font-size: 18px;
  font-weight: 700;
}
.control-device-location {
  margin-top: 5px;
  color: var(--text-secondary);
  font-size: 13px;
}
.control-grid {
  display: grid;
  gap: 12px;
}
.control-item {
  display: grid;
  grid-template-columns: 48px 1fr auto;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: #fbfdff;
}
.control-icon {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  font-size: 22px;
}
.control-icon-beep {
  color: #e64545;
  background: #fff0f0;
}
.control-icon-led {
  color: #d48806;
  background: #fff8e6;
}
.control-name {
  color: var(--text-main);
  font-size: 15px;
  font-weight: 650;
}
.control-desc {
  margin-top: 5px;
  color: var(--text-secondary);
  font-size: 12px;
}
.control-actions {
  display: flex;
  gap: 4px;
}
.command-result {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 11px 13px;
  border-radius: 10px;
  color: #357a22;
  background: #f0f9eb;
  font-size: 13px;
}
.result-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--success);
  box-shadow: 0 0 0 4px rgba(103, 194, 58, 0.12);
}
.command-id {
  margin-left: auto;
  color: #7f8c78;
  font-size: 11px;
}
@media (max-width: 680px) {
  .control-item {
    grid-template-columns: 44px 1fr;
  }
  .control-actions {
    grid-column: 1 / -1;
  }
  .command-id {
    display: none;
  }
}
</style>
