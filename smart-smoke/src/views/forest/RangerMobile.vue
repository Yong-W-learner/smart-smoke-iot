<template>
  <div class="mobile-shell">
    <header class="mobile-head">
      <div class="mobile-brand"><span class="brand-mark"><el-icon><MostlyCloudy /></el-icon></span><div><b>福州国家森林公园</b><small>护林员现场巡护终端</small></div></div>
      <button class="desktop-entry" @click="router.push('/ranger')"><el-icon><Monitor /></el-icon><span>桌面端</span></button>
      <div class="welcome-block"><div><span>{{ greetings }} · RANGER ON DUTY</span><h1>{{ userName }}护林员</h1></div><div class="duty-state"><i></i><div><small>当前状态</small><b>在线值守</b></div></div></div>
    </header>

    <main>
      <template v-if="activeTab==='home'">
        <section class="risk-hero" :class="{danger:urgentIncidents.length}">
          <div class="risk-title"><span><i></i>{{ parkEvacuating ? '全园紧急清场 · 全员处置' : urgentIncidents.length ? '存在待处置警情' : '林区运行平稳' }}</span><b>{{ parkEvacuating ? '三级火情应急响应' : (parkInfo.fireRisk || '—')+'森林火险' }}</b><p>{{ parkEvacuating ? '所有护林员默认进入共同处置状态，无需接取任务；请立即前往责任区域并保持位置上报。' : urgentIncidents.length ? `${urgentIncidents[0].zone}需要尽快核查，请注意现场安全。` : '暂无新增火情，按计划开展日常巡护。' }}</p></div>
          <el-icon><MostlyCloudy /></el-icon>
        </section>

        <section class="quick-stats">
          <article><b>{{ urgentIncidents.length }}</b><span>待处置警情</span></article>
          <article><b>{{ activeTasks.length }}</b><span>进行中任务</span></article>
          <article><b>{{ idleDrones.length }}</b><span>可用无人机</span></article>
        </section>

        <section class="section-block">
          <div class="section-title"><div><span>URGENT RESPONSE</span><h2>警情与任务</h2></div><button @click="activeTab='messages'">全部消息</button></div>
          <article v-for="incident in urgentIncidents.slice(0,2)" :key="incident.id" class="incident-card" @click="openIncidentTask(incident)">
            <div class="incident-level">{{ incident.level }}</div><div><b>{{ incident.zone }}</b><span>{{ incident.reason }}</span><small>{{ incident.time }} · {{ incidentStatus(incident.status) }}</small></div><el-icon><ArrowRight /></el-icon>
          </article>
          <div v-if="!urgentIncidents.length" class="empty-line"><el-icon><CircleCheck /></el-icon><span>当前没有待处置火情</span></div>
        </section>

        <section class="section-block">
          <div class="section-title"><div><span>TODAY PATROL</span><h2>今日巡护</h2></div><button @click="openCreate">＋ 日常巡护</button></div>
          <TaskCard v-for="task in visibleTasks.slice(0,3)" :key="task.id" :task="task" @click="openTask(task)" />
          <div v-if="!visibleTasks.length" class="empty-line"><el-icon><Guide /></el-icon><span>暂无巡护任务，可发起日常巡护</span></div>
        </section>
      </template>

      <template v-else-if="activeTab==='tasks'">
        <div class="page-title"><span>PATROL TASKS</span><h2>巡护任务</h2><button @click="openCreate">＋ 新建</button></div>
        <div class="filter-row"><button v-for="f in taskFilters" :key="f.value" :class="{active:taskFilter===f.value}" @click="taskFilter=f.value">{{ f.label }}</button></div>
        <TaskCard v-for="task in filteredTasks" :key="task.id" :task="task" @click="openTask(task)" />
      </template>

      <template v-else>
        <div class="page-title"><span>FIELD MESSAGES</span><h2>消息中心</h2><button @click="enableNotifications">开启通知</button></div>
        <article v-for="m in messages" :key="m.id" class="message-card" :class="m.kind" @click="m.task ? openTask(m.task) : null">
          <div class="message-icon"><el-icon><component :is="m.icon" /></el-icon></div><div><b>{{ m.title }}</b><p>{{ m.content }}</p><span>{{ m.time }}</span></div>
        </article>
        <div v-if="!messages.length" class="empty-line"><el-icon><Bell /></el-icon><span>暂无新消息</span></div>
      </template>
    </main>

    <nav class="bottom-nav">
      <button :class="{active:activeTab==='home'}" @click="activeTab='home'"><span class="nav-icon"><el-icon><House /></el-icon></span><span class="nav-label">工作台</span></button>
      <button :class="{active:activeTab==='tasks'}" @click="activeTab='tasks'"><span class="nav-icon"><el-icon><Guide /></el-icon><b v-if="taskAttentionCount" class="nav-badge">{{ taskAttentionCount }}</b></span><span class="nav-label">巡护任务</span></button>
      <button :class="{active:activeTab==='messages'}" @click="activeTab='messages'"><span class="nav-icon"><el-icon><Bell /></el-icon><b v-if="urgentIncidents.length" class="nav-badge">{{ urgentIncidents.length }}</b></span><span class="nav-label">消息</span></button>
    </nav>

    <el-drawer v-model="taskDrawer" direction="btt" size="88%" :with-header="false" class="mobile-drawer">
      <div v-if="selectedTask" class="task-detail">
        <div class="drawer-grip"></div><div class="detail-head"><div><span>{{ selectedTask.id }}</span><h2>{{ selectedTask.name }}</h2></div><el-tag :type="taskTag(selectedTask.status)">{{ selectedTask.status }}</el-tag></div>
        <div class="route-box"><el-icon><Location /></el-icon><div><span>巡护路线</span><b>{{ selectedTask.route }}</b></div></div>
        <div class="detail-grid"><div><span>执行无人机</span><b>{{ selectedTask.drone }}</b></div><div><span>计划时间</span><b>{{ selectedTask.planTime || '立即执行' }}</b></div><div><span>护林员</span><b>{{ selectedTask.ranger || '待自主接取' }}</b></div><div><span>关联警情</span><b>{{ selectedTask.incidentId || '日常巡护' }}</b></div></div>
        <div class="location-panel">
          <ForestMap :zones="mobileMapZones" :markers="positionMarkers" :center="mapCenter" :zoom="16" :interactive="false" height="210px" />
          <div class="location-status"><span><i :class="{online:position}"></i>{{ position ? '真实位置已上报' : '尚未获取手机定位' }}</span><b>{{ positionText }}</b></div>
        </div>
        <div v-if="selectedTask.status==='手动飞行'" class="capture-panel"><div><b>现场记录</b><span>照片将直接归档到该任务，桌面端可立即查看</span></div><el-upload :show-file-list="false" accept="image/*" capture="environment" :auto-upload="false" :on-change="uploadFieldPhoto"><el-button :loading="photoUploading"><el-icon><Camera /></el-icon> 拍照上报</el-button></el-upload></div>
        <div class="task-actions">
          <el-button v-if="!selectedTask.incidentId&&['待执行','已接收','前往现场'].includes(selectedTask.status)" type="primary" size="large" @click="startTask">开始日常巡护</el-button>
          <template v-else-if="selectedTask.status==='待执行'">
            <el-button type="primary" size="large" @click="acceptTask">自主接取任务</el-button>
          </template>
          <el-button v-else-if="selectedTask.status==='已接收'" type="primary" size="large" @click="departTask">出发并上报位置</el-button>
          <el-button v-else-if="selectedTask.status==='前往现场'" type="primary" size="large" @click="startTask">到达现场，开始巡护</el-button>
          <el-button v-else-if="selectedTask.status==='手动飞行'" type="success" size="large" @click="openFinish">完成巡护并上报</el-button>
          <el-button v-else size="large" @click="taskDrawer=false">任务已归档</el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="createDialog" title="创建日常巡护任务" width="calc(100% - 28px)" class="mobile-dialog">
      <el-form label-position="top"><el-form-item label="任务名称"><el-input v-model="newTask.name" placeholder="例如：银杏古树区日常巡查" /></el-form-item><el-form-item label="巡护分区"><el-select v-model="newTask.zone" style="width:100%"><el-option v-for="z in zones" :key="z.id" :label="z.name" :value="z.name" /></el-select></el-form-item><el-form-item label="使用无人机"><el-select v-model="newTask.drone" style="width:100%"><el-option v-for="d in drones" :key="d.id" :label="`${d.name} · 电量${d.battery}%`" :value="d.name" :disabled="d.status!=='idle'" /></el-select></el-form-item></el-form>
      <template #footer><el-button @click="createDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="createDailyTask">创建任务</el-button></template>
    </el-dialog>

    <el-dialog v-model="finishDialog" title="巡护结果上报" width="calc(100% - 28px)" class="mobile-dialog">
      <el-form label-position="top"><el-form-item label="现场结论"><el-select v-model="finishForm.result" style="width:100%"><el-option label="未发现异常" value="日常巡护完成，未发现异常"/><el-option label="疑似火情，需持续处置" value="确认存在疑似火情，已完成现场核查"/><el-option label="违规用火，已消除隐患" value="发现违规用火，已劝阻并消除隐患"/><el-option label="环境干扰，无真实火情" value="环境干扰，无真实火情"/></el-select></el-form-item><el-form-item label="现场说明"><el-input v-model="finishForm.remark" type="textarea" :rows="4" placeholder="补充现场情况、处置措施或异常位置" /></el-form-item></el-form>
      <template #footer><el-button @click="finishDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="finishTask">提交并归档</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, markRaw, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Bell, Camera, CircleCheck, Guide, House, Location, Monitor, MostlyCloudy, Position, Warning } from '@element-plus/icons-vue'
import ForestMap from '@/components/ForestMap.vue'
import TaskCard from '@/components/MobileTaskCard.vue'
import { addForestMissionPhoto, completeForestMission, createForestMission, getForestBootstrap, reportRangerPosition, saveDroneTelemetry, startForestMission, updateForestIncident, updateForestMissionStatus } from '@/api/forest'
import { PARK } from '@/config/park'

const router=useRouter(),activeTab=ref('home'),taskFilter=ref('active'),taskDrawer=ref(false),createDialog=ref(false),finishDialog=ref(false),saving=ref(false),photoUploading=ref(false)
const currentUser=JSON.parse(localStorage.getItem('currentUser')||'{}'),userName=currentUser.username||'护林员'
const parkInfo=reactive({}),zones=ref([]),drones=ref([]),patrols=ref([]),incidents=ref([]),selectedTask=ref(null),position=ref(null),mapCenter=ref([...PARK.center])
const newTask=reactive({name:'',zone:'',drone:''}),finishForm=reactive({result:'日常巡护完成，未发现异常',remark:''})
const taskFilters=[{label:'进行中',value:'active'},{label:'待接取',value:'pending'},{label:'已完成',value:'done'}]
const greetings=computed(()=>new Date().getHours()<12?'早上好':new Date().getHours()<18?'下午好':'晚上好')
const claimableTasks=computed(()=>patrols.value.filter(t=>t.status==='待执行'&&!t.ranger))
const myPatrols=computed(()=>patrols.value.filter(t=>t.ranger===userName||t.ranger==='全体护林员'))
const availableTasks=computed(()=>[...claimableTasks.value,...myPatrols.value])
const myIncidentIds=computed(()=>new Set(availableTasks.value.map(t=>t.incidentId).filter(Boolean)))
const urgentIncidents=computed(()=>incidents.value.filter(i=>i.status!=='closed'&&(i.level==='三级'||myIncidentIds.value.has(i.id))))
const parkEvacuating=computed(()=>incidents.value.some(i=>i.status!=='closed'&&i.level==='三级'))
const mobileMapZones=computed(()=>zones.value.map(z=>({...z,alertState:parkEvacuating.value?'evacuate':incidents.value.some(i=>i.status!=='closed'&&i.zone===z.name)?'incident':''})))
const activeTasks=computed(()=>myPatrols.value.filter(t=>!['待执行','已完成'].includes(t.status)))
const taskAttentionCount=computed(()=>claimableTasks.value.length+activeTasks.value.length)
const visibleTasks=computed(()=>availableTasks.value.filter(t=>t.status!=='已完成'))
const idleDrones=computed(()=>drones.value.filter(d=>d.status==='idle'))
const filteredTasks=computed(()=>taskFilter.value==='pending'?availableTasks.value.filter(t=>t.status==='待执行'):taskFilter.value==='done'?myPatrols.value.filter(t=>t.status==='已完成'):myPatrols.value.filter(t=>!['待执行','已完成'].includes(t.status)))
const positionText=computed(()=>position.value?`${position.value.longitude.toFixed(6)}, ${position.value.latitude.toFixed(6)} · 精度约${Math.round(position.value.accuracy||0)}m`:'开启任务后使用手机GPS真实定位')
const positionMarkers=computed(()=>position.value?[{id:'mobile-ranger',lng:position.value.longitude,lat:position.value.latitude,kind:'ranger',status:'normal',name:userName}]:[])
const messages=computed(()=>[
  ...urgentIncidents.value.map(i=>({id:i.id,kind:'danger',icon:markRaw(Warning),title:i.level==='三级'?`全园清场 · ${i.zone}三级火情`:`${i.level}警情 · ${i.zone}`,content:i.level==='三级'?`全园已进入疏散状态。${i.reason}`:i.reason,time:i.time})),
  ...visibleTasks.value.map(t=>({id:t.id,kind:'task',icon:markRaw(Guide),title:`巡护任务 · ${t.status}`,content:`${t.name} · ${t.drone}`,time:t.planTime||'立即执行',task:t}))
])
const incidentStatus=s=>({pending:'待摄像复查',verifying:'待无人机核查',processing:'待结果上报'}[s]||s)
const taskTag=s=>s==='已完成'?'success':s==='手动飞行'?'warning':s==='前往现场'?'primary':'info'
let refreshTimer=null,locationWatch=null,droneReportTimer=null,droneFlight=null,knownMessageIds=new Set(),initialized=false,lastPositionReport=0,uploadedPhotos=0

async function loadData(showError=false){try{const res=await getForestBootstrap();const d=res.data||{};Object.assign(parkInfo,d.parkInfo||{});zones.value=d.zones||[];drones.value=d.drones||[];patrols.value=d.patrols||[];incidents.value=d.incidents||[];if(d.map?.centerLng&&d.map?.centerLat)mapCenter.value=[d.map.centerLng,d.map.centerLat];if(selectedTask.value){const latest=patrols.value.find(t=>t.id===selectedTask.value.id);if(latest)selectedTask.value=latest}notifyChanges()}catch{if(showError)ElMessage.error('无法连接森林业务数据库')}}
function notifyChanges(){const ids=messages.value.map(m=>m.id);if(initialized&&'Notification' in window&&Notification.permission==='granted')messages.value.filter(m=>!knownMessageIds.has(m.id)).forEach(m=>new Notification(m.title,{body:m.content}));if(parkEvacuating.value&&locationWatch==null)startLocation();knownMessageIds=new Set(ids);initialized=true}
async function enableNotifications(){if(!('Notification' in window))return ElMessage.warning('当前浏览器不支持系统通知');const p=await Notification.requestPermission();ElMessage[p==='granted'?'success':'warning'](p==='granted'?'已开启警情与任务通知':'未获得通知权限')}
function openTask(task){selectedTask.value=task;uploadedPhotos=Number(task.images)||0;taskDrawer.value=true}
function openIncidentTask(incident){const task=availableTasks.value.find(t=>t.incidentId===incident.id&&t.status!=='已完成');if(task)return openTask(task);activeTab.value='messages';ElMessage.info(incident.level==='三级'?'三级警情已进入全员共同处置，请按清场广播立即行动':'该警情尚未发布无人机核查任务')}
function openCreate(){Object.assign(newTask,{name:'',zone:zones.value[0]?.name||'',drone:idleDrones.value[0]?.name||''});createDialog.value=true}
async function createDailyTask(){if(!newTask.name||!newTask.zone||!newTask.drone)return ElMessage.warning('请填写完整任务信息');saving.value=true;try{await createForestMission({name:newTask.name,route:`护林员现场操控 → ${newTask.zone} → 返回起降点`,ranger:userName,drone:newTask.drone,planTime:'立即执行',mode:'manual'});createDialog.value=false;await loadData();activeTab.value='tasks';ElMessage.success('日常巡护任务已创建')}catch{ElMessage.error('任务创建失败')}finally{saving.value=false}}
async function acceptTask(){await changeTaskStatus('已接收','任务接取成功，其他护林员端将不再显示该任务')}
async function departTask(){await changeTaskStatus('前往现场','已出发，开始上报真实位置');startLocation()}
async function changeTaskStatus(status,message){saving.value=true;try{const res=await updateForestMissionStatus(selectedTask.value.id,{status,ranger:userName});if(res.code!==200)throw new Error(res.msg||'任务状态更新失败');Object.assign(selectedTask.value,res.data||{status,ranger:userName});await loadData();ElMessage.success(message)}catch(err){await loadData();taskDrawer.value=false;ElMessage.error(err.message||'任务状态更新失败')}finally{saving.value=false}}
async function startTask(){saving.value=true;try{const res=await startForestMission(selectedTask.value.id,{ranger:userName});if(res.code!==200)throw new Error(res.msg||'无法开始巡护');startLocation();await loadData();startDroneReporting();ElMessage.success('巡护已开始，位置与无人机遥测将自动上报')}catch(err){ElMessage.error(err.message||'无法开始巡护')}finally{saving.value=false}}
function startLocation(){if(locationWatch!=null||!navigator.geolocation)return !navigator.geolocation&&ElMessage.warning('当前浏览器不支持定位');locationWatch=navigator.geolocation.watchPosition(async p=>{position.value={latitude:p.coords.latitude,longitude:p.coords.longitude,altitude:p.coords.altitude,accuracy:p.coords.accuracy};const now=Date.now();if(now-lastPositionReport>5000){lastPositionReport=now;try{await reportRangerPosition({ranger:userName,...position.value})}catch{}}},()=>ElMessage.warning('未获得手机定位权限，任务仍可继续但不会上报位置'),{enableHighAccuracy:true,maximumAge:3000,timeout:10000})}
function startDroneReporting(){if(droneReportTimer!=null)return;const task=selectedTask.value,drone=drones.value.find(d=>d.name===task?.drone);if(!task||!drone||task.status!=='手动飞行')return;const[west,south,east,north]=PARK.bounds;droneFlight={baseLat:Number(drone.latitude)||mapCenter.value[1],baseLng:Number(drone.longitude)||mapCenter.value[0],tick:0,battery:Number(drone.battery)||100};const tick=async()=>{if(!selectedTask.value||selectedTask.value.status!=='手动飞行')return;const f=droneFlight;f.tick++;const i=f.tick,progress=(i%12)/11,arc=Math.sin(progress*Math.PI);const lat=f.baseLat+Math.sin(i*0.9)*0.0008+progress*0.0002;const lng=f.baseLng+arc*0.001-progress*0.0002;f.battery=Math.max(3,f.battery-0.6);const payload={droneId:drone.id,battery:Math.round(f.battery*10)/10,droneStatus:'flying',location:(selectedTask.value.route||'').split('→')[1]?.trim()||'巡护航线',x:Math.round((lng-west)/(east-west)*1000)/10,y:Math.round((north-lat)/(north-south)*1000)/10,latitude:Math.round(lat*1e6)/1e6,longitude:Math.round(lng*1e6)/1e6,altitude:Math.round((8+45*arc)*10)/10,speed:Math.round((4.2+Math.random()*2)*10)/10,satellites:16+Math.floor(Math.random()*4),linkQuality:96-Math.floor(Math.random()*7),etaSec:0,phase:'手动飞行 · 巡航监测',pm25:Math.round((18+11*arc+Math.random()*2)*10)/10,temperature:Math.round((28.6+2.4*arc+Math.random()*0.5)*10)/10,humidity:Math.round((57-9*arc+Math.random()*1.4)*10)/10,co:Math.round((2.8+2*arc+Math.random()*0.5)*10)/10,surfaceTemperature:Math.round((31.5+9.5*arc+Math.random())*10)/10,windEstimate:Math.round((2.1+Math.random()*0.7)*10)/10};try{await saveDroneTelemetry(selectedTask.value.id,payload)}catch{}};droneReportTimer=setInterval(tick,4000);tick()}
function stopDroneReporting(){if(droneReportTimer!=null){clearInterval(droneReportTimer);droneReportTimer=null}droneFlight=null}
async function uploadFieldPhoto(file){if(!selectedTask.value)return;const raw=file?.raw;if(!raw)return;if(raw.size>1024*1024)return ElMessage.warning('演示环境单张照片请控制在1MB以内');photoUploading.value=true;try{const imageData=await new Promise(resolve=>{const reader=new FileReader();reader.onload=()=>resolve(String(reader.result||''));reader.readAsDataURL(raw)});const res=await addForestMissionPhoto(selectedTask.value.id,{ranger:userName,category:'巡护全景',zoneName:zones.value.find(z=>selectedTask.value.route?.includes(z.name))?.name||'',tags:'移动端,现场上报',imageData,latitude:position.value?.latitude,longitude:position.value?.longitude,note:'护林员移动端现场采集'});if(res.code!==200)throw new Error(res.msg||'照片上报失败');uploadedPhotos++;ElMessage.success('现场照片已归档，桌面端可查看')}catch(err){ElMessage.error(err.message||'照片上报失败')}finally{photoUploading.value=false}}
function openFinish(){Object.assign(finishForm,{result:selectedTask.value.incidentId?'确认存在疑似火情，已完成现场核查':'日常巡护完成，未发现异常',remark:''});finishDialog.value=true}
async function finishTask(){if(!finishForm.result)return ElMessage.warning('请选择现场结论');saving.value=true;try{const drone=drones.value.find(d=>d.name===selectedTask.value.drone);const res=await completeForestMission(selectedTask.value.id,{ranger:userName,droneId:drone?.id,coverage:'0.4 km²',images:uploadedPhotos,rangerLatitude:position.value?.latitude,rangerLongitude:position.value?.longitude,droneLatitude:drone?.latitude,droneLongitude:drone?.longitude});if(res.code!==200)throw new Error(res.msg||'巡护结果上报失败');stopDroneReporting();if(selectedTask.value.incidentId){const incident=incidents.value.find(i=>i.id===selectedTask.value.incidentId),result=`移动端护林员上报：${finishForm.result}${finishForm.remark?'；'+finishForm.remark:''}。等待桌面端确认结案。`;await updateForestIncident(selectedTask.value.incidentId,{status:'processing',result,ranger:incident?.level==='三级'?'全体护林员':userName})}finishDialog.value=false;taskDrawer.value=false;await loadData();ElMessage.success(selectedTask.value.incidentId?'现场结果已返回桌面端，等待确认结案':'日常巡护已归档，桌面端报告已生成')}catch(err){ElMessage.error(err.message||'巡护结果上报失败')}finally{saving.value=false}}

onMounted(()=>{loadData(true);refreshTimer=setInterval(loadData,8000)})
onBeforeUnmount(()=>{clearInterval(refreshTimer);if(locationWatch!=null)navigator.geolocation.clearWatch(locationWatch);stopDroneReporting()})
</script>

<style scoped>
.mobile-shell{--green:#1f6948;min-height:100vh;padding-bottom:96px;background:linear-gradient(180deg,#e9f1e8 0,#f5f7f3 300px);color:#20372a;font-family:"Microsoft YaHei",system-ui,sans-serif}.mobile-head{height:178px;padding:20px 20px 28px;display:grid;grid-template-columns:minmax(0,1fr) auto;grid-template-rows:43px 1fr;gap:18px 14px;color:#fff;background:linear-gradient(145deg,#123c2a,#246545);border-radius:0 0 30px 30px;box-shadow:0 14px 30px rgba(25,69,47,.16)}.mobile-head span,.page-title>span,.section-title span{font-size:9px;letter-spacing:.16em;opacity:.72}.mobile-brand{display:flex;align-items:center;gap:10px;min-width:0}.brand-mark{width:39px;height:39px;flex:none;border:1px solid rgba(255,255,255,.18);border-radius:12px;display:grid;place-items:center;background:rgba(255,255,255,.09);color:#a9d8b5;font-size:21px}.mobile-brand b,.mobile-brand small{display:block}.mobile-brand b{font-size:13px}.mobile-brand small{margin-top:4px;color:#9fc1ab;font-size:8px;letter-spacing:.08em}.desktop-entry{height:39px;padding:0 11px;border:1px solid rgba(255,255,255,.15);border-radius:12px;display:flex;align-items:center;gap:5px;background:rgba(255,255,255,.08);color:#d5e6da;font-size:9px}.desktop-entry .el-icon{font-size:15px}.desktop-entry span{letter-spacing:0;opacity:1}.welcome-block{grid-column:1/-1;display:flex;align-items:flex-end;justify-content:space-between;gap:18px}.welcome-block>div:first-child>span{font-size:8px;letter-spacing:.14em;color:#94bca3}.welcome-block h1{font-size:23px;margin-top:6px}.duty-state{min-width:104px;padding:9px 11px;border:1px solid rgba(255,255,255,.13);border-radius:12px;display:flex;align-items:center;gap:8px;background:rgba(7,40,27,.18)}.duty-state>i{width:8px;height:8px;flex:none;border-radius:50%;background:#6dd286;box-shadow:0 0 0 5px rgba(109,210,134,.12)}.duty-state small,.duty-state b{display:block}.duty-state small{font-size:8px;color:#96b9a2}.duty-state b{margin-top:3px;font-size:10px}.mobile-shell main{max-width:620px;margin:-18px auto 0;padding:0 14px;position:relative}.risk-hero{min-height:126px;padding:19px;border-radius:20px;display:flex;align-items:center;justify-content:space-between;background:linear-gradient(145deg,#2c7652,#1d5c3f);color:#fff;box-shadow:0 13px 30px rgba(32,91,62,.2)}.risk-hero.danger{background:linear-gradient(145deg,#a84b3e,#743b32)}.risk-title>span{font-size:10px;color:#c5dfcc}.risk-title>span i{display:inline-block;width:7px;height:7px;margin-right:7px;border-radius:50%;background:#79d28d;box-shadow:0 0 0 5px rgba(121,210,141,.13)}.risk-hero.danger .risk-title>span i{background:#ffbf68}.risk-title b{display:block;font-size:20px;margin:9px 0 5px}.risk-title p{max-width:290px;font-size:10px;line-height:1.55;color:#d2e2d6}.risk-hero>.el-icon{font-size:50px;opacity:.25}.quick-stats{display:grid;grid-template-columns:repeat(3,1fr);gap:9px;margin:12px 0 20px}.quick-stats article{padding:13px 8px;border:1px solid #e0e9df;border-radius:14px;background:rgba(255,255,255,.9);text-align:center}.quick-stats b,.quick-stats span{display:block}.quick-stats b{font-size:20px}.quick-stats span{font-size:9px;color:#7d8e84;margin-top:4px}.section-block{margin-bottom:23px}.section-title,.page-title{display:flex;align-items:end;justify-content:space-between;margin:0 3px 11px}.section-title h2,.page-title h2{font-size:18px;margin-top:3px}.section-title button,.page-title button{border:0;background:transparent;color:#2d7650;font-size:11px;font-weight:650}.incident-card,.task-card,.message-card{display:flex;align-items:center;gap:12px;padding:14px;margin-bottom:9px;border:1px solid #e0e8de;border-radius:16px;background:#fff;box-shadow:0 6px 18px rgba(36,68,47,.05)}.incident-level{width:42px;height:42px;flex:none;border-radius:13px;display:grid;place-items:center;background:#ffebe7;color:#c84d42;font-size:11px;font-weight:700}.incident-card>div:nth-child(2),.task-card>div:nth-child(2),.message-card>div:nth-child(2){flex:1;min-width:0}.incident-card b,.incident-card span,.incident-card small,.task-card span,.task-card b,.task-card small{display:block}.incident-card b,.task-card b{font-size:13px}.incident-card span,.task-card small{margin:5px 0;color:#708178;font-size:10px;line-height:1.45;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.incident-card small,.task-card span{font-size:9px;color:#98a39d}.incident-card>.el-icon,.task-arrow{color:#93a198}.empty-line{min-height:78px;border:1px dashed #cfdccf;border-radius:16px;display:flex;align-items:center;justify-content:center;gap:8px;color:#87978e;font-size:11px}.empty-line .el-icon{font-size:18px;color:#5b9b70}.page-title{padding-top:34px;margin-bottom:15px}.filter-row{display:flex;gap:7px;margin-bottom:13px}.filter-row button{border:1px solid #dbe5d9;background:#fff;color:#75857c;padding:7px 12px;border-radius:999px;font-size:10px}.filter-row button.active{border-color:#317752;background:#e6f2e7;color:#246744}.task-state{width:43px;height:43px;position:relative;flex:none;border-radius:13px;background:#e8f2e8;color:#2e7350;display:grid;place-items:center;font-size:20px}.task-state>i{position:absolute;right:-2px;bottom:-2px;width:10px;height:10px;border:2px solid #fff;border-radius:50%;background:#9aa49e}.task-state>i.手动飞行{background:#e49528}.task-state>i.已接收,.task-state>i.前往现场{background:#4a9b66}.message-card{align-items:flex-start}.message-card.danger{border-left:4px solid #d8584d}.message-card.task{border-left:4px solid #4b9064}.message-icon{width:38px;height:38px;border-radius:11px;display:grid;place-items:center;background:#edf4ed;color:#347551}.message-card.danger .message-icon{background:#ffebe7;color:#c84d42}.message-card b{font-size:12px}.message-card p{font-size:10px;color:#718078;line-height:1.55;margin:5px 0}.message-card span{font-size:9px;color:#9aa59f}.bottom-nav{position:fixed;z-index:20;left:50%;bottom:0;transform:translateX(-50%);width:min(100%,620px);height:78px;padding:7px 22px calc(7px + env(safe-area-inset-bottom));display:grid;grid-template-columns:repeat(3,1fr);background:rgba(255,255,255,.96);border-top:1px solid rgba(38,93,62,.1);box-shadow:0 -10px 28px rgba(31,69,46,.08);backdrop-filter:blur(18px)}.bottom-nav button{min-width:0;border:0;background:transparent;color:#89968f;display:grid;grid-template-rows:34px 15px;place-items:center;align-content:center;row-gap:1px}.nav-icon{position:relative;width:34px;height:32px;border-radius:11px;display:grid;place-items:center;transition:.22s ease}.nav-icon .el-icon{font-size:20px}.nav-label{font-size:9px;line-height:15px;letter-spacing:0!important;opacity:1!important;white-space:nowrap}.bottom-nav button.active{color:#256b48;font-weight:700}.bottom-nav button.active .nav-icon{background:#e3f0e5;color:#256b48}.nav-badge{position:absolute;right:-6px;top:-4px;min-width:16px;height:16px;padding:0 4px;border:2px solid #fff;border-radius:999px;display:grid;place-items:center;background:#d7564b;color:#fff;font-size:8px;line-height:1;font-weight:800;box-sizing:border-box}.drawer-grip{width:42px;height:4px;border-radius:4px;background:#d7dfd7;margin:0 auto 18px}.detail-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.detail-head span{font-size:9px;color:#789084}.detail-head h2{font-size:20px;margin-top:5px}.route-box{display:flex;gap:10px;margin:16px 0;padding:13px;border-radius:13px;background:#eef5ed;color:#2e7450}.route-box>.el-icon{font-size:20px;flex:none}.route-box span,.route-box b{display:block}.route-box span{font-size:9px;color:#769082}.route-box b{font-size:11px;line-height:1.5;margin-top:4px}.detail-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:8px}.detail-grid>div{padding:11px;border-radius:11px;background:#f5f8f4}.detail-grid span,.detail-grid b{display:block}.detail-grid span{font-size:9px;color:#84938a}.detail-grid b{font-size:11px;margin-top:5px}.location-panel{margin-top:13px;border:1px solid #dfe8dc;border-radius:14px;overflow:hidden}.location-status{padding:10px 12px;background:#fff}.location-status span,.location-status b{display:block}.location-status span{font-size:9px;color:#76877e}.location-status span i{display:inline-block;width:7px;height:7px;border-radius:50%;background:#9ba49e;margin-right:6px}.location-status span i.online{background:#50a76a}.location-status b{font-size:10px;margin-top:4px}.capture-panel{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-top:12px;padding:12px;border:1px solid #dfe8dc;border-radius:12px}.capture-panel b,.capture-panel span{display:block}.capture-panel b{font-size:11px}.capture-panel span{font-size:9px;color:#84938b;margin-top:4px}.task-actions{margin-top:15px}.task-actions :deep(.el-button){width:100%;height:45px}.mobile-shell :deep(.el-drawer){max-width:620px;left:50%;transform:translateX(-50%);border-radius:24px 24px 0 0}.mobile-shell :deep(.el-drawer__body){padding:14px 16px 26px}.mobile-shell :deep(.el-dialog){max-width:560px;border-radius:18px}.mobile-shell :deep(.el-button--primary:not(.is-plain)){background:#2f7d50}.mobile-shell :deep(.el-button--success){background:#2f7d50;border-color:#2f7d50}@media(min-width:621px){.mobile-shell{background:linear-gradient(145deg,#dfeadd,#f5f7f3)}.mobile-head{max-width:620px;margin:auto}.mobile-shell main{background:rgba(255,255,255,.3);min-height:calc(100vh - 122px)}}
/* 顶部采用两张独立等宽卡片：身份卡 + 火险卡，取消负边距叠放。 */
.mobile-shell{padding-top:12px;background:linear-gradient(180deg,#e7f0e6 0,#f5f7f3 330px)}
.mobile-head{box-sizing:border-box;width:calc(100% - 28px);max-width:592px;height:154px;margin:0 auto;padding:17px 18px;grid-template-rows:42px minmax(0,1fr);gap:13px 14px;background:linear-gradient(145deg,#123c2a,#205c40);border:1px solid rgba(255,255,255,.09);border-radius:24px;box-shadow:0 12px 28px rgba(25,69,47,.16)}
.welcome-block{gap:14px}.welcome-block>div:first-child{min-width:0}.welcome-block h1{font-size:22px;white-space:nowrap}
.desktop-entry{transition:transform .2s ease,background .2s ease}.desktop-entry:active{transform:scale(.97);background:rgba(255,255,255,.13)}
.duty-state{background:rgba(7,40,27,.2)}
.mobile-shell main{margin:12px auto 0}
.risk-hero{box-sizing:border-box;min-height:118px;padding:18px;border:1px solid rgba(255,255,255,.12);border-radius:24px;background:linear-gradient(145deg,#2d7a55,#1d5c3f);box-shadow:0 11px 26px rgba(32,91,62,.16)}
@media(max-width:360px){.mobile-head{height:150px;padding:15px}.mobile-brand b{font-size:12px}.welcome-block h1{font-size:20px}.duty-state{min-width:94px;padding:8px 9px}.risk-hero{min-height:112px;padding:16px}.risk-title b{font-size:18px}}
@media(min-width:621px){.mobile-head{max-width:592px;margin:0 auto}.mobile-shell main{background:transparent;min-height:calc(100vh - 178px)}}
</style>
