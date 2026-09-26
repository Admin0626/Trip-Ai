<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { toggleFavoriteApi } from '@/api/modules/interaction'
import request from '@/api/request'
import RouteCard from '@/components/common/RouteCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import Pagination from '@/components/common/Pagination.vue'

const list = ref<RoutePageVO[]>([])
const total = ref(0)
const current = ref(1)
const size = 8
const loading = ref(false)

async function load(): Promise<void> {
  loading.value = true
  try {
    const resp = await request.get<ApiResponse<PageResult<RoutePageVO>>>('/interaction/favorite/page', {
      params: { current: current.value, size },
    })
    const page = resp.data.data
    list.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function unFavorite(routeId: number): Promise<void> {
  await toggleFavoriteApi(routeId)
  ElMessage.success('已取消收藏')
  void load()
}

function onPageChange(page: number): void {
  current.value = page
  void load()
}

onMounted(load)
</script>

<template>
  <div class="container page-wrapper">
    <h1 class="page-head">我的收藏</h1>

    <div v-loading="loading">
      <el-row v-if="list.length" :gutter="16">
        <el-col v-for="r in list" :key="r.id" :xs="12" :sm="8" :md="6">
          <div class="fav-item">
            <RouteCard :route="r" />
            <el-button size="small" class="fav-item__un" @click="unFavorite(r.id)">取消收藏</el-button>
          </div>
        </el-col>
      </el-row>
      <EmptyState v-else-if="!loading" icon="⭐" text="还没有收藏，去路线页逛逛吧" />
    </div>

    <Pagination v-if="total > size" :total="total" :current="current" :size="size" @change="onPageChange" />
  </div>
</template>

<style scoped lang="scss">
.page-head {
  margin: 0 0 20px;
  font-size: 24px;
}

.fav-item {
  position: relative;
  margin-bottom: 16px;

  &__un {
    position: absolute;
    top: 8px;
    right: 8px;
    z-index: 2;
    opacity: 0;
    transition: opacity 0.2s;
  }

  &:hover &__un {
    opacity: 1;
  }
}
</style>