<template>
  <div class="app-container">
    <el-form :model="queryParams" inline label-width="72px">
      <el-form-item label="工单号">
        <el-input
          v-model="queryParams.ticketNo"
          placeholder="请输入工单号"
          clearable
          style="width: 190px"
          @keyup.enter="loadTickets"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" clearable style="width: 150px">
          <el-option label="待处理" value="WAITING" />
          <el-option label="批准" value="APPROVED" />
          <el-option label="修改后批准" value="MODIFIED" />
          <el-option label="升级处理" value="ESCALATED" />
        </el-select>
      </el-form-item>
      <el-form-item label="优先级">
        <el-select v-model="queryParams.priority" clearable style="width: 130px">
          <el-option label="高" value="high" />
          <el-option label="中" value="medium" />
          <el-option label="低" value="low" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="loadTickets">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button icon="Refresh" @click="loadTickets">刷新</el-button>
      </el-col>
      <right-toolbar @queryTable="loadTickets" />
    </el-row>

    <el-table v-loading="loading" :data="tickets" border row-key="ticketId">
      <el-table-column label="工单号" prop="ticketNo" width="190">
        <template #default="scope">
          <el-button link type="primary" @click="openTicket(scope.row)">
            {{ scope.row.ticketNo }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="问题分类" width="120">
        <template #default="scope">
          <el-tag type="danger" effect="plain" size="small">
            {{ categoryName(scope.row.category) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="优先级" width="100" align="center">
        <template #default="scope">
          <el-tag :type="priorityTag(scope.row.priority)" effect="plain" size="small">
            {{ priorityName(scope.row.priority) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120" align="center">
        <template #default="scope">
          <el-tag :type="statusTag(scope.row.status)" size="small">
            {{ statusName(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="问题内容" prop="question" min-width="300" :show-overflow-tooltip="true" />
      <el-table-column label="创建时间" prop="createTime" width="170" />
      <el-table-column label="处理人" prop="handledBy" width="100" />
      <el-table-column label="操作" width="110" fixed="right" align="center">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openTicket(scope.row)"
            v-hasPermi="['customer-service:ticket:edit']"
          >
            处理
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && tickets.length === 0" description="暂无客服工单" />

    <el-drawer
      v-model="detailOpen"
      title="智能客服工单处理"
      size="min(760px, 94vw)"
      destroy-on-close
    >
      <div v-if="activeTicket" class="ticket-detail">
        <div class="ticket-heading">
          <div>
            <div class="ticket-number">{{ activeTicket.ticketNo }}</div>
            <div class="ticket-subtitle">
              {{ categoryName(activeTicket.category) }} · {{ activeTicket.createTime }}
            </div>
          </div>
          <el-tag :type="statusTag(activeTicket.status)">
            {{ statusName(activeTicket.status) }}
          </el-tag>
        </div>

        <section class="detail-section">
          <h4>工单内容</h4>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="AI 分类">
              {{ categoryName(activeTicket.category) }}
            </el-descriptions-item>
            <el-descriptions-item label="分类理由">
              {{ activeTicket.classificationReason || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="访客标识">
              {{ activeTicket.visitorId }}
            </el-descriptions-item>
            <el-descriptions-item label="来源会话">
              {{ activeTicket.conversationId }}
            </el-descriptions-item>
            <el-descriptions-item label="问题内容" :span="2">
              <div class="long-text">{{ activeTicket.question }}</div>
            </el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-section">
          <h4>AI 预分析</h4>
          <div class="analysis-block">
            <span>问题摘要</span>
            <p>{{ activeTicket.preanalysisSummary || '-' }}</p>
          </div>
          <div class="analysis-block">
            <span>建议处理方案</span>
            <p>{{ activeTicket.preanalysisSolution || '-' }}</p>
          </div>
          <div class="analysis-block">
            <span>建议回复草稿</span>
            <p>{{ activeTicket.suggestedReply || '-' }}</p>
          </div>
        </section>

        <section class="detail-section">
          <h4>人工输入</h4>
          <el-form label-position="top">
            <el-form-item label="优先级">
              <el-select v-model="handleForm.priority" style="width: 180px">
                <el-option label="高" value="high" />
                <el-option label="中" value="medium" />
                <el-option label="低" value="low" />
              </el-select>
            </el-form-item>
            <el-form-item label="回复内容">
              <el-input
                v-model="handleForm.replyContent"
                type="textarea"
                :rows="6"
                maxlength="4000"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="处理备注">
              <el-input v-model="handleForm.note" type="textarea" :rows="3" maxlength="1000" show-word-limit />
            </el-form-item>
          </el-form>
        </section>

        <div class="action-bar">
          <el-button
            type="primary"
            :disabled="!activeTicket.suggestedReply || handling"
            @click="submitAction('approve')"
          >
            批准
          </el-button>
          <el-button
            type="success"
            :disabled="!handleForm.replyContent.trim() || handling"
            @click="submitAction('modify')"
          >
            修改后批准
          </el-button>
          <el-button type="warning" :loading="handling" @click="submitAction('escalate')">
            升级处理
          </el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts" name="CustomerServiceTicket">
import {
  handleCustomerServiceTicket,
  listCustomerServiceTickets
} from '@/api/knowledge/customerServiceTicket'
import type {
  CustomerServiceTicket,
  CustomerServiceTicketAction,
  CustomerServiceTicketPriority,
  CustomerServiceTicketQuery
} from '@/types'

const { proxy } = getCurrentInstance() as any
const loading = ref(false)
const handling = ref(false)
const detailOpen = ref(false)
const tickets = ref<CustomerServiceTicket[]>([])
const activeTicket = ref<CustomerServiceTicket>()
const queryParams = reactive<CustomerServiceTicketQuery>({
  ticketNo: '',
  status: '',
  priority: ''
})
const handleForm = reactive<{
  priority: CustomerServiceTicketPriority
  replyContent: string
  note: string
}>({
  priority: 'medium',
  replyContent: '',
  note: ''
})

onMounted(loadTickets)

async function loadTickets() {
  loading.value = true
  try {
    const response = await listCustomerServiceTickets(queryParams)
    tickets.value = response.data || []
    if (activeTicket.value) {
      const refreshed = tickets.value.find(
        (item: CustomerServiceTicket) => item.ticketId === activeTicket.value?.ticketId
      )
      if (refreshed) activeTicket.value = refreshed
    }
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  queryParams.ticketNo = ''
  queryParams.status = ''
  queryParams.priority = ''
  loadTickets()
}

function openTicket(ticket: CustomerServiceTicket) {
  activeTicket.value = ticket
  handleForm.priority = ticket.priority
  handleForm.replyContent = ticket.handlerReply || ticket.suggestedReply || ''
  handleForm.note = ticket.handleNote || ''
  detailOpen.value = true
}

async function submitAction(action: CustomerServiceTicketAction) {
  if (!activeTicket.value) return
  if (action === 'modify' && !handleForm.replyContent.trim()) {
    proxy.$modal.msgWarning('请填写人工回复内容')
    return
  }
  handling.value = true
  try {
    const response = await handleCustomerServiceTicket(activeTicket.value.ticketId, {
      action,
      priority: handleForm.priority,
      replyContent: handleForm.replyContent,
      note: handleForm.note
    })
    activeTicket.value = response.data || activeTicket.value
    proxy.$modal.msgSuccess(actionSuccessMessage(action))
    await loadTickets()
  } finally {
    handling.value = false
  }
}

function actionSuccessMessage(action: CustomerServiceTicketAction) {
  if (action === 'approve') return '已按 AI 建议批准'
  if (action === 'modify') return '已提交修改后的回复'
  return '已升级至专项处理团队'
}

function categoryName(category: string) {
  return category === 'complex' ? '复杂问题' : '标准问题'
}

function priorityName(priority: string) {
  return { high: '高', medium: '中', low: '低' }[priority] || priority
}

function priorityTag(priority: string) {
  return priority === 'high' ? 'danger' : priority === 'medium' ? 'warning' : 'info'
}

function statusName(status: string) {
  return {
    WAITING: '待处理',
    APPROVED: '已批准',
    MODIFIED: '修改后批准',
    ESCALATED: '已升级'
  }[status] || status
}

function statusTag(status: string) {
  return status === 'WAITING'
    ? 'warning'
    : status === 'ESCALATED'
      ? 'danger'
      : status === 'APPROVED'
        ? 'success'
        : 'primary'
}
</script>

<style scoped lang="scss">
.ticket-number {
  color: var(--el-text-color-primary);
  font-family: "Cascadia Mono", Consolas, monospace;
  font-size: 19px;
  font-weight: 700;
}

.ticket-subtitle {
  margin-top: 5px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.ticket-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--el-border-color-light);
}

.detail-section {
  margin-top: 24px;
}

.detail-section h4 {
  margin: 0 0 10px;
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.long-text {
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.analysis-block {
  margin-bottom: 10px;
  padding: 12px 14px;
  border-left: 3px solid #6f8fbd;
  background: var(--el-fill-color-light);
}

.analysis-block span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.analysis-block p {
  margin: 6px 0 0;
  color: var(--el-text-color-primary);
  line-height: 1.7;
  white-space: pre-wrap;
}

.action-bar {
  position: sticky;
  bottom: 0;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin: 26px -20px -20px;
  padding: 14px 20px;
  border-top: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}
</style>
