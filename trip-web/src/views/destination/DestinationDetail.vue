<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { destinationDetailApi, attractionsApi } from '@/api/modules/destination'
import { routePageApi } from '@/api/modules/route'
import RouteCard from '@/components/common/RouteCard.vue'
const route=useRoute(), destination=ref<DestinationVO|null>(null), attractions=ref<AttractionVO[]>([]), routes=ref<RoutePageVO[]>([])
const loading=ref(false), failed=ref(false)
let revision=0
async function load() {
  const version=++revision; loading.value=true; failed.value=false; destination.value=null
  try {
    const id=Number(route.params.id)
    if(!Number.isSafeInteger(id)||id<1) throw new Error('目的地不存在')
    const [data,items,related]=await Promise.all([destinationDetailApi(id),attractionsApi(id),routePageApi({destinationId:id,size:100})])
    if(version===revision){destination.value=data;attractions.value=items;routes.value=related.records}
  } catch { if(version===revision) failed.value=true }
  finally { if(version===revision) loading.value=false }
}
watch(()=>route.params.id,load,{immediate:true})
</script>
<template>
  <main class="destination-detail" v-loading="loading">
    <router-link to="/destinations">← 返回目的地列表</router-link>
    <el-empty v-if="failed" description="目的地不存在、已下架或暂时无法加载"><el-button @click="load">重新加载</el-button></el-empty>
    <template v-if="destination">
      <section class="intro card"><img v-if="destination.coverImg" :src="destination.coverImg" :alt="destination.name" /><div>
        <h1>{{ destination.name }}</h1><p>{{ destination.province }} · {{ destination.city }}</p>
        <el-tag v-for="tag in destination.tags" :key="tag">{{ tag }}</el-tag>
        <p class="description">{{ destination.intro || '暂无介绍' }}</p>
        <p>最佳出行季节：{{ destination.bestSeason || '暂无信息' }}</p><p>参考人均花费：¥{{ destination.avgCost ?? 0 }}</p>
      </div></section>
      <section><h2>当地景点</h2><el-empty v-if="!attractions.length" description="暂无景点" /><div class="attractions">
        <article v-for="item in attractions" :key="item.id" class="card attraction"><img v-if="item.coverImg" :src="item.coverImg" :alt="item.name" loading="lazy" />
          <div><h3>{{ item.name }}</h3><p>{{ item.intro }}</p><p>地址：{{ item.address || '暂无信息' }}</p><p>开放时间：{{ item.openTime || '请以现场公告为准' }}</p><p>参考门票：¥{{ item.ticketPrice ?? 0 }} · 建议游览 {{ item.durationMin ?? 0 }} 分钟</p></div>
        </article></div>
      </section>
      <section><h2>相关路线</h2><el-empty v-if="!routes.length" description="暂无在售路线" /><div class="routes"><RouteCard v-for="item in routes" :key="item.id" :route="item" /></div></section>
    </template>
  </main>
</template>
<style scoped>
.destination-detail{max-width:1200px;margin:auto;padding:24px 16px}.intro{display:grid;grid-template-columns:1fr 1fr;gap:24px;margin-top:20px;padding:24px}.intro>img{width:100%;height:300px;object-fit:cover;border-radius:10px}.description{white-space:pre-wrap;line-height:1.8}.el-tag{margin-right:8px}.attractions,.routes{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}.attraction{overflow:hidden}.attraction>img{width:100%;height:180px;object-fit:cover}.attraction>div{padding:16px}.attraction p{line-height:1.6;overflow-wrap:anywhere}section{margin-bottom:30px}@media(max-width:640px){.intro,.attractions,.routes{grid-template-columns:1fr}.intro{padding:16px}.intro>img{height:200px}}
</style>
