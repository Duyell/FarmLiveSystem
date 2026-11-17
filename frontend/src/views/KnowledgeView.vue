<template>
  <div>
    <h2 class="page-title">AI 知识库</h2>
    <p class="page-subtitle">
      上传直播预约规则、农产品溯源资料、常见问题等文档，系统自动解析分块并向量化，供 AI 助手检索引用。
      支持 txt / md / pdf / docx，单文件不超过 10MB。
    </p>

    <div class="page-card">
      <div class="page-toolbar">
        <div class="upload-wrap">
          <input ref="fileInput" type="file" accept=".txt,.md,.pdf,.docx" class="upload-input" @change="onFilePicked" />
          <el-button type="primary" :loading="uploading" @click="pickFile">
            {{ uploading ? '正在上传解析…' : '上传文档' }}
          </el-button>
        </div>
        <el-input
          v-model="uploadTitle"
          placeholder="文档标题（留空使用文件名）"
          style="width: 260px"
          maxlength="120"
        />
        <el-button @click="load">刷新</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="fileName" label="文件" min-width="160" />
        <el-table-column label="类型" width="70">
          <template #default="{ row }">{{ row.fileType.toUpperCase() }}</template>
        </el-table-column>
        <el-table-column label="大小" width="90">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="解析状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="parseMeta(row.parseStatus).type">
              {{ parseMeta(row.parseStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="分块" width="70" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              @change="(value: string | number | boolean) => toggle(row, Boolean(value))"
            />
          </template>
        </el-table-column>
        <el-table-column prop="uploadedByName" label="上传人" width="110" />
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="reparse(row)">重新解析</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  PARSE_STATUS,
  deleteKnowledgeDoc,
  listKnowledgeDocs,
  reparseKnowledgeDoc,
  toggleKnowledgeDoc,
  uploadKnowledgeDoc,
  type KnowledgeDoc
} from '../api/ai'

const rows = ref<KnowledgeDoc[]>([])
const loading = ref(false)
const uploading = ref(false)
const uploadTitle = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

function parseMeta(value: string) {
  return PARSE_STATUS[value] ?? { label: value, type: 'info' as const }
}

function formatSize(bytes: number) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

async function load() {
  loading.value = true
  try {
    rows.value = await listKnowledgeDocs()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

function pickFile() {
  fileInput.value?.click()
}

async function onFilePicked(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('文件不能超过 10MB')
    return
  }
  uploading.value = true
  try {
    const doc = await uploadKnowledgeDoc(file, uploadTitle.value)
    ElMessage.success(`文档「${doc.title}」上传并解析成功（${doc.chunkCount} 个分块）`)
    uploadTitle.value = ''
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    uploading.value = false
  }
}

async function toggle(row: KnowledgeDoc, enabled: boolean) {
  try {
    await toggleKnowledgeDoc(row.id, enabled)
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function reparse(row: KnowledgeDoc) {
  try {
    const doc = await reparseKnowledgeDoc(row.id)
    ElMessage.success(`已重新解析「${doc.title}」（${doc.chunkCount} 个分块）`)
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function remove(row: KnowledgeDoc) {
  try {
    await ElMessageBox.confirm(`确定删除文档「${row.title}」吗？其分块与向量将一并删除。`, '删除确认', {
      type: 'warning'
    })
    await deleteKnowledgeDoc(row.id)
    ElMessage.success('文档已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error((error as Error).message || '删除失败')
    }
  }
}

onMounted(load)
</script>

<style scoped>
.upload-wrap {
  position: relative;
  display: inline-block;
}

.upload-input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}
</style>
