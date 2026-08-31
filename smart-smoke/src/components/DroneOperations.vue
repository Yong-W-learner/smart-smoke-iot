<template>
  <div class="uav-workbench">
    <section class="uav-kpis">
      <article><span>无人机在线</span><b>{{ onlineCount }}<small>/{{ drones.length }}</small></b><em>图传与遥测链路正常</em></article>
      <article><span>可接入真实数据</span><b>3<small>类接口</small></b><em>飞控 SDK · 相机载荷 · 扩展载荷</em></article>
      <article><span>今日巡护覆盖</span><b>3.8<small>km²</small></b><em>累计航程 12.6 km</em></article>
      <article><span>采集数据</span><b>286<small>条</small></b><em>影像 74 张 · 热点 1 处</em></article>
    </section>

    <section class="live-layout">
      <el-card shadow="never" class="live-card">
        <template #header>
          <div class="card-head">
            <div><b>无人机手动巡护实时画面</b><span>{{ liveDrone.name }} · {{ liveMission.name }}</span></div>
            <div class="feed-controls">
              <div class="drone-switch" aria-label="切换无人机画面"><button v-for="d in drones" :key="d.id" :class="{active:selectedDroneId===d.id}" @click="selectDrone(d.id)"><i :class="d.status"></i>{{ d.name }}</button></div>
              <div class="feed-tabs"><button v-for="mode in feedModes" :key="mode.value" :class="{active:feedMode===mode.value}" @click="feedMode=mode.value">{{ mode.label }}</button></div>
            </div>
          </div>
        </template>
        <div class="feed-stage" :class="`mode-${feedMode}`">
          <div v-if="!isDroneWorking" class="grounded-placeholder"><span class="grounded-icon"><el-icon><Position /></el-icon><i></i></span><b>{{ liveDrone.name }}当前未执行巡护任务</b><p>{{ droneStatus(liveDrone.status) }} · 等待护林员在移动端接收并开始任务</p></div>
          <template v-else>
            <div v-if="feedMode!=='thermal'" class="video-pane visible-feed">
              <video ref="videoRef" autoplay muted playsinline></video>
              <div v-if="!cameraReady" class="camera-placeholder"><el-icon><VideoCamera /></el-icon><b>{{ cameraStarting ? '正在连接虚拟摄像头…' : '虚拟摄像头未接入' }}</b><span>{{ cameraError || '请在 OBS 等软件中播放飞行视频并开启虚拟摄像头' }}</span><el-button v-if="!cameraStarting" size="small" type="primary" @click="startVirtualCamera">接入虚拟摄像头</el-button></div>
              <div class="feed-label"><i :class="{offline:!cameraReady}"></i> {{ cameraReady ? `${liveDrone.name} · ${videoSourceName}` : '等待视频源' }}</div><time>{{ liveTime }}</time>
            </div>
            <div v-if="feedMode!=='visible'" class="video-pane thermal-feed">
              <div class="thermal-ridge one"></div><div class="thermal-ridge two"></div><div class="hotspot"><i></i><span>{{ liveDrone.telemetry?.surfaceTemperature || '--' }}℃</span></div>
              <div class="thermal-scale"><span>20℃</span><i></i><span>55℃</span></div>
              <div class="feed-label"><i></i> 辐射测温热成像仿真流</div><time>R-JPEG · {{ liveDrone.updatedAt }}</time>
            </div>
            <div class="flight-hud"><span>高度 <b>{{ liveDrone.altitude }} m</b></span><span>速度 <b>{{ liveDrone.speed }} m/s</b></span><span>卫星 <b>{{ liveDrone.satellites }}颗</b></span><span>图传质量 <b>{{ liveDrone.linkQuality }}%</b></span></div>
          </template>
        </div>
        <div class="mission-progress manual-progress">
          <div><span>当前阶段</span><b>{{ liveDrone.phase }} · {{ liveDrone.location }}</b></div>
          <small class="manual-hint">实时画面用于辅助护林员现场操作；结束飞行后在下方任务表确认并归档返航数据</small>
        </div>
      </el-card>

      <div class="telemetry-stack">
        <el-card shadow="never" class="telemetry-card">
          <template #header><div class="card-head"><div><b>机载扩展载荷遥测</b><span>仿真 1 秒/次 · 真实设备可按 2 秒采样</span></div><span class="live-pill"><i></i>动态</span></div></template>
          <div class="sensor-matrix">
            <article v-for="m in telemetry" :key="m.name" :class="m.level"><span>{{ m.name }}</span><b>{{ m.value }}<small>{{ m.unit }}</small></b><em>{{ m.note }}</em></article>
          </div>
          <div class="fusion-note"><el-icon><DataAnalysis /></el-icon><div><b>多源融合判断</b><span>PM2.5短时升高，CO与热成像温度未同步越限，当前判定为“关注”，由护林员继续悬停或调整位置复查。</span></div></div>
        </el-card>
        <el-card shadow="never" class="aircraft-card">
          <template #header><div class="card-head"><div><b>飞控与链路状态</b><span>{{ liveDrone.id }} · 飞控SDK仿真</span></div><el-tag :type="liveDrone.status==='flying'?'success':'info'">{{ liveDrone.phase }}</el-tag></div></template>
          <div class="aircraft-body"><div class="drone-orbit"><el-icon><Position /></el-icon><i></i></div><div class="aircraft-data"><p><span>剩余电量</span><b>{{ liveDrone.battery }}%</b></p><p><span>GNSS定位</span><b>{{ liveDrone.satellites }} 星</b></p><p><span>图传质量</span><b>{{ liveDrone.linkQuality }}%</b></p><p><span>经纬度</span><b>{{ coordinateText }}</b></p></div></div>
          <div class="payload-tags"><span>H20T 640×512测温</span><span>PM2.5扩展载荷</span><span>温湿度</span><span>CO</span><span>可见光云台</span></div>
        </el-card>
      </div>
    </section>

    <section class="route-live">
      <div class="route-map-wrap">
        <ForestMap class="route-map" :zones="zones" :markers="operationMarkers" :interactive="false" />
        <div class="route-source"><i></i>分区：数据库同步 · 紫色：无人机 · 蓝色：地面传感器 · {{ coordinateText }}</div>
      </div>
      <div class="source-panel"><span>REAL DATA MAPPING</span><h3>仿真字段与真实来源一一对应</h3><ul><li><b>飞控 SDK</b><em>经纬度、高度、速度、姿态、电量、卫星数、返航状态</em></li><li><b>H20T 相机载荷</b><em>可见光视频、热成像视频、点/区域测温、R-JPEG照片</em></li><li><b>自研扩展载荷</b><em>PM2.5、CO、温湿度；通过 PSDK/串口采集后上传</em></li></ul><p>颗粒物与气体数据仅在低速或悬停采样时参与判断，避免旋翼下洗气流造成明显误差。</p></div>
    </section>

    <section class="fleet-row">
      <article v-for="d in drones" :key="d.id" class="fleet-item">
        <div class="fleet-icon"><el-icon><Position /></el-icon><i :class="d.status"></i></div>
        <div><span>{{ d.id }} · {{ droneStatus(d.status) }}</span><b>{{ d.name }}</b><small>{{ d.location }} · 电量 {{ d.battery }}%</small></div>
        <div class="fleet-actions"><el-button size="small" @click="$emit('detail', currentTask(d))">查看数据</el-button></div>
      </article>
    </section>

    <el-card shadow="never" class="mission-table">
      <template #header><div class="card-head"><div><b>巡护任务与返航数据</b><span>桌面端跟踪分发、接收、出发、巡护和结果上报全过程</span></div><el-tag type="info">执行操作仅限移动端</el-tag></div></template>
      <el-table :data="patrols" border>
        <el-table-column prop="id" label="任务编号" width="150"/>
        <el-table-column label="任务与路线" min-width="260"><template #default="s"><div class="mission-name"><b>{{ s.row.name }}</b><span>{{ s.row.route }}</span></div></template></el-table-column>
        <el-table-column label="执行人员" width="145"><template #default="s"><span>{{ s.row.drone }}</span><small class="table-sub">护林员 {{ s.row.ranger }}</small></template></el-table-column>
        <el-table-column label="返回数据" min-width="240"><template #default="s"><div class="return-chips"><span>覆盖 {{ s.row.coverage || (s.row.progress===100?'1.6 km²':'计算中') }}</span><span>影像 {{ s.row.images ?? (s.row.progress===100?42:'--') }}</span><span :class="{alert:(s.row.hotspots||0)>0}">热点 {{ s.row.hotspots ?? 0 }}</span><span>采样 {{ s.row.samples ?? (s.row.progress===100?96:'--') }}</span></div></template></el-table-column>
        <el-table-column label="任务方式" width="150"><template #default="s"><small class="table-sub manual-tag">手动巡护</small><el-tag class="mission-status" size="small" :type="taskStatusType(s.row)">{{ taskDisplayStatus(s.row) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="135"><template #default="s"><el-button size="small" @click="$emit('detail',s.row)">{{ s.row.status==='已完成'?'巡护报告':'查看进度' }}</el-button></template></el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Position, DataAnalysis, VideoCamera } from '@element-plus/icons-vue'
import ForestMap from '@/components/ForestMap.vue'

const props=defineProps({drones:{type:Array,default:()=>[]},sensors:{type:Array,default:()=>[]},zones:{type:Array,default:()=>[]},patrols:{type:Array,default:()=>[]},incidents:{type:Array,default:()=>[]}})
defineEmits(['detail'])
const feedMode=ref('split')
const feedModes=[{label:'双光融合',value:'split'},{label:'可见光',value:'visible'},{label:'热成像',value:'thermal'}]
const selectedDroneId=ref('')
const videoRef=ref(null),cameraReady=ref(false),cameraStarting=ref(false),cameraError=ref(''),liveTime=ref(''),videoSourceName=ref('虚拟图传')
let mediaStream=null,timeTimer=null
const liveDrone=computed(()=>props.drones.find(d=>d.id===selectedDroneId.value)||props.drones.find(d=>d.status==='flying')||props.drones[0]||{name:'暂无无人机',id:'--',battery:0})
const isDroneWorking=computed(()=>liveDrone.value.status==='flying')
const liveMission=computed(()=>props.patrols.find(p=>p.drone===liveDrone.value.name&&p.status==='手动飞行')||props.patrols.find(p=>p.drone===liveDrone.value.name)||{name:'暂无执行任务',progress:0})
const onlineCount=computed(()=>props.drones.filter(d=>d.status!=='maintenance').length)
const coordinateText=computed(()=>`${Number(liveDrone.value.latitude||0).toFixed(6)}, ${Number(liveDrone.value.longitude||0).toFixed(6)}`)
const operationMarkers=computed(()=>{const d=liveDrone.value;const ground=props.sensors.map(s=>({id:`operation-${s.id}`,lng:s.longitude,lat:s.latitude,kind:'sensor',status:s.status,name:s.name||s.id}));return(d&&d.longitude!=null&&d.latitude!=null)?[...ground,{id:`live-${d.id}`,lng:d.longitude,lat:d.latitude,kind:'drone',status:d.status==='flying'?'flying':'normal',name:d.name}]:ground})
const telemetry=computed(()=>{const t=liveDrone.value.telemetry||{};return[
  {name:'PM2.5颗粒物',value:t.pm25??'--',unit:'μg/m³',note:'扩展载荷 · 悬停采样',level:Number(t.pm25)>35?'watch':''},
  {name:'环境温度',value:t.temperature??'--',unit:'℃',note:'扩展载荷 · 林冠上方',level:''},
  {name:'相对湿度',value:t.humidity??'--',unit:'%RH',note:'扩展载荷 · 空气偏干',level:Number(t.humidity)<40?'watch':''},
  {name:'一氧化碳',value:t.co??'--',unit:'ppm',note:'扩展载荷 · 未同步升高',level:''},
  {name:'地表最高温',value:t.surfaceTemperature??'--',unit:'℃',note:'H20T区域测温',level:Number(t.surfaceTemperature)>42?'warm':''},
  {name:'估算风速',value:t.windEstimate??'--',unit:'m/s',note:'飞控模型估算，非风速计',level:''}
]})
const droneStatus=s=>({idle:'空闲',charging:'充电中',flying:'执行中',maintenance:'维护中'}[s]||s)
function taskDisplayStatus(task){
  if(task.status!=='已完成'||!task.incidentId)return task.status
  const incident=props.incidents.find(item=>String(item.id)===String(task.incidentId))
  if(!incident||incident.status==='closed')return task.status
  return incident.status==='processing'?'待结案':'警情处置中'
}
function taskStatusType(task){const status=taskDisplayStatus(task);return status==='已完成'?'success':status==='待结案'?'danger':'warning'}
function currentTask(d){return props.patrols.find(p=>p.drone===d.name)||props.patrols[0]}
function selectDrone(id){selectedDroneId.value=id}
function updateLiveTime(){liveTime.value=new Date().toLocaleTimeString('zh-CN',{hour12:false})}
function attachCamera(){if(videoRef.value&&mediaStream){videoRef.value.srcObject=mediaStream;videoRef.value.play().catch(()=>{})}}
function stopVirtualCamera(){mediaStream?.getTracks().forEach(track=>track.stop());mediaStream=null;cameraReady.value=false}
async function startVirtualCamera(){
  if(!isDroneWorking.value)return
  if(!navigator.mediaDevices?.getUserMedia){cameraError.value='当前浏览器不支持摄像头接入';return}
  cameraStarting.value=true;cameraError.value='';stopVirtualCamera()
  try{
    mediaStream=await navigator.mediaDevices.getUserMedia({video:{width:{ideal:1280},height:{ideal:720},frameRate:{ideal:30}},audio:false})
    const devices=await navigator.mediaDevices.enumerateDevices()
    const virtualCamera=devices.find(d=>d.kind==='videoinput'&&/(obs|virtual|虚拟)/i.test(d.label))
    const currentId=mediaStream.getVideoTracks()[0]?.getSettings()?.deviceId
    if(virtualCamera?.deviceId&&virtualCamera.deviceId!==currentId){
      mediaStream.getTracks().forEach(track=>track.stop())
      mediaStream=await navigator.mediaDevices.getUserMedia({video:{deviceId:{exact:virtualCamera.deviceId},width:{ideal:1280},height:{ideal:720},frameRate:{ideal:30}},audio:false})
    }
    videoSourceName.value=virtualCamera?.label||mediaStream.getVideoTracks()[0]?.label||'虚拟图传'
    cameraReady.value=true
    await nextTick();attachCamera()
  }catch(err){cameraError.value=err?.name==='NotAllowedError'?'摄像头权限未授权，请允许后重试':'未检测到可用的视频输入，请先开启虚拟摄像头'}
  finally{cameraStarting.value=false}
}
watch(()=>props.drones.map(d=>d.id).join(','),()=>{if(!props.drones.some(d=>d.id===selectedDroneId.value))selectedDroneId.value=(props.drones.find(d=>d.status==='flying')||props.drones[0])?.id||''},{immediate:true})
watch(feedMode,async()=>{await nextTick();attachCamera()})
watch([selectedDroneId,isDroneWorking],async()=>{if(!isDroneWorking.value){stopVirtualCamera();cameraError.value='';return}await nextTick();startVirtualCamera()})
onMounted(()=>{updateLiveTime();timeTimer=setInterval(updateLiveTime,1000);if(isDroneWorking.value)startVirtualCamera()})
onBeforeUnmount(()=>{clearInterval(timeTimer);stopVirtualCamera()})
</script>

<style scoped>
.uav-workbench{display:flex;flex-direction:column;gap:18px}.uav-kpis{display:grid;grid-template-columns:repeat(4,1fr);gap:14px}.uav-kpis article{padding:16px 18px;border:1px solid #dfe8dc;border-radius:16px;background:#fff}.uav-kpis span,.uav-kpis em{display:block;font-style:normal}.uav-kpis span{font-size:10px;color:#829188}.uav-kpis b{display:flex;align-items:baseline;gap:4px;margin:6px 0;font-size:24px;color:#213b2d}.uav-kpis small{font-size:11px;color:#71847a}.uav-kpis em{font-size:10px;color:#91a097}.live-layout{display:grid;grid-template-columns:minmax(0,1.55fr) minmax(340px,.78fr);gap:18px}.card-head{display:flex;align-items:center;justify-content:space-between;gap:15px}.card-head>div:first-child b,.card-head>div:first-child span{display:block}.card-head span{font-size:10px;color:#84948b;margin-top:4px}.feed-controls{display:flex;align-items:center;gap:8px;flex-wrap:wrap;justify-content:flex-end}.drone-switch,.feed-tabs{display:flex;padding:3px;background:#edf3ed;border-radius:9px}.drone-switch button,.feed-tabs button{border:0;background:transparent;padding:6px 9px;border-radius:7px;font-size:10px;color:#687b70;cursor:pointer;transition:.2s ease}.drone-switch button{display:flex;align-items:center;gap:5px}.drone-switch button i{width:6px;height:6px;border-radius:50%;background:#58a76d}.drone-switch button i.charging{background:#e5a02e}.drone-switch button i.maintenance{background:#c45c51}.drone-switch button.active,.feed-tabs button.active{background:#fff;color:#286a48;box-shadow:0 2px 7px rgba(32,71,47,.12)}.feed-stage{aspect-ratio:16/9;position:relative;overflow:hidden;border-radius:14px;background:#19372a;display:grid;grid-template-columns:1fr 1fr;gap:2px}.feed-stage.mode-visible,.feed-stage.mode-thermal{grid-template-columns:1fr}.video-pane{position:relative;min-width:0;overflow:hidden}.visible-feed{background:#0c1812}.visible-feed video{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;background:#0c1812}.camera-placeholder{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;padding:24px;text-align:center;color:#d9e7dd;background:radial-gradient(circle at 50% 42%,#244b36,#0c1d14 72%)}.camera-placeholder>.el-icon{font-size:36px;color:#7eb18d}.camera-placeholder b{font-size:13px}.camera-placeholder span{max-width:330px;font-size:9px;line-height:1.55;color:#9ab0a2}.thermal-feed{background:linear-gradient(145deg,#172446,#5b256b 48%,#d45457)}.thermal-ridge{position:absolute;left:-10%;right:-10%;bottom:-4%;clip-path:polygon(0 51%,12% 31%,22% 55%,36% 19%,49% 53%,64% 24%,77% 57%,89% 18%,100% 42%,100% 100%,0 100%)}.thermal-ridge.one{height:84%;background:linear-gradient(180deg,#512170,#f08a35)}.thermal-ridge.two{height:56%;background:linear-gradient(180deg,#9a2c64,#ffd65a)}.hotspot{position:absolute;left:61%;top:39%;display:flex;align-items:center;gap:7px;color:#fff;font-size:10px}.hotspot i{width:19px;height:19px;border-radius:50%;background:#fff6a3;box-shadow:0 0 0 8px rgba(255,101,50,.35),0 0 22px #fff06d;animation:hotPulse 1.6s infinite}.thermal-scale{position:absolute;right:9px;top:50%;transform:translateY(-50%);display:flex;flex-direction:column;align-items:center;gap:4px;color:#fff;font-size:8px}.thermal-scale i{width:7px;height:110px;border:1px solid rgba(255,255,255,.7);background:linear-gradient(#fff38d,#f14b45,#692178,#13223f)}.feed-label{position:absolute;left:10px;top:10px;padding:5px 7px;background:rgba(10,29,22,.68);border-radius:6px;color:#fff;font-size:9px;backdrop-filter:blur(6px)}.feed-label i,.live-pill i{display:inline-block;width:6px;height:6px;margin-right:5px;border-radius:50%;background:#6ad27f;box-shadow:0 0 0 4px rgba(106,210,127,.14)}.feed-label i.offline{background:#a3aaa5;box-shadow:none}.video-pane time{position:absolute;right:10px;bottom:38px;color:#fff;font-size:8px;padding:4px 6px;background:rgba(0,0,0,.45)}.flight-hud{position:absolute;z-index:4;left:0;right:0;bottom:0;height:31px;display:flex;align-items:center;justify-content:space-around;background:rgba(7,24,18,.76);backdrop-filter:blur(7px);color:#adc2b4;font-size:9px}.flight-hud b{color:#fff;margin-left:3px}.mission-progress{display:grid;grid-template-columns:minmax(190px,1fr) minmax(180px,.8fr);gap:10px 18px;align-items:center;margin-top:14px}.mission-progress span,.mission-progress b{display:block}.mission-progress span,.mission-progress small{font-size:9px;color:#85948b}.mission-progress b{font-size:12px;margin-top:4px}.mission-progress small{grid-column:2}.manual-progress{display:flex;align-items:center;gap:16px;flex-wrap:wrap}.manual-progress>div:first-child{flex:1;min-width:150px}.manual-progress .manual-hint{margin-left:auto}.telemetry-stack{display:flex;flex-direction:column;gap:18px}.live-pill{padding:5px 8px;border-radius:8px;background:#e9f5ea;color:#367653!important;font-size:9px!important}.sensor-matrix{display:grid;grid-template-columns:repeat(2,1fr);gap:8px}.sensor-matrix article{padding:11px;border-radius:11px;background:#f5f8f4;border:1px solid transparent}.sensor-matrix article.watch{background:#fff7e9;border-color:#f1d5a4}.sensor-matrix article.warm{background:#fff0ec;border-color:#efc5bd}.sensor-matrix span,.sensor-matrix em{display:block;font-style:normal}.sensor-matrix span,.sensor-matrix em{font-size:9px;color:#84938a}.sensor-matrix b{display:block;font-size:18px;margin:5px 0}.sensor-matrix small{font-size:9px;margin-left:3px;color:#76877d}.fusion-note{display:flex;gap:10px;margin-top:10px;padding:11px;border-radius:11px;background:#eaf3ea;color:#2c6c48}.fusion-note>.el-icon{font-size:19px;flex:none;margin-top:2px}.fusion-note b,.fusion-note span{display:block}.fusion-note b{font-size:11px}.fusion-note span{font-size:9px;line-height:1.6;margin-top:4px;color:#668074}.aircraft-body{display:grid;grid-template-columns:95px 1fr;gap:14px}.drone-orbit{height:95px;border-radius:50%;display:grid;place-items:center;position:relative;background:radial-gradient(circle,#e8f4e9 0 45%,#d7e8d8 46% 47%,transparent 48% 62%,#dceadc 63% 64%,transparent 65%)}.drone-orbit>.el-icon{font-size:40px;color:#2e7450}.drone-orbit i{position:absolute;inset:10px;border-top:2px solid #55a36d;border-radius:50%;animation:orbit 3s linear infinite}.aircraft-data{display:grid;grid-template-columns:repeat(2,1fr);gap:8px}.aircraft-data p{padding:8px;border-radius:9px;background:#f6f9f5}.aircraft-data span,.aircraft-data b{display:block}.aircraft-data span{font-size:8px;color:#87958d}.aircraft-data b{font-size:10px;margin-top:4px}.payload-tags{display:flex;flex-wrap:wrap;gap:5px;margin-top:10px}.payload-tags span,.return-chips span{padding:4px 6px;border-radius:6px;background:#edf4ed;color:#52715e;font-size:8px}.fleet-row{display:grid;grid-template-columns:repeat(2,1fr);gap:14px}.fleet-item{display:flex;align-items:center;gap:12px;padding:14px 16px;border-radius:15px;background:#fff;border:1px solid #dfe8dc}.fleet-icon{position:relative;width:42px;height:42px;border-radius:12px;background:#e8f2e8;color:#2e7350;display:grid;place-items:center;font-size:21px}.fleet-icon i{position:absolute;right:-2px;bottom:-2px;width:10px;height:10px;border-radius:50%;background:#53a86c;border:2px solid #fff}.fleet-icon i.charging{background:#e7a12c}.fleet-item>div:nth-child(2){flex:1}.fleet-item span,.fleet-item b,.fleet-item small{display:block}.fleet-item span,.fleet-item small{font-size:9px;color:#84938a}.fleet-item b{font-size:13px;margin:4px 0}.mission-name b,.mission-name span{display:block}.mission-name b{font-size:11px}.mission-name span,.table-sub{display:block;font-size:9px;color:#84938a;margin-top:4px}.return-chips{display:flex;flex-wrap:wrap;gap:4px}.return-chips span.alert{background:#ffebe7;color:#c84e43}.fleet-actions{display:flex;gap:6px}@keyframes hotPulse{50%{box-shadow:0 0 0 13px rgba(255,101,50,0),0 0 30px #fff06d}}@keyframes orbit{to{transform:rotate(360deg)}}@media(max-width:1180px){.live-layout{grid-template-columns:1fr}.telemetry-stack{display:grid;grid-template-columns:repeat(2,1fr)}}@media(max-width:850px){.uav-kpis{grid-template-columns:repeat(2,1fr)}.fleet-row,.telemetry-stack{grid-template-columns:1fr}.mission-progress{grid-template-columns:1fr}.mission-progress small{grid-column:auto}.card-head{align-items:flex-start}.feed-controls{justify-content:flex-start}}
.route-live{display:grid;grid-template-columns:minmax(0,1.65fr) minmax(300px,.7fr);gap:18px}.route-map-wrap,.source-panel{border:1px solid #dfe8dc;border-radius:16px;background:#fff;overflow:hidden}.route-map-wrap{aspect-ratio:1000/620;min-height:300px;position:relative;background:#dce8d7}.route-map{position:absolute;inset:0;width:100%;height:100%}.uav-map-marker{position:absolute;z-index:5;transform:translate(-50%,-50%);transition:left .25s linear,top .25s linear;display:flex;flex-direction:column;align-items:center}.uav-map-marker span{width:32px;height:32px;border-radius:50%;display:grid;place-items:center;background:#7358b7;color:#fff;border:3px solid #fff;box-shadow:0 0 0 7px rgba(115,88,183,.18),0 6px 14px rgba(44,41,76,.3)}.uav-map-marker b{margin-top:6px;padding:3px 7px;border-radius:6px;background:rgba(27,48,35,.88);color:#fff;font-size:9px;white-space:nowrap}.route-source{position:absolute;left:12px;bottom:12px;padding:7px 10px;border-radius:8px;background:rgba(255,255,255,.9);box-shadow:0 5px 16px rgba(35,68,45,.15);font-size:9px;color:#536b5c}.route-source i{display:inline-block;width:6px;height:6px;border-radius:50%;background:#53aa6d;margin-right:5px}.source-panel{padding:20px}.source-panel>span{font-size:9px;letter-spacing:.16em;color:#6d927c}.source-panel h3{font-size:16px;margin:6px 0 12px}.source-panel ul{list-style:none}.source-panel li{display:grid;grid-template-columns:95px 1fr;gap:8px;padding:10px 0;border-top:1px solid #edf1eb}.source-panel li b{font-size:10px;color:#2e704d}.source-panel li em{font-size:9px;line-height:1.55;font-style:normal;color:#718279}.source-panel p{margin-top:8px;padding:10px;border-radius:9px;background:#fff5e6;color:#946323;font-size:9px;line-height:1.6}@media(max-width:1180px){.route-live{grid-template-columns:1fr}.source-panel ul{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.source-panel li{display:block}.source-panel li em{display:block;margin-top:5px}}
.grounded-placeholder{grid-column:1/-1;position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;color:#dce9df;background:radial-gradient(circle at 50% 42%,#294d39 0,#12271c 68%,#0c1e15 100%)}.grounded-icon{position:relative;width:64px;height:64px;margin-bottom:14px;border:1px solid rgba(153,205,169,.22);border-radius:20px;display:grid;place-items:center;background:rgba(107,170,126,.1);color:#8ac59b;font-size:30px}.grounded-icon i{position:absolute;right:9px;bottom:9px;width:8px;height:8px;border-radius:50%;background:#8a958e;border:2px solid #193225}.grounded-placeholder>b{font-size:15px}.grounded-placeholder p{margin:7px 0 16px;font-size:10px;color:#91aa9a}.grounded-placeholder :deep(.el-button){border-color:rgba(137,195,154,.42);background:rgba(98,157,116,.08);color:#a9d3b5}
</style>
