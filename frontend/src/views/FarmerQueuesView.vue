<template>
  <div>
    <h2 class="page-title">直播排队与候补</h2>
    <p class="page-subtitle">时段被占满时可加入排队；场地释放后队首自动升级为候补，确认后分配设备生成预约。</p>

    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label">当前排队中</div>
        <div class="stat-value">{{ counts.queued }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">候补状态</div>
        <div class="stat-value warning">{{ counts.candidate }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已确认</div>
        <div class="stat-value success">{{ counts.confirmed }}</div>
      </div>
    </div>

    <div class="page-card join-card">
      <h3 class="block-title">时段状态查询</h3>
      <el-form :inline="true" :model="joinForm">
        <el-form-item label="场地">
          <el-select v-model="joinForm.venueId" placeholder="选择场地" style="width: 220px">
            <el-option
              v-for="venue in venues"
              :key="venue.venueId"
              :label="venue.venueName"
              :value="venue.venueId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="joinForm.date" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
        </el-form-item>
        <el-form-item label="时段">
          <el-time-select v-model="joinForm.startTime" start="06:00" step="01:00" end="21:00" placeholder="开始" style="width: 110px" />
          <span class="time-sep">至</span>
          <el-time-select v-model="joinForm.endTime" start="07:00" step="01:00" end="22:00" placeholder="结束" style="width: 110px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="checking" @click="check">查询状态</el-button>
        </el-form-item>
      </el-form>

      <div v-if="status" class="status-card" :class="statusClass">
        <div class="status-text">{{ status.reason }}</div>
        <div class="status-actions">
          <el-button
            v-if="!status.occupied"
            type="primary"
            size="small"
            @click="goBooking"
          >
            去预约
          </el-button>
          <el-button
            v-if="status.canJoin"
            type="warning"
            size="small"
            :loading="joining"
            @click="join"
          >
            加入排队
          </el-button>
        </div>
      </div>

      <div class="join-hint">规则：时段被他人占满时才需要排队；同一时段只能有一个有效排队；开播前 24 小时内不能加入；候补确认后又取消，72 小时内不能对该场地排队。</div>
    </div>

    <div class="page-card">
      <h3 class="block-title">我的排队状态详情</h3>
      <el-empty v-if="rows.length === 0 && !loading" description="暂无排队记录" />
      <div v-for="row in rows" :key="row.id" class="queue-card">
        <div class="queue-head">
          <div>
            <span class="queue-venue">{{ row.venueName }}</span>
            <span class="queue-no">{{ row.queueNo }}</span>
          </div>
          <el-tag :type="statusMeta(row.status).type">{{ statusMeta(row.status).label }}</el-tag>
        </div>
        <div class="queue-slot">{{ row.slotStart }} 至 {{ row.slotEnd.slice(11) }}</div>
        <div v-if="row.status === 'queued' || row.status === 'candidate'" class="progress-row">
          <span class="progress-label">当前顺位 {{ row.position }}/{{ row.totalInQueue }}</span>
          <el-progress
            class="progress-bar"
            :percentage="row.totalInQueue === 0 ? 0 : Math.round(((row.totalInQueue - row.position + 1) / row.totalInQueue) * 100)"
            :stroke-width="8"
          />
        </div>
        <div class="queue-meta">
          <span v-if="row.status === 'queued'">预计等待 {{ row.estimatedWait }} 场</span>
          <span v-if="row.status === 'candidate' && row.candidateDeadline" class="deadline">
            请在 {{ row.candidateDeadline }} 前确认，超时自动顺延
          </span>
          <span v-if="row.status === 'confirmed' && row.assignedDeviceName">
            已分配设备：{{ row.assignedDeviceName }}，预约已生成
          </span>
        </div>
        <div v-if="row.status === 'candidate'" class="queue-actions">
          <el-button type="primary" size="small" :loading="acting" @click="confirmIt(row)">确认候补</el-button>
          <el-button size="small" @click="abandonIt(row)">放弃</el-button>
        </div>
        <div v-else-if="row.status === 'queued'" class="queue-actions">
          <el-button size="small" @click="abandonIt(row)">放弃排队</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAvailableVenues, type AvailableVenue } from '../api/bookings'
import {
  QUEUE_STATUS,
  abandonQueue,
  confirmQueue,
  joinQueue,
  listMyQueues,
  slotStatus,
  type QueueTicket,
  type SlotStatus
} from '../api/queues'

const router = useRouter()
const rows = ref<QueueTicket[]>([])
const venues = ref<AvailableVenue[]>([])
const loading = ref(false)
const joining = ref(false)
const acting = ref(false)
const checking = ref(false)
const status = ref<SlotStatus | null>(null)

const joinForm = reactive({
  venueId: undefined as number | undefined,
  date: '',
  startTime: '',
  endTime: ''
})

const counts = computed(() => ({
  queued: rows.value.filter((item) => item.status === 'queued').length,
  candidate: rows.value.filter((item) => item.status === 'candidate').length,
  confirmed: rows.value.filter((item) => item.status === 'confirmed').length
}))

const statusClass = computed(() => {
  if (!status.value) return ''
  if (status.value.canJoin) return 'status-warn'
  if (!status.value.occupied) return 'status-free'
  return 'status-muted'
})

function statusMeta(value: string) {
  return QUEUE_STATUS[value] ?? { label: value, type: 'info' as const }
}

async function load() {
  loading.value = true
  try {
    rows.value = await listMyQueues()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function loadVenues() {
  try {
    venues.value = await listAvailableVenues({})
  } catch {
    // 场地加载失败不阻塞排队列表
  }
}

function slotPayload() {
  return {
    venueId: joinForm.venueId!,
    startTime: `${joinForm.date} ${joinForm.startTime}:00`,
    endTime: `${joinForm.date} ${joinForm.endTime}:00`
  }
}

function formReady() {
  if (!joinForm.venueId || !joinForm.date || !joinForm.startTime || !joinForm.endTime) {
    ElMessage.warning('请把场地、日期、时段选完整')
    return false
  }
  if (joinForm.startTime >= joinForm.endTime) {
    ElMessage.warning('开始时间必须早于结束时间')
    return false
  }
  return true
}

async function check() {
  if (!formReady()) return
  checking.value = true
  try {
    status.value = await slotStatus(slotPayload())
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    checking.value = false
  }
}

function goBooking() {
  router.push('/farmer/bookings/new')
}

async function join() {
  if (!formReady()) return
  joining.value = true
  try {
    const ticket = await joinQueue(slotPayload())
    ElMessage.success(`已加入排队：${ticket.queueNo}，当前顺位第 ${ticket.position} 位`)
    status.value = null
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    joining.value = false
  }
}

async function confirmIt(row: QueueTicket) {
  acting.value = true
  try {
    const result = await confirmQueue(row.id)
    ElMessage.success(`候补确认成功，已分配设备并生成预约（单号 ${result.confirmedBookingId}）`)
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    acting.value = false
  }
}

async function abandonIt(row: QueueTicket) {
  try {
    await ElMessageBox.confirm(`确定放弃排队「${row.queueNo}」吗？`, '放弃确认', { type: 'warning' })
    await abandonQueue(row.id)
    ElMessage.success('已放弃')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error((error as Error).message || '操作失败')
    }
  }
}

onMounted(() => {
  load()
  loadVenues()
})
</script>

<style scoped>
.stat-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
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

.join-card {
  margin-bottom: 16px;
}

.block-title {
  margin: 0 0 14px;
  font-size: 15px;
  color: #1f3b25;
}

.join-hint {
  font-size: 12px;
  color: #7a8699;
  background: #f5f8f2;
  border-radius: 8px;
  padding: 8px 12px;
}

.status-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-radius: 10px;
  padding: 12px 16px;
  margin-bottom: 12px;
}

.status-free {
  background: #f0f9eb;
  border: 1px solid #c2e7b0;
  color: #2f7d32;
}

.status-warn {
  background: #fdf6ec;
  border: 1px solid #f3d19e;
  color: #b88230;
}

.status-muted {
  background: #f4f4f5;
  border: 1px solid #e4e7ed;
  color: #7a8699;
}

.status-text {
  font-size: 13px;
}

.status-actions {
  white-space: nowrap;
}

.time-sep {
  margin: 0 6px;
  color: #5e6d82;
}

.queue-card {
  border: 1px solid #eef1f4;
  border-radius: 10px;
  padding: 14px 16px;
  margin-bottom: 12px;
  transition: box-shadow 0.2s;
}

.queue-card:hover {
  box-shadow: 0 4px 14px rgba(31, 59, 37, 0.08);
}

.queue-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.queue-venue {
  font-size: 15px;
  font-weight: 600;
  color: #1f3b25;
  margin-right: 10px;
}

.queue-no {
  font-size: 12px;
  color: #7a8699;
}

.queue-slot {
  font-size: 13px;
  color: #5e6d82;
  margin-top: 6px;
}

.progress-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
}

.progress-label {
  font-size: 12px;
  color: #3b6cff;
  white-space: nowrap;
}

.progress-bar {
  flex: 1;
}

.queue-meta {
  font-size: 12px;
  color: #7a8699;
  margin-top: 8px;
}

.deadline {
  color: #ff7d00;
}

.queue-actions {
  margin-top: 10px;
}
</style>
