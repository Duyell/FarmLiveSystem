<template>
  <div>
    <h2 class="page-title">数据统计</h2>
    <p class="page-subtitle">
      {{ canSeeDetail ? '按时间、区域和农产品查看四项指标、完成场次明细与趋势。' : '按时间、区域和农产品查看四项指标和趋势。数据统计员只查看聚合数据。' }}
    </p>

    <div class="page-card">
      <div class="page-toolbar stats-filter">
        <el-date-picker
          v-model="range"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 260px"
        />
        <RegionSelect v-model="countyCode" class="region-filter" />
        <el-select
          v-model="productName"
          placeholder="全部农产品"
          clearable
          filterable
          style="width: 100%"
        >
          <el-option v-for="name in productNames" :key="name" :label="name" :value="name" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button v-if="canSeeDetail" @click="exportData">导出 CSV</el-button>
      </div>

      <el-row :gutter="16" class="metric-row">
        <el-col :xs="12" :sm="12" :lg="6">
          <div class="metric-card">
            <div class="metric-label">场地占用率</div>
            <div class="metric-value">{{ overview.occupancyRate }}%</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :lg="6">
          <div class="metric-card">
            <div class="metric-label">直播场次</div>
            <div class="metric-value">{{ overview.liveCount }}</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :lg="6">
          <div class="metric-card">
            <div class="metric-label">卖出数量</div>
            <div class="metric-value">{{ overview.soldQuantity }}</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :lg="6">
          <div class="metric-card">
            <div class="metric-label">预约成功率</div>
            <div class="metric-value">{{ overview.successRate }}%</div>
          </div>
        </el-col>
      </el-row>

      <div class="section-title">趋势</div>
      <el-table :data="trend" size="small" border>
        <el-table-column prop="period" label="周期" />
        <el-table-column prop="liveCount" label="完成场次" width="120" />
        <el-table-column prop="soldQuantity" label="卖出数量" width="120" />
      </el-table>

      <template v-if="canSeeDetail">
        <div class="section-title">完成场次明细</div>
        <el-table :data="details" v-loading="loading" border stripe>
          <el-table-column prop="startTime" label="场次时间" width="160" />
          <el-table-column prop="bookingNo" label="预约号" width="160" />
          <el-table-column prop="farmerName" label="农户" width="90" />
          <el-table-column prop="venueName" label="场地" width="120" />
          <el-table-column prop="topic" label="主题" min-width="140" />
          <el-table-column prop="soldQuantity" label="卖出数量" width="100" />
          <el-table-column prop="salesCount" label="销售条数" width="90" />
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
      </template>
      <el-alert
        v-else
        class="privacy-tip"
        type="info"
        :closable="false"
        title="数据统计员只查看聚合指标和趋势，不展示单个农户明细。"
        show-icon
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import RegionSelect from '../components/RegionSelect.vue'
import { currentUser } from '../api/auth'
import {
  downloadStats,
  getDetails,
  getOverview,
  getTrend,
  listProductNames,
  type StatsDetail,
  type StatsOverview,
  type StatsTrend
} from '../api/stats'

const canSeeDetail = computed(() => currentUser()?.roleCode !== 'data_viewer')

const range = ref<[string, string] | null>(null)
const countyCode = ref('')
const productName = ref('')
const productNames = ref<string[]>([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const loading = ref(false)
const overview = reactive<StatsOverview>({
  occupancyRate: 0,
  liveCount: 0,
  soldQuantity: 0,
  successRate: 0,
  bookingTotal: 0,
  approvedCount: 0,
  completedCount: 0
})
const trend = ref<StatsTrend[]>([])
const details = ref<StatsDetail[]>([])

function query() {
  return {
    from: range.value?.[0],
    to: range.value?.[1],
    countyCode: countyCode.value || undefined,
    productName: productName.value.trim() || undefined
  }
}

async function load() {
  loading.value = true
  try {
    const [ov, tr] = await Promise.all([
      getOverview(query()),
      getTrend({ ...query(), granularity: 'day' })
    ])
    Object.assign(overview, ov)
    trend.value = tr
    if (canSeeDetail.value) {
      const detail = await getDetails({ ...query(), page: page.value, size: size.value })
      details.value = detail.items
      total.value = detail.total
    } else {
      details.value = []
      total.value = 0
    }
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
    const blob = await downloadStats(query())
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'stats-details.csv'
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function loadProductNames() {
  try {
    productNames.value = await listProductNames()
  } catch {
    // 农产品下拉加载失败时仍可按其他条件统计
  }
}

onMounted(async () => {
  await loadProductNames()
  await load()
})
</script>

<style scoped>
.stats-filter {
  display: grid;
  grid-template-columns: minmax(210px, 1.1fr) minmax(170px, 0.9fr) minmax(130px, 0.6fr) auto auto;
  align-items: center;
  gap: 10px;
  flex-wrap: nowrap;
}

.stats-filter :deep(.el-date-editor),
.stats-filter :deep(.el-input),
.stats-filter :deep(.el-select) {
  width: 100% !important;
}

.region-filter {
  width: 100%;
}

.metric-row {
  margin: 8px 0 20px;
}
.metric-card {
  background: #f7faf5;
  border: 1px solid #e4ecdf;
  border-radius: 10px;
  padding: 14px 16px;
}
.metric-label {
  color: #7a8699;
  font-size: 13px;
}
.metric-value {
  margin-top: 6px;
  color: #1f3b25;
  font-size: 24px;
  font-weight: 700;
}
.section-title {
  margin: 18px 0 10px;
  font-weight: 600;
  color: #1f3b25;
}

.privacy-tip {
  margin-top: 18px;
}

@media (max-width: 768px) {
  .stats-filter {
    grid-template-columns: 1fr;
  }

  .stats-filter :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }

  .region-filter {
    width: 100% !important;
  }
}
</style>
