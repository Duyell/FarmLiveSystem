<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="brand-mark">助</div>
        <div>
          <h1>助农直播管理系统</h1>
          <p>请输入账号和密码登录</p>
        </div>
      </div>

      <el-form :model="form" label-position="top" @keyup.enter="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" size="large" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password size="large" placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="验证码">
          <div class="captcha-row">
            <el-input v-model="form.captchaAnswer" size="large" placeholder="请输入计算结果" />
            <el-button link type="primary" :loading="captchaLoading" @click="loadCaptcha">
              {{ captchaQuestion || '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-button type="primary" size="large" :loading="loading" class="login-button" @click="submit">
          登录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { post } from '../api/http'
import { getCaptcha, saveAuth, type LoginResult } from '../api/auth'

const router = useRouter()
const loading = ref(false)
const captchaLoading = ref(false)
const captchaId = ref('')
const captchaQuestion = ref('')
const form = reactive({
  username: '',
  password: '',
  captchaAnswer: ''
})

async function loadCaptcha() {
  captchaLoading.value = true
  try {
    const result = await getCaptcha()
    captchaId.value = result.captchaId
    captchaQuestion.value = result.question
    form.captchaAnswer = ''
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    captchaLoading.value = false
  }
}

function homeFor(roleCode: string): string {
  if (roleCode === 'farmer') return '/farmer/bookings'
  if (roleCode === 'live_admin') return '/bookings'
  if (roleCode === 'data_viewer') return '/stats'
  return '/dashboard'
}

async function submit() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  if (!form.captchaAnswer.trim()) {
    ElMessage.warning('请输入验证码')
    return
  }
  loading.value = true
  try {
    const result = await post<LoginResult>('/auth/login', {
      username: form.username.trim(),
      password: form.password,
      captchaId: captchaId.value,
      captchaAnswer: form.captchaAnswer.trim()
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
    await loadCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(loadCaptcha)
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

.login-button {
  width: 100%;
  margin-top: 6px;
  background: #2f7d32;
  border-color: #2f7d32;
}

.captcha-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
</style>
