/**
 * API 类型统一导出
 */
export * from "./common";

// 登录模块
export * from "./login";
export * from "./menu";
export * from "./publicChat";

// System 模块
export * from "./system/user";
export * from "./system/role";
export * from "./system/menu";
export * from "./system/dict";
export * from "./system/config";
export * from "./system/notice";

// monitor 模块
export * from "./monitor/cache";
export * from "./monitor/job";
export * from "./monitor/jobLog";
export * from "./monitor/logininfor";
export * from "./monitor/operlog";
export * from "./monitor/online";

// 代码生成模块
export * from "./tool/gen";

// 微信聊天分析模块
export * from "./wechat/chat";

// 知识库与模型管理模块
export * from "./knowledge/collection";
export * from "./knowledge/model";
export * from "./knowledge/customerService";
