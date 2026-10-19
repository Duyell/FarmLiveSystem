<template>
  <div>
    <h2 class="page-title">账号管理</h2>
    <p class="page-subtitle">系统管理员维护登录账号、角色和状态；农户账号必须关联农户。</p>

    <div class="page-card">
      <div class="page-toolbar">
        <el-input v-model="keyword" placeholder="搜索用户名 / 显示名" clearable style="width: 240px" @keyup.enter="load" />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="openCreate">新增账号</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="displayName" label="显示名" width="140" />
        <el-table-column prop="roleName" label="角色" width="120" />
        <el-table-column label="关联农户" width="130">
          <template #default="{ row }">{{ row.farmerName || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status === 'active' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="首次改密" width="100">
          <template #default="{ row }">{{ row.mustChangePassword ? '待修改' : '已修改' }}</template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="resetPassword(row)">重置密码</el-button>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑账号' : '新增账号'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="isEdit" placeholder="3-30 位字母、数字或下划线" />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="初始密码">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 8 位，含字母和数字" />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleId" style="width: 100%" @change="onRoleChange">
            <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedRoleCode === 'farmer'" label="关联农户">
          <el-select v-model="form.farmerId" filterable clearable placeholder="请选择农户" style="width: 100%">
            <el-option
              v-for="farmer in farmers"
              :key="farmer.id"
              :label="`${farmer.farmerNo} · ${farmer.name}`"
              :value="farmer.id"
            />
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

interface Role {
  id: number
  code: string
  name: string
}

interface Farmer {
  id: number
  farmerNo: string
  name: string
}

interface Account {
  id: number
  username: string
  displayName: string
  roleId: number
  roleCode: string
  roleName: string
  farmerId: number | null
  farmerName: string | null
  status: string
  mustChangePassword: boolean
  lastLoginAt: string | null
  createdAt: string
}

const rows = ref<Account[]>([])
const roles = ref<Role[]>([])
const farmers = ref<Farmer[]>([])
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
  username: '',
  password: '',
  displayName: '',
  roleId: 2,
  farmerId: null as number | null,
  status: 'active'
})

const selectedRoleCode = computed(() => roles.value.find((role) => role.id === form.roleId)?.code || '')

async function load() {
  loading.value = true
  try {
    const result = await get<PageResult<Account>>('/admin/accounts', {
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

async function loadMeta() {
  try {
    roles.value = await get<Role[]>('/admin/roles')
    const result = await get<PageResult<Farmer>>('/admin/farmers', { page: 1, size: 100 })
    farmers.value = result.items
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, {
    id: 0,
    username: '',
    password: '',
    displayName: '',
    roleId: 2,
    farmerId: null,
    status: 'active'
  })
  dialogVisible.value = true
}

function openEdit(row: Account) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    username: row.username,
    password: '',
    displayName: row.displayName,
    roleId: row.roleId,
    farmerId: row.farmerId,
    status: row.status
  })
  dialogVisible.value = true
}

function onRoleChange() {
  if (selectedRoleCode.value !== 'farmer') {
    form.farmerId = null
  }
}

async function save() {
  if (!form.displayName.trim() || !form.roleId) {
    ElMessage.warning('请填写显示名和角色')
    return
  }
  if (!isEdit.value && !form.password) {
    ElMessage.warning('请填写初始密码')
    return
  }
  if (selectedRoleCode.value === 'farmer' && !form.farmerId) {
    ElMessage.warning('农户角色必须选择关联农户')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await put(`/admin/accounts/${form.id}`, {
        displayName: form.displayName,
        roleId: form.roleId,
        farmerId: form.farmerId,
        status: form.status
      })
      ElMessage.success('账号已更新')
    } else {
      await post('/admin/accounts', {
        username: form.username,
        password: form.password,
        displayName: form.displayName,
        roleId: form.roleId,
        farmerId: form.farmerId,
        status: form.status
      })
      ElMessage.success('账号已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function resetPassword(row: Account) {
  try {
    const result = await ElMessageBox.prompt(`为账号「${row.username}」设置新密码`, '重置密码', {
      inputType: 'password',
      inputPattern: /^(?=.*[A-Za-z])(?=.*\d).{8,}$/,
      inputErrorMessage: '至少 8 位，且包含字母和数字'
    })
    await post(`/admin/accounts/${row.id}/reset-password`, { newPassword: result.value })
    ElMessage.success('密码已重置，账号下次登录需要改密')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error((error as Error).message || '重置失败')
    }
  }
}

async function remove(row: Account) {
  try {
    await ElMessageBox.confirm(`确定删除账号「${row.username}」吗？`, '删除确认', { type: 'warning' })
    await del(`/admin/accounts/${row.id}`)
    ElMessage.success('账号已删除')
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
  await loadMeta()
  await load()
})
</script>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
