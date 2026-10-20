<template>
  <div>
    <h2 class="page-title">工作台</h2>
    <p class="page-subtitle">系统管理员视角，查看待审核、今日直播和资源概况。</p>

    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="6" v-for="item in cards" :key="item.key">
        <div class="stat-card">
          <div class="stat-icon" :style="{ background: item.bg }">
            <el-icon :size="22"><component :is="item.icon" /></el-icon>
          </div>
          <div>
            <div class="stat-label">{{ item.label }}</div>
            <div class="stat-value">{{ data[item.key] ?? 0 }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="page-card quick-card">
      <h3>当前开发范围</h3>
      <p>已覆盖登录、账号、农户、场地、设备、区域、预约调整、排队候补、通知、销售统计和操作日志。二次验证、AI 助手和手机适配在后续迭代实现。</p>
      <el-alert
        title="初始密码提示"
        type="warning"
        :closable="false"
        description="admin 初始密码为 Admin@123；首次登录后系统会要求修改密码。"
        show-icon
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Avatar, Calendar, Monitor, OfficeBuilding, Tickets, Warning } from '@element-plus/icons-vue'
import { get } from '../api/http'

interface Dashboard {
  accounts: number
  farmers: number
  venues: number
  devices: number
  bookings: number
  pendingReview: number
  todayLive: number
  availableVenues: number
  availableDevices: number
}

const data = reactive<Dashboard>({
  accounts: 0,
  farmers: 0,
  venues: 0,
  devices: 0,
  bookings: 0,
  pendingReview: 0,
  todayLive: 0,
  availableVenues: 0,
  availableDevices: 0
})

const cards = ref([
  { key: 'pendingReview' as const, label: '待审核预约', icon: Warning, bg: '#fff5e6' },
  { key: 'todayLive' as const, label: '今日直播', icon: Calendar, bg: '#e8f0fe' },
  { key: 'availableVenues' as const, label: '可用场地', icon: OfficeBuilding, bg: '#e8f4e4' },
  { key: 'availableDevices' as const, label: '可用设备', icon: Monitor, bg: '#f3e8ff' },
  { key: 'bookings' as const, label: '预约总数', icon: Tickets, bg: '#e6fffb' },
  { key: 'farmers' as const, label: '农户', icon: Avatar, bg: '#f7faf5' }
])

onMounted(async () => {
  try {
    const result = await get<Dashboard>('/admin/dashboard')
    Object.assign(data, result)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
})
</script>

<style scoped>
.stat-card {
  background: #fff;
  border-radius: 10px;
  padding: 18px;
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  color: #2f7d32;
}

.stat-label {
  color: #7a8699;
  font-size: 13px;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1f2d3d;
}

.quick-card {
  margin-top: 8px;
  line-height: 1.7;
}

.quick-card h3 {
  margin: 0 0 8px;
}

.quick-card p {
  color: #5c6b7a;
  margin-top: 0;
}
</style>
