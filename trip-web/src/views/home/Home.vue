<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { routeHotApi, routeRecommendHomeApi } from '@/api/modules/route'
import { destinationHotApi } from '@/api/modules/destination'
import RouteCard from '@/components/common/RouteCard.vue'

const router = useRouter()
const hotRoutes = ref<RoutePageVO[]>([])
const recommendRoutes = ref<RoutePageVO[]>([])
const hotDests = ref<DestinationVO[]>([])

onMounted(async () => {
  try {
    const [r1, r2, d] = await Promise.all([routeHotApi(5), routeRecommendHomeApi(), destinationHotApi(8)])
    hotRoutes.value = r1
    recommendRoutes.value = r2
    hotDests.value = d
  } catch {
    /* 拦截器已提示 */
  }
})
</script>

<template>
  <div>
    <!-- 轮播英雄区 -->
    <section class="hero">
      <div class="hero__inner container">
        <h1 class="hero__title">用 AI 规划你的下一段旅程</h1>
        <p class="hero__subtitle">智能推荐 · 行程规划 · 一键生成</p>
        <div class="hero__actions">
          <el-button type="primary" size="large" round @click="router.push('/routes')">浏览路线</el-button>
          <el-button size="large" round plain @click="router.push('/plan/create')">开始规划</el-button>
          <el-button size="large" round plain @click="router.push('/recommend')">按需求找路线</el-button>
          <el-button size="large" round plain @click="router.push('/ai-planner')">AI帮我规划</el-button>
        </div>
      </div>
    </section>

    <!-- 热门目的地 -->
    <section class="container section">
      <h2 class="section__title">热门目的地</h2>
      <el-row :gutter="16">
        <el-col v-for="d in hotDests" :key="d.id" :xs="12" :sm="8" :md="6">
          <div class="dest-card card" role="link" tabindex="0" @click="router.push(`/destination/${d.id}`)" @keydown.enter="router.push(`/destination/${d.id}`)">
            <img :src="d.coverImg" :alt="d.name" loading="lazy" />
            <div class="dest-card__name">{{ d.name }}</div>
            <div class="dest-card__meta">{{ d.province }} {{ d.city }}</div>
          </div>
        </el-col>
      </el-row>
    </section>

    <!-- 精选路线 -->
    <section class="container section">
      <h2 class="section__title">精选路线</h2>
      <el-row :gutter="16">
        <el-col v-for="r in recommendRoutes" :key="r.id" :xs="12" :sm="8" :md="6">
          <RouteCard :route="r" />
        </el-col>
      </el-row>
    </section>

    <!-- 热门路线 -->
    <section class="container section">
      <h2 class="section__title">热门路线 Top 5</h2>
      <el-row :gutter="16">
        <el-col v-for="r in hotRoutes" :key="r.id" :xs="12" :sm="8" :md="6">
          <RouteCard :route="r" />
        </el-col>
      </el-row>
    </section>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.hero {
  background: linear-gradient(135deg, #2f7bff 0%, #6aa5ff 100%);
  color: #fff;
  padding: 64px 0;

  &__title {
    margin: 0 0 12px;
    font-size: 32px;
  }

  &__subtitle {
    margin: 0 0 24px;
    opacity: 0.9;
  }

  &__actions {
    display: flex;
    gap: 12px;
  }
}

.section {
  padding-top: 32px;

  &__title {
    font-size: 20px;
    margin: 0 0 16px;
  }
}

.dest-card {
  margin-bottom: 16px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s;

  &:hover {
    transform: translateY(-4px);
  }

  img {
    width: 100%;
    height: 110px;
    object-fit: cover;
    display: block;
  }

  &__name {
    padding: 10px 12px 2px;
    font-weight: 600;
  }

  &__meta {
    padding: 0 12px 10px;
    font-size: 12px;
    color: $color-text-secondary;
  }
}
</style>
