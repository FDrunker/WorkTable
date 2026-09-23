-- ----------------------------
-- 智能客服功能
-- 依赖于 ai-knowledge.sql 已执行完毕
-- ----------------------------

create table if not exists ai_customer_service_config (
  config_id          bigint        not null auto_increment comment '智能客服配置ID',
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
  primary key (config_id)
) engine=innodb auto_increment=1 comment='智能客服配置表';

insert ignore into ai_customer_service_config
  (config_id, top_k, enabled, create_by, create_time)
values
  (1, 5, '1', 'admin', sysdate());

delete from sys_role_menu where menu_id in ('3003', '3040', '3041', '3042');
delete from sys_menu where menu_id in ('3003', '3040', '3041', '3042');

insert into sys_menu values('3003', '智能客服', '3000', '3', 'customer-service', 'knowledge/customerService/index', '', '', 1, 0, 'C', '0', '0', 'knowledge:customer-service:list', 'message', 'admin', sysdate(), '', null, '智能客服菜单');
insert into sys_menu values('3040', '客服查询', '3003', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:customer-service:list', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3041', '客服问答', '3003', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:customer-service:chat', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3042', '客服设置', '3003', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'knowledge:customer-service:edit', '#', 'admin', sysdate(), '', null, '');

insert into sys_role_menu values('1', '3003');
insert into sys_role_menu values('1', '3040');
insert into sys_role_menu values('1', '3041');
insert into sys_role_menu values('1', '3042');
