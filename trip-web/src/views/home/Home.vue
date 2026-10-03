<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { routeHotApi, routeRecommendHomeApi } from '@/api/modules/route'
import { destinationHotApi } from '@/api/modules/destination'
import RouteCard from '@/components/common/RouteCard.vue'
import { activeBanners, type Banner } from '@/api/modules/catalog'

const router = useRouter()
const hotRoutes = ref<RoutePageVO[]>([])
const recommendRoutes = ref<RoutePageVO[]>([])
const hotDests = ref<DestinationVO[]>([])
const banners = ref<Banner[]>([])
function bannerLink(b:Banner):string|null {
  if(b.linkType==='ROUTE' && /^[1-9]\d*$/.test(b.linkValue))return `/route/${b.linkValue}`
  if(b.linkType==='DESTINATION' && /^[1-9]\d*$/.test(b.linkValue))return `/destination/${b.linkValue}`
  if(b.linkType==='URL')try{const u=new URL(b.linkValue);if(['http:','https:'].includes(u.protocol)&&!u.username&&!u.password)return u.href}catch{}
  return null
}

onMounted(async () => {
  void activeBanners().then(data=>banners.value=data).catch(()=>{})
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
          <el-button size="large" round plain @click="router.push('/travel-assistant')">旅行助手 · 查路线或AI规划</el-button>
        </div>
      </div>
    </section>

    <section v-if="banners.length" class="container section home-banners" aria-label="首页轮播">
      <el-carousel height="280px" :interval="6000" :autoplay="banners.length>1" arrow="hover">
        <el-carousel-item v-for="b in banners" :key="b.id">
          <a v-if="bannerLink(b)" :href="bannerLink(b)!" :target="b.linkType==='URL'?'_blank':undefined" rel="noopener noreferrer" class="banner-slide"><img :src="b.imageUrl" :alt="b.title"/><span>{{b.title}}</span></a>
          <div v-else class="banner-slide"><img :src="b.imageUrl" :alt="b.title"/><span>{{b.title}}</span></div>
        </el-carousel-item>
      </el-carousel>
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
.hero__actions{flex-wrap:wrap}.banner-slide{position:relative;display:block;height:100%;color:white}.banner-slide img{width:100%;height:100%;object-fit:cover}.banner-slide span{position:absolute;bottom:0;left:0;right:0;padding:24px;background:linear-gradient(transparent,rgba(0,0,0,.7));font-size:22px}.home-banners :deep(.el-carousel){border-radius:12px}@media(max-width:600px){.hero{padding:36px 0}.hero__title{font-size:26px}.banner-slide span{font-size:18px;padding:18px}.home-banners :deep(.el-carousel__container){height:200px!important}}

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
