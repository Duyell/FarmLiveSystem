<template>
  <div>
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="12" :lg="6">
        <div class="stat-card">
          <div class="stat-label">预约总数</div>
          <div class="stat-value">{{ stats.bookingTotal }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :lg="6">
        <div class="stat-card">
          <div class="stat-label">已通过</div>
          <div class="stat-value success">{{ stats.approvedCount }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :lg="6">
        <div class="stat-card">
          <div class="stat-label">已完成场次</div>
          <div class="stat-value">{{ stats.completedCount }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :lg="6">
        <div class="stat-card">
          <div class="stat-label">卖出数量</div>
          <div class="stat-value warning">{{ stats.soldQuantity }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="page-card">
      <h3 class="section-title">我的已完成场次</h3>
      <el-table :data="details" v-loading="loading" border stripe>
        <el-table-column prop="startTime" label="场次时间" width="165" />
        <el-table-column prop="bookingNo" label="预约号" width="165" show-overflow-tooltip />
        <el-table-column prop="venueName" label="场地" width="120" />
        <el-table-column prop="topic" label="直播主题" min-width="150" show-overflow-tooltip />
        <el-table-column prop="soldQuantity" label="卖出数量" width="100" />
        <el-table-column prop="salesCount" label="销售条数" width="90" />
      </el-table>
      <el-empty v-if="!loading && details.length === 0" description="还没有已完成场次" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyStats, getMyStatsDetails, type MyStats, type StatsDetail } from '../api/stats'

const loading = ref(false)
const stats = reactive<MyStats>({
  bookingTotal: 0,
  approvedCount: 0,
  completedCount: 0,
  soldQuantity: 0
})
const details = ref<StatsDetail[]>([])

async function load() {
  loading.value = true
  try {
    const [overview, list] = await Promise.all([getMyStats(), getMyStatsDetails()])
    Object.assign(stats, overview)
    details.value = list
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.stat-row {
  margin-bottom: 16px;
}
.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(31, 59, 37, 0.06);
  margin-bottom: 12px;
}
.stat-label {
  font-size: 13px;
  color: #7a8699;
}
.stat-value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 700;
  color: #1f3b25;
}
.stat-value.success {
  color: #00b42a;
}
.stat-value.warning {
  color: #ff7d00;
}
.section-title {
  margin: 0 0 14px;
  font-size: 16px;
  color: #1f3b25;
}
</style>
