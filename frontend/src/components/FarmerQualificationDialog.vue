<template>
  <el-dialog
    :model-value="modelValue"
    :title="`资质材料：${farmerName}`"
    width="680px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="qualification-toolbar">
      <el-upload
        :show-file-list="false"
        accept=".pdf,.jpg,.jpeg,.png,.docx"
        :http-request="upload"
      >
        <el-button type="primary" :loading="uploading">上传材料</el-button>
      </el-upload>
      <span class="muted">支持 PDF、JPG、PNG、DOCX，单文件不超过 10MB</span>
    </div>

    <el-table :data="rows" v-loading="loading" border size="small">
      <el-table-column prop="originalName" label="文件" min-width="200" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMeta(row.status).type">{{ statusMeta(row.status).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="uploaderName" label="上传人" width="110" />
      <el-table-column prop="createdAt" label="上传时间" width="165" />
      <el-table-column prop="reviewComment" label="审核意见" min-width="140" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="downloadFile(row)">查看</el-button>
          <template v-if="row.status === 'pending'">
            <el-button link type="success" @click="review(row, 'approve')">通过</el-button>
            <el-button link type="danger" @click="review(row, 'reject')">驳回</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && rows.length === 0" description="还没有上传资质材料" />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import {
  QUALIFICATION_STATUS,
  downloadQualification,
  listQualifications,
  reviewQualification,
  uploadQualification,
  type Qualification
} from '../api/qualifications'

const props = defineProps<{
  modelValue: boolean
  farmerId: number
  farmerName: string
}>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const rows = ref<Qualification[]>([])
const loading = ref(false)
const uploading = ref(false)

function statusMeta(value: string) {
  return QUALIFICATION_STATUS[value] ?? { label: value, type: 'warning' as const }
}

async function load() {
  if (!props.farmerId) return
  loading.value = true
  try {
    rows.value = await listQualifications(props.farmerId)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function upload(options: UploadRequestOptions) {
  uploading.value = true
  try {
    await uploadQualification(props.farmerId, options.file)
    ElMessage.success('材料已上传，等待审核')
    options.onSuccess({})
    await load()
  } catch (error) {
    const message = (error as Error).message
    ElMessage.error(message)
  } finally {
    uploading.value = false
  }
}

async function downloadFile(row: Qualification) {
  try {
    const blob = await downloadQualification(row.id)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = row.originalName
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function review(row: Qualification, result: 'approve' | 'reject') {
  try {
    let comment = ''
    if (result === 'reject') {
      const { value } = await ElMessageBox.prompt('请输入驳回原因（至少 5 个字）', '驳投资质材料', {
        confirmButtonText: '驳回',
        cancelButtonText: '取消',
        inputPattern: /^.{5,}$/,
        inputErrorMessage: '至少 5 个字'
      })
      comment = value
    } else {
      await ElMessageBox.confirm('确认通过这份资质材料？', '确认', { type: 'warning' })
    }
    await reviewQualification(row.id, { result, comment })
    ElMessage.success('审核完成')
    await load()
  } catch (error) {
    if (error instanceof Error && error.message) ElMessage.error(error.message)
  }
}

watch(
  () => [props.modelValue, props.farmerId],
  ([visible]) => {
    if (visible) load()
  }
)
</script>

<style scoped>
.qualification-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.muted {
  color: #8a94a6;
  font-size: 12px;
}
</style>
