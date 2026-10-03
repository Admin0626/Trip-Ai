<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { parseIntent, matchRoutes, preferenceOptions, type TravelIntent, type MatchResponse } from '@/api/modules/recommend'
import { preferenceApi,type UserPreference } from '@/api/modules/user'

const props=defineProps<{embedded?:boolean;sharedQuery?:string}>()
const emit=defineEmits<{'update:sharedQuery':[string]}>()
const query = ref(props.sharedQuery||'')
watch(query,value=>emit('update:sharedQuery',value))
watch(()=>props.sharedQuery,value=>{if(value!==undefined&&value!==query.value)query.value=value})
const intent = ref<TravelIntent | null>(null)
const destinationText = ref('')
const results = ref<MatchResponse | null>(null)
const parsing = ref(false)
const matching = ref(false)
const saved=ref<UserPreference|null>(null),useSaved=ref(true),preferenceLoading=ref(false),preferenceFailed=ref(false),budgetMin=ref<number|null>(null)
type DefaultField='days'|'budget'|'budgetMin'|'preferenceTags'|'avoid'
const applied=ref<Partial<Record<DefaultField,unknown>>>({})
const value=(key:DefaultField)=>key==='budgetMin'?budgetMin.value:intent.value?.[key]
const same=(a:unknown,b:unknown)=>JSON.stringify(a)===JSON.stringify(b)
const defaultLabels:Record<DefaultField,string>={days:'常用天数',budget:'预算上限',budgetMin:'预算下限',preferenceTags:'偏好标签',avoid:'排除标签'}
const appliedLabels=computed(()=>(Object.keys(applied.value) as DefaultField[]).filter(k=>same(value(k),applied.value[k])).map(k=>defaultLabels[k]))
async function loadPreference(){preferenceLoading.value=true;preferenceFailed.value=false;try{saved.value=await preferenceApi();if(useSaved.value)fillDefaults()}catch{preferenceFailed.value=true}finally{preferenceLoading.value=false}}
function fillDefaults(){
  if(!intent.value||!saved.value)return
  const i=intent.value,p=saved.value
  const explicitPreferred=i.preferenceTags.length>0,explicitAvoid=i.avoid.length>0
  const set=(key:DefaultField,v:unknown)=>{if(key==='budgetMin')budgetMin.value=v as number|null;else Object.assign(i,{[key]:v});applied.value[key]=structuredClone(v)}
  if(i.days==null&&p.preferredDays!=null)set('days',p.preferredDays)
  if(i.budget==null&&budgetMin.value==null&&(p.budgetMin>0||p.budgetMax>0)){if(p.budgetMin>0)set('budgetMin',p.budgetMin);if(p.budgetMax>0)set('budget',p.budgetMax)}
  if(!explicitPreferred&&p.preferenceTags.length)set('preferenceTags',p.preferenceTags.filter(t=>!explicitAvoid||!i.avoid.includes(t)))
  if(!explicitAvoid&&p.avoidTags.length)set('avoid',p.avoidTags.filter(t=>!explicitPreferred||!i.preferenceTags.includes(t)))
}
function togglePreference(){
  if(useSaved.value)fillDefaults()
  else{for(const key of Object.keys(applied.value) as DefaultField[]){if(!same(value(key),applied.value[key]))continue;if(key==='budgetMin')budgetMin.value=null;else if(intent.value)Object.assign(intent.value,{[key]:key==='preferenceTags'||key==='avoid'?[]:null})}applied.value={}}
}
const error = ref('')
const failedCovers = ref(new Set<number>())
let revision = 0

function invalidate() { revision++; results.value = null; error.value = '' }
watch(query, () => { invalidate(); intent.value = null;budgetMin.value=null;applied.value={} })
watch([intent, destinationText,budgetMin,useSaved], invalidate, { deep: true, flush: 'sync' })
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
    budgetMin.value=null;applied.value={};if(useSaved.value)fillDefaults()
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
    const response = await matchRoutes(snapshot,{budgetMin:budgetMin.value??null,useSavedPreference:useSaved.value})
    if (version === revision) results.value = response
  } catch { if (version === revision) error.value = '路线匹配失败，请重试。' }
  finally { matching.value = false }
}
onMounted(loadPreference)
</script>

<template>
  <main class="recommend-page" :class="{embedded}">
    <header v-if="!embedded" class="intro">
      <p class="eyebrow">从一个想法，找到下一站</p>
      <h1>旅行推荐</h1>
      <p>描述需求，确认条件，再挑选适合你的路线。</p>
    </header>
    <section class="panel">
      <h2>1. 说说你的旅行想法</h2>
      <el-input v-model="query" type="textarea" :rows="3" maxlength="500" show-word-limit
        aria-label="旅行需求" placeholder="例如：我想去云南玩5天，预算3000左右，喜欢自然风光和美食" />
      <el-button type="primary" :loading="parsing" :disabled="matching||preferenceLoading" @click="parse">解析需求</el-button>
      <p class="hint">当前使用基础规则解析，请检查并修正提取结果。</p>
      <el-checkbox v-model="useSaved" :disabled="preferenceLoading||preferenceFailed||matching" @change="togglePreference">参考已保存偏好</el-checkbox>
      <router-link to="/user/preference">管理旅行偏好</router-link>
      <p v-if="preferenceFailed" class="hint">偏好加载失败，本次可手动填写条件。<el-button link @click="loadPreference">重试加载偏好</el-button></p>
    </section>

    <section v-if="intent" class="panel" data-testid="intent-confirm">
      <h2>2. 确认你的筛选条件</h2>
      <el-alert title="这是初步解析结果，确认后才会查找路线。修改或清空优先，留空表示不限。" type="warning" :closable="false" />
      <p v-if="appliedLabels.length" class="hint" data-testid="saved-preference-note">已用保存的偏好预填：{{appliedLabels.join('、')}}。</p>
      <el-form label-position="top" class="criteria">
        <el-form-item label="目的地（最多3个，用顿号或逗号分隔）" class="wide">
          <el-input v-model="destinationText" aria-label="目的地" placeholder="例如：云南、大理；填写目的地、省份或城市全名" />
        </el-form-item>
        <el-form-item label="旅行天数（完全匹配）">
          <el-input-number v-model="intent.days" :min="1" :max="365" :precision="0" aria-label="旅行天数" />
        </el-form-item>
        <el-form-item label="人均路线预算下限（元）">
          <el-input-number v-model="budgetMin" :min="0" :max="99999999.99" :precision="2" :step="100" aria-label="预算下限" />
        </el-form-item>
        <el-form-item label="人均路线预算上限（元）">
          <el-input-number v-model="intent.budget" :min="0" :max="99999999.99" :precision="2" :step="100" aria-label="预算上限" />
        </el-form-item>
        <el-form-item label="偏好标签（最多8个，用于优先排序）">
          <el-select v-model="intent.preferenceTags" multiple :multiple-limit="8" aria-label="偏好标签">
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
      <el-button type="primary" :loading="matching" :disabled="parsing||preferenceLoading" @click="match">确认条件并查找路线</el-button>
    </section>

    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <section v-if="results" class="panel" data-testid="match-results" aria-live="polite">
      <h2>3. 为你找到的路线</h2>
      <p class="hint">基础匹配 · 共{{ results.totalCandidates }}条符合筛选条件，展示前{{ results.list.length }}条。优先匹配偏好标签，再按用户评分排序。</p>
      <p class="hint" data-testid="effective-criteria">实际条件：{{results.effectiveCriteria.days??'不限'}}天；人均预算 {{results.effectiveCriteria.budgetMin??'不限'}}—{{results.effectiveCriteria.budgetMax??'不限'}}元；排除 {{results.effectiveCriteria.avoid.join('、')||'无'}}。</p>
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
.recommend-page.embedded { max-width:none;padding:0; }
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
