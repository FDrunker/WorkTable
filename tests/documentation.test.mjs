import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import test from 'node:test'

const root = resolve(import.meta.dirname, '..')

function read(relativePath) {
  const absolutePath = resolve(root, relativePath)
  assert.equal(existsSync(absolutePath), true, `missing file: ${relativePath}`)
  return readFileSync(absolutePath, 'utf8')
}

test('README links to the handoff documents and public design contract', () => {
  const readme = read('README.md')

  assert.match(readme, /docs\/FRONTEND_HANDOFF\.md/)
  assert.match(readme, /docs\/BACKEND_HANDOFF\.md/)
  assert.match(readme, /docs\/PUBLIC_CHAT_DESIGN\.md/)
})

test('public chat design contract covers the intended direction and anti-patterns', () => {
  const document = read('docs/PUBLIC_CHAT_DESIGN.md')

  assert.match(document, /calm editorial workbench/)
  assert.match(document, /Mineral green/)
  assert.match(document, /No glossy gradients/)
  assert.match(document, /No pill navigation/)
  assert.match(document, /390px/)
})

test('frontend handoff covers startup, architecture, features, and verification', () => {
  const document = read('docs/FRONTEND_HANDOFF.md')
  const expectedSections = [
    '启动与构建',
    '应用启动链路',
    '路由与权限',
    '请求与响应约定',
    '知识库、模型与 AI 对话',
    '微信聊天归档',
    '测试与交付检查'
  ]

  expectedSections.forEach((section) => {
    assert.match(document, new RegExp(section))
  })
})

test('backend handoff covers modules, auth, persistence, custom domains, and tests', () => {
  const document = read('docs/BACKEND_HANDOFF.md')
  const expectedSections = [
    'Maven 模块边界',
    '请求处理与认证链路',
    '接口与响应约定',
    'MySQL 数据模型',
    '知识库异步链路',
    'AI 对话链路',
    '微信归档链路',
    '测试覆盖与执行'
  ]

  expectedSections.forEach((section) => {
    assert.match(document, new RegExp(section))
  })
})

test('handoff documents keep their key code references valid', () => {
  const references = [
    'Frontend/src/main.ts',
    'Frontend/src/permission.ts',
    'Frontend/src/router/index.ts',
    'Frontend/src/utils/request.ts',
    'Frontend/src/views/knowledge/collection/index.vue',
    'Frontend/src/views/knowledge/customerService/index.vue',
    'Frontend/src/views/knowledge/customerServiceTicket/index.vue',
    'Frontend/src/views/public/resume/index.vue',
    'Frontend/src/views/public/customer-service/index.vue',
    'Frontend/src/views/wechat/chat/index.vue',
    'Backend/pom.xml',
    'Backend/ruoyi-admin/pom.xml',
    'Backend/ruoyi-admin/src/main/resources/application.yml',
    'Backend/ruoyi-framework/src/main/java/com/ruoyi/framework/config/SecurityConfig.java',
    'Backend/ruoyi-wechat/src/main/java/com/ruoyi/wechat/controller/WechatChatController.java',
    'Backend/ruoyi-wechat/src/main/java/com/ruoyi/wechat/service/WeChatArchiveService.java',
    'Backend/ruoyi-knowledge/src/main/java/com/ruoyi/knowledge/service/DocumentTaskProcessor.java'
  ]

  references.forEach((relativePath) => {
    assert.equal(existsSync(resolve(root, relativePath)), true, `missing referenced path: ${relativePath}`)
  })
})

test('business controllers are colocated with their Maven modules', () => {
  const controllers = [
    ['Backend/ruoyi-knowledge/src/main/java/com/ruoyi/knowledge/controller/AiModelController.java', 'com.ruoyi.knowledge.controller'],
    ['Backend/ruoyi-knowledge/src/main/java/com/ruoyi/knowledge/controller/CustomerServiceController.java', 'com.ruoyi.knowledge.controller'],
    ['Backend/ruoyi-knowledge/src/main/java/com/ruoyi/knowledge/controller/CustomerServiceTicketController.java', 'com.ruoyi.knowledge.controller'],
    ['Backend/ruoyi-knowledge/src/main/java/com/ruoyi/knowledge/controller/KnowledgeCollectionController.java', 'com.ruoyi.knowledge.controller'],
    ['Backend/ruoyi-knowledge/src/main/java/com/ruoyi/knowledge/controller/KnowledgeTaskController.java', 'com.ruoyi.knowledge.controller'],
    ['Backend/ruoyi-knowledge/src/main/java/com/ruoyi/knowledge/controller/PublicCustomerServiceAiController.java', 'com.ruoyi.knowledge.controller'],
    ['Backend/ruoyi-knowledge/src/main/java/com/ruoyi/knowledge/controller/PublicResumeAiController.java', 'com.ruoyi.knowledge.controller'],
    ['Backend/ruoyi-wechat/src/main/java/com/ruoyi/wechat/controller/WechatChatController.java', 'com.ruoyi.wechat.controller'],
    ['Backend/ruoyi-wechat/src/main/java/com/ruoyi/wechat/controller/WechatExceptionHandler.java', 'com.ruoyi.wechat.controller']
  ]

  controllers.forEach(([relativePath, expectedPackage]) => {
    assert.match(read(relativePath), new RegExp(`^package ${expectedPackage.replaceAll('.', '\\.')};`, 'm'))
  })
})
