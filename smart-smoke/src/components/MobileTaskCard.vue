<template>
  <article class="task-card" @click="$emit('click')">
    <div class="task-state"><i :class="task.status"></i><el-icon><Guide /></el-icon></div>
    <div><span>{{ task.id }} · {{ task.status }}</span><b>{{ task.name }}</b><small>{{ task.drone }} · {{ task.route }}</small></div>
    <el-icon class="task-arrow"><ArrowRight /></el-icon>
  </article>
</template>

<script setup>
import { ArrowRight, Guide } from '@element-plus/icons-vue'
defineProps({task:{type:Object,required:true}})
defineEmits(['click'])
</script>

<style scoped>
.task-card{display:flex;align-items:center;gap:12px;padding:14px;margin-bottom:9px;border:1px solid #e0e8de;border-radius:16px;background:#fff;box-shadow:0 6px 18px rgba(36,68,47,.05);cursor:pointer}.task-card>div:nth-child(2){flex:1;min-width:0}.task-card span,.task-card b,.task-card small{display:block}.task-card b{font-size:13px;color:#20372a}.task-card small{margin:5px 0;color:#708178;font-size:10px;line-height:1.45;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.task-card span{font-size:9px;color:#98a39d}.task-state{width:43px;height:43px;position:relative;flex:none;border-radius:13px;background:#e8f2e8;color:#2e7350;display:grid;place-items:center;font-size:20px}.task-state>i{position:absolute;right:-2px;bottom:-2px;width:10px;height:10px;border:2px solid #fff;border-radius:50%;background:#9aa49e}.task-state>i.手动飞行{background:#e49528}.task-state>i.已接收,.task-state>i.前往现场{background:#4a9b66}.task-arrow{color:#93a198}
</style>
