<script setup lang="ts">
import { ref, watch } from 'vue'
const props = defineProps<{ src?: string; alt: string; compact?: boolean; landscape?: boolean }>()
const failed = ref(false)
const loaded = ref(false)
watch(() => props.src, () => { failed.value = false; loaded.value = false })
</script>

<template>
  <div v-if="(src && !failed) || !compact" class="catalog-cover" :class="{ 'catalog-cover--landscape': landscape }">
    <img v-if="src && !failed" :src="src" :alt="alt" loading="lazy" :class="{ 'catalog-cover__pending': !loaded }" @load="loaded = true" @error="failed = true" />
    <template v-if="!loaded || failed">
      <img v-if="landscape" src="/figma-landscape.svg" width="640" height="480" alt="" class="catalog-cover__illustration" />
      <span class="catalog-cover__unavailable">{{ alt }} · {{ src && !failed ? '封面加载中' : '图片暂不可用' }}</span>
    </template>
  </div>
</template>

<style scoped>
.catalog-cover { display: grid; place-items: center; position: relative; background: var(--trip-tint); aspect-ratio: 3 / 2; overflow: hidden; min-width: 0; color: var(--trip-muted); }
.catalog-cover img { display: block; width: 100%; height: 100%; object-fit: cover; }
.catalog-cover__unavailable { padding: 16px; text-align: center; font-size: 14px; }
.catalog-cover img.catalog-cover__pending { position: absolute; inset: 0; opacity: 0; }
.catalog-cover--landscape { aspect-ratio: 8 / 5; align-content: start; position: relative; border-radius: 24px; }
.catalog-cover img.catalog-cover__illustration { height: auto; object-fit: contain; }
.catalog-cover--landscape .catalog-cover__unavailable { position: absolute; bottom: 0; left: 0; right: 0; background: var(--trip-tint); padding: 8px; }
</style>
