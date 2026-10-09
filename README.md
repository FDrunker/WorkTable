# WorkTable

AI 对话与后台管理平台，基于 **RuoYi-Vue 3.9.2**（若依）前后端分离脚手架搭建，采用「Backend + Frontend」双目录单仓库结构。

## 接手文档

- [前端接手说明](docs/FRONTEND_HANDOFF.md)：启动链路、路由权限、请求协议、页面模块、测试与扩展方式。
- [后端接手说明](docs/BACKEND_HANDOFF.md)：Maven 模块、认证鉴权、数据库、知识库任务、AI 对话、微信归档与测试。
- [公开聊天页设计规范](docs/PUBLIC_CHAT_DESIGN.md)：公开 AI 页面的视觉命题、设计原则、反模式和响应式约束。

文档引用校验：`node --test tests/documentation.test.mjs`。

## 目录结构

```
WorkTable/
├── Backend/                     # 后端：Spring Boot + Maven 多模块
│   ├── ruoyi-admin/             # 启动模块（RuoYiApplication、Controller、配置、Swagger）
│   ├── ruoyi-common/            # 通用工具、注解、常量、异常、基础实体
│   ├── ruoyi-framework/         # 框架核心：安全、JWT、数据源、拦截器、AOP
│   ├── ruoyi-system/            # 系统业务模块：用户 / 角色 / 菜单 / 字典 / 公告
│   ├── ruoyi-quartz/            # 定时任务
│   ├── ruoyi-generator/         # 代码生成器（Velocity 模板）
│   ├── ruoyi-wechat/            # 微信聊天归档（恋爱军师）
│   ├── ruoyi-knowledge/         # Chroma 集合、文档任务与 AI 模型配置
│   ├── sql/                     # 数据库脚本：ry_*.sql、quartz.sql
│   └── pom.xml                  # 聚合父 POM（groupId: com.ruoyi, version: 3.9.2）
└── Frontend/                    # 前端：Vue 3 + Vite + Element Plus
    ├── src/api/                 # 接口封装
    ├── src/views/               # 页面：public / system / monitor / tool / wechat / knowledge
    ├── src/router/              # 路由
    ├── src/store/               # Pinia 状态管理
    ├── src/layout/              # 布局与导航
    ├── .env.development         # 开发环境配置（VITE_APP_BASE_API = /dev-api）
    ├── .env.production          # 生产环境配置（VITE_APP_BASE_API = /prod-api）
    └── package.json             # 依赖与脚本
```

## 技术栈

| 层次 | 组件 | 版本 |
| --- | --- | --- |
| 后端运行时 | JDK | 17 |
| 后端框架 | Spring Boot | 4.1.0 |
| 持久层 | MyBatis / MyBatis-Spring-Boot | 4.1.0 |
| 连接池 | Druid（`druid-spring-boot-4-starter`） | 1.2.28 |
| 分页 | PageHelper | 4.1.1 |
| 数据库 | MySQL（库名 `ry-vue`，脚本见 `Backend/sql/`） | — |
| 缓存 / 会话 | Redis | — |
| 接口文档 | springdoc-openapi（Swagger UI） | 3.1.0 |
| 前端框架 | Vue | 3.5.26 |
| 构建工具 | Vite | 6.4.1 |
| UI 组件库 | Element Plus | 2.13.1 |
| 状态管理 | Pinia | 3.0.4 |
| 语言 | TypeScript | 5.6.3 |

## 快速开始

### 1. 准备环境

- JDK 17、Maven 3.8+
- MySQL 8（执行 `Backend/sql/ry_20260417.sql`、`Backend/sql/quartz.sql`）
- Redis（默认 `localhost:6379`）
- Node.js 18+ 与 npm
- Chroma 服务地址及集合的 embedding function 由外部 Chroma 部署负责

### 2. 后端

```bash
cd Backend
# 按需修改 ruoyi-admin/src/main/resources/application-druid.yml 中的数据库地址与密码
mvn clean package
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

服务默认监听 `http://localhost:8080`，Swagger UI：`http://localhost:8080/swagger-ui.html`。

> 该端口由 `application.yml` 的 `server.port` 控制。
> 需要临时更换时用启动参数覆盖即可，无须改文件：
>
> ```bash
> java -jar ruoyi-admin/target/ruoyi-admin.jar --server.port=8081
> ```

### 3. 前端

```bash
cd Frontend
npm install
npm run dev          # 开发模式
npm run build:prod   # 生产构建
```

开发服务器默认监听 `http://localhost:80`，请求经 `/dev-api` 代理到后端 `http://localhost:8080`（见 `vite.config.ts`）。

- 免登录个人简历 AI：`http://localhost/` 或 `http://localhost/personal-info`
- 免登录智能客服：`http://localhost/customer-service`
- 后台管理：`http://localhost/admin/login`

## 知识库与模型管理（ruoyi-knowledge）

- **入口**：顶级菜单包含「知识库管理」「模型管理」和「AI对话」。
- **知识库管理**：支持新增和删除知识库。删除知识库会同时删除其中全部切片及本系统对应的文档记录；存在运行中任务时会拒绝删除。列表展示名称、知识库 ID 和文档数。
- **文档管理**：仅支持上传 `.md` 文件或直接粘贴 Markdown；系统会按标题层级、段落、列表、表格、引用和代码块等 Markdown 结构切分，并保留标题上下文，块大小和重叠长度默认分别为 `1024`、`100`。
- **向量生成**：添加或修改文档时可以选择启用的 embedding 模型。如果未选择模型，只向 Chroma 提交 `ids`、`documents` 和 metadata，由集合自身的 embedding function 处理。
- **逻辑文档**：同一份 Markdown 会切成多个 Chroma 记录，切片 metadata 包含 `documentId`、`documentName`、`chunkIndex` 等字段。编辑文档会删除该 `documentId` 的旧切片并重建；删除会移除该逻辑文档及其全部切片。
- **已有数据**：没有 `documentId` metadata 的 Chroma 记录会作为“已有数据”展示，可以查看内容、修改、删除或重新向量化；修改后会纳入本系统管理。
- **任务**：导入、修改和重新向量化均异步执行，任务、进度、取消标记和历史记录持久化到 MySQL。服务重启时运行中的任务会标记为失败，可手动重试。
- **模型管理**：支持 OpenAI / ChatGPT、DeepSeek、Ollama，以及 TypeSafe Jev 自定义适配器。模型具有 `embedding`、`chat`、`system-one` 类型、启用状态、默认标记和连通性测试结果。
- **密钥安全**：API Key 使用 AES-GCM 加密后存入数据库，查询接口只返回掩码。主密钥配置为 `knowledge.encryption-key`，生产环境应通过 `WORKTABLE_KNOWLEDGE_KEY` 环境变量覆盖。
- **本地文件**：上传或粘贴的 Markdown 先写入 `knowledge.storage-path`，成功切分并写入 Chroma 后删除暂存文件；正文不写入 MySQL，避免大文档拖垮数据库。编辑时会从 Chroma 切片临时恢复正文。
- **AI对话设置**：顶级菜单「AI对话」可分别配置「个人简历 AI」和「智能客服」的页面标题、欢迎语、对话模型、向量模型、知识库集合、TopK（1-20）和启停状态。
- **个人简历 AI**：公开页 `/personal-info` 是一套独立代码，流程固定为“接收问题 → 简历知识库检索 → LLM 生成标准回答 → 直接回复”，支持匿名会话和历史记录。
- **智能客服 AI**：公开页 `/customer-service` 是另一套独立代码，用户直接描述问题。LLM 根据工单内容按 `standard / complex` 分类：标准问题直接检索知识库并生成回答；复杂问题先做摘要、处理方案和建议回复预分析，再创建人工工单并回复“已记录您的问题，等待人工给您处理”。
- **消息来源**：`ai_chat_message.sources_json` 只保存知识片段记录 ID 和知识库 ID，不保存片段正文；打开历史会话时再从 Chroma 批量回查并展示。
- **客服工单页**：后台「客服工单」页面支持按工单号、状态、优先级筛选；人工可编辑优先级、回复内容和备注，并执行「批准」「修改后批准」「升级处理」。

首次启用该模块需要执行：

```bash
mysql -uroot -p work_table < Backend/sql/ai-knowledge.sql
```

已经执行过旧版 `ai-knowledge.sql` 的数据库，需要补充集合增删权限：

```bash
mysql -uroot -p work_table < Backend/sql/ai-knowledge-collection-management.sql
```

已经执行过旧版 `ai-knowledge.sql` 的数据库，还需要移除文档正文存储：

```bash
mysql -uroot -p work_table < Backend/sql/ai-knowledge-document-temp-storage.sql
```

已经执行过旧版 `ai-knowledge.sql` 的数据库，还需要执行 AI对话迁移：

```bash
mysql -uroot -p work_table < Backend/sql/customer-service.sql
```

如果已经执行过旧版 `customer-service.sql`，只需执行菜单升级脚本，将菜单改名为「AI对话」并提升为顶级菜单：

```bash
mysql -uroot -p work_table < Backend/sql/ai-chat-menu.sql
```

为现有数据库启用免登录 AI 对话和会话记录，需要执行：

```bash
mysql -uroot -p work_table < Backend/sql/public-ai-chat.sql
```

如果数据库已经执行过旧版公开 AI 对话脚本，为个人简历 AI 与智能客服 AI 拆分、消息分类字段和客服工单表补充迁移：

```bash
mysql -uroot -p work_table < Backend/sql/resume-customer-service-split.sql
```

如果已有消息的 `sources_json` 保存过完整知识片段内容，需要压缩为只保留记录 ID 和知识库 ID：

```bash
mysql -uroot -p work_table < Backend/sql/ai-chat-message-source-refs.sql
```

如果要同步最新的菜单层级调整，将知识库管理和模型管理提升为顶级菜单，并删除「若依官网」菜单，需要执行：

```bash
mysql -uroot -p work_table < Backend/sql/menu-restructure.sql
```

默认 Chroma 配置为：

```yaml
knowledge:
  storage-path: D:/ruoyi/uploadPath/knowledge
  encryption-key: ${WORKTABLE_KNOWLEDGE_KEY:生产环境必须覆盖}
  task-threads: 4
  chroma:
    base-url: http://localhost:8000
    tenant: default_tenant
    database: default_database
```

## 模块裁剪记录

本项目已在若依脚手架基础上裁掉 **部门** 与 **岗位** 两个模块，代码与初始化脚本中不再包含「部门」「岗位」概念：

- **已移除后端文件**：`SysDept`/`SysDeptController`/`ISysDeptService`/`SysDeptServiceImpl`/`SysDeptMapper`、`SysPost`/`SysPostController`/`ISysPostService`/`SysPostServiceImpl`/`SysPostMapper`、`SysUserPost`/`SysRoleDept` 及其 Mapper 与对应 XML。
- **已移除数据权限**：`@DataScope` 注解、`DataScopeAspect` 切面、`checkUserDataScope`/`checkRoleDataScope`/`authDataScope`；角色不再有「数据权限」入口，用户与角色列表不再按机构过滤。
- **已移除关联字段**：`sys_user.dept_id`、`LoginUser.deptId`、`BaseController.getDeptId()`、`SecurityUtils.getDeptId()`、`SysUser.postIds`/`dept`、`SysRole.dataScope`/`deptIds`/`deptCheckStrictly`、`SysOperLog.deptName`、在线用户与公告已读列表的「所属部门」列。
- **已移除前端**：`src/views/system/dept`、`src/views/system/post`、`src/api/system/dept.ts`、`src/api/system/post.ts`、`src/types/api/system/dept.ts`、`src/types/api/system/post.ts`，以及用户管理、角色管理、操作日志、在线用户、公告已读页面中的部门/岗位表单项与列。
- **SQL**：`Backend/sql/ry_20260417.sql` 已删除 `sys_dept`、`sys_post`、`sys_role_dept`、`sys_user_post` 建表与初始化数据，删除 `sys_user.dept_id`、`sys_oper_log.dept_name`、`sys_role.data_scope`、`sys_role.dept_check_strictly` 列，并清理 `sys_menu` / `sys_role_menu` 中部门与岗位相关菜单与按钮权限（菜单 103、104 及 1016–1024）。

## 微信聊天记录与关系面板（ruoyi-wechat）

聊天分析的**数据来源是用户粘贴的聊天记录文本**，不读取本地微信数据库，也不依赖任何外部命令：

- **入口**：聊天页（`Frontend/src/views/wechat/chat/index.vue`）左下角「导入文本」。
- **格式**：每 3 行一条消息 —— 昵称 / 时间 / 消息内容；消息内容可跨多行，组与组之间可空行。
  时间支持 `2026-04-01 19:02`、`2026/4/1 19:02`、`2026年4月1日 19:02`、`4月1日 19:02`（省略年份按当年计算），
  兼容 `下午7:02` 这类时段写法，统一归一化为 `yyyy-MM-dd HH:mm[:ss]`。
- **语音与动画表情**：`[语音]`、`[动画表情]` 会照常归档并显示在聊天记录界面，但**不参与分析**
  （对应 `CleanedMessage.Kind.VOICE` / `STICKER` 的 `displayOnly()`）。静态 `[表情]` 仍按降噪丢弃。
- **两步导入**：`POST /wechat/text/preview` 先解析并返回昵称候选；用户选择哪个昵称是自己后，
  `POST /wechat/text/commit` 才正式归档。消息按文本时间排序，不受粘贴先后影响。
- **会话归属**：除自己外的昵称共同决定会话名称和稳定标识，同一对象的后续导入会追加到同一会话。
- **关系面板**：聊天区顶部展示当前好感度、认识天数、我的发挥和建议入口。
  未填写时显示「选择认识日期」，点击后弹窗选择；认识当天按第 1 天计算。
- **删除会话**：左侧会话列表可删除当前用户的会话，并同步逻辑删除消息与历史分析记录。
- **分析面板**：当前聊天页只保留消息与关系洞察，不再展示右侧军师分析面板。
- **消息洞察协议**：会话响应预留 `affinityScore`、`myPerformanceScore`、`nextStepSuggestion`；
  消息响应预留 `emotionTag` / `emotionPercent`、`intentTag` / `intentPercent`、
  `replyRating` / `replyScore`。当前没有计算数据时前端显示“待分析 / 待评估”，后续模型链路可直接回填这些字段。
- **切换来源**：`wechat.datasource` 可选 `text`（默认）、`mock`（内置样例）、`cli`（本地 wechat-cli）；
  只有 `text` 支持文本导入，其余取值下「导入文本」按钮会置灰并说明原因。

- **排错**：页面若报 `No static resource wechat/sessions for request '/wechat/sessions'`（或其它 `/wechat/*` 报同样的错），
  说明**当前运行的后端进程里没有这些接口**，通常是跑在了 `ruoyi-wechat` 编译进去之前的旧产物/旧进程上。
  重新编译并重启后端即可（`cd Backend && mvn clean package` 后重启，或在 IDEA 里 Rebuild Project 后重启）。
  验证：启动日志会出现「微信数据源：粘贴文本（默认）」，或访问 `/v3/api-docs` 能搜到 `/wechat/sessions`。

## 配置说明

- 后端主配置：`Backend/ruoyi-admin/src/main/resources/application.yml`
- 数据源配置：`Backend/ruoyi-admin/src/main/resources/application-druid.yml`
  - 默认账号 `root` / 密码 `password` 为脚手架占位值，**部署前务必修改**；该文件已纳入版本管理，请勿在其中提交真实生产凭据。
- `token.secret` 为默认占位密钥，生产环境请替换。
- 前端环境变量：`Frontend/.env.development`、`.env.production`、`.env.staging`
- 已执行过旧版 `wechat.sql` 的数据库需要执行一次 `Backend/sql/wechat-insights.sql`，
  增加好感度、认识日期及消息洞察字段；全新安装直接执行最新的 `Backend/sql/wechat.sql`。
- 已执行过旧版 `wechat.sql` 的数据库还需执行 `Backend/sql/wechat-session-delete.sql`，
  增加「删除会话」按钮权限。

## 版本管理

本仓库为单仓双项目结构，根目录 `.gitignore` 与子目录 `.gitignore`（`Backend/`、`Frontend/`）共同生效，已排除 `target/`、`node_modules/`、`dist/`、IDE 配置与日志等构建产物。

## 许可

`Backend/LICENSE` 与 `Frontend/LICENSE` 均为 MIT。
