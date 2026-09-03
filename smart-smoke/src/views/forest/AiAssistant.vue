<template>
  <div class="forest-shell">
    <aside>
      <div class="logo"><el-icon><MostlyCloudy /></el-icon><div><b>福州国家森林公园</b><span>森林安全智能助手</span></div></div>
      <div class="side-block">
        <div class="side-head"><span>对话记录</span><el-button text size="small" @click="newConversation"><el-icon><Plus /></el-icon>新建</el-button></div>
        <el-scrollbar class="conv-list">
          <div v-for="conv in conversations" :key="conv.id" class="conv-item" :class="{ active: currentId === 'c-' + conv.id }" @click="openConversation(conv)">
            <div class="conv-text"><b>{{ conv.title }}</b><span>{{ conv.updateTime }}</span></div>
            <el-button text circle size="small" class="conv-del" @click.stop="removeConversation(conv)"><el-icon><Delete /></el-icon></el-button>
          </div>
          <p v-if="!conversations.length" class="side-empty">暂无历史对话</p>
        </el-scrollbar>
      </div>
      <div class="side-block" v-if="isAdmin">
        <div class="side-head"><span>知识库管理（管理员）</span></div>
        <div class="admin-tools">
          <el-button size="small" type="primary" plain :loading="reindexing" @click="reindex">重建知识库</el-button>
          <el-button size="small" plain @click="triggerUpload">导入文档</el-button>
          <input ref="fileInput" type="file" accept=".md,.txt,.pdf" class="hidden-file" @change="onFilePicked" />
          <el-button size="small" text @click="loadDocuments">刷新列表</el-button>
        </div>
        <p class="side-hint">仅 .md / .txt / .pdf，≤5MB；导入与重建为管理员权限，操作会写入审计日志。</p>
      </div>
      <div class="account"><el-avatar>{{ userName.charAt(0) }}</el-avatar><div><b>{{ userName }}</b><span>{{ isAdmin ? '管理员' : '护林员' }}</span></div><el-button text circle @click="logout"><el-icon><SwitchButton /></el-icon></el-button></div>
    </aside>

    <main>
      <header>
        <div><div class="eyebrow">LOCAL FOREST AI ASSISTANT</div><h1>森林安全智能助手</h1><p>本地 Ollama 小模型 + Qdrant 知识库 · 只读查询 · 无需云端 API Key</p></div>
        <div class="head-actions">
          <div class="health-chips">
            <el-tag :type="healthTag(health.aiEnabled)" size="small">AI {{ health.aiEnabled ? '已启用' : '已关闭' }}</el-tag>
            <el-tag :type="healthTag(health.ollamaReachable)" size="small">Ollama {{ health.ollamaReachable ? '正常' : '不可用' }}</el-tag>
            <el-tag :type="healthTag(health.chatModelExists)" size="small">{{ health.chatModel || '模型' }} {{ health.chatModelExists ? '已就绪' : '缺失' }}</el-tag>
            <el-tag :type="healthTag(health.qdrantReachable)" size="small">Qdrant {{ health.qdrantReachable ? '正常' : '不可用' }}</el-tag>
            <el-tag :type="healthTag((health.indexedDocuments || 0) > 0)" size="small">知识片段 {{ health.knowledgePoints || 0 }}</el-tag>
          </div>
          <el-button plain @click="router.push('/ranger')"><el-icon><Back /></el-icon> 返回工作台</el-button>
        </div>
      </header>

      <el-alert v-if="health.degraded" type="warning" show-icon :closable="false" class="degrade-alert"
        :title="`AI 处于降级模式：${(health.degradedReasons || []).join('；') || '部分组件不可用'}（原有业务功能不受影响）`" />

      <el-card class="chat-card" shadow="never" body-class="chat-body">
        <el-scrollbar ref="scrollerRef" class="chat-scroll">
          <div v-if="!messages.length" class="welcome">
            <el-icon class="welcome-icon"><ChatDotSquare /></el-icon>
            <h2>你好，{{ userName }}</h2>
            <p>我可以基于本地知识库与系统实时数据回答防火业务问题。AI 结果仅供辅助判断，<b>火情与高风险事件必须人工复核</b>。</p>
            <div class="samples">
              <button v-for="s in samples" :key="s" class="sample" @click="send(s)">{{ s }}</button>
            </div>
          </div>
          <article v-for="(msg, i) in messages" :key="i" class="msg" :class="msg.role">
            <div class="bubble">
              <div class="content">{{ msg.content }}</div>
              <div v-if="msg.role === 'assistant'">
                <div v-if="msg.degraded && (msg.degradationNotes || []).length" class="notes">
                  <span v-for="(n, j) in msg.degradationNotes" :key="j">⚠ {{ n }}</span>
                </div>
                <div v-if="(msg.sources || []).length" class="sources">
                  <b>来源：</b>
                  <el-tag v-for="(s, j) in msg.sources" :key="j" size="small" :type="s.type === 'knowledge' ? 'success' : 'info'" effect="plain">
                    {{ s.type === 'knowledge' ? '📚' : '📡' }} {{ s.name }}
                  </el-tag>
                </div>
                <div v-if="(msg.suggestedActions || []).length" class="actions">
                  <el-button v-for="a in msg.suggestedActions" :key="a.target + a.label" size="small" type="primary" plain @click="router.push(a.target)">{{ a.label }}</el-button>
                </div>
                <div v-if="msg.needHumanReview" class="review-flag"><el-icon><Warning /></el-icon> 高风险结论 · 需人工复核</div>
                <div class="meta"><span>{{ msg.model === 'unavailable' ? '模型不可用（降级）' : msg.model }}</span><span v-if="msg.latencyMs">{{ (msg.latencyMs / 1000).toFixed(1) }}s</span><span v-if="msg.time">{{ msg.time }}</span></div>
              </div>
            </div>
          </article>
          <article v-if="sending" class="msg assistant">
            <div class="bubble"><div class="content thinking"><el-icon class="spin"><Loading /></el-icon> 本地模型思考中（qwen3:4b 首次加载可能需要几十秒）…</div></div>
          </article>
        </el-scrollbar>
        <div class="composer">
          <el-input v-model="draft" type="textarea" :rows="2" resize="none" maxlength="2000" show-word-limit
            :placeholder="sending ? '正在回复…' : '询问项目问题，如：GT-01 最近24小时数据如何？（Enter 发送，Shift+Enter 换行）'"
            @keydown.enter.exact.prevent="send()" />
          <div class="composer-side">
            <el-button type="primary" :loading="sending" :disabled="!draft.trim()" @click="send()"><el-icon><Promotion /></el-icon>发送</el-button>
          </div>
        </div>
      </el-card>
      <p class="disclaimer">AI 结果仅供辅助判断，高风险事件需要人工复核；本助手无任何修改权限，所有回答基于本地模型与系统数据。</p>
    </main>

    <el-dialog v-model="docsDialog" title="知识库文档" width="760px">
      <el-table :data="documents" size="small" max-height="420">
        <el-table-column prop="filename" label="文档" min-width="200" show-overflow-tooltip />
        <el-table-column prop="relativePath" label="路径" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag size="small" :type="docTag(row.status)">{{ docStatusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="片段" width="70" />
        <el-table-column prop="indexedAt" label="最近索引" width="150" />
        <el-table-column prop="lastError" label="错误" min-width="150" show-overflow-tooltip />
      </el-table>
      <p class="side-hint">状态说明：indexed 已索引 / indexing 索引中 / failed 失败 / removed 源文件已删除。文档内容未变化时重建会跳过。</p>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MostlyCloudy, Plus, Delete, SwitchButton, Back, ChatDotSquare, Warning, Loading, Promotion } from '@element-plus/icons-vue'
import { aiChat, deleteAiConversation, getAiConversation, getAiHealth, listAiConversations, listKnowledgeDocuments, reindexKnowledge, uploadKnowledgeDocument } from '@/api/ai'

const router = useRouter()
const user = JSON.parse(localStorage.getItem('currentUser') || '{}')
const userName = computed(() => user.username || '护林员')
const isAdmin = computed(() => user.role === 'admin')

const health = ref({})
const conversations = ref([])
const currentId = ref(null)
const messages = ref([])
const draft = ref('')
const sending = ref(false)
const scrollerRef = ref(null)
const documents = ref([])
const docsDialog = ref(false)
const reindexing = ref(false)
const fileInput = ref(null)

const samples = [
  '今天有多少未结案火情？',
  'GT-01 最近24小时传感器数据如何，有什么异常？',
  '分析当前高风险设备并给出复核建议',
  '生成今日巡护总结',
  '系统的烟雾告警阈值是多少？',
  '三级警情会触发哪些系统联动？'
]

let healthTimer = null
onMounted(() => {
  loadHealth()
  loadConversations()
  healthTimer = setInterval(loadHealth, 30000)
})
onUnmounted(() => clearInterval(healthTimer))

async function loadHealth() {
  try { const res = await getAiHealth(); if (res.code === 200) health.value = res.data || {} } catch { health.value = { degraded: true, degradedReasons: ['无法连接后端服务'] } }
}
async function loadConversations() {
  try { const res = await listAiConversations(); if (res.code === 200) conversations.value = res.data || [] } catch { /* 静默 */ }
}
function newConversation() { currentId.value = null; messages.value = [] }
async function openConversation(conv) {
  const external = 'c-' + conv.id
  currentId.value = external
  try {
    const res = await getAiConversation(external)
    if (res.code !== 200) return ElMessage.error(res.msg || '加载会话失败')
    messages.value = (res.data.messages || []).map(m => ({
      role: m.role, content: m.content, model: m.modelName, latencyMs: m.latencyMs, time: (m.createTime || '').slice(5, 16),
      sources: safeParse(m.sourceJson), suggestedActions: [], degradationNotes: []
    }))
    scrollToBottom()
  } catch { ElMessage.error('加载会话失败') }
}
function safeParse(value) { try { return JSON.parse(value) || [] } catch { return [] } }
async function removeConversation(conv) {
  try {
    await ElMessageBox.confirm(`确定清除会话「${conv.title}」？仅能清除本人会话。`, '清除对话', { type: 'warning' })
    const res = await deleteAiConversation('c-' + conv.id)
    if (res.code !== 200) return ElMessage.error(res.msg || '清除失败')
    if (currentId.value === 'c-' + conv.id) newConversation()
    await loadConversations()
    ElMessage.success('会话已清除')
  } catch { /* 取消 */ }
}

async function send(text) {
  const question = (text ?? draft.value).trim()
  if (!question || sending.value) return
  if (question.length > 2000) return ElMessage.warning('问题过长（最多 2000 字）')
  draft.value = ''
  messages.value.push({ role: 'user', content: question })
  sending.value = true
  scrollToBottom()
  try {
    const res = await aiChat({ conversationId: currentId.value, message: question })
    if (res.code !== 200) {
      messages.value.push({ role: 'assistant', content: `请求失败：${res.msg || '未知错误'}`, model: 'error', needHumanReview: false, sources: [], suggestedActions: [], degradationNotes: [] })
      return
    }
    const data = res.data || {}
    currentId.value = data.conversationId || currentId.value
    messages.value.push({
      role: 'assistant', content: data.answer || '（空回答）', model: data.model, latencyMs: data.latencyMs,
      sources: data.sources || [], suggestedActions: data.suggestedActions || [],
      needHumanReview: !!data.needHumanReview,
      degraded: !!data.degraded, degradationNotes: data.degradationNotes || [], time: new Date().toTimeString().slice(0, 5)
    })
    loadConversations()
  } catch (err) {
    messages.value.push({ role: 'assistant', content: '无法连接 AI 服务。请确认后端与网络正常；系统原有功能不受此影响。', model: 'unavailable', sources: [], suggestedActions: [], degraded: true, degradationNotes: ['请求异常，可稍后重试'], needHumanReview: false })
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

function scrollToBottom() {
  nextTick(() => { const el = scrollerRef.value?.wrapRef; if (el) el.scrollTop = el.scrollHeight })
}

function healthTag(ok) { return ok ? 'success' : 'danger' }
function docTag(status) { return { indexed: 'success', indexing: 'warning', failed: 'danger', removed: 'info', pending: 'info' }[status] || 'info' }
function docStatusText(status) { return { indexed: '已索引', indexing: '索引中', failed: '失败', removed: '已删除', pending: '待处理' }[status] || status }

async function reindex() {
  reindexing.value = true
  try {
    const res = await reindexKnowledge(false)
    ElMessage[res.code === 200 ? 'success' : 'error'](res.msg || (res.code === 200 ? res.data : '重建失败'))
    setTimeout(() => { loadHealth(); loadDocuments() }, 8000)
  } catch { ElMessage.error('重建请求失败') } finally { reindexing.value = false }
}
async function loadDocuments() {
  try { const res = await listKnowledgeDocuments(); if (res.code === 200) { documents.value = res.data || []; docsDialog.value = true } else ElMessage.error(res.msg || '加载失败') } catch { ElMessage.error('加载文档列表失败') }
}
function triggerUpload() { fileInput.value?.click() }
async function onFilePicked(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (!/\.(md|txt|pdf)$/i.test(file.name)) return ElMessage.warning('仅支持 .md / .txt / .pdf')
  if (file.size > 5 * 1024 * 1024) return ElMessage.warning('文件大小超过 5MB 限制')
  try {
    const res = await uploadKnowledgeDocument(file)
    if (res.code !== 200) return ElMessage.error(res.msg || '导入失败')
    ElMessage.success(res.data?.message || '已提交索引')
    setTimeout(() => { loadHealth(); loadDocuments() }, 8000)
  } catch { ElMessage.error('导入失败') }
}
function logout() { localStorage.removeItem('token'); localStorage.removeItem('currentUser'); router.push('/login') }
</script>

<style scoped>
.forest-shell{min-height:100vh;background:linear-gradient(145deg,#f5f7f2,#edf2eb);color:#20352a;display:flex}
.forest-shell aside{width:250px;background:linear-gradient(180deg,#123c2a,#183f2e 62%,#102f22);color:#fff;position:fixed;inset:0 auto 0 0;display:flex;flex-direction:column;z-index:10;box-shadow:12px 0 34px rgba(18,57,40,.08)}
.logo{height:82px;padding:0 18px;display:flex;align-items:center;gap:12px;border-bottom:1px solid rgba(255,255,255,.08);flex-shrink:0}
.logo>.el-icon{font-size:30px;color:#a7dcb2}
.logo b,.logo span{display:block}.logo b{font-size:15px}.logo span{font-size:10px;letter-spacing:.12em;color:#91b3a0;margin-top:4px}
.side-block{padding:12px 10px;border-top:1px solid rgba(255,255,255,.06)}
.side-head{display:flex;align-items:center;justify-content:space-between;color:#9fbaaa;font-size:12px;padding:2px 8px 8px;font-weight:700;letter-spacing:.08em}
.side-head :deep(.el-button){color:#a7dcb2}
.conv-list{height:250px}
.conv-item{display:flex;align-items:center;gap:6px;padding:8px 8px;border-radius:10px;cursor:pointer;margin:3px 0;transition:background .2s}
.conv-item:hover{background:rgba(99,159,119,.18)}
.conv-item.active{background:linear-gradient(90deg,#2d6b4b,#347455)}
.conv-text{min-width:0;flex:1}
.conv-text b,.conv-text span{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.conv-text b{font-size:12px;font-weight:600}
.conv-text span{font-size:10px;color:#91b3a0;margin-top:2px}
.conv-del{color:#7e9a8b !important}.conv-del:hover{color:#ffd9d4 !important}
.side-empty,.side-hint{color:#7e9a8b;font-size:11px;padding:4px 8px;margin:0}
.admin-tools{display:flex;flex-wrap:wrap;gap:6px;padding:0 6px}
.hidden-file{display:none}
.account{margin-top:auto;padding:16px;display:flex;align-items:center;gap:10px;border-top:1px solid rgba(255,255,255,.08)}
.account .el-avatar{background:#79b889}
.account>div{min-width:0;flex:1}
.account b,.account span{display:block}
.account span{font-size:11px;color:#9fbaaa;margin-top:3px}
.account .el-button{color:#bfd2c5}
.forest-shell main{margin-left:250px;padding:25px 28px 18px;width:calc(100% - 250px);min-width:0;display:flex;flex-direction:column;min-height:100vh;box-sizing:border-box}
.forest-shell header{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:14px;flex-wrap:wrap}
.eyebrow{font-size:10px;letter-spacing:.18em;color:#709080;font-weight:700}
.forest-shell h1{font-size:25px;margin:5px 0}
.forest-shell header p{margin:0;font-size:12px;color:#5f7d6c}
.head-actions{display:flex;flex-direction:column;align-items:flex-end;gap:10px}
.health-chips{display:flex;gap:6px;flex-wrap:wrap;justify-content:flex-end}
.degrade-alert{margin-bottom:12px}
.chat-card{flex:1;display:flex;flex-direction:column;min-height:0;border:1px solid #dbe4da;border-radius:16px;overflow:hidden;background:#fff}
.chat-card :deep(.el-card__body){flex:1;display:flex;flex-direction:column;min-height:0;padding:0}
.chat-body{min-height:0}
.chat-scroll{flex:1;min-height:320px;padding:18px 22px;box-sizing:border-box;background:linear-gradient(180deg,#fbfcfa,#f4f7f2)}
.welcome{text-align:center;padding:48px 18px;color:#44644f}
.welcome-icon{font-size:46px;color:#5c9a70}
.welcome h2{margin:12px 0 6px;font-size:20px}
.welcome p{margin:0 auto 18px;font-size:13px;max-width:520px;color:#5f7d6c}
.samples{display:flex;flex-wrap:wrap;gap:8px;justify-content:center}
.sample{border:1px solid #cfe0cf;background:#fff;color:#2f5c3c;border-radius:16px;font-size:12px;padding:7px 12px;cursor:pointer;transition:.2s}
.sample:hover{background:#eaf5ea;transform:translateY(-1px)}
.msg{display:flex;margin-bottom:14px}
.msg.user{justify-content:flex-end}
.bubble{max-width:78%;background:#fff;border:1px solid #e0e7de;border-radius:14px;padding:10px 14px;box-shadow:0 4px 14px rgba(18,57,40,.05)}
.msg.user .bubble{background:linear-gradient(135deg,#2d6b4b,#3a7d59);color:#fff;border:0}
.content{white-space:pre-wrap;word-break:break-word;font-size:13.5px;line-height:1.75}
.thinking{color:#5f7d6c;display:flex;align-items:center;gap:8px}
.spin{animation:rot 1.1s linear infinite}
@keyframes rot{to{transform:rotate(360deg)}}
.notes{margin-top:8px;font-size:11.5px;color:#a05a12;display:flex;flex-direction:column;gap:3px}
.sources{margin-top:9px;display:flex;flex-wrap:wrap;gap:6px;align-items:center;font-size:11.5px;color:#5f7d6c}
.sources b{font-weight:600}
.actions{margin-top:9px;display:flex;gap:8px;flex-wrap:wrap}
.review-flag{margin-top:9px;color:#b4443a;font-size:12px;display:flex;align-items:center;gap:5px;font-weight:600}
.meta{margin-top:8px;display:flex;gap:12px;font-size:10.5px;color:#8aa295}
.composer{display:flex;gap:10px;align-items:flex-end;padding:12px 16px;border-top:1px solid #e4ebe2;background:#fff}
.composer :deep(.el-textarea__inner){box-shadow:none;border:1px solid #d7e2d5;border-radius:12px;padding:10px 12px}
.composer-side{padding-bottom:2px}
.disclaimer{text-align:center;font-size:11px;color:#83988a;margin:10px 0 0}
@media (max-width:860px){.forest-shell aside{display:none}.forest-shell main{margin-left:0;width:100%}.bubble{max-width:92%}}
</style>
