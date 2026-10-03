<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import type { DiagramEdge, DiagramNode } from './content'
const props = defineProps<{ nodes: DiagramNode[]; edges: DiagramEdge[]; selected: string; label: string }>()
const emit = defineEmits<{ select: [id: string] }>()
const canvas = ref<HTMLElement>()
const marker = 'arrow' + useId().replace(/[^a-z0-9]/gi, '')
const paths = ref<{ d: string; x: number; y: number; label: string; dashed: boolean; active: boolean }[]>([])
const height = ref(400)
const width = ref(900)
const columns = computed(() => Math.max(...props.nodes.map(n => n.col)) + 1)
let observer: ResizeObserver | undefined
let frame = 0
function measure() {
  const root = canvas.value
  if (!root) return
  const bounds = root.getBoundingClientRect()
  width.value = bounds.width; height.value = bounds.height
  paths.value = props.edges.flatMap(edge => {
    const a = root.querySelector<HTMLElement>(`[data-node="${edge.from}"]`)
    const b = root.querySelector<HTMLElement>(`[data-node="${edge.to}"]`)
    if (!a || !b) return []
    const ar = a.getBoundingClientRect(), br = b.getBoundingClientRect()
    const horizontal = Math.abs(ar.top - br.top) < 10
    let ax: number, ay: number, bx: number, by: number, d: string, lx: number | undefined, ly: number | undefined
    if (horizontal) {
      const right = br.left > ar.left
      ax = (right ? ar.right : ar.left) - bounds.left
      bx = (right ? br.left : br.right) - bounds.left
      ay = ar.top + ar.height / 2 - bounds.top; by = br.top + br.height / 2 - bounds.top
      d = `M ${ax} ${ay} L ${bx} ${by}`
    } else {
      const down = br.top > ar.top
      ax = ar.left + ar.width / 2 - bounds.left; bx = br.left + br.width / 2 - bounds.left
      ay = (down ? ar.bottom : ar.top) - bounds.top; by = (down ? br.top : br.bottom) - bounds.top
      const mid = (ay + by) / 2
      const from = props.nodes.find(n => n.id === edge.from)!, to = props.nodes.find(n => n.id === edge.to)!
      if (from.col === to.col && Math.abs(from.row - to.row) > 1) {
        const lane = ar.left - bounds.left - 16
        d = `M ${ax} ${ay} L ${ax} ${ay + (down ? 20 : -20)} L ${lane} ${ay + (down ? 20 : -20)} L ${lane} ${by - (down ? 20 : -20)} L ${bx} ${by - (down ? 20 : -20)} L ${bx} ${by}`
        lx = (ax + lane) / 2; ly = ay + (down ? 20 : -20)
      } else {
        d = `M ${ax} ${ay} L ${ax} ${mid} L ${bx} ${mid} L ${bx} ${by}`
      }
    }
    return [{ d, x: lx ?? (ax + bx) / 2, y: ly ?? (ay + by) / 2, label: edge.label, dashed: !!edge.dashed, active: edge.from === props.selected || edge.to === props.selected }]
  })
}
function schedule() { cancelAnimationFrame(frame); frame = requestAnimationFrame(measure) }
onMounted(() => { observer = new ResizeObserver(schedule); if (canvas.value) observer.observe(canvas.value); schedule() })
watch(() => [props.nodes, props.edges, props.selected], async () => { await nextTick(); schedule() })
onBeforeUnmount(() => { observer?.disconnect(); cancelAnimationFrame(frame) })
</script>
<template>
  <div ref="canvas" class="diagram" :style="{ '--columns': columns }" role="group" :aria-label="label">
    <svg class="connections" :viewBox="`0 0 ${width} ${height}`" aria-hidden="true">
      <defs><marker :id="marker" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="currentColor" /></marker></defs>
      <g v-for="(p, index) in paths" :key="index" :class="{ active: p.active }">
        <path :d="p.d" fill="none" :stroke-dasharray="p.dashed ? '4 4' : undefined" :marker-end="`url(#${marker})`" />
        <rect :x="p.x - p.label.length * 6 - 6" :y="p.y - 10" :width="p.label.length * 12 + 12" height="20" rx="4" />
        <text :x="p.x" :y="p.y + 4" text-anchor="middle">{{ p.label }}</text>
      </g>
    </svg>
    <button v-for="node in nodes" :key="node.id" type="button" class="diagram-node" :class="{ selected: selected === node.id }" :data-node="node.id" :style="{ gridColumn: node.col + 1, gridRow: node.row + 1 }" :aria-pressed="selected === node.id" @click="emit('select', node.id)">
      <span class="node-tag">{{ node.tag }}</span><strong>{{ node.title }}</strong><span class="node-subtitle">{{ node.subtitle }}</span>
    </button>
  </div>
  <ul class="relation-list"><li v-for="edge in edges" :key="edge.from + edge.to">{{ nodes.find(n => n.id === edge.from)?.title }} → {{ edge.label }} → {{ nodes.find(n => n.id === edge.to)?.title }}</li></ul>
</template>
<style scoped>
.diagram { position: relative; display: grid; grid-template-columns: repeat(var(--columns), minmax(0, 1fr)); gap: 64px 48px; padding: 28px 12px; isolation: isolate; }
.connections { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; z-index: -1; color: #a1aaa5; }
.connections g > path { stroke: currentColor; stroke-width: 1.5; }
.connections .active { color: #2f6650; }
.connections rect { fill: #f7f9f7; }
.connections text { fill: #59645e; font-size: 11px; }
.diagram-node { text-align: left; border: 1px solid #dce4de; border-radius: 12px; padding: 16px 12px; background: #fff; font: inherit; min-width: 0; cursor: pointer; transition: border-color .18s, box-shadow .18s; }
.diagram-node:hover { border-color: #709580; }
.diagram-node.selected { border-color: #2f6650; box-shadow: 0 0 0 2px #2f665018; background: #edf4ef; }
.diagram-node strong { display: block; font-size: 14px; color: #27382d; overflow-wrap: anywhere; margin: 8px 0 5px; }
.node-tag { color: #526859; font-size: 11px; letter-spacing: .02em; }
.node-subtitle { display: block; color: #657168; font-size: 11px; overflow-wrap: anywhere; }
.diagram-node:focus-visible { outline: 3px solid #2f6650; outline-offset: 4px; }
.relation-list { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
@media (max-width: 760px) { .diagram { gap: 52px 28px; padding: 24px 6px; } .diagram-node { padding: 12px 8px; } }
@media (max-width: 580px) { .diagram { grid-template-columns: 1fr; gap: 12px; padding: 20px 10px; } .diagram-node { grid-column: 1 !important; grid-row: auto !important; padding: 14px 18px; } .diagram-node strong { font-size: 15px; } .connections { display: none; } .relation-list { position: static; width: auto; height: auto; overflow: visible; clip-path: none; font-size: 11px; color: #536156; padding: 0 14px 0 30px; } .relation-list li { padding: 5px 0; } }
@media (prefers-reduced-motion: reduce) { .diagram-node { transition: none; } }
</style>
