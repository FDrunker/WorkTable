<template>
  <div class="app-container">
    <el-form v-show="showSearch" :model="queryParams" ref="queryRef" :inline="true" label-width="82px">
      <el-form-item label="模型名称" prop="modelName">
        <el-input
          v-model="queryParams.modelName"
          placeholder="请输入模型名称"
          clearable
          style="width: 220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="提供商" prop="provider">
        <el-select v-model="queryParams.provider" placeholder="全部" clearable style="width: 180px">
          <el-option
            v-for="item in providers"
            :key="item.code"
            :label="item.name"
            :value="item.code"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="类型" prop="modelType">
        <el-select v-model="queryParams.modelType" placeholder="全部" clearable style="width: 160px">
          <el-option label="Embedding" value="embedding" />
          <el-option label="Chat" value="chat" />
          <el-option label="System One" value="system-one" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="enabled">
        <el-select v-model="queryParams.enabled" placeholder="全部" clearable style="width: 130px">
          <el-option label="启用" value="1" />
          <el-option label="停用" value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleAdd"
          v-hasPermi="['knowledge:model:add']"
        >
          新增
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete()"
          v-hasPermi="['knowledge:model:remove']"
        >
          删除
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="modelList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="48" align="center" />
      <el-table-column label="名称" prop="modelName" min-width="150" :show-overflow-tooltip="true">
        <template #default="scope">
          <span>{{ scope.row.modelName }}</span>
          <el-tag v-if="scope.row.isDefault === '1'" type="success" size="small" class="ml6">默认</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提供商" prop="provider" width="150">
        <template #default="scope">{{ providerName(scope.row.provider) }}</template>
      </el-table-column>
      <el-table-column label="类型" prop="modelType" width="120">
        <template #default="scope">
          <el-tag :type="modelTypeTag(scope.row.modelType)" effect="plain">
            {{ modelTypeName(scope.row.modelType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="模型代码" prop="modelCode" min-width="180" :show-overflow-tooltip="true" />
      <el-table-column label="API Key" prop="apiKeyMasked" width="130" />
      <el-table-column label="启用" width="78" align="center">
        <template #default="scope">
          <el-switch
            v-model="scope.row.enabled"
            active-value="1"
            inactive-value="0"
            @change="changeEnabled(scope.row)"
          />
        </template>
      </el-table-column>
      <el-table-column label="连通性" min-width="180">
        <template #default="scope">
          <el-tag :type="testStatusTag(scope.row.lastTestStatus)" size="small">
            {{ testStatusName(scope.row.lastTestStatus) }}
          </el-tag>
          <span v-if="scope.row.lastTestTime" class="test-time">{{ parseTime(scope.row.lastTestTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="220" align="center">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['knowledge:model:edit']"
          >
            修改
          </el-button>
          <el-button
            link
            type="success"
            icon="Connection"
            @click="handleTest(scope.row)"
            v-hasPermi="['knowledge:model:test']"
          >
            测试
          </el-button>
          <el-button
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['knowledge:model:remove']"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog v-model="open" :title="title" width="640px" append-to-body>
      <el-form ref="modelRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="form.modelName" placeholder="用于业务页面展示的名称" />
        </el-form-item>
        <el-form-item label="提供商" prop="provider">
          <el-select v-model="form.provider" style="width: 100%" @change="handleProviderChange">
            <el-option
              v-for="item in providers"
              :key="item.code"
              :label="item.name"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="form.modelType" style="width: 100%" @change="handleTypeChange">
            <el-option
              v-for="item in availableModelTypes"
              :key="item.code"
              :label="item.name"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模型代码" prop="modelCode">
          <el-input v-model="form.modelCode" placeholder="供应商实际模型名" />
        </el-form-item>
        <el-form-item label="接口地址" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="留空使用提供商默认地址" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="form.modelId ? '留空保持原值' : '请输入 API Key'"
          />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" active-value="1" inactive-value="0" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" active-value="1" inactive-value="0" />
        </el-form-item>
        <el-form-item label="扩展配置">
          <el-input v-model="form.extraConfig" type="textarea" :rows="3" placeholder="JSON，可留空" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确定</el-button>
        <el-button @click="cancel">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="KnowledgeModel">
import type { AiModelConfig, AiModelProvider, AiModelQueryParams } from '@/types'
import { ElLoading } from 'element-plus'
import {
  addAiModel,
  deleteAiModels,
  getAiModel,
  listAiModelProviders,
  listAiModels,
  testAiModel,
  updateAiModel
} from '@/api/knowledge/model'

const { proxy } = getCurrentInstance() as any
const loading = ref(true)
const showSearch = ref(true)
const modelList = ref<AiModelConfig[]>([])
const providers = ref<AiModelProvider[]>([])
const ids = ref<number[]>([])
const multiple = ref(true)
const total = ref(0)
const open = ref(false)
const title = ref('')

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    modelName: undefined,
    provider: undefined,
    modelType: undefined,
    enabled: undefined
  } as AiModelQueryParams,
  form: {} as AiModelConfig,
  rules: {
    modelName: [{ required: true, message: '模型名称不能为空', trigger: 'blur' }],
    provider: [{ required: true, message: '请选择提供商', trigger: 'change' }],
    modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }],
    modelCode: [{ required: true, message: '模型代码不能为空', trigger: 'blur' }],
    apiKey: [{ validator: validateApiKey, trigger: 'blur' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

const availableModelTypes = computed(() => {
  const provider = providers.value.find((item: AiModelProvider) => item.code === form.value.provider)
  const types = provider?.modelTypes || []
  return [
    { code: 'embedding', name: 'Embedding' },
    { code: 'chat', name: 'Chat' },
    { code: 'system-one', name: 'System One' }
  ].filter((item) => types.includes(item.code))
})

function getList() {
  loading.value = true
  listAiModels(queryParams.value)
    .then((response) => {
      modelList.value = response.rows
      total.value = response.total
    })
    .finally(() => {
      loading.value = false
    })
}

function getProviders() {
  listAiModelProviders().then((response) => {
    providers.value = response.data || []
  })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

function handleSelectionChange(selection: AiModelConfig[]) {
  ids.value = selection.map((item) => item.modelId!).filter(Boolean)
  multiple.value = !selection.length
}

function reset() {
  form.value = {
    modelId: undefined,
    modelName: undefined,
    provider: 'openai',
    modelType: 'embedding',
    modelCode: undefined,
    baseUrl: 'https://api.openai.com',
    apiKey: undefined,
    enabled: '1',
    isDefault: '0',
    extraConfig: undefined,
    remark: undefined
  }
  proxy.resetForm('modelRef')
}

function handleAdd() {
  reset()
  open.value = true
  title.value = '新增模型'
}

function handleUpdate(row: AiModelConfig) {
  reset()
  getAiModel(row.modelId!).then((response) => {
    form.value = { ...response.data!, apiKey: undefined }
    open.value = true
    title.value = '修改模型'
  })
}

function handleProviderChange(providerCode: string) {
  const provider = providers.value.find((item: AiModelProvider) => item.code === providerCode)
  if (provider) {
    form.value.baseUrl = provider.defaultBaseUrl
    if (!provider.modelTypes.includes(form.value.modelType || '')) {
      form.value.modelType = provider.modelTypes[0]
    }
  }
}

function handleTypeChange() {
  if (form.value.modelType !== 'embedding') {
    form.value.isDefault = '0'
  }
}

function validateApiKey(_rule: any, value: string, callback: (error?: Error) => void) {
  if (!form.value.modelId && !value) {
    callback(new Error('API Key 不能为空'))
    return
  }
  callback()
}

function submitForm() {
  proxy.$refs.modelRef.validate((valid: boolean) => {
    if (!valid) return
    const action = form.value.modelId ? updateAiModel(form.value) : addAiModel(form.value)
    action.then(() => {
      proxy.$modal.msgSuccess(form.value.modelId ? '修改成功' : '新增成功')
      open.value = false
      getList()
    })
  })
}

function cancel() {
  open.value = false
  reset()
}

function changeEnabled(row: AiModelConfig) {
  updateAiModel(row)
    .then(() => proxy.$modal.msgSuccess(row.enabled === '1' ? '已启用' : '已停用'))
    .catch(() => {
      row.enabled = row.enabled === '1' ? '0' : '1'
    })
}

function handleTest(row: AiModelConfig) {
  const testLoading = ElLoading.service({ text: '正在测试连通性' })
  testAiModel(row.modelId!)
    .then((response) => {
      Object.assign(row, response.data)
      const success = response.data?.lastTestStatus === 'SUCCESS'
      success ? proxy.$modal.msgSuccess('连通成功') : proxy.$modal.msgError(response.data?.lastTestMessage || '连通失败')
    })
    .finally(() => {
      testLoading.close()
    })
}

function handleDelete(row?: AiModelConfig) {
  const modelIds = row?.modelId || ids.value
  proxy.$modal
    .confirm('是否确认删除选中的模型配置？')
    .then(() => deleteAiModels(modelIds))
    .then(() => {
      getList()
      proxy.$modal.msgSuccess('删除成功')
    })
    .catch(() => {})
}

function providerName(code?: string) {
  return providers.value.find((item: AiModelProvider) => item.code === code)?.name || code || '-'
}

function modelTypeName(type?: string) {
  return {
    embedding: 'Embedding',
    chat: 'Chat',
    'system-one': 'System One'
  }[type || ''] || type || '-'
}

function modelTypeTag(type?: string) {
  return type === 'embedding' ? 'success' : type === 'chat' ? 'primary' : 'warning'
}

function testStatusName(status?: string) {
  return {
    SUCCESS: '成功',
    FAILED: '失败',
    UNTESTED: '未测试'
  }[status || ''] || '未测试'
}

function testStatusTag(status?: string) {
  return status === 'SUCCESS' ? 'success' : status === 'FAILED' ? 'danger' : 'info'
}

getProviders()
getList()
</script>

<style scoped lang="scss">
.ml6 {
  margin-left: 6px;
}

.test-time {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
