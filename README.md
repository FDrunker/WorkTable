# WorkTable

后台管理平台，基于 **RuoYi-Vue 3.9.2**（若依）前后端分离脚手架搭建，采用「Backend + Frontend」双目录单仓库结构。

## 目录结构

```
WorkTable/
├── Backend/                     # 后端：Spring Boot + Maven 多模块
│   ├── ruoyi-admin/             # 启动模块（RuoYiApplication、Controller、配置、Swagger）
│   ├── ruoyi-common/            # 通用工具、注解、常量、异常、基础实体
│   ├── ruoyi-framework/         # 框架核心：安全、JWT、数据源、拦截器、AOP
│   ├── ruoyi-system/            # 系统业务模块：用户 / 角色 / 菜单 / 部门 / 字典
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

服务默认监听 `http://localhost:8080`，Swagger UI：`http://localhost:8080/swagger-ui.html`。

### 3. 前端

```bash
cd Frontend
npm install
npm run dev          # 开发模式
npm run build:prod   # 生产构建
```

开发服务器默认监听 `http://localhost:80`，请求经 `/dev-api` 代理到后端 `http://localhost:8080`（见 `vite.config.ts`）。

## 配置说明

- 后端主配置：`Backend/ruoyi-admin/src/main/resources/application.yml`
- 数据源配置：`Backend/ruoyi-admin/src/main/resources/application-druid.yml`
  - 默认账号 `root` / 密码 `password` 为脚手架占位值，**部署前务必修改**；该文件已纳入版本管理，请勿在其中提交真实生产凭据。
- `token.secret` 为默认占位密钥，生产环境请替换。
- 前端环境变量：`Frontend/.env.development`、`.env.production`、`.env.staging`

## 版本管理

本仓库为单仓双项目结构，根目录 `.gitignore` 与子目录 `.gitignore`（`Backend/`、`Frontend/`）共同生效，已排除 `target/`、`node_modules/`、`dist/`、IDE 配置与日志等构建产物。

## 许可

`Backend/LICENSE` 与 `Frontend/LICENSE` 均为 MIT。
