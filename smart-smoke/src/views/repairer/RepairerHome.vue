<template>
  <div class="admin-wrap">
    <!--侧边栏-->
    <el-aside width="220px" class="aside">
      <div class="sidebar-title">智慧烟感系统·维修员</div>
      <el-menu
        class="sidebar-menu"
        active-text-color="#165DFF"
        background-color="#ffffff"
        text-color="#606266"
        :default-active="activeMenu"
      >
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
          <p class="page-subtitle">接取居民设备报修，完成检修后填写结果并关闭工单</p>
        </div>
        <div class="page-date">{{ today }}</div>
      </div>

      <el-card>
        <template #header>
          <div class="card-header-row">
            <el-radio-group v-model="statusFilter" size="small" @change="loadList">
              <el-radio-button value="all">全部</el-radio-button>
              <el-radio-button value="pending">待接单</el-radio-button>
              <el-radio-button value="accepted">已接单</el-radio-button>
              <el-radio-button value="closed">已关闭</el-radio-button>
            </el-radio-group>
            <el-button type="primary" size="small" @click="loadList">刷新</el-button>
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
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip/>
          <el-table-column label="位置" width="140">
            <template #default="scope">{{ locText(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="报修人" width="100">
            <template #default="scope">
              <span v-if="scope.row.reporterName">{{ scope.row.reporterName }}</span>
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
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="scope">
              <el-button
                v-if="scope.row.status === 'pending'"
                type="primary"
                size="small"
                @click="handleAccept(scope.row)"
              >接单</el-button>
              <el-button
                v-else-if="scope.row.status === 'accepted' && scope.row.repairerId === currentUser.id"
                type="success"
                size="small"
                @click="handleClose(scope.row)"
              >确认完成</el-button>
              <span v-else class="empty-cell">—</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && !filteredList.length" description="暂无设备运维工单" :image-size="80" />
      </el-card>
    </el-main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Tickets } from '@element-plus/icons-vue'
import { getWorkOrderList, acceptWorkOrder, closeWorkOrder } from '@/api/workOrder'
import { orderStatus, orderType } from '@/utils/workOrder'

const router = useRouter()
const activeMenu = ref('orders')

//当前登录维修员信息
const currentUser = JSON.parse(localStorage.getItem('currentUser') || '{}')
const userName = computed(() => currentUser.username || '维修员')
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

async function handleAccept(row) {
  try {
    const res = await acceptWorkOrder({ id: row.id, repairerId: currentUser.id })
    if (res.code === 200) {
      ElMessage.success('接单成功')
      loadList()
    } else {
      ElMessage.error(res.msg || '接单失败')
    }
  } catch (err) {
    ElMessage.error('接单失败，请检查后端服务')
    console.error(err)
  }
}

async function handleClose(row) {
  try {
    const { value } = await ElMessageBox.prompt('请输入关闭备注（可留空）', '确认完成', {
      confirmButtonText: '确认关闭',
      cancelButtonText: '取消',
      inputPlaceholder: '如：已更换传感器，恢复正常',
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
.empty-cell {
  color: #aaa;
}
</style>
