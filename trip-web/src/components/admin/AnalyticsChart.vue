<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, useId, watch } from 'vue'
interface ChartDatum { id:string|number; label:string; value:number; x?:number }
const props=defineProps<{kind:'bar'|'line'|'scatter'; title:string; data:ChartDatum[]; xLabel?:string; yLabel?:string}>()
const uid=useId(),selected=ref<string|number|null>(null)
const canvas=ref<HTMLElement>(),width=ref(640)
let observer:ResizeObserver|undefined
onMounted(()=>{
  const fit=()=>{if(canvas.value)width.value=Math.max(280,Math.min(640,Math.round(canvas.value.getBoundingClientRect().width)))}
  fit()
  if(typeof ResizeObserver!=='undefined'){observer=new ResizeObserver(fit);if(canvas.value)observer.observe(canvas.value)}
})
onBeforeUnmount(()=>observer?.disconnect())
const right=computed(()=>width.value-24),range=computed(()=>width.value-84)
const barStart=computed(()=>width.value<440?126:160),barRange=computed(()=>width.value-barStart.value-68)
const xTicks=computed(()=>width.value<440?[0,2,4]:[0,1,2,3,4])
watch(()=>props.data,()=>{selected.value=null})
const current=computed(()=>props.data.find(d=>d.id===selected.value))
const max=computed(()=>Math.max(4,Math.ceil(Math.max(0,...props.data.map(d=>d.value))/4)*4))
const xMax=computed(()=>Math.max(4,Math.ceil(Math.max(0,...props.data.map(d=>d.x??0))/4)*4))
const ticks=computed(()=>[0,1,2,3,4].map(i=>({value:max.value*i/4,y:266-i*52})))
function x(d:ChartDatum,i:number){return props.kind==='scatter'?60+(d.x??0)/xMax.value*range.value:60+i/Math.max(1,props.data.length-1)*range.value}
function y(d:ChartDatum){return 266-d.value/max.value*208}
const line=computed(()=>props.data.map((d,i)=>`${x(d,i)},${y(d)}`).join(' '))
const height=computed(()=>props.kind==='bar'?Math.max(140,props.data.length*38+54):328)
function describe(d:ChartDatum){return props.kind==='scatter'?`${d.label}：${props.xLabel??'参考价'} ${d.x}，${props.yLabel??'预约单数'} ${d.value}`:`${d.label}：${d.value}`}
function short(label:string){const limit=width.value<440?7:10;return label.length>limit?label.slice(0,limit)+'…':label}
</script>
<template>
  <figure ref="canvas" class="analytics-chart" :aria-labelledby="uid">
    <figcaption :id="uid">{{title}}</figcaption>
    <p v-if="!data.length" class="chart-empty" role="status">当前范围暂无数据</p>
    <template v-else>
      <svg :viewBox="`0 0 ${width} 328`" v-if="kind!=='bar'" role="group" :aria-label="title+'；可聚焦数据点查看详情'">
        <g v-for="tick in ticks" :key="tick.value" aria-hidden="true"><line x1="60" :x2="right" :y1="tick.y" :y2="tick.y" class="grid-line"/><text x="49" :y="tick.y+5" text-anchor="end">{{tick.value}}</text></g>
        <text x="60" y="25">{{yLabel||'数量'}}</text>
        <polyline v-if="kind==='line'" :points="line" class="trend-line"/>
        <g v-for="(d,i) in data" :key="d.id"><circle :cx="x(d,i)" :cy="y(d)" r="10" fill="transparent" tabindex="0" role="button" :aria-label="describe(d)" @focus="selected=d.id" @mouseenter="selected=d.id" @click="selected=d.id" @keydown.enter="selected=d.id" @keydown.space.prevent="selected=d.id"><title>{{describe(d)}}</title></circle><circle :cx="x(d,i)" :cy="y(d)" :r="kind==='scatter'?5:3" class="data-dot" :class="{selected:selected===d.id}" aria-hidden="true" pointer-events="none"/></g>
        <template v-if="kind==='scatter'"><text v-for="i in xTicks" :key="i" :x="60+i*range/4" y="289" :text-anchor="i===4?'end':i===0?'start':'middle'">{{xMax*i/4}}</text><text :x="width/2" y="320" text-anchor="middle">{{xLabel||'参考价（元）'}}</text></template>
        <template v-else><text x="60" y="289">{{data[0]?.label}}</text><text :x="right" y="289" text-anchor="end">{{data[data.length-1]?.label}}</text><text :x="width/2" y="320" text-anchor="middle">日期</text></template>
      </svg>
      <svg v-else :viewBox="`0 0 ${width} ${height}`" role="group" :aria-label="title+'；可聚焦条形查看详情'">
        <g v-for="(d,i) in data" :key="d.id"><text :x="barStart-13" :y="i*38+26" text-anchor="end"><title>{{d.label}}</title>{{short(d.label)}}</text><rect :x="barStart" :y="i*38+8" :width="d.value/max*barRange" height="26" rx="3" class="data-bar" tabindex="0" role="button" :aria-label="describe(d)" @focus="selected=d.id" @mouseenter="selected=d.id" @click="selected=d.id" @keydown.enter="selected=d.id" @keydown.space.prevent="selected=d.id"><title>{{describe(d)}}</title></rect><text :x="barStart+12+d.value/max*barRange" :y="i*38+26">{{d.value}}</text></g>
        <text :x="barStart" :y="height-12">0</text><text :x="width-16" :y="height-12" text-anchor="end">{{max}} · {{xLabel||'预约单数'}}</text>
      </svg>
      <p class="chart-detail" aria-live="polite">{{current?describe(current):'鼠标或 Tab 聚焦可查看数值；路线和每日明细见下方表格。'}}</p>
    </template>
  </figure>
</template>
<style scoped>
.analytics-chart{margin:0;min-width:0}.analytics-chart figcaption{font-size:20px;font-weight:600;margin:0 0 14px}.analytics-chart svg{width:100%;height:auto;display:block;overflow:visible}.analytics-chart text{fill:var(--trip-muted);font-size:14px;font-family:inherit}.grid-line{stroke:var(--trip-border);stroke-width:1}.trend-line{fill:none;stroke:var(--trip-forest);stroke-width:2}.data-dot,.data-bar{fill:var(--trip-forest)}.data-dot.selected{stroke:var(--trip-ink);stroke-width:2}.data-bar:focus-visible{stroke:var(--trip-ink);stroke-width:2}.chart-detail,.chart-empty{font-size:14px;line-height:1.65;color:var(--trip-muted);overflow-wrap:anywhere}.chart-detail{min-height:46px;margin:12px 0 0}.chart-empty{padding:50px 16px;text-align:center;background:var(--trip-canvas);border-radius:8px}
</style>
