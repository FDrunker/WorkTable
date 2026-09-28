# 后端接手说明

本文面向第一次接手 WorkTable 的后端开发者或 AI，重点说明模块边界、启动链路、鉴权、数据存储和自研业务的实际执行路径。

## 1. 技术栈与运行环境

| 项目 | 当前版本或方案 |
| --- | --- |
| JDK | 17 |
| Spring Boot | 4.1.0 |
| 构建 | Maven 多模块 |
| Web | Spring MVC / Jakarta Servlet |
| 安全 | Spring Security + JWT + Redis 登录态 |
| ORM | MyBatis 4.1.0，Mapper XML |
| 分页 | PageHelper 4.1.1 |
| 数据库连接池 | Druid 1.2.28 |
| 数据库 | MySQL 8 |
| 缓存 | Redis |
| API 文档 | springdoc-openapi 3.1.0 |
| AI 集成 | Spring AI 2.0.1 |
| 向量库 | Chroma |

根 POM 是 `Backend/pom.xml`，业务启动模块是 `ruoyi-admin`。项目基于 RuoYi-Vue 3.9.2，但已经裁剪部门、岗位和数据权限，并扩展了微信归档与知识库/AI 对话模块。

## 2. Maven 模块边界

| 模块 | 职责 | 关键依赖 |
| --- | --- | --- |
| `ruoyi-admin` | 启动类、Controller、配置、Swagger、最终可执行 JAR | `ruoyi-framework`、`ruoyi-quartz`、`ruoyi-generator`、`ruoyi-wechat`、`ruoyi-knowledge` |
| `ruoyi-common` | 通用实体、工具、异常、注解、分页、Redis、安全基础 | Spring MVC/Security/Redis、MyBatis 相关基础库 |
| `ruoyi-system` | 用户、角色、菜单、字典、配置、公告及微信 Mapper XML | `ruoyi-common` |
| `ruoyi-framework` | 安全、JWT、数据源、AOP、过滤器、Redis、全局异常 | `ruoyi-system` |
| `ruoyi-quartz` | 定时任务管理与 Quartz 持久化 | `ruoyi-common` |
| `ruoyi-generator` | 数据库表导入和 Velocity 代码生成 | `ruoyi-common`、Druid |
| `ruoyi-wechat` | 微信数据源、文本解析、清洗、归档服务 | `ruoyi-common` |
| `ruoyi-knowledge` | Chroma、Markdown 切片、异步任务、模型配置、AI 对话 | `ruoyi-common`、Spring AI OpenAI/DeepSeek/Ollama/Chroma |

有一个容易误解的边界：微信模块的 Java Mapper 接口在 `ruoyi-wechat`，XML 映射文件在 `ruoyi-system/src/main/resources/mapper/wechat/`。Spring 构建时会把所有模块的 `mapper/**/*Mapper.xml` 统一加载，因此改 SQL 时要到 `ruoyi-system` 找。

## 3. 首次启动

### 3.1 基础依赖

- MySQL 8，默认数据库名示例为 `ry-vue` 或 `work_table`，以本机配置为准。
- Redis，默认 `localhost:6379`，数据库 0。
- JDK 17、Maven 3.8+。
- Chroma 和外部模型服务只在知识库、AI 对话功能中需要。

### 3.2 SQL 顺序

全新环境按以下顺序执行：

```powershell
# 基础若依表、菜单、字典、代码生成表
mysql -uroot -p < Backend/sql/ry_20260417.sql

# Quartz 表
mysql -uroot -p < Backend/sql/quartz.sql

# 微信会话、消息、历史分析表及菜单
mysql -uroot -p < Backend/sql/wechat.sql

# 知识库、模型、任务、AI 对话表及菜单
mysql -uroot -p < Backend/sql/ai-knowledge.sql
```

已有数据库的迁移脚本：

| 脚本 | 场景 |
| --- | --- |
| `wechat-insights.sql` | 旧版 `wechat.sql` 升级关系洞察字段 |
| `wechat-session-delete.sql` | 旧版数据库补“删除会话”权限 |
| `ai-knowledge-collection-management.sql` | 旧版数据库补知识库新增/删除权限 |
| `customer-service.sql` | 旧版知识库升级 AI 对话能力 |
| `ai-chat-menu.sql` | 智能客服菜单升级为一级“AI 对话” |
| `public-ai-chat.sql` | 已有数据库升级场景化配置、公开会话与消息持久化 |
| `menu-restructure.sql` | 知识库、模型管理提升为一级菜单并删除若依官网菜单 |

这些迁移脚本部分不是幂等的，执行前应先确认当前库状态，并做好备份。

### 3.3 配置

主配置：`Backend/ruoyi-admin/src/main/resources/application.yml`

数据源配置：`Backend/ruoyi-admin/src/main/resources/application-druid.yml`

启动：

```powershell
cd Backend
mvn clean package
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

默认端口是 `8090`。可临时覆盖：

```powershell
java -jar ruoyi-admin/target/ruoyi-admin.jar --server.port=8091
```

Swagger UI：`http://localhost:8090/swagger-ui.html`

注意：当前 `springdoc.group-configs` 只扫描 `com.ruoyi.web.controller.tool`，不能把 Swagger 当成全部业务接口的完整文档。知识库、AI 对话和微信接口应以 Controller 源码和本文接口表为准。

## 4. 请求处理与认证链路

典型请求链路：

```text
HTTP
  -> Spring Security Filter Chain
  -> JwtAuthenticationTokenFilter
  -> Controller
  -> @PreAuthorize 权限校验
  -> Service
  -> Mapper / Chroma / 外部模型
  -> AjaxResult 或 TableDataInfo
```

关键实现：

- `RuoYiApplication`：Spring Boot 启动类，排除默认 DataSource 自动配置，使用自定义动态数据源。
- `SecurityConfig`：无状态会话、JWT 过滤器、CORS、匿名白名单、方法级权限。
- `JwtAuthenticationTokenFilter`：解析请求头 Token，并从 Redis 恢复 `LoginUser`。
- `TokenService`：创建、解析、刷新 JWT；真正用户信息缓存在 Redis。
- `SysLoginService`：验证码、密码、登录失败次数、IP 黑名单、登录日志。
- `SysPermissionService`：从角色和菜单中计算 `roles` 与 `permissions`。
- `BaseController`：分页、当前用户、统一响应辅助方法。
- `GlobalExceptionHandler`：统一输出校验、鉴权、业务和运行时异常。

Token 请求头默认是 `Authorization: Bearer <token>`。Token 本身只是一种索引，Redis 中不存在时即使 JWT 签名有效也不能通过认证。

## 5. 接口与响应约定

普通接口返回：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {}
}
```

分页接口返回：

```json
{
  "code": 200,
  "msg": "查询成功",
  "rows": [],
  "total": 0
}
```

常用状态码：

| code | 含义 |
| --- | --- |
| `200` | 成功 |
| `4002` | 微信数据源错误，响应可带 `suggestion` |
| `401` | 未认证或登录失效 |
| `403` | 无权限 |
| `500` | 业务或系统错误 |
| `601` | 业务警告 |

接口分层约定：

- Controller 负责参数接收、权限、操作日志和响应包装。
- Service 负责业务校验、事务和跨组件编排。
- Mapper 负责 MySQL。
- Chroma 和模型 API 不应直接从 Controller 调用。

## 6. 已实现接口索引

### 6.1 基础框架

| 接口 | 说明 |
| --- | --- |
| `POST /login` | 登录并返回 Token |
| `POST /register` | 注册，是否开放由系统配置控制 |
| `GET /captchaImage` | 获取验证码 |
| `GET /getInfo` | 获取用户、角色、权限、密码状态 |
| `GET /getRouters` | 获取当前用户菜单路由 |
| `GET /system/*` | 用户、角色、菜单、字典、参数、公告等系统管理 |
| `GET|DELETE /monitor/*` | 在线用户、日志、缓存和服务器监控 |
| `/tool/gen/*` | 代码生成器 |
| `/common/upload` | 通用文件上传 |

### 6.2 知识库

Controller：`ruoyi-admin/.../web/controller/knowledge/KnowledgeCollectionController.java`

| 方法与路径 | 说明 |
| --- | --- |
| `GET /knowledge/collection/list` | Chroma collection 列表 |
| `POST /knowledge/collection` | 新建 collection |
| `DELETE /knowledge/collection` | 删除 collection、其中切片和本地逻辑文档 |
| `GET /knowledge/document/list` | 文档列表，合并 Chroma 记录和 MySQL 逻辑文档 |
| `GET /knowledge/document/{documentId}` | 获取受管文档原文 |
| `GET /knowledge/document/{documentId}/chunks` | 获取 Chroma 切片 |
| `POST /knowledge/document/import` | 上传或粘贴 Markdown，提交导入任务 |
| `PUT /knowledge/document/{documentId}` | 修改并重建文档 |
| `DELETE /knowledge/document/{documentId}` | 删除文档及切片 |
| `POST /knowledge/document/{documentId}/reindex` | 使用指定 embedding 模型重建 |

Controller：`KnowledgeTaskController`

| 方法与路径 | 说明 |
| --- | --- |
| `GET /knowledge/task/list` | 任务分页列表 |
| `GET /knowledge/task/{taskId}` | 任务详情 |
| `POST /knowledge/task/{taskId}/cancel` | 请求取消 |
| `POST /knowledge/task/{taskId}/retry` | 重试失败或取消的任务 |

### 6.3 AI 模型和对话

Controller：`AiModelController`

| 方法与路径 | 说明 |
| --- | --- |
| `GET /knowledge/model/list` | 模型分页列表 |
| `GET /knowledge/model/{modelId}` | 模型详情 |
| `GET /knowledge/model/options` | 按类型查询启用模型 |
| `GET /knowledge/model/providers` | 提供商与能力目录 |
| `POST /knowledge/model` | 新增模型 |
| `PUT /knowledge/model` | 修改模型 |
| `DELETE /knowledge/model/{modelIds}` | 删除模型 |
| `POST /knowledge/model/{modelId}/test` | 连通性测试 |

Controller：`CustomerServiceController`

| 方法与路径 | 说明 |
| --- | --- |
| `GET /knowledge/ai-chat/config` | 获取 AI 对话配置 |
| `GET /knowledge/ai-chat/scenes` | 获取公开 AI 对话场景选项 |
| `GET /knowledge/ai-chat/options` | 获取 chat 模型、embedding 模型和知识库选项 |
| `PUT /knowledge/ai-chat/config` | 保存配置 |
| `POST /knowledge/ai-chat/chat` | 执行一次知识库问答 |

`config` 和 `chat` 支持 `scene` 查询参数，默认值为 `admin`；可选场景为 `admin`、`personal-info`、`customer-service`。

公开端拆成两个独立 Controller，均标记 `@Anonymous`：

| 方法与路径 | 说明 |
| --- | --- |
| `GET /public/resume-ai/bootstrap` | 个人简历 AI 状态和历史会话 |
| `POST /public/resume-ai/conversations` | 新建简历 AI 匿名会话 |
| `POST /public/resume-ai/conversations/{conversationId}/messages` | 检索简历知识库并直接回答 |
| `GET /public/customer-service-ai/bootstrap` | 智能客服状态和历史会话 |
| `POST /public/customer-service-ai/conversations` | 新建智能客服匿名会话 |
| `POST /public/customer-service-ai/conversations/{conversationId}/messages` | 分类工单，标准问题回答或复杂问题创建人工工单 |

Controller：`CustomerServiceTicketController`

| 方法与路径 | 说明 |
| --- | --- |
| `GET /knowledge/customer-service/tickets` | 按工单号、状态和优先级查询 |
| `POST /knowledge/customer-service/tickets/{ticketId}/handle` | 批准、修改后批准或升级处理 |

### 6.4 微信归档

Controller：`ruoyi-admin/.../web/controller/wechat/WechatChatController.java`

| 方法与路径 | 说明 |
| --- | --- |
| `GET /wechat/status` | 数据源状态，当前 `agentEnabled=false` |
| `GET /wechat/sessions` | 已归档和数据源发现的会话 |
| `POST /wechat/text/preview` | 解析粘贴文本，返回昵称候选，不写库 |
| `POST /wechat/text/commit` | 确认自己的昵称并归档 |
| `GET /wechat/chat/open` | 打开会话并分页读取消息 |
| `POST /wechat/chat/sync` | 手动同步数据源 |
| `POST /wechat/session/acquaintance-date` | 设置或清除认识日期 |
| `DELETE /wechat/session/{sessionId}` | 逻辑删除会话、消息和分析记录 |
| `POST /wechat/identity/confirm` | 确认说话人映射并回写消息角色 |
| `POST /wechat/chat/analyze` | 占位接口，当前明确返回未启用 |
| `GET /wechat/analysis/list` | 读取历史分析记录 |
| `GET /wechat/analysis/{analysisId}` | 读取分析详情 |
| `DELETE /wechat/analysis/{analysisIds}` | 删除历史分析记录 |

后台自研 Controller 使用 `@PreAuthorize`；`PublicResumeAiController` 和 `PublicCustomerServiceAiController` 是明确的匿名执行边界。前端菜单和按钮权限必须与 `Backend/sql/` 中的权限标识保持一致。

## 7. MySQL 数据模型

### 基础若依表

核心表包括：

- `sys_user`、`sys_role`、`sys_menu`、`sys_user_role`、`sys_role_menu`
- `sys_dict_type`、`sys_dict_data`
- `sys_config`
- `sys_oper_log`、`sys_logininfor`
- `sys_job`、`sys_job_log` 及 `QRTZ_*`
- `sys_notice`、`sys_notice_read`
- `gen_table`、`gen_table_column`

项目已删除部门、岗位相关表以及角色数据权限字段。

### 知识库与 AI 对话表

| 表 | 作用 |
| --- | --- |
| `ai_model_config` | 模型提供商、类型、模型代码、Base URL、加密后的 API Key、默认和测试状态 |
| `ai_knowledge_document` | 本系统管理的逻辑文档、Markdown 原文、切片参数、任务状态 |
| `ai_knowledge_task` | 导入、修改、重建任务的持久化状态和请求快照 |
| `ai_customer_service_config` | 按 `scene` 保存页面标题、欢迎语、chat/embedding 模型、知识库、TopK 和启用状态 |
| `ai_chat_conversation` | 公开 AI 会话、匿名 `visitor_id`、场景、标题和更新时间 |
| `ai_chat_message` | 公开 AI 消息、角色、意图、澄清状态和知识来源 JSON |

`ai_knowledge_document` 没有 `user_id`，知识库数据是系统级共享资源，访问控制依赖菜单权限。`ai_chat_conversation` 使用浏览器生成的 `visitor_id` 隔离匿名历史，查询和删除都会同时校验 `visitor_id + scene`；它不是登录身份的替代品。

### 微信表

| 表 | 作用 |
| --- | --- |
| `sys_wechat_session` | 用户会话、说话人映射、认识日期、统计和预留洞察字段 |
| `sys_wechat_message` | 归档消息、角色、类型、去重摘要和分析结果字段 |
| `sys_wechat_analysis` | 历史分析结果和 prompt 快照 |

微信会话、消息和分析记录都带 `user_id`。查询和写入必须保留归属校验，不能只按 `session_id` 查询后直接返回。

## 8. 知识库异步链路

入口：

- `KnowledgeDocumentService.importDocument`
- `KnowledgeDocumentService.updateDocument`
- `KnowledgeDocumentService.reindex`

执行链路：

```text
Controller
  -> KnowledgeDocumentService
  -> 写入 ai_knowledge_document
  -> KnowledgeTaskService 写入 ai_knowledge_task
  -> knowledgeTaskExecutor 线程池
  -> DocumentTaskProcessor
  -> MarkdownChunker
  -> AiModelClientFactory 创建 EmbeddingModel
  -> 逐片段生成向量
  -> ChromaCollectionService 分批 upsert
  -> 删除旧切片
  -> 更新文档和任务状态
```

关键行为：

- 导入必须选择 `embedding` 模型，未选择会直接拒绝。
- `.md` 文件先保存到 `knowledge.storage-path`，成功写入后删除暂存文件；正文仍保存在 MySQL。
- Markdown 按段落自动切分，围栏代码块会作为完整段落保留；默认块大小 1024，默认重叠 100。
- Chroma 写入批次是 64；读取按 500 分页，单次最多读取 10000 条。
- 没有 `documentId` metadata 的 Chroma 记录会被视为外部已有数据。
- 删除 collection 前会检查 `PENDING` 或 `RUNNING` 任务。
- 服务启动时 `KnowledgeTaskRecoveryRunner` 把中断任务标记为失败，允许手动重试。
- 取消是协作式的，当前向量计算或 Chroma 写入步骤结束后才真正停止。

## 9. 模型配置与 API Key 安全

支持的能力矩阵：

| Provider | embedding | chat | system-one |
| --- | --- | --- | --- |
| OpenAI / ChatGPT | 是 | 是 | 否 |
| DeepSeek | 否 | 是 | 否 |
| Ollama | 是 | 是 | 否 |
| Jev / TypeSafe | 否 | 否 | 是 |

API Key 处理：

- 写入时由 `ApiKeyCryptoService` 使用 AES-GCM 加密。
- 主密钥来自 `knowledge.encryption-key`，必须是 Base64 编码的 32 字节密钥。
- 查询接口只返回 `apiKeyMasked`，实体中的明文 `apiKey` 标记为只写。
- 生产环境应通过 `WORKTABLE_KNOWLEDGE_KEY` 覆盖仓库中的默认值。
- 更换主密钥后，已有密文无法直接解密，必须先设计迁移方案。

模型连通性测试不会抛 500，而是把 `SUCCESS` 或 `FAILED` 和消息写回 `last_test_status` / `last_test_message`。

## 10. AI 对话链路

核心实现是 `CustomerServiceService`。

```text
POST /knowledge/ai-chat/chat
  -> 校验问题和运行配置
  -> chat 模型识别意图
  -> embedding 模型对“意图 + 问题”生成查询向量
  -> 逐个 Chroma collection 检索 TopK
  -> 汇总并按距离排序，最多保留配置的 TopK
  -> 无结果时返回澄清问题
  -> 有结果时让 chat 模型仅依据知识片段生成 JSON 回答
  -> 返回 answer、intent、confidence、needsClarification、sources
```

保护限制：

- 问题最长 4000 字符。
- 历史最多保留 10 轮，单条内容最多 2000 字符。
- TopK 范围为 1 到 20。
- 拼给模型的上下文最多 24000 字符。
- 多个知识库中只有一个失败时继续使用其他知识库；全部失败才报错。
- 回答 JSON 解析失败时退化为原始模型文本。

公开链路已经拆成两套独立业务服务：

```text
POST /public/resume-ai/conversations/{conversationId}/messages
  -> ResumeAiChatService 校验 visitorId 会话归属
  -> 读取最近 10 条持久化消息作为历史
  -> KnowledgeQaService 执行知识库检索与标准回答
  -> 写入 user / assistant 两条 ai_chat_message
  -> 首条消息用问题摘要生成会话标题
  -> 直接返回回答

POST /public/customer-service-ai/conversations/{conversationId}/messages
  -> CustomerServiceAiChatService 接收用户问题
  -> LLM 输出 category=standard/complex、reason、priority
  -> standard: KnowledgeQaService 检索后生成标准回答
  -> complex: LLM 预分析 summary/solution/suggestedReply
  -> CustomerServiceTicketService 写入 ai_customer_service_ticket
  -> 返回“已记录您的问题，等待人工给您处理”和工单号
```

## 11. 微信归档链路

### 11.1 数据源

`WechatDataSourceConfig` 根据 `wechat.datasource` 选择：

| 值 | 实现 | 用途 |
| --- | --- | --- |
| `text` | `TextWechatDataSource` | 默认，用户粘贴文本 |
| `mock` | `MockWechatDataSource` | 内置样例和演示 |
| `cli` | `CliWechatDataSource` | 调用本地 `wechat-cli` |

文本格式是每 3 行一条消息：昵称、时间、可跨行的消息内容。

### 11.2 文本导入

```text
POST /wechat/text/preview
  -> ChatTextParser 解析和归一化时间
  -> 按时间排序
  -> 统计发送者
  -> 原文暂存在进程内存
  -> 返回 importId 和昵称候选

POST /wechat/text/commit
  -> 校验 importId、当前用户和自己的昵称
  -> 追加到指定会话，或按对方昵称生成稳定会话标识
  -> TextWechatDataSource 暂存已提交消息
  -> WeChatArchiveService 清洗并落库
  -> 回写说话人角色和会话名称
  -> 返回聊天详情
```

重要限制：预览和已提交待同步的原始消息都只在 JVM 内存中。MySQL 中的归档记录持久化，但服务重启后不能依靠文本数据源重新同步旧会话，必须重新粘贴原文。

### 11.3 清洗规则

`WechatMessageCleaner` 的处理顺序是类型识别、降噪、脱敏、连续消息折叠、上下文预算裁剪。

- 电话、撤回、系统提示、静态 `[表情]` 和纯符号会降噪。
- 图片、文件、链接、小程序、位置、视频保留为媒体证据。
- `[语音]` 和 `[动画表情]` 会归档和展示，但标记为只展示，不参与分析。
- 手机号、身份证、银行卡会脱敏。
- 连续同一发送者达到阈值时折叠，但保留“连续发送 N 条”的行为信号。
- 上下文超预算时从最旧消息开始丢弃，并告知模型上下文不完整。

### 11.4 当前分析能力

`WechatChatController.AGENT_INVOCATION_ENABLED` 当前固定为 `false`，`POST /wechat/chat/analyze` 会明确返回“分析功能未启用”。微信模块没有 LLM 调用实现，好感度、我的发挥、情绪、意图和建议等字段只是预留协议与展示位。

## 12. 配置索引

| 配置前缀 | 关键项 | 说明 |
| --- | --- | --- |
| `server` | `port`、`servlet.context-path` | 默认端口 8090 |
| `spring.data.redis` | host、port、database、password | 登录状态和缓存 |
| `token` | header、secret、expireTime | JWT 配置，生产必须更换 secret |
| `ruoyi` | profile、captchaType | 上传路径和验证码 |
| `wechat.datasource` | text/mock/cli | 微信数据源 |
| `wechat.cli` | executable、timeout、batch-size | 命令行数据源 |
| `wechat.clean` | drop-noise、collapse、mask、budget | 消息清洗 |
| `knowledge.storage-path` | Markdown 暂存目录 | 需要持久磁盘 |
| `knowledge.encryption-key` | API Key 主密钥 | 生产使用环境变量 |
| `knowledge.task-threads` | 异步任务线程数 | 默认 4 |
| `knowledge.chroma` | base-url、tenant、database | Chroma 连接 |

不要把真实数据库密码、Token 密钥或模型 API Key 提交到仓库。即使 `application.yml` 当前有默认值，也只应视为本地占位。

## 13. 测试覆盖与执行

当前包含 Java 单元测试的模块：

- `ruoyi-wechat`：文本解析、时间归一化、数据源装配、消息清洗、说话人身份、会话归档、洞察字段。
- `ruoyi-knowledge`：Markdown 切片、任务请求 JSON、API Key 加解密、Chroma 调用、模型 Provider 能力、AI 对话。

执行全部后端测试：

```powershell
cd Backend
mvn test
```

只执行自研模块及其依赖：

```powershell
cd Backend
mvn -pl ruoyi-wechat,ruoyi-knowledge -am test
```

打包但不跑测试：

```powershell
cd Backend
mvn clean package -DskipTests
```

新增 MySQL Mapper、事务边界、异步任务状态机、外部 API 请求或安全逻辑时，应优先补单元测试。涉及 Spring Context、数据库或外部服务时再增加集成测试，避免让所有单元测试依赖真实基础设施。

## 14. 常见改动路径

### 新增后台业务模块

1. 优先在独立 Maven 模块或现有域中实现 Service、Domain、Mapper。
2. Controller 放在 `ruoyi-admin` 的对应包下。
3. Mapper XML 放入模块资源目录，并确保符合 `mapper/**/*Mapper.xml` 扫描规则。
4. 在 `ruoyi-admin/pom.xml` 和根 POM 注册新模块依赖。
5. 增加 SQL 菜单与按钮权限。
6. 增加测试并执行 Maven 全量测试。

### 修改知识库任务

重点检查：

- `KnowledgeDocumentService`
- `KnowledgeTaskService`
- `DocumentTaskProcessor`
- `MarkdownChunker`
- `ChromaCollectionService`
- `KnowledgeTaskMapper.xml`

这些类共同形成状态机，只改其中一处容易造成文档状态、任务状态和 Chroma 数据不一致。

### 修改微信导入或清洗

重点检查：

- `ChatTextParser`
- `TextWechatDataSource`
- `WechatMessageCleaner`
- `WeChatArchiveService`
- `WechatTextImportService`
- `WeChatSessionMapper.xml`
- `WeChatMessageMapper.xml`

消息去重依赖“会话 + 时间 + 原始发送者 + 内容 MD5”的唯一索引。改变时间格式、内容清洗顺序或角色回写方式时，必须同步检查幂等性。

### 接入真正的微信分析

当前 Controller 有明确的占位返回，但不要直接把真实调用塞进 Controller。推荐新增独立 Service，复用已归档消息、清洗结果和 `sys_wechat_analysis` 数据模型，再实现取消、超时、错误持久化和权限校验。

## 15. 首先阅读的文件

建议接手顺序：

1. `Backend/pom.xml`
2. `Backend/ruoyi-admin/pom.xml`
3. `Backend/ruoyi-admin/src/main/java/com/ruoyi/RuoYiApplication.java`
4. `Backend/ruoyi-admin/src/main/resources/application.yml`
5. `Backend/ruoyi-framework/src/main/java/com/ruoyi/framework/config/SecurityConfig.java`
6. `Backend/ruoyi-framework/src/main/java/com/ruoyi/framework/security/filter/JwtAuthenticationTokenFilter.java`
7. `Backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/knowledge/`
8. `Backend/ruoyi-knowledge/src/main/java/com/ruoyi/knowledge/service/`
9. `Backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/wechat/WechatChatController.java`
10. `Backend/ruoyi-wechat/src/main/java/com/ruoyi/wechat/`
