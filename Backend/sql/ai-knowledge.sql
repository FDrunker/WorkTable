-- ----------------------------
-- 知识库与模型管理模块
-- 依赖于 ry_20260417.sql 已执行完毕
-- ----------------------------

drop table if exists ai_knowledge_task;
drop table if exists ai_knowledge_document;
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
  content           longtext                              comment 'Markdown 正文',
  temp_file_path    varchar(1000) default null            comment '本地暂存文件路径',
  chunk_separator   varchar(64)   default '\n'            comment '切片分隔符',
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

-- ----------------------------
-- 菜单与权限
-- ----------------------------
delete from sys_role_menu where menu_id between 3000 and 3099;
delete from sys_menu where menu_id between 3000 and 3099;

insert into sys_menu values('3000', '知识库管理', '0', '5', 'knowledge', null, '', '', 1, 0, 'M', '0', '0', '', 'notebook-1', 'admin', sysdate(), '', null, '知识库管理目录');
insert into sys_menu values('3001', '向量集合', '3000', '1', 'collection', 'knowledge/collection/index', '', '', 1, 0, 'C', '0', '0', 'knowledge:collection:list', 'collection', 'admin', sysdate(), '', null, 'Chroma 集合管理菜单');
insert into sys_menu values('3002', '模型管理', '3000', '2', 'model', 'knowledge/model/index', '', '', 1, 0, 'C', '0', '0', 'knowledge:model:list', 'cpu', 'admin', sysdate(), '', null, 'AI 模型配置菜单');

insert into sys_menu values('3010', '集合查询', '3001', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:collection:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3011', '文档查询', '3001', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:document:list', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3012', '文档新增', '3001', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:document:add', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3013', '文档修改', '3001', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:document:edit', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3014', '文档删除', '3001', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:document:remove', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3015', '重新向量化', '3001', '6', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:document:reindex', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3016', '任务查询', '3001', '7', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:task:list', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3017', '取消任务', '3001', '8', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:task:cancel', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3018', '重试任务', '3001', '9', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:task:retry', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3019', '新增集合', '3001', '10', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:collection:add', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3020', '删除集合', '3001', '11', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:collection:remove', '#', 'admin', sysdate(), '', null, '');

insert into sys_menu values('3030', '模型查询', '3002', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:model:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3031', '模型新增', '3002', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:model:add', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3032', '模型修改', '3002', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:model:edit', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3033', '模型删除', '3002', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:model:remove', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3034', '连通性测试', '3002', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:model:test', '#', 'admin', sysdate(), '', null, '');

insert into sys_role_menu values('1', '3000');
insert into sys_role_menu values('1', '3001');
insert into sys_role_menu values('1', '3002');
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
