<template>
  <el-container class="layout">
    <el-aside width="232px" class="aside">
      <div class="brand">
        <div class="brand-mark"><el-icon :size="18"><LocationFilled /></el-icon></div>
        <span class="brand-title">助农直播管理系统</span>
      </div>

      <div class="user-card">
        <div class="user-avatar">{{ avatarText }}</div>
        <div class="user-meta">
          <div class="user-name">{{ roleLabel }}：{{ user?.displayName || user?.username }}</div>
          <div class="user-role">{{ user?.roleName }}权限</div>
        </div>
      </div>

      <div class="menu-group">系统功能</div>
      <el-menu :default-active="route.path" router class="menu">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>

      <div class="aside-footer">
        <el-button class="logout-button" :icon="SwitchButton" @click="logout">退出登录</el-button>
      </div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div>
          <div class="header-title">{{ pageTitle }}</div>
          <div class="header-sub">{{ pageSubtitle }}</div>
        </div>
        <el-tooltip content="通知中心" placement="bottom">
          <el-badge
            :value="unread"
            :hidden="unread === 0"
            :max="99"
            class="bell"
            type="danger"
            @click="goNotifications"
          >
            <el-icon :size="18"><Bell /></el-icon>
          </el-badge>
        </el-tooltip>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Bell,
  Calendar,
  DataBoard,
  Document,
  Files,
  List,
  LocationFilled,
  Lock,
  Money,
  QuestionFilled,
  Refresh,
  Sort,
  SwitchButton,
  TrendCharts
} from '@element-plus/icons-vue'
import { post } from '../api/http'
import { clearAuth, currentUser } from '../api/auth'
import { getUnreadCount } from '../api/notifications'

interface MenuItem {
  path: string
  title: string
  icon: unknown
}

const menuByRole: Record<string, MenuItem[]> = {
  system_admin: [
    { path: '/dashboard', title: '工作台', icon: DataBoard },
    { path: '/bookings', title: '直播预约管理', icon: Calendar },
    { path: '/live/adjustments', title: '预约调整审批', icon: Refresh },
    { path: '/basic-info', title: '基础信息管理', icon: Files },
    { path: '/live/queues', title: '直播排队与候补', icon: Sort },
    { path: '/live/sales', title: '销售录入', icon: Money },
    { path: '/stats', title: '数据统计', icon: TrendCharts },
    { path: '/live/notifications', title: '通知管理', icon: Bell },
    { path: '/admin/logs', title: '操作日志', icon: Document },
    { path: '/accounts', title: '权限管理', icon: Lock },
    { path: '/todo?name=帮助中心', title: '帮助中心', icon: QuestionFilled }
  ],
  live_admin: [
    { path: '/bookings', title: '直播预约管理', icon: Calendar },
    { path: '/live/adjustments', title: '预约调整审批', icon: Refresh },
    { path: '/basic-info', title: '基础信息管理', icon: Files },
    { path: '/live/queues', title: '直播排队与候补', icon: Sort },
    { path: '/live/sales', title: '销售录入', icon: Money },
    { path: '/stats', title: '数据统计', icon: TrendCharts },
    { path: '/live/notifications', title: '通知管理', icon: Bell },
    { path: '/todo?name=帮助中心', title: '帮助中心', icon: QuestionFilled }
  ],
  farmer: [
    { path: '/farmer/bookings/new', title: '预约直播', icon: Calendar },
    { path: '/farmer/bookings', title: '我的预约', icon: List },
    { path: '/farmer/queues', title: '直播排队与候补', icon: Sort },
    { path: '/notifications', title: '通知管理', icon: Bell },
    { path: '/todo?name=数据统计分析', title: '数据统计分析', icon: TrendCharts },
    { path: '/todo?name=帮助中心', title: '帮助中心', icon: QuestionFilled }
  ],
  data_viewer: [
    { path: '/stats', title: '数据统计', icon: TrendCharts },
    { path: '/notifications', title: '通知管理', icon: Bell },
    { path: '/todo?name=帮助中心', title: '帮助中心', icon: QuestionFilled }
  ]
}

const pageTitleByPath: Record<string, string> = {
  '/dashboard': '工作台',
  '/bookings': '直播预约管理',
  '/live/adjustments': '预约调整审批',
  '/basic-info': '基础信息管理',
  '/accounts': '权限管理',
  '/farmer/bookings': '我的预约',
  '/farmer/bookings/new': '直播预约提交',
  '/farmer/queues': '直播排队与候补',
  '/live/queues': '直播排队与候补',
  '/live/sales': '销售录入',
  '/stats': '数据统计',
  '/admin/logs': '操作日志',
  '/notifications': '我的通知',
  '/live/notifications': '通知管理'
}

const pageSubtitleByPath: Record<string, string> = {
  '/dashboard': '汇总账号、农户、场地、设备和预约数据',
  '/bookings': '审核农户提交的直播预约',
  '/live/adjustments': '审批农户提交的改期申请',
  '/basic-info': '管理农户信息、直播设备和场地等基础数据',
  '/accounts': '管理系统账号和角色权限',
  '/farmer/bookings': '查看和管理您的直播预约状态',
  '/farmer/bookings/new': '提交直播预约信息，等待管理员审核',
  '/farmer/queues': '时段已满可加入排队，场地释放后自动升级候补',
  '/live/queues': '查看各场地时段排队情况，必要时关闭队列',
  '/live/sales': '按已完成场次录入卖出的农产品数量',
  '/stats': '查看四项指标、完成场次明细和趋势',
  '/admin/logs': '查询和导出关键操作日志',
  '/notifications': '发给您的事件通知（每个事件一条，短信状态在行内显示）',
  '/live/notifications': '查看发送记录、重试失败短信并维护通知模板'
}

const route = useRoute()
const router = useRouter()
const user = currentUser()
const unread = ref(0)

async function loadUnread() {
  if (!user) return
  try {
    unread.value = await getUnreadCount()
  } catch {
    // 未读数获取失败不阻塞页面
  }
}

function goNotifications() {
  router.push('/notifications')
}

watch(() => route.path, loadUnread)
onMounted(loadUnread)

const menus = computed<MenuItem[]>(() => menuByRole[user?.roleCode ?? ''] ?? [])
const avatarText = computed(() => (user?.displayName || user?.username || '?').slice(0, 1))
const roleLabel = computed(() => (user?.roleCode === 'system_admin' ? '管理员' : user?.roleName))
const pageTitle = computed(() => pageTitleByPath[route.path] ?? '功能建设中')
const pageSubtitle = computed(() => pageSubtitleByPath[route.path] ?? '')

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
  background: linear-gradient(180deg, #2f7d32 0%, #245f27 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  padding: 0 14px;
}

.brand {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-mark {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.18);
}

.brand-title {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.12);
  margin-bottom: 14px;
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.22);
}

.user-meta {
  min-width: 0;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.65);
  margin-top: 2px;
}

.menu-group {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.55);
  padding: 4px 8px 8px;
}

.menu {
  border-right: none;
  background: transparent;
  flex: 1;
}

.menu :deep(.el-menu-item) {
  height: 44px;
  border-radius: 8px;
  margin-bottom: 4px;
  color: rgba(255, 255, 255, 0.78);
  transition: background 0.2s;
}

.menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}

.menu :deep(.el-menu-item.is-active) {
  color: #1f3b25;
  background: #eaf4ea;
  font-weight: 600;
}

.aside-footer {
  padding: 12px 0 16px;
}

.logout-button {
  width: 100%;
  color: #fff;
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 255, 255, 0.25);
}

.logout-button:hover {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  border-color: rgba(255, 255, 255, 0.35);
}

.header {
  height: 68px;
  background: #fff;
  border-bottom: 1px solid #eef1f4;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f3b25;
}

.header-sub {
  font-size: 12px;
  color: #7a8699;
  margin-top: 2px;
}

.bell {
  cursor: pointer;
  color: #5c6b7a;
  transition: color 0.2s;
}

.bell:hover {
  color: #2f7d32;
}

.main {
  padding: 22px;
  background: #f5f7fa;
}

.main :deep(.page-title),
.main :deep(.page-subtitle) {
  display: none;
}

@media (max-width: 768px) {
  .layout {
    flex-direction: column;
  }

  .aside {
    width: 100% !important;
    padding: 0 10px 8px;
  }

  .brand {
    height: 52px;
  }

  .user-card,
  .menu-group {
    display: none;
  }

  .menu {
    display: flex;
    flex: none;
    overflow-x: auto;
    padding-bottom: 4px;
  }

  .menu :deep(.el-menu-item) {
    flex: 0 0 auto;
    height: 38px;
    padding: 0 12px;
    margin: 0 4px 0 0;
    font-size: 13px;
  }

  .aside-footer {
    padding: 4px 0;
  }

  .logout-button {
    width: auto;
    height: 30px;
    padding: 0 12px;
    font-size: 12px;
  }

  .header {
    height: auto;
    min-height: 58px;
    padding: 8px 12px;
    gap: 8px;
  }

  .header-title {
    font-size: 16px;
  }

  .header-sub {
    font-size: 11px;
  }

  .main {
    padding: 12px;
  }
}
</style>
