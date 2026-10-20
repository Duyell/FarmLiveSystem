<template>
  <div>
    <h2 class="page-title">通知管理</h2>
    <p class="page-subtitle">查看全部发送记录并手动重试失败短信；维护五类事件的通知模板。</p>

    <div class="page-card">
      <el-tabs v-model="tab">
        <el-tab-pane label="发送记录" name="records">
          <div class="page-toolbar">
            <el-select v-model="filter.type" placeholder="全部类型" clearable style="width: 140px" @change="load">
              <el-option v-for="(label, key) in NOTIFICATION_TYPE" :key="key" :label="label" :value="key" />
            </el-select>
            <el-select v-model="filter.channel" placeholder="全部渠道" clearable style="width: 130px" @change="load">
              <el-option label="站内" value="in_app" />
              <el-option label="短信" value="sms" />
            </el-select>
            <el-select v-model="filter.status" placeholder="全部状态" clearable style="width: 140px" @change="load">
              <el-option v-for="(meta, key) in NOTIFICATION_STATUS" :key="key" :label="meta.label" :value="key" />
            </el-select>
            <el-button type="primary" @click="load">查询</el-button>
          </div>

          <el-table :data="rows" v-loading="loading" border stripe>
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ NOTIFICATION_TYPE[row.type] ?? row.type }}</template>
            </el-table-column>
            <el-table-column label="渠道" width="80">
              <template #default="{ row }">{{ row.channel === 'in_app' ? '站内' : '短信' }}</template>
            </el-table-column>
            <el-table-column prop="title" label="标题" width="140" />
            <el-table-column prop="content" label="内容" min-width="300" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="statusMeta(row.status).type">{{ statusMeta(row.status).label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="retryCount" label="重试" width="70" />
            <el-table-column prop="sentAt" label="发送时间" width="170" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.channel === 'sms' && row.status !== 'sent' && row.status !== 'pending'"
                  link
                  type="primary"
                  @click="retry(row)"
                >
                  重试
                </el-button>
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
        </el-tab-pane>

        <el-tab-pane label="通知模板" name="templates">
          <div class="page-toolbar">
            <el-select v-model="templateType" placeholder="全部类型" clearable style="width: 140px" @change="loadTemplates">
              <el-option v-for="(label, key) in NOTIFICATION_TYPE" :key="key" :label="label" :value="key" />
            </el-select>
            <el-button @click="openTemplate(null)">新增模板</el-button>
          </div>

          <el-table :data="templates" v-loading="tplLoading" border stripe>
            <el-table-column prop="code" label="编码" width="150" />
            <el-table-column prop="name" label="名称" width="110" />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ NOTIFICATION_TYPE[row.type] ?? row.type }}</template>
            </el-table-column>
            <el-table-column prop="titleTemplate" label="标题模板" width="150" />
            <el-table-column prop="contentTemplate" label="内容模板" min-width="300" />
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openTemplate(row)">编辑</el-button>
                <el-button link type="danger" @click="removeTemplate(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑模板' : '新增模板'" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="编码">
          <el-input v-model="form.code" :disabled="editing" placeholder="如 review_approved" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="(label, key) in NOTIFICATION_TYPE" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题模板">
          <el-input v-model="form.titleTemplate" />
        </el-form-item>
        <el-form-item label="内容模板">
          <el-input v-model="form.contentTemplate" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <div class="var-hint">
          可用变量：${{ '{farmerName}' }}、${{ '{topic}' }}、${{ '{startTime}' }}、${{ '{venueName}' }}、${{ '{bookingNo}' }}、${{ '{reviewComment}' }}、${{ '{recommendedTime}' }}、${{ '{deadline}' }}、${{ '{position}' }}、${{ '{deviceName}' }}；变量缺失时不会发送。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTemplate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  NOTIFICATION_STATUS,
  NOTIFICATION_TYPE,
  createTemplate,
  deleteTemplate,
  listAllNotifications,
  listTemplates,
  retryNotification,
  updateTemplate,
  type NotificationItem,
  type NotificationTemplate
} from '../api/notifications'

const tab = ref('records')
const rows = ref<NotificationItem[]>([])
const filter = reactive({ type: '', channel: '', status: '' })
const page = ref(1)
const size = ref(10)
const total = ref(0)
const loading = ref(false)

const templates = ref<NotificationTemplate[]>([])
const templateType = ref('')
const tplLoading = ref(false)
const dialogVisible = ref(false)
const editing = ref<number | null>(null)
const saving = ref(false)

const form = reactive({
  code: '',
  name: '',
  type: 'review',
  titleTemplate: '',
  contentTemplate: '',
  enabled: true
})

function statusMeta(value: string) {
  return NOTIFICATION_STATUS[value] ?? { label: value, type: 'info' as const }
}

async function load() {
  loading.value = true
  try {
    const result = await listAllNotifications({
      type: filter.type,
      channel: filter.channel,
      status: filter.status,
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

async function loadTemplates() {
  tplLoading.value = true
  try {
    templates.value = await listTemplates(templateType.value || undefined)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    tplLoading.value = false
  }
}

async function retry(row: NotificationItem) {
  try {
    await retryNotification(row.id)
    ElMessage.success('已发起重试')
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function openTemplate(row: NotificationTemplate | null) {
  editing.value = row ? row.id : null
  Object.assign(form, {
    code: row?.code ?? '',
    name: row?.name ?? '',
    type: row?.type ?? 'review',
    titleTemplate: row?.titleTemplate ?? '',
    contentTemplate: row?.contentTemplate ?? '',
    enabled: row?.enabled ?? true
  })
  dialogVisible.value = true
}

async function saveTemplate() {
  if (!form.code.trim() || !form.name.trim() || !form.titleTemplate.trim() || !form.contentTemplate.trim()) {
    ElMessage.warning('请把模板信息填完整')
    return
  }
  saving.value = true
  try {
    const payload = {
      code: form.code.trim(),
      name: form.name.trim(),
      type: form.type,
      titleTemplate: form.titleTemplate,
      contentTemplate: form.contentTemplate,
      enabled: form.enabled
    }
    if (editing.value) {
      await updateTemplate(editing.value, payload)
      ElMessage.success('模板已更新')
    } else {
      await createTemplate(payload)
      ElMessage.success('模板已创建')
    }
    dialogVisible.value = false
    await loadTemplates()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function removeTemplate(row: NotificationTemplate) {
  try {
    await ElMessageBox.confirm(`确定删除模板「${row.name}」吗？`, '删除确认', { type: 'warning' })
    await deleteTemplate(row.id)
    ElMessage.success('模板已删除')
    await loadTemplates()
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

onMounted(() => {
  load()
  loadTemplates()
})
</script>

<style scoped>
.page-toolbar {
  gap: 10px;
  margin-bottom: 14px;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.var-hint {
  font-size: 12px;
  color: #7a8699;
  background: #f5f8f2;
  border-radius: 8px;
  padding: 8px 12px;
  margin-left: 90px;
}
</style>
