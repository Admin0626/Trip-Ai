<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { parseIntent, matchRoutes, preferenceOptions, type TravelIntent, type MatchResponse } from '@/api/modules/recommend'

const query = ref('')
const intent = ref<TravelIntent | null>(null)
const destinationText = ref('')
const results = ref<MatchResponse | null>(null)
const parsing = ref(false)
const matching = ref(false)
const error = ref('')
const failedCovers = ref(new Set<number>())
let revision = 0

function invalidate() { revision++; results.value = null; error.value = '' }
watch(query, () => { invalidate(); intent.value = null })
watch([intent, destinationText], invalidate, { deep: true, flush: 'sync' })
onBeforeUnmount(() => { revision++ })

async function parse() {
  if (query.value.trim().length < 5 || query.value.length > 500) {
    ElMessage.warning('请填写5—500字的旅行需求'); return
  }
  invalidate(); intent.value = null
  const version = revision
  parsing.value = true
  try {
    const response = await parseIntent(query.value.trim())
    if (version !== revision) return
    destinationText.value = response.intent.destinations.join('、')
    intent.value = response.intent
  } catch { if (version === revision) error.value = '解析失败，请稍后重试，也可修改需求后重新解析。' }
  finally { parsing.value = false }
}

async function match() {
  if (!intent.value) return
  const destinations = destinationText.value.split(/[、，,]/).map(s => s.trim()).filter(Boolean)
  if (destinations.length > 3 || destinations.some(s => s.length > 100)) { ElMessage.warning('最多填写3个目的地，每项不超过100字'); return }
  if (intent.value.preferenceTags.some(t => intent.value!.avoid.includes(t))) { ElMessage.warning('偏好与排除标签不能重叠'); return }
  invalidate()
  const version = revision
  matching.value = true
  const snapshot = JSON.parse(JSON.stringify({ ...intent.value, destinations })) as TravelIntent
  // Element Plus clears input-number to undefined; normalize unknown numeric fields.
  snapshot.days ??= null; snapshot.budget ??= null
  try {
    const response = await matchRoutes(snapshot)
    if (version === revision) results.value = response
  } catch { if (version === revision) error.value = '路线匹配失败，请重试。' }
  finally { matching.value = false }
}
</script>

<template>
  <main class="recommend-page">
    <header class="intro">
      <p class="eyebrow">从一个想法，找到下一站</p>
      <h1>旅行推荐</h1>
      <p>描述需求，确认条件，再挑选适合你的路线。</p>
    </header>
    <section class="panel">
      <h2>1. 说说你的旅行想法</h2>
      <el-input v-model="query" type="textarea" :rows="3" maxlength="500" show-word-limit
        aria-label="旅行需求" placeholder="例如：我想去云南玩5天，预算3000左右，喜欢自然风光和美食" />
      <el-button type="primary" :loading="parsing" :disabled="matching" @click="parse">解析需求</el-button>
      <p class="hint">当前使用基础规则解析，请检查并修正提取结果。</p>
    </section>

    <section v-if="intent" class="panel" data-testid="intent-confirm">
      <h2>2. 确认你的筛选条件</h2>
      <el-alert title="这是初步解析结果，确认后才会查找路线。留空表示不限。" type="warning" :closable="false" />
      <el-form label-position="top" class="criteria">
        <el-form-item label="目的地（最多3个，用顿号或逗号分隔）" class="wide">
          <el-input v-model="destinationText" aria-label="目的地" placeholder="例如：云南、大理；填写目的地、省份或城市全名" />
        </el-form-item>
        <el-form-item label="旅行天数（完全匹配）">
          <el-input-number v-model="intent.days" :min="1" :max="365" :precision="0" aria-label="旅行天数" />
        </el-form-item>
        <el-form-item label="人均路线预算上限（元）">
          <el-input-number v-model="intent.budget" :min="1" :max="1000000" :precision="0" :step="100" aria-label="预算上限" />
        </el-form-item>
        <el-form-item label="偏好标签（最多5个，用于优先排序）">
          <el-select v-model="intent.preferenceTags" multiple :multiple-limit="5" aria-label="偏好标签">
            <el-option v-for="tag in preferenceOptions" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </el-form-item>
        <el-form-item label="排除标签（不会推荐含该标签的路线）">
          <el-select v-model="intent.avoid" multiple aria-label="排除标签">
            <el-option v-for="tag in preferenceOptions" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </el-form-item>
      </el-form>
      <p class="hint">预算按路线的人均参考价筛选，不代表整趟旅行总费用。同行人、节奏、月份和必去景点暂不参与筛选。</p>
      <el-button type="primary" :loading="matching" :disabled="parsing" @click="match">确认条件并查找路线</el-button>
    </section>

    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <section v-if="results" class="panel" data-testid="match-results" aria-live="polite">
      <h2>3. 为你找到的路线</h2>
      <p class="hint">基础匹配 · 共{{ results.totalCandidates }}条符合筛选条件，展示前{{ results.list.length }}条。优先匹配偏好标签，再按用户评分排序。</p>
      <el-empty v-if="!results.list.length" description="没有符合条件的路线，试试减少筛选条件或提高预算。" />
      <div v-else class="result-grid">
        <article v-for="route in results.list" :key="route.routeId" class="match-card">
          <div class="cover">
            <img v-if="route.coverImg && !failedCovers.has(route.routeId)" :src="route.coverImg" :alt="route.title"
              loading="lazy" @error="failedCovers.add(route.routeId)" />
            <span v-else>{{ route.destinationName }} · 发现下一段旅程</span>
          </div>
          <div class="match-card__body">
            <h3>{{ route.title }}</h3>
            <p>{{ route.destinationName }} · {{ route.days }}天 · <strong>¥{{ route.price }}/人</strong></p>
            <p class="reason">{{ route.reason }}</p>
            <router-link :to="`/route/${route.routeId}`">查看路线详情 →</router-link>
          </div>
        </article>
      </div>
    </section>
  </main>
</template>

<style scoped lang="scss">
.recommend-page { max-width: 1000px; margin: 0 auto; padding: 32px 20px 60px; }
.intro { margin-bottom: 28px; h1 { font-size: 32px; margin: 8px 0; } p { color: #64748b; } }
.eyebrow { color: #2563eb !important; font-size: 14px; }
.panel { background: #fff; border: 1px solid #e2e8f0; border-radius: 14px; padding: 24px; margin-bottom: 24px; h2 { font-size: 20px; margin: 0 0 18px; } }
.panel > .el-button { margin-top: 16px; }
.hint { color: #64748b; line-height: 1.7; font-size: 14px; }
.criteria { display: grid; grid-template-columns: 1fr 1fr; gap: 0 24px; margin-top: 20px; .wide { grid-column: 1 / -1; } }
.result-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
.match-card { border: 1px solid #e2e8f0; border-radius: 10px; overflow: hidden; img { width: 100%; height: 170px; object-fit: cover; } }
.cover { min-height: 170px; display: grid; place-items: center; background: linear-gradient(135deg, #dbeafe, #ecfdf5); color: #1e40af; font-size: 16px; }
.match-card__body { padding: 18px; h3 { margin: 0 0 12px; } a { color: #2563eb; } }
.reason { font-size: 14px; color: #475569; line-height: 1.6; }
@media (max-width: 640px) { .criteria, .result-grid { grid-template-columns: 1fr; } .panel { padding: 18px; } .recommend-page { padding: 24px 12px; } }
</style>
