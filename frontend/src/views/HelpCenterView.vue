<template>
  <div>
    <h2 class="page-title">帮助中心</h2>
    <p class="page-subtitle">按角色查看操作指引与常见问题；更细的规则问题可以直接问 AI 助手。</p>

    <el-alert
      class="assistant-tip"
      type="success"
      :closable="false"
      title="有疑问？试试 AI 助手"
      description="可咨询预约时段、排队规则、农产品溯源、设备校准等问题；AI 只解答咨询，不会代替您操作预约。"
    >
      <template #default>
        <div class="assistant-row">
          <span>可咨询预约时段、排队规则、农产品溯源、设备校准等问题；AI 只解答咨询，不会代替您操作预约。</span>
          <el-button type="primary" link @click="$router.push('/assistant')">去咨询 →</el-button>
        </div>
      </template>
    </el-alert>

    <div class="page-card help-card">
      <h3 class="block-title">通用说明</h3>
      <el-collapse>
        <el-collapse-item title="账号安全与首次登录" name="account">
          <p>首次登录必须修改初始密码，修改成功后直接进入主界面，无需重新登录。忘记密码请联系系统管理员重置。</p>
          <p>查看农户完整手机号属于敏感操作，需要输入当前账号密码二次验证，验证通过后令牌 5 分钟内有效且仅能使用一次，查看行为会记录操作日志（含来源 IP）。</p>
        </el-collapse-item>
        <el-collapse-item title="通知与短信" name="notify">
          <p>系统会在预约审核结果、排队顺位变化、候补确认、开播前 1 小时、设备故障预警、设备校准到期时自动发送通知。</p>
          <p>通知支持系统内消息与短信双渠道，设备故障预警优先短信；发送失败会自动重试，也可在通知管理页手动重试。</p>
        </el-collapse-item>
      </el-collapse>
    </div>

    <div v-if="isFarmer" class="page-card help-card">
      <h3 class="block-title">农户主播</h3>
      <el-collapse>
        <el-collapse-item title="如何提交直播预约？" name="booking">
          <p>进入「直播预约」，选择县域、日期、时段、主题和主推农产品后提交。时段必须是整点、单场 1-4 小时、不跨天，最早提前 2 小时、最多提前 30 天预约。</p>
          <p><strong>直播时段必须完全落在所选场地的开放时段内</strong>（例如场地开放 08:00-20:00，则预约结束时间不能晚于 20:00）。选场地后若时段越界，系统会清空时段并提示重新选择。</p>
          <p>若目标时段场地或设备已被占用，系统会提示是否进入排队，同意后生成排队号，格式如 Q-农户ID-日期-序号。</p>
        </el-collapse-item>
        <el-collapse-item title="审核、调整与取消" name="review">
          <p>预约提交后由直播管理员审核，审核结果实时通知。驳回时会注明原因并给出推荐预约时间。</p>
          <p>开播前 12 小时以上可申请调整时段或场地设备，需管理员审批后生效；开播前 24 小时以上可取消预约，取消必须填写原因。</p>
        </el-collapse-item>
        <el-collapse-item title="排队与候补" name="queue">
          <p>排队按申请时间排序，可在「直播排队与候补」查看当前位置。前序农户取消时顺位自动前移并通知。</p>
          <p>收到候补通知后需在规定时间内选择确认或放弃；确认后又取消的，3 天内不能再加入该场地排队。</p>
        </el-collapse-item>
        <el-collapse-item title="查看我的数据" name="stats">
          <p>「我的数据」展示预约场次、已完成场次、卖出数量与明细。销售数据由直播管理员在直播完成后录入。</p>
        </el-collapse-item>
      </el-collapse>
    </div>

    <div v-if="isAdmin" class="page-card help-card">
      <h3 class="block-title">直播管理员</h3>
      <el-collapse>
        <el-collapse-item title="预约审核与推荐时间" name="live-review">
          <p>支持按预约时间、县域、农产品类型筛选审核列表。审核通过后锁定场地设备资源；驳回需填写至少 5 个字的原因，推荐时间也必须落在场地开放时段内且资源空闲。</p>
        </el-collapse-item>
        <el-collapse-item title="资源与设备维护" name="resource">
          <p>手动更新农户资质、场地开放时段、设备状态时必须填写修改原因，否则无法保存；每次更新都会记录更新时间与原因。</p>
          <p>设备校准日期前 7 天自动向管理员发送校准提醒；设备故障可提交报修并记录故障部位与描述，修复后回填处理说明。</p>
        </el-collapse-item>
        <el-collapse-item title="排队与候补管理" name="live-queue">
          <p>释放空闲资源或前序取消时，系统按排队顺序触发候补通知，管理员可查看每次候补操作的选择结果和时间。</p>
        </el-collapse-item>
        <el-collapse-item title="销售录入与统计" name="live-stats">
          <p>只有已完成的场次可以录入销售记录（产品名、规格、数量、单位）。统计支持按时间、县域、农产品类型筛选，可导出 Excel / PDF 报表（含趋势图）。</p>
        </el-collapse-item>
      </el-collapse>
    </div>

    <div v-if="isViewer" class="page-card help-card">
      <h3 class="block-title">数据统计员</h3>
      <el-collapse>
        <el-collapse-item title="可查看与导出的内容" name="viewer">
          <p>可查看四项核心指标（资源占用率、农户直播场次、农产品销量、预约成功率）与趋势，并导出 Excel / PDF 聚合报表。</p>
          <p>出于个人信息保护，数据统计员不能查看单农户明细，也不能导出 CSV 明细与操作日志。</p>
        </el-collapse-item>
      </el-collapse>
    </div>

    <div v-if="isSystemAdmin" class="page-card help-card">
      <h3 class="block-title">系统管理员</h3>
      <el-collapse>
        <el-collapse-item title="账号与权限" name="accounts">
          <p>按系统管理员、直播管理员、农户主播、数据统计员分配角色。新增账号时由管理员设置初始密码（至少 8 位，含字母和数字），不能代用户设置统一默认密码。</p>
        </el-collapse-item>
        <el-collapse-item title="知识库维护" name="knowledge">
          <p>在「AI 知识库」上传 txt / md / pdf / docx 文档（单文件 ≤ 10MB），系统自动分块解析；解析失败的文档可重新解析。</p>
        </el-collapse-item>
        <el-collapse-item title="审计与数据备份" name="audit">
          <p>操作日志可按类型与时间查询，并支持一键防篡改校验。数据备份脚本位于 deploy/backup，可按说明配置每日定时备份。</p>
        </el-collapse-item>
      </el-collapse>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { currentUser } from '../api/auth'

const role = computed(() => currentUser()?.roleCode || '')
const isFarmer = computed(() => role.value === 'farmer')
const isAdmin = computed(() => role.value === 'live_admin' || role.value === 'system_admin')
const isViewer = computed(() => role.value === 'data_viewer')
const isSystemAdmin = computed(() => role.value === 'system_admin')
</script>

<style scoped>
.assistant-tip {
  margin-bottom: 14px;
}

.assistant-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.help-card {
  margin-bottom: 14px;
}

.block-title {
  margin: 0 0 12px;
  font-size: 16px;
}

.block-title + :deep(.el-collapse) p {
  color: #5e6d82;
  font-size: 13px;
  line-height: 1.8;
  margin: 0 0 8px;
}
</style>
