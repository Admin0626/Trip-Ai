<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { routeHotApi, routeRecommendHomeApi } from '@/api/modules/route'
import { destinationHotApi } from '@/api/modules/destination'
import { activeBanners, type Banner } from '@/api/modules/catalog'
import RouteCard from '@/components/common/RouteCard.vue'
import CatalogCover from '@/components/common/CatalogCover.vue'
import EmptyState from '@/components/common/EmptyState.vue'
const router = useRouter()
const hotRoutes = ref<RoutePageVO[]>([]), recommendRoutes = ref<RoutePageVO[]>([]), hotDests = ref<DestinationVO[]>([])
const banners = ref<Banner[]>([]), loading = ref(true), loadError = ref(false), autoplay = ref(false)
function bannerLink(b: Banner): string | null {
  if (b.linkType === 'ROUTE' && /^[1-9]\d*$/.test(b.linkValue)) return `/route/${b.linkValue}`
  if (b.linkType === 'DESTINATION' && /^[1-9]\d*$/.test(b.linkValue)) return `/destination/${b.linkValue}`
  if (b.linkType === 'URL') try {
    const u = new URL(b.linkValue)
    if (['http:', 'https:'].includes(u.protocol) && !u.username && !u.password) return u.href
  } catch { /* 无效链接不提供跳转 */ }
  return null
}
async function load() {
  loading.value = true; loadError.value = false
  const results = await Promise.allSettled([routeHotApi(5), routeRecommendHomeApi(), destinationHotApi(8), activeBanners()])
  const [hot, recommended, destinations, banner] = results
  if (hot.status === 'fulfilled') hotRoutes.value = hot.value
  if (recommended.status === 'fulfilled') recommendRoutes.value = recommended.value
  if (destinations.status === 'fulfilled') hotDests.value = destinations.value
  if (banner.status === 'fulfilled') banners.value = banner.value
  loadError.value = results.some(result => result.status === 'rejected'); loading.value = false
}
onMounted(() => { void load() })
</script>

<template>
  <main class="home-page page-wrapper">
    <section class="hero" aria-labelledby="home-title">
      <div class="hero__copy">
        <p class="eyebrow">为下一次出发，留一点期待</p>
        <h1 id="home-title">下一段旅程，<br />从这里开始</h1>
        <p class="hero__subtitle">发现目的地，挑选路线，<br />安排适合自己的行程。</p>
        <el-button type="primary" @click="router.push('/travel-assistant')">开始规划</el-button>
        <p class="hero__note">自然风光 · 城市漫步 · 轻松规划</p>
      </div>
      <div class="hero__landscape"><img src="/figma-landscape.svg" width="640" height="480" alt="" fetchpriority="high" /></div>
    </section>
    <div v-if="loadError" class="load-error" role="status">部分内容暂时无法加载。<el-button text type="primary" @click="load">重新加载</el-button></div>
    <section class="section" aria-labelledby="destinations-title">
      <div class="section__heading"><h2 id="destinations-title">热门目的地</h2><router-link to="/destinations">全部目的地</router-link></div>
      <el-skeleton v-if="loading" :rows="3" animated />
      <div v-else-if="hotDests.length" class="content-grid">
        <router-link v-for="d in hotDests" :key="d.id" :to="`/destination/${d.id}`" class="dest-card card">
          <CatalogCover :src="d.coverImg" :alt="d.name" compact />
          <div class="dest-card__body"><h3>{{ d.name }}</h3><p>{{ d.province }} · {{ d.city }}</p><span>查看目的地</span></div>
        </router-link>
      </div>
      <EmptyState v-else text="暂时没有热门目的地" />
    </section>
    <section class="section" aria-labelledby="recommended-title">
      <div class="section__heading"><h2 id="recommended-title">精选路线</h2><router-link to="/routes">全部路线</router-link></div>
      <el-skeleton v-if="loading" :rows="3" animated />
      <div v-else-if="recommendRoutes.length" class="content-grid"><RouteCard v-for="r in recommendRoutes" :key="r.id" :route="r" /></div>
      <EmptyState v-else text="暂时没有精选路线" />
    </section>
    <section class="section" aria-labelledby="hot-title">
      <div class="section__heading"><h2 id="hot-title">热门路线</h2><span class="text-secondary">当前热门 Top 5</span></div>
      <el-skeleton v-if="loading" :rows="3" animated />
      <div v-else-if="hotRoutes.length" class="content-grid"><RouteCard v-for="r in hotRoutes" :key="r.id" :route="r" /></div>
      <EmptyState v-else text="暂时没有热门路线" />
    </section>
    <section v-if="banners.length" class="section home-banners" aria-label="旅行精选轮播">
      <div class="section__heading"><h2>旅行精选</h2><el-button v-if="banners.length > 1" :aria-pressed="autoplay" @click="autoplay = !autoplay">{{ autoplay ? '暂停轮播' : '自动播放' }}</el-button></div>
      <el-carousel height="320px" :interval="6000" :autoplay="autoplay && banners.length > 1" arrow="always">
        <el-carousel-item v-for="b in banners" :key="b.id">
          <component :is="bannerLink(b) ? 'a' : 'div'" :href="bannerLink(b) || undefined" :target="b.linkType === 'URL' ? '_blank' : undefined" rel="noopener noreferrer" class="banner-slide">
            <CatalogCover :src="b.imageUrl" :alt="b.title" /><span>{{ b.title }}</span>
          </component>
        </el-carousel-item>
      </el-carousel>
    </section>
  </main>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;
.hero { display: grid; grid-template-columns: .9fr 1fr; gap: 64px; align-items: center; }
.hero__copy { display: grid; justify-items: start; gap: 24px; min-width: 0; }
.hero p, .hero h1 { margin: 0; }
.eyebrow { color: $color-primary; font-size: 14px; }
.hero h1 { font-size: clamp(34px, 3.34vw, 48px); font-weight: 600; line-height: 1.5; }
.hero__subtitle { color: $color-text-secondary; line-height: 1.5; }
.hero__note { color: $color-text-secondary; font-size: 14px; }
.hero__landscape { min-width: 0; aspect-ratio: 4 / 3; overflow: hidden; border-radius: 24px; background: #EDF4EF; }
.hero__landscape img { display: block; width: 100%; height: auto; }
.section { margin-top: 48px; }
.section__heading { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px; margin-bottom: 24px; }
.section__heading h2 { margin: 0; font-size: 28px; font-weight: 600; }
.section__heading a, .section__heading > span { font-size: 14px; }
.content-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 24px; }
.dest-card { display: block; min-width: 0; color: $color-text; overflow: hidden; box-shadow: none; transition: transform .2s; }
.dest-card:hover { transform: translateY(-2px); }
.dest-card__body { padding: 24px; display: grid; gap: 16px; }
.dest-card h3 { margin: 0; font-size: 24px; font-weight: 600; }
.dest-card p { margin: 0; font-size: 14px; color: $color-text-secondary; }
.dest-card span { font-size: 14px; color: $color-primary; }
.banner-slide { display: grid; grid-template-rows: 1fr auto; height: 100%; background: white; border-radius: 20px; overflow: hidden; border: 1px solid $color-border; }
.banner-slide span { padding: 16px 24px; font-size: 20px; color: $color-text; }
.banner-slide :deep(.catalog-cover) { height: 100%; min-height: 0; }
.load-error { margin-top: 24px; color: $color-text-secondary; }
@media (max-width: 767px) { .hero, .content-grid { grid-template-columns: 1fr; }.hero { gap: 32px; }.section { margin-top: 32px; }.section__heading h2 { font-size: 24px; } }
</style>
