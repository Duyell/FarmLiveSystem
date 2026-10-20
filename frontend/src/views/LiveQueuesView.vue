<template>
  <div>
    <h2 class="page-title">直播排队与候补</h2>
    <p class="page-subtitle">查看各场地时段的排队情况；关闭队列后该时段全部有效排队终止。</p>

    <div class="page-card">
      <div class="page-toolbar">
        <el-select v-model="venueId" placeholder="全部场地" clearable style="width: 220px">
          <el-option v-for="venue in venues" :key="venue.venueId" :label="venue.venueName" :value="venue.venueId" />
        </el-select>
        <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" placeholder="按日期筛选" clearable />
        <el-button type="primary" @click="load">查询</el-button>
      </div>

      <el-empty v-if="groups.length === 0 && !loading" description="暂无排队数据" />
      <div v-for="group in groups" :key="group.key" class="queue-group">
        <div class="group-head">
          <div>
            <span class="group-venue">{{ group.venueName }}</span>
            <span class="group-slot">{{ group.slotStart.slice(0, 16) }} 至 {{ group.slotEnd.slice(11, 16) }}</span>
            <el-tag size="small" class="group-count">{{ group.items.length }} 人</el-tag>
          </div>
          <el-button
            v-if="hasActive(group)"
            type="danger"
            plain
            size="small"
            :loading="acting"
            @click="closeGroup(group)"
          >
            关闭队列
          </el-button>
        </div>
        <el-table :data="group.items" size="small" border>
          <el-table-column prop="position" label="顺位" width="70" />
          <el-table-column prop="queueNo" label="排队号" width="200" />
          <el-table-column prop="farmerName" label="农户" width="120" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="statusMeta(row.status).type">{{ statusMeta(row.status).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="候选截止" min-width="170">
            <template #default="{ row }">{{ row.candidateDeadline ?? '-' }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAvailableVenues, type AvailableVenue } from '../api/bookings'
import { QUEUE_STATUS, closeQueue, listLiveQueues, type QueueTicket } from '../api/queues'

interface QueueGroup {
  key: string
  venueName: string
  slotStart: string
  slotEnd: string
  items: QueueTicket[]
}

const rows = ref<QueueTicket[]>([])
const venues = ref<AvailableVenue[]>([])
const venueId = ref<number | undefined>()
const date = ref('')
const loading = ref(false)
const acting = ref(false)

const groups = computed<QueueGroup[]>(() => {
  const map = new Map<string, QueueGroup>()
  for (const item of rows.value) {
    const key = `${item.venueId}-${item.slotStart}`
    if (!map.has(key)) {
      map.set(key, {
        key,
        venueName: item.venueName,
        slotStart: item.slotStart,
        slotEnd: item.slotEnd,
        items: []
      })
    }
    map.get(key)!.items.push(item)
  }
  return [...map.values()].sort((a, b) => a.slotStart.localeCompare(b.slotStart))
})

function statusMeta(value: string) {
  return QUEUE_STATUS[value] ?? { label: value, type: 'info' as const }
}

function hasActive(group: QueueGroup) {
  return group.items.some((item) => item.status === 'queued' || item.status === 'candidate')
}

async function load() {
  loading.value = true
  try {
    rows.value = await listLiveQueues({ venueId: venueId.value, date: date.value || undefined })
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function closeGroup(group: QueueGroup) {
  const first = group.items.find((item) => item.status === 'queued' || item.status === 'candidate')
  if (!first) return
  try {
    await ElMessageBox.confirm(
      `确定关闭「${group.venueName} ${group.slotStart.slice(0, 16)}」的整个队列吗？该时段全部有效排队将终止。`,
      '关闭队列',
      { type: 'warning' }
    )
    await closeQueue(first.id)
    ElMessage.success('队列已关闭')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error((error as Error).message || '操作失败')
    }
  }
}

onMounted(async () => {
  load()
  try {
    venues.value = await listAvailableVenues({})
  } catch {
    // 场地加载失败不阻塞
  }
})
</script>

<style scoped>
.page-toolbar {
  gap: 10px;
}

.queue-group {
  border: 1px solid #eef1f4;
  border-radius: 10px;
  padding: 14px 16px;
  margin-bottom: 14px;
}

.group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.group-venue {
  font-size: 15px;
  font-weight: 600;
  color: #1f3b25;
  margin-right: 10px;
}

.group-slot {
  font-size: 13px;
  color: #5e6d82;
  margin-right: 10px;
}

.group-count {
  color: #3b6cff;
}
</style>
