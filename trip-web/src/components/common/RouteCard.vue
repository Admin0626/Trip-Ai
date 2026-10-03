<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { formatMoney } from '@/utils/format'
import CatalogCover from './CatalogCover.vue'

const props = defineProps<{ route: RoutePageVO }>()
const router = useRouter()

const destName = computed(() => props.route.destinationName ?? '')
const tags = computed(() => (props.route.tags ?? []).slice(0, 3))
</script>

<template>
  <article class="route-card card" role="link" tabindex="0" :aria-label="route.title" @click="router.push(`/route/${route.id}`)" @keydown.enter="router.push(`/route/${route.id}`)">
    <div class="route-card__cover">
      <CatalogCover :src="route.coverImg" :alt="route.title" compact />
    </div>
    <div class="route-card__body">
      <h3 class="route-card__title">{{ route.title }}</h3>
      <span v-if="route.isTop === 1" class="route-card__top">精选</span>
      <p v-if="route.subtitle" class="route-card__subtitle">{{ route.subtitle }}</p>
      <div class="route-card__meta">
        <span>{{ destName }}</span>
        <span>{{ route.days }} 天</span>
        <el-rate :model-value="Math.round(route.avgScore ?? 0)" disabled size="small" />
        <span class="text-secondary">{{ (route.avgScore ?? 0).toFixed(1) }}</span>
      </div>
      <div v-if="tags.length" class="route-card__tags">
        <el-tag v-for="t in tags" :key="t" size="small" type="info" effect="plain">{{ t }}</el-tag>
      </div>
      <div class="route-card__footer">
        <span class="money">{{ formatMoney(route.price) }}<small>/人</small></span>
        <span class="text-secondary">点赞 {{ route.likeCount }} · 收藏 {{ route.favoriteCount }}</span>
      </div>
    </div>
  </article>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.route-card {
  overflow: hidden;
  min-width: 0;
  box-shadow: none;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px rgba(31, 41, 55, 0.12);
  }

  &__cover {
    position: relative;
    overflow: hidden;
    background: $color-bg;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  &__top {
    display: inline-block;
    margin-bottom: 12px;
    background: #EDF4EF;
    color: $color-primary;
    font-size: 14px;
    padding: 2px 8px;
    border-radius: 4px;
  }

  &__body {
    padding: 24px;
  }

  &__title {
    margin: 0 0 12px;
    font-size: 20px;
    font-weight: 600;
    overflow-wrap: anywhere;
  }

  &__subtitle {
    margin: 0 0 8px;
    font-size: 14px;
    color: $color-text-secondary;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px;
    font-size: 14px;
    color: $color-text-secondary;
  }

  &__tags {
    margin-top: 8px;
    display: flex;
    gap: 4px;
    flex-wrap: wrap;
  }

  &__footer {
    margin-top: 10px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 14px;
    flex-wrap: wrap;
    gap: 12px;

    .money small {
      font-size: 14px;
      color: $color-text-secondary;
    }
  }
}
</style>
