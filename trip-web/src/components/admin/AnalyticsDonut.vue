<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'

interface DonutDatum { id: string | number; label: string; value: number }
const props = defineProps<{ title: string; data: DonutDatum[]; unit: string; palette?: string[] }>()
const uid = useId(), canvas = ref<HTMLElement>(), width = ref(360)
const selected = ref<string | number | null>(null)
let observer: ResizeObserver | undefined
onMounted(() => {
  const fit = () => {
    if (canvas.value) width.value = Math.max(240, Math.min(360, Math.round(canvas.value.getBoundingClientRect().width)))
  }
  fit()
  observer = new ResizeObserver(fit)
  if (canvas.value) observer.observe(canvas.value)
})
onBeforeUnmount(() => observer?.disconnect())
watch(() => props.data, () => { selected.value = null })

const colors = computed(() => props.palette?.length ? props.palette : ['#477F9B', '#2F6650', '#C98B72', '#9A91BB'])
const total = computed(() => props.data.reduce((sum, d) => sum + d.value, 0))
const radius = computed(() => Math.min(82, (width.value - 140) / 2))
const circumference = computed(() => 2 * Math.PI * radius.value)
const center = computed(() => width.value / 2)
const current = computed(() => props.data.find(d => d.id === selected.value))
function share(d: DonutDatum) { return total.value > 0 ? `${(d.value / total.value * 100).toFixed(1)}%` : '—' }
function describe(d: DonutDatum) { return `${d.label}：${d.value}${props.unit}，${total.value > 0 ? '占比 ' + share(d) : '暂无记录，占比不适用'}` }
const sectors = computed(() => {
  let offset = 0
  return props.data.map((d, i) => {
    const fraction = total.value > 0 ? d.value / total.value : 0
    const angle = (offset + fraction / 2) * 2 * Math.PI - Math.PI / 2
    const sector = { ...d, color: colors.value[i % colors.value.length], fraction, offset, angle }
    offset += fraction
    return sector
  }).filter(d => d.value > 0)
})
// Separate labels on each side so small adjacent slices do not overlap.
const labels = computed(() => {
  const all = sectors.value.map(d => ({
    ...d, side: Math.cos(d.angle) >= 0 ? 1 : -1,
    startX: center.value + Math.cos(d.angle) * (radius.value + 18),
    startY: 130 + Math.sin(d.angle) * (radius.value + 18),
    labelY: 130 + Math.sin(d.angle) * (radius.value + 38),
  }))
  for (const side of [-1, 1]) {
    const group = all.filter(d => d.side === side).sort((a, b) => a.labelY - b.labelY)
    let last = 18
    for (const d of group) { d.labelY = Math.max(last + 24, Math.min(230, d.labelY)); last = d.labelY }
    const overflow = Math.max(0, last - 230)
    for (const d of group) d.labelY -= overflow
  }
  return all
})
</script>

<template>
  <figure class="analytics-chart analytics-donut" :aria-labelledby="uid">
    <figcaption :id="uid">{{ title }}</figcaption>
    <div class="donut-layout">
      <div ref="canvas" class="donut-canvas">
        <svg :viewBox="`0 0 ${width} 260`" role="group" :aria-label="title + '；环形图，合计 ' + total + unit">
          <circle :cx="center" cy="130" :r="radius" class="donut-track" fill="none" stroke-width="30" aria-hidden="true" />
          <circle v-for="d in sectors" :key="d.id" class="donut-sector" :class="{ selected: selected === d.id }"
            :cx="center" cy="130" :r="radius" fill="none" :stroke="d.color" stroke-width="30"
            :stroke-dasharray="`${d.fraction * circumference} ${circumference}`" :stroke-dashoffset="-d.offset * circumference"
            :transform="`rotate(-90 ${center} 130)`" tabindex="0" role="button" :aria-label="describe(d)"
            @focus="selected = d.id" @mouseenter="selected = d.id" @click="selected = d.id"
            @keydown.enter="selected = d.id" @keydown.space.prevent="selected = d.id">
            <title>{{ describe(d) }}</title>
          </circle>
          <g v-for="d in labels" :key="d.id" aria-hidden="true" class="donut-label">
            <polyline :points="`${d.startX},${d.startY} ${d.side > 0 ? width - 62 : 62},${d.labelY} ${d.side > 0 ? width - 54 : 54},${d.labelY}`" />
            <text :x="d.side > 0 ? width - 4 : 4" :y="d.labelY - 5" :text-anchor="d.side > 0 ? 'end' : 'start'">{{ share(d) }}</text>
          </g>
          <text :x="center" y="128" text-anchor="middle" class="donut-total">{{ total }}</text>
          <text :x="center" y="152" text-anchor="middle" class="donut-unit">合计（{{ unit }}）</text>
        </svg>
        <p v-if="total === 0" class="donut-empty" role="status">暂无记录，暂不计算占比</p>
      </div>
      <ul class="donut-legend" :aria-label="title + '数量与占比'">
        <li v-for="(d, i) in data" :key="d.id">
          <button type="button" :class="{ selected: selected === d.id }" :aria-label="describe(d)"
            @focus="selected = d.id" @mouseenter="selected = d.id" @click="selected = d.id">
            <span class="legend-swatch" :style="{ backgroundColor: colors[i % colors.length] }" aria-hidden="true" />
            <span class="legend-label">{{ d.label }}</span>
            <span class="legend-value">{{ d.value }}<span class="legend-unit">{{ unit }}</span></span>
            <span class="legend-share">{{ share(d) }}</span>
          </button>
        </li>
      </ul>
    </div>
    <p class="chart-detail" aria-live="polite">{{ current ? describe(current) : '悬停或用 Tab 聚焦扇区、图例可查看数量与占比。' }}</p>
  </figure>
</template>

<style scoped>
.analytics-donut{margin:0;min-width:0}.analytics-donut figcaption{font-size:20px;font-weight:600;margin:0 0 14px}
.donut-layout{display:grid;grid-template-columns:minmax(240px,1.15fr) minmax(160px,1fr);align-items:center;gap:16px}
.donut-canvas{min-width:0}.donut-canvas svg{display:block;width:100%;max-width:360px;height:auto;margin:auto;overflow:visible}
.donut-track{stroke:var(--trip-border)}.donut-sector{cursor:pointer;pointer-events:stroke;transition:opacity 160ms ease}
.donut-sector.selected,.donut-sector:focus-visible{opacity:.78;outline:none;stroke-width:36}
.donut-label polyline{fill:none;stroke:var(--trip-border);stroke-width:1}.donut-label text,.donut-unit{fill:var(--trip-muted);font-size:14px;font-family:inherit}
.donut-total{fill:var(--trip-ink);font-size:28px;font-weight:600;font-variant-numeric:tabular-nums;font-family:inherit}
.donut-legend{list-style:none;padding:0;margin:0;min-width:0}.donut-legend li+li{margin-top:6px}
.donut-legend button{display:grid;grid-template-columns:10px minmax(0,1fr) auto 52px;align-items:center;gap:8px;width:100%;padding:10px 6px;border:0;border-radius:6px;color:var(--trip-ink);background:transparent;font:inherit;font-size:14px;text-align:left;cursor:pointer}
.donut-legend button:hover,.donut-legend button.selected{background:var(--trip-tint)}.donut-legend button:focus-visible{outline:2px solid var(--trip-forest);outline-offset:2px}
.legend-swatch{width:10px;height:10px;border-radius:2px}.legend-label{overflow-wrap:anywhere}.legend-value,.legend-share{font-variant-numeric:tabular-nums;white-space:nowrap}.legend-unit{color:var(--trip-muted);margin-left:2px}.legend-share{text-align:right;color:var(--trip-muted)}
.chart-detail,.donut-empty{font-size:14px;line-height:1.65;color:var(--trip-muted);overflow-wrap:anywhere}.chart-detail{min-height:46px;margin:12px 0 0}.donut-empty{text-align:center;margin:0 0 12px}
@container (max-width:480px){.donut-layout{grid-template-columns:minmax(0,1fr)}}
.analytics-donut{container-type:inline-size}
@media(prefers-reduced-motion:reduce){.donut-sector{transition:none}}
</style>
