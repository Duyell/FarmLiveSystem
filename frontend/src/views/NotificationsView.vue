<template>
  <div>
    <h2 class="page-title">通知管理</h2>
    <p class="page-subtitle">查看发给您的事件通知，点击可标记已读。</p>

    <div class="page-card">
      <div class="page-toolbar">
        <el-select v-model="type" placeholder="全部类型" clearable style="width: 160px" @change="load">
          <el-option v-for="(label, key) in NOTIFICATION_TYPE" :key="key" :label="label" :value="key" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column width="60">
          <template #default="{ row }">
            <el-badge is-dot :hidden="row.read" class="unread-dot" />
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ NOTIFICATION_TYPE[row.type] ?? row.type }}</template>
        </el-table-column>
        <el-table-column prop="title" label="标题" width="150" />
        <el-table-column prop="content" label="内容" min-width="320" />
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.read" link type="primary" @click="markRead(row)">标记已读</el-button>
            <span v-else class="muted">已读</span>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  NOTIFICATION_TYPE,
  listMyNotifications,
  markNotificationRead,
  type NotificationItem
} from '../api/notifications'

const rows = ref<NotificationItem[]>([])
const type = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await listMyNotifications({ type: type.value, page: page.value, size: size.value })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function markRead(row: NotificationItem) {
  try {
    await markNotificationRead(row.id)
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function changePage(next: number) {
  page.value = next
  load()
}

onMounted(load)
</script>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.unread-dot :deep(.el-badge__dot) {
  border: none;
}

.muted {
  color: #a8b2c1;
  font-size: 12px;
}
</style>
