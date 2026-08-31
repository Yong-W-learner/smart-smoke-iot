<template>
<div class="register-container">
  <div class="bg-decoration"></div>
  <el-card shadow="hover" class="register-card">
    <div class="title">
      <h2>居民账号注册</h2>
      <p>接入智慧烟感监测系统 · 守护居家安全</p>
    </div>
    <el-form :model="regForm" label-width="80px">
      <el-form-item label="用户名">
        <el-input v-model="regForm.username" placeholder="请设置登录用户名" size="large"></el-input>
      </el-form-item>

      <!-- 修复：居住地址label正常显示 -->
      <el-form-item label="居住地址">
        <div class="house-row">
          <div class="house-item">
            <el-input v-model.number="regForm.building" placeholder="数字" size="large"></el-input>
            <span class="house-label">栋</span>
          </div>
          <div class="house-item">
            <el-input v-model.number="regForm.floor" placeholder="数字" size="large"></el-input>
            <span class="house-label">层</span>
          </div>
          <div class="house-item">
            <el-input v-model.number="regForm.room" placeholder="数字" size="large"></el-input>
            <span class="house-label">户</span>
          </div>
        </div>
      </el-form-item>

      <el-form-item label="手机号">
        <el-input v-model="regForm.phone" placeholder="请填写11位手机号码" maxlength="11" size="large"></el-input>
      </el-form-item>

      <el-form-item label="密码">
        <el-input v-model="regForm.password" placeholder="设置登录密码" type="password" size="large"></el-input>
      </el-form-item>
      <el-form-item label="确认密码">
        <el-input v-model="regForm.repwd" placeholder="再次输入密码" type="password" size="large"></el-input>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" size="large" class="reg-btn" @click="handleRegister">完成注册</el-button>
      </el-form-item>
    </el-form>
    <div class="divider-line"></div>
    <div class="tip">
      <span>已有账号？</span>
      <el-link type="primary" @click="$router.push('/login')">前往登录</el-link>
    </div>
    <div class="back">
      <el-link @click="$router.push('/login')">← 返回登录</el-link>
    </div>
  </el-card>
</div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
const router = useRouter()

const regForm = ref({
  username:'',
  building:'',
  floor:'',
  room:'',
  phone:'',
  password:'',
  repwd:''
})

const handleRegister = async ()=>{
  const data = regForm.value
  if(!data.username || !data.building || !data.floor || !data.room || !data.phone || !data.password || !data.repwd){
    ElMessage.warning('请完整填写所有信息（栋、层、户填写数字）')
    return
  }
  if(data.password !== data.repwd){
    ElMessage.warning('两次输入密码不一致')
    return
  }
  if(!/^\d{11}$/.test(data.phone)){
    ElMessage.warning('手机号需为11位数字')
    return
  }

  // 调后端注册接口，写入数据库
  try {
    const res = await request.post('/api/register', {
      username: data.username,
      building: data.building,
      floor: data.floor,
      room: data.room,
      phone: data.phone,
      password: data.password
    })
    if(res.code === 200){
      ElMessage.success('注册成功！即将跳转登录页')
      router.push('/login')
    } else {
      ElMessage.error(res.msg || '注册失败')
    }
  } catch (err) {
    ElMessage.error('注册失败，请检查后端服务是否启动')
    console.error(err)
  }
}
</script>

<style scoped>
.register-container{
  min-height:100vh;
  display:flex;
  justify-content:center;
  align-items:center;
  background:linear-gradient(145deg,#edf4ff,#d7e8ff);
  position: relative;
  overflow:hidden;
}
.bg-decoration{
  position:absolute;
  width:400px;
  height:400px;
  background:rgba(22, 93, 255, 0.06);
  border-radius:50%;
  top:-120px;
  right:-100px;
}
.register-card{
  width:520px;
  padding:28px 32px;
  border-radius:12px;
  box-shadow: 0 8px 30px rgba(22,93,255,0.12);
  background:#ffffff;
}
.title{
  text-align:center;
  margin-bottom:28px;
}
.icon-box{
  font-size:42px;
  margin-bottom:8px;
}
.title h2{
  color:#165DFF;
  margin:0 0 6px;
  font-weight:600;
}
.title p{
  color:#66789c;
  margin:0;
}
.reg-btn{
  width:100%;
  height:46px;
  font-size:16px;
}
.divider-line{
  height:1px;
  background:#e4eaf5;
  margin:12px 0;
}
.tip{
  text-align:center;
  margin:8px 0;
  font-size:15px;
  color:#555;
}
.back{
  text-align:center;
  margin-top:10px;
}

.house-row{
  display:flex;
  gap:10px;
}
.house-item{
  flex:1;
  display:flex;
  align-items:center;
}
.house-item .el-input{
  flex:1;
}
.house-label{
  margin-left:6px;
  font-size:16px;
  color:#444;
}

@media(max-width:580px){
  .register-card{
    width:94%;
    padding:20px 16px;
  }
  .house-row{
    gap:6px;
  }
}
</style>
