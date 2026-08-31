<template>
  <div ref="el" class="forest-amap" :style="{ height }">
    <div v-if="loadError" class="amap-fallback">
      <el-icon><MapLocation /></el-icon>
      <p>{{ loadError }}</p>
      <small>请先在 src/config/amap.js 填入高德 key 与安全密钥</small>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { MapLocation } from '@element-plus/icons-vue'
import { loadAMap } from '@/utils/amap'
import { PARK, isPointInPark } from '@/config/park'

const props = defineProps({
  zones: { type: Array, default: () => [] },
  markers: { type: Array, default: () => [] },
  center: { type: Array, default: () => [...PARK.center] },
  zoom: { type: Number, default: PARK.zoom },
  interactive: { type: Boolean, default: true },
  publicMode: { type: Boolean, default: false },
  height: { type: String, default: '100%' }
})
const emit = defineEmits(['select-zone', 'draw-complete', 'view-change', 'ready'])

const el = ref(null)
const loadError = ref('')
let AMapLib = null
let map = null
let mouseTool = null
let parkBoundaryOverlay = null
let infoWindow = null
let polygonOverlays = []
let zoneLabelOverlays = []
const markerOverlays = new Map() // id -> AMap.Marker
let viewTimer = null

const RISK_FILL = { 高: { fill: '#d98763', stroke: '#c4714f' }, 中: { fill: '#d0c07c', stroke: '#b8a95f' }, 低: { fill: '#a9cc95', stroke: '#8bb578' } }
const PUBLIC_FILL = { 高: { fill: '#c98a66', stroke: '#c17a52' }, 中: { fill: '#b9b074', stroke: '#a3975a' }, 低: { fill: '#9dc18a', stroke: '#84a874' } }

function zoneColor(zone) {
  if (zone.alertState === 'evacuate') return { fill: '#df453b', stroke: '#ff6b60', fillOpacity: 0.5, strokeWeight: 4 }
  if (zone.alertState === 'incident') return { fill: '#e9942f', stroke: '#ffc15c', fillOpacity: 0.4, strokeWeight: 3 }
  const table = props.publicMode ? PUBLIC_FILL : RISK_FILL
  const color = table[zone.risk] || (props.publicMode ? { fill: '#9dc18a', stroke: '#84a874' } : { fill: '#a9cc95', stroke: '#8bb578' })
  return { ...color, fillOpacity: 0.35, strokeWeight: 2 }
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]))
}

function markerColor(m) {
  if (m.kind === 'ranger') return '#2f704d'
  if (m.kind === 'drone') return '#7259b4'
  const s = m.status
  if (s === 'alarm') return '#d9574c'
  if (s === 'warning') return '#d69a24'
  if (s === 'offline') return '#7e8a83'
  return '#2788a8'
}

function dotHtml(m) {
  const c = markerColor(m)
  const name = m.name || m.label || m.id || '地图点位'
  const lng = Number(m.lng)
  const lat = Number(m.lat)
  const coordinate = Number.isFinite(lng) && Number.isFinite(lat) ? `${lng.toFixed(6)}, ${lat.toFixed(6)}` : '坐标待上报'
  return `<div class="map-dot" style="--dot:${c}"><i class="dot-core"></i><span class="map-dot-label"><b>${escapeHtml(name)}</b><em>${coordinate}</em></span></div>`
}

function markerInfoHtml(m) {
  const name = m.name || m.label || m.id || '地图点位'
  const type = { sensor: '监测传感器', ranger: '护林员', drone: '巡护无人机' }[m.kind] || '地图点位'
  const lng = Number(m.lng)
  const lat = Number(m.lat)
  return `<div class="map-point-info"><b>${escapeHtml(name)}</b><span>${type}</span><dl><dt>经度</dt><dd>${Number.isFinite(lng) ? lng.toFixed(6) : '—'}</dd><dt>纬度</dt><dd>${Number.isFinite(lat) ? lat.toFixed(6) : '—'}</dd></dl></div>`
}

function openMarkerInfo(marker) {
  if (!map || !AMapLib) return
  const data = marker.getExtData() || {}
  if (!infoWindow) infoWindow = new AMapLib.InfoWindow({ offset: new AMapLib.Pixel(0, -8), closeWhenClickMap: true })
  infoWindow.setContent(markerInfoHtml(data))
  infoWindow.open(map, marker.getPosition())
}

function parseZoneGeometry(geojson) {
  if (!geojson) return null
  let g = geojson
  if (typeof g === 'string') { try { g = JSON.parse(g) } catch { return null } }
  if (g.type === 'Feature') g = g.geometry
  if (!g || !g.type || !g.coordinates) return null
  if (g.type === 'Polygon') return g.coordinates[0]
  if (g.type === 'MultiPolygon') return g.coordinates[0][0]
  return null
}

function renderZones() {
  if (!map) return
  polygonOverlays.forEach(p => map.remove(p))
  zoneLabelOverlays.forEach(label => map.remove(label))
  polygonOverlays = []
  zoneLabelOverlays = []
  props.zones.forEach(z => {
    const ring = parseZoneGeometry(z.geojson)
    if (!ring || !ring.length) return
    const path = ring.map(c => [Number(c[0]), Number(c[1])])
    const { fill, stroke, fillOpacity, strokeWeight } = zoneColor(z)
    const poly = new AMapLib.Polygon({
      path,
      fillColor: fill,
      fillOpacity,
      strokeColor: stroke,
      strokeWeight,
      strokeOpacity: 0.9,
      strokeStyle: z.alertState ? 'dashed' : 'solid',
      zIndex: z.alertState ? 18 : 10
    })
    if (props.interactive) poly.on('click', () => emit('select-zone', z))
    poly.setMap(map)
    polygonOverlays.push(poly)
    const center = z.longitude != null && z.latitude != null
      ? [Number(z.longitude), Number(z.latitude)]
      : path.reduce((sum, point) => [sum[0] + point[0] / path.length, sum[1] + point[1] / path.length], [0, 0])
    const label = new AMapLib.Text({
      text: z.name,
      position: center,
      anchor: 'center',
      zIndex: z.alertState ? 24 : 16,
      style: {
        maxWidth: '74px',
        padding: '2px 5px',
        border: `1px solid ${z.alertState ? stroke : 'rgba(64,105,75,.2)'}`,
        borderRadius: '5px',
        background: z.alertState ? 'rgba(91,43,29,.78)' : 'rgba(255,255,255,.68)',
        color: z.alertState ? '#fff' : '#244b32',
        fontSize: '8px',
        fontWeight: '600',
        lineHeight: '1.25',
        textAlign: 'center',
        whiteSpace: 'normal',
        wordBreak: 'break-all',
        boxShadow: '0 2px 6px rgba(27,58,38,.08)',
        pointerEvents: 'none'
      }
    })
    label.setMap(map)
    zoneLabelOverlays.push(label)
  })
}

function renderParkBoundary() {
  if (!map || !PARK.boundary?.length) return
  if (parkBoundaryOverlay) map.remove(parkBoundaryOverlay)
  parkBoundaryOverlay = new AMapLib.Polygon({
    path: PARK.boundary,
    fillColor: '#2f8a58',
    fillOpacity: 0.1,
    strokeColor: '#65e296',
    strokeWeight: 3,
    strokeOpacity: 0.95,
    strokeStyle: 'solid',
    lineJoin: 'round',
    zIndex: 6
  })
  parkBoundaryOverlay.setMap(map)
}

function syncMarkers() {
  if (!map) return
  const visibleMarkers = props.markers.filter(m => isPointInPark(m.lng, m.lat))
  const ids = new Set(visibleMarkers.map(m => m.id))
  for (const [id, marker] of markerOverlays) {
    if (!ids.has(id)) { map.remove(marker); markerOverlays.delete(id) }
  }
  visibleMarkers.forEach(m => {
    if (m.lng == null || m.lat == null) return
    const pos = [Number(m.lng), Number(m.lat)]
    const existing = markerOverlays.get(m.id)
    if (existing) {
      existing.setPosition(pos)
      existing.setContent(dotHtml(m))
      existing.setExtData(m)
    } else {
      const marker = new AMapLib.Marker({ position: pos, content: dotHtml(m), anchor: 'center', zIndex: 30, extData: m })
      marker.on('click', () => openMarkerInfo(marker))
      marker.setMap(map)
      markerOverlays.set(m.id, marker)
    }
  })
}

function onViewChanged() {
  if (!map) return
  const c = map.getCenter()
  emit('view-change', { lng: c.getLng(), lat: c.getLat(), zoom: map.getZoom() })
}

function scheduleViewChange() {
  if (viewTimer) clearTimeout(viewTimer)
  viewTimer = setTimeout(onViewChanged, 600)
}

function fitAll() {
  if (!map) return
  const overlays = [parkBoundaryOverlay, ...polygonOverlays, ...zoneLabelOverlays, ...markerOverlays.values()].filter(Boolean)
  if (overlays.length) map.setFitView(overlays, false, [60, 60, 60, 60])
  else map.setZoomAndCenter(props.zoom, props.center)
}

function startDraw() {
  if (!map || !AMapLib) return
  AMapLib.plugin('AMap.MouseTool', () => {
    if (!mouseTool) {
      mouseTool = new AMapLib.MouseTool(map)
      mouseTool.on('draw', e => {
        const obj = e.obj
        const raw = obj && obj.getPath ? obj.getPath() : []
        const path = raw.map(p => [p.getLng ? p.getLng() : p.lng, p.getLat ? p.getLat() : p.lat])
        stopDraw()
        emit('draw-complete', path)
      })
    }
    mouseTool.polygon({ strokeColor: '#2f7d50', strokeWeight: 2, fillColor: '#2f7d50', fillOpacity: 0.15 })
  })
}

function stopDraw() {
  if (mouseTool) { try { mouseTool.close(true) } catch { /* noop */ } }
}

async function init() {
  if (!el.value) return
  try {
    AMapLib = await loadAMap()
    map = new AMapLib.Map(el.value, {
      center: props.center,
      zoom: props.zoom,
      viewMode: '2D',
      layers: [new AMapLib.TileLayer.Satellite(), new AMapLib.TileLayer.RoadNet()]
    })
    // 限定地图范围：拖拽不可超出公园边界，缩放限制在园区可辨识区间
    map.setLimitBounds(new AMapLib.Bounds([PARK.bounds[0], PARK.bounds[1]], [PARK.bounds[2], PARK.bounds[3]]))
    map.setZooms([PARK.minZoom, PARK.maxZoom])
    renderParkBoundary()
    renderZones()
    syncMarkers()
    map.on('moveend', scheduleViewChange)
    map.on('zoomend', scheduleViewChange)
    emit('ready', map)
    // 标签页（v-else-if）内地图首次挂载时容器可能尚未完成排版，导致初始化为零尺寸、分区等覆盖物不显示；
    // 等两帧布局稳定后再 resize 并重绘分区与标记。
    requestAnimationFrame(() => requestAnimationFrame(() => {
      if (!map) return
      map.resize()
      renderZones()
      syncMarkers()
    }))
  } catch (e) {
    loadError.value = (e && e.message) || '高德地图加载失败'
  }
}

watch(() => props.zones, renderZones, { deep: true })
watch(() => props.markers, syncMarkers, { deep: true })

onMounted(init)
onBeforeUnmount(() => {
  if (viewTimer) clearTimeout(viewTimer)
  stopDraw()
  if (map) { map.destroy(); map = null }
})

defineExpose({ fitAll, startDraw, stopDraw })
</script>

<style>
.forest-amap{width:100%;position:relative;overflow:hidden;background:#0f2419}
.amap-fallback{height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;color:#8fb49b;font-size:13px;background:linear-gradient(160deg,#14301f,#0f2518)}
.amap-fallback .el-icon{font-size:40px}
.amap-fallback small{font-size:11px;color:#6f9480}
/* 呼吸灯小点：AMap 通过 innerHTML 注入，需全局样式 */
.map-dot{position:relative;width:8px;height:8px;cursor:pointer}
.map-dot::before{content:"";position:absolute;inset:-7px;border-radius:50%}
.map-dot .dot-core{position:absolute;inset:0;border:1.5px solid rgba(255,255,255,.96);border-radius:50%;background:var(--dot);box-shadow:0 1px 5px rgba(4,22,13,.48)}
.map-dot .dot-core::after{content:"";position:absolute;inset:-3px;border:1px solid var(--dot);border-radius:50%;opacity:.45;animation:dotRipple 2.4s ease-out infinite}
.map-dot .map-dot-label{position:absolute;z-index:2;top:13px;left:50%;min-width:128px;transform:translate(-50%,4px);padding:6px 8px;border:1px solid rgba(91,129,104,.28);border-radius:7px;background:rgba(255,255,255,.96);box-shadow:0 5px 15px rgba(21,48,31,.18);color:#173d2a;white-space:nowrap;pointer-events:none;opacity:0;visibility:hidden;transition:opacity .16s ease,transform .16s ease}
.map-dot:hover .map-dot-label{opacity:1;visibility:visible;transform:translate(-50%,0)}
.map-dot .map-dot-label b,.map-dot .map-dot-label em{display:block;font-style:normal}
.map-dot .map-dot-label b{font-size:10px;line-height:1.3}
.map-dot .map-dot-label em{margin-top:3px;color:#687d70;font:9px/1.2 ui-monospace,SFMono-Regular,Consolas,monospace}
.map-point-info{min-width:168px;padding:3px 2px;color:#203a2a}.map-point-info>b,.map-point-info>span{display:block}.map-point-info>b{font-size:13px}.map-point-info>span{margin-top:3px;color:#728378;font-size:10px}.map-point-info dl{display:grid;grid-template-columns:34px 1fr;gap:5px 10px;margin:10px 0 0;padding-top:8px;border-top:1px solid #e7eee8;font:10px/1.35 ui-monospace,SFMono-Regular,Consolas,monospace}.map-point-info dt{color:#829087}.map-point-info dd{margin:0;color:#294d37;font-weight:600}
@keyframes dotRipple{0%{transform:scale(.55);opacity:.55}100%{transform:scale(1.8);opacity:0}}
</style>
