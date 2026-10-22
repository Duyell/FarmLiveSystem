<template>
  <div>
    <el-tabs v-model="activeTab" class="basic-tabs">
      <el-tab-pane label="农户信息管理" name="farmers">
        <FarmersView />
      </el-tab-pane>
      <el-tab-pane label="直播场地管理" name="venues">
        <VenuesView />
      </el-tab-pane>
      <el-tab-pane label="直播设备组合" name="devices">
        <DevicesView />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import FarmersView from './FarmersView.vue'
import VenuesView from './VenuesView.vue'
import DevicesView from './DevicesView.vue'

const activeTab = ref('farmers')
</script>

<style scoped>
.basic-tabs {
  background: #fff;
  border-radius: 12px;
  padding: 4px 18px 12px;
  box-shadow: 0 1px 3px rgba(31, 59, 37, 0.06);
}

:deep(.page-title),
:deep(.page-subtitle) {
  display: none;
}
</style>
