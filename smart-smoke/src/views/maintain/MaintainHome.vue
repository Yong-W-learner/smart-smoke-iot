<template>
  <div class="maintain-wrap">
    <!--顶部栏：右上角退出登录-->
    <div class="top-bar">
      <div class="top-title">智慧烟感系统 · 账号维护员</div>
      <el-button type="danger" plain size="small" @click="logout">退出登录</el-button>
    </div>

    <div class="main-body">
      <!--页头-->
      <div class="page-header">
        <div>
          <h2 class="page-title">账号与设备维护</h2>
          <p class="page-subtitle">管理小区管理员、维修员、应急消防员、居民账户及全部传感器</p>
        </div>
        <div class="page-date">{{ today }}</div>
      </div>

      <!--第一部分：小区管理员-->
      <el-card class="section-card">
        <template #header>
          <div class="section-header">
            <span>小区管理员账户</span>
            <div class="header-right">
              <el-input v-model="adminKeyword" placeholder="搜索用户名" size="small" clearable style="width:180px"/>
              <el-button type="primary" size="small" @click="openAddDialog('admin')">＋ 新增管理员</el-button>
            </div>
          </div>
        </template>
        <el-table :data="filteredAdminList" border stripe>
          <el-table-column prop="username" label="用户名"/>
          <el-table-column prop="jobNum" label="工号"/>
          <el-table-column prop="phone" label="联系方式"/>
          <el-table-column label="角色">
            <template #default>小区管理员</template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="scope">
              <el-button type="danger" size="small" circle title="删除" @click="handleDelete(scope.row)">−</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!--第二部分：维修员-->
      <el-card class="section-card">
        <template #header>
          <div class="section-header">
            <span>维修员账户</span>
            <div class="header-right">
              <el-input v-model="repairerKeyword" placeholder="搜索用户名" size="small" clearable style="width:180px"/>
              <el-button type="primary" size="small" @click="openAddDialog('repairer')">＋ 新增维修员</el-button>
            </div>
          </div>
        </template>
        <el-table :data="filteredRepairerList" border stripe>
          <el-table-column prop="username" label="用户名"/>
          <el-table-column prop="jobNum" label="工号"/>
          <el-table-column prop="phone" label="联系方式"/>
          <el-table-column label="角色">
            <template #default>维修员</template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="scope">
              <el-button type="danger" size="small" circle title="删除" @click="handleDelete(scope.row)">−</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!--应急消防员-->
      <el-card class="section-card">
        <template #header>
          <div class="section-header">
            <span>应急消防员账户</span>
            <div class="header-right">
              <el-input v-model="responderKeyword" placeholder="搜索用户名" size="small" clearable style="width:180px"/>
              <el-button type="primary" size="small" @click="openAddDialog('responder')">＋ 新增消防员</el-button>
            </div>
          </div>
        </template>
        <el-table :data="filteredResponderList" border stripe>
          <el-table-column prop="username" label="用户名"/>
          <el-table-column prop="jobNum" label="工号"/>
          <el-table-column prop="phone" label="联系方式"/>
          <el-table-column label="角色"><template #default>应急消防员</template></el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="scope"><el-button type="danger" size="small" circle title="删除" @click="handleDelete(scope.row)">−</el-button></template>
          </el-table-column>
        </el-table>
      </el-card>

      <!--第三部分：居民（无加号）-->
      <el-card class="section-card">
        <template #header>
          <div class="section-header">
            <span>居民账户</span>
            <div class="header-right">
              <el-input v-model="residentKeyword" placeholder="搜索用户名" size="small" clearable style="width:180px"/>
            </div>
          </div>
        </template>
        <el-table :data="filteredResidentList" border stripe>
          <el-table-column prop="username" label="用户名"/>
          <el-table-column prop="phone" label="联系方式"/>
          <el-table-column label="地址">
            <template #default="scope">
              <span v-if="scope.row.building != null">
                {{ scope.row.building }}栋 {{ scope.row.floor }}层 {{ scope.row.room }}户
              </span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="角色">
            <template #default>居民</template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="scope">
              <el-button type="danger" size="small" circle title="删除" @click="handleDelete(scope.row)">−</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!--第四部分：传感器（无加无减）-->
      <el-card class="section-card">
        <template #header>
          <div class="section-header">
            <span>全部传感器</span>
            <div class="header-right">
              <el-input v-model="deviceKeyword" placeholder="搜索设备编号" size="small" clearable style="width:180px"/>
            </div>
          </div>
        </template>
        <el-table :data="filteredDeviceList" border stripe>
          <el-table-column prop="deviceId" label="设备编号" width="100"/>
          <el-table-column label="位置">
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
        </el-table>
      </el-card>
    </div>

    <!--新增账号弹窗-->
    <el-dialog v-model="dialogVisible" title="新增账号" width="520px">
      <el-form :model="addForm" label-width="90px">
        <el-form-item label="用户名" required>
          <el-input v-model="addForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="addForm.role" style="width: 100%">
            <el-option label="小区管理员" value="admin" />
            <el-option label="维修员" value="repairer" />
            <el-option label="应急消防员" value="responder" />
          </el-select>
        </el-form-item>
        <el-form-item label="工号" required>
          <el-input v-model="addForm.jobNum" placeholder="请输入工号（数字）" />
        </el-form-item>
        <el-form-item label="联系方式" required>
          <el-input v-model="addForm.phone" placeholder="11位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input v-model="addForm.password" placeholder="请输入密码" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAdd">确定新增</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserList, addAdmin, deleteUser } from '@/api/account'
import { getDeviceList } from '@/api/device'
import { alarmInfo } from '@/utils/alarm'

const router = useRouter()

// 页头日期（星期）
const today = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day} 星期${week}`
})

const adminList = ref([])
const residentList = ref([])
const repairerList = ref([])
const responderList = ref([])
const deviceList = ref([])

const adminKeyword = ref('')
const residentKeyword = ref('')
const repairerKeyword = ref('')
const responderKeyword = ref('')
const deviceKeyword = ref('')

const dialogVisible = ref(false)
const submitting = ref(false)
const addForm = ref({ username: '', jobNum: '', phone: '', password: '', role: 'admin' })

// 分组后的搜索过滤
const filteredAdminList = computed(() =>
  adminList.value.filter(u => !adminKeyword.value || u.username.includes(adminKeyword.value))
)
const filteredResidentList = computed(() =>
  residentList.value.filter(u => !residentKeyword.value || u.username.includes(residentKeyword.value))
)
const filteredRepairerList = computed(() =>
  repairerList.value.filter(u => !repairerKeyword.value || u.username.includes(repairerKeyword.value))
)
const filteredResponderList = computed(() =>
  responderList.value.filter(u => !responderKeyword.value || u.username.includes(responderKeyword.value))
)
const filteredDeviceList = computed(() =>
  deviceList.value.filter(d => !deviceKeyword.value || String(d.deviceId).includes(deviceKeyword.value))
)

// 加载全部账号
async function loadUsers() {
  try {
    const users = await getUserList()
    const list = Array.isArray(users) ? users : []
    adminList.value = list.filter(u => u.role === 'admin')
    residentList.value = list.filter(u => u.role === 'resident')
    repairerList.value = list.filter(u => u.role === 'repairer')
    responderList.value = list.filter(u => u.role === 'responder')
  } catch (err) {
    ElMessage.error('读取账号列表失败，请检查后端服务')
    console.error(err)
  }
}

// 加载全部传感器
async function loadDevices() {
  try {
    const devices = await getDeviceList()
    deviceList.value = Array.isArray(devices) ? devices : []
  } catch (err) {
    ElMessage.error('读取传感器列表失败')
    console.error(err)
  }
}

// 新增弹窗
const openAddDialog = (role = 'admin') => {
  addForm.value = { username: '', jobNum: '', phone: '', password: '', role }
  dialogVisible.value = true
}

const handleAdd = async () => {
  const f = addForm.value
  if (!f.username || !f.jobNum || !f.phone || !f.password) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (!/^\d{11}$/.test(f.phone)) {
    ElMessage.warning('联系方式需为11位数字手机号')
    return
  }
  submitting.value = true
  try {
    const res = await addAdmin(f)
    if (res.code === 200) {
      ElMessage.success(res.data || '新增管理员成功')
      dialogVisible.value = false
      loadUsers()
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

// 删除账号
const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除账号「${row.username}」吗？`,
    '删除确认',
    { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    try {
      const res = await deleteUser(row.id)
      if (res.code === 200) {
        ElMessage.success(res.data || '删除成功')
        loadUsers()
      } else {
        ElMessage.error(res.msg || '删除失败')
      }
    } catch (err) {
      ElMessage.error('删除失败，请检查后端服务')
      console.error(err)
    }
  }).catch(() => {})
}

// 退出登录
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
  loadUsers()
  loadDevices()
  // 每5秒刷新传感器列表，让在线状态自动更新
  timer = setInterval(loadDevices, 5000)
})
onUnmounted(() => {
  clearInterval(timer)
})
</script>

<style scoped>
.maintain-wrap {
  min-height: 100vh;
  background: #f5f7fa;
}
.top-bar {
  height: 56px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid #e4eaf5;
  position: sticky;
  top: 0;
  z-index: 10;
}
.top-title {
  font-size: 16px;
  font-weight: bold;
  color: #165DFF;
}
.main-body {
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
/*页头*/
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
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
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
</style>
