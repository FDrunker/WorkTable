-- ----------------------------
-- 将智能客服升级为一级菜单 AI对话
-- 适用于已经执行过 customer-service.sql 的数据库
-- ----------------------------

delete from sys_role_menu where menu_id in ('3003', '3040', '3041', '3042');
delete from sys_menu where menu_id in ('3003', '3040', '3041', '3042');

insert into sys_menu values('3003', 'AI对话', '0', '6', 'ai-chat', 'knowledge/customerService/index', '', '', 1, 0, 'C', '0', '0', 'ai:chat:list', 'message', 'admin', sysdate(), '', null, 'AI对话菜单');
insert into sys_menu values('3040', 'AI对话查询', '3003', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'ai:chat:list', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3041', 'AI对话问答', '3003', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'ai:chat:chat', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('3042', 'AI对话设置', '3003', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'ai:chat:edit', '#', 'admin', sysdate(), '', null, '');

insert into sys_role_menu values('1', '3003');
insert into sys_role_menu values('1', '3040');
insert into sys_role_menu values('1', '3041');
insert into sys_role_menu values('1', '3042');
