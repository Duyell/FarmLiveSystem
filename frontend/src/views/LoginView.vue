<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="brand-mark">助</div>
        <div>
          <h1>助农直播管理系统</h1>
          <p>管理端 · 系统管理员</p>
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

const router = useRouter()
const loading = ref(false)
const form = reactive({
  username: 'admin',
  password: ''
})

async function submit() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const result = await post<LoginResult>('/auth/login', {
      username: form.username.trim(),
      password: form.password
    })
    saveAuth(result.token, result.user)
    if (result.user.mustChangePassword) {
      ElMessage.info('首次登录，请先修改密码')
      await router.replace('/change-password')
    } else {
      await router.replace('/dashboard')
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
  width: 420px;
  padding: 34px 34px 26px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 18px 50px rgba(23, 62, 35, 0.14);
}

.brand {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-bottom: 28px;
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
  font-size: 21px;
  color: #1f3b25;
}

.brand p {
  margin: 5px 0 0;
  color: #7a8699;
  font-size: 13px;
}

.login-button {
  width: 100%;
  margin-top: 6px;
  background: #2f7d32;
  border-color: #2f7d32;
}

.login-tip {
  margin-top: 18px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f5f8f2;
  color: #63735f;
  font-size: 12px;
  line-height: 1.6;
}
</style>
