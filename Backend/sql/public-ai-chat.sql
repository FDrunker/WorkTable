-- ----------------------------
-- 免登录 AI 对话场景、会话与消息
-- 适用于已经执行过 ai-knowledge.sql / customer-service.sql 的数据库
-- ----------------------------

alter table ai_customer_service_config
  add column scene varchar(32) not null default 'admin' comment '场景标识' after config_id,
  add column title varchar(64) not null default '' comment '页面标题' after scene,
  add column welcome_message varchar(500) not null default '' comment '欢迎语' after title;

update ai_customer_service_config
set scene = 'admin',
    title = '后台 AI 对话',
    welcome_message = '你好，有什么可以帮你的？'
where config_id = 1;

alter table ai_customer_service_config
  add unique key uk_ai_customer_service_scene (scene);

insert ignore into ai_customer_service_config
  (scene, title, welcome_message, top_k, enabled, create_by, create_time)
values
  ('personal-info', '个人简历 AI', '你好，我可以依据简历知识库回答你的问题。', 5, '1', 'admin', sysdate()),
  ('customer-service', '智能客服', '你好，我是智能客服，请告诉我你需要咨询的问题。', 5, '1', 'admin', sysdate());

create table if not exists ai_chat_conversation (
  conversation_id varchar(64)  not null                comment '会话ID',
  visitor_id      varchar(64)  not null                comment '匿名访客ID',
  scene           varchar(32)  not null                comment '对话场景',
  title           varchar(255) not null default '新对话' comment '会话标题',
  create_time     datetime     not null                comment '创建时间',
  update_time     datetime     not null                comment '更新时间',
  primary key (conversation_id),
  key idx_ai_chat_conversation_visitor (visitor_id, scene, update_time)
) engine=innodb comment='公开AI对话会话表';

create table if not exists ai_chat_message (
  message_id          bigint       not null auto_increment comment '消息ID',
  conversation_id     varchar(64)  not null                comment '会话ID',
  role                varchar(16)  not null                comment '角色 user/assistant',
  content             longtext     not null                comment '消息内容',
  intent              varchar(128) default null            comment '识别意图',
  intent_confidence   double       default null            comment '意图置信度',
  needs_clarification char(1)      not null default '0'    comment '是否需要澄清',
  sources_json        longtext     default null            comment '知识来源 JSON',
  category            varchar(32)  default null            comment '客服分类 standard/complex',
  priority            varchar(16)  default null            comment '工单优先级 low/medium/high',
  ticket_no           varchar(32)  default null            comment '复杂问题工单号',
  create_time         datetime     not null                comment '创建时间',
  primary key (message_id),
  key idx_ai_chat_message_conversation (conversation_id, message_id)
) engine=innodb auto_increment=1 comment='公开AI对话消息表';
