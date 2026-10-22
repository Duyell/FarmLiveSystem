<template>
  <div>
    <h2 class="page-title">设备组合管理</h2>
    <p class="page-subtitle">每个组合是一套完整直播设备，组合内不再拆成单个补光灯或摄像机；组合必须归属一个场地。</p>

    <div class="page-card">
      <div class="page-toolbar">
        <el-input v-model="keyword" placeholder="搜索组合编号 / 名称 / 配置 / 场地" clearable style="width: 280px" @keyup.enter="load" />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="openCreate">新增设备组合</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="deviceNo" label="组合编号" width="130" />
        <el-table-column label="设备组合" min-width="220">
          <template #default="{ row }">
            <div>{{ row.name }}</div>
            <div class="config-text">{{ row.config || '未配置组合内容' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="venueName" label="所属场地" width="140" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="calibrationDate" label="下次校准" width="110" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑设备组合' : '新增设备组合'" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="组合编号">
          <el-input v-model="form.deviceNo" :disabled="isEdit" placeholder="如 ZB-C001-03" />
        </el-form-item>
        <el-form-item label="组合名称">
          <el-input v-model="form.name" placeholder="如 设备组合3" />
        </el-form-item>
        <el-form-item label="组合型号">
          <el-input v-model="form.model" placeholder="如 手机直播组合" />
        </el-form-item>
        <el-form-item label="组合配置">
          <el-input
            v-model="form.config"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="如 手机 + 三脚架 + 双补光灯 + 无线领夹麦 + 5G 路由 + 插排"
          />
        </el-form-item>
        <el-form-item label="所属场地">
          <el-select v-model="form.venueId" style="width: 100%" placeholder="请选择场地">
            <el-option v-for="venue in venues" :key="venue.id" :label="`${venue.venueNo} · ${venue.name}`" :value="venue.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="可用" value="available" />
            <el-option label="维修" value="maintenance" />
            <el-option label="停用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="下次校准">
          <el-date-picker v-model="form.calibrationDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="isEdit" label="修改原因">
          <el-input v-model="form.changeReason" maxlength="200" placeholder="本次修改的原因，会写入操作日志" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { del, get, post, put, type PageResult } from '../api/http'

interface Venue {
  id: number
  venueNo: string
  name: string
}

interface Device {
  id: number
  deviceNo: string
  name: string
  model: string | null
  config: string | null
  venueId: number
  venueName: string
  status: string
  calibrationDate: string | null
  createdAt: string
}

const rows = ref<Device[]>([])
const venues = ref<Venue[]>([])
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)

const form = reactive({
  id: 0,
  deviceNo: '',
  name: '',
  model: '',
  config: '',
  venueId: 0,
  status: 'available',
  calibrationDate: '',
  changeReason: ''
})

function statusLabel(value: string) {
  return { available: '可用', maintenance: '维修', disabled: '停用' }[value] || value
}

function statusTag(value: string) {
  return { available: 'success', maintenance: 'warning', disabled: 'info' }[value] || 'info'
}

async function load() {
  loading.value = true
  try {
    const result = await get<PageResult<Device>>('/live/devices', {
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

async function loadVenues() {
  try {
    const result = await get<PageResult<Venue>>('/live/venues', { page: 1, size: 100 })
    venues.value = result.items
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, {
    id: 0,
    deviceNo: '',
    name: '',
    model: '',
    config: '',
    venueId: venues.value[0]?.id || 0,
    status: 'available',
    calibrationDate: '',
    changeReason: ''
  })
  dialogVisible.value = true
}

function openEdit(row: Device) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    deviceNo: row.deviceNo,
    name: row.name,
    model: row.model || '',
    config: row.config || '',
    venueId: row.venueId,
    status: row.status,
    calibrationDate: row.calibrationDate || '',
    changeReason: ''
  })
  dialogVisible.value = true
}

async function save() {
  if (!form.deviceNo.trim() || !form.name.trim() || !form.venueId) {
    ElMessage.warning('请把组合编号、名称、所属场地填完整')
    return
  }
  saving.value = true
  try {
    const payload = {
      deviceNo: form.deviceNo,
      name: form.name,
      model: form.model,
      config: form.config,
      venueId: form.venueId,
      status: form.status,
      calibrationDate: form.calibrationDate || null,
      changeReason: form.changeReason
    }
    if (isEdit.value) {
      await put(`/live/devices/${form.id}`, payload)
      ElMessage.success('设备组合已更新')
    } else {
      await post('/live/devices', payload)
      ElMessage.success('设备组合已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function remove(row: Device) {
  try {
    await ElMessageBox.confirm(`确定删除设备组合「${row.name}」吗？`, '删除确认', { type: 'warning' })
    await del(`/live/devices/${row.id}`)
    ElMessage.success('设备组合已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error((error as Error).message || '删除失败')
    }
  }
}

function changePage(next: number) {
  page.value = next
  load()
}

onMounted(async () => {
  await loadVenues()
  await load()
})
</script>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.config-text {
  margin-top: 3px;
  color: #8a94a6;
  font-size: 12px;
  line-height: 1.5;
}
</style>
