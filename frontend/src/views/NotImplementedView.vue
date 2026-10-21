<template>
  <div class="todo-page">
    <div class="page-card todo-card">
      <div class="todo-icon">
        <el-icon :size="44"><Tools /></el-icon>
      </div>
      <h2>{{ title }}</h2>
      <p>该功能暂未开放，敬请期待。</p>
      <el-button type="primary" @click="goBack">返回</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Tools } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const title = computed(() => (route.query.name as string) || '功能建设中')

function goBack() {
  router.back()
}
</script>

<style scoped>
.todo-page {
  min-height: 60vh;
  display: grid;
  place-items: center;
}

.todo-card {
  max-width: 420px;
  text-align: center;
  padding: 48px 32px;
}

.todo-icon {
  width: 76px;
  height: 76px;
  margin: 0 auto 18px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #2f7d32;
  background: #eef6ee;
}

.todo-card h2 {
  margin: 0 0 8px;
  font-size: 18px;
  color: #1f3b25;
}

.todo-card p {
  margin: 0 0 22px;
  font-size: 13px;
  color: #7a8699;
}
</style>
