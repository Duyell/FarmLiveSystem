<template>
  <el-cascader
    v-loading="loading"
    class="region-cascader"
    :model-value="modelValue || undefined"
    :options="options"
    :props="cascaderProps"
    placeholder="请选择省 / 市 / 县"
    clearable
    style="width: 100%"
    @change="onChange"
  />
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { loadRegionTree, type RegionOption } from '../api/regions'

defineProps<{ modelValue: string | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const cascaderProps = { value: 'code', label: 'name', emitPath: false }
const options = ref<RegionOption[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    options.value = await loadRegionTree()
  } catch (error) {
    ElMessage.error((error as Error).message || '区域数据加载失败')
  } finally {
    loading.value = false
  }
}

function onChange(value: string | null) {
  emit('update:modelValue', value || '')
}

onMounted(load)
</script>

<style scoped>
.region-cascader :deep(.el-cascader-node:hover) {
  background: #ecf2ff;
  color: #3b6cff;
}

.region-cascader :deep(.el-cascader-node.is-active) {
  background: #ecf2ff;
  color: #3b6cff;
  font-weight: 600;
}

.region-cascader :deep(.el-input__inner) {
  transition: border-color 0.2s;
}

.region-cascader :deep(.el-input__inner:focus) {
  border-color: #3b6cff;
}
</style>
