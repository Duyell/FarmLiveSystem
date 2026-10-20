<template>
  <div>
    <h2 class="page-title">销售录入</h2>
    <p class="page-subtitle">按已完成场次录入卖出的农产品数量；同一场次可按规格录入多条。</p>

    <div class="page-card">
      <div class="page-toolbar">
        <el-input v-model="keyword" placeholder="农产品 / 预约号 / 农户" clearable style="width: 250px" @keyup.enter="load" />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="openCreate">新增销售记录</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="bookingNo" label="预约号" width="180" />
        <el-table-column prop="farmerName" label="农户" width="100" />
        <el-table-column prop="venueName" label="场地" width="140" />
        <el-table-column prop="productName" label="农产品" width="120" />
        <el-table-column prop="spec" label="规格" width="110" />
        <el-table-column label="数量" width="110">
          <template #default="{ row }">{{ row.quantity }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column prop="startTime" label="场次时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑销售记录' : '新增销售记录'" width="520px">
      <el-form label-position="top">
        <el-form-item label="已完成场次">
          <el-select v-model="form.bookingId" filterable placeholder="选择已完成场次" style="width: 100%" :disabled="Boolean(form.id)">
            <el-option
              v-for="item in completedBookings"
              :key="item.id"
              :label="`${item.bookingNo} / ${item.farmerName} / ${item.startTime}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="农产品名称">
          <el-input v-model="form.productName" maxlength="80" />
        </el-form-item>
        <el-form-item label="规格">
          <el-input v-model="form.spec" maxlength="80" placeholder="如 500g/盒" />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="form.quantity" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="form.unit" maxlength="20" placeholder="盒 / 斤 / 箱" />
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
import { listLiveBookings, type Booking } from '../api/bookings'
import {
  createSales,
  deleteSales,
  listSales,
  updateSales,
  type SalesPayload,
  type SalesRecord
} from '../api/sales'

const rows = ref<SalesRecord[]>([])
const completedBookings = ref<Booking[]>([])
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const form = reactive<SalesPayload & { id?: number }>({
  id: undefined,
  bookingId: 0,
  productName: '',
  spec: '',
  quantity: 1,
  unit: '盒'
})

async function load() {
  loading.value = true
  try {
    const result = await listSales({ keyword: keyword.value, page: page.value, size: size.value })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function loadCompleted() {
  try {
    const result = await listLiveBookings({ status: 'completed', page: 1, size: 50 })
    completedBookings.value = result.items
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function changePage(value: number) {
  page.value = value
  load()
}

function resetForm() {
  form.id = undefined
  form.bookingId = 0
  form.productName = ''
  form.spec = ''
  form.quantity = 1
  form.unit = '盒'
}

async function openCreate() {
  resetForm()
  await loadCompleted()
  dialogVisible.value = true
}

function openEdit(row: SalesRecord) {
  form.id = row.id
  form.bookingId = row.bookingId
  form.productName = row.productName
  form.spec = row.spec ?? ''
  form.quantity = row.quantity
  form.unit = row.unit
  dialogVisible.value = true
}

async function save() {
  if (!form.bookingId || !form.productName.trim() || !form.unit.trim()) {
    ElMessage.warning('请填写场次、农产品和单位')
    return
  }
  saving.value = true
  try {
    const payload: SalesPayload = {
      bookingId: form.bookingId,
      productName: form.productName.trim(),
      spec: form.spec?.trim() || null,
      quantity: form.quantity,
      unit: form.unit.trim()
    }
    if (form.id) {
      await updateSales(form.id, payload)
    } else {
      await createSales(payload)
    }
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function remove(row: SalesRecord) {
  try {
    await ElMessageBox.confirm(`删除 ${row.productName} 的销售记录？`, '确认删除', { type: 'warning' })
    await deleteSales(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    if (error instanceof Error && error.message) ElMessage.error(error.message)
  }
}

onMounted(load)
</script>
