<template>
  <div>
    <h2 class="page-title">直播预约管理</h2>
    <p class="page-subtitle">审核农户提交的直播预约；通过后锁定场地和设备资源。</p>

    <div class="page-card">
      <div class="page-toolbar">
        <el-radio-group v-model="status" @change="load">
          <el-radio-button value="pending_review">待审核</el-radio-button>
          <el-radio-button value="approved">已通过</el-radio-button>
          <el-radio-button value="rejected">已驳回</el-radio-button>
          <el-radio-button value="">全部</el-radio-button>
        </el-radio-group>
        <div class="toolbar-right">
          <el-input
            v-model="keyword"
            placeholder="搜索单号 / 主题 / 农户"
            clearable
            style="width: 220px"
            @keyup.enter="load"
          />
          <el-button type="primary" @click="load">查询</el-button>
        </div>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="bookingNo" label="预约单号" :width="compact ? 120 : 160" show-overflow-tooltip />
        <el-table-column prop="farmerName" label="农户" :width="compact ? 65 : 90" show-overflow-tooltip />
        <el-table-column prop="topic" label="直播主题" :min-width="compact ? 90 : 140" show-overflow-tooltip />
        <el-table-column prop="venueName" label="场地" :width="compact ? 80 : 120" show-overflow-tooltip />
        <el-table-column label="时段" :width="compact ? 120 : 170">
          <template #default="{ row }">{{ row.startTime }} 至 {{ row.endTime.slice(11) }}</template>
        </el-table-column>
        <el-table-column label="状态" :width="compact ? 60 : 80">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type">{{ statusMeta(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" :width="compact ? 120 : 180" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'pending_review'"
              link
              type="primary"
              @click="openReview(row)"
            >
              审核
            </el-button>
            <template v-else-if="row.status === 'approved' && ended(row)">
              <el-button link type="success" @click="complete(row, 'completed')">完成</el-button>
              <el-button link type="info" @click="complete(row, 'no_show')">未到场</el-button>
            </template>
            <el-button v-else link type="info" @click="openReview(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="changePage"
      />
    </div>

    <el-dialog v-model="dialogVisible" title="预约审核" width="560px">
      <template v-if="current">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="预约单号">{{ current.bookingNo }}</el-descriptions-item>
          <el-descriptions-item label="农户">{{ current.farmerName }}</el-descriptions-item>
          <el-descriptions-item label="场地">{{ current.venueName }}</el-descriptions-item>
          <el-descriptions-item label="设备">{{ current.deviceName }}（{{ current.deviceNo }}）</el-descriptions-item>
          <el-descriptions-item label="时段" :span="2">
            {{ current.startTime }} 至 {{ current.endTime }}
          </el-descriptions-item>
          <el-descriptions-item label="直播主题" :span="2">{{ current.topic }}</el-descriptions-item>
          <el-descriptions-item label="主推产品" :span="2">
            <span v-if="current.products.length === 0" class="muted">未填写</span>
            <el-tag
              v-for="product in current.products"
              :key="product.productName + product.spec"
              size="small"
              class="product-tag"
            >
              {{ product.productName }}{{ product.spec ? `（${product.spec}）` : '' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <template v-if="current.status !== 'pending_review'">
          <el-alert
            class="review-result"
            :title="`审核结果：${statusMeta(current.status).label}${current.reviewComment ? '，' + current.reviewComment : ''}`"
            :type="current.status === 'approved' ? 'success' : current.status === 'rejected' ? 'error' : 'info'"
            :closable="false"
          />
          <div v-if="current.recommendedTime" class="recommend">推荐时间：{{ current.recommendedTime }}</div>
        </template>

        <template v-else>
          <el-form :model="reviewForm" label-width="100px" class="review-form">
            <el-form-item label="审核意见">
              <el-input
                v-model="reviewForm.comment"
                type="textarea"
                :rows="2"
                placeholder="驳回时必填，至少 5 个字"
              />
            </el-form-item>
            <el-form-item label="推荐时间">
              <el-date-picker
                v-model="reviewForm.recommendedTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="驳回时必填，选择可预约时间"
              />
            </el-form-item>
          </el-form>
        </template>
      </template>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <template v-if="current?.status === 'pending_review'">
          <el-button type="danger" :loading="saving" @click="review('reject')">驳回</el-button>
          <el-button type="primary" :loading="saving" @click="review('approve')">通过</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  BOOKING_STATUS,
  completeBooking,
  listLiveBookings,
  reviewBooking,
  type Booking
} from '../api/bookings'

const rows = ref<Booking[]>([])
const status = ref('pending_review')
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const current = ref<Booking | null>(null)
const reviewForm = reactive({ comment: '', recommendedTime: '' })
const compact = ref(window.innerWidth < 1320)

function updateCompact() {
  compact.value = window.innerWidth < 1320
}

function statusMeta(value: string) {
  return BOOKING_STATUS[value] ?? { label: value, type: 'info' as const }
}

function ended(row: Booking): boolean {
  const end = new Date(row.endTime.replace(' ', 'T')).getTime()
  return Number.isNaN(end) || end <= Date.now()
}

async function complete(row: Booking, status: 'completed' | 'no_show') {
  const label = status === 'completed' ? '完成' : '未到场'
  try {
    await ElMessageBox.confirm(`确认将预约 ${row.bookingNo} 标记为“${label}”？`, '确认', { type: 'warning' })
    await completeBooking(row.id, status)
    ElMessage.success(`已标记为${label}`)
    await load()
  } catch (error) {
    if (error instanceof Error && error.message) ElMessage.error(error.message)
  }
}

async function load() {
  loading.value = true
  try {
    const result = await listLiveBookings({
      status: status.value,
      keyword: keyword.value,
      page: page.value,
      size: size.value
    })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

function openReview(row: Booking) {
  current.value = row
  reviewForm.comment = ''
  reviewForm.recommendedTime = row.recommendedTime ?? ''
  dialogVisible.value = true
}

async function review(result: 'approve' | 'reject') {
  if (!current.value) return
  saving.value = true
  try {
    await reviewBooking(current.value.id, {
      result,
      comment: reviewForm.comment.trim() || undefined,
      recommendedTime: reviewForm.recommendedTime || undefined
    })
    ElMessage.success(result === 'approve' ? '已通过，资源已锁定' : '已驳回')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

function changePage(next: number) {
  page.value = next
  load()
}

onMounted(() => {
  window.addEventListener('resize', updateCompact)
  load()
})

onUnmounted(() => {
  window.removeEventListener('resize', updateCompact)
})
</script>

<style scoped>
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.page-toolbar {
  justify-content: space-between;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.product-tag {
  margin: 0 6px 4px 0;
}

.review-form {
  margin-top: 16px;
}

.review-result {
  margin-top: 16px;
}

.recommend {
  font-size: 13px;
  color: #3b6cff;
  margin-top: 8px;
}

.muted {
  color: #a8b2c1;
}
</style>
