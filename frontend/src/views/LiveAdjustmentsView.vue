<template>
  <div>
    <h2 class="page-title">预约调整审批</h2>
    <p class="page-subtitle">处理农户提交的改期申请；通过后按新时段锁定资源，原预约不丢失。</p>

    <div class="page-card">
      <div class="page-toolbar">
        <el-input v-model="keyword" placeholder="预约号 / 农户 / 申请号" clearable style="width: 240px" @keyup.enter="load" />
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 150px" @change="load">
          <el-option label="待审批" value="pending" />
          <el-option label="已通过" value="approved" />
          <el-option label="已驳回" value="rejected" />
          <el-option label="已撤回" value="withdrawn" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="requestNo" label="申请号" width="190" />
        <el-table-column prop="bookingNo" label="预约号" width="185" />
        <el-table-column prop="farmerName" label="农户" width="100" />
        <el-table-column label="原时段" width="210">
          <template #default="{ row }">
            <div>{{ row.oldVenueName }}</div>
            <div class="muted">{{ row.oldStartTime }} 至 {{ row.oldEndTime.slice(11) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="申请新时段" width="220">
          <template #default="{ row }">
            <div>{{ row.newVenueName }} / {{ row.newDeviceName }}</div>
            <div class="muted">{{ row.newStartTime }} 至 {{ row.newEndTime.slice(11) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="160" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type">{{ statusMeta(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reviewComment" label="审批意见" min-width="140" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'pending'" link type="primary" @click="openReview(row)">审批</el-button>
            <span v-else class="muted">-</span>
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

    <el-dialog v-model="dialogVisible" title="审批调整申请" width="460px">
      <el-form label-position="top">
        <el-form-item label="申请">
          <div>{{ current?.requestNo }}：{{ current?.oldVenueName }} {{ current?.oldStartTime }} →
            {{ current?.newVenueName }} {{ current?.newStartTime }}</div>
        </el-form-item>
        <el-form-item label="审批结果">
          <el-radio-group v-model="form.result">
            <el-radio value="approve">通过</el-radio>
            <el-radio value="reject">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.result === 'reject'" label="驳回意见（至少 5 个字）">
          <el-input v-model="form.comment" type="textarea" :rows="3" maxlength="300" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReview">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ADJUSTMENT_STATUS,
  listLiveAdjustments,
  reviewAdjustment,
  type Adjustment
} from '../api/adjustments'

const rows = ref<Adjustment[]>([])
const status = ref('')
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const current = ref<Adjustment | null>(null)
const form = reactive<{ result: 'approve' | 'reject'; comment: string }>({
  result: 'approve',
  comment: ''
})

function statusMeta(value: string) {
  return ADJUSTMENT_STATUS[value] ?? { label: value, type: 'info' as const }
}

async function load() {
  loading.value = true
  try {
    const result = await listLiveAdjustments({
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

function changePage(value: number) {
  page.value = value
  load()
}

function openReview(row: Adjustment) {
  current.value = row
  form.result = 'approve'
  form.comment = ''
  dialogVisible.value = true
}

async function submitReview() {
  if (!current.value) return
  if (form.result === 'reject' && form.comment.trim().length < 5) {
    ElMessage.warning('驳回意见至少 5 个字')
    return
  }
  saving.value = true
  try {
    await reviewAdjustment(current.value.id, {
      result: form.result,
      comment: form.result === 'reject' ? form.comment.trim() : undefined
    })
    ElMessage.success('审批完成')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.muted {
  color: #8a94a6;
  font-size: 12px;
}
</style>
