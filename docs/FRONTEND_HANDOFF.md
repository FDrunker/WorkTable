# 前端接手说明

本文面向第一次接手 WorkTable 的前端开发者或 AI。目标不是重复业务需求，而是说明代码如何启动、请求如何流动、页面如何挂载，以及修改时应优先查看哪些文件。

## 1. 技术栈与边界

| 项目 | 当前方案 |
| --- | --- |
| 框架 | Vue 3.5.26，Composition API，`script setup` |
| 语言 | TypeScript 5.6.3，严格模式 |
| 构建 | Vite 6.4.1 |
| UI | Element Plus 2.13.1，`@element-plus/icons-vue` |
| 状态 | Pinia 3.0.4 |
| 路由 | Vue Router 4.6.4，History 模式 |
| HTTP | Axios 1.13.2 |
| 样式 | SCSS，`sass-embedded` |
| 图表 | ECharts 5.6.0 |

前端目录是独立子项目，根目录为 `Frontend/`。它只负责浏览器应用；后端接口、数据库、Redis、Chroma 和模型调用都由 `Backend/` 承担。

## 2. 启动与构建

```powershell
cd Frontend
npm install
npm run dev
```

开发服务器默认监听 `http://localhost:80`，并将 `/dev-api` 代理到 `http://localhost:8080`。后端地址只在前端 `Frontend/vite.config.ts` 中配置一次，所有开发代理共用该地址。

常用命令：

```powershell
npm test
npm run build:prod
npm run build:stage
npm run preview
```

环境文件：

| 文件 | 用途 | API 前缀 |
| --- | --- | --- |
| `.env.development` | 本地开发 | `/dev-api` |
| `.env.staging` | 预发布构建 | `/stage-api` |
| `.env.production` | 生产构建 | `/prod-api` |

Vite 只配置了开发环境 `/dev-api` 代理。生产环境必须由 Nginx、网关或其他静态部署方案把 `/prod-api` 转发到后端，前端不会自动处理该代理。

## 3. 目录职责

| 路径 | 职责 |
| --- | --- |
| `src/main.ts` | 应用入口，注册 Element Plus、Pinia、Router、插件、全局组件和全局方法 |
| `src/App.vue` | 根组件 |
| `src/permission.ts` | 全局路由守卫、登录校验、动态路由生成 |
| `src/router/index.ts` | 静态路由、隐藏路由和固定动态路由定义 |
| `src/layout/` | 后台框架布局、侧边栏、顶栏、标签页、通知和主题设置 |
| `src/views/` | 页面，按 `public`、`system`、`monitor`、`tool`、`knowledge`、`wechat` 分域 |
| `src/api/` | 后端接口封装，只处理请求参数和返回类型 |
| `src/types/api/` | API 请求、响应和领域类型 |
| `src/store/modules/` | Pinia 状态：用户、权限、字典、布局、锁屏、标签页 |
| `src/utils/request.ts` | Axios 实例、Token、防重复提交、错误提示、下载 |
| `src/components/` | 可复用业务组件和若依通用组件 |
| `src/directive/permission/` | `v-hasPermi`、`v-hasRole` 按钮级权限 |
| `src/plugins/` | `$modal`、缓存、下载、标签页等全局能力 |
| `vite/plugins/` | Vue 插件和构建期插件，包含自动导入、SVG、压缩 |
| `tests/` | 不依赖浏览器运行时的 Node 测试 |
| `scripts/verify-sfc.mjs` | 对指定 Vue/TS 文件做语法与相对导入校验 |

## 4. 应用启动链路

1. `index.html` 加载 `src/main.ts`。
2. `main.ts` 创建 Vue 应用并依次注册 Router、Pinia、全局插件、Element Plus 和全局组件。
3. `src/permission.ts` 在路由进入前检查 Cookie 中的 `Admin-Token`。
4. 首次进入受保护页面时，`user` store 调用 `/getInfo` 获取用户、角色和权限。
5. `permission` store 调用 `/getRouters` 获取后端菜单，把组件路径转换为动态 import。
6. 动态路由注册完成后重新执行目标导航。

登录相关接口位于 `src/api/login.ts`。Token 的读写位于 `src/utils/auth.ts`，当前存储键是 `Admin-Token`。

## 5. 路由与权限

`src/router/index.ts` 中有两类路由：

- 公开路由：`/`、`/personal-info`、`/customer-service`，不需要登录。
- `constantRoutes`：`/admin/login`、`/admin/register`、`/admin/index`、`/admin/user/profile` 等后台基础路由。
- `dynamicRoutes`：需要特定权限才注册的后台详情页或分配页，例如用户分配角色、角色分配用户、字典数据、任务日志、代码生成编辑页。

业务菜单本身来自后端 `sys_menu` 表。`src/store/modules/permission.ts` 会根据菜单中的 `component` 字符串匹配 `src/views/**/*.vue`，并转换 `Layout`、`ParentView`、`InnerLink` 等特殊组件。数据库菜单路径保持原值，前端通过 `src/utils/admin-route.ts` 在注册路由时统一加上 `/admin`，同时转换 `meta.activeMenu`。

按钮权限有两层：

- 模板中使用 `v-hasPermi="['knowledge:document:add']"` 等指令隐藏无权按钮。
- 后端 Controller 使用 `@PreAuthorize("@ss.hasPermi(...)")` 做强校验。

前端权限只用于改善界面，不能代替后端鉴权。新增页面时必须同时配置后端菜单权限和 Controller 权限。

## 6. 请求与响应约定

统一 Axios 实例位于 `src/utils/request.ts`。

请求阶段：

- `baseURL` 来自 `VITE_APP_BASE_API`。
- 默认携带 `Authorization: Bearer <token>`。
- `isToken: false` 可用于登录、验证码等匿名接口。
- GET 参数由 `tansParams` 拼接。
- POST/PUT 默认做 1 秒防重复提交，可通过 `repeatSubmit: false` 跳过。
- FormData 请求会先清理 JSON `Content-Type`，避免 Axios 在浏览器中无法自动生成 multipart boundary。

响应阶段：

- 普通接口使用 `AjaxResult`，结构是 `{ code, msg, data }`。
- 列表接口使用 `TableDataInfo`，结构是 `{ code, msg, rows, total }`。
- `401`：提示重新登录并退出。
- `500`：错误消息提示并 reject。
- `601`：警告消息提示并 reject。
- 其他非 200：错误通知并 reject。
- Blob 和 ArrayBuffer 会直接返回，供下载逻辑处理。

接口封装应放在 `src/api/` 并补充 `src/types/api/` 类型。页面不应直接调用 Axios。

## 7. 状态与缓存

主要 Pinia store：

| Store | 文件 | 职责 |
| --- | --- | --- |
| `user` | `src/store/modules/user.ts` | Token、用户资料、角色、权限、登录/退出 |
| `permission` | `src/store/modules/permission.ts` | 后端菜单转路由、动态路由过滤 |
| `app` | `src/store/modules/app.ts` | 侧边栏、设备类型、全局尺寸 |
| `settings` | `src/store/modules/settings.ts` | 主题、导航模式、页签、页脚、暗色模式 |
| `dict` | `src/store/modules/dict.ts` | 数据字典缓存 |
| `lock` | `src/store/modules/lock.ts` | 锁屏状态和返回路径 |
| `tagsView` | `src/store/modules/tagsView.ts` | 打开页签及缓存 |

持久化位置并不统一：Token 使用 Cookie，布局和锁屏使用 `localStorage`，防重复提交使用 `sessionStorage`。修改存储键时要同时检查读取逻辑和退出/重置逻辑。

## 8. 页面索引

### 系统与监控

- `src/views/system/`：用户、角色、菜单、字典、参数、通知公告、个人中心。
- `src/views/monitor/`：在线用户、登录日志、操作日志、定时任务、服务监控、缓存、Druid。
- `src/views/tool/`：代码生成、Swagger 页面、表单构建器。

项目已裁剪“部门”和“岗位”，旧页面、类型和 API 不应重新引入。用户与角色的基础数据权限也已移除。

### 知识库、模型与 AI 对话

三个页面都属于自研业务：

| 页面 | 主文件 | 关键 API |
| --- | --- | --- |
| 知识库管理 | `src/views/knowledge/collection/index.vue` | `src/api/knowledge/collection.ts` |
| 模型管理 | `src/views/knowledge/model/index.vue` | `src/api/knowledge/model.ts` |
| AI 对话设置 | `src/views/knowledge/customerService/index.vue` | `src/api/knowledge/customerService.ts` |
| 客服工单 | `src/views/knowledge/customerServiceTicket/index.vue` | `src/api/knowledge/customerServiceTicket.ts` |

知识库页面的关键行为：

- 左侧列表来自 Chroma collection，不是 MySQL 文档表。
- 打开知识库抽屉后分别加载文档和任务历史。
- 导入、修改和重建都是异步任务；页面每 2 秒轮询任务状态，只要存在 `PENDING` 或 `RUNNING` 就继续刷新。
- 文档可以上传 `.md` 或粘贴 Markdown；请求使用 `multipart/form-data`。
- 文档仅支持 Markdown 结构切分，导入表单不提供分隔符配置。
- 原文档不会持久化；编辑页面展示的正文由 Chroma 现有切片临时恢复。
- 每次导入、修改或重建必须选择启用中的 `embedding` 模型。
- 点击文档名查看 Chroma 切片；外部已有数据也可修改、删除或重新向量化。
- 页面关闭抽屉时会停止任务轮询。

模型页面支持 OpenAI/ChatGPT、DeepSeek、Ollama、TypeSafe Jev 的配置和连通性测试。API Key 只应提交明文到后端；后端返回的是 `apiKeyMasked`，修改时留空表示沿用旧值。

AI 对话设置页面的关键行为：

- 页面顶部可切换「后台 AI 对话」「个人简历 AI」「智能客服」三个配置场景。
- 设置包括页面标题、欢迎语、chat 模型、embedding 模型、多个知识库和 TopK 1 到 20。
- 前端只保留最近 10 轮对话作为历史请求。
- 回答可以显示意图、置信度、是否请求澄清和实际使用的知识来源。
- 问答接口超时为 180 秒，适合模型和向量检索的长耗时请求。

客服工单页面的关键行为：

- 列表支持按工单号、处理状态和优先级筛选，最多展示最近 200 条。
- 详情中展示问题内容、AI 分类理由、预分析摘要和 AI 建议回复草稿。
- 人工输入区包含优先级、回复内容和处理备注。
- 自定义动作包括「批准」「修改后批准」「升级处理」，分别写入 `APPROVED`、`MODIFIED`、`ESCALATED` 状态。

### 免登录 AI 主页

公共页面拆成两套独立代码，不再共享场景化聊天组件：

| 场景 | 页面 | API 与类型 |
| --- | --- | --- |
| 个人简历 AI | `src/views/public/resume/index.vue` | `src/api/publicResume.ts`、`src/types/api/publicResume.ts` |
| 智能客服 AI | `src/views/public/customer-service/index.vue` | `src/api/publicCustomerService.ts`、`src/types/api/publicCustomerService.ts` |

- `/` 和 `/personal-info` 指向个人简历 AI；旧 `/chat/personal-info` 会重定向到 `/personal-info`。
- `/customer-service` 指向智能客服 AI；旧 `/chat/customer-service` 会重定向到 `/customer-service`。
- 浏览器分别在 `localStorage` 中生成匿名 `visitorId`；会话列表和消息由后端按 `visitorId + scene` 隔离。
- 两页均支持新建、打开、刷新和删除历史会话，移动端使用抽屉展示历史栏。
- 简历页和客服页都只需填写问题；客服 AI 会自行判断标准问题或复杂工单。
- 客服复杂问题回复会展示分类、优先级和工单号，知识来源只在标准问题回复出现。

### 微信聊天归档

主页面是 `src/views/wechat/chat/index.vue`，接口位于 `src/api/wechat/chat.ts`，类型位于 `src/types/api/wechat/chat.ts`。

页面流程：

1. 挂载时调用 `/wechat/status` 和 `/wechat/sessions`。
2. 点击“导入文本”后，每 3 行解析为“昵称 / 时间 / 消息内容”。
3. `/wechat/text/preview` 只解析并返回昵称候选，不写数据库。
4. 用户选择“我的昵称”和可选的目标会话后，`/wechat/text/commit` 才正式归档。
5. 选择会话时追加到已有会话；不选择时按对方昵称新建会话。
6. 打开会话默认触发一次幂等同步，再显示本地归档消息。
7. 关系面板可展示好感度、认识天数、我的发挥和下一步建议，但当前只有认识日期稳定可用，其余字段多数等待后续分析链路回填。

当前后端明确关闭了微信“分析”能力。前端不应把关系洞察字段为空视为接口故障。

## 9. 全局组件与插件

`src/main.ts` 已全局注册常用组件：`Pagination`、`RightToolbar`、`FileUpload`、`ImageUpload`、`ImagePreview`、`Editor`、`DictTag`、`svg-icon`。

常用全局能力：

- `$modal`：`src/plugins/modal.ts`，统一消息、确认框和 Loading。
- `$tab`：`src/plugins/tab.ts`，页签跳转、刷新和关闭。
- `$auth`：`src/plugins/auth.ts`，代码中判断权限和角色。
- `$cache`：`src/plugins/cache.ts`，session/local 缓存封装。
- `$download`：`src/main.ts` 挂载的通用下载方法。

Vue、Vue Router、Pinia、`useDict` 和 `selectDictLabel` 已由自动导入插件处理。不要手工重复导入这些 API；业务模块应从 `src/api/` 显式导入接口。

## 10. 新增页面推荐流程

1. 在 `src/views/<domain>/` 创建 Vue 页面。
2. 在 `src/api/<domain>/` 添加接口封装。
3. 在 `src/types/api/<domain>/` 添加请求与响应类型，并确认 `src/types/api/index.ts` 已导出。
4. 在后端新增 Controller、Service、Mapper 和菜单 SQL。
5. 在 `sys_menu` 中配置组件路径、菜单权限和按钮权限。
6. 在页面按钮上使用 `v-hasPermi`，后端同步加 `@PreAuthorize`。
7. 对复杂纯逻辑补 Node 测试；对 Vue/SFC 至少运行语法校验和生产构建。

## 11. 测试与交付检查

最低检查：

```powershell
cd Frontend
npm test
npm run build:prod
```

只检查本次改动文件时可使用：

```powershell
node scripts/verify-sfc.mjs src/views/your/page.vue src/api/your/api.ts
```

涉及 TypeScript 类型或动态路由时，还应运行：

```powershell
npx vue-tsc --noEmit
```

当前仓库的 `vue-tsc 2.1.10`、Vue 3.5.26 和 TypeScript 5.6.3 组合下，完整类型检查并非绿色，已知会报 Vue 核心导出、`pwdChrtype` 声明和少量隐式 `any`。这是本次文档工作之前就存在的技术债，不代表生产构建失败；修复时应单独升级或校准类型依赖，并与 `npm run build:prod` 一起验证。

当前自动化测试很少，`tests/request-content-type.test.mjs` 主要覆盖 FormData 请求头修正。对知识库任务轮询、微信导入、权限逻辑或请求拦截器做改动时，应增加针对纯函数或组合逻辑的测试。

`tests/admin-route.test.mjs` 已覆盖后台路径前缀和 `meta.activeMenu` 的不可变转换。公共对话或后台场景改动后，至少应运行 `npm test` 与 `npm run build:prod`。

## 12. 常见陷阱

- 不要直接改 `src/router/index.ts` 来新增普通业务菜单；普通菜单应由后端 `sys_menu` 驱动。
- 动态路由依赖组件字符串与 `src/views` 下的实际相对路径完全匹配。
- 修改 `permission.ts`、`user.ts` 或动态路由时，注意首次导航重放和 404 路由的顺序。
- 生产 API 前缀是 `/prod-api`，但 Vite 代理只覆盖开发环境。
- 知识库导入和重建是异步接口，收到“任务已提交”不代表 Chroma 已写入完成。
- 微信文本预览和已提交文本保存在后端进程内存中，后端重启后需重新粘贴原文。
- 页面权限和按钮权限不能替代后端校验。

## 13. 首先阅读的文件

建议接手顺序：

1. `Frontend/src/main.ts`
2. `Frontend/src/permission.ts`
3. `Frontend/src/router/index.ts`
4. `Frontend/src/store/modules/user.ts`
5. `Frontend/src/store/modules/permission.ts`
6. `Frontend/src/utils/request.ts`
7. `Frontend/src/views/knowledge/collection/index.vue`
8. `Frontend/src/views/knowledge/customerService/index.vue`
9. `Frontend/src/views/wechat/chat/index.vue`
10. `Frontend/src/views/public/resume/index.vue`
11. `Frontend/src/views/public/customer-service/index.vue`
12. `Frontend/src/views/knowledge/customerServiceTicket/index.vue`
