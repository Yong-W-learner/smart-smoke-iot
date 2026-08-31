<template>
  <div class="admin-wrap">
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
        <el-menu-item index="home" @click="$router.push('/resident')">
          <el-icon><House /></el-icon>
          <span>主页</span>
        </el-menu-item>
        <el-menu-item index="history" @click="$router.push('/resident/history')">
          <el-icon><Document /></el-icon>
          <span>告警历史</span>
        </el-menu-item>
        <el-menu-item index="repair">
          <el-icon><Tools /></el-icon>
          <span>设备报修</span>
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
          <h2 class="page-title">设备报修</h2>
          <p class="page-subtitle">在线提交设备报修，并查看报修处理进度</p>
        </div>
        <div class="page-date">{{ today }}</div>
      </div>

      <!--报修表单-->
      <el-card class="form-card">
        <template #header>
          <div class="card-header-row">
            <span>我要报修</span>
          </div>
        </template>
        <el-form :model="form" label-width="90px">
          <el-form-item label="报修标题" required>
            <el-input v-model="form.title" placeholder="请简要描述问题，如：烟感设备无法联网" maxlength="60" />
          </el-form-item>
          <el-form-item label="问题描述">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="4"
              placeholder="请补充故障现象、发生时间等详细信息（选填）"
              maxlength="300"
              show-word-limit
            />
          </el-form-item>
          <el-form-item label="安装位置">
            <span class="loc-text">{{ locationText }}</span>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="handleSubmit">提交报修</el-button>
            <el-button @click="resetForm">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!--我的工单-->
      <el-card>
        <template #header>
          <div class="card-header-row">
            <span>我的工单</span>
            <el-button type="primary" size="small" @click="loadOrders">刷新</el-button>
          </div>
        </template>
        <el-table :data="orderList" border stripe v-loading="loading">
          <el-table-column prop="orderNo" label="工单号" width="180"/>
          <el-table-column label="类型" width="90">
            <template #default="scope">
              <el-tag :type="orderType(scope.row.type).type" effect="plain">
                {{ orderType(scope.row.type).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip/>
          <el-table-column label="状态" width="110">
            <template #default="scope">
              <el-tag :type="orderStatus(scope.row.status).type" :effect="orderStatus(scope.row.status).effect">
                {{ orderStatus(scope.row.status).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="170"/>
          <el-table-column prop="remark" label="关闭备注" min-width="140" show-overflow-tooltip>
            <template #default="scope">
              <span v-if="scope.row.remark">{{ scope.row.remark }}</span>
              <span v-else class="empty-cell">—</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && !orderList.length" description="暂无设备报修工单" :image-size="80" />
      </el-card>
    </el-main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { House, Document, Tools } from '@element-plus/icons-vue'
import { createWorkOrder, getMyOrders } from '@/api/workOrder'
import { orderStatus, orderType } from '@/utils/workOrder'

const router = useRouter()
const activeMenu = ref('repair')

//当前登录居民信息
const currentUser = JSON.parse(localStorage.getItem('currentUser') || '{}')
const userName = computed(() => currentUser.username || '居民')
const userInitial = computed(() => userName.value.charAt(0).toUpperCase())
const locationText = computed(() => {
  const b = currentUser.building, f = currentUser.floor, r = currentUser.room
  if (b == null || f == null || r == null) return '未绑定'
  return `${b}栋 ${f}层 ${r}户`
})

const today = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day} 星期${week}`
})

//报修表单
const form = ref({ title: '', description: '' })
const submitting = ref(false)

function resetForm() {
  form.value = { title: '', description: '' }
}

async function handleSubmit() {
  if (!form.value.title.trim()) {
    ElMessage.warning('请填写报修标题')
    return
  }
  if (!currentUser.id) {
    ElMessage.error('未获取到当前账号，请重新登录')
    return
  }
  submitting.value = true
  try {
    const res = await createWorkOrder({
      reporterId: currentUser.id,
      title: form.value.title.trim(),
      description: form.value.description
    })
    if (res.code === 200) {
      ElMessage.success(res.msg || '报修工单已提交')
      resetForm()
      loadOrders()
    } else {
      ElMessage.error(res.msg || '提交失败')
    }
  } catch (err) {
    ElMessage.error('提交失败，请检查后端服务')
    console.error(err)
  } finally {
    submitting.value = false
  }
}

//我的工单列表
const orderList = ref([])
const loading = ref(false)

async function loadOrders() {
  if (!currentUser.id) return
  loading.value = true
  try {
    const res = await getMyOrders(currentUser.id)
    orderList.value = Array.isArray(res) ? res : []
  } catch (err) {
    ElMessage.error('读取工单列表失败，请检查后端服务')
    console.error(err)
  } finally {
    loading.value = false
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
  loadOrders()
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
.form-card {
  margin-bottom: 16px;
}
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.loc-text {
  font-size: 14px;
  color: #303133;
}
.empty-cell {
  color: #aaa;
}
</style>
