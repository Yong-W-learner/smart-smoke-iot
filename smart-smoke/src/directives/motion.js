// 统一动效：v-reveal 滚动渐入 / v-tilt 3D 倾斜 / v-count-to 数字滚动 + 全局光标光晕
// 均尊重系统「减少动态效果」设置；仅精确指针设备启用 3D 倾斜。
const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
const finePointer = window.matchMedia('(pointer: fine)').matches

let observer = null
function getObserver() {
  if (!observer && 'IntersectionObserver' in window) {
    observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (!entry.isIntersecting) return
        const el = entry.target
        const delay = Number(el.dataset.delay || 0)
        setTimeout(() => el.classList.add('visible'), delay)
        observer.unobserve(el)
      })
    }, { threshold: 0.08 })
  }
  return observer
}

// v-reveal：元素进入视口时淡入上移；value 可传延迟毫秒，或 { delay } 对象
export const reveal = {
  mounted(el, binding) {
    el.classList.add('reveal')
    const delay = binding.value && typeof binding.value === 'object' ? binding.value.delay : binding.value
    if (delay) el.dataset.delay = String(delay)
    if (prefersReduced) { el.classList.add('visible'); return }
    const ob = getObserver()
    ob ? ob.observe(el) : el.classList.add('visible')
  },
  unmounted(el) { observer && observer.unobserve(el) }
}

// v-tilt：卡片随鼠标轻微 3D 倾斜（仅精确指针设备）
export const tilt = {
  mounted(el) {
    if (prefersReduced || !finePointer) return
    el.classList.add('tilt')
    const move = (e) => {
      const rect = el.getBoundingClientRect()
      const x = (e.clientX - rect.left) / rect.width - 0.5
      const y = (e.clientY - rect.top) / rect.height - 0.5
      el.style.transform = `perspective(950px) rotateX(${-y * 3.2}deg) rotateY(${x * 4.2}deg) translateY(-2px)`
    }
    const leave = () => { el.style.transform = '' }
    el.addEventListener('pointermove', move, { passive: true })
    el.addEventListener('pointerleave', leave)
    el.__tiltCleanup = () => {
      el.removeEventListener('pointermove', move)
      el.removeEventListener('pointerleave', leave)
    }
  },
  unmounted(el) { el.__tiltCleanup && el.__tiltCleanup() }
}

function easeOutCubic(t) { return 1 - Math.pow(1 - t, 3) }

// v-count-to：数字从当前值缓动到目标值（按整数显示）
export const countTo = {
  mounted(el, binding) {
    const target = Number(binding.value) || 0
    el.__countToken = 0
    el.__countValue = 0
    el.__countTarget = target
    el.textContent = '0'
    if (prefersReduced) { el.textContent = target; return }
    run(el, 0, target)
  },
  updated(el, binding) {
    const target = Number(binding.value) || 0
    if (target === el.__countTarget) return
    el.__countTarget = target
    if (prefersReduced) { el.textContent = target; return }
    run(el, el.__countValue, target)
  }
}

function run(el, from, to) {
  const token = ++el.__countToken
  const start = performance.now()
  const duration = 850
  const tick = (now) => {
    if (token !== el.__countToken) return
    const p = Math.min(1, (now - start) / duration)
    const v = from + (to - from) * easeOutCubic(p)
    el.textContent = Math.round(v)
    if (p < 1) requestAnimationFrame(tick)
    else { el.textContent = to; el.__countValue = to }
  }
  requestAnimationFrame(tick)
}

// 全局光标光晕：柔和蓝光跟随鼠标
export function initMotion() {
  if (prefersReduced) return
  const glow = document.createElement('div')
  glow.className = 'cursor-glow'
  glow.style.left = '-600px'
  glow.style.top = '-600px'
  document.body.appendChild(glow)
  window.addEventListener('pointermove', (e) => {
    glow.style.left = e.clientX + 'px'
    glow.style.top = e.clientY + 'px'
  }, { passive: true })
}
