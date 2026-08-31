<template>
  <router-view v-slot="{ Component, route }">
    <transition name="route-fade" mode="out-in">
      <component :is="Component" :key="route.path" />
    </transition>
  </router-view>
</template>

<style>
*{
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

/* ===== 设计令牌（简约风格统一配色） ===== */
:root{
  /* 主色 */
  --primary: #2f7d50;
  --primary-light: #5da374;
  --primary-bg: #e5f1e7;
  --primary-bg-soft: #f2f6f1;
  /* 语义色 */
  --success: #67c23a;
  --warning: #e6a23c;
  --danger: #f56c6c;
  --danger-dark: #e64545;
  --info: #909399;
  /* 文字 */
  --text-main: #303133;
  --text-regular: #606266;
  --text-secondary: #66789c;
  /* 边框 / 背景 */
  --border: #eef1f6;
  --border-soft: #f0f2f5;
  --bg-page: #f5f7fa;
  /* 统计卡渐变 */
  --grad-blue: linear-gradient(135deg, #4e8bff, #165dff);
  --grad-green: linear-gradient(135deg, #67c23a, #409e11);
  --grad-orange: linear-gradient(135deg, #ffa94d, #e6a23c);
  --grad-red: linear-gradient(135deg, #f56c6c, #e64545);
  --grad-gray: linear-gradient(135deg, #909399, #606266);
}

body{
  background-color: var(--bg-page);
}
.route-fade-enter-active,.route-fade-leave-active{transition:opacity .28s ease,transform .32s cubic-bezier(.22,.75,.25,1)}
.route-fade-enter-from{opacity:0;transform:translateY(8px)}
.route-fade-leave-to{opacity:0;transform:translateY(-5px)}
@media(prefers-reduced-motion:reduce){.route-fade-enter-active,.route-fade-leave-active{transition:none!important}*{animation-duration:.01ms!important;animation-iteration-count:1!important}}

/* ===== 卡片层次感 ===== */
.el-card {
  border-radius: 20px;
  border: 1px solid var(--border);
  box-shadow: 0 2px 12px rgba(31, 45, 61, 0.06);
  transition: box-shadow 0.3s ease;
}
.el-card:hover {
  box-shadow: 0 6px 24px rgba(31, 45, 61, 0.12);
}
.el-card__header {
  border-bottom: 1px solid var(--border-soft);
  font-weight: 600;
  color: var(--text-main);
}

/* ===== 按钮层次感 ===== */
.el-button {
  border-radius: 8px;
}
.el-button--primary:not(.is-plain) {
  border: none;
  background: var(--primary);
  box-shadow: 0 2px 8px rgba(22, 93, 255, 0.22);
  transition: background 0.2s ease, box-shadow 0.2s ease;
}
.el-button--primary:not(.is-plain):hover {
  background: #0e4fd6;
  box-shadow: 0 4px 12px rgba(22, 93, 255, 0.32);
}

/* ===== 表格层次感 ===== */
.el-table th.el-table__cell {
  background: var(--bg-page) !important;
  color: var(--text-main);
  font-weight: 600;
}

/* ===== 弹窗圆角（与卡片统一） ===== */
.el-message-box {
  border-radius: 16px;
}
.el-dialog {
  border-radius: 16px;
}
.el-notification {
  border-radius: 12px;
}

/* ===== 动效基础设施（浅色） ===== */
/* 光标光晕：柔和蓝光跟随鼠标 */
.cursor-glow {
  position: fixed;
  z-index: 999;
  pointer-events: none;
  width: 480px;
  height: 480px;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  background: radial-gradient(circle, rgba(22, 93, 255, 0.045), transparent 68%);
  opacity: 0;
  transition: opacity 0.35s ease;
  filter: blur(8px);
}
body:hover .cursor-glow { opacity: 1; }

/* 滚动渐入 */
.reveal {
  opacity: 0;
  transform: translateY(18px);
  transition: opacity 0.6s cubic-bezier(0.2, 0.75, 0.2, 1), transform 0.6s cubic-bezier(0.2, 0.75, 0.2, 1);
}
.reveal.visible { opacity: 1; transform: none; }

/* 3D 倾斜 */
.tilt {
  transform-style: preserve-3d;
  transition: transform 0.16s ease-out;
  will-change: transform;
}

/* 主按钮扫光（按需加在按钮 class 上） */
.btn-shine { position: relative; overflow: hidden; }
.btn-shine::after {
  content: "";
  position: absolute;
  top: -40%;
  left: -30%;
  width: 26%;
  height: 180%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.5), transparent);
  transform: rotate(18deg);
  animation: buttonShine 4.8s ease-in-out infinite;
  pointer-events: none;
}

/* 呼吸脉冲小圆点（在线状态） */
.live-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--success);
  box-shadow: 0 0 0 4px rgba(103, 194, 58, 0.12), 0 0 12px rgba(103, 194, 58, 0.5);
  animation: livePulse 1.8s ease-in-out infinite;
  display: inline-block;
  flex-shrink: 0;
}

@keyframes ambientDrift {
  0% { transform: translate3d(-3%, 0, 0) scale(1); }
  100% { transform: translate3d(4%, 6%, 0) scale(1.1); }
}
@keyframes buttonShine {
  0%, 55% { left: -35%; opacity: 0; }
  65% { opacity: 1; }
  82%, 100% { left: 118%; opacity: 0; }
}
@keyframes livePulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(0.72); opacity: 0.7; }
}
@keyframes softFloat {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}
</style>
