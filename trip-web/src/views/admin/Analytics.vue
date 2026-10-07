<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { analysisDashboard, type AnalysisDashboard } from '@/api/modules/analysis'
import AnalyticsChart from '@/components/admin/AnalyticsChart.vue'

const days=ref(30),destinationId=ref<number>(),data=ref<AnalysisDashboard|null>(null),loading=ref(false),error=ref('')
const options=ref<AnalysisDashboard['destinations']>([]),destinationTotal=ref(0),metric=ref<'bookings'|'newUsers'|'aiCalls'>('bookings')
let version=0,controller:AbortController|undefined
async function load(){
  const v=++version;controller?.abort();controller=new AbortController();data.value=null;error.value='';loading.value=true
  try{const response=await analysisDashboard(days.value,destinationId.value,controller.signal);if(v!==version)return;data.value=response;options.value=response.destinations;destinationTotal.value=response.destinationTotal}
  catch(e){if(v===version)error.value=e instanceof Error?e.message:'统计加载失败，请重试'}
  finally{if(v===version)loading.value=false}
}
onMounted(load)
onBeforeUnmount(()=>{++version;controller?.abort()})
const title=computed(()=>({bookings:'预约创建趋势',newUsers:'新增用户趋势（全站）',aiCalls:'模型调用记录趋势（全站）'}[metric.value]))
const trend=computed(()=>data.value?.trend.map(d=>({id:d.date,label:d.date,value:d[metric.value]}))??[])
const heat=computed(()=>data.value?.destinationHeat.map(d=>({id:d.id,label:d.name,value:d.bookings}))??[])
const scatter=computed(()=>data.value?.routeScatter.map(d=>({id:d.id,label:d.title,value:d.bookings,x:d.price}))??[])
const bookingStates=computed(()=>data.value?.bookingStatuses.map(d=>({id:d.status,label:d.label,value:d.count}))??[])
const aiStates=computed(()=>data.value?.aiStatuses.map(d=>({id:d.status,label:d.label,value:d.count}))??[])
function percent(part:number,total:number){return total?`${(part/total*100).toFixed(1)}%`:'暂无记录'}
</script>
<template>
  <main class="admin-page analysis-page" data-testid="analytics-page" :aria-busy="loading">
    <header class="analysis-heading"><div><p class="analysis-eyebrow">运营观察</p><h1>数据与可视化</h1><p class="admin-help">从实际预约、目录和调用记录，了解近期业务变化。</p></div></header>
    <section class="admin-panel analysis-filters" aria-label="统计筛选">
      <label>统计窗口<el-select v-model="days" aria-label="统计窗口" @change="load"><el-option v-for="n in [7,30,90]" :key="n" :label="`近${n}日`" :value="n"/></el-select></label>
      <label>目的地<el-select v-model="destinationId" aria-label="统计目的地" clearable filterable placeholder="全部目的地" @change="load"><el-option v-for="d in options" :key="d.id" :label="d.name" :value="d.id"/></el-select></label>
      <el-button :loading="loading" @click="load">刷新统计</el-button>
      <p class="admin-note">目的地筛选仅影响路线与预约；用户、模型指标为全站。含上下架，排除已删除路线与目的地。</p>
      <p v-if="destinationTotal>options.length" class="admin-note">筛选列出前{{options.length}}个目的地，共{{destinationTotal}}个；当前全部统计仍覆盖全部有效目的地。</p>
    </section>
    <p v-if="loading" class="analysis-loading" role="status">正在读取统计数据…</p>
    <section v-else-if="error" class="admin-panel" role="alert"><p>无法加载统计：{{error}}</p><el-button @click="load">重试统计</el-button></section>
    <template v-else-if="data">
      <p class="analysis-snapshot" data-testid="analysis-snapshot">{{data.startDate}} 至 {{data.endDate}} · 北京时间 · {{data.destinationId===null?'全部目的地':options.find(d=>d.id===data!.destinationId)?.name||`目的地 ${data.destinationId}`}} · 更新于 {{data.generatedAt.slice(0,19).replace('T',' ')}}</p>
      <dl class="analysis-metrics" data-testid="analysis-metrics">
        <div><dt>窗口内预约单数</dt><dd>{{data.summary.bookings}}</dd><small>已取消 {{data.summary.cancelled}} · {{percent(data.summary.cancelled,data.summary.bookings)}}</small></div>
        <div><dt>当前有效路线</dt><dd>{{data.summary.routes}}</dd><small>当前筛选范围 · 含下架</small></div>
        <div><dt>窗口内新增用户</dt><dd>{{data.summary.newUsers}}</dd><small>全站未注销账号 {{data.summary.users}}</small></div>
        <div><dt>窗口内模型记录</dt><dd>{{data.summary.aiCalls}}</dd><small>成功 {{percent(data.summary.aiSucceeded,data.summary.aiCalls)}} · 平均 {{data.summary.aiAverageMs===null?'暂无':data.summary.aiAverageMs.toFixed(0)+'ms'}}</small></div>
      </dl>
      <section class="admin-panel analysis-trend"><div class="analysis-chart-toolbar"><label>趋势指标<el-select v-model="metric" aria-label="趋势指标"><el-option label="预约创建" value="bookings"/><el-option label="新增用户（全站）" value="newUsers"/><el-option label="模型调用（全站）" value="aiCalls"/></el-select></label></div><AnalyticsChart kind="line" :title="title" :data="trend" y-label="记录数"/><p class="admin-note">每天按创建时间统计，无记录日补0；不表示出行日期或当日实际取消动作。</p></section>
      <div class="analysis-grid"><section class="admin-panel"><AnalyticsChart kind="bar" title="目的地预约热度 · Top 10" :data="heat"/><p class="admin-note">按窗口内预约单数排序，含当前已取消预约。</p></section><section class="admin-panel"><AnalyticsChart kind="scatter" title="路线参考价与预约量" :data="scatter" x-label="人均参考价（元）" y-label="预约单数"/><p class="admin-note">每点代表一条当前有效路线。参考价并非实收金额，图形不证明价格导致预约变化。{{data.matchingRoutes>data.scatterLimit?`仅展示预约量最高的${data.scatterLimit}条，共${data.matchingRoutes}条；不代表全部分布。`:`共${data.matchingRoutes}条路线，含零预约。`}}</p></section></div>
      <div class="analysis-grid"><section class="admin-panel"><AnalyticsChart kind="bar" title="窗口预约当前状态" :data="bookingStates"/><p class="admin-note">统计窗口内创建的预约，目前处于各状态的数量。</p></section><section class="admin-panel"><AnalyticsChart kind="bar" title="模型调用结果（全站）" :data="aiStates" x-label="记录数"/><p class="admin-note">每条llm_call_log计一次，可能含结构重试；不等于用户操作次数或计费金额。</p></section></div>
      <section class="admin-panel analysis-table"><h2>路线散点明细</h2><p class="admin-note">与图中数据一致，按预约量降序、路线ID升序；可在路线管理中查看内容。</p><el-table :data="data.routeScatter" empty-text="当前范围暂无路线"><el-table-column prop="title" label="路线" min-width="200"/><el-table-column prop="destination" label="目的地" min-width="130"/><el-table-column prop="price" label="参考价（元/人）" width="150"/><el-table-column prop="days" label="天数" width="70"/><el-table-column prop="bookings" label="预约单数" width="100"/><el-table-column label="状态" width="90"><template #default="s">{{s.row.status===1?'上架':'下架'}}</template></el-table-column></el-table></section>
      <details class="admin-panel analysis-table"><summary>查看每日统计明细（{{data.days}}天）</summary><el-table :data="data.trend"><el-table-column prop="date" label="日期" width="140"/><el-table-column prop="bookings" label="预约单数" min-width="100"/><el-table-column prop="cancelled" label="其中当前已取消" min-width="145"/><el-table-column prop="newUsers" label="新增用户（全站）" min-width="150"/><el-table-column prop="aiCalls" label="模型记录（全站）" min-width="150"/></el-table></details>
    </template>
  </main>
</template>
<style scoped>
.analysis-page{padding-top:32px}.analysis-eyebrow{margin:0 0 10px;color:var(--trip-forest);font-size:14px}.analysis-heading h1{font-size:30px}.analysis-filters{display:flex;flex-wrap:wrap;align-items:end;gap:16px}.analysis-filters label,.analysis-chart-toolbar label{display:grid;gap:8px;font-size:14px}.analysis-filters .el-select{width:210px}.analysis-filters .admin-note{width:100%;margin:0}.analysis-snapshot{font-size:14px;line-height:1.7;color:var(--trip-muted);overflow-wrap:anywhere;margin:24px 0}.analysis-metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));margin:24px 0 32px;border-block:1px solid var(--trip-border)}.analysis-metrics>div{padding:24px 16px;border-right:1px solid var(--trip-border)}.analysis-metrics>div:last-child{border:0}.analysis-metrics dt{font-size:14px;color:var(--trip-muted)}.analysis-metrics dd{margin:10px 0;font-size:32px;font-variant-numeric:tabular-nums;font-weight:600}.analysis-metrics small{display:block;font-size:13px;color:var(--trip-muted);line-height:1.7}.analysis-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:24px;margin:24px 0}.analysis-trend{max-width:100%}.analysis-chart-toolbar{display:flex;justify-content:flex-end;margin-bottom:12px}.analysis-chart-toolbar .el-select{width:200px}.analysis-trend :deep(svg){max-height:340px}.analysis-table{margin-top:24px;overflow:hidden}.analysis-table h2{font-size:20px;margin-top:0}.analysis-table summary{cursor:pointer;font-weight:600;padding:8px 0 16px}.admin-note{line-height:1.7}.analysis-loading{padding:40px 16px;text-align:center;color:var(--trip-muted)}
@media(max-width:1000px){.analysis-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}.analysis-metrics>div:nth-child(2){border:0}.analysis-metrics>div:nth-child(-n+2){border-bottom:1px solid var(--trip-border)}}
@media(max-width:700px){.analysis-grid{grid-template-columns:minmax(0,1fr);gap:16px}.analysis-filters{align-items:stretch}.analysis-filters label,.analysis-filters .el-select{width:100%}.analysis-heading h1{font-size:26px}.analysis-metrics>div{padding:16px 8px}.analysis-metrics dd{font-size:28px}}
</style>
