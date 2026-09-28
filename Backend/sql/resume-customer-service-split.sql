-- ----------------------------
-- 个人简历 AI / 智能客服 AI 拆分与客服工单
-- 适用于已执行过 ai-knowledge.sql、customer-service.sql、public-ai-chat.sql 的数据库。
-- 全新部署可直接执行最新版 ai-knowledge.sql，无需再执行本迁移。
-- ----------------------------

alter table ai_chat_message
  add column category varchar(32) default null comment '客服分类 standard/complex' after sources_json,
  add column priority varchar(16) default null comment '工单优先级 low/medium/high' after category,
  add column ticket_no varchar(32) default null comment '复杂问题工单号' after priority;

create table if not exists ai_customer_service_ticket (
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

update ai_customer_service_config
set title = '个人简历 AI',
    welcome_message = '你好，我可以依据简历知识库回答你的问题。'
where scene = 'personal-info';

delete from sys_role_menu where menu_id in ('3004', '3043', '3044');
delete from sys_menu where menu_id in ('3004', '3043', '3044');

insert into sys_menu values('3004', '客服工单', '0', '8', 'customer-service-ticket', 'knowledge/customerServiceTicket/index', '', '', 1, 0, 'C', '0', '0', 'customer-service:ticket:list', 'message', 'admin', sysdate(), '', null, '智能客服人工处理工单');
insert into sys_menu values('3043', '工单查询', '3004', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'customer-service:ticket:list', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3044', '工单处理', '3004', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'customer-service:ticket:edit', '#', 'admin', sysdate(), '', null, '');

insert into sys_role_menu values('1', '3004');
insert into sys_role_menu values('1', '3043');
insert into sys_role_menu values('1', '3044');
