<template>
  <div>
    <h2 class="page-title">农户管理</h2>
    <p class="page-subtitle">直播管理员负责农户建档和资质审核；手机号列表默认脱敏，不存身份证和家庭住址。</p>

    <div class="page-card">
      <div class="page-toolbar">
        <el-input v-model="keyword" placeholder="搜索编号 / 姓名 / 县域 / 手机号" clearable style="width: 240px" @keyup.enter="load" />
        <RegionSelect v-model="filterCountyCode" style="width: 260px" @change="changeRegionFilter" />
        <el-tag v-if="filterLabel" class="filter-tag" type="info" closable @close="clearRegionFilter">
          {{ filterLabel }}
        </el-tag>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="openCreate">新增农户</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="farmerNo" label="农户编号" width="130" />
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column prop="county" label="县域" width="110" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="资质" width="110">
          <template #default="{ row }">
            <el-tag :type="qualificationTag(row.qualificationStatus)">
              {{ qualificationLabel(row.qualificationStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status === 'active' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="建档时间" width="170" />
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑农户' : '新增农户'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="农户编号">
          <el-input v-model="form.farmerNo" :disabled="isEdit" placeholder="如 NF2026004" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="所属区域" required>
          <RegionSelect v-model="form.countyCode" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="11 位手机号" />
        </el-form-item>
        <el-form-item label="资质状态">
          <el-select v-model="form.qualificationStatus" style="width: 100%">
            <el-option label="待审核" value="pending" />
            <el-option label="已认证" value="verified" />
            <el-option label="未通过" value="rejected" />
            <el-option label="停用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="active">正常</el-radio>
            <el-radio value="disabled">停用</el-radio>
          </el-radio-group>
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

interface Farmer {
  id: number
  farmerNo: string
  name: string
  county: string
  countyCode: string | null
  phone: string
  qualificationStatus: string
  status: string
  createdAt: string
}

const rows = ref<Farmer[]>([])
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
  farmerNo: '',
  name: '',
  countyCode: '',
  phone: '',
  qualificationStatus: 'pending',
  status: 'active'
})

function qualificationLabel(value: string) {
  return { pending: '待审核', verified: '已认证', rejected: '未通过', disabled: '停用' }[value] || value
}

function qualificationTag(value: string) {
  return { pending: 'warning', verified: 'success', rejected: 'danger', disabled: 'info' }[value] || 'info'
}

async function load() {
  loading.value = true
  try {
    const result = await get<PageResult<Farmer>>('/live/farmers', {
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
    farmerNo: '',
    name: '',
    countyCode: '',
    phone: '',
    qualificationStatus: 'pending',
    status: 'active'
  })
  dialogVisible.value = true
}

function openEdit(row: Farmer) {
  isEdit.value = true
  Object.assign(form, row)
  dialogVisible.value = true
}

async function save() {
  if (!form.farmerNo.trim() || !form.name.trim() || !form.phone.trim()) {
    ElMessage.warning('请把农户编号、姓名、手机号填完整')
    return
  }
  if (!form.countyCode) {
    ElMessage.warning('请选择完整的省 / 市 / 县')
    return
  }
  if (!/^1\d{10}$/.test(form.phone) && !form.phone.includes('****')) {
    ElMessage.warning('手机号必须是 11 位数字')
    return
  }
  saving.value = true
  try {
    const payload = {
      farmerNo: form.farmerNo,
      name: form.name,
      countyCode: form.countyCode,
      phone: form.phone,
      qualificationStatus: form.qualificationStatus,
      status: form.status
    }
    if (isEdit.value) {
      await put(`/live/farmers/${form.id}`, payload)
      ElMessage.success('农户已更新')
    } else {
      await post('/live/farmers', payload)
      ElMessage.success('农户已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function remove(row: Farmer) {
  try {
    await ElMessageBox.confirm(`确定删除农户「${row.name}」吗？`, '删除确认', { type: 'warning' })
    await del(`/live/farmers/${row.id}`)
    ElMessage.success('农户已删除')
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
