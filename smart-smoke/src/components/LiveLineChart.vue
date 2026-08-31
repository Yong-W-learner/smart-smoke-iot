<template>
  <div class="live-chart-wrap" :style="{ height: height + 'px' }">
    <div class="chart-area">
      <svg
        class="live-chart"
        viewBox="0 0 800 260"
        preserveAspectRatio="none"
        aria-label="趋势图"
        @mousemove="onMove"
        @mouseleave="onLeave"
        @wheel.prevent="onWheel"
      >
        <defs>
          <linearGradient :id="gradId" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" :stop-color="color" stop-opacity="0.24"/>
            <stop offset="1" :stop-color="color" stop-opacity="0"/>
          </linearGradient>
          <filter :id="glowId" x="-30%" y="-30%" width="160%" height="160%">
            <feGaussianBlur stdDeviation="3.2" result="blur"/>
            <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
          </filter>
          <!-- 裁剪到绘图区并四周留 6px 余量（线宽/圆点不贴边被切），放大时样条过冲仍被裁掉 -->
          <clipPath :id="clipId">
            <rect x="0" y="24" width="800" height="232" />
          </clipPath>
        </defs>

        <g class="grid">
          <line x1="0" y1="40" x2="800" y2="40"/>
          <line x1="0" y1="100" x2="800" y2="100"/>
          <line x1="0" y1="160" x2="800" y2="160"/>
          <line x1="0" y1="220" x2="800" y2="220"/>
        </g>

        <g :clip-path="`url(#${clipId})`">
          <path
            v-if="!empty"
            :key="sig + '-area'"
            :d="areaPath"
            :fill="`url(#${gradId})`"
            class="area"
          />
          <path
            v-if="!empty"
            :key="sig + '-line'"
            :d="linePath"
            :stroke="color"
            :filter="`url(#${glowId})`"
            class="line"
          />
        </g>

        <g v-if="!empty" :key="sig + '-pts'">
          <circle
            v-for="(p, i) in visiblePts"
            :key="p.idx"
            :cx="p.x" :cy="p.y"
            :r="i === visiblePts.length - 1 ? 4.6 : 3.1"
            :fill="p.danger ? dangerColor : color"
            :class="[i === visiblePts.length - 1 ? 'pt latest' : 'pt', p.danger ? 'danger' : '']"
            :style="{ animationDelay: (180 + i * 22) + 'ms' }"
          />
        </g>

        <line
          v-if="thresholdY != null"
          :x1="0" :x2="800" :y1="thresholdY" :y2="thresholdY"
          class="threshold"
        />
        <text
          v-if="thresholdY != null"
          x="798" :y="thresholdY - 7" text-anchor="end"
          class="threshold-label"
        >阈值 {{ threshold }}</text>

        <line
          v-if="hoverPoint"
          :x1="hoverPoint.x" :x2="hoverPoint.x"
          y1="24" y2="236"
          class="hover-line"
        />

        <circle
          v-if="hoverPoint"
          :cx="hoverPoint.x" :cy="hoverPoint.y"
          r="6"
          :fill="hoverPoint.danger ? dangerColor : color"
          class="pt-hover"
        />

        <text v-if="empty" x="400" y="135" text-anchor="middle" class="empty-text">暂无可绘制的数据</text>
      </svg>

      <div v-if="hoverPoint" class="tip" :style="tipStyle">
        <div class="tip-time">{{ hoverPoint.label }}</div>
        <div class="tip-val">{{ fmtVal(hoverPoint.value) }}<span class="tip-unit">{{ unit }}</span></div>
      </div>
    </div>

    <div v-if="showXAxis && !empty" class="x-axis">
      <span v-for="(t, i) in xTicks" :key="i">{{ t.label }}</span>
    </div>

    <div class="legend">
      <span>{{ minText }}</span>
      <span>{{ subtitle }}</span>
      <span>{{ maxText }}</span>
    </div>

    <!-- 缩放/平移滑动条 -->
    <div
      v-if="showSlider"
      ref="sliderRef"
      class="slider"
      @pointermove="onSliderMove"
      @pointerup="onSliderUp"
      @pointercancel="onSliderUp"
    >
      <div ref="trackRef" class="slider-track" @pointerdown="onTrackDown">
        <div class="slider-range" :style="rangeStyle" @pointerdown.stop="onRangeDown"></div>
        <div class="slider-thumb left" :style="thumbLeftStyle" @pointerdown.stop="onThumbDown('left', $event)"></div>
        <div class="slider-thumb right" :style="thumbRightStyle" @pointerdown.stop="onThumbDown('right', $event)"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  data: { type: Array, default: () => [] },
  color: { type: String, default: '#165DFF' },
  height: { type: Number, default: 260 },
  threshold: { type: Number, default: null },
  dangerColor: { type: String, default: '#f56c6c' },
  showXAxis: { type: Boolean, default: false },
  maxXTicks: { type: Number, default: 5 },
  hideSlider: { type: Boolean, default: false },
  unit: { type: String, default: '' },
  subtitle: { type: String, default: '' }
})

// 每个实例唯一 id，避免渐变/滤镜 id 冲突
let _uid = 0
const uid = ++_uid
const gradId = `lg${uid}`
const glowId = `gl${uid}`
const clipId = `clip${uid}`

const W = 800
const H = 260
const top = 24
const bottom = 236

// 数据签名：变化时用 key 重新挂载图形，重放画线动画
const sig = computed(() =>
  (props.data || []).map(d => `${d.label ?? d.name ?? d.time ?? ''}:${d.value}`).join('|')
)

// 全量数据（解析 label/value/danger）
const src = computed(() => (props.data || [])
  .map(d => {
    const value = Number(d.value)
    return {
      label: d.label ?? d.name ?? d.time ?? '',
      value,
      danger: !!(d.danger || d.alarm > 0) || (props.threshold != null && Number.isFinite(value) && value >= props.threshold)
    }
  })
  .filter(d => Number.isFinite(d.value)))

// Y 轴范围（全量，缩放/平移时保持不变，避免纵向跳动）
const yRange = computed(() => {
  const values = src.value.map(p => p.value)
  if (!values.length) return { min: 0, max: 1 }
  let min = Math.min(...values)
  let max = Math.max(...values)
  if (min === max) max = min + 1
  const extra = Math.max(0.45, (max - min) * 0.3)
  min = Math.max(0, min - extra)
  max += extra
  return { min, max }
})

const actualMin = computed(() => src.value.length ? Math.min(...src.value.map(p => p.value)) : 0)
const actualMax = computed(() => src.value.length ? Math.max(...src.value.map(p => p.value)) : 0)

// —— 可视窗口：以全量数据的 [0,1] 比例表示，由滚轮或滑动条控制 ——
const winStart = ref(0)
const winEnd = ref(1)

const windowed = computed(() => {
  const list = src.value
  const N = list.length
  if (!N) return { empty: true, pts: [], linePath: '', areaPath: '', xTicks: [], thresholdY: null }

  const { min, max } = yRange.value
  const mapY = v => top + (max - v) / (max - min) * (bottom - top)

  const s = winStart.value
  const e = winEnd.value
  const span = Math.max(e - s, 1e-6)
  const i0 = Math.max(0, Math.floor(s * (N - 1)))
  const i1 = Math.min(N - 1, Math.ceil(e * (N - 1)))

  const pts = []
  for (let i = i0; i <= i1; i++) {
    const f = N === 1 ? 0 : i / (N - 1)
    pts.push({
      ...list[i],
      idx: i,
      x: (f - s) / span * W,
      y: mapY(list[i].value)
    })
  }
  if (pts.length === 1) pts[0].x = W / 2

  const linePath = smoothPath(pts)
  const areaPath = pts.length ? `${linePath} L ${pts[pts.length - 1].x} ${H} L ${pts[0].x} ${H} Z` : ''
  const thresholdY = props.threshold != null ? mapY(props.threshold) : null

  // X 轴刻度：对可见子集均匀取点
  const m = pts.length
  let tickIdx = []
  if (m <= props.maxXTicks) {
    tickIdx = pts.map((_, i) => i)
  } else {
    for (let k = 0; k < props.maxXTicks; k++) tickIdx.push(Math.round(k / (props.maxXTicks - 1) * (m - 1)))
  }
  const xTicks = tickIdx.map(i => ({ label: pts[i].label }))

  return { empty: false, pts, linePath, areaPath, xTicks, thresholdY }
})

function smoothPath(list) {
  if (list.length === 1) return `M ${list[0].x} ${list[0].y}`
  if (list.length === 2) return `M ${list[0].x} ${list[0].y} L ${list[1].x} ${list[1].y}`
  let d = `M ${list[0].x} ${list[0].y}`
  for (let i = 0; i < list.length - 1; i++) {
    const p0 = list[i - 1] || list[i]
    const p1 = list[i]
    const p2 = list[i + 1]
    const p3 = list[i + 2] || p2
    const cp1x = p1.x + (p2.x - p0.x) / 6
    const cp1y = p1.y + (p2.y - p0.y) / 6
    const cp2x = p2.x - (p3.x - p1.x) / 6
    const cp2y = p2.y - (p3.y - p1.y) / 6
    d += ` C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${p2.x} ${p2.y}`
  }
  return d
}

const empty = computed(() => windowed.value.empty)
const areaPath = computed(() => windowed.value.areaPath)
const linePath = computed(() => windowed.value.linePath)
const pts = computed(() => windowed.value.pts)
// 只绘制落在可视区内的圆点：缩放时窗口两端的“延伸点” x 会略小于 0 / 大于 W，
// 它们只用于把折线接到边缘，不该在图表外画出圆点
const visiblePts = computed(() => pts.value.filter(p => p.x >= 0 && p.x <= W))
const thresholdY = computed(() => windowed.value.thresholdY)
const xTicks = computed(() => windowed.value.xTicks)
const minText = computed(() => src.value.length ? `${actualMin.value.toFixed(1)}${props.unit}` : '--')
const maxText = computed(() => src.value.length ? `${actualMax.value.toFixed(1)}${props.unit}` : '--')
const showSlider = computed(() => src.value.length > 1 && !props.hideSlider)

// —— 悬浮提示 ——
const hoverIndex = ref(null)
const hoverPoint = computed(() => {
  const i = hoverIndex.value
  return (i != null && pts.value[i]) ? pts.value[i] : null
})
const tipStyle = computed(() => {
  const p = hoverPoint.value
  if (!p) return {}
  const left = Math.max(9, Math.min(91, (p.x / W) * 100))
  const topPct = (p.y / H) * 100
  const flip = p.y < 64
  return {
    left: left + '%',
    top: topPct + '%',
    transform: flip ? 'translate(-50%, 22px)' : 'translate(-50%, calc(-100% - 14px))'
  }
})
function fmtVal(v) {
  const n = Math.round(v * 10) / 10
  return Number.isInteger(n) ? n : n.toFixed(1)
}
function onMove(e) {
  const svg = e.currentTarget
  const rect = svg.getBoundingClientRect()
  if (!rect.width || !pts.value.length) return
  const vx = (e.clientX - rect.left) / rect.width * W
  let nearest = 0
  let best = Infinity
  for (let i = 0; i < pts.value.length; i++) {
    const d = Math.abs(pts.value[i].x - vx)
    if (d < best) { best = d; nearest = i }
  }
  hoverIndex.value = nearest
}

// —— 滚轮缩放：围绕光标位置放大/缩小时间窗口 ——
function onWheel(e) {
  const N = src.value.length
  if (N <= 1) return
  const rect = e.currentTarget.getBoundingClientRect()
  if (!rect.width) return
  const frac = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
  const span = winEnd.value - winStart.value
  const minSpan = Math.min(1, 2 / (N - 1)) // 至少保留约 2 个点
  const factor = e.deltaY > 0 ? 1.15 : 1 / 1.15
  const newSpan = Math.max(minSpan, Math.min(1, span * factor))

  const anchor = winStart.value + frac * span
  let ns = anchor - frac * newSpan
  let ne = ns + newSpan
  if (ns < 0) { ns = 0; ne = newSpan }
  if (ne > 1) { ne = 1; ns = 1 - newSpan }
  if (newSpan >= 1) { ns = 0; ne = 1 }
  winStart.value = ns
  winEnd.value = ne
}

// —— 滑动条：拖动两端把手缩放，拖动中间蓝色区平移 ——
const sliderRef = ref(null)
const trackRef = ref(null)
const sliderDrag = ref(null)

const rangeStyle = computed(() => ({
  left: (winStart.value * 100) + '%',
  width: ((winEnd.value - winStart.value) * 100) + '%'
}))
const thumbLeftStyle = computed(() => ({ left: (winStart.value * 100) + '%' }))
const thumbRightStyle = computed(() => ({ left: (winEnd.value * 100) + '%' }))

function clampFrac(v) { return Math.max(0, Math.min(1, v)) }

function beginSliderDrag(mode, e) {
  const track = trackRef.value
  if (!track) return
  const rect = track.getBoundingClientRect()
  if (!rect.width) return
  const frac = clampFrac((e.clientX - rect.left) / rect.width)
  sliderDrag.value = { mode, startFrac: frac, startWinStart: winStart.value, startWinEnd: winEnd.value }
  sliderRef.value && sliderRef.value.setPointerCapture && sliderRef.value.setPointerCapture(e.pointerId)
}
function onRangeDown(e) { e.stopPropagation(); beginSliderDrag('range', e) }
function onThumbDown(mode, e) { e.stopPropagation(); beginSliderDrag(mode, e) }
function onTrackDown(e) {
  // 点击空白轨道：把窗口中心跳到点击处
  const track = trackRef.value
  if (!track) return
  const rect = track.getBoundingClientRect()
  if (!rect.width) return
  const frac = clampFrac((e.clientX - rect.left) / rect.width)
  const span = winEnd.value - winStart.value
  let ns = frac - span / 2
  let ne = ns + span
  if (ns < 0) { ns = 0; ne = span }
  if (ne > 1) { ne = 1; ns = 1 - span }
  winStart.value = ns
  winEnd.value = ne
}
function onSliderMove(e) {
  const d = sliderDrag.value
  const track = trackRef.value
  if (!d || !track) return
  const rect = track.getBoundingClientRect()
  if (!rect.width) return
  const frac = clampFrac((e.clientX - rect.left) / rect.width)
  const delta = frac - d.startFrac
  if (d.mode === 'left') {
    let ns = clampFrac(d.startWinStart + delta)
    if (ns > winEnd.value) ns = winEnd.value
    winStart.value = ns
  } else if (d.mode === 'right') {
    let ne = clampFrac(d.startWinEnd + delta)
    if (ne < winStart.value) ne = winStart.value
    winEnd.value = ne
  } else {
    const span = d.startWinEnd - d.startWinStart
    let ns = clampFrac(d.startWinStart + delta)
    let ne = ns + span
    if (ne > 1) { ne = 1; ns = 1 - span }
    winStart.value = ns
    winEnd.value = ne
  }
}
function onSliderUp(e) {
  sliderDrag.value = null
  sliderRef.value && sliderRef.value.releasePointerCapture && sliderRef.value.releasePointerCapture(e.pointerId)
}

function onLeave() {
  hoverIndex.value = null
}

// 数据变化时复位窗口与悬浮状态
watch(sig, () => {
  winStart.value = 0
  winEnd.value = 1
  hoverIndex.value = null
  sliderDrag.value = null
})
</script>

<style scoped>
.live-chart-wrap{
  position:relative;
  display:flex;
  flex-direction:column;
  border-radius:16px;
  background:linear-gradient(180deg, rgba(22,93,255,.035), rgba(22,93,255,.006));
}
.chart-area{
  position:relative;
  flex:1 1 auto;
  min-height:0;
}
.live-chart{
  width:100%;
  height:100%;
  display:block;
  overflow:visible;
  cursor:crosshair;
}
.grid line{
  stroke:rgba(22,93,255,.10);
  stroke-width:1;
  vector-effect:non-scaling-stroke;
}
.area{
  animation:areaIn .9s ease-out forwards;
}
.line{
  fill:none;
  stroke-width:3.5;
  stroke-linecap:round;
  stroke-linejoin:round;
  vector-effect:non-scaling-stroke;
  stroke-dasharray:1400;
  stroke-dashoffset:1400;
  animation:lineDraw 1.1s cubic-bezier(.2,.75,.2,1) forwards;
}
.pt{
  stroke:#fff;
  stroke-width:1.5;
  opacity:0;
  filter:drop-shadow(0 0 4px rgba(22,93,255,.55));
  transform-box:fill-box;
  transform-origin:center;
  animation:ptIn .4s ease-out forwards;
}
.pt.latest{
  animation:ptIn .4s ease-out forwards, ptPulse 1.8s ease-in-out .5s infinite;
}
.pt.danger{
  filter:drop-shadow(0 0 6px rgba(245,108,108,.85));
}
.hover-line{
  stroke:rgba(22,93,255,.30);
  stroke-width:1;
  stroke-dasharray:4 4;
  vector-effect:non-scaling-stroke;
  pointer-events:none;
}
.pt-hover{
  stroke:#fff;
  stroke-width:2;
  pointer-events:none;
  filter:drop-shadow(0 0 8px rgba(22,93,255,.9));
}
.threshold{
  stroke:#f56c6c;
  stroke-width:1.5;
  stroke-dasharray:6 5;
  vector-effect:non-scaling-stroke;
}
.threshold-label{
  fill:#f56c6c;
  font-size:11px;
  font-weight:600;
}
.empty-text{
  fill:#a8b4c8;
  font-size:13px;
  font-weight:600;
}
.tip{
  position:absolute;
  background:#fff;
  color:#123c8a;
  padding:8px 12px;
  border-radius:10px;
  font-size:12px;
  line-height:1.5;
  pointer-events:none;
  white-space:nowrap;
  box-shadow:0 10px 30px rgba(31,45,61,.18);
  border:1px solid #e4eaf5;
  z-index:6;
}
.tip-time{ color:#8a9bb8; font-size:11px; }
.tip-val{ font-weight:700; font-size:15px; color:#165DFF; }
.tip-unit{ font-size:12px; font-weight:600; color:#7e93b8; margin-left:2px; }
.legend{
  display:flex;
  justify-content:space-between;
  font-size:11px;
  color:#8a9bb8;
  margin-top:6px;
  padding:0 4px;
}
.x-axis{
  display:flex;
  justify-content:space-between;
  font-size:10px;
  color:#8a9bb8;
  margin-top:8px;
  padding:0 4px;
}
.slider{
  position:relative;
  height:26px;
  margin-top:8px;
  padding:0 8px;
  user-select:none;
}
.slider-track{
  position:relative;
  height:26px;
  cursor:pointer;
}
.slider-track::before{
  content:"";
  position:absolute;
  left:0;
  right:0;
  top:50%;
  height:6px;
  transform:translateY(-50%);
  border-radius:3px;
  background:rgba(22,93,255,.12);
}
.slider-range{
  position:absolute;
  left:0;
  top:50%;
  height:6px;
  transform:translateY(-50%);
  border-radius:3px;
  background:linear-gradient(90deg,#2f7bff,#165DFF);
  cursor:grab;
  z-index:1;
}
.slider-range:active{ cursor:grabbing; }
.slider-thumb{
  position:absolute;
  top:50%;
  width:16px;
  height:16px;
  border-radius:50%;
  background:#fff;
  border:2px solid #165DFF;
  box-shadow:0 2px 8px rgba(22,93,255,.35);
  transform:translate(-50%,-50%);
  cursor:ew-resize;
  z-index:2;
  touch-action:none;
}
@keyframes lineDraw{ to{ stroke-dashoffset:0 } }
@keyframes areaIn{ from{opacity:0} to{opacity:1} }
@keyframes ptIn{ from{opacity:0; transform:scale(.4)} to{opacity:1; transform:scale(1)} }
@keyframes ptPulse{ 0%,100%{opacity:1} 50%{opacity:.45} }
</style>
