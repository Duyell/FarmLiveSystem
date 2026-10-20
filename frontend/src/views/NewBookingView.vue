<template>
  <div>
    <h2 class="page-title">直播预约提交</h2>
    <p class="page-subtitle">提交直播预约信息，等待管理员审核；同一场地同一时段仅一场直播。</p>

    <div class="page-card form-card">
      <el-form :model="form" label-width="120px" :disabled="saving">
        <el-form-item label="农户">
          <el-input :model-value="farmerLabel" disabled />
        </el-form-item>
        <el-form-item label="所属县域" required>
          <RegionSelect v-model="form.countyCode" />
        </el-form-item>
        <el-form-item label="直播日期" required>
          <el-date-picker
            v-model="form.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择直播日期"
            :disabled-date="disablePastDate"
          />
        </el-form-item>
        <el-form-item label="直播时段" required>
          <div class="time-range">
            <el-time-select
              v-model="form.startTime"
              start="06:00"
              step="01:00"
              end="22:00"
              placeholder="开始时间"
            />
            <span class="time-sep">至</span>
            <el-time-select
              v-model="form.endTime"
              start="07:00"
              step="01:00"
              end="23:00"
              placeholder="结束时间"
            />
            <span class="time-hint">整点、不跨天，1-4 小时</span>
          </div>
        </el-form-item>
        <el-form-item label="直播主题" required>
          <el-input v-model="form.topic" placeholder="示例：XX草莓采摘专场" maxlength="120" />
        </el-form-item>
        <el-form-item label="主推农产品清单">
          <el-input
            v-model="form.productsText"
            type="textarea"
            :rows="3"
            placeholder="示例：草莓（5斤装）、圣女果（3斤装）&#10;多个产品用“、”分隔，需注明规格"
          />
        </el-form-item>
        <el-form-item label="直播场地" required>
          <el-select v-model="form.venueId" placeholder="选择场地" style="width: 100%" @change="onVenueChange">
            <el-option
              v-for="venue in venues"
              :key="venue.venueId"
              :label="`${venue.venueName}（${venue.openStart}-${venue.openEnd}）`"
              :value="venue.venueId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="直播设备" required>
          <el-select v-model="form.deviceId" placeholder="选择设备" style="width: 100%">
            <el-option
              v-for="device in devices"
              :key="device.deviceId"
              :label="`${device.name}（${device.deviceNo}）`"
              :value="device.deviceId"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="reset">重置</el-button>
          <el-button type="primary" :loading="saving" @click="submit">提交预约</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import RegionSelect from '../components/RegionSelect.vue'
import { currentUser } from '../api/auth'
import {
  listAvailableVenues,
  submitBooking,
  type AvailableDevice,
  type AvailableVenue
} from '../api/bookings'

const router = useRouter()
const user = currentUser()

const form = reactive({
  countyCode: '',
  date: '',
  startTime: '',
  endTime: '',
  topic: '',
  productsText: '',
  venueId: undefined as number | undefined,
  deviceId: undefined as number | undefined
})

const venues = ref<AvailableVenue[]>([])
const devices = ref<AvailableDevice[]>([])
const saving = ref(false)

const farmerLabel = computed(() => (user?.farmerName ? `${user.farmerName}（${user.username}）` : user?.username ?? ''))

function disablePastDate(date: Date) {
  return date.getTime() < Date.now() - 24 * 60 * 60 * 1000
}

watch(
  () => [form.countyCode, form.date, form.startTime, form.endTime],
  () => {
    form.venueId = undefined
    form.deviceId = undefined
    loadVenues()
  }
)

async function loadVenues() {
  if (!form.countyCode) {
    venues.value = []
    return
  }
  try {
    venues.value = await listAvailableVenues({
      countyCode: form.countyCode,
      date: form.date || undefined,
      startTime: form.startTime || undefined,
      endTime: form.endTime || undefined
    })
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function onVenueChange(venueId: number) {
  form.deviceId = undefined
  const venue = venues.value.find((item) => item.venueId === venueId)
  devices.value = venue ? venue.devices : []
}

function parseProducts(text: string) {
  const items = text
    .split(/[、，,\n]/)
    .map((part) => part.trim())
    .filter((part) => part.length > 0)
  return items.map((item) => {
    const matched = item.match(/^(.+?)（(.+)）$/)
    if (matched) return { productName: matched[1].trim(), spec: matched[2].trim() }
    return { productName: item, spec: null }
  })
}

async function submit() {
  if (!form.countyCode) {
    ElMessage.warning('请选择所属县域')
    return
  }
  if (!form.date || !form.startTime || !form.endTime) {
    ElMessage.warning('请选择完整的日期和时段')
    return
  }
  if (form.startTime >= form.endTime) {
    ElMessage.warning('开始时间必须早于结束时间')
    return
  }
  if (!form.topic.trim()) {
    ElMessage.warning('请填写直播主题')
    return
  }
  if (!form.venueId || !form.deviceId) {
    ElMessage.warning('请选择场地和设备')
    return
  }
  saving.value = true
  try {
    const booking = await submitBooking({
      venueId: form.venueId,
      deviceId: form.deviceId,
      startTime: `${form.date} ${form.startTime}:00`,
      endTime: `${form.date} ${form.endTime}:00`,
      topic: form.topic.trim(),
      products: parseProducts(form.productsText)
    })
    ElMessage.success(`预约已提交，单号 ${booking.bookingNo}，等待审核`)
    await router.replace('/farmer/bookings')
  } catch (error) {
    const message = (error as Error).message || ''
    if (message.includes('已被占用') || message.includes('已有预约')) {
      try {
        await ElMessageBox.confirm(`${message}。是否前往排队页面加入候补？`, '时段已满', {
          confirmButtonText: '去加入排队',
          cancelButtonText: '留在本页',
          type: 'warning'
        })
        await router.push('/farmer/queues')
      } catch {
        // 用户选择留在本页
      }
    } else {
      ElMessage.error(message)
    }
  } finally {
    saving.value = false
  }
}

function reset() {
  Object.assign(form, {
    countyCode: '',
    date: '',
    startTime: '',
    endTime: '',
    topic: '',
    productsText: '',
    venueId: undefined,
    deviceId: undefined
  })
  venues.value = []
  devices.value = []
}
</script>

<style scoped>
.form-card {
  max-width: 720px;
  padding-top: 10px;
}

.time-range {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.time-sep {
  color: #5e6d82;
}

.time-hint {
  font-size: 12px;
  color: #7a8699;
}
</style>
