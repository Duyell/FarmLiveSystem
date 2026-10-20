<template>
  <div>
    <h2 class="page-title">我的预约</h2>
    <p class="page-subtitle">查看和管理您的直播预约状态；开播前 24 小时以上可取消。</p>

    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label">待审核</div>
        <div class="stat-value warning">{{ counts.pending_review }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已通过</div>
        <div class="stat-value success">{{ counts.approved }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已驳回</div>
        <div class="stat-value danger">{{ counts.rejected }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">全部</div>
        <div class="stat-value">{{ counts.all }}</div>
      </div>
    </div>

    <div class="page-card">
      <div class="page-toolbar">
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 160px" @change="load">
          <el-option label="待审核" value="pending_review" />
          <el-option label="已通过" value="approved" />
          <el-option label="已驳回" value="rejected" />
          <el-option label="已取消" value="canceled" />
          <el-option label="已完成" value="completed" />
        </el-select>
        <el-button type="primary" @click="openCreate">提交新预约</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="bookingNo" label="预约单号" width="180" />
        <el-table-column prop="topic" label="直播主题" min-width="180" />
        <el-table-column label="时段" width="200">
          <template #default="{ row }">{{ row.startTime }} 至 {{ row.endTime.slice(11) }}</template>
        </el-table-column>
        <el-table-column prop="venueName" label="场地" width="140" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type">{{ statusMeta(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核意见" min-width="160">
          <template #default="{ row }">
            <span v-if="row.reviewComment">{{ row.reviewComment }}</span>
            <span v-else-if="row.cancelReason">{{ row.cancelReason }}</span>
            <span v-else class="muted">-</span>
            <div v-if="row.recommendedTime" class="recommend">推荐：{{ row.recommendedTime }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'approved' && hoursLeft(row) >= 24"
              link
              type="primary"
              @click="openAdjust(row)"
            >
              申请调整
            </el-button>
            <el-button
              v-if="row.status === 'pending_review' || row.status === 'approved'"
              link
              type="danger"
              @click="remove(row)"
            >
              取消
            </el-button>
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

    <el-dialog v-model="adjustVisible" title="申请调整预约" width="520px">
      <el-form label-position="top">
        <el-form-item label="新日期">
          <el-date-picker
            v-model="adjustForm.date"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            @change="loadAdjustResources"
          />
        </el-form-item>
        <el-form-item label="新时段">
          <div class="time-row">
            <el-time-select
              v-model="adjustForm.startTime"
              start="08:00"
              end="20:00"
              step="01:00"
              placeholder="开始"
              @change="loadAdjustResources"
            />
            <span>至</span>
            <el-time-select
              v-model="adjustForm.endTime"
              start="09:00"
              end="22:00"
              step="01:00"
              placeholder="结束"
              @change="loadAdjustResources"
            />
          </div>
        </el-form-item>
        <el-form-item label="新场地">
          <el-select v-model="adjustForm.venueId" placeholder="选择可用场地" style="width: 100%" @change="adjustForm.deviceId = 0">
            <el-option
              v-for="item in availableVenues"
              :key="item.venueId"
              :label="`${item.venueName}（${item.venueNo}）`"
              :value="item.venueId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="新设备">
          <el-select v-model="adjustForm.deviceId" placeholder="选择场地内设备" style="width: 100%">
            <el-option
              v-for="item in availableDevices"
              :key="item.deviceId"
              :label="`${item.name}（${item.deviceNo}）`"
              :value="item.deviceId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="调整原因">
          <el-input v-model="adjustForm.reason" type="textarea" :rows="3" maxlength="300" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="adjusting" @click="submitAdjust">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, type PageResult } from '../api/http'
import {
  BOOKING_STATUS,
  cancelBooking,
  listAvailableVenues,
  listMyBookings,
  type AvailableVenue,
  type Booking
} from '../api/bookings'
import { submitAdjustment } from '../api/adjustments'

const router = useRouter()
const rows = ref<Booking[]>([])
const status = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const loading = ref(false)
const counts = reactive({ pending_review: 0, approved: 0, rejected: 0, all: 0 })
const adjustVisible = ref(false)
const adjusting = ref(false)
const adjustBooking = ref<Booking | null>(null)
const availableVenues = ref<AvailableVenue[]>([])
const adjustForm = reactive({
  date: '',
  startTime: '',
  endTime: '',
  venueId: 0,
  deviceId: 0,
  reason: ''
})
const availableDevices = computed(
  () => availableVenues.value.find((item) => item.venueId === adjustForm.venueId)?.devices ?? []
)

function statusMeta(value: string) {
  return BOOKING_STATUS[value] ?? { label: value, type: 'info' as const }
}

function hoursLeft(row: Booking): number {
  return (new Date(row.startTime.replace(' ', 'T')).getTime() - Date.now()) / 3600000
}

function openAdjust(row: Booking) {
  adjustBooking.value = row
  adjustForm.date = row.startTime.slice(0, 10)
  adjustForm.startTime = row.startTime.slice(11, 16)
  adjustForm.endTime = row.endTime.slice(11, 16)
  adjustForm.venueId = 0
  adjustForm.deviceId = 0
  adjustForm.reason = ''
  availableVenues.value = []
  adjustVisible.value = true
  loadAdjustResources()
}

async function loadAdjustResources() {
  if (!adjustForm.date || !adjustForm.startTime || !adjustForm.endTime) return
  try {
    availableVenues.value = await listAvailableVenues({
      date: adjustForm.date,
      startTime: adjustForm.startTime,
      endTime: adjustForm.endTime
    })
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function submitAdjust() {
  if (!adjustBooking.value) return
  if (!adjustForm.date || !adjustForm.startTime || !adjustForm.endTime) {
    ElMessage.warning('请选择新日期和时段')
    return
  }
  if (!adjustForm.venueId || !adjustForm.deviceId) {
    ElMessage.warning('请选择新场地和设备')
    return
  }
  if (!adjustForm.reason.trim()) {
    ElMessage.warning('请填写调整原因')
    return
  }
  adjusting.value = true
  try {
    await submitAdjustment(adjustBooking.value.id, {
      newVenueId: adjustForm.venueId,
      newDeviceId: adjustForm.deviceId,
      newStartTime: `${adjustForm.date} ${adjustForm.startTime}:00`,
      newEndTime: `${adjustForm.date} ${adjustForm.endTime}:00`,
      reason: adjustForm.reason.trim()
    })
    ElMessage.success('调整申请已提交')
    adjustVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    adjusting.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const result = await listMyBookings({ status: status.value, page: page.value, size: size.value })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function loadCounts() {
  try {
    const all = await get<PageResult<Booking>>('/farmer/bookings', { size: 50 })
    counts.all = all.total
    const [pending, approved, rejected] = await Promise.all([
      get<PageResult<Booking>>('/farmer/bookings', { status: 'pending_review', size: 1 }),
      get<PageResult<Booking>>('/farmer/bookings', { status: 'approved', size: 1 }),
      get<PageResult<Booking>>('/farmer/bookings', { status: 'rejected', size: 1 })
    ])
    counts.pending_review = pending.total
    counts.approved = approved.total
    counts.rejected = rejected.total
  } catch {
    // 统计失败不阻塞列表
  }
}

async function remove(row: Booking) {
  const hoursLeft = (new Date(row.startTime.replace(' ', 'T')).getTime() - Date.now()) / 3600000
  if (hoursLeft < 24) {
    ElMessage.warning('开播前 24 小时内不能取消')
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('请输入取消原因（选填）', '取消预约', {
      confirmButtonText: '取消预约',
      cancelButtonText: '再想想',
      inputPlaceholder: '例如：农户临时有事'
    })
    await cancelBooking(row.id, value)
    ElMessage.success('预约已取消')
    await load()
    await loadCounts()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error((error as Error).message || '取消失败')
    }
  }
}

function changePage(next: number) {
  page.value = next
  load()
}

function openCreate() {
  router.push('/farmer/bookings/new')
}

onMounted(() => {
  load()
  loadCounts()
})
</script>

<style scoped>
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}

.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(31, 59, 37, 0.06);
}

.stat-label {
  font-size: 13px;
  color: #7a8699;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #1f3b25;
  margin-top: 6px;
}

.stat-value.warning {
  color: #ff7d00;
}

.stat-value.success {
  color: #00b42a;
}

.stat-value.danger {
  color: #f53f3f;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.muted {
  color: #a8b2c1;
}

.recommend {
  font-size: 12px;
  color: #3b6cff;
  margin-top: 2px;
}

.time-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
</style>
