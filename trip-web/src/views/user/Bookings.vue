<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { myBookingsApi, cancelBookingApi } from '@/api/modules/interaction'
import { bookingStatusText, bookingStatusType } from '@/utils/format'
import EmptyState from '@/components/common/EmptyState.vue'
import Pagination from '@/components/common/Pagination.vue'

const router = useRouter()
const list = ref<BookingVO[]>([])
const total = ref(0)
const current = ref(1)
const size = 8
const loading = ref(false)

const cancellable = computed(() => list.value.filter((b) => b.status === 0 || b.status === 1))

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await myBookingsApi({ current: current.value, size })
    list.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function cancel(b: BookingVO): void {
  ElMessageBox.confirm(`确定取消「${b.routeTitle}」的预约吗？`, '取消预约', { type: 'warning' })
    .then(async () => {
      await cancelBookingApi(b.bookingId)
      ElMessage.success('预约已取消')
      void load()
    })
    .catch(() => {})
}

function goRoute(routeId: number): void {
  void router.push(`/route/${routeId}`)
}

function onPageChange(page: number): void {
  current.value = page
  void load()
}

onMounted(load)
</script>

<template>
  <div class="container page-wrapper">
    <h1 class="page-head">
      我的预约
      <span class="page-head__hint text-secondary">（可取消 {{ cancellable.length }} 条）</span>
    </h1>

    <div v-loading="loading">
      <template v-if="list.length">
        <div v-for="b in list" :key="b.bookingId" class="booking-card card">
          <div class="booking-card__main" @click="goRoute(b.routeId)">
            <div class="booking-card__title">{{ b.routeTitle }}</div>
            <div class="booking-card__meta">
              <span>📅 {{ b.travelDate }}</span>
              <span>🧑‍🤝‍🧑 {{ b.peopleNum }} 人</span>
              <span>📞 {{ b.contactName || '--' }}</span>
              <span class="text-secondary">{{ b.bookingNo }}</span>
            </div>
            <div v-if="b.remark" class="booking-card__remark text-secondary">备注：{{ b.remark }}</div>
          </div>
          <div class="booking-card__side">
            <el-tag :type="bookingStatusType(b.status)">{{ bookingStatusText(b.status) }}</el-tag>
            <el-button v-if="b.status === 0 || b.status === 1" size="small" plain @click="cancel(b)">取消预约</el-button>
          </div>
        </div>
      </template>
      <EmptyState v-else-if="!loading" icon="🎫" text="还没有预约，去路线页看看吧" />
    </div>

    <Pagination v-if="total > size" :total="total" :current="current" :size="size" @change="onPageChange" />
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.page-head {
  margin: 0 0 20px;
  font-size: 24px;

  &__hint {
    font-size: 13px;
    font-weight: 400;
  }
}

.booking-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  margin-bottom: 12px;
  cursor: pointer;

  &__main {
    flex: 1;
    min-width: 0;
  }

  &__title {
    font-weight: 600;
    margin-bottom: 8px;
  }

  &__meta {
    display: flex;
    gap: 16px;
    font-size: 13px;
    color: $color-text-secondary;
    flex-wrap: wrap;
  }

  &__remark {
    margin-top: 6px;
    font-size: 12px;
  }

  &__side {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 8px;
    flex-shrink: 0;
  }
}
</style>