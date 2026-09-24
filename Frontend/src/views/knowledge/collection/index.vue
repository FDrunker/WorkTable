<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="70px">
      <el-form-item label="知识库名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入知识库名称"
          clearable
          style="width: 240px"
          @keyup.enter="getCollections"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="getCollections">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleAddCollection"
          v-hasPermi="['knowledge:collection:add']"
        >
          新增知识库
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="selectedCollectionIds.length === 0"
          @click="handleDeleteCollections()"
          v-hasPermi="['knowledge:collection:remove']"
        >
          删除知识库
        </el-button>
      </el-col>
      <right-toolbar @queryTable="getCollections" />
    </el-row>

    <el-table
      v-loading="collectionLoading"
      :data="collections"
      @selection-change="handleCollectionSelectionChange"
    >
      <el-table-column type="selection" width="48" align="center" />
      <el-table-column label="知识库名称" prop="name" min-width="240" :show-overflow-tooltip="true">
        <template #default="scope">
          <el-icon class="collection-icon"><Collection /></el-icon>
          <span>{{ scope.row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column label="知识库 ID" prop="id" min-width="260" :show-overflow-tooltip="true" />
      <el-table-column label="文档数" prop="documentCount" width="100" align="center" />
      <el-table-column label="操作" width="190" align="center">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="FolderOpened"
            @click="openCollection(scope.row)"
            v-hasPermi="['knowledge:document:list']"
          >
            管理文档
          </el-button>
          <el-button
            link
            type="danger"
            icon="Delete"
            @click="handleDeleteCollections(scope.row)"
            v-hasPermi="['knowledge:collection:remove']"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="collectionOpen" title="新增知识库" width="480px" append-to-body>
      <el-form ref="collectionRef" :model="collectionForm" :rules="collectionRules" label-width="90px">
        <el-form-item label="知识库名称" prop="name">
          <el-input
            v-model="collectionForm.name"
            placeholder="请输入知识库名称"
            maxlength="255"
            @keyup.enter="submitCollection"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitCollection">确定</el-button>
        <el-button @click="collectionOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="drawerOpen"
      :title="currentCollection?.name || '知识库文档'"
      size="92%"
      destroy-on-close
      @closed="stopTaskPolling"
    >
      <el-tabs v-model="activeTab" class="knowledge-tabs">
        <el-tab-pane label="文档" name="documents">
          <el-form :model="documentQuery" inline label-width="70px">
            <el-form-item label="文档名称">
              <el-input
                v-model="documentQuery.documentName"
                placeholder="请输入文档名称"
                clearable
                style="width: 220px"
                @keyup.enter="handleDocumentQuery"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleDocumentQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetDocumentQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <el-row :gutter="10" class="mb8">
            <el-col :span="1.5">
              <el-button
                type="primary"
                plain
                icon="Plus"
                @click="handleAddDocument"
                v-hasPermi="['knowledge:document:add']"
              >
                添加文档
              </el-button>
            </el-col>
            <right-toolbar @queryTable="getDocuments" />
          </el-row>

          <el-table v-loading="documentLoading" :data="documents">
            <el-table-column label="文档名称" prop="documentName" min-width="220" :show-overflow-tooltip="true">
              <template #default="scope">
                <el-button link type="primary" @click="showChunks(scope.row)">
                  {{ scope.row.documentName }}
                </el-button>
              </template>
            </el-table-column>
            <el-table-column label="来源" prop="sourceType" width="110">
              <template #default="scope">
                <el-tag effect="plain" size="small">{{ sourceName(scope.row.sourceType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="切片数" prop="chunkCount" width="90" align="center" />
            <el-table-column label="状态" prop="status" width="110">
              <template #default="scope">
                <el-tag :type="documentStatusTag(scope.row.status)" size="small">
                  {{ documentStatusName(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="scope">
                <span>{{ scope.row.createTime ? parseTime(scope.row.createTime) : '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" fixed="right" width="240" align="center">
              <template #default="scope">
                <el-button
                  link
                  type="primary"
                  icon="Edit"
                  @click="handleEditDocument(scope.row)"
                  v-hasPermi="['knowledge:document:edit']"
                >
                  修改
                </el-button>
                <el-button
                  link
                  type="success"
                  icon="Refresh"
                  @click="handleReindex(scope.row)"
                  v-hasPermi="['knowledge:document:reindex']"
                >
                  重建
                </el-button>
                <el-button
                  link
                  type="danger"
                  icon="Delete"
                  @click="handleDeleteDocument(scope.row)"
                  v-hasPermi="['knowledge:document:remove']"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <pagination
            v-show="documentTotal > 0"
            :total="documentTotal"
            v-model:page="documentQuery.pageNum"
            v-model:limit="documentQuery.pageSize"
            @pagination="getDocuments"
          />
        </el-tab-pane>

        <el-tab-pane label="任务历史" name="tasks">
          <el-row :gutter="10" class="mb8">
            <el-col :span="1.5">
              <el-button icon="Refresh" @click="getTasks">刷新任务</el-button>
            </el-col>
          </el-row>
          <el-table v-loading="taskLoading" :data="tasks">
            <el-table-column label="任务" prop="taskId" width="90" />
            <el-table-column label="文档" prop="documentName" min-width="180" :show-overflow-tooltip="true" />
            <el-table-column label="类型" prop="taskType" width="100" />
            <el-table-column label="进度" min-width="220">
              <template #default="scope">
                <el-progress
                  :percentage="scope.row.progress || 0"
                  :status="taskProgressStatus(scope.row.status)"
                  :stroke-width="12"
                />
                <div class="task-message">{{ stageName(scope.row.stage) }} · {{ scope.row.message }}</div>
              </template>
            </el-table-column>
            <el-table-column label="状态" prop="status" width="110">
              <template #default="scope">
                <el-tag :type="taskStatusTag(scope.row.status)" size="small">
                  {{ taskStatusName(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="scope">{{ parseTime(scope.row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" align="center">
              <template #default="scope">
                <el-button
                  v-if="scope.row.status === 'PENDING' || scope.row.status === 'RUNNING'"
                  link
                  type="warning"
                  @click="handleCancelTask(scope.row)"
                  v-hasPermi="['knowledge:task:cancel']"
                >
                  取消
                </el-button>
                <el-button
                  v-if="scope.row.status === 'FAILED' || scope.row.status === 'CANCELLED'"
                  link
                  type="primary"
                  @click="handleRetryTask(scope.row)"
                  v-hasPermi="['knowledge:task:retry']"
                >
                  重试
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <pagination
            v-show="taskTotal > 0"
            :total="taskTotal"
            v-model:page="taskQuery.pageNum"
            v-model:limit="taskQuery.pageSize"
            @pagination="getTasks"
          />
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <el-dialog v-model="editorOpen" :title="editorTitle" width="780px" append-to-body>
      <el-form ref="documentRef" :model="documentForm" :rules="documentRules" label-width="100px">
        <el-form-item label="文档名称" prop="documentName">
          <el-input v-model="documentForm.documentName" placeholder="请输入文档名称" />
        </el-form-item>
        <el-form-item label="Embedding" prop="modelId">
          <el-select v-model="documentForm.modelId" clearable placeholder="请选择 Embedding 模型" style="width: 100%">
            <el-option
              v-for="item in embeddingModels"
              :key="item.modelId"
              :label="`${item.modelName} (${item.modelCode})`"
              :value="item.modelId!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="输入方式">
          <el-radio-group v-model="inputMode">
            <el-radio-button value="upload">上传 Markdown</el-radio-button>
            <el-radio-button value="paste">粘贴内容</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="inputMode === 'upload'" label="Markdown">
          <el-upload
            v-model:file-list="fileList"
            :auto-upload="false"
            :limit="1"
            accept=".md,text/markdown"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
          >
            <el-button icon="Upload">选择 .md 文件</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item v-else label="内容" prop="content">
          <el-input
            v-model="documentForm.content"
            type="textarea"
            :rows="14"
            placeholder="请输入 Markdown 内容"
          />
        </el-form-item>
        <el-form-item label="分隔符" prop="separator">
          <el-input v-model="documentForm.separator" placeholder="默认 \n" />
        </el-form-item>
        <el-row>
          <el-col :span="12">
            <el-form-item label="块大小" prop="chunkSize">
              <el-input-number v-model="documentForm.chunkSize" :min="1" :max="10000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="重叠长度" prop="chunkOverlap">
              <el-input-number v-model="documentForm.chunkOverlap" :min="0" :max="9999" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitDocument">提交任务</el-button>
        <el-button @click="editorOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="chunkOpen" :title="`${chunkDocumentName} · 切片`" width="820px" append-to-body>
      <el-empty v-if="chunks.length === 0" description="暂无切片" />
      <el-collapse v-else>
        <el-collapse-item
          v-for="(chunk, index) in chunks"
          :key="chunk.id"
          :name="chunk.id"
          :title="`切片 ${index + 1} · ${chunk.id}`"
        >
          <pre class="chunk-content">{{ chunk.content }}</pre>
        </el-collapse-item>
      </el-collapse>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="KnowledgeCollection">
import type {
  AiModelConfig,
  ChromaCollection,
  CollectionCreateRequest,
  DocumentImportRequest,
  KnowledgeDocument,
  KnowledgeDocumentChunk,
  KnowledgeDocumentQueryParams,
  KnowledgeTask,
  KnowledgeTaskQueryParams
} from '@/types'
import {
  cancelKnowledgeTask,
  createCollection,
  deleteCollections,
  deleteKnowledgeDocument,
  getKnowledgeDocument,
  importKnowledgeDocument,
  listCollections,
  listKnowledgeChunks,
  listKnowledgeDocuments,
  listKnowledgeTasks,
  reindexKnowledgeDocument,
  retryKnowledgeTask,
  updateKnowledgeDocument
} from '@/api/knowledge/collection'
import { listAiModelOptions } from '@/api/knowledge/model'

const { proxy } = getCurrentInstance() as any
const collections = ref<ChromaCollection[]>([])
const collectionLoading = ref(false)
const queryParams = reactive({ name: '' })
const collectionOpen = ref(false)
const selectedCollectionIds = ref<string[]>([])
const collectionForm = reactive({ name: '' } as CollectionCreateRequest)
const collectionRules = {
  name: [{ required: true, message: '知识库名称不能为空', trigger: 'blur' }]
}
const drawerOpen = ref(false)
const currentCollection = ref<ChromaCollection | null>(null)
const activeTab = ref('documents')
const documents = ref<KnowledgeDocument[]>([])
const documentLoading = ref(false)
const documentTotal = ref(0)
const tasks = ref<KnowledgeTask[]>([])
const taskLoading = ref(false)
const taskTotal = ref(0)
const embeddingModels = ref<AiModelConfig[]>([])
const editorOpen = ref(false)
const editorTitle = ref('')
const inputMode = ref<'upload' | 'paste'>('upload')
const fileList = ref<any[]>([])
const selectedFile = ref<File>()
const chunkOpen = ref(false)
const chunkDocumentName = ref('')
const chunks = ref<KnowledgeDocumentChunk[]>([])
let taskTimer: ReturnType<typeof setTimeout> | undefined

const documentQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  documentName: undefined
} as KnowledgeDocumentQueryParams)

const taskQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  collectionId: undefined
} as KnowledgeTaskQueryParams)

const documentForm = reactive({
  documentId: undefined,
  collectionId: '',
  collectionName: '',
  documentName: '',
  modelId: undefined,
  separator: '\\n',
  chunkSize: 1024,
  chunkOverlap: 100,
  content: ''
} as DocumentImportRequest)

const documentRules = {
  documentName: [{ required: true, message: '文档名称不能为空', trigger: 'blur' }],
  modelId: [{ required: true, message: '请选择 Embedding 模型', trigger: 'change' }],
  separator: [{ required: true, message: '分隔符不能为空', trigger: 'blur' }],
  chunkSize: [{ required: true, message: '块大小不能为空', trigger: 'blur' }],
  chunkOverlap: [{ required: true, message: '重叠长度不能为空', trigger: 'blur' }]
}

function getCollections() {
  collectionLoading.value = true
  listCollections(queryParams.name)
    .then((response) => {
      collections.value = response.data || []
    })
    .finally(() => {
      collectionLoading.value = false
    })
}

function resetQuery() {
  queryParams.name = ''
  getCollections()
}

function handleCollectionSelectionChange(selection: ChromaCollection[]) {
  selectedCollectionIds.value = selection.map((item: ChromaCollection) => item.id)
}

function handleAddCollection() {
  collectionForm.name = ''
  collectionOpen.value = true
  nextTick(() => proxy.resetForm('collectionRef'))
}

function submitCollection() {
  proxy.$refs.collectionRef.validate((valid: boolean) => {
    if (!valid) return
    createCollection(collectionForm).then(() => {
      proxy.$modal.msgSuccess('知识库创建成功')
      collectionOpen.value = false
      getCollections()
    })
  })
}

function handleDeleteCollections(row?: ChromaCollection) {
  const collectionIds = row ? [row.id] : selectedCollectionIds.value
  if (collectionIds.length === 0) {
    proxy.$modal.msgError('请选择要删除的知识库')
    return
  }
  const names = row
    ? row.name
    : collections.value
        .filter((item: ChromaCollection) => collectionIds.includes(item.id))
        .map((item: ChromaCollection) => item.name)
        .join('、')
  proxy.$modal
    .confirm(`是否确认删除知识库“${names}”？知识库内全部文档和切片将同时删除。`)
    .then(() => deleteCollections(collectionIds))
    .then(() => {
      proxy.$modal.msgSuccess('知识库删除成功')
      selectedCollectionIds.value = []
      getCollections()
      if (currentCollection.value && collectionIds.includes(currentCollection.value.id)) {
        drawerOpen.value = false
        currentCollection.value = null
      }
    })
    .catch(() => {})
}

function openCollection(collection: ChromaCollection) {
  currentCollection.value = collection
  documentQuery.collectionId = collection.id
  documentQuery.collectionName = collection.name
  documentQuery.pageNum = 1
  taskQuery.collectionId = collection.id
  taskQuery.pageNum = 1
  activeTab.value = 'documents'
  drawerOpen.value = true
  getDocuments()
  getTasks()
  loadEmbeddingModels()
}

function getDocuments() {
  documentLoading.value = true
  listKnowledgeDocuments(documentQuery)
    .then((response) => {
      documents.value = response.rows
      documentTotal.value = response.total
    })
    .finally(() => {
      documentLoading.value = false
    })
}

function handleDocumentQuery() {
  documentQuery.pageNum = 1
  getDocuments()
}

function resetDocumentQuery() {
  documentQuery.documentName = undefined
  handleDocumentQuery()
}

function getTasks(scheduleNext = true) {
  if (!currentCollection.value) return
  taskLoading.value = true
  listKnowledgeTasks(taskQuery)
    .then((response) => {
      tasks.value = response.rows
      taskTotal.value = response.total
      if (
        scheduleNext &&
        tasks.value.some((item: KnowledgeTask) => item.status === 'PENDING' || item.status === 'RUNNING')
      ) {
        scheduleTaskPolling()
      }
    })
    .finally(() => {
      taskLoading.value = false
    })
}

function scheduleTaskPolling() {
  stopTaskPolling()
  taskTimer = setTimeout(() => {
    getTasks()
    getDocuments()
    getCollections()
  }, 2000)
}

function stopTaskPolling() {
  if (taskTimer) {
    clearTimeout(taskTimer)
    taskTimer = undefined
  }
}

function loadEmbeddingModels() {
  listAiModelOptions('embedding').then((response) => {
    embeddingModels.value = response.data || []
    selectDefaultEmbeddingModel()
  })
}

function selectDefaultEmbeddingModel() {
  if (documentForm.modelId || embeddingModels.value.length === 0) {
    return
  }
  const defaultModel = embeddingModels.value.find((item: AiModelConfig) => item.isDefault === '1')
    || embeddingModels.value[0]
  documentForm.modelId = defaultModel?.modelId
}

function resetDocumentForm() {
  documentForm.documentId = undefined
  documentForm.collectionId = currentCollection.value?.id || ''
  documentForm.collectionName = currentCollection.value?.name || ''
  documentForm.documentName = ''
  documentForm.modelId = undefined
  selectDefaultEmbeddingModel()
  documentForm.separator = '\\n'
  documentForm.chunkSize = 1024
  documentForm.chunkOverlap = 100
  documentForm.content = ''
  inputMode.value = 'upload'
  fileList.value = []
  selectedFile.value = undefined
  proxy.resetForm('documentRef')
}

function handleAddDocument() {
  resetDocumentForm()
  editorTitle.value = '添加 Markdown 文档'
  editorOpen.value = true
}

async function handleEditDocument(row: KnowledgeDocument) {
  resetDocumentForm()
  editorTitle.value = '修改文档'
  documentForm.documentId = row.documentId
  documentForm.documentName = row.documentName
  documentForm.modelId = row.embeddingModelId
  inputMode.value = 'paste'
  if (row.managed) {
    const response = await getKnowledgeDocument(row.documentId)
    documentForm.content = response.data?.content || ''
    documentForm.separator = response.data?.chunkSeparator || '\\n'
    documentForm.chunkSize = response.data?.chunkSize || 1024
    documentForm.chunkOverlap = response.data?.chunkOverlap || 100
  } else {
    const response = await listKnowledgeChunks(row.collectionId, row.documentId)
    documentForm.content = (response.data || []).map((item) => item.content).join('\n')
    documentForm.separator = '\\n'
  }
  editorOpen.value = true
}

function handleFileChange(file: any, files: any[]) {
  if (!file.name.toLowerCase().endsWith('.md')) {
    proxy.$modal.msgError('仅支持 Markdown（.md）文件')
    fileList.value = []
    selectedFile.value = undefined
    return
  }
  selectedFile.value = file.raw
  fileList.value = files.slice(-1)
}

function handleFileRemove() {
  selectedFile.value = undefined
  fileList.value = []
}

function submitDocument() {
  proxy.$refs.documentRef.validate((valid: boolean) => {
    if (!valid) return
    if (inputMode.value === 'upload' && !selectedFile.value && !documentForm.documentId) {
      proxy.$modal.msgError('请选择 Markdown 文件')
      return
    }
    if (!documentForm.modelId) {
      proxy.$modal.msgError('请先配置并选择 Embedding 模型')
      return
    }
    const action = documentForm.documentId
      ? updateKnowledgeDocument(documentForm.documentId, documentForm, selectedFile.value)
      : importKnowledgeDocument(documentForm, selectedFile.value)
    action.then(() => {
      proxy.$modal.msgSuccess('任务已提交')
      editorOpen.value = false
      activeTab.value = 'tasks'
      getTasks(false)
      scheduleTaskPolling()
    })
  })
}

function handleDeleteDocument(row: KnowledgeDocument) {
  proxy.$modal
    .confirm(`是否确认删除文档“${row.documentName}”及其全部切片？`)
    .then(() => deleteKnowledgeDocument(row.collectionId, row.documentId))
    .then(() => {
      proxy.$modal.msgSuccess('删除成功')
      getDocuments()
      getCollections()
    })
    .catch(() => {})
}

function handleReindex(row: KnowledgeDocument) {
  proxy.$modal
    .confirm(`是否重新向量化文档“${row.documentName}”？`)
    .then(() => reindexKnowledgeDocument(row.collectionId, row.documentId, row.embeddingModelId))
    .then(() => {
      proxy.$modal.msgSuccess('重建任务已提交')
      activeTab.value = 'tasks'
      getTasks(false)
      scheduleTaskPolling()
    })
    .catch(() => {})
}

function showChunks(row: KnowledgeDocument) {
  chunkDocumentName.value = row.documentName
  listKnowledgeChunks(row.collectionId, row.documentId).then((response) => {
    chunks.value = response.data || []
    chunkOpen.value = true
  })
}

function handleCancelTask(row: KnowledgeTask) {
  proxy.$modal
    .confirm('是否取消该任务？当前步骤结束后停止。')
    .then(() => cancelKnowledgeTask(row.taskId))
    .then(() => {
      proxy.$modal.msgSuccess('取消请求已提交')
      getTasks(false)
    })
    .catch(() => {})
}

function handleRetryTask(row: KnowledgeTask) {
  retryKnowledgeTask(row.taskId).then(() => {
    proxy.$modal.msgSuccess('重试任务已提交')
    getTasks(false)
    scheduleTaskPolling()
  })
}

function sourceName(source?: string) {
  return {
    UPLOAD: '上传文件',
    PASTE: '粘贴内容',
    EXTERNAL: '已有数据'
  }[source || ''] || source || '-'
}

function documentStatusName(status?: string) {
  return {
    PENDING: '等待中',
    RUNNING: '处理中',
    SUCCESS: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消',
    EXTERNAL: '外部数据'
  }[status || ''] || status || '-'
}

function documentStatusTag(status?: string) {
  return {
    SUCCESS: 'success',
    FAILED: 'danger',
    RUNNING: 'warning',
    PENDING: 'info',
    EXTERNAL: 'info'
  }[status || ''] || 'info'
}

function taskStatusName(status?: string) {
  return {
    PENDING: '等待中',
    RUNNING: '执行中',
    SUCCESS: '成功',
    FAILED: '失败',
    CANCELLED: '已取消'
  }[status || ''] || status || '-'
}

function taskStatusTag(status?: string) {
  return {
    SUCCESS: 'success',
    FAILED: 'danger',
    RUNNING: 'warning',
    PENDING: 'info',
    CANCELLED: 'info'
  }[status || ''] || 'info'
}

function taskProgressStatus(status?: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'exception'
  if (status === 'CANCELLED') return 'warning'
  return undefined
}

function stageName(stage?: string) {
  return {
    PENDING: '等待',
    PREPARING: '准备',
    CHUNKING: '切片',
    EMBEDDING: '向量化',
    WRITING: '写入 Chroma',
    COMPLETED: '完成',
    FAILED: '失败',
    CANCELLED: '取消',
    INTERRUPTED: '中断'
  }[stage || ''] || stage || '-'
}

getCollections()
onBeforeUnmount(stopTaskPolling)
</script>

<style scoped lang="scss">
.collection-icon {
  margin-right: 8px;
  vertical-align: -2px;
  color: var(--el-color-primary);
}

.knowledge-tabs {
  :deep(.el-tabs__content) {
    overflow: visible;
  }
}

.task-message {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.chunk-content {
  margin: 0;
  padding: 12px;
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--el-fill-color-light);
  font-family: inherit;
  line-height: 1.7;
}
</style>
