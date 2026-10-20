<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="brand-mark">助</div>
        <div>
          <h1>助农直播管理系统</h1>
          <p>请选择角色并登录</p>
        </div>
      </div>

      <div class="role-list">
        <div
          v-for="role in roles"
          :key="role.code"
          class="role-item"
          :class="{ active: form.roleCode === role.code }"
          @click="form.roleCode = role.code"
        >
          <div class="role-name">{{ role.name }}</div>
          <div class="role-desc">{{ role.desc }}</div>
        </div>
      </div>

      <el-form :model="form" label-position="top" @keyup.enter="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" size="large" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password size="large" placeholder="请输入密码" />
        </el-form-item>
        <el-button type="primary" size="large" :loading="loading" class="login-button" @click="submit">
          登录
        </el-button>
      </el-form>

      <div class="login-tip">
        初始账号：admin / Admin@123，首次登录后需要修改密码。
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { post } from '../api/http'
import { saveAuth, type LoginResult } from '../api/auth'

const roles = [
  { code: 'system_admin', name: '系统管理员', desc: '账号、农户、场地、设备与预约审核' },
  { code: 'live_admin', name: '直播管理员', desc: '预约审核、排队与销售管理' },
  { code: 'farmer', name: '农户', desc: '预约直播、查看排队与通知' },
  { code: 'data_viewer', name: '数据统计员', desc: '统计查看与导出' }
]

const router = useRouter()
const loading = ref(false)
const form = reactive({
  roleCode: 'system_admin',
  username: 'admin',
  password: ''
})

function homeFor(roleCode: string): string {
  if (roleCode === 'farmer') return '/farmer/bookings'
  if (roleCode === 'live_admin') return '/bookings'
  if (roleCode === 'data_viewer') return '/todo?name=数据统计分析'
  return '/dashboard'
}

async function submit() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const result = await post<LoginResult>('/auth/login', {
      username: form.username.trim(),
      password: form.password,
      roleCode: form.roleCode
    })
    saveAuth(result.token, result.user)
    if (result.user.mustChangePassword) {
      ElMessage.info('首次登录，请先修改密码')
      await router.replace('/change-password')
    } else {
      await router.replace(homeFor(result.user.roleCode))
    }
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at 15% 20%, rgba(103, 194, 58, 0.22), transparent 30%),
    radial-gradient(circle at 85% 80%, rgba(64, 158, 255, 0.18), transparent 35%),
    #f4f7f2;
}

.login-card {
  width: 460px;
  padding: 30px 34px 24px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 18px 50px rgba(23, 62, 35, 0.14);
}

.brand {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-bottom: 20px;
}

.brand-mark {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: #2f7d32;
  color: #fff;
  font-size: 24px;
  font-weight: 700;
}

.brand h1 {
  margin: 0;
  font-size: 20px;
  color: #1f3b25;
}

.brand p {
  margin: 5px 0 0;
  color: #7a8699;
  font-size: 13px;
}

.role-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 20px;
}

.role-item {
  padding: 10px 14px;
  border: 1px solid #e4e9f0;
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s, transform 0.15s;
}

.role-item:hover {
  border-color: #a9c6ff;
  background: #f7faff;
}

.role-item.active {
  border-color: #3b6cff;
  background: #ecf2ff;
}

.role-name {
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
}

.role-item.active .role-name {
  color: #3b6cff;
}

.role-desc {
  font-size: 12px;
  color: #7a8699;
  margin-top: 2px;
}

.login-button {
  width: 100%;
  margin-top: 6px;
  background: #2f7d32;
  border-color: #2f7d32;
}

.login-tip {
  margin-top: 16px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f5f8f2;
  color: #63735f;
  font-size: 12px;
  line-height: 1.6;
}
</style>
