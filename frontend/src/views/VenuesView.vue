<template>
  <div>
    <h2 class="page-title">场地管理</h2>
    <p class="page-subtitle">场地是主资源，同一场地同一时段只能做一场直播；设备必须归属场地。</p>

    <div class="page-card">
      <div class="page-toolbar">
        <el-input v-model="keyword" placeholder="搜索场地编号 / 名称 / 位置" clearable style="width: 220px" @keyup.enter="load" />
        <RegionSelect v-model="filterCountyCode" style="width: 260px" @change="changeRegionFilter" />
        <el-tag v-if="filterLabel" class="filter-tag" type="info" closable @close="clearRegionFilter">
          {{ filterLabel }}
        </el-tag>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="openCreate">新增场地</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="venueNo" label="场地编号" width="120" />
        <el-table-column prop="name" label="场地名称" width="150" />
        <el-table-column prop="county" label="所属区域" width="130" />
        <el-table-column prop="location" label="位置" min-width="180" />
        <el-table-column prop="capacity" label="容纳人数" width="100" />
        <el-table-column label="开放时段" width="130">
          <template #default="{ row }">{{ row.openStart }} - {{ row.openEnd }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑场地' : '新增场地'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="场地编号">
          <el-input v-model="form.venueNo" :disabled="isEdit" placeholder="如 C003" />
        </el-form-item>
        <el-form-item label="场地名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="位置">
          <el-input v-model="form.location" />
        </el-form-item>
        <el-form-item label="所属区域" required>
          <RegionSelect v-model="form.countyCode" />
        </el-form-item>
        <el-form-item label="容纳人数">
          <el-input-number v-model="form.capacity" :min="1" :max="1000" />
        </el-form-item>
        <el-form-item label="开放开始">
          <el-time-picker v-model="form.openStart" format="HH:mm" value-format="HH:mm" placeholder="08:00" />
        </el-form-item>
        <el-form-item label="开放结束">
          <el-time-picker v-model="form.openEnd" format="HH:mm" value-format="HH:mm" placeholder="20:00" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="可用" value="available" />
            <el-option label="维修" value="maintenance" />
            <el-option label="停用" value="disabled" />
          </el-select>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { del, get, post, put, type PageResult } from '../api/http'
import { findRegionPath } from '../api/regions'
import RegionSelect from '../components/RegionSelect.vue'

interface Venue {
  id: number
  venueNo: string
  name: string
  location: string
  countyCode: string | null
  capacity: number
  openStart: string
  openEnd: string
  status: string
  createdAt: string
}

const rows = ref<Venue[]>([])
const keyword = ref('')
const filterCountyCode = ref('')
const filterLabel = computed(() => {
  const path = findRegionPath(filterCountyCode.value)
  return path ? path.map((item) => item.name).join(' / ') : ''
})
const page = ref(1)
const size = ref(10)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)

const form = reactive({
  id: 0,
  venueNo: '',
  name: '',
  location: '',
  countyCode: '',
  capacity: 10,
  openStart: '08:00',
  openEnd: '20:00',
  status: 'available'
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
    const result = await get<PageResult<Venue>>('/live/venues', {
      keyword: keyword.value,
      countyCode: filterCountyCode.value,
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

function changeRegionFilter() {
  page.value = 1
  load()
}

function clearRegionFilter() {
  filterCountyCode.value = ''
  page.value = 1
  load()
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, {
    id: 0,
    venueNo: '',
    name: '',
    location: '',
    countyCode: '',
    capacity: 10,
    openStart: '08:00',
    openEnd: '20:00',
    status: 'available'
  })
  dialogVisible.value = true
}

function openEdit(row: Venue) {
  isEdit.value = true
  Object.assign(form, row)
  dialogVisible.value = true
}

async function save() {
  if (!form.venueNo.trim() || !form.name.trim() || !form.location.trim()) {
    ElMessage.warning('请把场地编号、名称、位置填完整')
    return
  }
  if (!form.countyCode) {
    ElMessage.warning('请选择完整的省 / 市 / 县')
    return
  }
  if (!form.openStart || !form.openEnd || form.openStart >= form.openEnd) {
    ElMessage.warning('开放开始时间必须早于结束时间')
    return
  }
  saving.value = true
  try {
    const payload = {
      venueNo: form.venueNo,
      name: form.name,
      location: form.location,
      countyCode: form.countyCode,
      capacity: form.capacity,
      openStart: form.openStart,
      openEnd: form.openEnd,
      status: form.status
    }
    if (isEdit.value) {
      await put(`/live/venues/${form.id}`, payload)
      ElMessage.success('场地已更新')
    } else {
      await post('/live/venues', payload)
      ElMessage.success('场地已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function remove(row: Venue) {
  try {
    await ElMessageBox.confirm(`确定删除场地「${row.name}」吗？`, '删除确认', { type: 'warning' })
    await del(`/live/venues/${row.id}`)
    ElMessage.success('场地已删除')
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

onMounted(load)
</script>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.filter-tag {
  height: 32px;
  padding: 0 10px;
  line-height: 30px;
}
</style>
