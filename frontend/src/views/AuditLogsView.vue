<template>
  <div>
    <h2 class="page-title">操作日志</h2>
    <p class="page-subtitle">关键操作只追加留痕，可按操作人、操作类型和时间查询、导出。</p>

    <div class="page-card">
      <div class="page-toolbar log-filter">
        <el-input v-model="keyword" placeholder="操作人 / 详情" clearable style="width: 220px" @keyup.enter="load" />
        <el-select v-model="action" placeholder="全部操作" clearable style="width: 190px" @change="load">
          <el-option label="预约调整-提交" value="BOOKING_ADJUST_SUBMIT" />
          <el-option label="预约调整-通过" value="BOOKING_ADJUST_APPROVE" />
          <el-option label="预约调整-驳回" value="BOOKING_ADJUST_REJECT" />
          <el-option label="预约审核" value="BOOKING_REVIEW" />
          <el-option label="预约取消" value="BOOKING_CANCEL" />
          <el-option label="场次完成" value="BOOKING_COMPLETE" />
          <el-option label="场次未到场" value="BOOKING_NO_SHOW" />
          <el-option label="销售录入" value="SALES_CREATE" />
          <el-option label="销售修改" value="SALES_UPDATE" />
          <el-option label="销售删除" value="SALES_DELETE" />
          <el-option label="资质上传" value="QUALIFICATION_UPLOAD" />
          <el-option label="资质通过" value="QUALIFICATION_APPROVED" />
          <el-option label="资质驳回" value="QUALIFICATION_REJECTED" />
          <el-option label="农户新增" value="FARMER_CREATE" />
          <el-option label="农户修改" value="FARMER_UPDATE" />
          <el-option label="场地修改" value="VENUE_UPDATE" />
          <el-option label="设备组合修改" value="DEVICE_UPDATE" />
        </el-select>
        <el-date-picker
          v-model="range"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 260px"
        />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="exportData">导出 CSV</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="operatorName" label="操作人" width="120" />
        <el-table-column prop="roleCode" label="角色" width="120" />
        <el-table-column prop="action" label="操作" width="190" />
        <el-table-column prop="detail" label="详情" min-width="280" />
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { downloadLogs, listLogs, type AuditLog } from '../api/logs'

const rows = ref<AuditLog[]>([])
const keyword = ref('')
const action = ref('')
const range = ref<[string, string] | null>(null)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const loading = ref(false)

function query() {
  return {
    keyword: keyword.value.trim() || undefined,
    action: action.value || undefined,
    from: range.value?.[0],
    to: range.value?.[1]
  }
}

async function load() {
  loading.value = true
  try {
    const result = await listLogs({ ...query(), page: page.value, size: size.value })
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

async function exportData() {
  try {
    const blob = await downloadLogs(query())
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'audit-logs.csv'
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

onMounted(load)
</script>

<style scoped>
.log-filter {
  flex-wrap: wrap;
}
</style>
