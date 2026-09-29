-- ----------------------------
-- 知识库与模型管理模块
-- 依赖于 ry_20260417.sql 已执行完毕
-- ----------------------------

drop table if exists ai_knowledge_task;
drop table if exists ai_knowledge_document;
drop table if exists ai_chat_message;
drop table if exists ai_chat_conversation;
drop table if exists ai_customer_service_config;
drop table if exists ai_model_config;

create table ai_model_config (
  model_id          bigint        not null auto_increment comment '模型配置ID',
  model_name        varchar(128)  not null                comment '显示名称',
  provider          varchar(32)   not null                comment '提供商 OPENAI/DEEPSEEK/OLLAMA/TYPESAFE',
  model_type        varchar(32)   not null                comment '模型类型 embedding/chat/system-one',
  model_code        varchar(191)  not null                comment '供应商模型名',
  base_url          varchar(500)  default ''              comment '接口地址',
  api_key_cipher    text                                  comment 'AES-GCM 加密后的 API Key',
  enabled           char(1)       default '1'             comment '启用状态（0停用 1启用）',
  is_default        char(1)       default '0'             comment '同类型默认模型（0否 1是）',
  extra_config      longtext                              comment '扩展配置 JSON',
  last_test_status  varchar(16)   default 'UNTESTED'      comment '最近测试状态 UNTESTED/SUCCESS/FAILED',
  last_test_message varchar(1000) default ''              comment '最近测试结果',
  last_test_time    datetime                              comment '最近测试时间',
  create_by         varchar(64)   default ''              comment '创建者',
  create_time       datetime                              comment '创建时间',
  update_by         varchar(64)   default ''              comment '更新者',
  update_time       datetime                              comment '更新时间',
  remark            varchar(500)  default null            comment '备注',
  primary key (model_id),
  key idx_ai_model_type (model_type, enabled, is_default)
) engine=innodb auto_increment=1 comment='AI 模型配置表';

create table ai_knowledge_document (
  document_id       varchar(64)   not null                comment '逻辑文档ID',
  collection_id     varchar(64)   not null                comment 'Chroma 集合ID',
  collection_name   varchar(255)  default ''              comment 'Chroma 集合名称快照',
  document_name     varchar(255)  not null                comment '文档名称',
  source_type       varchar(16)   not null                comment '来源 UPLOAD/PASTE',
  temp_file_path    varchar(1000) default null            comment '本地暂存文件路径',
  chunk_size        int           default 1024            comment '切片大小（字符）',
  chunk_overlap     int           default 100             comment '切片重叠（字符）',
  embedding_model_id bigint       default null            comment '创建或最近更新时选择的 embedding 模型',
  status            varchar(32)   default 'PENDING'       comment '文档状态',
  chunk_count       int           default 0               comment '切片数量',
  create_by         varchar(64)   default ''              comment '创建者',
  create_time       datetime                              comment '创建时间',
  update_by         varchar(64)   default ''              comment '更新者',
  update_time       datetime                              comment '更新时间',
  remark            varchar(500)  default null            comment '备注',
  primary key (document_id),
  key idx_ai_document_collection (collection_id, create_time)
) engine=innodb comment='知识库逻辑文档表';

create table ai_knowledge_task (
  task_id           bigint        not null auto_increment comment '任务ID',
  task_type         varchar(32)   not null                comment '任务类型 IMPORT/UPDATE/REINDEX',
  document_id       varchar(64)   default ''              comment '逻辑文档ID',
  document_name     varchar(255)  default ''              comment '文档名称',
  collection_id     varchar(64)   not null                comment 'Chroma 集合ID',
  collection_name   varchar(255)  default ''              comment 'Chroma 集合名称',
  model_id          bigint        default null            comment 'embedding 模型ID，空表示由 Chroma 处理',
  status            varchar(16)   default 'PENDING'       comment 'PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
  progress          int           default 0               comment '进度百分比',
  stage             varchar(32)   default 'PENDING'       comment '当前阶段',
  message           varchar(1000) default ''              comment '进度说明',
  error_message     varchar(2000) default ''              comment '错误信息',
  request_payload   longtext                              comment '任务请求快照 JSON',
  cancel_requested  char(1)       default '0'             comment '是否请求取消',
  retry_count       int           default 0               comment '重试次数',
  start_time        datetime                              comment '开始时间',
  end_time          datetime                              comment '结束时间',
  create_by         varchar(64)   default ''              comment '创建者',
  create_time       datetime                              comment '创建时间',
  update_by         varchar(64)   default ''              comment '更新者',
  update_time       datetime                              comment '更新时间',
  remark            varchar(500)  default null            comment '备注',
  primary key (task_id),
  key idx_ai_task_document (document_id, task_id),
  key idx_ai_task_collection (collection_id, task_id),
  key idx_ai_task_status (status, task_id)
) engine=innodb auto_increment=1 comment='知识库异步任务表';

create table ai_customer_service_config (
  config_id          bigint        not null auto_increment comment 'AI对话配置ID',
  scene              varchar(32)   not null default 'admin' comment '场景标识',
  title              varchar(64)   not null default ''      comment '页面标题',
  welcome_message    varchar(500)  not null default ''      comment '欢迎语',
  chat_model_id      bigint        default null            comment '对话模型ID',
  embedding_model_id bigint        default null            comment '检索向量模型ID',
  collection_ids     text                                  comment '知识库集合ID JSON 数组',
  top_k              int           default 5               comment '检索返回条数',
  enabled            char(1)       default '1'             comment '启用状态（0停用 1启用）',
  create_by          varchar(64)   default ''              comment '创建者',
  create_time        datetime                              comment '创建时间',
  update_by          varchar(64)   default ''              comment '更新者',
  update_time        datetime                              comment '更新时间',
  remark             varchar(500)  default null            comment '备注',
  primary key (config_id),
  unique key uk_ai_customer_service_scene (scene)
) engine=innodb auto_increment=1 comment='AI对话配置表';

insert into ai_customer_service_config
  (config_id, scene, title, welcome_message, top_k, enabled, create_by, create_time)
values
  (1, 'admin', '后台 AI 对话', '你好，有什么可以帮你的？', 5, '1', 'admin', sysdate()),
  (2, 'personal-info', '个人简历 AI', '你好，我可以依据简历知识库回答你的问题。', 5, '1', 'admin', sysdate()),
  (3, 'customer-service', '智能客服', '你好，我是智能客服，请告诉我你需要咨询的问题。', 5, '1', 'admin', sysdate());

create table ai_chat_conversation (
  conversation_id varchar(64)  not null                comment '会话ID',
  visitor_id      varchar(64)  not null                comment '匿名访客ID',
  scene           varchar(32)  not null                comment '对话场景',
  title           varchar(255) not null default '新对话' comment '会话标题',
  create_time     datetime     not null                comment '创建时间',
  update_time     datetime     not null                comment '更新时间',
  primary key (conversation_id),
  key idx_ai_chat_conversation_visitor (visitor_id, scene, update_time)
) engine=innodb comment='公开AI对话会话表';

create table ai_chat_message (
  message_id          bigint       not null auto_increment comment '消息ID',
  conversation_id     varchar(64)  not null                comment '会话ID',
  role                varchar(16)  not null                comment '角色 user/assistant',
  content             longtext     not null                comment '消息内容',
  intent              varchar(128) default null            comment '识别意图',
  intent_confidence   double       default null            comment '意图置信度',
  needs_clarification char(1)      not null default '0'    comment '是否需要澄清',
  sources_json        longtext     default null            comment '知识来源引用 JSON，仅保存记录ID和知识库ID',
  category            varchar(32)  default null            comment '客服分类 standard/complex',
  priority            varchar(16)  default null            comment '工单优先级 low/medium/high',
  ticket_no           varchar(32)  default null            comment '复杂问题工单号',
  create_time         datetime     not null                comment '创建时间',
  primary key (message_id),
  key idx_ai_chat_message_conversation (conversation_id, message_id)
) engine=innodb auto_increment=1 comment='公开AI对话消息表';

create table ai_customer_service_ticket (
  ticket_id             bigint        not null auto_increment comment '工单ID',
  ticket_no             varchar(32)   not null                comment '工单号',
  conversation_id       varchar(64)   not null                comment '来源会话ID',
  visitor_id            varchar(64)   not null                comment '匿名访客ID',
  issue_type            varchar(64)   not null                comment '用户选择的问题类型',
  question              longtext      not null                comment '问题内容',
  category              varchar(32)   not null                comment '分类结果',
  classification_reason varchar(500)  default ''              comment '分类理由',
  priority              varchar(16)   not null default 'medium' comment '优先级',
  status                varchar(32)   not null default 'WAITING' comment 'WAITING/APPROVED/MODIFIED/ESCALATED',
  preanalysis_summary   longtext                              comment '问题摘要',
  preanalysis_solution  longtext                              comment '建议处理方案',
  suggested_reply       longtext                              comment 'AI 建议回复草稿',
  handler_reply         longtext                              comment '人工最终回复',
  handle_note           varchar(1000) default ''              comment '人工处理备注',
  handled_by            varchar(64)   default null            comment '处理人',
  handle_time           datetime                              comment '处理时间',
  create_time           datetime      not null                comment '创建时间',
  update_time           datetime      not null                comment '更新时间',
  primary key (ticket_id),
  unique key uk_ai_customer_service_ticket_no (ticket_no),
  key idx_ai_customer_service_ticket_status (status, priority, create_time),
  key idx_ai_customer_service_ticket_conversation (conversation_id)
) engine=innodb auto_increment=1 comment='智能客服人工处理工单';

-- ----------------------------
-- 菜单与权限
-- ----------------------------
delete from sys_role_menu where menu_id between 3000 and 3099;
delete from sys_menu where menu_id between 3000 and 3099;

insert into sys_menu values('3001', '知识库管理', '0', '5', 'knowledge-base', 'knowledge/collection/index', '', '', 1, 0, 'C', '0', '0', 'knowledge:collection:list', 'collection', 'admin', sysdate(), '', null, '知识库管理菜单');
insert into sys_menu values('3002', '模型管理', '0', '6', 'model', 'knowledge/model/index', '', '', 1, 0, 'C', '0', '0', 'knowledge:model:list', 'cpu', 'admin', sysdate(), '', null, 'AI 模型配置菜单');
insert into sys_menu values('3003', 'AI对话', '0', '7', 'ai-chat', 'knowledge/customerService/index', '', '', 1, 0, 'C', '0', '0', 'ai:chat:list', 'message', 'admin', sysdate(), '', null, 'AI对话菜单');
insert into sys_menu values('3004', '客服工单', '0', '8', 'customer-service-ticket', 'knowledge/customerServiceTicket/index', '', '', 1, 0, 'C', '0', '0', 'customer-service:ticket:list', 'message', 'admin', sysdate(), '', null, '智能客服人工处理工单');

insert into sys_menu values('3010', '知识库查询', '3001', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:collection:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3011', '文档查询', '3001', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:document:list', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3012', '文档新增', '3001', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:document:add', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3013', '文档修改', '3001', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:document:edit', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3014', '文档删除', '3001', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:document:remove', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3015', '重新向量化', '3001', '6', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:document:reindex', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3016', '任务查询', '3001', '7', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:task:list', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3017', '取消任务', '3001', '8', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:task:cancel', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3018', '重试任务', '3001', '9', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:task:retry', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3019', '新增知识库', '3001', '10', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:collection:add', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3020', '删除知识库', '3001', '11', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:collection:remove', '#', 'admin', sysdate(), '', null, '');

insert into sys_menu values('3030', '模型查询', '3002', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:model:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3031', '模型新增', '3002', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:model:add', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3032', '模型修改', '3002', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:model:edit', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3033', '模型删除', '3002', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:model:remove', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3034', '连通性测试', '3002', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:model:test', '#', 'admin', sysdate(), '', null, '');

insert into sys_menu values('3040', 'AI对话查询', '3003', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'ai:chat:list', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3041', 'AI对话问答', '3003', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'ai:chat:chat', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3042', 'AI对话设置', '3003', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'ai:chat:edit', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3043', '工单查询', '3004', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'customer-service:ticket:list', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3044', '工单处理', '3004', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'customer-service:ticket:edit', '#', 'admin', sysdate(), '', null, '');

insert into sys_role_menu values('1', '3001');
insert into sys_role_menu values('1', '3002');
insert into sys_role_menu values('1', '3003');
insert into sys_role_menu values('1', '3004');
insert into sys_role_menu values('1', '3010');
insert into sys_role_menu values('1', '3011');
insert into sys_role_menu values('1', '3012');
insert into sys_role_menu values('1', '3013');
insert into sys_role_menu values('1', '3014');
insert into sys_role_menu values('1', '3015');
insert into sys_role_menu values('1', '3016');
insert into sys_role_menu values('1', '3017');
insert into sys_role_menu values('1', '3018');
insert into sys_role_menu values('1', '3019');
insert into sys_role_menu values('1', '3020');
insert into sys_role_menu values('1', '3030');
insert into sys_role_menu values('1', '3031');
insert into sys_role_menu values('1', '3032');
insert into sys_role_menu values('1', '3033');
insert into sys_role_menu values('1', '3034');
insert into sys_role_menu values('1', '3040');
insert into sys_role_menu values('1', '3041');
insert into sys_role_menu values('1', '3042');
insert into sys_role_menu values('1', '3043');
insert into sys_role_menu values('1', '3044');
