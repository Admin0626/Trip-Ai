<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { routePageApi } from '@/api/modules/route'
import RouteCard from '@/components/common/RouteCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import Pagination from '@/components/common/Pagination.vue'

const loading = ref(false)
const list = ref<RoutePageVO[]>([])
const total = ref(0)
const query = reactive<{ current: number; size: number; keyword: string; sortBy: string }>({
  current: 1,
  size: 12,
  keyword: '',
  sortBy: 'hot',
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await routePageApi({ ...query, sortBy: query.sortBy || undefined })
    list.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  query.current = 1
  void load()
}

function onPageChange(page: number): void {
  query.current = page
  void load()
}

onMounted(load)
</script>

<template>
  <div class="container page-wrapper">
    <div class="toolbar card">
      <el-input
        v-model="query.keyword"
        placeholder="搜索路线标题 / 关键词"
        clearable
        style="width: 320px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-radio-group v-model="query.sortBy" @change="onSearch">
        <el-radio-button value="hot">最热</el-radio-button>
        <el-radio-button value="newest">最新</el-radio-button>
        <el-radio-button value="score">高分</el-radio-button>
      </el-radio-group>
      <el-button type="primary" @click="onSearch">搜索</el-button>
    </div>

    <div v-loading="loading">
      <el-row v-if="list.length" :gutter="16">
        <el-col v-for="r in list" :key="r.id" :xs="12" :sm="8" :md="6">
          <RouteCard :route="r" />
        </el-col>
      </el-row>
      <EmptyState v-else-if="!loading" icon="🧭" text="没有找到符合条件的路线" />
    </div>

    <Pagination v-if="total > query.size" :total="total" :current="query.current" :size="query.size" @change="onPageChange" />
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  margin-bottom: 20px;
}
</style>