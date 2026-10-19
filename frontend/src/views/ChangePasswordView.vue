<template>
  <div class="change-page">
    <div class="change-card">
      <h1>修改初始密码</h1>
      <p class="hint">首次登录必须修改密码，修改后需要重新登录。</p>
      <el-form :model="form" label-position="top">
        <el-form-item label="旧密码">
          <el-input v-model="form.oldPassword" type="password" show-password size="large" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="form.newPassword" type="password" show-password size="large" placeholder="至少 8 位，包含字母和数字" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="form.confirmPassword" type="password" show-password size="large" />
        </el-form-item>
        <el-button type="primary" size="large" :loading="loading" class="submit" @click="submit">
          确认修改
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { post } from '../api/http'
import { clearAuth } from '../api/auth'

const router = useRouter()
const loading = ref(false)
const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

async function submit() {
  if (!form.oldPassword || !form.newPassword) {
    ElMessage.warning('请填写旧密码和新密码')
    return
  }
  if (form.newPassword.length < 8 || !/[A-Za-z]/.test(form.newPassword) || !/\d/.test(form.newPassword)) {
    ElMessage.warning('新密码至少 8 位，且包含字母和数字')
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  loading.value = true
  try {
    await post('/auth/change-password', {
      oldPassword: form.oldPassword,
      newPassword: form.newPassword
    })
    clearAuth()
    ElMessage.success('密码修改成功，请重新登录')
    await router.replace('/login')
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.change-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: #f4f7f2;
}

.change-card {
  width: 420px;
  background: #fff;
  padding: 32px;
  border-radius: 14px;
  box-shadow: 0 16px 42px rgba(23, 62, 35, 0.12);
}

h1 {
  margin: 0 0 6px;
  font-size: 21px;
}

.hint {
  color: #7a8699;
  font-size: 13px;
  margin: 0 0 20px;
}

.submit {
  width: 100%;
  background: #2f7d32;
  border-color: #2f7d32;
}
</style>
