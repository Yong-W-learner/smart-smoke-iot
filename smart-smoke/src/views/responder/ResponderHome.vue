<template>
  <div class="workspace">
    <el-aside width="220px" class="aside">
      <div class="brand">智慧烟感系统·应急消防</div>
      <el-menu default-active="incidents" class="menu" active-text-color="#165DFF">
        <el-menu-item index="incidents"><el-icon><Bell /></el-icon><span>应急任务</span></el-menu-item>
      </el-menu>
      <div class="user-panel">
        <el-avatar :size="38" class="avatar">{{ userName.charAt(0).toUpperCase() }}</el-avatar>
        <div><b>{{ userName }}</b><el-button text size="small" @click="logout">退出登录</el-button></div>
      </div>
    </el-aside>

    <el-main class="main">
      <div class="page-header">
        <div><h2>应急处置工作台</h2><p>到达现场后本人确认到场，完成核查后如实上报处置结果</p></div>
        <el-button type="primary" plain @click="loadList">刷新任务</el-button>
      </div>

      <el-row :gutter="16" class="stats">
        <el-col :span="8"><el-card shadow="never"><div class="stat"><div class="stat-icon blue"><el-icon><Location /></el-icon></div><div><small>待确认到场</small><strong>{{ counts.confirmed }}</strong></div></div></el-card></el-col>
        <el-col :span="8"><el-card shadow="never"><div class="stat"><div class="stat-icon orange"><el-icon><WarningFilled /></el-icon></div><div><small>现场处置中</small><strong>{{ counts.arrived }}</strong></div></div></el-card></el-col>
        <el-col :span="8"><el-card shadow="never"><div class="stat"><div class="stat-icon green"><el-icon><CircleCheckFilled /></el-icon></div><div><small>已完成上报</small><strong>{{ counts.handled }}</strong></div></div></el-card></el-col>
      </el-row>

      <el-card class="task-card" shadow="never">
        <template #header><div class="card-header"><div><b>警情任务</b><span>仅显示管理员已确认的警情</span></div><el-radio-group v-model="filter" size="small"><el-radio-button value="active">待处理</el-radio-button><el-radio-button value="handled">已完成</el-radio-button><el-radio-button value="all">全部</el-radio-button></el-radio-group></div></template>
        <el-table :data="filteredList" border stripe v-loading="loading">
          <el-table-column prop="id" label="事件编号" width="100" />
          <el-table-column prop="alarmTime" label="触发时间" width="175" />
          <el-table-column prop="location" label="报警位置" min-width="170" />
          <el-table-column prop="deviceId" label="设备编号" width="105" />
          <el-table-column label="管理员确认" width="120"><template #default="s">{{ s.row.confirmerName || '管理员' }}</template></el-table-column>
          <el-table-column label="状态" width="125"><template #default="s"><el-tag :type="processInfo(s.row).type">{{ processInfo(s.row).text }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="210" fixed="right"><template #default="s">
            <el-button size="small" plain @click="openDetail(s.row)">流程详情</el-button>
            <el-button v-if="processKey(s.row) === 'confirmed'" type="primary" size="small" @click="confirmArrival(s.row)">确认到场</el-button>
            <el-button v-else-if="processKey(s.row) === 'arrived'" type="success" size="small" @click="openReport(s.row)">上报结果</el-button>
            <span v-else class="done">已上报</span>
          </template></el-table-column>
        </el-table>
        <el-empty v-if="!loading && !filteredList.length" description="暂无相关应急任务" :image-size="78" />
      </el-card>
    </el-main>

    <el-drawer v-model="detailVisible" title="警情处置流程" size="480px">
      <div v-if="selected" class="detail">
        <div class="summary"><div><span>事件编号</span><b>#{{ selected.id }}</b></div><div><span>报警位置</span><b>{{ selected.location }}</b></div><div><span>当前状态</span><el-tag :type="processInfo(selected).type">{{ processInfo(selected).text }}</el-tag></div></div>
        <el-timeline>
          <el-timeline-item :timestamp="selected.alarmTime" type="danger"><b>警情触发</b><p>设备 #{{ selected.deviceId }} 生成警情事件</p></el-timeline-item>
          <el-timeline-item :timestamp="selected.confirmTime || selected.responseTime" type="primary"><b>管理员确认</b><p>{{ selected.confirmerName || '管理员' }} 已确认并移交任务</p></el-timeline-item>
          <el-timeline-item :timestamp="selected.arrivalTime || '尚未到场'" :type="selected.arrivalTime ? 'warning' : 'info'" :hollow="!selected.arrivalTime"><b>本人确认到场</b><p>{{ selected.arrivalTime ? `${selected.handlerName} 已到场开展核查` : '请到达现场后点击确认到场' }}</p></el-timeline-item>
          <el-timeline-item :timestamp="selected.handleTime || '尚未上报'" :type="selected.handleTime ? 'success' : 'info'" :hollow="!selected.handleTime"><b>处置结果上报</b><p>{{ selected.handleTime ? resultText(selected.handleResult) : '完成现场核查后填写结果' }}</p></el-timeline-item>
        </el-timeline>
        <el-descriptions v-if="selected.status === 1" :column="1" border><el-descriptions-item label="处置结果">{{ resultText(selected.handleResult) }}</el-descriptions-item><el-descriptions-item label="上报人员">{{ selected.handlerName }}</el-descriptions-item><el-descriptions-item label="现场说明">{{ selected.handleRemark || '未填写' }}</el-descriptions-item></el-descriptions>
      </div>
    </el-drawer>

    <el-dialog v-model="reportVisible" title="上报现场处置结果" width="520px">
      <el-alert title="结果将同步给管理员并完成本次警情流程" type="info" :closable="false" show-icon />
      <el-form label-position="top" class="report-form">
        <el-form-item label="现场确认结果" required><el-select v-model="form.handleResult" style="width:100%" placeholder="请选择"><el-option label="确认真实火情" value="真实火情"/><el-option label="生活烟雾（烹饪/吸烟）" value="生活烟雾"/><el-option label="水蒸气等环境干扰" value="环境干扰"/><el-option label="设备误报或故障" value="设备异常"/><el-option label="现场无异常" value="现场无异常"/></el-select></el-form-item>
        <el-form-item label="现场情况与处置说明" required><el-input v-model="form.handleRemark" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="说明现场情况、采取的措施和最终状态"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="reportVisible=false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitReport">确认上报</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, Location, WarningFilled, CircleCheckFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAlarmIncidents, arriveAlarmIncident, handleAlarmIncident } from '@/api/alarm'

const router = useRouter()
const currentUser = JSON.parse(localStorage.getItem('currentUser') || '{}')
const userName = computed(() => currentUser.username || '应急消防员')
const list = ref([]), loading = ref(false), filter = ref('active')
const detailVisible = ref(false), selected = ref(null), reportVisible = ref(false), reportTarget = ref(null), submitting = ref(false)
const form = ref({ handleResult: '', handleRemark: '' })
const processKey = row => row.status === 1 ? 'handled' : (row.processStatus === 'responding' ? 'confirmed' : row.processStatus)
const processInfo = row => ({ confirmed:{text:'待确认到场',type:'primary'}, arrived:{text:'现场处置中',type:'warning'}, handled:{text:'已完成',type:'success'} }[processKey(row)] || {text:'待管理员确认',type:'info'})
const visibleList = computed(() => list.value.filter(row => ['confirmed','arrived','handled'].includes(processKey(row))))
const filteredList = computed(() => filter.value === 'all' ? visibleList.value : filter.value === 'handled' ? visibleList.value.filter(r => processKey(r)==='handled') : visibleList.value.filter(r => processKey(r)!=='handled'))
const counts = computed(() => ({ confirmed: visibleList.value.filter(r=>processKey(r)==='confirmed').length, arrived: visibleList.value.filter(r=>processKey(r)==='arrived').length, handled: visibleList.value.filter(r=>processKey(r)==='handled').length }))
const resultText = v => ({'真实火情':'确认真实火情，已启动应急处置','生活烟雾':'确认为生活烟雾','环境干扰':'确认为环境干扰','设备异常':'确认为设备异常','现场无异常':'现场检查未发现异常'}[v] || v || '未填写')
async function loadList(){ loading.value=true; try { const res=await getAlarmIncidents(); list.value=Array.isArray(res)?res:[] } catch(e){ ElMessage.error('读取应急任务失败') } finally { loading.value=false } }
function replace(updated){ const i=list.value.findIndex(v=>v.id===updated.id); if(i>=0) list.value[i]=updated; if(selected.value?.id===updated.id) selected.value=updated }
function openDetail(row){ selected.value=row; detailVisible.value=true }
async function confirmArrival(row){ try { await ElMessageBox.confirm(`确认你已到达 ${row.location}？请仅在实际到场后操作。`,'到场确认',{confirmButtonText:'我已到场',cancelButtonText:'取消',type:'warning'}); const res=await arriveAlarmIncident(row.id,currentUser.id); if(res?.code!==200) return ElMessage.error(res?.msg||'确认失败'); replace(res.data); ElMessage.success('已确认到场，请完成现场核查') } catch(e){ if(e!=='cancel'&&e!=='close') ElMessage.error('确认到场失败') } }
function openReport(row){ reportTarget.value=row; form.value={handleResult:'',handleRemark:''}; reportVisible.value=true }
async function submitReport(){ if(!form.value.handleResult) return ElMessage.warning('请选择现场确认结果'); if(!form.value.handleRemark.trim()) return ElMessage.warning('请填写现场处置说明'); submitting.value=true; try { const res=await handleAlarmIncident(reportTarget.value.id,{operatorId:currentUser.id,...form.value}); if(res?.code!==200) return ElMessage.error(res?.msg||'上报失败'); replace(res.data); reportVisible.value=false; ElMessage.success('处置结果已上报管理员') } catch(e){ ElMessage.error('结果上报失败') } finally { submitting.value=false } }
function logout(){ ElMessageBox.confirm('确定退出当前账号吗？','提示',{type:'warning'}).then(()=>{localStorage.removeItem('token');localStorage.removeItem('currentUser');router.push('/login')}).catch(()=>{}) }
onMounted(loadList)
</script>

<style scoped>
.workspace{display:flex;height:100vh;background:#f5f7fb;color:#1d2129}.aside{display:flex;flex-direction:column;background:#fff;border-right:1px solid #e5e9f2}.brand{height:72px;display:flex;align-items:center;padding:0 20px;color:#165dff;font-weight:700;font-size:16px;border-bottom:1px solid #eef1f6}.menu{border-right:0;padding-top:12px}.user-panel{margin-top:auto;padding:16px;display:flex;align-items:center;gap:10px;border-top:1px solid #eef1f6}.user-panel>div{display:flex;flex-direction:column}.avatar{background:#165dff}.main{padding:24px;overflow:auto}.page-header,.card-header{display:flex;justify-content:space-between;align-items:center}.page-header h2{margin:0;font-size:24px}.page-header p{margin:7px 0 0;color:#86909c;font-size:13px}.stats{margin:20px 0}.stat{display:flex;align-items:center;gap:14px}.stat small{display:block;color:#86909c;margin-bottom:7px}.stat strong{font-size:28px}.stat-icon{width:46px;height:46px;border-radius:12px;display:flex;align-items:center;justify-content:center;font-size:23px;flex-shrink:0}.blue{background:#e8f3ff;color:#165dff}.orange{background:#fff3e8;color:#ff7d00}.green{background:#e8ffea;color:#00b42a}.task-card{border:0}.card-header span{margin-left:12px;color:#86909c;font-size:12px}.done{font-size:13px;color:#86909c}.summary{background:#f7f8fa;border-radius:10px;padding:16px;margin-bottom:24px}.summary>div{display:flex;justify-content:space-between;align-items:center;margin:9px 0}.summary span{color:#86909c}.detail :deep(.el-timeline-item__content p){margin:6px 0;color:#667085;line-height:1.6}.report-form{margin-top:18px}
</style>
