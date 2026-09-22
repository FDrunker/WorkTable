# WorkTable

后台管理平台，基于 **RuoYi-Vue 3.9.2**（若依）前后端分离脚手架搭建，采用「Backend + Frontend」双目录单仓库结构。

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
│   ├── sql/                     # 数据库脚本：ry_*.sql、quartz.sql
│   └── pom.xml                  # 聚合父 POM（groupId: com.ruoyi, version: 3.9.2）
└── Frontend/                    # 前端：Vue 3 + Vite + Element Plus
    ├── src/api/                 # 接口封装
    ├── src/views/               # 页面：system / monitor / tool
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

### 2. 后端

```bash
cd Backend
# 按需修改 ruoyi-admin/src/main/resources/application-druid.yml 中的数据库地址与密码
mvn clean package
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

服务默认监听 `http://localhost:8090`，Swagger UI：`http://localhost:8090/swagger-ui.html`。

> 该端口由 `application.yml` 的 `server.port` 控制（脚手架原为 8080，本机长期被其他进程占用故改为 8090）。
> 需要临时更换时用启动参数覆盖即可，无须改文件：
>
> ```bash
> java -jar ruoyi-admin/target/ruoyi-admin.jar --server.port=8091
> ```

### 3. 前端

```bash
cd Frontend
npm install
npm run dev          # 开发模式
npm run build:prod   # 生产构建
```

开发服务器默认监听 `http://localhost:80`，请求经 `/dev-api` 代理到后端 `http://localhost:8090`（见 `vite.config.ts`）。

## 模块裁剪记录

本项目已在若依脚手架基础上裁掉 **部门** 与 **岗位** 两个模块，代码与初始化脚本中不再包含「部门」「岗位」概念：

- **已移除后端文件**：`SysDept`/`SysDeptController`/`ISysDeptService`/`SysDeptServiceImpl`/`SysDeptMapper`、`SysPost`/`SysPostController`/`ISysPostService`/`SysPostServiceImpl`/`SysPostMapper`、`SysUserPost`/`SysRoleDept` 及其 Mapper 与对应 XML。
- **已移除数据权限**：`@DataScope` 注解、`DataScopeAspect` 切面、`checkUserDataScope`/`checkRoleDataScope`/`authDataScope`；角色不再有「数据权限」入口，用户与角色列表不再按机构过滤。
- **已移除关联字段**：`sys_user.dept_id`、`LoginUser.deptId`、`BaseController.getDeptId()`、`SecurityUtils.getDeptId()`、`SysUser.postIds`/`dept`、`SysRole.dataScope`/`deptIds`/`deptCheckStrictly`、`SysOperLog.deptName`、在线用户与公告已读列表的「所属部门」列。
- **已移除前端**：`src/views/system/dept`、`src/views/system/post`、`src/api/system/dept.ts`、`src/api/system/post.ts`、`src/types/api/system/dept.ts`、`src/types/api/system/post.ts`，以及用户管理、角色管理、操作日志、在线用户、公告已读页面中的部门/岗位表单项与列。
- **SQL**：`Backend/sql/ry_20260417.sql` 已删除 `sys_dept`、`sys_post`、`sys_role_dept`、`sys_user_post` 建表与初始化数据，删除 `sys_user.dept_id`、`sys_oper_log.dept_name`、`sys_role.data_scope`、`sys_role.dept_check_strictly` 列，并清理 `sys_menu` / `sys_role_menu` 中部门与岗位相关菜单与按钮权限（菜单 103、104 及 1016–1024）。

## 微信聊天分析（ruoyi-wechat）

聊天分析的**数据来源是用户粘贴的聊天记录文本**，不读取本地微信数据库，也不依赖任何外部命令：

- **入口**：聊天分析页（`Frontend/src/views/wechat/chat/index.vue`）左下角「导入文本」。
- **格式**：每 3 行一条消息 —— 昵称 / 时间 / 消息内容；消息内容可跨多行，组与组之间可空行。
  时间支持 `2026-04-01 19:02`、`2026/4/1 19:02`、`2026年4月1日 19:02`、`4月1日 19:02`（省略年份按当年计算），
  兼容 `下午7:02` 这类时段写法，统一归一化为 `yyyy-MM-dd HH:mm[:ss]`。
- **语音与动画表情**：`[语音]`、`[动画表情]` 会照常归档并显示在聊天记录界面，但**不参与分析**
  （对应 `CleanedMessage.Kind.VOICE` / `STICKER` 的 `displayOnly()`）。静态 `[表情]` 仍按降噪丢弃。
- **幂等**：会话标识由会话名派生，重复粘贴同一段对话是追加去重，不会重复建会话。
- **会话名可改**：聊天界面标题上的铅笔按钮可就地改名（`POST /wechat/session/rename`，沿用 `wechat:identity:edit` 权限）。
  只改展示名，会话标识不变 —— 所以改过名的会话在后续导入里依然是同一个会话，不会被拆成两个，也不会被重新导入冲掉。
- **关系面板**：聊天区顶部展示当前好感度、认识天数、我的发挥和下一步建议入口。
  认识日期由用户选择，通过 `POST /wechat/session/acquaintance-date` 保存；认识当天按第 1 天计算。
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

## 版本管理

本仓库为单仓双项目结构，根目录 `.gitignore` 与子目录 `.gitignore`（`Backend/`、`Frontend/`）共同生效，已排除 `target/`、`node_modules/`、`dist/`、IDE 配置与日志等构建产物。

## 许可

`Backend/LICENSE` 与 `Frontend/LICENSE` 均为 MIT。
