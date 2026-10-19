<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <div class="logo-mark">助</div>
        <div>
          <div class="logo-title">助农直播</div>
          <div class="logo-sub">管理系统</div>
        </div>
      </div>

      <el-menu :default-active="route.path" router class="menu">
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon>
          <span>工作台</span>
        </el-menu-item>
        <el-menu-item index="/accounts">
          <el-icon><User /></el-icon>
          <span>账号管理</span>
        </el-menu-item>
        <el-menu-item index="/farmers">
          <el-icon><Avatar /></el-icon>
          <span>农户管理</span>
        </el-menu-item>
        <el-menu-item index="/venues">
          <el-icon><OfficeBuilding /></el-icon>
          <span>场地管理</span>
        </el-menu-item>
        <el-menu-item index="/devices">
          <el-icon><Monitor /></el-icon>
          <span>设备管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">系统管理员</div>
        <div class="header-right">
          <span class="user-name">{{ user?.displayName || user?.username }}</span>
          <el-button text :icon="SwitchButton" @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DataBoard, User, Avatar, OfficeBuilding, Monitor, SwitchButton } from '@element-plus/icons-vue'
import { post } from '../api/http'
import { clearAuth, currentUser } from '../api/auth'

const route = useRoute()
const router = useRouter()
const user = currentUser()

async function logout() {
  try {
    await post('/auth/logout')
  } catch {
    // 即使接口失败，也清本地登录态
  }
  clearAuth()
  ElMessage.success('已退出登录')
  await router.replace('/login')
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
}

.aside {
  background: #173b24;
  color: #fff;
}

.logo {
  height: 76px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
}

.logo-mark {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  background: #67c23a;
  font-weight: 700;
}

.logo-title {
  font-size: 16px;
  font-weight: 600;
}

.logo-sub {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.55);
  margin-top: 2px;
}

.menu {
  border-right: none;
  background: transparent;
}

.menu :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.72);
}

.menu :deep(.el-menu-item.is-active) {
  color: #fff;
  background: rgba(103, 194, 58, 0.18);
}

.menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.06);
}

.header {
  background: #fff;
  border-bottom: 1px solid #eef1f4;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  color: #5c6b7a;
  font-size: 13px;
}

.main {
  padding: 22px;
}
</style>
